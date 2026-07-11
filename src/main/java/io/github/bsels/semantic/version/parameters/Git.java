package io.github.bsels.semantic.version.parameters;

/// Enum representing different states or contexts related to Git functionality.
/// This can include representing the absence of Git integration,
/// a staging state, or an active commit state within a Git repository.
public enum Git {
    /// Represents a state where no Git-related context or repository information is associated.
    /// This enum value can be used to indicate an absence of Git functionality
    /// or integration within the current context.
    NO_GIT,
    /// Represents a staging state in a Git repository.
    /// This enum value is used to indicate that the current instance corresponds to a staging state.
    /// @deprecated use [#STAGING] instead. Since {DEPRECATION_VERSION}.
    @Deprecated(since = "{DEPRECATION_VERSION}", forRemoval = true)
    STASH,
    /// Represents the state where changes are staged in a Git repository.
    /// This enum value indicates that the current instance corresponds to a staging state.
    STAGING,
    /// Represents the state of an active commit in a Git repository.
    /// This enum value indicates that the current instance is in a commit state,
    /// which can be checked using the [#isCommit()] method.
    COMMIT;

    /// Determines if the current instance represents a staging state.
    ///
    /// @return `true` if the current instance is either [#STASH], [#STAGING] or [#COMMIT], otherwise `false`.
    public boolean isStaging() {
        return switch (this) {
            case COMMIT, STASH, STAGING -> true;
            case NO_GIT -> false;
        };
    }

    /// Determines if the current instance represents a staging state.
    ///
    /// @return `true` if the current instance is either [#STASH], [#STAGING] or [#COMMIT], otherwise `false`.
    /// @deprecated use [#isStaging()] instead. Since {DEPRECATION_VERSION}.
    @Deprecated(since = "{DEPRECATION_VERSION}", forRemoval = true)
    public boolean isStash() {
        return isStaging();
    }

    /// Determines if the current instance represents a commit state.
    ///
    /// @return `true` if the current instance is [#COMMIT], otherwise `false`.
    public boolean isCommit() {
        return switch (this) {
            case COMMIT -> true;
            case STASH, STAGING, NO_GIT -> false;
        };
    }
}
