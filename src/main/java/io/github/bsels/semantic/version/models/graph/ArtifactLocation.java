package io.github.bsels.semantic.version.models.graph;

import io.github.bsels.semantic.version.models.MavenArtifact;

import java.util.Objects;

/// Represents the physical location of a Maven artifact within the project structure.
///
/// This record associates a [MavenArtifact] with its corresponding folder path,
/// which can be either absolute or relative depending on the plugin configuration.
///
/// @param artifact the Maven artifact; must not be null
/// @param folder   the folder path where the artifact's project is located; must not be null
public record ArtifactLocation(MavenArtifact artifact, String folder) {

    /// Constructs a new `ArtifactLocation` and validates that its components are non-null.
    ///
    /// @param artifact the Maven artifact
    /// @param folder   the folder path
    /// @throws NullPointerException if `artifact` or `folder` is null
    public ArtifactLocation {
        Objects.requireNonNull(artifact, "`artifact` must not be null");
        Objects.requireNonNull(folder, "`folder` must not be null");
    }
}
