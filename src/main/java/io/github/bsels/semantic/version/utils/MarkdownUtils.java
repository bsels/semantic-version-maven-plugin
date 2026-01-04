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
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /// @throws NullPointerException    if `log` or `markdownFile` is null
    /// @throws MojoExecutionException  if an error occurs while reading the file, parsing the YAML front matter, or the Markdown does not contain the expected YAML front matter block
    public static VersionMarkdown readVersionMarkdown(Log log, Path markdownFile)
            throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(log, "`log` must not be null");
        Objects.requireNonNull(markdownFile, "`markdownFile` must not be null");
        Node document;
        try (Stream<String> lineStream = Files.lines(markdownFile, StandardCharsets.UTF_8)) {
            List<String> lines = lineStream.toList();
            log.info("Read %d lines from %s".formatted(lines.size(), markdownFile));
            document = PARSER.parse(String.join("\n", lines));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to read '%s' file".formatted(markdownFile), e);
        }

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

    /// Recursively logs the structure of a Markdown document starting from the given node.
    /// Each node in the document is logged at a specific indentation level to visually
    /// represent the hierarchy of the Markdown content.
    ///
    /// @param log   the logger used for logging the node details; must not be null
    /// @param node  the current node in the Markdown structure to be logged; can be null
    /// @param level the indentation level, used to format logged output to represent hierarchy
    private static void printMarkdown(Log log, Node node, int level) {
        if (node == null) {
            return;
        }
        log.debug(node.toString().indent(level).stripTrailing());
        printMarkdown(log, node.getFirstChild(), level + 2);
        printMarkdown(log, node.getNext(), level);
    }
}
