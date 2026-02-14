package io.github.bsels.semantic.version.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.PlaceHolderWithType;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
    class CreateDirectoryIfNotExistsTest {

        @Test
        void nullPath_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.createDirectoryIfNotExists(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`path` must not be null");
        }

        @Test
        void directoryExists_NoException() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path directory = Path.of("project/directory");
                files.when(() -> Files.exists(directory))
                        .thenReturn(true);

                assertThatNoException()
                        .isThrownBy(() -> Utils.createDirectoryIfNotExists(directory));

                files.verify(() -> Files.createDirectories(directory), Mockito.never());
            }
        }

        @Test
        void directoryDoesNotExist_CreatesSuccessfully() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path directory = Path.of("project/directory");
                files.when(() -> Files.exists(directory))
                        .thenReturn(false);

                assertThatNoException()
                        .isThrownBy(() -> Utils.createDirectoryIfNotExists(directory));

                files.verify(() -> Files.createDirectories(directory), Mockito.times(1));
            }
        }

        @Test
        void creationFails_ThrowsMojoExecutionException() {
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
                Path directory = Path.of("project/directory");
                IOException ioException = new IOException("creation failed");
                files.when(() -> Files.exists(directory))
                        .thenReturn(false);
                files.when(() -> Files.createDirectories(directory))
                        .thenThrow(ioException);

                assertThatThrownBy(() -> Utils.createDirectoryIfNotExists(directory))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Failed to create directory")
                        .hasCause(ioException);

                files.verify(() -> Files.createDirectories(directory), Mockito.times(1));
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

    @Nested
    class ResolveVersioningFileTest {

        @Test
        void nullProject_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.resolveVersioningFile(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`folder` must not be null");
        }

        @Test
        void resolveNewVersioningFile_ValidPath() {
            Path folder = Path.of("project");
            LocalDateTime localDateTime = LocalDateTime.of(2023, 1, 1, 12, 0, 8);
            try (MockedStatic<LocalDateTime> localDateTimeMock = Mockito.mockStatic(LocalDateTime.class)) {
                localDateTimeMock.when(LocalDateTime::now)
                        .thenReturn(localDateTime);
                Path expectedPath = Path.of("project/versioning-20230101120008.md");
                assertThat(Utils.resolveVersioningFile(folder))
                        .isEqualTo(expectedPath);
            }
        }
    }

    @Nested
    class PrepareFormatStringTest {

        @Test
        void nullFormatString_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.prepareFormatString(null, List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`formatString` must not be null");
        }

        @Test
        void nullKeys_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.prepareFormatString("test", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`keys` must not be null");
        }

        @Test
        void nullKeyInList_ThrowsNullPointerException() {
            List<PlaceHolderWithType> keys = Arrays.asList(
                    new PlaceHolderWithType("version", "s"),
                    null,
                    new PlaceHolderWithType("date", "s")
            );
            assertThatThrownBy(() -> Utils.prepareFormatString("test", keys))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("All keys must not be null");
        }

        @Test
        void emptyKeysAndEmptyString_ReturnsEmptyString() {
            String result = Utils.prepareFormatString("", List.of());
            assertThat(result).isEmpty();
        }

        @Test
        void emptyKeys_ReturnsOriginalString() {
            String formatString = "No placeholders here";
            String result = Utils.prepareFormatString(formatString, List.of());
            assertThat(result).isEqualTo(formatString);
        }

        @Test
        void singlePlaceholder_CorrectlyReplaced() {
            String formatString = "Version: {version}";
            List<PlaceHolderWithType> keys = List.of(
                    new PlaceHolderWithType("version", "s")
            );
            String result = Utils.prepareFormatString(formatString, keys);
            assertThat(result).isEqualTo("Version: %1$s");
        }

        @Test
        void multiplePlaceholders_CorrectlyReplacedInOrder() {
            String formatString = "Version {version} released on {date} by {author}";
            List<PlaceHolderWithType> keys = List.of(
                    new PlaceHolderWithType("version", "s"),
                    new PlaceHolderWithType("date", "s"),
                    new PlaceHolderWithType("author", "s")
            );
            String result = Utils.prepareFormatString(formatString, keys);
            assertThat(result).isEqualTo("Version %1$s released on %2$s by %3$s");
        }

        @Test
        void differentFormatTypes_CorrectlyApplied() {
            String formatString = "Count: {count}, Name: {name}, Value: {value}";
            List<PlaceHolderWithType> keys = List.of(
                    new PlaceHolderWithType("count", "d"),
                    new PlaceHolderWithType("name", "s"),
                    new PlaceHolderWithType("value", "f")
            );
            String result = Utils.prepareFormatString(formatString, keys);
            assertThat(result).isEqualTo("Count: %1$d, Name: %2$s, Value: %3$f");
        }

        @Test
        void samePlaceholderMultipleTimes_AllInstancesReplaced() {
            String formatString = "{version} is the latest version. Update to {version} now!";
            List<PlaceHolderWithType> keys = List.of(
                    new PlaceHolderWithType("version", "s")
            );
            String result = Utils.prepareFormatString(formatString, keys);
            assertThat(result).isEqualTo("%1$s is the latest version. Update to %1$s now!");
        }

        @Test
        void placeholderNotInString_StringUnchanged() {
            String formatString = "No version here";
            List<PlaceHolderWithType> keys = List.of(
                    new PlaceHolderWithType("version", "s"),
                    new PlaceHolderWithType("date", "s")
            );
            String result = Utils.prepareFormatString(formatString, keys);
            assertThat(result).isEqualTo("No version here");
        }

        @Test
        void partialPlaceholderMatch_OnlyFullMatchReplaced() {
            String formatString = "{version} and {versioning} are different";
            List<PlaceHolderWithType> keys = List.of(
                    new PlaceHolderWithType("version", "s")
            );
            String result = Utils.prepareFormatString(formatString, keys);
            assertThat(result).isEqualTo("%1$s and {versioning} are different");
        }

        @Test
        void complexFormatString_CorrectlyProcessed() {
            String formatString = "Released version {version} on {date} with {count} features";
            List<PlaceHolderWithType> keys = List.of(
                    new PlaceHolderWithType("version", "s"),
                    new PlaceHolderWithType("date", "tF"),
                    new PlaceHolderWithType("count", "d")
            );
            String result = Utils.prepareFormatString(formatString, keys);
            assertThat(result).isEqualTo("Released version %1$s on %2$tF with %3$d features");
        }

        @Test
        void placeholdersWithSpecialCharacters_CorrectlyReplaced() {
            String formatString = "Value: {some-value}, Another: {another_value}";
            List<PlaceHolderWithType> keys = List.of(
                    new PlaceHolderWithType("some-value", "s"),
                    new PlaceHolderWithType("another_value", "d")
            );
            String result = Utils.prepareFormatString(formatString, keys);
            assertThat(result).isEqualTo("Value: %1$s, Another: %2$d");
        }
    }

    @Nested
    class FormatHeaderLineTest {

        @Test
        void nullHeaderLine_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.formatHeaderLine(null, "1.0.0", LocalDate.now()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`headerLine` must not be null");
        }

        @Test
        void nullVersion_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.formatHeaderLine("## {version}", null, LocalDate.now()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`version` must not be null");
        }

        @Test
        void nullDate_ThrowsNullPointerException() {
            assertThatThrownBy(() -> Utils.formatHeaderLine("## {date}", "1.0.0", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`date` must not be null");
        }

        @Test
        void defaultDateAndVersionPlaceholders_Replaced() {
            LocalDate date = LocalDate.of(2024, 2, 3);
            String result = Utils.formatHeaderLine("## {version} - {date}", "1.2.3", date);

            assertThat(result).isEqualTo("## 1.2.3 - 2024-02-03");
        }

        @Test
        void customDatePattern_Replaced() {
            LocalDate date = LocalDate.of(2024, 2, 3);
            String result = Utils.formatHeaderLine("Released {date#yyyy/MM/dd}", "1.2.3", date);

            assertThat(result).isEqualTo("Released 2024/02/03");
        }

        @Test
        void multiplePlaceholders_AllReplaced() {
            LocalDate date = LocalDate.of(2024, 2, 3);
            String result = Utils.formatHeaderLine(
                    "v{version} ({date}) -> {version}",
                    "2.0.0",
                    date
            );

            assertThat(result).isEqualTo("v2.0.0 (2024-02-03) -> 2.0.0");
        }

        @Test
        void multipleDateFormats_AllReplaced() {
            LocalDate date = LocalDate.of(2024, 2, 3);
            String result = Utils.formatHeaderLine(
                    "Released {date#yyyy/MM/dd} (ISO {date}) [stamp {date#yyyyMMdd}]",
                    "2.0.0",
                    date
            );

            assertThat(result).isEqualTo("Released 2024/02/03 (ISO 2024-02-03) [stamp 20240203]");
        }

        @Test
        void unknownPlaceholder_Preserved() {
            LocalDate date = LocalDate.of(2024, 2, 3);
            String result = Utils.formatHeaderLine(
                    "Release {unknown} on {date}",
                    "1.0.0",
                    date
            );

            assertThat(result).isEqualTo("Release {unknown} on 2024-02-03");
        }
    }

    @Nested
    class MavenProjectToArtifactTest {

        @Test
        void validProject_ReturnsCorrectArtifact() {
            Mockito.when(mavenProject.getGroupId())
                    .thenReturn("io.github.bsels");
            Mockito.when(mavenProject.getArtifactId())
                    .thenReturn("semantic-version-maven-plugin");

            MavenArtifact artifact = Utils.mavenProjectToArtifact(mavenProject);
            assertThat(artifact).isNotNull();
            assertThat(artifact.groupId()).isEqualTo("io.github.bsels");
            assertThat(artifact.artifactId()).isEqualTo("semantic-version-maven-plugin");
        }
    }

    @Nested
    class WriteObjectAsJsonTest {

        @Test
        void validObject_ReturnsJson() throws MojoExecutionException {
            Map<String, String> map = Map.of("key", "value");
            String json = Utils.writeObjectAsJson(map);

            assertThat(json).contains("\"key\" : \"value\"");
        }

        @Test
        void mavenArtifact_ReturnsJson() throws MojoExecutionException {
            MavenArtifact artifact = new MavenArtifact("io.github.bsels", "semantic-version-maven-plugin");
            String json = Utils.writeObjectAsJson(artifact);

            assertThat(json).isEqualToIgnoringNewLines("""
                    {
                      "groupId" : "io.github.bsels",
                      "artifactId" : "semantic-version-maven-plugin"
                    }
                    """);
        }

        @Test
        void mavenArtifactAsKey_ReturnsJsonWithMavenArtifactAsKey() throws MojoExecutionException {
            MavenArtifact artifact = new MavenArtifact("io.github.bsels", "semantic-version-maven-plugin");
            Map<MavenArtifact, String> map = Map.of(artifact, "value");
            String json = Utils.writeObjectAsJson(map);

            assertThat(json).contains("\"io.github.bsels:semantic-version-maven-plugin\" : \"value\"");
        }

        @Test
        void failingObject_ThrowsMojoExecutionException() {
            Object failingObject = new Object() {
                public String getFailingProperty() {
                    throw new RuntimeException("Failing getter");
                }
            };

            assertThatThrownBy(() -> Utils.writeObjectAsJson(failingObject))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Failed to serialize object to JSON")
                    .hasCauseInstanceOf(JsonProcessingException.class);
        }
    }
}
