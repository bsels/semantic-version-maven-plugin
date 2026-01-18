package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.parameters.Modus;
import io.github.bsels.semantic.version.test.utils.ReadMockedMavenSession;
import io.github.bsels.semantic.version.test.utils.TestLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class CreateVersionMarkdownMojoTest extends AbstractBaseMojoTest {
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2023, 1, 1, 12, 0, 8);
    private CreateVersionMarkdownMojo classUnderTest;
    private TestLog testLog;
    private Map<Path, StringWriter> mockedOutputFiles;
    private Set<Path> mockedCreatedDirectories;

    private MockedStatic<Files> filesMockedStatic;
    private MockedStatic<LocalDateTime> localDateTimeMockedStatic;

    @BeforeEach
    public void setUp() {
        classUnderTest = new CreateVersionMarkdownMojo();
        testLog = new TestLog(TestLog.LogLevel.DEBUG);
        classUnderTest.setLog(testLog);

        mockedOutputFiles = new HashMap<>();
        mockedCreatedDirectories = new HashSet<>();

        filesMockedStatic = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
        filesMockedStatic.when(() -> Files.newBufferedWriter(Mockito.any(), Mockito.any(), Mockito.any(OpenOption[].class)))
                .thenAnswer(answer -> {
                    Path path = answer.getArgument(0);
                    mockedOutputFiles.put(path, new StringWriter());
                    return new BufferedWriter(mockedOutputFiles.get(path));
                });
        filesMockedStatic.when(() -> Files.createDirectories(Mockito.any()))
                .thenAnswer(answer -> mockedCreatedDirectories.add(answer.getArgument(0)));

        localDateTimeMockedStatic = Mockito.mockStatic(LocalDateTime.class);
        localDateTimeMockedStatic.when(LocalDateTime::now)
                .thenReturn(DATE_TIME);
    }

    @AfterEach
    public void tearDown() {
        filesMockedStatic.close();
        localDateTimeMockedStatic.close();
    }

    @Test
    void noExecutionOnSubProjectIfDisabled() {
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
                .satisfiesExactly(validateLogRecordInfo(
                        "Skipping execution for subproject org.example.itests.leaves:child-1:5.0.0-child-1"
                ));

        assertThat(mockedOutputFiles)
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Modus.class, names = {"PROJECT_VERSION", "PROJECT_VERSION_ONLY_LEAFS"})
    void noProjectsInScope_LogsWarning(Modus modus) {
        classUnderTest.session = ReadMockedMavenSession.readMockedMavenSessionNoTopologicalSortedProjects(
                getResourcesPath("single"),
                Path.of(".")
        );
        classUnderTest.modus = modus;

        assertThatNoException()
                .isThrownBy(classUnderTest::execute);

        assertThat(testLog.getLogRecords())
                .isNotEmpty()
                .hasSize(2)
                .satisfiesExactly(
                        validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                        validateLogRecordWarn("No projects found in scope")
                );

        assertThat(mockedOutputFiles)
                .isEmpty();
    }


}
