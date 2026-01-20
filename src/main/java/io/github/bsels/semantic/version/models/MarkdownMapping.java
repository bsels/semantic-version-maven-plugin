package io.github.bsels.semantic.version.models;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Represents a mapping of Maven artifacts to their corresponding semantic version bumps and version-specific markdown entries.
/// It encapsulates two main mappings:
/// - A mapping of MavenArtifact instances to their associated SemanticVersionBump values.
/// - A mapping of MavenArtifact instances to a list of VersionMarkdown entries.
///
/// This record ensures immutability and validates input data during construction.
///
/// @param versionBumpMap a map associating MavenArtifact instances with their corresponding SemanticVersionBump values; must not be null
/// @param markdownMap    a map associating MavenArtifact instances with a list of VersionMarkdown entries; must not be null
public record MarkdownMapping(
        Map<MavenArtifact, SemanticVersionBump> versionBumpMap,
        Map<MavenArtifact, List<VersionMarkdown>> markdownMap
) {

    /// Constructs a new instance of the MarkdownMapping record.
    /// Validates and creates immutable copies of the provided maps to ensure integrity and immutability.
    ///
    /// @param versionBumpMap a map associating MavenArtifact instances with their corresponding SemanticVersionBump values; must not be null
    /// @param markdownMap    a map associating MavenArtifact instances with a list of VersionMarkdown entries; must not be null
    /// @throws NullPointerException if `versionBumpMap` or `markdownMap` is null
    public MarkdownMapping {
        versionBumpMap = Map.copyOf(Objects.requireNonNull(versionBumpMap, "`versionBumpMap` must not be null"));
        markdownMap = Map.copyOf(Objects.requireNonNull(markdownMap, "`markdownMap` must not be null"));
    }
}
