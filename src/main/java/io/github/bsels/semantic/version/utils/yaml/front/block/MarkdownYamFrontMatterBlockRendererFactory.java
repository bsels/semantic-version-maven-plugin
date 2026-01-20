package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;

import java.util.Objects;
import java.util.Set;

/// A singleton factory class for creating [NodeRenderer] instances that handle rendering of YAML front matter blocks
/// in Markdown documents.
/// This factory is part of the CommonMark extension for processing YAML front matter.
///
/// The [MarkdownYamFrontMatterBlockRendererFactory] follows a singleton design,
/// ensuring only one instance is available throughout the application.
/// It provides mechanisms for creating a renderer specific to processing nodes of YAML front matter blocks,
/// as well as retrieving special characters used by the implementation.
///
/// YAML front matter is a structured block of metadata typically delimited by `---` markers
/// and placed at the beginning of Markdown documents.
/// This factory produces renderers, such as [MarkdownYamFrontMatterBlockRenderer],
/// to handle the production of output for such front matter in accordance with Markdown rendering context requirements.
///
/// The factory interacts with the [MarkdownNodeRendererContext] for rendering configuration,
/// ensuring seamless integration with the Markdown framework during rendering operations.
///
/// ## Responsibilities
///
/// - Provide singleton access to the factory instance via [#getInstance()].
/// - Create YAML front matter renderers via [#create(MarkdownNodeRendererContext)].
/// - Provide the set of special characters supported by the implementation via [#getSpecialCharacters()].
public class MarkdownYamFrontMatterBlockRendererFactory implements MarkdownNodeRendererFactory {
    /// A singleton instance of [MarkdownYamFrontMatterBlockRendererFactory].
    ///
    /// This instance is used to provide a centralized,
    /// shared instance of the [MarkdownYamFrontMatterBlockRendererFactory] class,
    /// adhering to the singleton design pattern.
    /// It ensures that only one instance of the factory exists throughout the application,
    /// which is used for creating node renderers capable of handling YAML front matter blocks in Markdown documents.
    private static final MarkdownYamFrontMatterBlockRendererFactory INSTANCE = new MarkdownYamFrontMatterBlockRendererFactory();

    /// Private constructor for the [MarkdownYamFrontMatterBlockRendererFactory] class.
    ///
    /// This constructor implements a singleton pattern to ensure that only a single instance of the factory can exist.
    /// It is responsible for the instantiation of the singleton instance
    /// and prevents external instantiation of the factory.
    private MarkdownYamFrontMatterBlockRendererFactory() {
        super();
    }

    /// Provides access to the singleton instance of [MarkdownYamFrontMatterBlockRendererFactory].
    /// This factory is responsible for creating node renderers specific to YAML front matter blocks
    /// in Markdown documents.
    ///
    /// @return the singleton instance of [MarkdownYamFrontMatterBlockRendererFactory]
    public static MarkdownYamFrontMatterBlockRendererFactory getInstance() {
        return INSTANCE;
    }

    /// Creates a new instance of [NodeRenderer] to handle YAML front matter block rendering in Markdown documents.
    ///
    /// @param context the rendering context used to facilitate node processing and writing; must not be null
    /// @return a [NodeRenderer] responsible for rendering YAML front matter blocks
    /// @throws NullPointerException if the provided context is null
    @Override
    public NodeRenderer create(MarkdownNodeRendererContext context) throws NullPointerException {
        Objects.requireNonNull(context, "`context` must not be null");
        return new MarkdownYamFrontMatterBlockRenderer(context);
    }

    /// Retrieves the set of special characters used or supported by the implementation.
    ///
    /// This method is typically overridden to provide a collection of characters considered as "special" during
    /// the processing or rendering of a particular content type, such as Markdown or YAML.
    /// In this implementation, an empty set is returned to indicate the absence of any special characters.
    ///
    /// @return a set of characters representing the special characters; an empty set if none are defined
    @Override
    public Set<Character> getSpecialCharacters() {
        return Set.of();
    }
}
