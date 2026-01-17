package io.github.bsels.semantic.version.models;

import org.commonmark.node.Node;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/// Represents parsed Markdown content for a version alongside mappings of Maven artifacts
/// and their corresponding semantic version bumps.
///
/// This record is used to encapsulate structured content and versioning information
/// for Maven artifacts in the context of semantic versioning.
/// The Markdown content is represented as a hierarchical structure of nodes, and the version bumps are specified
/// as a mapping between Maven artifacts and their respective semantic version increments.
///
/// The record guarantees that the content and the map of bumps are always non-null
/// and enforces that the map of bumps cannot be empty.
///
/// @param path    the path to the Markdown file containing the version information
/// @param content the root node of the Markdown content representing the parsed structure must not be null
/// @param bumps   the mapping of Maven artifacts to their respective semantic version bumps; must not be null or empty
public record VersionMarkdown(
        Path path,
        Node content,
        Map<MavenArtifact, SemanticVersionBump> bumps
) {

    /// Constructs an instance of the VersionMarkdown record.
    /// Validates the provided content and bumps map to ensure they are non-null and meet required constraints.
    ///
    /// @param path    the path to the Markdown file containing the version information; can be null for in-memory files
    /// @param content the root node representing the content; must not be null
    /// @param bumps   a map of Maven artifacts to their corresponding semantic version bumps; must not be null or empty
    /// @throws NullPointerException     if content or bumps is null
    /// @throws IllegalArgumentException if bumps is empty
    public VersionMarkdown {
        Objects.requireNonNull(content, "`content` must not be null");
        bumps = Map.copyOf(Objects.requireNonNull(bumps, "`bumps` must not be null"));
        if (bumps.isEmpty()) {
            throw new IllegalArgumentException("`bumps` must not be empty");
        }
    }
}
