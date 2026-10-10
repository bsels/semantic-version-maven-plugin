package io.github.bsels.semantic.version.parameters;

/// Enumeration representing modes for handling build timestamp behavior.
///
/// This enum provides predefined constants specifying how the build timestamp should be treated
/// during processes where timestamps are managed.
/// These modes allow the user to determine whether timestamps are skipped, only updated, or inserted/updated (upsert).
///
/// Enum Values:
/// - `SKIP`: Indicates that the build timestamp should not be processed or altered.
/// - `UPDATE_ONLY`: Indicates that the build timestamp should be updated if it already exists.
/// - `UPSERT`: Indicates that the build timestamp should be updated if it exists,
///   or created if it does not already exist.
public enum HandleBuildTimestamp {
    /// Indicates that the build timestamp should not be processed or altered.
    SKIP,
    /// Indicates that the build timestamp should be updated if it already exists.
    UPDATE_ONLY,
    /// Indicates that the build timestamp should be updated if it exists, or created if it does not already exist.
    UPSERT
}
