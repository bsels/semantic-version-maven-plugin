package io.github.bsels.semantic.version.models;

import java.util.Objects;

/// Represents a set of version headers consisting of major, minor, patch, and other components.
/// This class is designed for use in versioning systems to provide standardized labels for identifying
/// and managing version information.
///
/// Each component (major, minor, patch, and other) is represented as a string and must not be null.
/// The class ensures immutability and type safety by leveraging the `record` feature in Java.
///
/// The class also provides a default constructor that initializes the headers with predefined constant values,
/// offering a standardized approach to creating instances.
///
/// @param major the major version header; must not be null
/// @param minor the minor version header; must not be null
/// @param patch the patch version header; must not be null
/// @param other an additional version-related header; must not be null
public record VersionHeaders(String major, String minor, String patch, String other) {
    /// A constant representing the "Major" version header identifier.
    ///
    /// This header is used to specify the major component within a versioning system,
    /// typically indicating significant changes or updates that may not be backward-compatible.
    /// It ensures standardized labeling for identifying major-level version updates.
    public static final String MAJOR_HEADER = "Major";
    /// A constant representing the "Minor" version header identifier.
    ///
    /// This header is used to specify the minor component within a versioning system,
    /// typically indicating functionality updates or improvements that are backward-compatible.
    /// It ensures standardized labeling for identifying minor-level version updates.
    public static final String MINOR_HEADER = "Minor";
    /// A constant representing the "Patch" version header identifier.
    ///
    /// This header is used to specify the patch component within a versioning system,
    /// typically indicating backward-compatible bug fixes or changes.
    /// It ensures standardized labeling for identifying patch-level version updates.
    public static final String PATCH_HEADER = "Patch";
    /// A constant representing the "Other" version header identifier.
    ///
    /// This header is used to specify additional version-related metadata that does not fall under typical categories
    /// like major, minor, or patch.
    /// It ensures a clear and consistent label for referencing supplementary version information.
    public static final String OTHER_HEADER = "Other";

    /// Constructs an instance of VersionHeaders, ensuring that all header components are non-null.
    ///
    /// @param major the major version header; must not be null
    /// @param minor the minor version header; must not be null
    /// @param patch the patch version header; must not be null
    /// @param other an additional version-related header; must not be null
    /// @throws NullPointerException if any of the header components (major, minor, patch, or other) are null
    public VersionHeaders {
        Objects.requireNonNull(major, "`major` header cannot be null");
        Objects.requireNonNull(minor, "`minor` header cannot be null");
        Objects.requireNonNull(patch, "`patch` header cannot be null");
        Objects.requireNonNull(other, "`other` header cannot be null");
    }

    /// Constructs a default instance of VersionHeaders using predefined header constants
    /// for major, minor, patch, and other headers.
    ///
    /// This no-argument constructor initializes the header fields with the following default values:
    /// - Major: `MAJOR_HEADER`
    /// - Minor: `MINOR_HEADER`
    /// - Patch: `PATCH_HEADER`
    /// - Other: `OTHER_HEADER`
    ///
    /// The purpose of this constructor is to provide a standardized way to create a VersionHeaders
    /// instance with commonly used headers. All header fields are guaranteed to be non-null.
    public VersionHeaders() {
        this(MAJOR_HEADER, MINOR_HEADER, PATCH_HEADER, OTHER_HEADER);
    }

    /// Retrieves the corresponding header string based on the specified version bump type.
    ///
    /// The method evaluates the provided `versionBump` and returns the appropriate header string associated with
    /// the semantic versioning increment type (major, minor, patch) or a default value for no increment.
    ///
    /// @param versionBump the type of semantic version bump, such as MAJOR, MINOR, PATCH, or NONE; must not be null
    /// @return the header string corresponding to the specified version bump type
    /// @throws NullPointerException if the provided `versionBump` is null
    public String getHeader(SemanticVersionBump versionBump) throws NullPointerException {
        Objects.requireNonNull(versionBump, "`versionBump` must not be null");
        return switch (versionBump) {
            case MAJOR -> major;
            case MINOR -> minor;
            case PATCH -> patch;
            case NONE -> other;
        };
    }
}
