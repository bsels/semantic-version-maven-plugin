package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.node.CustomBlock;

import java.util.Objects;

/// Represents a custom block in a document that encapsulates YAML front matter.
/// This class is used to manage and store the YAML content extracted from a block within a parsed document.
/// It serves as a specialized block type extending the [CustomBlock] class, allowing integration with
/// CommonMark parsers and extensions.
///
/// This block is typically used in Markdown parsing contexts where YAML front matter
/// is specified at the beginning of a document, delimited by specific markers (e.g., "---").
/// The YAML content is stored as a single string, which can be retrieved using the provided accessor methods.
public class YamlFrontMatterBlock extends CustomBlock {
    /// Represents the YAML content extracted or associated with a specific block of text within a document.
    /// This variable is expected to hold the serialized YAML string content and is managed as part of a block's lifecycle.
    private String yaml;

    /// Constructs a new instance of the YamlFrontMatterBlock class with the specified YAML content.
    ///
    /// @param yaml the YAML string content to be associated with this block; must not be null
    /// @throws NullPointerException if the provided YAML parameter is null
    public YamlFrontMatterBlock(String yaml) throws NullPointerException {
        this.yaml = Objects.requireNonNull(yaml, "`yaml` must not be null");
    }

    /// Retrieves the YAML content of this block.
    ///
    /// @return the YAML string associated with this block, or null if not set
    public String getYaml() {
        return yaml;
    }

    /// Sets the YAML content for this block.
    ///
    /// Updates the YAML front matter content associated with this block.
    /// The input string must not be null.
    ///
    /// @param yaml the YAML string content to be set; must not be null
    /// @throws NullPointerException if the provided YAML parameter is null
    public void setYaml(String yaml) throws NullPointerException {
        this.yaml = Objects.requireNonNull(yaml, "`yaml` must not be null");
    }
}
