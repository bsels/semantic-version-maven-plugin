package io.github.bsels.semantic.version.models;

import java.util.Objects;

/// Represents a change or transition between two versions.
/// This class is a record that holds information about the old version
/// and the new version during a version update or transition process.
///
/// Objects of this record are immutable and encapsulate both the old
/// and the new version as non-null values.
///
/// @param oldVersion the previous version, representing the initial state before the update. Must not be null.
/// @param newVersion the updated version, representing the final state after the change. Must not be null.
public record VersionChange(String oldVersion, String newVersion) {

    /// Constructs an instance of VersionChange to represent a transition from one version to another.
    /// Both the old version and the new version must be non-null.
    ///
    /// @param oldVersion the previous version. Must not be null.
    /// @param newVersion the new version. Must not be null.
    /// @throws NullPointerException if either oldVersion or newVersion is null.
    public VersionChange {
        Objects.requireNonNull(oldVersion, "`oldVersion` must not be null");
        Objects.requireNonNull(newVersion, "`newVersion` must not be null");
    }
}
