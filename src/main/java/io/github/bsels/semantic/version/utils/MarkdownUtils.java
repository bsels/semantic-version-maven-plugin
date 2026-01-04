package io.github.bsels.semantic.version.utils;

import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.utils.yaml.front.block.YamlFrontMatterBlock;
import io.github.bsels.semantic.version.utils.yaml.front.block.YamlFrontMatterExtension;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class MarkdownUtils {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(YamlFrontMatterExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    private static final MarkdownRenderer RENDERER = MarkdownRenderer.builder()
            .build();

    /**
     * Utility class for handling operations related to Markdown processing.
     * This class contains static methods and is not intended to be instantiated.
     */
    private MarkdownUtils() {
        // No instance needed
    }

    public static VersionMarkdown readMarkdown(Log log, Path markdownFile)
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

        yamlFrontMatterBlock.unlink();

        printMarkdown(log, document, 0);
        return new VersionMarkdown(
                document,
                Map.of() // TODO: parse metadata
        );
    }

    private static void printMarkdown(Log log, Node node, int level) {
        if (node == null) {
            return;
        }
        log.info(node.toString().indent(level).stripTrailing());
        if (node instanceof YamlFrontMatterBlock block) {
            log.info(block.getYaml().indent(level + 2).stripTrailing());
        }
        printMarkdown(log, node.getFirstChild(), level + 2);
        printMarkdown(log, node.getNext(), level);
    }
}
