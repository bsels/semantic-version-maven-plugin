package io.github.bsels.semantic.version.parameters;

/// Enum representing different modes of handling project versions.
public enum Modus {
    /// Represents the mode for handling a single project version.
    SINGLE_PROJECT_VERSION,
    /// Represents the mode for handling single or multi-project versions using the revision property.
    /// The revision property is defined on the root project.
    REVISION_PROPERTY,
    /// Represents the mode for handling multi-project versions.
    MULTI_PROJECT_VERSION,
    /// Represents the mode for handling multi-project versions, but only for leaf projects.
    MULTI_PROJECT_VERSION_ONLY_LEAFS
}
