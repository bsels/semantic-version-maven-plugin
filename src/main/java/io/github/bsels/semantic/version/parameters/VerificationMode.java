package io.github.bsels.semantic.version.parameters;

/// Enum representing different modes of verification logic applicable to projects within a specific context.
/// The `VerificationMode` enum provides a set of constants that define how validations
/// or checks should be applied in relation to projects,
/// which may be used in various multi-project or single-project setups.
public enum VerificationMode {
    /// Represents the absence of any specific verification mode.
    /// The `NONE` verification mode indicates that no verification logic or checks should be applied,
    /// and it serves as a default or placeholder value within the `VerificationMode` enumeration.
    NONE,
    /// Represents a verification mode indicating that the presence of at least one project within the current context
    /// must be ensured.
    /// This mode is typically used in scenarios where a minimum threshold of projects (at least one) is required
    /// to proceed with certain actions or validations.
    AT_LEAST_ONE_PROJECT,
    /// Represents a verification mode that focuses on projects that have dependencies on other projects within
    /// the current context.
    /// This mode is typically used in scenarios where actions or checks are applied specifically to dependent projects,
    /// based on the structure or relationships in a multi-project setup.
    DEPENDENT_PROJECTS,
    /// Represents a verification mode indicating that actions or checks should be applied to all projects within
    /// the current context.
    /// This may be used in scenarios where every project in a multi-project setup needs to be considered or verified
    /// in totality.
    ALL_PROJECTS
}
