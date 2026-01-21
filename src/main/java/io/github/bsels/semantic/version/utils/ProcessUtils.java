package io.github.bsels.semantic.version.utils;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

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

    /// Stages the specified files for a Git stash operation.
    /// This method ensures the given list of files is non-null, non-empty, and contains no null elements.
    /// It executes a Git command to add the provided files to staging.
    ///
    /// @param files the list of file paths to be stashed; must not be null, empty, or contain null elements
    /// @throws NullPointerException     if the `files` list or any element within the list is null
    /// @throws IllegalArgumentException if the `files` list is empty
    /// @throws MojoExecutionException   if the Git command execution fails
    public static void gitStashFiles(List<Path> files)
            throws IllegalArgumentException, NullPointerException, MojoExecutionException {
        Objects.requireNonNull(files, "`files` must not be null");
        files.forEach(file -> Objects.requireNonNull(file, "`file` in `files` must not be null"));
        if (files.isEmpty()) {
            throw new IllegalArgumentException("`files` must not be empty");
        }
        List<String> command = Stream.concat(
                Stream.of("git", "add"),
                files.stream().map(Path::toString)
        ).toList();
        executeGitCommand(command, "Unable to add files to Git stash");
    }

    /// Commits staged changes in a Git repository with the given commit message.
    /// This method constructs a Git commit command using the provided message and executes it as a system process.
    ///
    /// @param message the commit message to be used for the Git commit; must not be null
    /// @throws NullPointerException   if the `message` is null
    /// @throws MojoExecutionException if the Git command execution fails
    public static void gitCommit(String message) throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(message, "`message` must not be null");
        executeGitCommand(
                List.of("git", "commit", "-m", message),
                "Unable to commit changes"
        );
    }

    /// Executes the specified Git command as a system process and monitors its exit status.
    /// This method blocks until the process completes
    /// and throws an exception if the process exits with a non-zero status code.
    ///
    /// @param command            the list of strings representing the Git command and its arguments; must not be null
    /// @param processExitNonZero the error message to throw if the process exits with a non-zero status code; must not be null
    /// @throws MojoExecutionException if an I/O error, process interruption, or non-zero exit status occurs
    private static void executeGitCommand(List<String> command, String processExitNonZero)
            throws MojoExecutionException {
        try {
            Process process = new ProcessBuilder()
                    .command(command)
                    .inheritIO()
                    .start();
            if (process.waitFor() != 0) {
                throw new MojoExecutionException(processExitNonZero);
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException(processExitNonZero, e);
        }
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
