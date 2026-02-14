package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.parameters.ArtifactIdentifier;
import io.github.bsels.semantic.version.parameters.Git;
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
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class UpdatePomMojoTest extends AbstractBaseMojoTest {
    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
    @Mock
    Process processMock;
    private UpdatePomMojo classUnderTest;
    private TestLog testLog;
    private Map<Path, StringWriter> mockedOutputFiles;
    private List<CopyPath> mockedCopiedFiles;
    private List<Path> mockedDeletedFiles;
    private List<List<String>> mockedExecutedProcesses;
    private MockedStatic<Files> filesMockedStatic;
    private MockedStatic<LocalDate> localDateMockedStatic;
    private MockedConstruction<ProcessBuilder> mockedProcessBuilderConstruction;

    @BeforeEach
    void setUp() {
        classUnderTest = new UpdatePomMojo();
        testLog = new TestLog(TestLog.LogLevel.NONE);
        classUnderTest.setLog(testLog);
        mockedOutputFiles = new HashMap<>();
        mockedCopiedFiles = new ArrayList<>();
        mockedDeletedFiles = new ArrayList<>();
        mockedExecutedProcesses = new ArrayList<>();

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

        mockedProcessBuilderConstruction = Mockito.mockConstruction(ProcessBuilder.class, (mock, context) -> {
            if (!context.arguments().isEmpty()) {
                List<String> command = List.of((String[]) context.arguments().get(0));
                if (!command.isEmpty()) {
                    mockedExecutedProcesses.add(command);
                }
            }
            Map<String, String> environment = new HashMap<>();
            Mockito.when(mock.command(Mockito.anyList()))
                    .thenAnswer(invocation -> {
                        mockedExecutedProcesses.add(invocation.getArgument(0));
                        return mock;
                    });
            Mockito.when(mock.environment()).thenReturn(environment);
            Mockito.when(mock.directory(Mockito.any())).thenReturn(mock);
            Mockito.when(mock.inheritIO()).thenReturn(mock);
            Mockito.when(mock.start()).thenReturn(processMock);
        });
    }

    @AfterEach
    void tearDown() {
        filesMockedStatic.close();
        localDateMockedStatic.close();
        mockedProcessBuilderConstruction.close();
    }

    @Test
    void noExecutionOnSubProjectIfDisabled_SkipExecution() {
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
        assertThat(mockedExecutedProcesses)
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
        assertThat(mockedExecutedProcesses)
                .isEmpty();
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @Test
        void singleFileBased_ArtifactOnlyIdentifier_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.identifier = ArtifactIdentifier.ONLY_ARTIFACT_ID;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "leaves", "single-artifact-only");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(21)
                    .satisfiesExactlyInAnyOrder(
                            validateLogRecordInfo("Execution for project: org.example.itests.leaves:root:5.0.0-root"),
                            validateLogRecordInfo("Read 7 lines from %s".formatted(
                                    getResourcesPath("versioning", "leaves", "single-artifact-only", "versioning.md")
                            )),
                            validateLogRecordDebug("""
                                    YAML front matter:
                                        child-1: patch
                                        child-2: minor
                                        child-3: major\
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
                    .containsExactly(getResourcesPath("versioning", "leaves", "single-artifact-only", "versioning.md"));
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(Git.class)
        void multiFileBased_Valid(Git gitMode) {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.git = gitMode;
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
            if (Git.NO_GIT == gitMode) {
                assertThat(mockedExecutedProcesses)
                        .isEmpty();
            } else if (Git.STASH == gitMode) {
                assertThat(mockedExecutedProcesses)
                        .hasSize(7)
                        .contains(
                                List.of("git", "add", getResourcesPath("leaves", "child-1", "pom.xml").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "child-1", "CHANGELOG.md").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "intermediate", "child-2", "pom.xml").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "intermediate", "child-2", "CHANGELOG.md").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "intermediate", "child-3", "pom.xml").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "intermediate", "child-3", "CHANGELOG.md").toString())
                        )
                        .anySatisfy(
                                command -> assertThat(command)
                                        .startsWith("git", "add")
                                        .containsExactlyInAnyOrder(
                                                "git",
                                                "add",
                                                getResourcesPath("versioning", "leaves", "multi", "child-1.md").toString(),
                                                getResourcesPath("versioning", "leaves", "multi", "child-2.md").toString(),
                                                getResourcesPath("versioning", "leaves", "multi", "child-3.md").toString()
                                        )
                        );
            } else {
                assertThat(mockedExecutedProcesses)
                        .hasSize(8)
                        .contains(
                                List.of("git", "add", getResourcesPath("leaves", "child-1", "pom.xml").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "child-1", "CHANGELOG.md").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "intermediate", "child-2", "pom.xml").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "intermediate", "child-2", "CHANGELOG.md").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "intermediate", "child-3", "pom.xml").toString()),
                                List.of("git", "add", getResourcesPath("leaves", "intermediate", "child-3", "CHANGELOG.md").toString()),
                                List.of("git", "commit", "-m", "Updated 3 project version(s) [skip ci]")
                        )
                        .anySatisfy(
                                command -> assertThat(command)
                                        .startsWith("git", "add")
                                        .containsExactlyInAnyOrder(
                                                "git",
                                                "add",
                                                getResourcesPath("versioning", "leaves", "multi", "child-1.md").toString(),
                                                getResourcesPath("versioning", "leaves", "multi", "child-2.md").toString(),
                                                getResourcesPath("versioning", "leaves", "multi", "child-3.md").toString()
                                        )
                        );
            }
        }

    }

    @Nested
    class MultiProjectTest {

        private static String getVersioningMessage(String dependency) {
            return switch (dependency) {
                case "dependency" -> "Dependency";
                case "dependencyManagement" -> "Dependency management";
                case "plugin" -> "Plugin";
                case "pluginManagement" -> "Plugin management";
                case "parent" -> "Parent";
                default -> throw new IllegalStateException("Unknown dependency type: " + dependency);
            };
        }

        private static String folderToMessage(String dependency) {
            return switch (dependency) {
                case "dependency" -> "dependency";
                case "dependencyManagement" -> "dependency-management";
                case "plugin" -> "plugin";
                case "pluginManagement" -> "plugin-management";
                case "parent" -> "parent";
                default -> throw new IllegalStateException("Unknown dependency type: " + dependency);
            };
        }

        @BeforeEach
        void setUp() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("multi"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.PROJECT_VERSION;
        }

        @ParameterizedTest
        @CsvSource({
                "dependency,4.1.0-dependency,4.0.0-dependency-management,4.0.0-plugin,4.0.0-plugin-management,4.0.0-parent",
                "dependencyManagement,4.0.0-dependency,4.1.0-dependency-management,4.0.0-plugin,4.0.0-plugin-management,4.0.0-parent",
                "plugin,4.0.0-dependency,4.0.0-dependency-management,4.1.0-plugin,4.0.0-plugin-management,4.0.0-parent",
                "pluginManagement,4.0.0-dependency,4.0.0-dependency-management,4.0.0-plugin,4.1.0-plugin-management,4.0.0-parent",
                "parent,4.0.0-dependency,4.0.0-dependency-management,4.0.0-plugin,4.0.0-plugin-management,4.1.0-parent"
        })
        void handleDependencyCorrect_NoErrors(
                String dependency,
                String dependencyVersion,
                String dependencyManagementVersion,
                String pluginVersion,
                String pluginManagementVersion,
                String parentVersion
        ) {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "multi", dependency);

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(mockedOutputFiles)
                    .hasSize(4)
                    .hasEntrySatisfying(
                            getResourcesPath("multi", "combination", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 4.0.1-combination - 2025-01-01
                                            
                                            ### Other
                                            
                                            Project version bumped as result of dependency bumps
                                            
                                            ## 4.0.0-combination - 2026-01-01
                                            
                                            Initial dependency release.
                                            """)
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("multi", "combination", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                                    <?xml version="1.0" encoding="UTF-8"?>
                                                    <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                                    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                                    http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                        <parent>
                                                            <groupId>org.example.itests.multi</groupId>
                                                            <artifactId>parent</artifactId>
                                                            <version>%5$s</version>
                                                        </parent>
                                                    
                                                        <modelVersion>4.0.0</modelVersion>
                                                        <artifactId>combination</artifactId>
                                                        <version>4.0.1-combination</version>
                                                    
                                                        <dependencies>
                                                            <dependency>
                                                                <groupId>org.example.itests.multi</groupId>
                                                                <artifactId>dependency</artifactId>
                                                                <version>%1$s</version>
                                                            </dependency>
                                                        </dependencies>
                                                    
                                                        <dependencyManagement>
                                                            <dependencies>
                                                                <dependency>
                                                                    <groupId>org.example.itests.multi</groupId>
                                                                    <artifactId>dependency-management</artifactId>
                                                                    <version>%2$s</version>
                                                                </dependency>
                                                            </dependencies>
                                                        </dependencyManagement>
                                                    
                                                        <build>
                                                            <plugins>
                                                                <plugin>
                                                                    <groupId>org.example.itests.multi</groupId>
                                                                    <artifactId>plugin</artifactId>
                                                                    <version>%3$s</version>
                                                                </plugin>
                                                            </plugins>
                                                            <pluginManagement>
                                                                <plugins>
                                                                    <plugin>
                                                                        <groupId>org.example.itests.multi</groupId>
                                                                        <artifactId>plugin-management</artifactId>
                                                                        <version>%4$s</version>
                                                                    </plugin>
                                                                </plugins>
                                                            </pluginManagement>
                                                        </build>
                                                    </project>
                                                    """.formatted(
                                                    dependencyVersion,
                                                    dependencyManagementVersion,
                                                    pluginVersion,
                                                    pluginManagementVersion,
                                                    parentVersion
                                            )
                                    )
                    );

            if ("parent".equals(dependency)) {
                assertThat(mockedOutputFiles)
                        .hasEntrySatisfying(
                                getResourcesPath("multi", "CHANGELOG.md"),
                                writer -> assertThat(writer.toString())
                                        .isEqualToIgnoringNewLines("""
                                                # Changelog
                                                
                                                ## 4.1.0-%1$s - 2025-01-01
                                                
                                                ### Minor
                                                
                                                %2$s update.
                                                
                                                ## 4.0.0-%1$s - 2026-01-01
                                                
                                                Initial %3$s release.
                                                """.formatted(
                                                folderToMessage(dependency),
                                                getVersioningMessage(dependency),
                                                getVersioningMessage(dependency).toLowerCase()
                                        ))
                        )
                        .hasEntrySatisfying(
                                getResourcesPath("multi", "pom.xml"),
                                writer -> assertThat(writer.toString())
                                        .isEqualToIgnoringNewLines("""
                                                <?xml version="1.0" encoding="UTF-8"?>
                                                <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                                xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                                http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                    <modelVersion>4.0.0</modelVersion>
                                                    <groupId>org.example.itests.multi</groupId>
                                                    <artifactId>%1$s</artifactId>
                                                    <version>4.1.0-%1$s</version>
                                                
                                                    <packaging>pom</packaging>
                                                
                                                    <modules>
                                                        <module>dependency</module>
                                                        <module>dependencyManagement</module>
                                                        <module>plugin</module>
                                                        <module>pluginManagement</module>
                                                        <module>combination</module>
                                                        <module>excluded</module>
                                                    </modules>
                                                </project>
                                                """.formatted(folderToMessage(dependency))
                                        )
                        );
            } else {
                assertThat(mockedOutputFiles)
                        .hasEntrySatisfying(
                                getResourcesPath("multi", dependency, "CHANGELOG.md"),
                                writer -> assertThat(writer.toString())
                                        .isEqualToIgnoringNewLines("""
                                                # Changelog
                                                
                                                ## 4.1.0-%1$s - 2025-01-01
                                                
                                                ### Minor
                                                
                                                %2$s update.
                                                
                                                ## 4.0.0-%1$s - 2026-01-01
                                                
                                                Initial %3$s release.
                                                """.formatted(
                                                folderToMessage(dependency),
                                                getVersioningMessage(dependency),
                                                getVersioningMessage(dependency).toLowerCase()
                                        ))
                        )
                        .hasEntrySatisfying(
                                getResourcesPath("multi", dependency, "pom.xml"),
                                writer -> assertThat(writer.toString())
                                        .isEqualToIgnoringNewLines("""
                                                <?xml version="1.0" encoding="UTF-8"?>
                                                <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                                xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                                http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                    <modelVersion>4.0.0</modelVersion>
                                                    <groupId>org.example.itests.multi</groupId>
                                                    <artifactId>%1$s</artifactId>
                                                    <version>4.1.0-%1$s</version>
                                                </project>
                                                """.formatted(folderToMessage(dependency))
                                        )
                        );
            }

            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactly(getResourcesPath("versioning", "multi", dependency, "versioning.md"));
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @Test
        void independentProject_NoDependencyUpdates() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "multi", "excluded");


            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(mockedOutputFiles)
                    .hasSize(2)
                    .hasEntrySatisfying(
                            getResourcesPath("multi", "excluded", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 4.1.0-excluded - 2025-01-01
                                            
                                            ### Minor
                                            
                                            Excluded update.
                                            
                                            ## 4.0.0-excluded - 2026-01-01
                                            
                                            Initial excluded release.
                                            """)
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("multi", "excluded", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.multi</groupId>
                                                <artifactId>excluded</artifactId>
                                                <version>4.1.0-excluded</version>
                                            </project>
                                            """)
                    );

            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactly(getResourcesPath("versioning", "multi", "excluded", "versioning.md"));
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }
    }

    @Nested
    class MultiRecursiveProjectTest {

        @BeforeEach
        void setUp() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("multi-recursive"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.PROJECT_VERSION;
        }

        @Test
        void handleMultiRecursiveProjectCorrect_NoErrors() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "multi-recursive");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(mockedOutputFiles)
                    .hasSize(6)
                    .hasEntrySatisfying(
                            getResourcesPath("multi-recursive", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 6.1.0-parent - 2025-01-01
                                            
                                            ### Minor
                                            
                                            Parent update.
                                            
                                            ## 6.0.0-parent - 2026-01-01
                                            
                                            Initial parent release.
                                            """)
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("multi-recursive", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.multi-recursive</groupId>
                                                <artifactId>parent</artifactId>
                                                <version>6.1.0-parent</version>
                                            
                                                <packaging>pom</packaging>
                                            
                                                <modules>
                                                    <module>child-1</module>
                                                    <module>child-2</module>
                                                </modules>
                                            </project>
                                            """)
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("multi-recursive", "child-1", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 6.0.1-child-1 - 2025-01-01
                                            
                                            ### Other
                                            
                                            Project version bumped as result of dependency bumps
                                            
                                            ## 6.0.0-child-1 - 2026-01-01
                                            
                                            Initial child 1 release.
                                            """)
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("multi-recursive", "child-1", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" \
                                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <parent>
                                                    <groupId>org.example.itests.multi-recursive</groupId>
                                                    <artifactId>parent</artifactId>
                                                    <version>6.1.0-parent</version>
                                                </parent>
                                            
                                                <modelVersion>4.0.0</modelVersion>
                                                <artifactId>child-1</artifactId>
                                                <version>6.0.1-child-1</version>
                                            </project>
                                            """)
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("multi-recursive", "child-2", "CHANGELOG.md"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            # Changelog
                                            
                                            ## 6.0.1-child-2 - 2025-01-01
                                            
                                            ### Other
                                            
                                            Project version bumped as result of dependency bumps
                                            
                                            ## 6.0.0-child-2 - 2026-01-01
                                            
                                            Initial child 2 release.
                                            """)
                    )
                    .hasEntrySatisfying(
                            getResourcesPath("multi-recursive", "child-2", "pom.xml"),
                            writer -> assertThat(writer.toString())
                                    .isEqualToIgnoringNewLines("""
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>org.example.itests.multi-recursive</groupId>
                                                <artifactId>child-2</artifactId>
                                                <version>6.0.1-child-2</version>
                                            
                                                <dependencies>
                                                    <dependency>
                                                        <groupId>org.example.itests.multi-recursive</groupId>
                                                        <artifactId>child-1</artifactId>
                                                        <version>6.0.1-child-1</version>
                                                    </dependency>
                                                </dependencies>
                                            </project>
                                            """)
                    );

            assertThat(mockedCopiedFiles)
                    .isEmpty();
            assertThat(mockedDeletedFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactly(getResourcesPath("versioning", "multi-recursive", "versioning.md"));
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
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
                                            
                                                <packaging>pom</packaging>
                                            
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
            assertThat(mockedExecutedProcesses)
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
                                            
                                                <packaging>pom</packaging>
                                            
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
            assertThat(mockedExecutedProcesses)
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
                                    
                                        <packaging>pom</packaging>
                                    
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
                                    Dry-run: new markdown file at %s:
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
                                            
                                                <packaging>pom</packaging>
                                            
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
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @Test
        void multipleSemanticVersionBumpFiles_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "multi", "multiple");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(18)
                    .satisfiesExactlyInAnyOrder(
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
                                            
                                                <packaging>pom</packaging>
                                            
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
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @Test
        void multipleSemanticVersionBumpFiles_CustomHeaders_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "multi", "multiple");
            classUnderTest.changelogHeader = "Changes";
            classUnderTest.versionHeader = "{version} / {date#yyyy/MM/dd}";
            classUnderTest.majorHeader = "Breaking";
            classUnderTest.minorHeader = "Features";
            classUnderTest.patchHeader = "Fixes";
            classUnderTest.otherHeader = "Misc";

            Path changelogPath = getResourcesPath("revision", "multi", "CHANGELOG.md");
            filesMockedStatic.when(() -> Files.lines(changelogPath, StandardCharsets.UTF_8))
                    .thenReturn(Stream.of(
                            "# Changes",
                            "",
                            "## 3.0.0 - 2026-01-01",
                            "",
                            "Initial release."
                    ));

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(18)
                    .satisfiesExactlyInAnyOrder(
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
                            validateLogRecordInfo("Read 5 lines from %s".formatted(changelogPath)),
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
                                            
                                                <packaging>pom</packaging>
                                            
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
                                            # Changes
                                            
                                            ## 4.0.0 / 2025/01/01
                                            
                                            ### Breaking
                                            
                                            Major versioning applied.
                                            
                                            ### Features
                                            
                                            Minor versioning applied.
                                            
                                            ### Fixes
                                            
                                            Patch versioning applied.
                                            
                                            ### Misc
                                            
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
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
                                    Dry-run: new markdown file at %s:
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @Test
        void multipleSemanticVersionBumpFiles_Valid() {
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "revision", "single", "multiple");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .hasSize(18)
                    .satisfiesExactlyInAnyOrder(
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
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
                                    Dry-run: new markdown file at %s:
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
            assertThat(mockedExecutedProcesses)
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
                    .satisfiesExactlyInAnyOrder(
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
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }
    }

    @Nested
    class ExecuteScriptsFlowTest {

        @Test
        void singleProject_ExecutesScriptsForUpdate() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("single"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.PROJECT_VERSION;
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "single", "patch");
            classUnderTest.scripts = String.join(File.pathSeparator, "script-a.sh", "script-b.sh");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(mockedExecutedProcesses)
                    .hasSize(2);
            assertThat(countScriptExecutions(Path.of("script-a.sh").toAbsolutePath().toString()))
                    .isEqualTo(1);
            assertThat(countScriptExecutions(Path.of("script-b.sh").toAbsolutePath().toString()))
                    .isEqualTo(1);
        }

        @Test
        void multiProject_ExecutesScriptsForEachUpdatedProject() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("leaves"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.PROJECT_VERSION_ONLY_LEAFS;
            classUnderTest.versionBump = VersionBump.FILE_BASED;
            classUnderTest.versionDirectory = getResourcesPath("versioning", "leaves", "single");
            classUnderTest.scripts = String.join(File.pathSeparator, "script-a.sh", "script-b.sh");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(mockedExecutedProcesses)
                    .hasSize(6);
            assertThat(countScriptExecutions(Path.of("script-a.sh").toAbsolutePath().toString()))
                    .isEqualTo(3);
            assertThat(countScriptExecutions(Path.of("script-b.sh").toAbsolutePath().toString()))
                    .isEqualTo(3);
        }

        private long countScriptExecutions(String script) {
            return mockedExecutedProcesses.stream()
                    .filter(command -> command.equals(List.of(script)))
                    .count();
        }
    }
}
