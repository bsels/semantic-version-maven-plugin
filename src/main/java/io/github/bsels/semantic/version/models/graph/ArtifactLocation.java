package io.github.bsels.semantic.version.models.graph;

import io.github.bsels.semantic.version.models.MavenArtifact;

import java.util.Objects;

public record ArtifactLocation(MavenArtifact artifact, String folder) {
    public ArtifactLocation {
        Objects.requireNonNull(artifact, "`artifact` must not be null");
        Objects.requireNonNull(folder, "`folder` must not be null");
    }
}
