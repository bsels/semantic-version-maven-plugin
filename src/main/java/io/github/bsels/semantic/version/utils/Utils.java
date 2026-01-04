package io.github.bsels.semantic.version.utils;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/// A utility class containing static constants and methods for various common operations.
/// This class is final and not intended to be instantiated.
public final class Utils {
    /// A constant string used as a suffix to represent backup files.
    /// Typically appended to filenames to indicate the file is a backup copy.
    public static final String BACKUP_SUFFIX = ".backup";

    /// Utility class containing static constants and methods for various common operations.
    /// This class is not designed to be instantiated.
    private Utils() {
        // No instance needed
    }

    /// Creates a backup of the specified file.
    /// The method copies the given file to a backup location in the same directory,
    /// replacing existing backups if necessary.
    ///
    /// @param file the path to the file to be backed up; must not be null
    /// @throws MojoExecutionException if an I/O error occurs during the backup operation
    public static void backupFile(Path file) throws MojoExecutionException {
        String fileName = file.getFileName().toString();
        Path backupPom = file.getParent()
                .resolve(fileName + BACKUP_SUFFIX);
        try {
            Files.copy(
                    file,
                    backupPom,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to backup %s to %s".formatted(file, backupPom), e);
        }
    }
}
