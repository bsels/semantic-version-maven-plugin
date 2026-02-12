package io.github.bsels.semantic.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.stream.Collectors;

@Mojo(name = "graph", aggregator = true, requiresDependencyResolution = ResolutionScope.NONE)
@Execute(phase = LifecyclePhase.NONE)
public final class DependencyGraphMojo extends BaseMojo {

    @Parameter(property = "versioning.relativePaths", required = true, defaultValue = "true")
    boolean useRelativePaths = true;

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException {
        Path executionRootDirectory = Path.of(session.getExecutionRootDirectory());
        List<MavenProject> projectsInScope = getProjectsInScope().toList();
        Set<MavenArtifact> projectArtifacts = projectsInScope.stream()
                .map(Utils::mavenProjectToArtifact)
                .collect(Collectors.toSet());

        Map<MavenArtifact, MavenProjectAndDocument> documents = readAllPoms(projectsInScope);
        Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifactMapping =
                createDependencyToProjectArtifactMapping(documents.values(), projectArtifacts);

        Map<MavenArtifact, List<MavenArtifact>> projectToDependenciesMapping = projectArtifacts.stream()
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
                        project -> new Node(
                                Utils.mavenProjectToArtifact(project),
                                executionRootDirectory.relativize(project.getBasedir().toPath()),
                                projectToDependenciesMapping.getOrDefault(Utils.mavenProjectToArtifact(project), List.of())
                        )
                ));

        printDependencyGraph(graph);
    }

    void printDependencyGraph(Object graph) throws MojoExecutionException {
        try {
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(graph));
        } catch (JsonProcessingException e) {
            throw new MojoExecutionException("Failed to serialize dependency graph", e);
        }
    }

    public record Node(MavenArtifact artifact, Path folder, List<MavenArtifact> dependencies) {

        public Node {
            Objects.requireNonNull(artifact, "`artifact` must not be null");
            Objects.requireNonNull(folder, "`folder` must not be null");
            Objects.requireNonNull(dependencies, "`dependencies` must not be null");
        }
    }
}
