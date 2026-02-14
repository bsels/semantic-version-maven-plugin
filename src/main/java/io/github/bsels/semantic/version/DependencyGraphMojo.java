package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.MavenProjectAndDocument;
import io.github.bsels.semantic.version.utils.Utils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        Map<MavenArtifact, Node> graph = projectArtifacts.keySet()
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        project -> prepareMavenProjectNode(project, projectToDependenciesMapping, projectArtifacts)
                ));

        produceGraphOutput(graph);
    }

    private void produceGraphOutput(Map<MavenArtifact, Node> graph) throws MojoExecutionException {
        // TODO: Enhance with different outputs and switching between console and file output
        System.out.println(Utils.writeObjectAsJson(graph));
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
    private Node prepareMavenProjectNode(
            MavenArtifact artifact,
            Map<MavenArtifact, List<MavenArtifact>> projectToDependenciesMapping,
            Map<MavenArtifact, String> projectArtifacts
    ) {
        List<MinDependency> minDependencies = projectToDependenciesMapping.getOrDefault(artifact, List.of()).stream()
                .map(depArtifact -> new MinDependency(depArtifact, projectArtifacts.get(depArtifact)))
                .toList();
        return new Node(
                artifact,
                projectArtifacts.get(artifact),
                minDependencies
        );
    }

    public record MinDependency(MavenArtifact artifact, String folder) {
        public MinDependency {
            Objects.requireNonNull(artifact, "`artifact` must not be null");
            Objects.requireNonNull(folder, "`folder` must not be null");
        }
    }

    public record Node(MavenArtifact artifact, String folder, List<MinDependency> dependencies) {

        public Node {
            Objects.requireNonNull(artifact, "`artifact` must not be null");
            Objects.requireNonNull(folder, "`folder` must not be null");
            Objects.requireNonNull(dependencies, "`dependencies` must not be null");
        }
    }
}
