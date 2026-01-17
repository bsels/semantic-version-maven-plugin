package io.github.bsels.semantic.version.utils;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/// Utility class providing methods for handling processes and editors.
/// This class is not intended to be instantiated.
public final class ProcessUtils {

    /// Utility class providing methods for handling processes and editors.
    /// This class is not intended to be instantiated.
    private ProcessUtils() {
        // No instance needed
    }

    /// Executes the default system editor to open a given file.
    /// The editor is determined to use system properties or a fallback mechanism.
    /// This method blocks until the editor process completes.
    ///
    /// @param file the path to the file that should be opened in the editor
    /// @return true if the editor process exits with a status code of 0, false otherwise
    /// @throws NullPointerException   if the `file` argument is null
    /// @throws MojoExecutionException if an I/O or interruption error occurs while executing the editor
    public static boolean executeEditor(Path file) throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(file, "`file` must not be null");
        try {
            Process process = new ProcessBuilder(getDefaultEditor(), file.toString())
                    .inheritIO()
                    .start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Unable to execute editor", e);
        }
    }

    /// Retrieves the default editor based on system properties or a fallback mechanism.
    /// The method checks the "VISUAL" system property first, followed by the "EDITOR" system property,
    /// and uses a fallback editor determined by the operating system if neither property is set.
    ///
    /// @return The name of the default editor as a String, or the operating-system-specific fallback editor ("notepad" for Windows or "vi" for other systems) if no editor is explicitly specified.
    public static String getDefaultEditor() {
        return Optional.ofNullable(System.getProperty("VISUAL"))
                .map(String::strip)
                .filter(Predicate.not(String::isBlank))
                .or(() -> Optional.ofNullable(System.getProperty("EDITOR")))
                .map(String::strip)
                .filter(Predicate.not(String::isBlank))
                .orElseGet(ProcessUtils::fallbackOsEditor);
    }

    /// Determines the fallback text editor based on the operating system.
    /// If the operating system is identified as Windows, the method returns "notepad".
    /// Otherwise, it returns "vi" as a default editor for non-Windows systems.
    ///
    /// @return The default fallback text editor name based on the operating system.
    private static String fallbackOsEditor() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "notepad" : "vi";
    }
}
