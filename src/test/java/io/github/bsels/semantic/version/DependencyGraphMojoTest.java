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

        // Act
        classUnderTest.internalExecute();

        // Assert
        verify(classUnderTest).internalExecute();
    }
}
