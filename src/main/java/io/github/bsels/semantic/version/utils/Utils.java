package io.github.bsels.semantic.version.utils;

import io.github.bsels.semantic.version.models.PlaceHolderWithType;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/// A utility class containing static constants and methods for various common operations.
/// This class is final and not intended to be instantiated.
public final class Utils {
    /// A constant string used as a suffix to represent backup files.
    /// Typically appended to filenames to indicate the file is a backup copy.
    public static final String BACKUP_SUFFIX = ".backup";
    /// A [DateTimeFormatter] instance used to format or parse date-time values according to the pattern
    /// `yyyyMMddHHmmss`.
    /// This formatter ensures that date-time values are represented in a compact string format with the following
    /// components:
    /// - Year: 4 digits
    /// - Month: 2 digits
    /// - Day: 2 digits
    /// - Hour: 2 digits (24-hour clock)
    /// - Minute: 2 digits
    /// - Second: 2 digits
    ///
    /// The formatter is thread-safe and can be used in concurrent environments.
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /// A regular expression pattern used to extract and match specific placeholders from a string.
    ///
    /// The placeholders supported by this pattern are:
    /// - `{date}`: A basic date placeholder.
    /// - `{date#<pattern>}`: A date placeholder that specifies a date formatting pattern
    /// within angled brackets following a '#' character.
    /// - `version`: A placeholder representing a version value.
    ///
    /// This pattern is mainly used for parsing or identifying templated strings that include
    /// dynamically replaceable placeholders for date and version values.
    private static final Pattern PLACEHOLDER_FORMAT_EXTRACTOR = Pattern.compile("\\{(date(#([^{}]*))?|version)}");

    private static final Map<String, DateTimeFormatter> CACHED_DATE_FORMATTERS = new HashMap<>();

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
    /// @throws NullPointerException   if the `file` argument is null
    public static void backupFile(Path file) throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(file, "`file` must not be null");
        String fileName = file.getFileName().toString();
        Path backupPom = file.getParent()
                .resolve(fileName + BACKUP_SUFFIX);
        if (!Files.exists(file)) {
            return;
        }
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

    /// Deletes the specified files if they exist.
    ///
    /// This method iterates over the collection of file paths, attempting to delete each file
    /// at the given path.
    /// If a file does not exist, no action is taken for that file.
    /// If an I/O error occurs during the deletion process, a [MojoExecutionException] is thrown.
    /// The collection of paths must not be null.
    ///
    /// @param paths the collection of file paths to be deleted; must not be null
    /// @throws NullPointerException   if the `paths` collection is null
    /// @throws MojoExecutionException if an I/O error occurs during the deletion process
    public static void deleteFilesIfExists(Collection<Path> paths) throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(paths, "`paths` must not be null");
        for (Path path : paths) {
            deleteFileIfExists(path);
        }
    }

    /// Deletes the specified file if it exists.
    ///
    /// This method attempts to delete the file at the given path.
    /// If the file does not exist, no action is taken.
    /// If an I/O error occurs during the deletion process, a [MojoExecutionException] is thrown.
    /// The path parameter cannot be null.
    ///
    /// @param path the path to the file to be deleted; must not be null
    /// @throws NullPointerException   if the `path` argument is null
    /// @throws MojoExecutionException if an I/O error occurs during the deletion process
    public static void deleteFileIfExists(Path path) throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(path, "`path` must not be null");
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to delete file", e);
        }
    }

    /// Creates a temporary Markdown file with a predefined prefix and suffix.
    ///
    /// The file is created in the default temporary-file directory, using the prefix "versioning-" and the suffix ".md".
    /// If the operation fails, a [MojoExecutionException] is thrown.
    ///
    /// @return the path to the created temporary Markdown file
    /// @throws MojoExecutionException if an I/O error occurs during the file creation process
    public static Path createTemporaryMarkdownFile() throws MojoExecutionException {
        try {
            return Files.createTempFile("versioning-", ".md");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create temporary file", e);
        }
    }

    /// Creates a directory at the specified path if it does not already exist.
    ///
    /// This method ensures that the directory structure for the given path is created,
    /// including any necessary but nonexistent parent directories.
    /// If the directory cannot be created due to an I/O error, a [MojoExecutionException] will be thrown.
    ///
    /// @param path the path of the directory to create; must not be null
    /// @throws NullPointerException   if the `path` parameter is null
    /// @throws MojoExecutionException if an I/O error occurs while attempting to create the directory
    public static void createDirectoryIfNotExists(Path path) throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(path, "`path` must not be null");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to create directory", e);
            }
        }
    }

    /// Resolves and returns the path to a versioning file within the specified folder.
    /// The file is named using the pattern "versioning-<current_datetime>.md",
    /// where <current_datetime> is formatted according to the predefined date-time formatter.
    ///
    /// @param folder the base folder where the versioning file will be resolved; must not be null
    /// @return the resolved path to the versioning file
    /// @throws NullPointerException if the `folder` parameter is null
    public static Path resolveVersioningFile(Path folder) throws NullPointerException {
        Objects.requireNonNull(folder, "`folder` must not be null");
        return folder.resolve("versioning-%s.md".formatted(DATE_TIME_FORMATTER.format(LocalDateTime.now())));
    }

    /// Prepares a format string by replacing placeholders defined in the given list of keys with formatted substitution
    /// placeholders using their formatType and position.
    ///
    /// @param formatString the string containing placeholders to be replaced. Must not be null.
    /// @param keys         a list of `PlaceHolderWithType` objects representing placeholders and their types. Each item in the list must not be null, and the list itself must also not be null.
    /// @return a string with all placeholders replaced by formatted substitution placeholders.
    /// @throws NullPointerException if `formatString`, `keys`, or any element in `keys` is null.
    public static String prepareFormatString(String formatString, List<PlaceHolderWithType> keys)
            throws NullPointerException {
        Objects.requireNonNull(formatString, "`formatString` must not be null");
        Objects.requireNonNull(keys, "`keys` must not be null");
        keys.forEach(key -> Objects.requireNonNull(key, "All keys must not be null"));
        for (int i = 0; i < keys.size(); i++) {
            PlaceHolderWithType currentKey = keys.get(i);
            formatString = formatString.replace(
                    "{" + currentKey.placeholder() + "}",
                    "%" + (i + 1) + "$" + currentKey.formatType()
            );
        }
        return formatString;
    }

    /// Formats the provided header line by replacing placeholders with the corresponding values.
    /// Placeholders include `{version}` for the version string
    /// and `{date}` or a custom date pattern for the date component.
    ///
    /// @param headerLine the header line string containing placeholders to be replaced; must not be null
    /// @param version    the version string to replace the `{version}` placeholder; must not be null
    /// @param date       the date object to replace the `{date}` or custom date pattern placeholders; must not be null
    /// @return a new string where placeholders in the header line are replaced with specified values
    /// @throws NullPointerException if any of the provided arguments are null
    public static String formatHeaderLine(String headerLine, String version, LocalDate date)
            throws NullPointerException {
        Objects.requireNonNull(headerLine, "`headerLine` must not be null");
        Objects.requireNonNull(version, "`version` must not be null");
        Objects.requireNonNull(date, "`date` must not be null");
        return PLACEHOLDER_FORMAT_EXTRACTOR.matcher(headerLine)
                .replaceAll(match -> replaceVersionOrDateOnMatch(version, date, match));
    }

    /// Replaces a placeholder in a matched pattern with either a version string or a formatted date,
    /// based on the placeholder's content.
    ///
    /// @param version the version string to replace the `{version}` placeholder
    /// @param date    the date to replace date-related placeholders
    /// @param match   the result of a regex match containing the placeholder to be replaced
    /// @return the replacement string for the matched placeholder, either the version or the formatted date
    private static String replaceVersionOrDateOnMatch(String version, LocalDate date, MatchResult match) {
        String placeholder = match.group(0);
        if ("{version}".equals(placeholder)) {
            return version;
        }
        final DateTimeFormatter formatter;
        if ("{date}".equals(placeholder)) {
            formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        } else {
            formatter = CACHED_DATE_FORMATTERS.computeIfAbsent(match.group(3), DateTimeFormatter::ofPattern);
        }
        return date.format(formatter);
    }

    /// Returns a predicate that always evaluates to `true`.
    ///
    /// @param <T> the type of the input to the predicate
    /// @return a predicate that evaluates to `true` for any input
    public static <T> Predicate<T> alwaysTrue() {
        return ignored -> true;
    }

    /// Returns a predicate that evaluates to true if the given Maven project has no modules.
    ///
    /// @return a predicate that checks whether a Maven project has no modules
    public static Predicate<MavenProject> mavenProjectHasNoModules() {
        return project -> project.getModules().isEmpty();
    }

    /// Converts a [BiConsumer] accumulator into a [BinaryOperator].
    /// The resulting operator applies the given accumulator on two arguments
    /// and returns the first argument as the result.
    ///
    /// @param <T>         the type of the input and output of the operation
    /// @param accumulator a [BiConsumer] that performs a combination operation on two inputs
    /// @return a [BinaryOperator] that combines two inputs using the provided accumulator
    public static <T> BinaryOperator<T> consumerToOperator(BiConsumer<T, ? super T> accumulator) {
        Objects.requireNonNull(accumulator, "`accumulator` must not be null");
        return (a, b) -> {
            accumulator.accept(a, b);
            return a;
        };
    }

    /// Returns a [Collector] that groups input elements by a classifier function, applies a downstream
    /// [Collector] to the values for each key, and produces an immutable [Map].
    ///
    /// @param <T>        the type of the input elements
    /// @param <K>        the type of the keys
    /// @param <A>        the intermediate accumulation type of the downstream [Collector]
    /// @param <D>        the result type of the downstream reduction
    /// @param classifier a function to classify input elements
    /// @param downstream a collector to reduce the values associated with a given key
    /// @return a [Collector] that groups elements by a classification function and produces an immutable [Map]
    public static <T, K, A, D> Collector<T, ?, Map<K, D>> groupingByImmutable(
            Function<? super T, ? extends K> classifier,
            Collector<? super T, A, D> downstream
    ) {
        return Collectors.collectingAndThen(
                Collectors.groupingBy(classifier, downstream),
                Map::copyOf
        );
    }

    /// Returns a collector that wraps the given downstream collector and produces an immutable list as its
    /// final result.
    /// The resulting collector applies the downstream collection and then creates an immutable view over
    /// the resulting list.
    ///
    /// @param <T>        the type of input elements to the collector
    /// @param <E>        the type of elements in the resulting list
    /// @param downstream the downstream collector to accumulate elements
    /// @return a collector that produces an immutable list as the final result
    /// @see #asImmutableList
    public static <T, E> Collector<T, ?, List<E>> asImmutableList(Collector<T, ?, List<E>> downstream) {
        return Collectors.collectingAndThen(downstream, List::copyOf);
    }

    /// Returns a collector that accumulates elements into a list and produces an immutable copy of that list as
    /// the final result.
    ///
    /// @param <T> the type of input elements to the collector
    /// @return a collector that produces an immutable list of the collected elements
    /// @see #asImmutableList(Collector)
    public static <T> Collector<T, ?, List<T>> asImmutableList() {
        return asImmutableList(Collectors.toList());
    }

    /// Returns a collector that accumulates elements into a set and produces an immutable copy of that set as the final
    /// result.
    /// The resulting set is unmodifiable and guarantees immutability.
    ///
    /// @param <T> the type of input elements to the collector
    /// @return a collector that produces an immutable set of the collected elements
    public static <T> Collector<T, ?, Set<T>> asImmutableSet() {
        return Collectors.collectingAndThen(Collectors.toSet(), Set::copyOf);
    }
}
