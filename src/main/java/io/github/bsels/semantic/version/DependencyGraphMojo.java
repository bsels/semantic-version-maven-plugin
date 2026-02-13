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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mojo(name = "graph", aggregator = true, requiresDependencyResolution = ResolutionScope.NONE)
@Execute(phase = LifecyclePhase.NONE)
public final class DependencyGraphMojo extends BaseMojo {

    @Parameter(property = "versioning.relativePaths", required = true, defaultValue = "true")
    boolean useRelativePaths = true;

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException {
        Path executionRootDirectory = Path.of(session.getExecutionRootDirectory())
                .toAbsolutePath();
        getLog().info("Execution root directory: %s".formatted(executionRootDirectory));
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

        System.out.println(Utils.writeObjectAsJson(graph));
    }

    /// Resolves and collects all Maven artifacts that are dependent on the specified artifact within the provided
    /// dependency mapping.
    ///
    /// This method filters through the `dependencyToProjectArtifactMapping` to find all entries
    /// where the given `artifact` is present in the list of dependencies,
    /// and then retrieves the corresponding project artifacts as a result.
    ///
    /// @param artifact                           the Maven artifact whose dependents need to be collected
    /// @param dependencyToProjectArtifactMapping a mapping that associates project artifacts with their dependencies
    /// @return a list of Maven artifacts that depend on the specified artifact
    private List<MavenArtifact> collectProjectDependencies(
            MavenArtifact artifact,
            Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifactMapping
    ) {
        return dependencyToProjectArtifactMapping.entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(artifact))
                .map(Map.Entry::getKey)
                .toList();
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
