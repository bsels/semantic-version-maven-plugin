package io.github.bsels.semantic.version.parameters;

/// Enum representing different modes of artifact identification within a repository or dependency context.
/// This is typically used to determine how an artifact should be uniquely identified,
/// either by combining the group ID with the artifact ID or by using only the artifact ID.
public enum ArtifactIdentifier {
    /// Represents an identifier mode that includes both the group ID and the artifact ID.
    /// This mode is typically used when it is necessary to fully qualify an artifact within a repository
    /// or dependency context to ensure unique identification.
    GROUP_ID_AND_ARTIFACT_ID,
    /// Represents an identifier mode that only includes the artifact ID.
    /// This mode is used when the group ID is not required (all modules share the same group ID).
    ONLY_ARTIFACT_ID
}
