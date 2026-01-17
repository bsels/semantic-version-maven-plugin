package io.github.bsels.semantic.version.utils;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class UtilsTest {

    @Mock
    MavenProject mavenProject;

    @Nested
    class BackupFileTest {

        @Test
        void nullInput_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.backupFile(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`file` must not be null");
        }

        @Test
        void nonExistingFile_DoNothing() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path file = Path.of("project/pom.xml");
                files.when(() -> Files.exists(file))
                        .thenReturn(false);

                assertThatNoException()
                        .isThrownBy(() -> Utils.backupFile(file));

                files.verify(() -> Files.copy(
                        Mockito.any(Path.class),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any()
                ), Mockito.never());
            }
        }

        @Test
        void copyFailed_ThrowsMojoExceptionException() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                files.when(() -> Files.copy(Mockito.any(Path.class), Mockito.any(), Mockito.any(CopyOption[].class)))
                        .thenThrow(new IOException("copy failed"));

                Path file = Path.of("project/pom.xml");
                files.when(() -> Files.exists(file))
                        .thenReturn(true);
                Path backupFile = Path.of("project/pom.xml" + Utils.BACKUP_SUFFIX);
                assertThatThrownBy(() -> Utils.backupFile(file))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Failed to backup %s to %s".formatted(file, backupFile))
                        .hasRootCauseInstanceOf(IOException.class)
                        .hasRootCauseMessage("copy failed");

                files.verify(() -> Files.copy(
                        file,
                        backupFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING
                ), Mockito.times(1));
            }
        }

        @Test
        void copySuccess_NoErrors() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path backupFile = Path.of("project/pom.xml" + Utils.BACKUP_SUFFIX);
                files.when(() -> Files.copy(Mockito.any(Path.class), Mockito.any(), Mockito.any(CopyOption[].class)))
                        .thenReturn(backupFile);

                Path file = Path.of("project/pom.xml");
                files.when(() -> Files.exists(file))
                        .thenReturn(true);
                assertThatNoException()
                        .isThrownBy(() -> Utils.backupFile(file));

                files.verify(() -> Files.copy(
                        file,
                        backupFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING
                ), Mockito.times(1));
            }
        }
    }

    @Nested
    class CreateTemporaryMarkdownFileTest {

        @Test
        void createTempFileSuccess_ReturnsPath() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path expectedPath = Path.of("/tmp/versioning-12345.md");
                files.when(() -> Files.createTempFile("versioning-", ".md"))
                        .thenReturn(expectedPath);

                assertThatNoException()
                        .isThrownBy(() -> {
                            Path actualPath = Utils.createTemporaryMarkdownFile();
                            assertThat(actualPath).isEqualTo(expectedPath);
                        });

                files.verify(() -> Files.createTempFile("versioning-", ".md"), Mockito.times(1));
            }
        }

        @Test
        void createTempFileFails_ThrowsMojoExecutionException() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                IOException ioException = new IOException("Unable to create temp file");
                files.when(() -> Files.createTempFile("versioning-", ".md"))
                        .thenThrow(ioException);

                assertThatThrownBy(Utils::createTemporaryMarkdownFile)
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Failed to create temporary file")
                        .hasCause(ioException);

                files.verify(() -> Files.createTempFile("versioning-", ".md"), Mockito.times(1));
            }
        }
    }

    @Nested
    class DeleteFileIfExistsTest {

        @Test
        void nullInput_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.deleteFileIfExists(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`path` must not be null");
        }

        @Test
        void fileDoesNotExist_NoException() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path file = Path.of("project/file.txt");
                files.when(() -> Files.deleteIfExists(file))
                        .thenReturn(false);

                assertThatNoException()
                        .isThrownBy(() -> Utils.deleteFileIfExists(file));

                files.verify(() -> Files.deleteIfExists(file), Mockito.times(1));
            }
        }

        @Test
        void fileExists_DeletesSuccessfully() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path file = Path.of("project/file.txt");
                files.when(() -> Files.deleteIfExists(file))
                        .thenReturn(true);

                assertThatNoException()
                        .isThrownBy(() -> Utils.deleteFileIfExists(file));

                files.verify(() -> Files.deleteIfExists(file), Mockito.times(1));
            }
        }

        @Test
        void deletionFails_ThrowsMojoExecutionException() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path file = Path.of("project/file.txt");
                IOException ioException = new IOException("deletion failed");
                files.when(() -> Files.deleteIfExists(file))
                        .thenThrow(ioException);

                assertThatThrownBy(() -> Utils.deleteFileIfExists(file))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasCause(ioException);

                files.verify(() -> Files.deleteIfExists(file), Mockito.times(1));
            }
        }
    }

    @Nested
    class DeleteFilesIfExistsTest {

        @Test
        void nullInput_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.deleteFilesIfExists(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`paths` must not be null");
        }

        @Test
        void emptyCollection_NoException() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                assertThatNoException()
                        .isThrownBy(() -> Utils.deleteFilesIfExists(List.of()));

                files.verify(() -> Files.deleteIfExists(Mockito.any()), Mockito.never());
            }
        }

        @Test
        void multipleFiles_DeletesAllSuccessfully() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path file1 = Path.of("project/file1.txt");
                Path file2 = Path.of("project/file2.txt");
                Path file3 = Path.of("project/file3.txt");
                List<Path> paths = List.of(file1, file2, file3);

                files.when(() -> Files.deleteIfExists(Mockito.any()))
                        .thenReturn(true);

                assertThatNoException()
                        .isThrownBy(() -> Utils.deleteFilesIfExists(paths));

                files.verify(() -> Files.deleteIfExists(file1), Mockito.times(1));
                files.verify(() -> Files.deleteIfExists(file2), Mockito.times(1));
                files.verify(() -> Files.deleteIfExists(file3), Mockito.times(1));
            }
        }

        @Test
        void deletionFailsOnSecondFile_ThrowsMojoExecutionException() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path file1 = Path.of("project/file1.txt");
                Path file2 = Path.of("project/file2.txt");
                Path file3 = Path.of("project/file3.txt");
                List<Path> paths = List.of(file1, file2, file3);

                IOException ioException = new IOException("deletion failed");
                files.when(() -> Files.deleteIfExists(file1))
                        .thenReturn(true);
                files.when(() -> Files.deleteIfExists(file2))
                        .thenThrow(ioException);

                assertThatThrownBy(() -> Utils.deleteFilesIfExists(paths))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasCause(ioException);

                files.verify(() -> Files.deleteIfExists(file1), Mockito.times(1));
                files.verify(() -> Files.deleteIfExists(file2), Mockito.times(1));
                files.verify(() -> Files.deleteIfExists(file3), Mockito.never());
            }
        }
    }

    @Nested
    class AlwaysTrueTest {

        @ParameterizedTest
        @NullSource
        @EnumSource(DayOfWeek.class)
        @ValueSource(booleans = {true, false})
        @ValueSource(ints = {-4, -3, -2, -1, 0, 1, 2, 3, 4})
        @ValueSource(strings = {"", "a", "abc"})
        void anyInput_AlwaysTrue(Object input) {
            Predicate<Object> predicate = Utils.alwaysTrue();
            assertThat(predicate.test(input))
                    .isTrue();
        }
    }

    @Nested
    class MavenProjectHasNoModulesTest {

        @Test
        void noModules_True() {
            Mockito.when(mavenProject.getModules())
                    .thenReturn(List.of());

            Predicate<MavenProject> predicate = Utils.mavenProjectHasNoModules();
            assertThat(predicate.test(mavenProject))
                    .isTrue();
        }

        @Test
        void withModules_False() {
            Mockito.when(mavenProject.getModules())
                    .thenReturn(List.of("module1", "module2"));

            Predicate<MavenProject> predicate = Utils.mavenProjectHasNoModules();
            assertThat(predicate.test(mavenProject))
                    .isFalse();
        }
    }

    @Nested
    class ConsumerToOperatorTest {

        @Test
        void nullInput_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.consumerToOperator(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`accumulator` must not be null");
        }

        @Test
        void setAddAll_CorrectlyAddedAndReturned() {
            BinaryOperator<Set<Integer>> operator = Utils.consumerToOperator(Set::addAll);

            Set<Integer> set = new HashSet<>(Set.of(1, 2, 3));
            assertThat(operator.apply(set, Set.of(4, 5, 6)))
                    .isEqualTo(Set.of(1, 2, 3, 4, 5, 6))
                    .isSameAs(set);
        }
    }

    @Nested
    class GroupingByImmutableTest {

        @Test
        void oddEvenNumbers_CorrectlySplitMapIsImmutable() {
            Map<Boolean, List<Number>> actual = IntStream.range(0, 10)
                    .boxed()
                    .collect(Utils.groupingByImmutable(i -> (i & 1) == 1, Collectors.toList()));

            assertThat(actual)
                    .isNotNull()
                    .hasSize(2)
                    .hasEntrySatisfying(true, list -> assertThat(list)
                            .hasSize(5)
                            .containsExactly(1, 3, 5, 7, 9)
                    )
                    .hasEntrySatisfying(false, list -> assertThat(list)
                            .hasSize(5)
                            .containsExactly(0, 2, 4, 6, 8)
                    );

            assertThatThrownBy(() -> actual.put(true, List.of(10)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(actual::clear)
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThat(Map.copyOf(actual))
                    .isSameAs(actual);
        }
    }

    @Nested
    class AsImmutableListTest {

        @Test
        void emptyStream_EmptyAndImmutable() {
            List<Integer> list = Stream.<Integer>of()
                    .collect(Utils.asImmutableList());

            assertThat(list)
                    .isEmpty();

            assertThatThrownBy(() -> list.add(1))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(list::clear)
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThat(List.copyOf(list))
                    .isSameAs(list);
        }

        @Test
        void nonEmptyStream_NonEmptyAndImmutable() {
            List<Integer> list = Stream.of(1, 2, 3)
                    .collect(Utils.asImmutableList());

            assertThat(list)
                    .containsExactly(1, 2, 3);

            assertThatThrownBy(() -> list.add(4))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(list::clear)
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThat(List.copyOf(list))
                    .isSameAs(list);
        }
    }

    @Nested
    class AsImmutableSetTest {

        @Test
        void emptyStream_EmptyAndImmutable() {
            Set<Integer> set = Stream.<Integer>of()
                    .collect(Utils.asImmutableSet());

            assertThat(set)
                    .isEmpty();

            assertThatThrownBy(() -> set.add(1))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(set::clear)
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThat(Set.copyOf(set))
                    .isSameAs(set);
        }

        @Test
        void nonEmptyStream_NonEmptyAndImmutable() {
            Set<Integer> list = Stream.of(1, 2, 3)
                    .collect(Utils.asImmutableSet());

            assertThat(list)
                    .containsExactlyInAnyOrder(1, 2, 3);

            assertThatThrownBy(() -> list.add(4))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(list::clear)
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThat(Set.copyOf(list))
                    .isSameAs(list);
        }
    }
}
