package io.github.bsels.semantic.version.parameters;

/// Enum representing different modes of handling project versions.
public enum Modus {
    /// Represents the mode for handling single or multi-project versions using each project's version property'.
    /// The project version will be defined on each project individually.
    PROJECT_VERSION,
    /// Represents the mode for handling single or multi-project versions using the revision property.
    /// The revision property is defined on the root project.
    REVISION_PROPERTY,
    /// Represents the mode for handling single or multi-project versions using each project's version property',
    /// but only for leaf projects in a multi-module setup; non-leaf projects will be skipped.
    /// The project version will be defined on each project individually.
    PROJECT_VERSION_ONLY_LEAFS
}
