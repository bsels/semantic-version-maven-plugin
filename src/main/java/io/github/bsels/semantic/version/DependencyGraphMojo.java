package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.MavenProjectAndDocument;
import io.github.bsels.semantic.version.models.graph.ArtifactLocation;
import io.github.bsels.semantic.version.models.graph.DetailedGraphNode;
import io.github.bsels.semantic.version.parameters.GraphOutput;
import io.github.bsels.semantic.version.utils.Utils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Represents a Maven Mojo goal for generating a dependency graph of Maven projects within the current execution scope.
/// This goal facilitates the extraction, transformation, and representation of dependency relationships among Maven
/// project artifacts and supports producing the graph output in a configurable format.
///
/// This Mojo is typically used during Maven builds to analyze project dependencies and produce insights
/// into the structure of the dependency tree, which may assist in understanding transitive dependencies,
/// resolving conflicts, or debugging build issues.
///
/// The generated dependency graph can include both direct and transitive dependencies and is represented
/// as directed graph nodes, where each node corresponds to a Maven project artifact.
///
/// The final graph representation, including its format and location, is configurable via parameters.
@Mojo(name = "graph", aggregator = true, requiresDependencyResolution = ResolutionScope.NONE)
@Execute(phase = LifecyclePhase.NONE)
public final class DependencyGraphMojo extends BaseMojo {

    /// Indicates whether project folder paths should be resolved as relative paths with respect to
    /// the execution root directory.
    /// If set to `true`, the project paths will be returned as relative paths.
    /// If set to `false`, absolute paths will be used instead.
    /// This parameter is required and defaults to `true`.
    ///
    /// Configurable via the Maven property `versioning.relativePaths`.
    @Parameter(property = "versioning.relativePaths", required = true, defaultValue = "true")
    boolean useRelativePaths = true;

    /// Specifies the format in which the dependency graph will be produced.
    ///
    /// This variable determines whether the dependency graph output includes:
    /// - Only Maven artifact data ([GraphOutput#ARTIFACT_ONLY]), format:
    ///  ```json
    ///  {
    ///     "{groupId}:{artifactId}": ["{groupId}:{artifactId}",...],
    ///     ...
    ///  }
    ///  ```
    /// - Only the folder paths of the projects ([GraphOutput#FOLDER_ONLY]), format
    ///  ```json
    ///  {
    ///     "{groupId}:{artifactId}": ["{folder}",...],
    ///     ...
    ///  }
    ///  ```
    /// - Both Maven artifact data and folder paths ([GraphOutput#ARTIFACT_AND_FOLDER]), format
    ///   ```json
    ///   {
    ///       "{groupId}:{artifactId}": [
    ///           {
    ///               "artifact": {
    ///                   "groupId": "{groupId}",
    ///                   "artifactId": "{artifactId}"
    ///               },
    ///               "folder": "{folder}"
    ///           },...
    ///       ],
    ///       ...
    ///   }
    ///   ```
    ///
    /// The value of this field is required and defaults to [GraphOutput#ARTIFACT_AND_FOLDER].
    /// It is set via the Maven plugin parameter `versioning.graphOutput`.
    @Parameter(property = "versioning.graphOutput", required = true, defaultValue = "ARTIFACT_AND_FOLDER")
    GraphOutput graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;

    /// Specifies the output file for the generated dependency graph.
    ///
    /// If this value is set to `null`,
    /// the dependency graph output will be printed to the console instead of being written to an external file.
    ///
    /// Uses the Maven property `versioning.outputFile` to allow configuration via Maven's usage of properties
    /// or command-line arguments.
    @Parameter(property = "versioning.outputFile")
    Path outputFile = null;

    /// Default constructor for the `DependencyGraphMojo` class.
    /// Initializes an instance by invoking the superclass constructor.
    ///
    /// This constructor is typically used by the Maven framework during the build process to instantiate
    /// the goal implementation, enabling the execution of its logic.
    public DependencyGraphMojo() {
        super();
    }

    /// Executes the internal logic for creating a dependency graph representation of Maven projects in the current scope.
    /// This method performs the following steps:
    /// 1. Resolves the execution root directory and logs it for debugging.
    /// 2. Retrieves all Maven projects in the current execution scope.
    /// 3. Maps Maven projects to their corresponding artifacts and project folder paths.
    /// 4. Parses and reads all POM files for the projects, creating a mapping of Maven artifacts to their parsed documents.
    /// 5. Creates a dependency mapping for artifacts, associating project artifacts with their dependent artifacts.
    /// 6. Resolves transitive dependencies for each project artifact and creates a mapping of project artifacts to their resolved dependencies.
    /// 7. Prepares a directed graph representation of Maven project artifacts (`Node` instances) using the resolved dependency data.
    /// 8. Produces the output of the dependency graph.
    ///
    /// @throws MojoExecutionException if an unexpected error occurs during execution.
    /// @throws MojoFailureException   if the execution fails due to inconsistent or invalid Maven project data.
    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException {
        Path executionRootDirectory = Path.of(session.getExecutionRootDirectory())
                .toAbsolutePath();
        getLog().debug("Execution root directory: %s".formatted(executionRootDirectory));
        List<MavenProject> projectsInScope = getProjectsInScope().toList();
        Map<MavenArtifact, String> projectArtifacts = projectsInScope.stream()
                .collect(Collectors.toMap(
                        Utils::mavenProjectToArtifact,
                        project -> getProjectFolderAsString(project, executionRootDirectory)
                ));

        Map<MavenArtifact, MavenProjectAndDocument> documents = readAllPoms(projectsInScope);
        Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifactMapping =
                createDependencyToProjectArtifactMapping(documents.values(), projectArtifacts.keySet());

        Map<MavenArtifact, List<MavenArtifact>> projectToDependenciesMapping = projectArtifacts.keySet()
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        artifact -> collectProjectDependencies(artifact, dependencyToProjectArtifactMapping)
                ));

        Map<MavenArtifact, DetailedGraphNode> graph = projectArtifacts.keySet()
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        project -> prepareMavenProjectNode(project, projectToDependenciesMapping, projectArtifacts)
                ));

        produceGraphOutput(graph);
    }

    /// Produces the output of the dependency graph for Maven artifacts based on the specified graph output type.
    /// The method transforms the given dependency graph into a format determined by the output configuration
    /// (artifact-only, folder-only, or a combination of both) and serializes the resulting data as JSON.
    /// The JSON output is either written to a file or printed to the console.
    ///
    /// @param graph a mapping of Maven artifacts to their corresponding detailed graph nodes, representing the dependency relationships between the artifacts
    /// @throws MojoExecutionException if an error occurs while transforming the graph or writing the output to a file
    private void produceGraphOutput(Map<MavenArtifact, DetailedGraphNode> graph) throws MojoExecutionException {
        Function<Object, String> mapper = Object::toString;
        Object output = switch (graphOutput) {
            case ARTIFACT_ONLY -> transformGraph(graph, mapper.compose(ArtifactLocation::artifact));
            case FOLDER_ONLY -> transformGraph(graph, ArtifactLocation::folder);
            case ARTIFACT_AND_FOLDER -> transformGraph(graph, Function.identity());
        };

        String objectAsJson = Utils.writeObjectAsJson(output);
        if (outputFile != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    outputFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                writer.write(objectAsJson);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to write to output file '%s'".formatted(outputFile), e);
            }
        } else {
            System.out.println(objectAsJson);
        }
    }

    /// Transforms a dependency graph of Maven artifacts into a new mapping where the dependencies
    /// of each artifact are processed using the provided mapping function.
    ///
    /// @param <T>    the target type of the transformation
    /// @param graph  a mapping of Maven artifacts to their detailed graph nodes, representing dependency relationships between artifacts
    /// @param mapper a function that maps artifact locations (dependencies) to the desired target type
    /// @return a transformed mapping of Maven artifacts to lists of processed dependencies where each dependency is mapped using the provided function
    private <T> Map<MavenArtifact, List<T>> transformGraph(
            Map<MavenArtifact, DetailedGraphNode> graph,
            Function<ArtifactLocation, T> mapper
    ) {
        return graph.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue()
                                .dependencies()
                                .stream()
                                .map(mapper)
                                .toList()
                ));
    }

    /// Resolves and collects all Maven artifacts that are dependent on the specified artifact within the provided
    /// dependency mapping, including transitive dependencies.
    ///
    /// This method performs a depth-first traversal to find all direct and transitive dependencies
    /// of the given artifact. The dependencies are returned in topological order (build order),
    /// where dependencies that need to be built first appear earlier in the list.
    ///
    /// @param artifact                           the Maven artifact whose dependents need to be collected
    /// @param dependencyToProjectArtifactMapping a mapping that associates project artifacts with their dependencies
    /// @return a list of Maven artifacts that depend on the specified artifact, sorted in build order
    private List<MavenArtifact> collectProjectDependencies(
            MavenArtifact artifact,
            Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifactMapping
    ) {
        Set<MavenArtifact> visited = new HashSet<>();
        List<MavenArtifact> result = new ArrayList<>();
        collectTransitiveDependencies(artifact, dependencyToProjectArtifactMapping, visited, result);
        return List.copyOf(result);
    }

    /// Recursively collects transitive dependencies using depth-first search.
    /// Dependencies are added to the result list in post-order (dependencies before dependents),
    /// which ensures topological ordering for build purposes.
    ///
    /// @param artifact                           the current artifact being processed
    /// @param dependencyToProjectArtifactMapping a mapping that associates project artifacts with their dependencies
    /// @param visited                            set of already visited artifacts to avoid cycles
    /// @param result                             list to accumulate dependencies in topological order
    private void collectTransitiveDependencies(
            MavenArtifact artifact,
            Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifactMapping,
            Set<MavenArtifact> visited,
            List<MavenArtifact> result
    ) {
        if (visited.contains(artifact)) {
            return;
        }
        visited.add(artifact);

        // Find all direct dependencies of this artifact
        List<MavenArtifact> directDependencies = dependencyToProjectArtifactMapping.entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(artifact))
                .map(Map.Entry::getKey)
                .toList();

        // Recursively process each direct dependency
        for (MavenArtifact dependency : directDependencies) {
            collectTransitiveDependencies(dependency, dependencyToProjectArtifactMapping, visited, result);
        }

        // Add the current artifact after its dependencies (post-order)
        result.add(artifact);
    }

    /// Returns the project folder path as a string.
    /// If relative paths are enabled, the path is returned relative to the specified execution root directory;
    /// otherwise, the absolute path of the project is returned.
    ///
    /// @param project                the [MavenProject] instance representing the current project
    /// @param executionRootDirectory the root directory of the current execution context
    /// @return the project folder path as a string, either relative to the execution root directory or as an absolute path
    private String getProjectFolderAsString(MavenProject project, Path executionRootDirectory) {
        Path projectBasePath = project.getBasedir().toPath().toAbsolutePath();
        if (!useRelativePaths) {
            return projectBasePath.toString();
        }
        String relativePath = executionRootDirectory.relativize(projectBasePath).toString();
        if (relativePath.isBlank()) {
            return ".";
        }
        return relativePath;
    }

    /// Prepares a `Node` representation of a Maven project artifact, including its associated
    /// folder path and its minimal dependencies as `MinDependency` instances.
    ///
    /// @param artifact                     the Maven artifact for which the `Node` is to be prepared; must not be null
    /// @param projectToDependenciesMapping a mapping of Maven projects to their respective dependencies; used to determine the dependencies of the provided artifact; must not be null
    /// @param projectArtifacts             a mapping of Maven artifacts to their corresponding folder paths; must not be null
    /// @return a `Node` instance representing the given artifact, its folder path, and its resolved dependencies
    private DetailedGraphNode prepareMavenProjectNode(
            MavenArtifact artifact,
            Map<MavenArtifact, List<MavenArtifact>> projectToDependenciesMapping,
            Map<MavenArtifact, String> projectArtifacts
    ) {
        List<ArtifactLocation> minDependencies = projectToDependenciesMapping.getOrDefault(artifact, List.of()).stream()
                .map(depArtifact -> new ArtifactLocation(depArtifact, projectArtifacts.get(depArtifact)))
                .toList();
        return new DetailedGraphNode(
                artifact,
                projectArtifacts.get(artifact),
                minDependencies
        );
    }
}
