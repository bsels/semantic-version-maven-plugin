package io.github.bsels.semantic.version.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.utils.yaml.front.block.MarkdownYamFrontMatterBlockRendererFactory;
import io.github.bsels.semantic.version.utils.yaml.front.block.YamlFrontMatterBlock;
import io.github.bsels.semantic.version.utils.yaml.front.block.YamlFrontMatterExtension;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

/// Utility class for handling operations related to Markdown processing.
///
/// This class provides static methods for parsing, rendering, merging,
/// and writing structured Markdown content and YAML front matter.
/// It is not intended to be instantiated.
public final class MarkdownUtils {

    /// A constant map type representing a mapping between [MavenArtifact] objects and [SemanticVersionBump] values.
    ///
    /// This map type is constructed using Jackson's [TypeFactory] for type-safe operations
    /// on a HashMap that maps [MavenArtifact] as keys to [SemanticVersionBump] as values.
    /// It is intended to provide a standardized type structure for operations where Maven artifacts are associated with
    /// their corresponding semantic version bump types.
    ///
    /// This constant is defined as a static field within the utility class,
    /// ensuring it cannot be modified during runtime and is globally accessible.
    private static final MapType MAVEN_ARTIFACT_BUMP_MAP_TYPE = TypeFactory.defaultInstance()
            .constructMapType(HashMap.class, MavenArtifact.class, SemanticVersionBump.class);

    /// A static and final [ObjectMapper] instance configured as a [YAMLMapper].
    /// This variable is intended for parsing and generating YAML content.
    /// It provides a convenient singleton for YAML operations within the context of the MarkdownUtils utility class.
    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    /// A statically defined parser built for processing CommonMark-based Markdown with certain custom configurations.
    /// This parser is configured to:
    /// - Utilize the [YamlFrontMatterExtension], which adds support for recognizing and processing YAML front matter
    ///   metadata in Markdown documents.
    /// - Include source spans to represent the start and end positions of both block and inline elements in the
    ///   original text, enabled by setting the [IncludeSourceSpans] mode to [IncludeSourceSpans#BLOCKS_AND_INLINES].
    ///
    /// The parser is immutable and thread-safe, making it suitable for concurrent use across multiple threads.
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(YamlFrontMatterExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    /// A static, pre-configured instance of the [Renderer] used to process and render Markdown content within
    /// the [MarkdownUtils] utility class.
    ///
    /// The [#MARKDOWN_RENDERER] is initialized using the [MarkdownRenderer#builder()] method to create a builder for
    /// fine-grained control over the rendering configuration and finalizes the building process via `build()`.
    ///
    /// This instance serves as the primary renderer for various Markdown processing tasks in the utility methods
    /// provided by the [MarkdownUtils] class.
    ///
    /// The renderer handles the task of generating structured output for Markdown nodes.
    ///
    /// This is a singleton-like static constant to ensure consistent rendering behavior throughout the invocation
    /// of Markdown processing methods.
    private static final Renderer MARKDOWN_RENDERER = MarkdownRenderer.builder()
            .nodeRendererFactory(MarkdownYamFrontMatterBlockRendererFactory.getInstance())
            .build();

    /// Represents the title "Changelog" used as the top-level heading in Markdown changelogs processed by
    /// the utility methods of the `MarkdownUtils` class.
    ///
    /// This constant is used as a reference to ensure that the changelog Markdown structure
    /// adheres to the expected format, where the main heading for the document is a single H1 titled
    private static final String CHANGELOG = "Changelog";

    /// Utility class for handling operations related to Markdown processing.
    /// This class contains static methods and is not intended to be instantiated.
    private MarkdownUtils() {
        // No instance needed
    }

    /// Parses a Markdown file to extract its contents and associated YAML front matter,
    /// which specifies mappings of Maven artifacts to their corresponding semantic version bumps.
    /// The parsed Markdown content is stored as a hierarchical structure of nodes,
    /// and the versioning information is extracted from the YAML front matter block.
    ///
    /// @param log          the logger used to log informational and debug messages during the parsing process; must not be null
    /// @param markdownFile the path to the Markdown file to be read and parsed; must not be null
    /// @return a [VersionMarkdown] object containing the parsed Markdown content and the extracted Maven artifact to semantic version bump mappings
    /// @throws NullPointerException   if `log` or `markdownFile` is null
    /// @throws MojoExecutionException if an error occurs while reading the file, parsing the YAML front matter, or the Markdown does not contain the expected YAML front matter block
    public static VersionMarkdown readVersionMarkdown(Log log, Path markdownFile)
            throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(log, "`log` must not be null");
        Objects.requireNonNull(markdownFile, "`markdownFile` must not be null");
        Node document = readMarkdown(log, markdownFile);

        if (!(document.getFirstChild() instanceof YamlFrontMatterBlock yamlFrontMatterBlock)) {
            throw new MojoExecutionException("YAML front matter block not found in '%s' file".formatted(markdownFile));
        }
        String yaml = yamlFrontMatterBlock.getYaml();
        yamlFrontMatterBlock.unlink();

        Map<MavenArtifact, SemanticVersionBump> bumps;
        try {
            log.debug("YAML front matter:\n%s".formatted(yaml.indent(4).stripTrailing()));
            bumps = YAML_MAPPER.readValue(yaml, MAVEN_ARTIFACT_BUMP_MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new MojoExecutionException(
                    "YAML front matter does not contain valid maven artifacts and semantic version bump", e
            );
        }
        log.debug("Maven artifacts and semantic version bumps:\n%s".formatted(bumps));
        printMarkdown(log, document, 0);
        return new VersionMarkdown(document, bumps);
    }

    /// Reads and parses a Markdown file, returning its content as a structured Node object.
    /// The method logs the number of lines read from the file for informational purposes.
    ///
    /// @param log          the logger used to log informational messages during the parsing process; must not be null
    /// @param markdownFile the path to the Markdown file to be read and parsed; must not be null
    /// @return a Node object representing the parsed structure of the Markdown content
    /// @throws NullPointerException   if log or markdownFile is null
    /// @throws MojoExecutionException if an error occurs while reading the file or parsing its content
    public static Node readMarkdown(Log log, Path markdownFile) throws MojoExecutionException {
        Objects.requireNonNull(log, "`log` must not be null");
        Objects.requireNonNull(markdownFile, "`markdownFile` must not be null");
        if (!Files.exists(markdownFile)) {
            log.info("No changelog file found at '%s', creating an empty CHANGELOG internally".formatted(markdownFile));
            Document document = new Document();
            Heading heading = new Heading();
            heading.setLevel(1);
            heading.appendChild(new Text(CHANGELOG));
            document.appendChild(heading);
            return document;
        }
        try (Stream<String> lineStream = Files.lines(markdownFile, StandardCharsets.UTF_8)) {
            List<String> lines = lineStream.toList();
            log.info("Read %d lines from %s".formatted(lines.size(), markdownFile));
            return PARSER.parse(String.join("\n", lines));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to read '%s' file".formatted(markdownFile), e);
        }
    }

    /// Merges version-specific Markdown content into a changelog Node structure.
    ///
    /// This method updates the provided changelog Node by inserting a new heading for the specified version
    /// at the appropriate position.
    /// The content associated with the version is then added under this heading,
    /// grouped by semantic version bump types (e.g., MAJOR, MINOR, PATCH).
    /// The changelog must begin with a single H1 heading titled "Changelog".
    ///
    /// @param changelog     the root Node of the changelog Markdown structure to be updated; must not be null
    /// @param version       the version string to be added to the changelog; must not be null
    /// @param headerToNodes a mapping of SemanticVersionBump types to their associated Markdown nodes; must not be null
    /// @throws NullPointerException     if any of the parameters `changelog`, `version`, or `headerToNodes` is null
    /// @throws IllegalArgumentException if the changelog is not a document or does not start with a single H1 heading titled "Changelog"
    /// @throws IllegalArgumentException if any of the nodes in the map entries node lists is not a document
    public static void mergeVersionMarkdownsInChangelog(
            Node changelog,
            String version,
            Map<SemanticVersionBump, List<Node>> headerToNodes
    ) throws NullPointerException, IllegalArgumentException {
        Objects.requireNonNull(changelog, "`changelog` must not be null");
        Objects.requireNonNull(version, "`version` must not be null");
        Objects.requireNonNull(headerToNodes, "`headerToNodes` must not be null");

        if (!(changelog instanceof Document document)) {
            throw new IllegalArgumentException("`changelog` must be a Document");
        }
        if (!(document.getFirstChild() instanceof Heading heading &&
                heading.getLevel() == 1 &&
                heading.getFirstChild() instanceof Text text && CHANGELOG.equals(text.getLiteral()))) {
            throw new IllegalArgumentException("Changelog must start with a single H1 heading with the text 'Changelog'");
        }
        Node nextChild = heading.getNext();

        Heading newVersionHeading = new Heading();
        newVersionHeading.setLevel(2);
        newVersionHeading.appendChild(new Text("%s - %s".formatted(version, LocalDate.now())));
        heading.insertAfter(newVersionHeading);

        Comparator<Map.Entry<SemanticVersionBump, List<Node>>> comparator = Map.Entry.comparingByKey();
        Node current = headerToNodes.entrySet()
                .stream()
                .sorted(comparator.reversed())
                .reduce(newVersionHeading, MarkdownUtils::copyVersionMarkdownToChangeset, mergeNodes());

        assert current.getNext() == nextChild : "Incorrectly inserted nodes into changelog";
    }

    /// Writes a Markdown document to a specified file. Optionally creates a backup of the existing file
    /// before overwriting it.
    ///
    /// @param markdownFile the path to the Markdown file where the document will be written; must not be null
    /// @param document     the node representing the structured Markdown content to be written; must not be null
    /// @param backupOld    a boolean indicating whether to create a backup of the existing file before writing
    /// @throws NullPointerException   if `markdownFile` or `document` is null
    /// @throws MojoExecutionException if an error occurs while creating the backup or writing to the file
    public static void writeMarkdownFile(Path markdownFile, Node document, boolean backupOld)
            throws MojoExecutionException, NullPointerException {
        Objects.requireNonNull(markdownFile, "`markdownFile` must not be null");
        Objects.requireNonNull(document, "`document` must not be null");
        if (backupOld) {
            Utils.backupFile(markdownFile);
        }
        try (Writer writer = Files.newBufferedWriter(markdownFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
            writeMarkdown(writer, document);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write %s".formatted(markdownFile), e);
        }
    }

    /// Writes the rendered Markdown content of the given document node to the specified output writer.
    /// This operation uses a pre-configured Markdown renderer to transform the structured document node into
    /// Markdown format before writing it to the output.
    ///
    /// @param output   the writer to which the rendered Markdown content will be written; must not be null
    /// @param document the node representing the structured Markdown content to be rendered; must not be null
    /// @throws NullPointerException if `output` or `document` is null
    public static void writeMarkdown(Writer output, Node document) throws NullPointerException {
        Objects.requireNonNull(output, "`output` must not be null");
        Objects.requireNonNull(document, "`document` must not be null");
        MARKDOWN_RENDERER.render(document, output);
    }

    /// Recursively logs the structure of a Markdown document starting from the given node.
    /// Each node in the document is logged at a specific indentation level to visually
    /// represent the hierarchy of the Markdown content.
    ///
    /// @param log   the logger used for logging the node details; must not be null
    /// @param node  the current node in the Markdown structure to be logged; can be null
    /// @param level the indentation level, used to format logged output to represent hierarchy
    public static void printMarkdown(Log log, Node node, int level) {
        if (!log.isDebugEnabled()) {
            return;
        }
        if (node == null) {
            return;
        }
        log.debug(node.toString().indent(level).stripTrailing());
        printMarkdown(log, node.getFirstChild(), level + 2);
        printMarkdown(log, node.getNext(), level);
    }

    /// Creates a simple version bump document that indicates a project version has been bumped as a result
    /// of dependency changes.
    ///
    /// @param mavenArtifact the Maven artifact associated with the version bump; must not be null
    /// @return a [VersionMarkdown] object containing the generated document and a mapping of the Maven artifact to a PATCH semantic version bump
    /// @throws NullPointerException if the `mavenArtifact` parameter is null
    public static VersionMarkdown createSimpleVersionBumpDocument(MavenArtifact mavenArtifact)
            throws NullPointerException {
        Objects.requireNonNull(mavenArtifact, "`mavenArtifact` must not be null");
        Document document = new Document();
        Paragraph paragraph = new Paragraph();
        paragraph.appendChild(new Text("Project version bumped as result of dependency bumps"));
        document.appendChild(paragraph);
        return new VersionMarkdown(document, Map.of(mavenArtifact, SemanticVersionBump.NONE));
    }

    /// Merges two [Node] instances by inserting the second node after the first node and returning the second node.
    ///
    /// @return a [BinaryOperator] that takes two [Node] instances, inserts the second node after the first, and returns the second node
    private static BinaryOperator<Node> mergeNodes() {
        return (a, b) -> {
            a.insertAfter(b);
            return b;
        };
    }

    /// Copies version-specific Markdown content to a changelog changeset by creating a new heading
    /// for the semantic version bump type and appending associated nodes under that heading.
    ///
    /// @param current the current Node in the Markdown structure to which the bump type heading and its associated nodes will be inserted; must not be null
    /// @param entry   a Map.Entry containing a SemanticVersionBump key representing the bump type (e.g., MAJOR, MINOR, PATCH, NONE) and a List of Nodes associated with that bump type; must not be null
    /// @return the last Node inserted into the Markdown structure, representing the merged result of the operation
    /// @throws IllegalArgumentException if any of the nodes in the entry node list is not a document
    private static Node copyVersionMarkdownToChangeset(Node current, Map.Entry<SemanticVersionBump, List<Node>> entry)
            throws IllegalArgumentException {
        Heading bumpTypeHeading = new Heading();
        bumpTypeHeading.setLevel(3);
        bumpTypeHeading.appendChild(new Text(switch (entry.getKey()) {
            case MAJOR -> "Major";
            case MINOR -> "Minor";
            case PATCH -> "Patch";
            case NONE -> "Other";
        }));
        current.insertAfter(bumpTypeHeading);
        return entry.getValue()
                .stream()
                .map(MarkdownUtils::cloneNode)
                .reduce(bumpTypeHeading, MarkdownUtils::insertNodeChilds, mergeNodes());
    }

    /// Inserts all child nodes of the given node into the current node sequentially.
    /// Each child node of the provided node is inserted after the current node, one at a time,
    /// and the method updates the current node reference to the last inserted child node.
    ///
    /// @param currentLambda the node after which the child nodes will be inserted; must not be null
    /// @param node          the node whose children are to be inserted; must not be null
    /// @return the last child node that was inserted after the current node
    private static Node insertNodeChilds(Node currentLambda, Node node) throws IllegalArgumentException {
        BinaryOperator<Node> binaryOperator = mergeNodes();
        Node nextChild = node.getFirstChild();
        while (nextChild != null) {
            Node nextSibling = nextChild.getNext();
            currentLambda = binaryOperator.apply(currentLambda, nextChild);
            nextChild = nextSibling;
        }
        return currentLambda;
    }

    /// Creates a deep copy of the given node by parsing its rendered Markdown representation.
    ///
    /// @param node the [Node] object to be cloned. Must not be null.
    /// @return a new [Node] object that represents a deep copy of the input node.
    /// @throws IllegalArgumentException if the `node` parameter is not a document
    private static Node cloneNode(Node node) {
        if (!(node instanceof Document document)) {
            throw new IllegalArgumentException("Node must be a Document");
        }
        return PARSER.parse(MARKDOWN_RENDERER.render(document));
    }
}
