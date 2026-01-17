package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.parameters.Modus;
import io.github.bsels.semantic.version.parameters.VersionBump;
import io.github.bsels.semantic.version.test.utils.ReadMockedMavenSession;
import io.github.bsels.semantic.version.test.utils.TestLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class UpdatePomMojoTest {
    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
    private UpdatePomMojo classUnderTest;
    private TestLog testLog;
    private Map<Path, StringWriter> mockedOutputFiles;
    private List<CopyPath> mockedCopiedFiles;
    private List<Path> mockedDeletedFiles;

    private MockedStatic<Files> filesMockedStatic;
    private MockedStatic<LocalDate> localDateMockedStatic;

    @BeforeEach
    void setUp() {
        classUnderTest = new UpdatePomMojo();
        testLog = new TestLog(TestLog.LogLevel.NONE);
        classUnderTest.setLog(testLog);
        mockedOutputFiles = new HashMap<>();
        mockedCopiedFiles = new ArrayList<>();
        mockedDeletedFiles = new ArrayList<>();

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
                    List<CopyOption> options = IntStream.range(2, answer.getArguments().length)
                            .<CopyOption>mapToObj(answer::getArgument)
                            .toList();
                    mockedCopiedFiles.add(new CopyPath(original, copy, options));
                    return copy;
                });
        filesMockedStatic.when(() -> Files.deleteIfExists(Mockito.any(Path.class)))
                .thenAnswer(answer -> mockedDeletedFiles.add(answer.getArgument(0)));
        filesMockedStatic.when(() -> Files.delete(Mockito.any(Path.class)))
                .thenAnswer(answer -> mockedDeletedFiles.add(answer.getArgument(0)));

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
        assertThat(mockedCopiedFiles)
                .isEmpty();
        assertThat(mockedDeletedFiles)
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
                .hasSize(3)
                .satisfiesExactly(
                        validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                        validateLogRecordWarn(
                                "No versioning files found in %s as folder does not exists".formatted(
                                        getResourcesPath("single", ".versioning")
                                )
                        ),
                        validateLogRecordWarn("No projects found in scope")
                );

        assertThat(mockedOutputFiles)
                .isEmpty();
        assertThat(mockedCopiedFiles)
                .isEmpty();
        assertThat(mockedDeletedFiles)
                .isEmpty();
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

    private Consumer<TestLog.LogRecord> validateLogRecordDebug(String message) {
        return validateLogRecord(TestLog.LogLevel.DEBUG, message);
    }

    private Consumer<TestLog.LogRecord> validateLogRecordInfo(String message) {
        return validateLogRecord(TestLog.LogLevel.INFO, message);
    }

    private Consumer<TestLog.LogRecord> validateLogRecordWarn(String message) {
        return validateLogRecord(TestLog.LogLevel.WARN, message);
    }

    private Consumer<TestLog.LogRecord> validateLogRecord(TestLog.LogLevel level, String message) {
        return record -> assertThat(record)
                .hasFieldOrPropertyWithValue("level", level)
                .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                .hasFieldOrPropertyWithValue("message", Optional.of(message));
    }


    private record CopyPath(Path original, Path copy, List<CopyOption> options) {
    }

    @Nested
    class LeavesProjectTest {

        @BeforeEach
        void setUp() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("leaves"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.PROJECT_VERSION_ONLY_LEAFS;
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBump_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(19)
                    .satisfiesExactlyInAnyOrder(
                            validateLogRecordInfo("Execution for project: org.example.itests.leaves:root:5.0.0-root"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("leaves", ".versioning")
                            )),
                            validateLogRecordInfo("Multiple projects in scope"),
                            validateLogRecordInfo("Found 3 projects in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "child-1", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "intermediate", "child-2", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "intermediate", "child-3", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-1"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-2"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-3")
                    );

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "6.0.0";
                case MINOR -> "5.1.0";
                case PATCH -> "5.0.1";
            };
            assertThat(mockedOutputFiles)
                    .hasSize(6);
            for (int i = 0; i < 3; i++) {
                final int index = i + 1;
                Path path;
                if (i == 0) {
                    path = getResourcesPath("leaves", "child-%d".formatted(index));
                } else {
                    path = getResourcesPath("leaves", "intermediate", "child-%d".formatted(index));
                }
                assertThat(mockedOutputFiles)
                        .hasEntrySatisfying(
                                path.resolve("pom.xml"),
                                writer -> assertThat(writer.toString())
                                        .isEqualToIgnoringNewLines("""
                                                <?xml version="1.0" encoding="UTF-8"?>
                                                <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                                xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                                http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                    <modelVersion>4.0.0</modelVersion>
                                                    <groupId>org.example.itests.leaves</groupId>
                                                    <artifactId>child-%1$d</artifactId>
                                                    <version>%2$s-child-%1$d</version>
                                                </project>
                                                """.formatted(index, expectedVersion)
                                        )
                        )
                        .hasEntrySatisfying(
                                path.resolve("CHANGELOG.md"),
                                writer -> assertThat(writer.toString())
                                        .isEqualToIgnoringNewLines("""
                                                # Changelog
                                                
                                                ## %2$s-child-%1$d - 2025-01-01
                                                
                                                ### Other
                                                
                                                Project version bumped as result of dependency bumps
                                                
                                                ## 5.0.0-child-%1$d - 2026-01-01
                                                
                                                Initial child %1$d release.
                                                """.formatted(index, expectedVersion)
                                        )
                        );
            }
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void noSemanticVersionBumpFileBased_NothingChanged() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "leaves", "none");


            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(12)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.leaves:root:5.0.0-root"),
                            validateLogRecordInfo("Read 7 lines from %s".formatted(
                                    getResourcesPath("versioning", "leaves", "none", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.leaves:child-1': none
                                        'org.example.itests.leaves:child-2': none
                                        'org.example.itests.leaves:child-3': none\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.leaves:child-2=NONE, org.example.itests.leaves:child-1=NONE, \
                                    org.example.itests.leaves:child-3=NONE}\
                                    """),
                            validateLogRecordInfo("Multiple projects in scope"),
                            validateLogRecordInfo("Found 3 projects in scope"),
                            validateLogRecordInfo("Updating version with a NONE semantic version"),
                            validateLogRecordInfo("No version update required"),
                            validateLogRecordInfo("Updating version with a NONE semantic version"),
                            validateLogRecordInfo("No version update required"),
                            validateLogRecordInfo("Updating version with a NONE semantic version"),
                            validateLogRecordInfo("No version update required")
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void singleFileBased_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "leaves", "single");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(21)
                    .satisfiesExactlyInAnyOrder(
                            validateLogRecordInfo("Execution for project: org.example.itests.leaves:root:5.0.0-root"),
                            validateLogRecordInfo("Read 7 lines from %s".formatted(
                                    getResourcesPath("versioning", "leaves", "single", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.leaves:child-1': patch
                                        'org.example.itests.leaves:child-2': minor
                                        'org.example.itests.leaves:child-3': major\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.leaves:child-2=MINOR, org.example.itests.leaves:child-1=PATCH, \
                                    org.example.itests.leaves:child-3=MAJOR}\
                                    """),
                            validateLogRecordInfo("Multiple projects in scope"),
                            validateLogRecordInfo("Found 3 projects in scope"),
                            validateLogRecordInfo("Updating version with a PATCH semantic version"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "child-1", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("Updating version with a MINOR semantic version"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "intermediate", "child-2", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("Updating version with a MAJOR semantic version"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "intermediate", "child-3", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-1"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-2"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-3")
                    );

            assertThat(mockedOutputFiles)
                    .hasSize(6)
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "child-1", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.leaves</groupId>
                                                <artifactId>child-1</artifactId>
                                                <version>5.0.1-child-1</version>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "intermediate", "child-2", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.leaves</groupId>
                                                <artifactId>child-2</artifactId>
                                                <version>5.1.0-child-2</version>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "intermediate", "child-3", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.leaves</groupId>
                                                <artifactId>child-3</artifactId>
                                                <version>6.0.0-child-3</version>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "child-1", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 5.0.1-child-1 - 2025-01-01
                                            
                                            ### Patch
                                            
                                            Different versions bump in different modules.
                                            
                                            ## 5.0.0-child-1 - 2026-01-01
                                            
                                            Initial child 1 release.
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "intermediate", "child-2", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 5.1.0-child-2 - 2025-01-01
                                            
                                            ### Minor
                                            
                                            Different versions bump in different modules.
                                            
                                            ## 5.0.0-child-2 - 2026-01-01
                                            
                                            Initial child 2 release.
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "intermediate", "child-3", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 6.0.0-child-3 - 2025-01-01
                                            
                                            ### Major
                                            
                                            Different versions bump in different modules.
                                            
                                            ## 5.0.0-child-3 - 2026-01-01
                                            
                                            Initial child 3 release.
                                            """
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();

            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactly(getResourcesPath("versioning", "leaves", "single", "versioning.md"));
        }

        @Test
        void multiFileBased_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "leaves", "multi");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(27)
                    .satisfiesExactlyInAnyOrder(
                            validateLogRecordInfo("Execution for project: org.example.itests.leaves:root:5.0.0-root"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "leaves", "multi", "child-1.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.leaves:child-1': patch\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.leaves:child-1=PATCH}\
                                    """),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "leaves", "multi", "child-2.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.leaves:child-2': minor\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.leaves:child-2=MINOR}\
                                    """),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "leaves", "multi", "child-3.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.leaves:child-3': major\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.leaves:child-3=MAJOR}\
                                    """),
                            validateLogRecordInfo("Multiple projects in scope"),
                            validateLogRecordInfo("Found 3 projects in scope"),
                            validateLogRecordInfo("Updating version with a PATCH semantic version"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "child-1", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("Updating version with a MINOR semantic version"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "intermediate", "child-2", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("Updating version with a MAJOR semantic version"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("leaves", "intermediate", "child-3", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-1"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-2"),
                            validateLogRecordDebug("Updating project org.example.itests.leaves:child-3")
                    );

            assertThat(mockedOutputFiles)
                    .hasSize(6)
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "child-1", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.leaves</groupId>
                                                <artifactId>child-1</artifactId>
                                                <version>5.0.1-child-1</version>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "intermediate", "child-2", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.leaves</groupId>
                                                <artifactId>child-2</artifactId>
                                                <version>5.1.0-child-2</version>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "intermediate", "child-3", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.leaves</groupId>
                                                <artifactId>child-3</artifactId>
                                                <version>6.0.0-child-3</version>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "child-1", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 5.0.1-child-1 - 2025-01-01
                                            
                                            ### Patch
                                            
                                            Child 1 = Patch
                                            
                                            ## 5.0.0-child-1 - 2026-01-01
                                            
                                            Initial child 1 release.
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "intermediate", "child-2", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 5.1.0-child-2 - 2025-01-01
                                            
                                            ### Minor
                                            
                                            Child 2 = Minor
                                            
                                            ## 5.0.0-child-2 - 2026-01-01
                                            
                                            Initial child 2 release.
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("leaves", "intermediate", "child-3", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 6.0.0-child-3 - 2025-01-01
                                            
                                            ### Major
                                            
                                            Child 3 = Major
                                            
                                            ## 5.0.0-child-3 - 2026-01-01
                                            
                                            Initial child 3 release.
                                            """
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();

            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(3)
                    .containsExactlyInAnyOrder(
                            getResourcesPath("versioning", "leaves", "multi", "child-1.md"),
                            getResourcesPath("versioning", "leaves", "multi", "child-2.md"),
                            getResourcesPath("versioning", "leaves", "multi", "child-3.md")
                    );
        }

    }

    @Nested
    class RevisionMultiProjectTest {

        @BeforeEach
        void setUp() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("revision", "multi"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.REVISION_PROPERTY;
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBump_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("revision", "multi", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "multi", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "4.0.0";
                case MINOR -> "3.1.0";
                case PATCH -> "3.0.1";
            };
            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "multi", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.revision.multi</groupId>
                                                <artifactId>parent</artifactId>
                                                <version>${revision}</version>
                                            
                                                <properties>
                                                    <revision>%s</revision>
                                                </properties>
                                            
                                                <modules>
                                                    <module>child1</module>
                                                    <module>child2</module>
                                                </modules>
                                            </project>
                                            """.formatted(expectedVersion)
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "multi", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## %s - 2025-01-01
                                            
                                            ### Other
                                            
                                            Project version bumped as result of dependency bumps
                                            
                                            ## 3.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """.formatted(expectedVersion)
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBumpWithBackup_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.backupFiles = true;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("revision", "multi", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "multi", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "4.0.0";
                case MINOR -> "3.1.0";
                case PATCH -> "3.0.1";
            };
            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "multi", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.revision.multi</groupId>
                                                <artifactId>parent</artifactId>
                                                <version>${revision}</version>
                                            
                                                <properties>
                                                    <revision>%s</revision>
                                                </properties>
                                            
                                                <modules>
                                                    <module>child1</module>
                                                    <module>child2</module>
                                                </modules>
                                            </project>
                                            """.formatted(expectedVersion)
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "multi", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## %s - 2025-01-01
                                            
                                            ### Other
                                            
                                            Project version bumped as result of dependency bumps
                                            
                                            ## 3.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """.formatted(expectedVersion)
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isNotEmpty()
                    .hasSize(2)
                    .containsExactlyInAnyOrder(
                            new CopyPath(
                                    getResourcesPath("revision", "multi", "pom.xml"),
                                    getResourcesPath("revision", "multi", "pom.xml.backup"),
                                    List.of(
                                            StandardCopyOption.ATOMIC_MOVE,
                                            StandardCopyOption.COPY_ATTRIBUTES,
                                            StandardCopyOption.REPLACE_EXISTING
                                    )
                            ),
                            new CopyPath(
                                    getResourcesPath("revision", "multi", "CHANGELOG.md"),
                                    getResourcesPath("revision", "multi", "CHANGELOG.md.backup"),
                                    List.of(
                                            StandardCopyOption.ATOMIC_MOVE,
                                            StandardCopyOption.COPY_ATTRIBUTES,
                                            StandardCopyOption.REPLACE_EXISTING
                                    )
                            )
                    );
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBumpDryRun_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.dryRun = true;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "4.0.0";
                case MINOR -> "3.1.0";
                case PATCH -> "3.0.1";
            };

            assertThat(testLog.getLogRecords())
                    .hasSize(9)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("revision", "multi", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("""
                                    Dry-run: new pom at %s:
                                    <?xml version="1.0" encoding="UTF-8"?>
                                    <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                    http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                        <modelVersion>4.0.0</modelVersion>
                                        <groupId>org.example.itests.revision.multi</groupId>
                                        <artifactId>parent</artifactId>
                                        <version>${revision}</version>
                                    
                                        <properties>
                                            <revision>%s</revision>
                                        </properties>
                                    
                                        <modules>
                                            <module>child1</module>
                                            <module>child2</module>
                                        </modules>
                                    </project>\
                                    """.formatted(getResourcesPath("revision", "multi", "pom.xml"), expectedVersion)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "multi", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("""
                                    Dry-run: new changelog at %s:
                                    # Changelog
                                    
                                    ## %s - 2025-01-01
                                    
                                    ### Other
                                    
                                    Project version bumped as result of dependency bumps
                                    
                                    ## 3.0.0 - 2026-01-01
                                    
                                    Initial release.
                                    """.formatted(getResourcesPath("revision", "multi", "CHANGELOG.md"), expectedVersion))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void dryRunStringWriteCloseFailure_ThrowMojoExecutionException(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.dryRun = true;

            IOException ioException = new IOException("Unable to open output stream for writing");
            try (MockedConstruction<StringWriter> ignored = Mockito.mockConstruction(
                    StringWriter.class,
                    (mock, context) -> {
                        Mockito.doThrow(ioException).when(mock).close();
                        Mockito.when(mock.toString()).thenReturn("Mock for StringWriter, hashCode: 0");
                    }
            )) {
                assertThatThrownBy(classUnderTest::execute)
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to open output stream for writing")
                        .hasRootCause(ioException);
            }

            assertThat(testLog.getLogRecords())
                    .hasSize(5)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("revision", "multi", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("""
                                    Dry-run: new pom at %s:
                                    Mock for StringWriter, hashCode: 0\
                                    """.formatted(getResourcesPath("revision", "multi", "pom.xml")))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void filedBasedWalkFailed_ThrowMojoExecutionException() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "multi", "unknown-project");
            filesMockedStatic.when(() -> Files.walk(Mockito.any(Path.class), Mockito.eq(1)))
                    .thenThrow(IOException.class);

            assertThatThrownBy(classUnderTest::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Unable to read versioning folder")
                    .hasRootCauseInstanceOf(IOException.class);

            assertThat(testLog.getLogRecords())
                    .hasSize(1)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0")
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void unknownProjectFileBased_ThrowMojoFailureException() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "multi", "unknown-project");


            assertThatThrownBy(classUnderTest::execute)
                    .isInstanceOf(MojoFailureException.class)
                    .hasMessage("""
                            The following artifacts in the Markdown files are not present in the project scope: \
                            org.example.itests.single:unknown-project\
                            """);

            assertThat(testLog.getLogRecords())
                    .hasSize(4)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "multi", "unknown-project", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:unknown-project': major\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:unknown-project=%s}\
                                    """.formatted(SemanticVersionBump.MAJOR))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void noSemanticVersionBumpFileBased_NothingChanged() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "multi", "none");


            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "multi", "none", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.multi:parent': none\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.multi:parent=%s}\
                                    """.formatted(SemanticVersionBump.NONE)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(SemanticVersionBump.NONE)
                            ),
                            validateLogRecordInfo("No version update required")
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
                "major,Major,4.0.0",
                "minor,Minor,3.1.0",
                "patch,Patch,3.0.1"
        })
        void singleSemanticVersionBumFile_Valid(String folder, String title, String expectedVersion) {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "multi", folder);


            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            SemanticVersionBump semanticVersionBump = SemanticVersionBump.fromString(folder);
            assertThat(testLog.getLogRecords())
                    .hasSize(9)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "multi", folder, "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.multi:parent': %s\
                                    """.formatted(folder)),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.multi:parent=%s}\
                                    """.formatted(semanticVersionBump)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(semanticVersionBump)
                            ),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "multi", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "multi", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.revision.multi</groupId>
                                                <artifactId>parent</artifactId>
                                                <version>${revision}</version>
                                            
                                                <properties>
                                                    <revision>%s</revision>
                                                </properties>
                                            
                                                <modules>
                                                    <module>child1</module>
                                                    <module>child2</module>
                                                </modules>
                                            </project>
                                            """.formatted(expectedVersion)
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "multi", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## %1$s - 2025-01-01
                                            
                                            ### %2$s
                                            
                                            %2$s versioning applied.
                                            
                                            ## 3.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """.formatted(expectedVersion, title)
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactlyInAnyOrder(
                            getResourcesPath("versioning", "revision", "multi", folder, "versioning.md")
                    );
        }

        @Test
        void multipleSemanticVersionBumpFiles_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "multi", "multiple");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(18)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.multi:parent:3.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "multi", "multiple", "major.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.multi:parent': major\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.multi:parent=%s}\
                                    """.formatted(SemanticVersionBump.MAJOR)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "multi", "multiple", "minor.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.multi:parent': minor\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.multi:parent=%s}\
                                    """.formatted(SemanticVersionBump.MINOR)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "multi", "multiple", "none.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.multi:parent': none\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.multi:parent=%s}\
                                    """.formatted(SemanticVersionBump.NONE)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "multi", "multiple", "patch.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.multi:parent': patch\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.multi:parent=%s}\
                                    """.formatted(SemanticVersionBump.PATCH)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(SemanticVersionBump.MAJOR)
                            ),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "multi", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "multi", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.revision.multi</groupId>
                                                <artifactId>parent</artifactId>
                                                <version>${revision}</version>
                                            
                                                <properties>
                                                    <revision>4.0.0</revision>
                                                </properties>
                                            
                                                <modules>
                                                    <module>child1</module>
                                                    <module>child2</module>
                                                </modules>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "multi", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 4.0.0 - 2025-01-01
                                            
                                            ### Major
                                            
                                            Major versioning applied.
                                            
                                            ### Minor
                                            
                                            Minor versioning applied.
                                            
                                            ### Patch
                                            
                                            Patch versioning applied.
                                            
                                            ### Other
                                            
                                            No versioning applied.
                                            
                                            ## 3.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(4)
                    .containsExactlyInAnyOrder(
                            getResourcesPath("versioning", "revision", "multi", "multiple", "major.md"),
                            getResourcesPath("versioning", "revision", "multi", "multiple", "minor.md"),
                            getResourcesPath("versioning", "revision", "multi", "multiple", "patch.md"),
                            getResourcesPath("versioning", "revision", "multi", "multiple", "none.md")
                    );
        }
    }

    @Nested
    class RevisionSingleProjectTest {

        @BeforeEach
        void setUp() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("revision", "single"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.REVISION_PROPERTY;
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBump_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("revision", "single", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "3.0.0";
                case MINOR -> "2.1.0";
                case PATCH -> "2.0.1";
            };
            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "single", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.revision.single</groupId>
                                                <artifactId>project</artifactId>
                                                <version>${revision}</version>
                                            
                                                <properties>
                                                    <revision>%s</revision>
                                                </properties>
                                            </project>
                                            """.formatted(expectedVersion)
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "single", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## %s - 2025-01-01
                                            
                                            ### Other
                                            
                                            Project version bumped as result of dependency bumps
                                            
                                            ## 2.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """.formatted(expectedVersion)
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBumpWithBackup_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.backupFiles = true;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("revision", "single", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "3.0.0";
                case MINOR -> "2.1.0";
                case PATCH -> "2.0.1";
            };
            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "single", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.revision.single</groupId>
                                                <artifactId>project</artifactId>
                                                <version>${revision}</version>
                                            
                                                <properties>
                                                    <revision>%s</revision>
                                                </properties>
                                            </project>
                                            """.formatted(expectedVersion)
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "single", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## %s - 2025-01-01
                                            
                                            ### Other
                                            
                                            Project version bumped as result of dependency bumps
                                            
                                            ## 2.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """.formatted(expectedVersion)
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isNotEmpty()
                    .hasSize(2)
                    .containsExactlyInAnyOrder(
                            new CopyPath(
                                    getResourcesPath("revision", "single", "pom.xml"),
                                    getResourcesPath("revision", "single", "pom.xml.backup"),
                                    List.of(
                                            StandardCopyOption.ATOMIC_MOVE,
                                            StandardCopyOption.COPY_ATTRIBUTES,
                                            StandardCopyOption.REPLACE_EXISTING
                                    )
                            ),
                            new CopyPath(
                                    getResourcesPath("revision", "single", "CHANGELOG.md"),
                                    getResourcesPath("revision", "single", "CHANGELOG.md.backup"),
                                    List.of(
                                            StandardCopyOption.ATOMIC_MOVE,
                                            StandardCopyOption.COPY_ATTRIBUTES,
                                            StandardCopyOption.REPLACE_EXISTING
                                    )
                            )
                    );
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBumpDryRun_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.dryRun = true;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "3.0.0";
                case MINOR -> "2.1.0";
                case PATCH -> "2.0.1";
            };

            assertThat(testLog.getLogRecords())
                    .hasSize(9)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("revision", "single", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("""
                                    Dry-run: new pom at %s:
                                    <?xml version="1.0" encoding="UTF-8"?>
                                    <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                    http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                        <modelVersion>4.0.0</modelVersion>
                                        <groupId>org.example.itests.revision.single</groupId>
                                        <artifactId>project</artifactId>
                                        <version>${revision}</version>
                                    
                                        <properties>
                                            <revision>%s</revision>
                                        </properties>
                                    </project>\
                                    """.formatted(getResourcesPath("revision", "single", "pom.xml"), expectedVersion)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("""
                                    Dry-run: new changelog at %s:
                                    # Changelog
                                    
                                    ## %s - 2025-01-01
                                    
                                    ### Other
                                    
                                    Project version bumped as result of dependency bumps
                                    
                                    ## 2.0.0 - 2026-01-01
                                    
                                    Initial release.
                                    """.formatted(getResourcesPath("revision", "single", "CHANGELOG.md"), expectedVersion))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void dryRunStringWriteCloseFailure_ThrowMojoExecutionException(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.dryRun = true;

            IOException ioException = new IOException("Unable to open output stream for writing");
            try (MockedConstruction<StringWriter> ignored = Mockito.mockConstruction(
                    StringWriter.class,
                    (mock, context) -> {
                        Mockito.doThrow(ioException).when(mock).close();
                        Mockito.when(mock.toString()).thenReturn("Mock for StringWriter, hashCode: 0");
                    }
            )) {
                assertThatThrownBy(classUnderTest::execute)
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to open output stream for writing")
                        .hasRootCause(ioException);
            }

            assertThat(testLog.getLogRecords())
                    .hasSize(5)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("revision", "single", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("""
                                    Dry-run: new pom at %s:
                                    Mock for StringWriter, hashCode: 0\
                                    """.formatted(getResourcesPath("revision", "single", "pom.xml")))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void filedBasedWalkFailed_ThrowMojoExecutionException() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "single", "unknown-project");
            filesMockedStatic.when(() -> Files.walk(Mockito.any(Path.class), Mockito.eq(1)))
                    .thenThrow(IOException.class);

            assertThatThrownBy(classUnderTest::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Unable to read versioning folder")
                    .hasRootCauseInstanceOf(IOException.class);

            assertThat(testLog.getLogRecords())
                    .hasSize(1)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0")
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void unknownProjectFileBased_ThrowMojoFailureException() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "single", "unknown-project");


            assertThatThrownBy(classUnderTest::execute)
                    .isInstanceOf(MojoFailureException.class)
                    .hasMessage("""
                            The following artifacts in the Markdown files are not present in the project scope: \
                            org.example.itests.single:unknown-project\
                            """);

            assertThat(testLog.getLogRecords())
                    .hasSize(4)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "single", "unknown-project", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:unknown-project': major\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:unknown-project=%s}\
                                    """.formatted(SemanticVersionBump.MAJOR))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void noSemanticVersionBumpFileBased_NothingChanged() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "single", "none");


            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "single", "none", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.single:project': none\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.single:project=%s}\
                                    """.formatted(SemanticVersionBump.NONE)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(SemanticVersionBump.NONE)
                            ),
                            validateLogRecordInfo("No version update required")
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
                "major,Major,3.0.0",
                "minor,Minor,2.1.0",
                "patch,Patch,2.0.1"
        })
        void singleSemanticVersionBumFile_Valid(String folder, String title, String expectedVersion) {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "single", folder);


            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            SemanticVersionBump semanticVersionBump = SemanticVersionBump.fromString(folder);
            assertThat(testLog.getLogRecords())
                    .hasSize(9)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "single", folder, "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.single:project': %s\
                                    """.formatted(folder)),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.single:project=%s}\
                                    """.formatted(semanticVersionBump)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(semanticVersionBump)
                            ),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "single", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.revision.single</groupId>
                                                <artifactId>project</artifactId>
                                                <version>${revision}</version>
                                            
                                                <properties>
                                                    <revision>%s</revision>
                                                </properties>
                                            </project>
                                            """.formatted(expectedVersion)
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "single", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## %1$s - 2025-01-01
                                            
                                            ### %2$s
                                            
                                            %2$s versioning applied.
                                            
                                            ## 2.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """.formatted(expectedVersion, title)
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactlyInAnyOrder(
                            getResourcesPath("versioning", "revision", "single", folder, "versioning.md")
                    );
        }

        @Test
        void multipleSemanticVersionBumpFiles_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "single", "multiple");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(18)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.revision.single:project:2.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "single", "multiple", "major.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.single:project': major\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.single:project=%s}\
                                    """.formatted(SemanticVersionBump.MAJOR)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "single", "multiple", "minor.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.single:project': minor\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.single:project=%s}\
                                    """.formatted(SemanticVersionBump.MINOR)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "single", "multiple", "none.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.single:project': none\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.single:project=%s}\
                                    """.formatted(SemanticVersionBump.NONE)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "revision", "single", "multiple", "patch.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.revision.single:project': patch\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.revision.single:project=%s}\
                                    """.formatted(SemanticVersionBump.PATCH)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(SemanticVersionBump.MAJOR)
                            ),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("revision", "single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "single", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.revision.single</groupId>
                                                <artifactId>project</artifactId>
                                                <version>${revision}</version>
                                            
                                                <properties>
                                                    <revision>3.0.0</revision>
                                                </properties>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("revision", "single", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 3.0.0 - 2025-01-01
                                            
                                            ### Major
                                            
                                            Major versioning applied.
                                            
                                            ### Minor
                                            
                                            Minor versioning applied.
                                            
                                            ### Patch
                                            
                                            Patch versioning applied.
                                            
                                            ### Other
                                            
                                            No versioning applied.
                                            
                                            ## 2.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(4)
                    .containsExactlyInAnyOrder(
                            getResourcesPath("versioning", "revision", "single", "multiple", "major.md"),
                            getResourcesPath("versioning", "revision", "single", "multiple", "minor.md"),
                            getResourcesPath("versioning", "revision", "single", "multiple", "patch.md"),
                            getResourcesPath("versioning", "revision", "single", "multiple", "none.md")
                    );
        }
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
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("single", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
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
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBumpWithBackup_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.backupFiles = true;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("single", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
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
                    .isNotEmpty()
                    .hasSize(2)
                    .containsExactlyInAnyOrder(
                            new CopyPath(
                                    getResourcesPath("single", "pom.xml"),
                                    getResourcesPath("single", "pom.xml.backup"),
                                    List.of(
                                            StandardCopyOption.ATOMIC_MOVE,
                                            StandardCopyOption.COPY_ATTRIBUTES,
                                            StandardCopyOption.REPLACE_EXISTING
                                    )
                            ),
                            new CopyPath(
                                    getResourcesPath("single", "CHANGELOG.md"),
                                    getResourcesPath("single", "CHANGELOG.md.backup"),
                                    List.of(
                                            StandardCopyOption.ATOMIC_MOVE,
                                            StandardCopyOption.COPY_ATTRIBUTES,
                                            StandardCopyOption.REPLACE_EXISTING
                                    )
                            )
                    );
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void fixedVersionBumpDryRun_Valid(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.dryRun = true;

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            String expectedVersion = switch (versionBump) {
                case FILE_BASED -> throw new AssertionError("Should not be called");
                case MAJOR -> "2.0.0";
                case MINOR -> "1.1.0";
                case PATCH -> "1.0.1";
            };

            assertThat(testLog.getLogRecords())
                    .hasSize(9)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("single", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("""
                                    Dry-run: new pom at %s:
                                    <?xml version="1.0" encoding="UTF-8"?>
                                    <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                    http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                        <modelVersion>4.0.0</modelVersion>
                                        <groupId>org.example.itests.single</groupId>
                                        <artifactId>project</artifactId>
                                        <version>%s</version>
                                    </project>\
                                    """.formatted(getResourcesPath("single", "pom.xml"), expectedVersion)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog"),
                            validateLogRecordInfo("""
                                    Dry-run: new changelog at %s:
                                    # Changelog
                                    
                                    ## %s - 2025-01-01
                                    
                                    ### Other
                                    
                                    Project version bumped as result of dependency bumps
                                    
                                    ## 1.0.0 - 2026-01-01
                                    
                                    Initial release.
                                    """.formatted(getResourcesPath("single", "CHANGELOG.md"), expectedVersion))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = VersionBump.class, names = {"FILE_BASED"}, mode = EnumSource.Mode.EXCLUDE)
        void dryRunStringWriteCloseFailure_ThrowMojoExecutionException(VersionBump versionBump) {
            classUnderTest.versionBump = versionBump;
            classUnderTest.dryRun = true;

            IOException ioException = new IOException("Unable to open output stream for writing");
            try (MockedConstruction<StringWriter> ignored = Mockito.mockConstruction(
                    StringWriter.class,
                    (mock, context) -> {
                        Mockito.doThrow(ioException).when(mock).close();
                        Mockito.when(mock.toString()).thenReturn("Mock for StringWriter, hashCode: 0");
                    }
            )) {
                assertThatThrownBy(classUnderTest::execute)
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to open output stream for writing")
                        .hasRootCause(ioException);
            }

            assertThat(testLog.getLogRecords())
                    .hasSize(5)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordWarn("No versioning files found in %s as folder does not exists".formatted(
                                    getResourcesPath("single", ".versioning")
                            )),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo("Updating version with a %s semantic version".formatted(versionBump)),
                            validateLogRecordInfo("""
                                    Dry-run: new pom at %s:
                                    Mock for StringWriter, hashCode: 0\
                                    """.formatted(getResourcesPath("single", "pom.xml")))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void filedBasedWalkFailed_ThrowMojoExecutionException() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "single", "unknown-project");
            filesMockedStatic.when(() -> Files.walk(Mockito.any(Path.class), Mockito.eq(1)))
                    .thenThrow(IOException.class);

            assertThatThrownBy(classUnderTest::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Unable to read versioning folder")
                    .hasRootCauseInstanceOf(IOException.class);

            assertThat(testLog.getLogRecords())
                    .hasSize(1)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0")
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void unknownProjectFileBased_ThrowMojoFailureException() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "single", "unknown-project");


            assertThatThrownBy(classUnderTest::execute)
                    .isInstanceOf(MojoFailureException.class)
                    .hasMessage("""
                            The following artifacts in the Markdown files are not present in the project scope: \
                            org.example.itests.single:unknown-project\
                            """);

            assertThat(testLog.getLogRecords())
                    .hasSize(4)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "single", "unknown-project", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:unknown-project': major\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:unknown-project=%s}\
                                    """.formatted(SemanticVersionBump.MAJOR))
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @Test
        void noSemanticVersionBumpFileBased_NothingChanged() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "single", "none");


            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(7)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "single", "none", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:project': none\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:project=%s}\
                                    """.formatted(SemanticVersionBump.NONE)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(SemanticVersionBump.NONE)
                            ),
                            validateLogRecordInfo("No version update required")
                    );

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
                "major,Major,2.0.0",
                "minor,Minor,1.1.0",
                "patch,Patch,1.0.1"
        })
        void singleSemanticVersionBumFile_Valid(String folder, String title, String expectedVersion) {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "single", folder);


            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            SemanticVersionBump semanticVersionBump = SemanticVersionBump.fromString(folder);
            assertThat(testLog.getLogRecords())
                    .hasSize(9)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "single", folder, "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:project': %s\
                                    """.formatted(folder)),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:project=%s}\
                                    """.formatted(semanticVersionBump)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(semanticVersionBump)
                            ),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

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
                                            
                                            ## %1$s - 2025-01-01
                                            
                                            ### %2$s
                                            
                                            %2$s versioning applied.
                                            
                                            ## 1.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """.formatted(expectedVersion, title)
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactlyInAnyOrder(
                            getResourcesPath("versioning", "single", folder, "versioning.md")
                    );
        }

        @Test
        void multipleSemanticVersionBumpFiles_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "single", "multiple");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(18)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "single", "multiple", "major.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:project': major\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:project=%s}\
                                    """.formatted(SemanticVersionBump.MAJOR)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "single", "multiple", "minor.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:project': minor\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:project=%s}\
                                    """.formatted(SemanticVersionBump.MINOR)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "single", "multiple", "none.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:project': none\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:project=%s}\
                                    """.formatted(SemanticVersionBump.NONE)),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("versioning", "single", "multiple", "patch.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        'org.example.itests.single:project': patch\
                                    """),
                            validateLogRecordDebug("""
                                    Maven artifacts and semantic version bumps:
                                    {org.example.itests.single:project=%s}\
                                    """.formatted(SemanticVersionBump.PATCH)),
                            validateLogRecordInfo("Single project in scope"),
                            validateLogRecordInfo(
                                    "Updating version with a %s semantic version".formatted(SemanticVersionBump.MAJOR)
                            ),
                            validateLogRecordInfo("Read 5 lines from %s".formatted(
                                    getResourcesPath("single", "CHANGELOG.md")
                            )),
                            validateLogRecordDebug("Original changelog"),
                            validateLogRecordDebug("Updated changelog")
                    );

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
                                                <version>2.0.0</version>
                                            </project>
                                            """
                                    )
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("single", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 2.0.0 - 2025-01-01
                                            
                                            ### Major
                                            
                                            Major versioning applied.
                                            
                                            ### Minor
                                            
                                            Minor versioning applied.
                                            
                                            ### Patch
                                            
                                            Patch versioning applied.
                                            
                                            ### Other
                                            
                                            No versioning applied.
                                            
                                            ## 1.0.0 - 2026-01-01
                                            
                                            Initial release.
                                            """
                                    )
                    );
            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(4)
                    .containsExactlyInAnyOrder(
                            getResourcesPath("versioning", "single", "multiple", "major.md"),
                            getResourcesPath("versioning", "single", "multiple", "minor.md"),
                            getResourcesPath("versioning", "single", "multiple", "patch.md"),
                            getResourcesPath("versioning", "single", "multiple", "none.md")
                    );
        }
    }
}
