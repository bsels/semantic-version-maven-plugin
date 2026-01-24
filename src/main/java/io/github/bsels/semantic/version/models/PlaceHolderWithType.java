package io.github.bsels.semantic.version.models;

import java.util.Objects;

/// Represents a placeholder with an associated formatType.
///
/// This record encapsulates a string placeholder and its corresponding formatType,
/// ensuring that both values are non-null during initialization.
///
/// @param placeholder the placeholder string; must not be null
/// @param formatType        the formatType string associated with the placeholder; must not be null
public record PlaceHolderWithType(String placeholder, String formatType) {

    /// Constructs a new instance of the `PlaceHolderWithType` record.
    /// Validates that both the placeholder and formatType values are non-null during initialization.
    ///
    /// @param placeholder the placeholder string; must not be null
    /// @param formatType        the formatType string; must not be null
    /// @throws NullPointerException if `placeholder` or `formatType` is null
    public PlaceHolderWithType {
        Objects.requireNonNull(placeholder, "`placeholder` cannot be null");
        Objects.requireNonNull(formatType, "`formatType` cannot be null");
    }
}
