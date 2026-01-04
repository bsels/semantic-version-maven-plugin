package io.github.bsels.semantic.version.parameters;

/// Enum representing the different types of version increments or handling approaches.
/// This can be based on semantic versioning principles or other version determination mechanisms.
public enum VersionBump {
    /// `FILE_BASED` represents a mode where version determination or handling is dependent on file-based mechanisms.
    /// This could involve reading specific files or configurations to infer or decide version-related changes.
    FILE_BASED,
    /// Represents a version increment of the major component in semantic versioning.
    /// A major increment is typically used for changes that are not backward-compatible.
    MAJOR,
    /// Represents a version increment of the minor component in semantic versioning.
    /// A minor increment is typically used for adding new backward-compatible features.
    MINOR,
    /// Represents a version increment of the patch component in semantic versioning.
    /// A patch increment is typically used for backwards-compatible bug fixes.
    PATCH
}
