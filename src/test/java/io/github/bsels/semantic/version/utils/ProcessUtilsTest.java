package io.github.bsels.semantic.version.utils;

import io.github.bsels.semantic.version.models.VersionChange;
import org.apache.maven.plugin.MojoExecutionException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class ProcessUtilsTest {

    @Mock
    Process process;

    private String originalVisual;
    private String originalEditor;
    private String originalOsName;

    @BeforeEach
    void setUp() {
        // Save original system properties
        originalVisual = System.getProperty("VISUAL");
        originalEditor = System.getProperty("EDITOR");
        originalOsName = System.getProperty("os.name");
    }

    @AfterEach
    void tearDown() {
        // Restore original system properties
        restoreSystemProperty("VISUAL", originalVisual);
        restoreSystemProperty("EDITOR", originalEditor);
        restoreSystemProperty("os.name", originalOsName);
    }

    private void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Nested
    class GetDefaultEditorTest {

        @Test
        void visualPropertySet_ReturnsVisual() {
            System.setProperty("VISUAL", "vim");
            System.setProperty("EDITOR", "nano");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("vim");
        }

        @Test
        void visualPropertySetWithWhitespace_ReturnsStrippedVisual() {
            System.setProperty("VISUAL", "  vim  ");
            System.setProperty("EDITOR", "nano");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("vim");
        }

        @Test
        void visualPropertyBlank_FallsBackToEditor() {
            System.setProperty("VISUAL", "   ");
            System.setProperty("EDITOR", "nano");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("nano");
        }

        @Test
        void visualPropertyEmpty_FallsBackToEditor() {
            System.setProperty("VISUAL", "");
            System.setProperty("EDITOR", "emacs");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("emacs");
        }

        @Test
        void onlyEditorPropertySet_ReturnsEditor() {
            System.clearProperty("VISUAL");
            System.setProperty("EDITOR", "nano");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("nano");
        }

        @Test
        void editorPropertySetWithWhitespace_ReturnsStrippedEditor() {
            System.clearProperty("VISUAL");
            System.setProperty("EDITOR", "  nano  ");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("nano");
        }

        @Test
        void noPropertiesSet_WindowsOs_ReturnsNotepad() {
            System.clearProperty("VISUAL");
            System.clearProperty("EDITOR");
            System.setProperty("os.name", "Windows 10");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("notepad");
        }

        @Test
        void noPropertiesSet_WindowsOs_CaseInsensitive_ReturnsNotepad() {
            System.clearProperty("VISUAL");
            System.clearProperty("EDITOR");
            System.setProperty("os.name", "WINDOWS 11");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("notepad");
        }

        @Test
        void noPropertiesSet_LinuxOs_ReturnsVi() {
            System.clearProperty("VISUAL");
            System.clearProperty("EDITOR");
            System.setProperty("os.name", "Linux");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("vi");
        }

        @Test
        void noPropertiesSet_MacOs_ReturnsVi() {
            System.clearProperty("VISUAL");
            System.clearProperty("EDITOR");
            System.setProperty("os.name", "Mac OS X");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("vi");
        }

        @Test
        void editorPropertyBlank_FallsBackToOsDefault() {
            System.clearProperty("VISUAL");
            System.setProperty("EDITOR", "   ");
            System.setProperty("os.name", "Linux");

            assertThat(ProcessUtils.getDefaultEditor())
                    .isEqualTo("vi");
        }
    }

    @Nested
    class ExecuteEditorTest {

        @Test
        void nullInput_ThrowsNullPointerException() {
            assertThatThrownBy(() -> ProcessUtils.executeEditor(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`file` must not be null");
        }

        @Test
        void processExitsWithZero_ReturnsTrue() throws Exception {
            Path file = Path.of("test.md");
            System.setProperty("VISUAL", "vim");

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(ProcessBuilder.class,
                    (mock, context) -> {
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    })) {

                Mockito.when(process.waitFor()).thenReturn(0);

                boolean result = ProcessUtils.executeEditor(file);

                assertThat(result).isTrue();
                assertThat(mockedBuilder.constructed()).hasSize(1);
                ProcessBuilder builder = mockedBuilder.constructed().get(0);
                Mockito.verify(builder).inheritIO();
                Mockito.verify(builder).start();
                Mockito.verify(process).waitFor();
            }
        }

        @Test
        void processExitsWithNonZero_ReturnsFalse() throws Exception {
            Path file = Path.of("test.md");
            System.setProperty("EDITOR", "nano");

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(ProcessBuilder.class,
                    (mock, context) -> {
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    })) {

                Mockito.when(process.waitFor()).thenReturn(1);

                boolean result = ProcessUtils.executeEditor(file);

                assertThat(result).isFalse();
                assertThat(mockedBuilder.constructed()).hasSize(1);
                Mockito.verify(process).waitFor();
            }
        }

        @Test
        void processBuilderThrowsIOException_ThrowsMojoExecutionException() {
            Path file = Path.of("test.md");
            System.setProperty("VISUAL", "vim");
            IOException ioException = new IOException("Failed to start process");

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(ProcessBuilder.class,
                    (mock, context) -> {
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenThrow(ioException);
                    })) {

                assertThatThrownBy(() -> ProcessUtils.executeEditor(file))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to execute editor")
                        .hasCause(ioException);

                assertThat(mockedBuilder.constructed()).hasSize(1);
            }
        }

        @Test
        void processWaitForThrowsInterruptedException_ThrowsMojoExecutionException() throws Exception {
            Path file = Path.of("test.md");
            System.setProperty("VISUAL", "vim");
            InterruptedException interruptedException = new InterruptedException("Process interrupted");

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(ProcessBuilder.class,
                    (mock, context) -> {
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    })) {

                Mockito.when(process.waitFor()).thenThrow(interruptedException);

                assertThatThrownBy(() -> ProcessUtils.executeEditor(file))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to execute editor")
                        .hasCause(interruptedException);

                assertThat(mockedBuilder.constructed()).hasSize(1);
                Mockito.verify(process).waitFor();
            }
        }

        @Test
        void usesCorrectEditorFromVisualProperty() throws Exception {
            Path file = Path.of("/tmp/changelog.md");
            System.setProperty("VISUAL", "emacs");

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(ProcessBuilder.class,
                    (mock, context) -> {
                        validateProcessArguments(context, "emacs", file);
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    })) {

                Mockito.when(process.waitFor()).thenReturn(0);

                ProcessUtils.executeEditor(file);

                assertThat(mockedBuilder.constructed()).hasSize(1);
            }
        }

        @Test
        void usesCorrectEditorFromEditorProperty() throws Exception {
            Path file = Path.of("/tmp/changelog.md");
            System.clearProperty("VISUAL");
            System.setProperty("EDITOR", "nano");

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(ProcessBuilder.class,
                    (mock, context) -> {
                        validateProcessArguments(context, "nano", file);
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    })) {

                Mockito.when(process.waitFor()).thenReturn(0);

                ProcessUtils.executeEditor(file);

                assertThat(mockedBuilder.constructed()).hasSize(1);
            }
        }

        @Test
        void usesCorrectFallbackEditorForWindows() throws Exception {
            Path file = Path.of("C:\\temp\\changelog.md");
            System.clearProperty("VISUAL");
            System.clearProperty("EDITOR");
            System.setProperty("os.name", "Windows 10");

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(ProcessBuilder.class,
                    (mock, context) -> {
                        validateProcessArguments(context, "notepad", file);
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    })) {

                Mockito.when(process.waitFor()).thenReturn(0);

                ProcessUtils.executeEditor(file);

                assertThat(mockedBuilder.constructed()).hasSize(1);
            }
        }

        @Test
        void usesCorrectFallbackEditorForLinux() throws Exception {
            Path file = Path.of("/tmp/changelog.md");
            System.clearProperty("VISUAL");
            System.clearProperty("EDITOR");
            System.setProperty("os.name", "Linux");

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(ProcessBuilder.class,
                    (mock, context) -> {
                        validateProcessArguments(context, "vi", file);
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    })) {

                Mockito.when(process.waitFor()).thenReturn(0);

                ProcessUtils.executeEditor(file);

                assertThat(mockedBuilder.constructed()).hasSize(1);
            }
        }

        private void validateProcessArguments(MockedConstruction.Context context, String editor, Path file) {
            assertThat(context.arguments())
                    .isNotNull()
                    .isNotEmpty()
                    .hasSize(1)
                    .first()
                    .asInstanceOf(InstanceOfAssertFactories.array(String[].class))
                    .containsExactly(editor, file.toString());
        }
    }

    @Nested
    class ExecuteScriptsTest {

        @Test
        void nullScript_ThrowsNullPointerException() {
            assertThatThrownBy(() -> ProcessUtils.executeScripts(
                    null, Path.of("."), new VersionChange("1.0.0", "1.1.0"), false, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`script` must not be null");
        }

        @Test
        void nullProjectPath_ThrowsNullPointerException() {
            assertThatThrownBy(() -> ProcessUtils.executeScripts(
                    Path.of("script.sh"), null, new VersionChange("1.0.0", "1.1.0"), false, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`projectPath` must not be null");
        }

        @Test
        void nullVersionChange_ThrowsNullPointerException() {
            assertThatThrownBy(() -> ProcessUtils.executeScripts(
                    Path.of("script.sh"), Path.of("."), null, false, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`versionChange` must not be null");
        }

        @Test
        void processZeroExit_SetsEnvironmentAndUsesProjectDirectory() throws Exception {
            Path script = Path.of("script.sh");
            Path projectPath = Path.of("/tmp/project");
            VersionChange versionChange = new VersionChange("1.2.3", "2.0.0");
            Map<String, String> environment = new HashMap<>();
            LocalDate before = LocalDate.now();

            try (MockedConstruction<ProcessBuilder> mockedBuilder = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        validateScriptArguments(context, script);
                        Mockito.when(mock.environment()).thenReturn(environment);
                        Mockito.when(mock.directory(Mockito.any())).thenReturn(mock);
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    }
            )) {
                Mockito.when(process.waitFor()).thenReturn(0);

                assertThatNoException().isThrownBy(() -> ProcessUtils.executeScripts(
                        script, projectPath, versionChange, true, false));

                assertThat(mockedBuilder.constructed()).hasSize(1);
                ProcessBuilder builder = mockedBuilder.constructed().get(0);
                Mockito.verify(builder).directory(projectPath.toFile());
                Mockito.verify(builder).inheritIO();
                Mockito.verify(builder).start();
            }

            LocalDate after = LocalDate.now();
            assertThat(environment)
                    .containsEntry("CURRENT_VERSION", "1.2.3")
                    .containsEntry("NEW_VERSION", "2.0.0")
                    .containsEntry("DRY_RUN", "true")
                    .containsEntry("GIT_STASH", "false");
            assertThat(environment.get("EXECUTION_DATE"))
                    .isIn(before.toString(), after.toString());
        }

        @Test
        void processNonZeroExit_ThrowsMojoExecutionException() throws InterruptedException {
            Path script = Path.of("script.sh");
            Path projectPath = Path.of("/tmp/project");
            VersionChange versionChange = new VersionChange("1.2.3", "2.0.0");
            Map<String, String> environment = new HashMap<>();

            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        validateScriptArguments(context, script);
                        Mockito.when(mock.environment()).thenReturn(environment);
                        Mockito.when(mock.directory(Mockito.any())).thenReturn(mock);
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    }
            )) {
                Mockito.when(process.waitFor()).thenReturn(1);

                assertThatThrownBy(() -> ProcessUtils.executeScripts(
                        script, projectPath, versionChange, false, true))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Script execution failed.");
            }
        }

        @Test
        void processStartThrowsIOException_ThrowsMojoExecutionException() {
            Path script = Path.of("script.sh");
            Path projectPath = Path.of("/tmp/project");
            VersionChange versionChange = new VersionChange("1.2.3", "2.0.0");
            Map<String, String> environment = new HashMap<>();
            IOException ioException = new IOException("Start failed");

            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        validateScriptArguments(context, script);
                        Mockito.when(mock.environment()).thenReturn(environment);
                        Mockito.when(mock.directory(Mockito.any())).thenReturn(mock);
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenThrow(ioException);
                    }
            )) {
                assertThatThrownBy(() -> ProcessUtils.executeScripts(
                        script, projectPath, versionChange, false, false))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Script execution failed.")
                        .hasCause(ioException);
            }
        }

        @Test
        void processWaitForThrowsInterruptedException_ThrowsMojoExecutionException() throws Exception {
            Path script = Path.of("script.sh");
            Path projectPath = Path.of("/tmp/project");
            VersionChange versionChange = new VersionChange("1.2.3", "2.0.0");
            Map<String, String> environment = new HashMap<>();
            InterruptedException interruptedException = new InterruptedException("Interrupted");

            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        validateScriptArguments(context, script);
                        Mockito.when(mock.environment()).thenReturn(environment);
                        Mockito.when(mock.directory(Mockito.any())).thenReturn(mock);
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                    }
            )) {
                Mockito.when(process.waitFor()).thenThrow(interruptedException);

                assertThatThrownBy(() -> ProcessUtils.executeScripts(
                        script, projectPath, versionChange, false, false))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Script execution failed.")
                        .hasCause(interruptedException);
            }
        }

        private void validateScriptArguments(MockedConstruction.Context context, Path script) {
            assertThat(context.arguments())
                    .isNotNull()
                    .isNotEmpty()
                    .hasSize(1)
                    .first()
                    .asInstanceOf(InstanceOfAssertFactories.array(String[].class))
                    .containsExactly(script.toString());
        }
    }

    @Nested
    class GitCommitTest {

        @Test
        void nullMessage_ThrowNullPointerException() {
            assertThatThrownBy(() -> ProcessUtils.gitCommit(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`message` must not be null");
        }

        @Test
        void processCreationFailed_ThrowsMojoExecutionException() {
            AtomicReference<List<String>> command = new AtomicReference<>();
            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        Mockito.when(mock.command(Mockito.anyList()))
                                .thenAnswer(invocation -> {
                                    command.set(invocation.getArgument(0));
                                    return mock;
                                });
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start())
                                .thenThrow(IOException.class);
                    }
            )) {
                assertThatThrownBy(() -> ProcessUtils.gitCommit("Test commit"))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to commit changes")
                        .hasRootCauseInstanceOf(IOException.class);
            }

            assertThat(command.get())
                    .containsExactly("git", "commit", "-m", "Test commit");
        }

        @Test
        void processInterrupted_ThrowsMojoExecutionException() {
            AtomicReference<List<String>> command = new AtomicReference<>();
            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        Mockito.when(mock.command(Mockito.anyList()))
                                .thenAnswer(invocation -> {
                                    command.set(invocation.getArgument(0));
                                    return mock;
                                });
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                        Mockito.when(process.waitFor())
                                .thenThrow(InterruptedException.class);
                    }
            )) {
                assertThatThrownBy(() -> ProcessUtils.gitCommit("Test commit 2"))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to commit changes")
                        .hasRootCauseInstanceOf(InterruptedException.class);
            }

            assertThat(command.get())
                    .containsExactly("git", "commit", "-m", "Test commit 2");
        }

        @Test
        void processNonZeroExit_ThrowsMojoExecutionException() {
            AtomicReference<List<String>> command = new AtomicReference<>();
            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        Mockito.when(mock.command(Mockito.anyList()))
                                .thenAnswer(invocation -> {
                                    command.set(invocation.getArgument(0));
                                    return mock;
                                });
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                        Mockito.when(process.waitFor())
                                .thenReturn(1);
                    }
            )) {
                assertThatThrownBy(() -> ProcessUtils.gitCommit("Test commit 3"))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to commit changes");
            }

            assertThat(command.get())
                    .containsExactly("git", "commit", "-m", "Test commit 3");
        }

        @Test
        void processZeroExit_Success() {
            AtomicReference<List<String>> command = new AtomicReference<>();
            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        Mockito.when(mock.command(Mockito.anyList()))
                                .thenAnswer(invocation -> {
                                    command.set(invocation.getArgument(0));
                                    return mock;
                                });
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                        Mockito.when(process.waitFor())
                                .thenReturn(0);
                    }
            )) {
                assertThatNoException()
                        .isThrownBy(() -> ProcessUtils.gitCommit("Test commit 4"));
            }

            assertThat(command.get())
                    .containsExactly("git", "commit", "-m", "Test commit 4");
        }
    }

    @Nested
    class GitStashFilesTest {
        private static final Path TEST_POM = Path.of("pom.xml");
        private static final Path TEST_CHANGELOG = Path.of("CHANGELOG.md");

        @Test
        void nullFiles_ThrowsNullPointerException() {
            assertThatThrownBy(() -> ProcessUtils.gitStashFiles(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`files` must not be null");
        }

        @Test
        void emptyFiles_ThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> ProcessUtils.gitStashFiles(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("`files` must not be empty");
        }

        @Test
        void nullFileInFiles_ThrowsNullPointerException() {
            assertThatThrownBy(() -> ProcessUtils.gitStashFiles(Collections.singletonList(null)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`file` in `files` must not be null");
        }

        @Test
        void processCreationFailed_ThrowsMojoExecutionException() {
            AtomicReference<List<String>> command = new AtomicReference<>();
            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        Mockito.when(mock.command(Mockito.anyList()))
                                .thenAnswer(invocation -> {
                                    command.set(invocation.getArgument(0));
                                    return mock;
                                });
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start())
                                .thenThrow(IOException.class);
                    }
            )) {
                assertThatThrownBy(() -> ProcessUtils.gitStashFiles(List.of(TEST_POM)))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to add files to Git stash")
                        .hasRootCauseInstanceOf(IOException.class);
            }

            assertThat(command.get())
                    .containsExactly("git", "add", TEST_POM.toString());
        }

        @Test
        void processInterrupted_ThrowsMojoExecutionException() {
            AtomicReference<List<String>> command = new AtomicReference<>();
            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        Mockito.when(mock.command(Mockito.anyList()))
                                .thenAnswer(invocation -> {
                                    command.set(invocation.getArgument(0));
                                    return mock;
                                });
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);

                        Mockito.when(process.waitFor())
                                .thenThrow(InterruptedException.class);
                    }
            )) {
                assertThatThrownBy(() -> ProcessUtils.gitStashFiles(List.of(TEST_CHANGELOG)))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to add files to Git stash")
                        .hasRootCauseInstanceOf(InterruptedException.class);
            }

            assertThat(command.get())
                    .containsExactly("git", "add", TEST_CHANGELOG.toString());
        }

        @Test
        void processNonZeroExit_ThrowsMojoExecutionException() {
            AtomicReference<List<String>> command = new AtomicReference<>();
            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        Mockito.when(mock.command(Mockito.anyList()))
                                .thenAnswer(invocation -> {
                                    command.set(invocation.getArgument(0));
                                    return mock;
                                });
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                        Mockito.when(process.waitFor())
                                .thenReturn(1);
                    }
            )) {
                assertThatThrownBy(() -> ProcessUtils.gitStashFiles(List.of(TEST_POM, TEST_CHANGELOG)))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to add files to Git stash");
            }

            assertThat(command.get())
                    .containsExactly("git", "add", TEST_POM.toString(), TEST_CHANGELOG.toString());
        }

        @Test
        void processZeroExit_Success() {
            AtomicReference<List<String>> command = new AtomicReference<>();
            try (MockedConstruction<ProcessBuilder> ignored = Mockito.mockConstruction(
                    ProcessBuilder.class, (mock, context) -> {
                        Mockito.when(mock.command(Mockito.anyList()))
                                .thenAnswer(invocation -> {
                                    command.set(invocation.getArgument(0));
                                    return mock;
                                });
                        Mockito.when(mock.inheritIO()).thenReturn(mock);
                        Mockito.when(mock.start()).thenReturn(process);
                        Mockito.when(process.waitFor())
                                .thenReturn(0);
                    }
            )) {
                assertThatNoException()
                        .isThrownBy(() -> ProcessUtils.gitStashFiles(List.of(TEST_CHANGELOG, TEST_POM)));
            }

            assertThat(command.get())
                    .containsExactly("git", "add", TEST_CHANGELOG.toString(), TEST_POM.toString());
        }
    }
}
