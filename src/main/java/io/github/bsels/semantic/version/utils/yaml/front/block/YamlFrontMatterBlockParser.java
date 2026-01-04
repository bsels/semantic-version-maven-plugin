package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.node.Block;
import org.commonmark.node.Document;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockParser;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/// A parser for YAML front matter blocks in Markdown documents.
///
/// This class is responsible for detecting, parsing, and processing YAML front matter blocks,
/// typically found at the beginning of Markdown documents.
/// YAML front matter is delimited by lines consisting solely of three hyphens (`---`).
/// The parsed content is stored line by line and used to create a [YamlFrontMatterBlock]
/// representing the structured metadata.
public class YamlFrontMatterBlockParser extends AbstractBlockParser {
    /// A compiled regular expression pattern used to identify the delimiters of a YAML front matter block.
    ///
    /// This pattern is designed to match lines consisting solely of three hyphens (`---`),
    /// and it serves as an indicator for the start or end of a YAML front matter section in Markdown documents.
    /// YAML front matter is typically used to provide metadata at the beginning of a document and is enclosed
    /// between these delimiter lines.
    ///
    /// The pattern plays a critical role in parsing Markdown content by differentiating between YAML
    /// front matter and the rest of the document.
    /// It is used in conjunction with the [YamlFrontMatterBlockParser] class to identify
    /// and process the YAML front matter block correctly.
    private static final Pattern YAML_FRONT_MATTER_PATTERN = Pattern.compile("^---$");

    /// A list of strings representing the content of the YAML front matter block.
    ///
    /// This variable is used to store the individual lines of the YAML front matter as they are parsed.
    /// Each line corresponds to a line of text within the YAML block and is added to this list during
    /// the parsing process.
    ///
    /// The content of this list is used to construct a [YamlFrontMatterBlock] which encapsulates
    /// the YAML front matter block in a document.
    ///
    /// This list is immutable and is initialized as an empty list upon the construction of the
    /// [YamlFrontMatterBlockParser] object.
    private final List<String> lines;

    /// Constructs a new instance of the [YamlFrontMatterBlockParser] class.
    ///
    /// This parser is responsible for handling YAML front matter blocks in Markdown documents.
    /// It reads the lines representing the YAML front matter and stores them for further processing.
    /// The parsed content is eventually converted into a custom block [YamlFrontMatterBlock] representing
    /// the YAML front matter.
    ///
    /// This constructor initializes an empty list to store the lines of YAML front matter content as they are
    /// encountered during parsing.
    public YamlFrontMatterBlockParser() {
        lines = new ArrayList<>();
    }

    /// Returns a [Block] object representing the YAML front matter block.
    /// The block is constructed using the concatenated lines of YAML front matter content.
    ///
    /// @return a [YamlFrontMatterBlock] containing the serialized YAML front matter content
    @Override
    public Block getBlock() {
        return new YamlFrontMatterBlock(String.join("\n", lines));
    }

    /// Attempts to continue parsing a block of text according to the current parser state.
    /// If the current line matches the YAML front matter pattern, parsing is concluded for the block.
    /// Otherwise, the line is added to the block content, and parsing continues.
    ///
    /// @param parserState the current state of the parser, including the line being processed
    /// @return a [BlockContinue] object indicating whether parsing should continue, finish, or continue at a specific index
    @Override
    public BlockContinue tryContinue(ParserState parserState) {
        CharSequence line = parserState.getLine().getContent();
        if (YAML_FRONT_MATTER_PATTERN.matcher(line).matches()) {
            return BlockContinue.finished();
        }
        lines.add(line.toString());
        return BlockContinue.atIndex(parserState.getIndex());
    }

    /// A factory class for creating instances of [YamlFrontMatterBlockParser].
    ///
    /// This class is responsible for detecting and initializing block parsers for YAML front matter blocks in Markdown
    /// documents.
    /// It extends [AbstractBlockParserFactory] to integrate with the CommonMark parser framework.
    ///
    /// The factory checks for the presence of YAML front matter delimiters (e.g., "---") at the start of a document
    /// and creates a new instance of [YamlFrontMatterBlockParser] to handle the parsing of the block.
    /// The YAML front matter block represents structured metadata typically found at the beginning
    /// of Markdown documents.
    public static class Factory extends AbstractBlockParserFactory {

        /// Constructs a new instance of the Factory class.
        ///
        /// The Factory class serves as a custom block parser factory for parsing YAML front matter blocks
        /// in Markdown documents.
        /// It extends the AbstractBlockParserFactory to provide the logic for detecting and initializing
        /// a new block parser of type [YamlFrontMatterBlockParser].
        ///
        /// By default, this constructor initializes the base AbstractBlockParserFactory without requiring
        /// additional parameters or custom configuration.
        public Factory() {
            super();
        }

        /// Attempts to start a new block parser for a YAML front matter block.
        /// This method checks whether the current line in the parser state matches a YAML front matter delimiter
        /// (e.g., "---") and whether it is valid to start a YAML front matter block at this position.
        /// If successful, it initializes a new [YamlFrontMatterBlockParser].
        ///
        /// @param state              the current parser state containing the line content and position
        /// @param matchedBlockParser the parser for the currently matched block, used to determine the parent block and its structural context
        /// @return a `BlockStart` either containing a new `YamlFrontMatterBlockParser` and its start index, or `BlockStart.none()` if the conditions to start a YAML front matter block are not met
        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            CharSequence line = state.getLine().getContent();
            BlockParser parentParser = matchedBlockParser.getMatchedBlockParser();
            if (parentParser.getBlock() instanceof Document document &&
                    document.getFirstChild() == null &&
                    YAML_FRONT_MATTER_PATTERN.matcher(line).matches()) {
                return BlockStart.of(new YamlFrontMatterBlockParser()).atIndex(state.getIndex());
            }
            return BlockStart.none();
        }
    }
}
