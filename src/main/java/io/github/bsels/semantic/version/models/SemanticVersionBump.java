package io.github.bsels.semantic.version.models;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/// Represents the type of version increment in the context of semantic versioning.
/// Semantic versioning consists of major, minor, and patch version components, along with an optional suffix.
/// This enum is used to denote which part of a version should be incremented or whether no increment is to occur.
///
/// The enum values are defined as:
/// - NONE: Indicates that no version increment is to occur.
/// - PATCH: Indicates a patch version increment, which may introduce backward-compatible bug fixes.
/// - MINOR: Indicates a minor version increment, which may add functionality in a backward-compatible manner.
/// - MAJOR: Indicates a major version increment, which may introduce breaking changes.
public enum SemanticVersionBump {
    /// Indicates that no version increment is to occur.
    /// This value is used in the context of semantic versioning when the version should remain unchanged.
    NONE,
    /// Indicates a patch version increment in the context of semantic versioning.
    /// A patch increment is typically used to introduce backward-compatible bug fixes.
    PATCH,
    /// Indicates a minor version increment in the context of semantic versioning.
    /// A minor increment is typically used to add new functionality in a backward-compatible manner.
    MINOR,
    /// Indicates a major version increment in the context of semantic versioning.
    /// A major increment typically introduces breaking changes, making backward compatibility
    /// with earlier versions unlikely.
    MAJOR;

    /// Converts a string representation of a semantic version bump to its corresponding enum value.
    ///
    /// The input string is case-insensitive and will be converted to uppercase to match the enum names.
    ///
    /// @param value the string representation of the semantic version bump, such as "MAJOR", "MINOR", "PATCH", or "NONE"
    /// @return the corresponding `SemanticVersionBump` enum value
    /// @throws IllegalArgumentException if the input value does not match any of the valid enum names
    @JsonCreator
    public static SemanticVersionBump fromString(String value) throws IllegalArgumentException {
        if (value == null) {
            return NONE;
        }
        return valueOf(value.toUpperCase());
    }

    /// Determines the maximum semantic version bump from an array of [SemanticVersionBump] values.
    /// The bumps are compared based on their natural order, and the highest value is returned.
    /// If the array is empty, [#NONE] is returned.
    ///
    /// @param bumps the array of [SemanticVersionBump] values to evaluate
    /// @return the maximum semantic version bump in the array, or [#NONE] if the array is empty
    /// @throws NullPointerException if the `bumps` parameter is null
    /// @see #max(Collection)
    public static SemanticVersionBump max(SemanticVersionBump... bumps) throws NullPointerException {
        Objects.requireNonNull(bumps, "`bumps` must not be null");
        return max(Arrays.asList(bumps));
    }

    /// Determines the maximum semantic version bump from a collection of [SemanticVersionBump] values.
    /// The bumps are compared based on their natural order, and the highest value is returned.
    /// If the collection is empty or only has null pointers, [#NONE] is returned.
    ///
    /// @param bumps the collection of [SemanticVersionBump] values to evaluate
    /// @return the maximum semantic version bump in the collection, or [#NONE] if the collection is empty
    /// @throws NullPointerException if the `bumps` parameter is null
    public static SemanticVersionBump max(Collection<SemanticVersionBump> bumps) throws NullPointerException {
        Objects.requireNonNull(bumps, "`bumps` must not be null");
        return bumps.stream()
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(NONE);
    }
}
