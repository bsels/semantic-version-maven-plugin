package io.github.bsels.semantic.version.models.graph;

import io.github.bsels.semantic.version.models.MavenArtifact;

import java.util.List;
import java.util.Objects;

/// Represents a detailed node in the dependency graph, including the artifact's location and its dependencies.
///
/// This record contains comprehensive information about a project artifact, including its identity,
/// its physical location, and a list of its internal project dependencies with their locations.
///
/// @param artifact     the Maven artifact representing the project; must not be null
/// @param folder       the folder path of the project; must not be null
/// @param dependencies the list of internal project dependencies and their locations; must not be null
public record DetailedGraphNode(MavenArtifact artifact, String folder, List<ArtifactLocation> dependencies) {

    /// Constructs a new `DetailedGraphNode` and validates that its components are non-null.
    ///
    /// @param artifact     the Maven artifact
    /// @param folder       the folder path
    /// @param dependencies the list of dependencies
    /// @throws NullPointerException if `artifact`, `folder`, or `dependencies` is null
    public DetailedGraphNode {
        Objects.requireNonNull(artifact, "`artifact` must not be null");
        Objects.requireNonNull(folder, "`folder` must not be null");
        Objects.requireNonNull(dependencies, "`dependencies` must not be null");
    }
}
