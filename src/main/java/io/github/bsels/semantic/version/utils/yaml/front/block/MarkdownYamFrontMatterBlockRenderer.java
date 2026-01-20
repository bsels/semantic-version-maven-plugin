package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownWriter;

import java.util.Objects;
import java.util.Set;

/// The [MarkdownYamFrontMatterBlockRenderer] class is responsible for rendering YAML front matter blocks
/// in Markdown documents by implementing the [NodeRenderer] interface.
///
/// This renderer processes nodes of type [YamlFrontMatterBlock],
/// writing their YAML content delimited by the standard front matter markers (`---`).
/// It integrates with the Markdown rendering context to ensure seamless output of structured Markdown content.
///
/// The renderer relies on a [MarkdownWriter] instance, obtained from the provided [MarkdownNodeRendererContext],
/// to handle raw writes and formatted line outputs during the rendering process.
public class MarkdownYamFrontMatterBlockRenderer implements NodeRenderer {
    /// A [MarkdownWriter] instance used to facilitate writing Markdown content during the rendering process.
    /// This writer is responsible for outputting structured Markdown text,
    /// including custom blocks such as YAML front matter.
    /// This variable is initialized using the [MarkdownNodeRendererContext] provided in the constructor,
    /// ensuring consistent access to the writer throughout the rendering lifecycle.
    /// It is expected that the writer is not null and supports the raw writing
    /// and line handling required for processing nodes like [YamlFrontMatterBlock].
    private final MarkdownWriter writer;

    /// Constructs a new instance of the MarkdownYamFrontMatterBlockRenderer class.
    /// This renderer is responsible for processing and rendering YAML front matter blocks
    /// in Markdown documents, using the provided context.
    ///
    /// @param context the rendering context used to facilitate writing and node processing; must not be null
    /// @throws NullPointerException if the provided context is null
    public MarkdownYamFrontMatterBlockRenderer(MarkdownNodeRendererContext context) throws NullPointerException {
        Objects.requireNonNull(context, "`context` must not be null");
        this.writer = context.getWriter();
    }

    /// Returns the set of `Node` types that this renderer can process.
    ///
    /// @return a set containing the class type `YamlFrontMatterBlock`, which represents the custom block for YAML front matter in Markdown documents
    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(YamlFrontMatterBlock.class);
    }

    /// Renders the specified [Node] by processing it as a [YamlFrontMatterBlock], if applicable.
    /// Outputs the YAML content encapsulated within the block, delimited by front matter markers (`---`).
    ///
    /// @param node the node to be rendered; must be an instance of [YamlFrontMatterBlock]. If the node is not of this type, the method performs no action.
    @Override
    public void render(Node node) {
        if (node instanceof YamlFrontMatterBlock yamlFrontMatterBlock) {
            writer.raw("---");
            writer.line();
            writer.raw(yamlFrontMatterBlock.getYaml());
            writer.line();
            writer.raw("---");
            writer.line();
            writer.line();
        }
    }
}
