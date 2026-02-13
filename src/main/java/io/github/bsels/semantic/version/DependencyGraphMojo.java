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
        System.out.println(executionRootDirectory);
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
                        artifact -> artifact,
                        artifact -> dependencyToProjectArtifactMapping.entrySet().stream()
                                .filter(entry -> entry.getValue().contains(artifact))
                                .map(Map.Entry::getKey)
                                .toList()
                ));

        Map<MavenArtifact, Node> graph = projectsInScope.stream()
                .collect(Collectors.toMap(
                        Utils::mavenProjectToArtifact,
                        project -> {
                            MavenArtifact artifact = Utils.mavenProjectToArtifact(project);
                            List<MinDependency> minDependencies = projectToDependenciesMapping.getOrDefault(artifact, List.of()).stream()
                                    .map(depArtifact -> new MinDependency(depArtifact, projectArtifacts.get(depArtifact)))
                                    .toList();
                            return new Node(
                                    artifact,
                                    projectArtifacts.get(artifact),
                                    minDependencies
                            );
                        }
                ));

        System.out.println(Utils.writeObjectAsJson(graph));
    }

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
