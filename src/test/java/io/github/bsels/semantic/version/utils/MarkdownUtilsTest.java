package io.github.bsels.semantic.version.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.parameters.ArtifactIdentifier;
import io.github.bsels.semantic.version.test.utils.TestLog;
import io.github.bsels.semantic.version.utils.yaml.front.block.YamlFrontMatterBlock;
import org.apache.maven.plugin.MojoExecutionException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.bsels.semantic.version.test.utils.MarkdownDocumentAsserter.assertThatDocument;
import static io.github.bsels.semantic.version.test.utils.MarkdownDocumentAsserter.hasHeading;
import static io.github.bsels.semantic.version.test.utils.MarkdownDocumentAsserter.hasParagraph;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MarkdownUtilsTest {
    private static final String ARTIFACT_ID = "artifactId";
    private static final String GROUP_ID = "groupId";
    private static final ArtifactIdentifier IDENTIFIER = ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID;
    private static final ArtifactIdentifier ARTIFACT_ONLY_IDENTIFIER = ArtifactIdentifier.ONLY_ARTIFACT_ID;
    private static final MavenArtifact MAVEN_ARTIFACT = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
    private static final Path CHANGELOG_PATH = Path.of("project/CHANGELOG.md");
    private static final Path CHANGELOG_BACKUP_PATH = Path.of("project/CHANGELOG.md.backup");
    private static final String VERSION = "1.0.0";
    private static final String HEADER_LINE = "{version} - {date}";
    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
    private static final String CHANGE_LINE = "Version bumped with a %s semantic version at index %d";
    private static final String SIMPLE_BUMP_TEXT = "Project version bumped as result of dependency bumps";

    private Node createDummyChangelogDocument() {
        Document document = new Document();
        Heading heading = new Heading();
        heading.setLevel(1);
        heading.appendChild(new Text("Changelog"));
        document.appendChild(heading);

        Paragraph paragraph = new Paragraph();
        paragraph.appendChild(new Text("Test paragraph"));
        document.appendChild(paragraph);
        return document;

    }

    private Node createDummyVersionBumpDocument(SemanticVersionBump bump, int index) {
        Document document = new Document();
        Paragraph paragraph = new Paragraph();
        paragraph.appendChild(new Text(CHANGE_LINE.formatted(bump, index)));
        document.appendChild(paragraph);
        return document;
    }

    @Nested
    class CreateSimpleVersionBumpDocumentTest {

        @Test
        void nullArtifact_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.createSimpleVersionBumpDocument(null, SIMPLE_BUMP_TEXT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`mavenArtifact` must not be null");
        }

        @Test
        void nullText_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.createSimpleVersionBumpDocument(MAVEN_ARTIFACT, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`text` must not be null");
        }

        @Test
        void createDocument_ValidMarkdown() {
            VersionMarkdown actual = MarkdownUtils.createSimpleVersionBumpDocument(MAVEN_ARTIFACT, SIMPLE_BUMP_TEXT);

            assertThat(actual.path())
                    .isNull();

            assertThatDocument(
                    actual.content(),
                    hasParagraph(SIMPLE_BUMP_TEXT)
            );

            assertThat(actual.bumps())
                    .hasSize(1)
                    .containsEntry(MAVEN_ARTIFACT, SemanticVersionBump.NONE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "Dependency bump applied", "Dependency bump:\n- api"})
        void textIsPreserved(String text) {
            VersionMarkdown actual = MarkdownUtils.createSimpleVersionBumpDocument(MAVEN_ARTIFACT, text);

            assertThatDocument(
                    actual.content(),
                    hasParagraph(text)
            );
        }
    }

    @Nested
    class PrintMarkdownTest {

        @ParameterizedTest
        @EnumSource(value = TestLog.LogLevel.class, mode = EnumSource.Mode.EXCLUDE, names = {"DEBUG"})
        void noDebugLogging_NoLogging(TestLog.LogLevel logLevel) {
            TestLog log = new TestLog(logLevel);

            assertThatNoException()
                    .isThrownBy(() -> MarkdownUtils.printMarkdown(log, createDummyChangelogDocument(), 0));

            assertThat(log.getLogRecords())
                    .isEmpty();
        }

        @Test
        void debugLogging_Logging() {
            TestLog log = new TestLog(TestLog.LogLevel.DEBUG);

            MarkdownUtils.printMarkdown(log, createDummyChangelogDocument(), 0);
            assertThat(log.getLogRecords())
                    .hasSize(5)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("Document{}"))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("  Heading{}"))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("    Text{literal=Changelog}"))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("  Paragraph{}"))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("    Text{literal=Test paragraph}"))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                    );
        }
    }

    @Nested
    class WriteMarkdownTest {

        @Test
        void nullWriter_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.writeMarkdown(null, createDummyChangelogDocument()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`output` must not be null");
        }

        @Test
        void nullDocument_ThrowsNullPointerException() {
            StringWriter writer = new StringWriter();
            assertThatThrownBy(() -> MarkdownUtils.writeMarkdown(writer, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`document` must not be null");
        }

        @Test
        void validDocument_WritesMarkdown() {
            StringWriter writer = new StringWriter();
            MarkdownUtils.writeMarkdown(writer, createDummyChangelogDocument());
            assertThat(writer.toString())
                    .isEqualTo("""
                            # Changelog
                            
                            Test paragraph
                            """);
        }
    }

    @Nested
    class WriteMarkdownFileTest {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void markdownFileIsNull_ThrowsNullPointerException(boolean backupOld) {
            assertThatThrownBy(() -> MarkdownUtils.writeMarkdownFile(null, createDummyChangelogDocument(), backupOld))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`markdownFile` must not be null");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void documentIsNull_ThrowsNullPointerException(boolean backupOld) {
            assertThatThrownBy(() -> MarkdownUtils.writeMarkdownFile(CHANGELOG_PATH, null, backupOld))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`document` must not be null");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void failedToCreateFileWriter_ThrowsMojoExceptionException(boolean backupOld) {
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(true);
                filesMockedStatic.when(() -> Files.newBufferedWriter(
                                CHANGELOG_PATH,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING
                        ))
                        .thenThrow(new IOException("Failed to create writer"));

                assertThatThrownBy(() -> MarkdownUtils.writeMarkdownFile(CHANGELOG_PATH, createDummyChangelogDocument(), backupOld))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to write %s".formatted(CHANGELOG_PATH))
                        .hasRootCauseInstanceOf(IOException.class)
                        .hasRootCauseMessage("Failed to create writer");

                filesMockedStatic.verify(() -> Files.copy(
                        CHANGELOG_PATH,
                        CHANGELOG_BACKUP_PATH,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING
                ), Mockito.times(backupOld ? 1 : 0));
                filesMockedStatic.verify(() -> Files.newBufferedWriter(
                        CHANGELOG_PATH,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING
                ), Mockito.times(1));
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void happyFlow_CorrectlyWritten(boolean backupOld) {
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                StringWriter writer = new StringWriter();
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(true);
                filesMockedStatic.when(() -> Files.newBufferedWriter(
                                CHANGELOG_PATH,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING
                        ))
                        .thenReturn(new BufferedWriter(writer));

                assertThatNoException()
                        .isThrownBy(() -> MarkdownUtils.writeMarkdownFile(CHANGELOG_PATH, createDummyChangelogDocument(), backupOld));
                assertThat(writer.toString())
                        .isEqualTo("""
                                # Changelog
                                
                                Test paragraph
                                """);

                filesMockedStatic.verify(() -> Files.copy(CHANGELOG_PATH,
                        CHANGELOG_BACKUP_PATH,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING
                ), Mockito.times(backupOld ? 1 : 0));
                filesMockedStatic.verify(() -> Files.newBufferedWriter(
                        CHANGELOG_PATH,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING
                ), Mockito.times(1));
            }
        }
    }

    @Nested
    class MergeVersionMarkdownsInChangelogTest {

        @Test
        void nullChangelog_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    null,
                    VERSION,
                    HEADER_LINE,
                    Map.ofEntries(createDummyVersionMarkdown(SemanticVersionBump.PATCH, 1))
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`changelog` must not be null");
        }

        @Test
        void nullVersion_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    createDummyChangelogDocument(),
                    null,
                    HEADER_LINE,
                    Map.ofEntries(createDummyVersionMarkdown(SemanticVersionBump.PATCH, 1))
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`version` must not be null");
        }

        @Test
        void nullHeaderLine_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    createDummyChangelogDocument(),
                    VERSION,
                    null,
                    Map.ofEntries(createDummyVersionMarkdown(SemanticVersionBump.PATCH, 1))
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`headerFormatLine` must not be null");
        }

        @Test
        void nullHeaderToNodes_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    createDummyChangelogDocument(),
                    VERSION,
                    HEADER_LINE,
                    null
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`headerToNodes` must not be null");
        }

        @Test
        void changelogIsNotDocument_ThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    new Paragraph(),
                    VERSION,
                    HEADER_LINE,
                    Map.ofEntries(createDummyVersionMarkdown(SemanticVersionBump.PATCH, 1))
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("`changelog` must be a Document");
        }

        @Test
        void changelogStartWithParagraph_ThrowsIllegalArgumentException() {
            Document document = new Document();
            Paragraph paragraph = new Paragraph();
            paragraph.appendChild(new Text("Changelog"));
            document.appendChild(paragraph);
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    document,
                    VERSION,
                    HEADER_LINE,
                    Map.ofEntries(createDummyVersionMarkdown(SemanticVersionBump.PATCH, 1))
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Changelog must start with a single H1 heading with the text 'Changelog'");
        }

        @Test
        void changelogStartWithLevel2Heading_ThrowsIllegalArgumentException() {
            Document document = new Document();
            Heading heading = new Heading();
            heading.setLevel(2);
            heading.appendChild(new Text("Changelog"));
            document.appendChild(heading);
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    document,
                    VERSION,
                    HEADER_LINE,
                    Map.ofEntries(createDummyVersionMarkdown(SemanticVersionBump.PATCH, 1))
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Changelog must start with a single H1 heading with the text 'Changelog'");
        }

        @Test
        void changelogStartWithLevel1HeadingNoText_ThrowsIllegalArgumentException() {
            Document document = new Document();
            Heading heading = new Heading();
            heading.setLevel(1);
            heading.appendChild(new Text("No Changelog"));
            document.appendChild(heading);
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    document,
                    VERSION,
                    HEADER_LINE,
                    Map.ofEntries(createDummyVersionMarkdown(SemanticVersionBump.PATCH, 1))
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Changelog must start with a single H1 heading with the text 'Changelog'");
        }

        @Test
        void changelogStartWithLevel1HeadingNotChangelogText_ThrowsIllegalArgumentException() {
            Document document = new Document();
            Heading heading = new Heading();
            heading.setLevel(1);
            document.appendChild(heading);
            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    document,
                    VERSION,
                    HEADER_LINE,
                    Map.ofEntries(createDummyVersionMarkdown(SemanticVersionBump.PATCH, 1))
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Changelog must start with a single H1 heading with the text 'Changelog'");
        }

        @Test
        void programmaticError_ThrowAssertionError() {
            Document document = new Document();
            Heading headingMock = Mockito.mock(Heading.class);
            Mockito.when(headingMock.getLevel()).thenReturn(1);
            Mockito.when(headingMock.getFirstChild()).thenReturn(new Text("Changelog"));
            document.appendChild(headingMock);

            Mockito.when(headingMock.getNext()).thenReturn(new Paragraph(), new Paragraph());

            assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                    document,
                    VERSION,
                    HEADER_LINE,
                    Map.of()
            ))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("Incorrectly inserted nodes into changelog");
        }

        @Test
        void nodeToAddIsNotADocument_ThrowsIllegalArgumentException() {
            Node changelogDocument = createDummyChangelogDocument();

            try (MockedStatic<LocalDate> localDateMockedStatic = Mockito.mockStatic(LocalDate.class)) {
                localDateMockedStatic.when(LocalDate::now)
                        .thenReturn(DATE);

                assertThatThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                        changelogDocument,
                        VERSION,
                        HEADER_LINE,
                        Map.of(SemanticVersionBump.PATCH, List.of(new Paragraph())))
                )
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Node must be a Document");
            }
        }

        @Test
        void noNodes_OnlyIncludeVersionHeader() {
            Node changelogDocument = createDummyChangelogDocument();

            try (MockedStatic<LocalDate> localDateMockedStatic = Mockito.mockStatic(LocalDate.class)) {
                localDateMockedStatic.when(LocalDate::now)
                        .thenReturn(DATE);

                assertThatNoException()
                        .isThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                                changelogDocument,
                                VERSION,
                                HEADER_LINE,
                                Map.of())
                        );
            }

            assertThatDocument(
                    changelogDocument,
                    hasHeading(1, "Changelog"),
                    hasHeading(2, "%s - %s".formatted(VERSION, DATE)),
                    hasParagraph("Test paragraph")
            );
        }

        @Test
        void multipleNodes_IncludeVersionHeaderForEachNode() {
            Document changelogDocument = new Document();
            Heading firstHeader = new Heading();
            firstHeader.setLevel(1);
            firstHeader.appendChild(new Text("Changelog"));
            changelogDocument.appendChild(firstHeader);

            try (MockedStatic<LocalDate> localDateMockedStatic = Mockito.mockStatic(LocalDate.class)) {
                localDateMockedStatic.when(LocalDate::now)
                        .thenReturn(DATE);

                assertThatNoException()
                        .isThrownBy(() -> MarkdownUtils.mergeVersionMarkdownsInChangelog(
                                changelogDocument,
                                VERSION,
                                HEADER_LINE,
                                Map.ofEntries(
                                        createDummyVersionMarkdown(SemanticVersionBump.NONE, 1),
                                        createDummyVersionMarkdown(SemanticVersionBump.PATCH, 2),
                                        createDummyVersionMarkdown(SemanticVersionBump.MINOR, 3),
                                        createDummyVersionMarkdown(SemanticVersionBump.MAJOR, 4)
                                ))
                        );
            }

            assertThatDocument(
                    changelogDocument,
                    hasHeading(1, "Changelog"),
                    hasHeading(2, "%s - %s".formatted(VERSION, DATE)),
                    hasHeading(3, "Major"),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.MAJOR, 0)),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.MAJOR, 1)),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.MAJOR, 2)),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.MAJOR, 3)),
                    hasHeading(3, "Minor"),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.MINOR, 0)),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.MINOR, 1)),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.MINOR, 2)),
                    hasHeading(3, "Patch"),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.PATCH, 0)),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.PATCH, 1)),
                    hasHeading(3, "Other"),
                    hasParagraph(CHANGE_LINE.formatted(SemanticVersionBump.NONE, 0))
            );

        }

        private Map.Entry<SemanticVersionBump, List<Node>> createDummyVersionMarkdown(
                SemanticVersionBump bump,
                int items
        ) {
            return Map.entry(
                    bump,
                    IntStream.range(0, items)
                            .mapToObj(index -> createDummyVersionBumpDocument(bump, index))
                            .collect(Utils.asImmutableList())
            );
        }
    }

    @Nested
    class ReadMarkdownTest {

        @Test
        void nullLog_ThrowNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.readMarkdown(null, CHANGELOG_PATH))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`log` must not be null");
        }

        @Test
        void nullMarkdownFile_ThrowNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.readMarkdown(new TestLog(TestLog.LogLevel.DEBUG), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`markdownFile` must not be null");
        }

        @Test
        void fileDoesNotExists_CreateInternalEmptyChangelog() throws MojoExecutionException {
            TestLog log = new TestLog(TestLog.LogLevel.DEBUG);
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(false);

                Node document = MarkdownUtils.readMarkdown(log, CHANGELOG_PATH);
                assertThatDocument(
                        document,
                        hasHeading(1, "Changelog")
                );
            }

            assertThat(log.getLogRecords())
                    .hasSize(1)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("No changelog file found at '%s', creating an empty CHANGELOG internally".formatted(CHANGELOG_PATH)))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                    );
        }

        @Test
        void readingLinesThrowsIOException_ThrowsMojoExecutionException() {
            TestLog log = new TestLog(TestLog.LogLevel.DEBUG);
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(true);
                filesMockedStatic.when(() -> Files.lines(CHANGELOG_PATH, StandardCharsets.UTF_8))
                        .thenThrow(new IOException("Failed to read file"));

                assertThatThrownBy(() -> MarkdownUtils.readMarkdown(log, CHANGELOG_PATH))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to read '%s' file".formatted(CHANGELOG_PATH))
                        .hasRootCauseInstanceOf(IOException.class)
                        .hasRootCauseMessage("Failed to read file");
            }

            assertThat(log.getLogRecords())
                    .isEmpty();
        }

        @Test
        void happyFlow_ReturnsCorrectDocument() throws MojoExecutionException {
            TestLog log = new TestLog(TestLog.LogLevel.DEBUG);
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(true);
                filesMockedStatic.when(() -> Files.lines(CHANGELOG_PATH, StandardCharsets.UTF_8))
                        .thenReturn(Stream.of("# My Changelog", "", "Test paragraph"));

                Node document = MarkdownUtils.readMarkdown(log, CHANGELOG_PATH);

                assertThatDocument(
                        document,
                        hasHeading(1, "My Changelog"),
                        hasParagraph("Test paragraph")
                );
            }

            assertThat(log.getLogRecords())
                    .hasSize(1)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("Read 3 lines from %s".formatted(CHANGELOG_PATH)))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                    );
        }
    }

    @Nested
    class ReadVersionMarkdownTest {

        @Test
        void nullLog_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.readVersionMarkdown(null, CHANGELOG_PATH, IDENTIFIER, GROUP_ID))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`log` must not be null");
        }

        @Test
        void nullMarkdownFile_ThrowsNullPointerException() {
            TestLog log = new TestLog(TestLog.LogLevel.DEBUG);
            assertThatThrownBy(() -> MarkdownUtils.readVersionMarkdown(log, null, IDENTIFIER, GROUP_ID))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`markdownFile` must not be null");
        }

        @Test
        void hasNoFrontBlock_ThrowsMojoExecutionException() {
            TestLog log = new TestLog(TestLog.LogLevel.DEBUG);
            String markdown = """
                    # Header 1
                    
                    Header 1 paragraph.
                    
                    ## Header 2
                    
                    Header 2 paragraph.
                    """;

            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(true);
                filesMockedStatic.when(() -> Files.lines(CHANGELOG_PATH, StandardCharsets.UTF_8))
                        .thenReturn(markdown.lines());

                assertThatThrownBy(() -> MarkdownUtils.readVersionMarkdown(log, CHANGELOG_PATH, IDENTIFIER, GROUP_ID))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("YAML front matter block not found in '%s' file".formatted(CHANGELOG_PATH));
            }

            assertThat(log.getLogRecords())
                    .hasSize(1)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("Read 7 lines from %s".formatted(CHANGELOG_PATH)))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                    );
        }

        @Test
        void hasNoVersionBumpFrontBlock_ThrowsMojoExecutionException() {
            TestLog log = new TestLog(TestLog.LogLevel.DEBUG);
            String markdown = """
                    ---
                    this:
                        yaml:
                            is:
                                not: a version bump block
                    ---
                    # Header 1
                    
                    Header 1 paragraph.
                    """;

            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(true);
                filesMockedStatic.when(() -> Files.lines(CHANGELOG_PATH, StandardCharsets.UTF_8))
                        .thenReturn(markdown.lines());

                assertThatThrownBy(() -> MarkdownUtils.readVersionMarkdown(log, CHANGELOG_PATH, IDENTIFIER, GROUP_ID))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("YAML front matter does not contain valid maven artifacts and semantic version bump")
                        .hasRootCauseInstanceOf(JsonProcessingException.class)
                        .hasRootCauseMessage(
                                """
                                        Cannot deserialize Map key of type \
                                        `io.github.bsels.semantic.version.models.MavenArtifact` from String "this": \
                                        not a valid representation, problem: \
                                        (java.lang.reflect.InvocationTargetException) Invalid Maven artifact format: \
                                        this, expected <group-id>:<artifact-id>
                                         at [Source: (StringReader); line: 1, column: 1]\
                                        """
                        );
            }

            assertThat(log.getLogRecords())
                    .hasSize(2)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("Read 9 lines from %s".formatted(CHANGELOG_PATH)))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("""
                                            YAML front matter:
                                                this:
                                                    yaml:
                                                        is:
                                                            not: a version bump block\
                                            """))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                    );
        }

        @Test
        void happyFlow_ValidObject() throws MojoExecutionException {
            TestLog log = new TestLog(TestLog.LogLevel.NONE);
            String markdown = """
                    ---
                    'group:none': None
                    'group:patch': patch
                    'group-2:minor': MINOR
                    'group-2:major': MAJOR
                    ---
                    
                    # Header 1
                    
                    Header 1 paragraph.
                    """;
            VersionMarkdown versionMarkdown;
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(true);
                filesMockedStatic.when(() -> Files.lines(CHANGELOG_PATH, StandardCharsets.UTF_8))
                        .thenReturn(markdown.lines());

                versionMarkdown = MarkdownUtils.readVersionMarkdown(log, CHANGELOG_PATH, IDENTIFIER, GROUP_ID);
            }

            assertThat(versionMarkdown)
                    .satisfies(
                            data -> assertThat(data.bumps())
                                    .hasSize(4)
                                    .containsEntry(
                                            new MavenArtifact("group", "none"),
                                            SemanticVersionBump.NONE
                                    )
                                    .containsEntry(
                                            new MavenArtifact("group", "patch"),
                                            SemanticVersionBump.PATCH
                                    )
                                    .containsEntry(
                                            new MavenArtifact("group-2", "minor"),
                                            SemanticVersionBump.MINOR
                                    )
                                    .containsEntry(
                                            new MavenArtifact("group-2", "major"),
                                            SemanticVersionBump.MAJOR
                                    )
                    );

            assertThat(log.getLogRecords())
                    .hasSize(3)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("Read 10 lines from %s".formatted(CHANGELOG_PATH)))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("""
                                            YAML front matter:
                                                'group:none': None
                                                'group:patch': patch
                                                'group-2:minor': MINOR
                                                'group-2:major': MAJOR\
                                            """))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("""
                                            Maven artifacts and semantic version bumps:
                                            {group:none=NONE, group:patch=PATCH, group-2:minor=MINOR, group-2:major=MAJOR}\
                                            """))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                    );
        }

        @Test
        void happyFlow_ValidObject_OnlyArtifactId() throws MojoExecutionException {
            TestLog log = new TestLog(TestLog.LogLevel.NONE);
            String markdown = """
                    ---
                    none: None
                    patch: patch
                    minor: MINOR
                    major: MAJOR
                    ---
                    
                    # Header 1
                    
                    Header 1 paragraph.
                    """;
            VersionMarkdown versionMarkdown;
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.exists(CHANGELOG_PATH))
                        .thenReturn(true);
                filesMockedStatic.when(() -> Files.lines(CHANGELOG_PATH, StandardCharsets.UTF_8))
                        .thenReturn(markdown.lines());

                versionMarkdown = MarkdownUtils.readVersionMarkdown(
                        log,
                        CHANGELOG_PATH,
                        ARTIFACT_ONLY_IDENTIFIER,
                        GROUP_ID
                );
            }

            assertThat(versionMarkdown)
                    .satisfies(
                            data -> assertThat(data.bumps())
                                    .hasSize(4)
                                    .containsEntry(
                                            new MavenArtifact(GROUP_ID, "none"),
                                            SemanticVersionBump.NONE
                                    )
                                    .containsEntry(
                                            new MavenArtifact(GROUP_ID, "patch"),
                                            SemanticVersionBump.PATCH
                                    )
                                    .containsEntry(
                                            new MavenArtifact(GROUP_ID, "minor"),
                                            SemanticVersionBump.MINOR
                                    )
                                    .containsEntry(
                                            new MavenArtifact(GROUP_ID, "major"),
                                            SemanticVersionBump.MAJOR
                                    )
                    );

            assertThat(log.getLogRecords())
                    .hasSize(3)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.INFO)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("Read 10 lines from %s".formatted(CHANGELOG_PATH)))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .hasFieldOrPropertyWithValue("message", Optional.of("""
                                            YAML front matter:
                                                none: None
                                                patch: patch
                                                minor: MINOR
                                                major: MAJOR\
                                            """))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty()),
                            line -> assertThat(line)
                                    .hasFieldOrPropertyWithValue("level", TestLog.LogLevel.DEBUG)
                                    .satisfies(record -> assertThat(record.message().orElseThrow())
                                            .contains("Maven artifacts and semantic version bumps:")
                                            .contains("groupId:none=NONE")
                                            .contains("groupId:patch=PATCH")
                                            .contains("groupId:minor=MINOR")
                                            .contains("groupId:major=MAJOR"))
                                    .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                    );
        }
    }

    @Nested
    class CreateVersionBumpHeaderTest {

        @Test
        void logIsNull_ThrowNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.createVersionBumpsHeader(null, Map.of(), IDENTIFIER))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`log` must not be null");
        }

        @Test
        void bumpsIsNull_ThrowNullPointerException() {
            assertThatThrownBy(() -> MarkdownUtils.createVersionBumpsHeader(
                    new TestLog(TestLog.LogLevel.DEBUG),
                    null,
                    IDENTIFIER
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`bumps` must not be null");
        }

        @Test
        void nullKey_ThrowMojoExecutionException() {
            TestLog log = new TestLog(TestLog.LogLevel.DEBUG);
            Map<MavenArtifact, SemanticVersionBump> bumps = new HashMap<>();
            bumps.put(null, SemanticVersionBump.PATCH);
            assertThatThrownBy(() -> MarkdownUtils.createVersionBumpsHeader(log, bumps, IDENTIFIER))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Unable to construct version bump YAML")
                    .hasRootCauseInstanceOf(JsonMappingException.class)
                    .hasRootCauseMessage("""
                            Null key for a Map not allowed in JSON (use a converting NullKeySerializer?) \
                            (through reference chain: java.util.HashMap["null"])\
                            """);
        }

        @ParameterizedTest
        @EnumSource(value = SemanticVersionBump.class, names = {"MAJOR", "MINOR", "PATCH"})
        void singleEntry_Valid(SemanticVersionBump bump) throws MojoExecutionException {
            TestLog log = new TestLog(TestLog.LogLevel.NONE);
            Map<MavenArtifact, SemanticVersionBump> bumps = Map.of(
                    new MavenArtifact("group", "artifact"), bump
            );
            YamlFrontMatterBlock block = MarkdownUtils.createVersionBumpsHeader(log, bumps, IDENTIFIER);
            assertThat(block)
                    .isNotNull()
                    .hasFieldOrPropertyWithValue("yaml", """
                            group:artifact: "%s"\
                            """.formatted(bump));

            assertThat(log.getLogRecords())
                    .isNotEmpty()
                    .hasSize(1)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .returns("""
                                            Version bumps YAML:
                                                group:artifact: "%s"
                                            """.formatted(bump), l -> l.message().orElseThrow())
                    );
        }

        @Test
        void singleEntry_OnlyArtifactId_Valid() throws MojoExecutionException {
            TestLog log = new TestLog(TestLog.LogLevel.NONE);
            Map<MavenArtifact, SemanticVersionBump> bumps = Map.of(
                    new MavenArtifact(GROUP_ID, ARTIFACT_ID), SemanticVersionBump.MINOR
            );
            YamlFrontMatterBlock block = MarkdownUtils.createVersionBumpsHeader(
                    log,
                    bumps,
                    ARTIFACT_ONLY_IDENTIFIER
            );
            assertThat(block)
                    .isNotNull()
                    .hasFieldOrPropertyWithValue("yaml", """
                            artifactId: "MINOR"\
                            """);

            assertThat(log.getLogRecords())
                    .isNotEmpty()
                    .hasSize(1)
                    .satisfiesExactly(
                            line -> assertThat(line)
                                    .returns("""
                                            Version bumps YAML:
                                                artifactId: "MINOR"
                                            """, l -> l.message().orElseThrow())
                    );
        }

        @Test
        void multipleEntries_Valid() throws MojoExecutionException {
            TestLog log = new TestLog(TestLog.LogLevel.NONE);
            Map<MavenArtifact, SemanticVersionBump> bumps = Map.of(
                    new MavenArtifact("group-1", "major"), SemanticVersionBump.MAJOR,
                    new MavenArtifact("group-2", "minor"), SemanticVersionBump.MINOR,
                    new MavenArtifact("group-3", "patch"), SemanticVersionBump.PATCH
            );
            YamlFrontMatterBlock block = MarkdownUtils.createVersionBumpsHeader(log, bumps, IDENTIFIER);
            assertThat(block)
                    .isNotNull()
                    .extracting(YamlFrontMatterBlock::getYaml)
                    .asInstanceOf(InstanceOfAssertFactories.STRING)
                    .contains("group-1:major: \"MAJOR\"")
                    .contains("group-2:minor: \"MINOR\"")
                    .contains("group-3:patch: \"PATCH\"");
        }
    }
}
