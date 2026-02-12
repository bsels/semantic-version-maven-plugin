package io.github.bsels.semantic.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.utils.Utils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.nio.file.Path;
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
        Path executionRootDirectory = Path.of(session.getExecutionRootDirectory());
        Map<MavenArtifact, Node> graph = getProjectsInScope()
                .collect(Collectors.toMap(
                        Utils::mavenProjectToArtifact,
                        project -> new Node(
                                Utils.mavenProjectToArtifact(project),
                                executionRootDirectory.relativize(project.getBasedir().toPath())
                        )
                ));

        printDependencyGraph(null);
    }

    private void printDependencyGraph(Object graph) throws MojoExecutionException {
        try {
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(graph));
        } catch (JsonProcessingException e) {
            throw new MojoExecutionException("Failed to serialize dependency graph", e);
        }
    }

    public record Node(MavenArtifact artifact, Path folder) {

        public Node {
            Objects.requireNonNull(artifact, "`artifact` must not be null");
            Objects.requireNonNull(folder, "`folder` must not be null");
        }
    }
}
