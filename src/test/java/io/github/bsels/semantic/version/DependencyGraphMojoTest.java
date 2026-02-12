package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.test.utils.ReadMockedMavenSession;
import io.github.bsels.semantic.version.test.utils.TestLog;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DependencyGraphMojoTest extends AbstractBaseMojoTest {

    @Spy
    private DependencyGraphMojo classUnderTest;

    @BeforeEach
    void setUp() {
        classUnderTest.setLog(new TestLog(TestLog.LogLevel.NONE));
    }

    @Test
    void internalExecute_calculatesCorrectDependencies() throws MojoExecutionException, MojoFailureException {
        // Arrange
        Path projectRoot = getResourcesPath("multi");
        classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
        
        ArgumentCaptor<Object> graphCaptor = ArgumentCaptor.forClass(Object.class);
        doNothing().when(classUnderTest).printDependencyGraph(graphCaptor.capture());

        // Act
        classUnderTest.internalExecute();

        // Assert
        Map<MavenArtifact, DependencyGraphMojo.Node> graph = (Map<MavenArtifact, DependencyGraphMojo.Node>) graphCaptor.getValue();
        assertThat(graph).isNotNull();

        MavenArtifact combinationArtifact = new MavenArtifact("org.example.itests.multi", "combination");
        assertThat(graph).containsKey(combinationArtifact);
        
        DependencyGraphMojo.Node combinationNode = graph.get(combinationArtifact);
        assertThat(combinationNode.dependencies()).contains(
                new MavenArtifact("org.example.itests.multi", "dependency"),
                new MavenArtifact("org.example.itests.multi", "plugin")
        );
        
        MavenArtifact dependencyArtifact = new MavenArtifact("org.example.itests.multi", "dependency");
        assertThat(graph).containsKey(dependencyArtifact);
        assertThat(graph.get(dependencyArtifact).dependencies()).isEmpty();
    }
}
