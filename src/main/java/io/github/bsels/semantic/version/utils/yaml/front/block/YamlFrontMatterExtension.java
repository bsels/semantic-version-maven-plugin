package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.parser.Parser;

/// The [YamlFrontMatterExtension] class is an implementation of the [Parser.ParserExtension] interface
/// designed to add support for parsing YAML front matter in CommonMark-based Markdown documents.
///
/// YAML front matter is commonly used to define metadata for a document and is usually located at the
/// very beginning of the file, enclosed in a pair of delimiter lines (e.g., "---").
/// This extension integrates with the parser framework by adding the necessary support for identifying and processing
/// such YAML front matter blocks.
public class YamlFrontMatterExtension implements Parser.ParserExtension {

    /// Constructs a new instance of the [YamlFrontMatterExtension] class.
    ///
    /// This extension is designed to provide support for parsing YAML front matter in CommonMark-based Markdown
    /// documents.
    /// It integrates with the parser framework by adding a custom block parser that identifies and processes
    /// YAML front matter blocks at the beginning of documents.
    public YamlFrontMatterExtension() {
        super();
    }

    /// Extends the provided [Parser.Builder] to incorporate support for YAML front matter parsing.
    ///
    /// This method adds a custom block parser factory to the builder,
    /// enabling the parsing and processing of YAML front matter blocks in Markdown documents.
    /// YAML front matter blocks are typically used to define metadata at the beginning of a document.
    ///
    /// @param parserBuilder the builder object used to configure the parser; this method adds a custom block parser factory to handle YAML front matter blocks
    @Override
    public void extend(Parser.Builder parserBuilder) {
        parserBuilder.customBlockParserFactory(new YamlFrontMatterBlockParser.Factory());
    }

    /// Creates and returns a new instance of the [YamlFrontMatterExtension] class.
    ///
    /// This method provides a convenient way to get a new instance of the extension,
    /// which integrates YAML front matter parsing capabilities with a CommonMark-based Markdown parser.
    /// The returned extension can be used to configure a parser for processing documents containing
    /// YAML front matter metadata.
    ///
    /// @return a new instance of the YamlFrontMatterExtension class
    public static YamlFrontMatterExtension create() {
        return new YamlFrontMatterExtension();
    }
}
