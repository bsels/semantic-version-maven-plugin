package io.github.bsels.semantic.version.utils;

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

import static org.assertj.core.api.Assertions.assertThat;
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
}
