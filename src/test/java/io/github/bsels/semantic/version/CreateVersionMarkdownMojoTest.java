package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.parameters.Git;
import io.github.bsels.semantic.version.parameters.Modus;
import io.github.bsels.semantic.version.test.utils.ReadMockedMavenSession;
import io.github.bsels.semantic.version.test.utils.TestLog;
import io.github.bsels.semantic.version.utils.Utils;
import org.apache.maven.plugin.MojoFailureException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class CreateVersionMarkdownMojoTest extends AbstractBaseMojoTest {
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2023, 1, 1, 12, 0, 8);
    private static final Path TEMP_FILE = Path.of("/tmp", "target", "test-output.md");
    private final InputStream originalSystemIn = System.in;
    private final PrintStream originalSystemOut = System.out;
    @Mock
    Process processMock;
    private CreateVersionMarkdownMojo classUnderTest;
    private TestLog testLog;
    private Map<Path, StringWriter> mockedOutputFiles;
    private Set<Path> mockedCreatedDirectories;
    private List<List<String>> mockedExecutedProcesses;
    private MockedStatic<Files> filesMockedStatic;
    private MockedStatic<LocalDateTime> localDateTimeMockedStatic;
    private MockedConstruction<ProcessBuilder> mockedProcessBuilderConstruction;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    public void setUp() {
        classUnderTest = new CreateVersionMarkdownMojo();
        testLog = new TestLog(TestLog.LogLevel.DEBUG);
        classUnderTest.setLog(testLog);

        mockedOutputFiles = new HashMap<>();
        mockedCreatedDirectories = new HashSet<>();
        mockedExecutedProcesses = new ArrayList<>();

        filesMockedStatic = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
        filesMockedStatic.when(() -> Files.newBufferedWriter(Mockito.any(), Mockito.any(), Mockito.any(OpenOption[].class)))
                .thenAnswer(answer -> {
                    Path path = answer.getArgument(0);
                    mockedOutputFiles.put(path, new StringWriter());
                    return new BufferedWriter(mockedOutputFiles.get(path));
                });
        filesMockedStatic.when(() -> Files.createDirectories(Mockito.any()))
                .thenAnswer(answer -> {
                    Path argument = answer.getArgument(0);
                    mockedCreatedDirectories.add(argument);
                    return argument;
                });
        filesMockedStatic.when(() -> Files.createTempFile(Mockito.any(), Mockito.any()))
                .thenReturn(TEMP_FILE);
        filesMockedStatic.when(() -> Files.exists(TEMP_FILE))
                .thenReturn(true);
        filesMockedStatic.when(() -> Files.deleteIfExists(TEMP_FILE))
                .thenReturn(true);
        filesMockedStatic.when(() -> Files.lines(TEMP_FILE, StandardCharsets.UTF_8))
                .thenReturn(Stream.of("Testing external"));

        localDateTimeMockedStatic = Mockito.mockStatic(LocalDateTime.class);
        localDateTimeMockedStatic.when(LocalDateTime::now)
                .thenReturn(DATE_TIME);

        mockedProcessBuilderConstruction = Mockito.mockConstruction(ProcessBuilder.class, (mock, context) -> {
            if (!context.arguments().isEmpty()) {
                List<String> command = List.of((String[]) context.arguments().get(0));
                if (!command.isEmpty()) {
                    mockedExecutedProcesses.add(command);
                }
            }
            Mockito.when(mock.command(Mockito.anyList()))
                    .thenAnswer(invocation -> {
                        mockedExecutedProcesses.add(invocation.getArgument(0));
                        return mock;
                    });
            Mockito.when(mock.inheritIO()).thenReturn(mock);
            Mockito.when(mock.start()).thenReturn(processMock);
        });

        outputStream = new ByteArrayOutputStream();
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    public void tearDown() {
        System.setIn(originalSystemIn);
        System.setOut(originalSystemOut);
        filesMockedStatic.close();
        localDateTimeMockedStatic.close();
        mockedProcessBuilderConstruction.close();
    }

    private void setSystemIn(String input) {
        System.setIn(new ByteArrayInputStream(input.getBytes()));
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
                .hasSize(2)
                .satisfiesExactly(
                        validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                        validateLogRecordWarn("No projects found in scope")
                );

        assertThat(mockedOutputFiles)
                .isEmpty();
    }

    @Nested
    class MultiProjectExecutionTest {

        @BeforeEach
        void setUp() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("multi"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.PROJECT_VERSION;
        }

        @Test
        void noProjectsSelected_LogWarning() {
            setSystemIn("");

            assertThatNoException()
                    .isThrownBy(classUnderTest::execute);

            assertThat(testLog.getLogRecords())
                    .isNotEmpty()
                    .hasSize(3)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.multi:parent:4.0.0-parent"),
                            validateLogRecordDebug("No projects selected"),
                            validateLogRecordWarn("No projects selected")
                    );

            assertThat(outputStream.toString())
                    .isEqualTo("""
                            Select projects:
                              1: org.example.itests.multi:parent
                              2: org.example.itests.multi:combination
                              3: org.example.itests.multi:dependency
                              4: org.example.itests.multi:dependency-management
                              5: org.example.itests.multi:excluded
                              6: org.example.itests.multi:plugin
                              7: org.example.itests.multi:plugin-management
                            Enter project numbers separated by spaces, commas or semicolons: \
                            """);

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = SemanticVersionBump.class, names = {"MAJOR", "MINOR", "PATCH"})
        void selectSingleProject_Valid(SemanticVersionBump bump) {
            classUnderTest.dryRun = false;

            try (MockedConstruction<Scanner> ignored = Mockito.mockConstruction(
                    Scanner.class, (mock, context) -> {
                        Mockito.when(mock.hasNextLine()).thenReturn(true, false);
                        if (context.getCount() == 1) {
                            Mockito.when(mock.nextLine()).thenReturn("1");
                        } else if (context.getCount() == 2) {
                            Mockito.when(mock.nextLine()).thenReturn(bump.name().toLowerCase());
                        } else {
                            Mockito.when(mock.nextLine()).thenReturn("Testing");
                        }
                    }
            )) {
                assertThatNoException()
                        .isThrownBy(classUnderTest::execute);
            }

            assertThat(testLog.getLogRecords())
                    .isNotEmpty()
                    .hasSize(2)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.multi:parent:4.0.0-parent"),
                            validateLogRecordDebug("""
                                    Version bumps YAML:
                                        org.example.itests.multi:parent: "%s"
                                    """.formatted(bump))
                    );

            assertThat(outputStream.toString())
                    .isEqualTo("""
                            Select projects:
                              1: org.example.itests.multi:parent
                              2: org.example.itests.multi:combination
                              3: org.example.itests.multi:dependency
                              4: org.example.itests.multi:dependency-management
                              5: org.example.itests.multi:excluded
                              6: org.example.itests.multi:plugin
                              7: org.example.itests.multi:plugin-management
                            Enter project numbers separated by spaces, commas or semicolons: Selected projects: org.example.itests.multi:parent
                            Select semantic version bump for org.example.itests.multi:parent:\s
                              1: PATCH
                              2: MINOR
                              3: MAJOR
                            Enter semantic version name or number: Version bumps: 'org.example.itests.multi:parent': %s
                            Please type the changelog entry here (enter empty line to open external editor, two empty lines after your input to end):
                            """.formatted(bump));

            assertThat(mockedOutputFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .hasEntrySatisfying(
                            getVersioningMarkdown(),
                            writer -> assertThat(writer.toString())
                                    .isEqualTo("""
                                            ---
                                            org.example.itests.multi:parent: "%s"
                                            
                                            ---
                                            
                                            Testing
                                            """.formatted(bump))
                    );
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = Git.class)
        void selectMultipleProjects_Valid(Git gitMode) {
            classUnderTest.dryRun = false;
            classUnderTest.git = gitMode;

            try (MockedConstruction<Scanner> ignored = Mockito.mockConstruction(
                    Scanner.class, (mock, context) -> {
                        Mockito.when(mock.hasNextLine()).thenReturn(true, false);
                        if (context.getCount() == 1) {
                            Mockito.when(mock.nextLine()).thenReturn("1,3,6");
                        } else if (context.getCount() >= 2 && context.getCount() <= 4) {
                            Mockito.when(mock.nextLine()).thenReturn(
                                    SemanticVersionBump.values()[context.getCount() - 1].name().toLowerCase()
                            );
                        } else {
                            Mockito.when(mock.nextLine()).thenReturn("Testing");
                        }
                    }
            )) {
                assertThatNoException()
                        .isThrownBy(classUnderTest::execute);
            }

            assertThat(testLog.getLogRecords())
                    .isNotEmpty()
                    .hasSize(2)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.multi:parent:4.0.0-parent"),
                            record -> assertThat(record)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                                    .extracting(TestLog.LogRecord::message)
                                    .asInstanceOf(InstanceOfAssertFactories.optional(String.class))
                                    .isPresent()
                                    .get()
                                    .asInstanceOf(InstanceOfAssertFactories.STRING)
                                    .startsWith("Version bumps YAML:\n")
                                    .contains("    org.example.itests.multi:parent: \"PATCH\"\n")
                                    .contains("    org.example.itests.multi:dependency: \"MINOR\"\n")
                                    .contains("    org.example.itests.multi:plugin: \"MAJOR\"\n")
                    );

            assertThat(outputStream.toString())
                    .isEqualTo("""
                            Select projects:
                              1: org.example.itests.multi:parent
                              2: org.example.itests.multi:combination
                              3: org.example.itests.multi:dependency
                              4: org.example.itests.multi:dependency-management
                              5: org.example.itests.multi:excluded
                              6: org.example.itests.multi:plugin
                              7: org.example.itests.multi:plugin-management
                            Enter project numbers separated by spaces, commas or semicolons: Selected projects: org.example.itests.multi:parent, org.example.itests.multi:dependency, org.example.itests.multi:plugin
                            Select semantic version bump for org.example.itests.multi:parent:\s
                              1: PATCH
                              2: MINOR
                              3: MAJOR
                            Enter semantic version name or number: Select semantic version bump for org.example.itests.multi:dependency:\s
                              1: PATCH
                              2: MINOR
                              3: MAJOR
                            Enter semantic version name or number: Select semantic version bump for org.example.itests.multi:plugin:\s
                              1: PATCH
                              2: MINOR
                              3: MAJOR
                            Enter semantic version name or number: Version bumps: 'org.example.itests.multi:dependency': MINOR, 'org.example.itests.multi:parent': PATCH, 'org.example.itests.multi:plugin': MAJOR
                            Please type the changelog entry here (enter empty line to open external editor, two empty lines after your input to end):
                            """);

            assertThat(mockedOutputFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .hasEntrySatisfying(
                            getVersioningMarkdown(),
                            writer -> assertThat(writer.toString())
                                    .startsWith("---\n")
                                    .contains("org.example.itests.multi:parent: \"PATCH\"\n")
                                    .contains("org.example.itests.multi:dependency: \"MINOR\"\n")
                                    .contains("org.example.itests.multi:plugin: \"MAJOR\"\n")
                                    .contains("---\n")
                                    .contains("Testing")
                    );

            if (Git.NO_GIT == gitMode) {
                assertThat(mockedExecutedProcesses)
                        .isEmpty();
            } else if (Git.STASH == gitMode) {
                assertThat(mockedExecutedProcesses)
                        .hasSize(1)
                        .containsExactly(List.of("git", "add", getVersioningMarkdown().toString()));
            } else {
                assertThat(mockedExecutedProcesses)
                        .hasSize(2)
                        .containsExactly(
                                List.of("git", "add", getVersioningMarkdown().toString()),
                                List.of("git", "commit", "-m", "Created version Markdown file for 3 project(s)")
                        );
            }
        }

        private Path getVersioningMarkdown() {
            return getResourcesPath("multi", ".versioning",
                    "versioning-%s.md".formatted(Utils.DATE_TIME_FORMATTER.format(DATE_TIME)));
        }
    }

    @Nested
    class SingleProjectExecutionTest {

        @BeforeEach
        void setUp() {
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                    getResourcesPath("single"),
                    Path.of(".")
            );
            classUnderTest.modus = Modus.PROJECT_VERSION;
        }

        @ParameterizedTest
        @EnumSource(value = SemanticVersionBump.class, names = {"MAJOR", "MINOR", "PATCH"})
        void dryRunInlineEditor_Valid(SemanticVersionBump bump) {
            classUnderTest.dryRun = true;

            try (MockedConstruction<Scanner> ignored = Mockito.mockConstruction(
                    Scanner.class, (mock, context) -> {
                        Mockito.when(mock.hasNextLine()).thenReturn(true, false);
                        if (context.getCount() == 1) {
                            Mockito.when(mock.nextLine()).thenReturn(bump.name());
                        } else {
                            Mockito.when(mock.nextLine()).thenReturn("Testing");
                        }
                    }
            )) {
                assertThatNoException()
                        .isThrownBy(classUnderTest::execute);
            }

            assertThat(testLog.getLogRecords())
                    .isNotEmpty()
                    .hasSize(3)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordDebug("""
                                    Version bumps YAML:
                                        org.example.itests.single:project: "%s"
                                    """.formatted(bump)),
                            validateLogRecordInfo("""
                                    Dry-run: new markdown file at %s:
                                    ---
                                    org.example.itests.single:project: "%s"
                                    
                                    ---
                                    
                                    Testing
                                    """.formatted(getSingleVersioningMarkdown(), bump))
                    );

            assertThat(outputStream.toString())
                    .isEqualTo("""
                            Project org.example.itests.single:project
                            Select semantic version bump:\s
                              1: PATCH
                              2: MINOR
                              3: MAJOR
                            Enter semantic version name or number: \
                            Version bumps: 'org.example.itests.single:project': %S
                            Please type the changelog entry here (enter empty line to open external editor, \
                            two empty lines after your input to end):
                            """.formatted(bump));

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedExecutedProcesses)
                    .isEmpty();
        }

        @Test
        void externalEditorFails_ThrowsMojoFailureException() throws InterruptedException {
            Mockito.when(processMock.waitFor())
                    .thenReturn(1);
            try (MockedConstruction<Scanner> ignored = Mockito.mockConstruction(
                    Scanner.class, (mock, context) -> {
                        if (context.getCount() == 1) {
                            Mockito.when(mock.hasNextLine()).thenReturn(true, false);
                            Mockito.when(mock.nextLine()).thenReturn("minor");
                        } else {
                            Mockito.when(mock.hasNextLine()).thenReturn(false);
                        }
                    }
            )) {
                assertThatThrownBy(classUnderTest::execute)
                        .isInstanceOf(MojoFailureException.class)
                        .hasMessage("Unable to create a new Markdown file in external editor.");
            }

            assertThat(testLog.getLogRecords())
                    .isNotEmpty()
                    .hasSize(2)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordDebug("""
                                    Version bumps YAML:
                                        org.example.itests.single:project: "MINOR"
                                    """)
                    );

            assertThat(outputStream.toString())
                    .isEqualTo("""
                            Project org.example.itests.single:project
                            Select semantic version bump:\s
                              1: PATCH
                              2: MINOR
                              3: MAJOR
                            Enter semantic version name or number: \
                            Version bumps: 'org.example.itests.single:project': MINOR
                            Please type the changelog entry here (enter empty line to open external editor, \
                            two empty lines after your input to end):
                            """);

            assertThat(mockedOutputFiles)
                    .isEmpty();
            assertThat(mockedExecutedProcesses)
                    .hasSize(1)
                    .containsExactly(
                            List.of("vi", TEMP_FILE.toString())
                    );
        }

        @ParameterizedTest
        @EnumSource(value = SemanticVersionBump.class, names = {"MAJOR", "MINOR", "PATCH"})
        void externalEditor_Valid(SemanticVersionBump bump) {
            classUnderTest.dryRun = false;

            try (MockedConstruction<Scanner> ignored = Mockito.mockConstruction(
                    Scanner.class, (mock, context) -> {
                        if (context.getCount() == 1) {
                            Mockito.when(mock.hasNextLine()).thenReturn(true, false);
                            Mockito.when(mock.nextLine()).thenReturn(bump.name());
                        } else {
                            Mockito.when(mock.hasNextLine()).thenReturn(false);
                        }
                    }
            )) {
                assertThatNoException()
                        .isThrownBy(classUnderTest::execute);
            }

            assertThat(testLog.getLogRecords())
                    .isNotEmpty()
                    .hasSize(3)
                    .satisfiesExactly(
                            validateLogRecordInfo("Execution for project: org.example.itests.single:project:1.0.0"),
                            validateLogRecordDebug("""
                                    Version bumps YAML:
                                        org.example.itests.single:project: "%s"
                                    """.formatted(bump)),
                            validateLogRecordInfo("Read 1 lines from %s".formatted(TEMP_FILE))
                    );

            assertThat(outputStream.toString())
                    .isEqualTo("""
                            Project org.example.itests.single:project
                            Select semantic version bump:\s
                              1: PATCH
                              2: MINOR
                              3: MAJOR
                            Enter semantic version name or number: \
                            Version bumps: 'org.example.itests.single:project': %S
                            Please type the changelog entry here (enter empty line to open external editor, \
                            two empty lines after your input to end):
                            """.formatted(bump));

            assertThat(mockedOutputFiles)
                    .isNotEmpty()
                    .hasSize(1)
                    .hasEntrySatisfying(
                            getSingleVersioningMarkdown(),
                            writer -> assertThat(writer.toString())
                                    .isEqualTo("""
                                            ---
                                            org.example.itests.single:project: "%s"
                                            
                                            ---
                                            
                                            Testing external
                                            """.formatted(bump))
                    );
            assertThat(mockedExecutedProcesses)
                    .hasSize(1)
                    .containsExactly(
                            List.of("vi", TEMP_FILE.toString())
                    );
        }

        @ParameterizedTest
        @EnumSource(Git.class)
        void inlineEditorGitIntegration_Valid(Git gitMode) {
            classUnderTest.dryRun = false;
            classUnderTest.git = gitMode;

            try (MockedConstruction<Scanner> ignored = Mockito.mockConstruction(
                    Scanner.class, (mock, context) -> {
                        Mockito.when(mock.hasNextLine()).thenReturn(true, false);
                        if (context.getCount() == 1) {
                            Mockito.when(mock.nextLine()).thenReturn(SemanticVersionBump.MAJOR.name());
                        } else {
                            Mockito.when(mock.nextLine()).thenReturn("Testing");
                        }
                    }
            )) {
                assertThatNoException()
                        .isThrownBy(classUnderTest::execute);
            }

            if (Git.NO_GIT == gitMode) {
                assertThat(mockedExecutedProcesses)
                        .isEmpty();
            } else if (Git.STASH == gitMode) {
                assertThat(mockedExecutedProcesses)
                        .hasSize(1)
                        .containsExactly(List.of("git", "add", getSingleVersioningMarkdown().toString()));
            } else {
                assertThat(mockedExecutedProcesses)
                        .hasSize(2)
                        .containsExactly(
                                List.of("git", "add", getSingleVersioningMarkdown().toString()),
                                List.of("git", "commit", "-m", "Created version Markdown file for 1 project(s)")
                        );
            }
        }

        private Path getSingleVersioningMarkdown() {
            return getResourcesPath("single", ".versioning",
                    "versioning-%s.md".formatted(Utils.DATE_TIME_FORMATTER.format(DATE_TIME)));
        }
    }
}
