package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.test.utils.ReadMockedMavenSession;
import io.github.bsels.semantic.version.test.utils.TestLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(MockitoExtension.class)
public class UpdatePomMojoTest {
    private UpdatePomMojo classUnderTest;
    private TestLog testLog;

    @BeforeEach
    void setUp() {
        classUnderTest = new UpdatePomMojo();
        testLog = new TestLog(TestLog.LogLevel.NONE);
        classUnderTest.setLog(testLog);
    }

    @Test
    void noExecutionOnSubProjectIfDisabled() {
        classUnderTest.executeForSubproject = false; // Just to make explicit that this is the default value
        classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                getResourcesPath().resolve("leaves"),
                Path.of("child-1")
        );

        assertThatNoException()
                .isThrownBy(classUnderTest::execute);

        Mockito.verify(classUnderTest.session, Mockito.times(1))
                .getCurrentProject();
        Mockito.verify(classUnderTest.session, Mockito.times(1))
                .getTopLevelProject();
        Mockito.verifyNoMoreInteractions(classUnderTest.session);

        assertThat(testLog.getLogRecords())
                .hasSize(1)
                .first()
                .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                .hasFieldOrPropertyWithValue(
                        "message",
                        Optional.of("Skipping execution for subproject org.example.itests.leaves:child-1:5.0.0-child-1")
                );
    }

    private Path getResourcesPath() {
        try {
            return Path.of(
                    Objects.requireNonNull(UpdatePomMojoTest.class.getResource("/itests/"))
                            .toURI()
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
