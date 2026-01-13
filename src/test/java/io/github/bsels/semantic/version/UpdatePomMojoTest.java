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
                    List<CopyOption> options = IntStream.range(2, answer.getArguments().length)
                            .<CopyOption>mapToObj(answer::getArgument)
                            .toList();
                    mockedCopiedFiles.add(new CopyPath(original, copy, options));
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
        }
    }
}
