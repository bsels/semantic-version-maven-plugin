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
import java.util.stream.Stream;

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
                .hasSize(3)
                .satisfiesExactly(
                        first -> assertThat(first)
                                .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                                .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                                .hasFieldOrPropertyWithValue(
                                        "message",
                                        Optional.of("Execution for project: org.example.itests.single:project:1.0.0")
                                ),
                        second -> assertThat(second)
                                .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.WARN)
                                .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                                .hasFieldOrPropertyWithValue(
                                        "message",
                                        Optional.of(
                                                "No versioning files found in %s as folder does not exists".formatted(
                                                        getResourcesPath("single", ".versioning")
                                                )
                                        )
                                ),
                        third -> assertThat(third)
                                .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.WARN)
                                .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                                .hasFieldOrPropertyWithValue(
                                        "message",
                                        Optional.of("No projects found in scope")
                                )
                );
    }

    private Path getResourcesPath(String... relativePaths) {
        return Stream.of(relativePaths)
                .reduce(getResourcesPath(), Path::resolve, (a, b) -> {
                    throw new UnsupportedOperationException();
                });
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
                    getResourcesPath("single"),
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

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "2.0.0";
                case MINOR -> "1.1.0";
                case PATCH -> "1.0.1";
            };
            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("single", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.single</groupId>
                                                <artifactId>project</artifactId>
                                                <version>%s</version>
                                            </project>
                                            """.formatted(expectedVersion)
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("single", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## %s - 2025-01-01
                                            
                                            ### Other
                                            
                                            Project version bumped as result of dependency bumps
                                            
                                            ## 1.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """.formatted(expectedVersion)
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
        }
    }
}
