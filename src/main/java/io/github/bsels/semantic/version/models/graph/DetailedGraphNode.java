package io.github.bsels.semantic.version.models.graph;

import io.github.bsels.semantic.version.models.MavenArtifact;

import java.util.List;
import java.util.Objects;

public record DetailedGraphNode(MavenArtifact artifact, String folder, List<ArtifactLocation> dependencies) {

    public DetailedGraphNode {
        Objects.requireNonNull(artifact, "`artifact` must not be null");
        Objects.requireNonNull(folder, "`folder` must not be null");
        Objects.requireNonNull(dependencies, "`dependencies` must not be null");
    }
}
