package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.parameters.Modus;
import io.github.bsels.semantic.version.parameters.VersionBump;
import io.github.bsels.semantic.version.test.utils.ReadMockedMavenSession;
import io.github.bsels.semantic.version.test.utils.TestLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(MockitoExtension.class)
public class UpdatePomMojoTest {
    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
    private UpdatePomMojo classUnderTest;
    private TestLog testLog;
    private Map<Path, StringWriter> mockedOutputFiles;
    private List<CopyPath> mockedCopiedFiles;

    private MockedStatic<Files> filesMockedStatic;
    private MockedStatic<LocalDate> localDateMockedStatic;

    @BeforeEach
    void setUp() {
        classUnderTest = new UpdatePomMojo();
        testLog = new TestLog(TestLog.LogLevel.NONE);
        classUnderTest.setLog(testLog);
        mockedOutputFiles = new HashMap<>();
        mockedCopiedFiles = new ArrayList<>();

        filesMockedStatic = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
        filesMockedStatic.when(() -> Files.newBufferedWriter(Mockito.any(), Mockito.any(), Mockito.any(OpenOption[].class)))
                .thenAnswer(answer -> {
                    Path path = answer.getArgument(0);
                    mockedOutputFiles.put(path, new StringWriter());
                    return new BufferedWriter(mockedOutputFiles.get(path));
                });
        filesMockedStatic.when(() -> Files.copy(Mockito.any(Path.class), Mockito.any(), Mockito.any(CopyOption[].class)))
                .thenAnswer(answer -> {
                    Path original = answer.getArgument(0);
                    Path copy = answer.getArgument(1);
                    mockedCopiedFiles.add(new CopyPath(original, copy, List.of(answer.getArgument(2))));
                    return copy;
                });

        localDateMockedStatic = Mockito.mockStatic(LocalDate.class);
        localDateMockedStatic.when(LocalDate::now)
                .thenReturn(DATE);
    }

    @AfterEach
    void tearDown() {
        filesMockedStatic.close();
        localDateMockedStatic.close();
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

    private record CopyPath(Path original, Path copy, List<CopyOption> options) {
    }

    @Nested
    class SingleProjectTest {

        @BeforeEach
        void setUp() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath().resolve("single"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.PROJECT_VERSION;
        }


        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBump_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .first()
                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                    .hasFieldOrPropertyWithValue(
                            "message",
                            Optional.of("Execution for project: org.example.itests.single:project:1.0.0")
                    );

            // TODO: Verify
        }
    }
}
