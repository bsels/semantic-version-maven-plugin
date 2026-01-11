package io.github.bsels.semantic.version.utils;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
