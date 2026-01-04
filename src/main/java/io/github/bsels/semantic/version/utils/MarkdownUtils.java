package io.github.bsels.semantic.version.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.utils.yaml.front.block.YamlFrontMatterBlock;
import io.github.bsels.semantic.version.utils.yaml.front.block.YamlFrontMatterExtension;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

public class MarkdownUtils {

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
    /// A static and final [ObjectMapper] instance configured as a [YAMLMapper].
    /// This variable is intended for parsing and generating YAML content.
    /// It provides a convenient singleton for YAML operations within the context of the MarkdownUtils utility class.
    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

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
        try (Stream<String> lineStream = Files.lines(markdownFile, StandardCharsets.UTF_8)) {
            List<String> lines = lineStream.toList();
            log.info("Read %d lines from %s".formatted(lines.size(), markdownFile));
            return PARSER.parse(String.join("\n", lines));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to read '%s' file".formatted(markdownFile), e);
        }
    }

    public static void mergeVersionMarkdownsInChangelog(
            Log log,
            Node changelog,
            String version,
            Map<SemanticVersionBump, List<Node>> headerToNodes
    ) {
        Objects.requireNonNull(log, "`log` must not be null");
        Objects.requireNonNull(changelog, "`changelog` must not be null");
        Objects.requireNonNull(version, "`version` must not be null");
        Objects.requireNonNull(headerToNodes, "`headerToNodes` must not be null");

        if (!(changelog.getFirstChild() instanceof Heading heading &&
                heading.getLevel() == 1 &&
                heading.getFirstChild() instanceof Text text && "Changelog".equals(text.getLiteral()))) {
            throw new IllegalArgumentException("Changelog must start with a single H1 heading with the text 'Changelog'");
        }
        Node nextChild = heading.getNext();

        Heading newVersionHeading = new Heading();
        newVersionHeading.setLevel(2);
        newVersionHeading.appendChild(new Text("%s - %s".formatted(version, LocalDate.now())));
        heading.insertAfter(newVersionHeading);

        Node current = headerToNodes.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .reduce(newVersionHeading, MarkdownUtils::copyVersionMarkdownToChangeset, mergeNodes());

        while (nextChild != null) {
            Node nextSibling = nextChild.getNext();
            current.insertAfter(nextChild);
            current = nextChild;
            nextChild = nextSibling;
        }
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

    private static Node copyVersionMarkdownToChangeset(Node current, Map.Entry<SemanticVersionBump, List<Node>> entry) {
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
                .reduce(bumpTypeHeading, MarkdownUtils::insertNodeChilds, mergeNodes());
    }

    /// Inserts all child nodes of the given node into the current node sequentially.
    /// Each child node of the provided node is inserted after the current node, one at a time,
    /// and the method updates the current node reference to the last inserted child node.
    ///
    /// @param currentLambda the node after which the child nodes will be inserted; must not be null
    /// @param node the node whose children are to be inserted; must not be null
    /// @return the last child node that was inserted after the current node
    private static Node insertNodeChilds(Node currentLambda, Node node) {
        Node nextChild = node.getFirstChild();
        while (nextChild != null) {
            Node nextSibling = nextChild.getNext();
            currentLambda.insertAfter(nextChild);
            currentLambda = nextChild;
            nextChild = nextSibling;
        }
        return currentLambda;
    }

    /// Recursively logs the structure of a Markdown document starting from the given node.
    /// Each node in the document is logged at a specific indentation level to visually
    /// represent the hierarchy of the Markdown content.
    ///
    /// @param log   the logger used for logging the node details; must not be null
    /// @param node  the current node in the Markdown structure to be logged; can be null
    /// @param level the indentation level, used to format logged output to represent hierarchy
    public static void printMarkdown(Log log, Node node, int level) {
        if (node == null) {
            return;
        }
        log.debug(node.toString().indent(level).stripTrailing());
        printMarkdown(log, node.getFirstChild(), level + 2);
        printMarkdown(log, node.getNext(), level);
    }
}
