package io.github.bsels.semantic.version.utils;

import io.github.bsels.semantic.version.models.VersionChange;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    /// Executes the given script within the context of a specified project directory and applies version-related
    /// environment variables.
    /// Optionally, the execution can be a dry run or include Git stash behavior.
    ///
    /// @param script        the path to the script to be executed; must not be null
    /// @param projectPath   the path to the project directory in which the script is executed; must not be null
    /// @param versionChange an instance of [VersionChange] representing the old and new version values; must not be null
    /// @param dryRun        a boolean flag indicating whether the operation should simulate changes without applying them
    /// @param stash         a boolean flag indicating whether Git stash behavior should be applied during execution
    /// @throws NullPointerException   if any of the `script`, `projectPath`, or `versionChange` arguments are null
    /// @throws MojoExecutionException if an I/O or interruption error occurs during script execution, or if the process exits with a non-zero status code
    public static void executeScripts(
            Path script, Path projectPath, VersionChange versionChange, boolean dryRun, boolean stash
    ) throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(script, "`script` must not be null");
        Objects.requireNonNull(projectPath, "`projectPath` must not be null");
        Objects.requireNonNull(versionChange, "`versionChange` must not be null");

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(script.toString());
            Map<String, String> environment = processBuilder.environment();
            environment.put("CURRENT_VERSION", versionChange.oldVersion());
            environment.put("NEW_VERSION", versionChange.newVersion());
            environment.put("DRY_RUN", Boolean.toString(dryRun));
            environment.put("GIT_STASH", Boolean.toString(stash));
            environment.put("EXECUTION_DATE", LocalDate.now().toString());
            Process process = processBuilder.directory(projectPath.toFile())
                    .inheritIO()
                    .start();
            if (process.waitFor() != 0) {
                throw new MojoExecutionException("Script execution failed.");
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Script execution failed.", e);
        }
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
