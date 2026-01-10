package io.github.bsels.semantic.version.models;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Represents a semantic version consisting of a major, minor, and patch version, along with an optional suffix.
/// Semantic versioning is a versioning scheme that follows the format `major.minor.patch-suffix`.
/// The major, minor, and patch components are mandatory and must be non-negative integers.
/// The optional suffix, if present, begins with a dash and consists of alphanumeric characters, dots, or dashes.
///
/// @param major  the major version number must be a non-negative integer
/// @param minor  the minor version number must be a non-negative integer
/// @param patch  the patch version number must be a non-negative integer
/// @param suffix an optional suffix for the version must start with a dash and may contain alphanumeric characters, dashes, or dots
public record SemanticVersion(int major, int minor, int patch, Optional<String> suffix) {
    /// A compiled regular expression pattern representing the syntax for a semantic version string.
    /// The semantic version format includes:
    /// - A mandatory major version component (non-negative integer).
    /// - A mandatory minor version component (non-negative integer).
    /// - A mandatory patch version component (non-negative integer).
    /// - An optional suffix component, starting with a dash and consisting of alphanumeric characters, dots, or dashes.
    ///
    /// The full version string must adhere to the following format:
    /// `major.minor.patch-suffix`, where the suffix is optional.
    ///
    /// This pattern ensures that the input string strictly follows the semantic versioning rules.
    public static final Pattern REGEX = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(-[a-zA-Z0-9-.]+)?$");
    /// A regular expression pattern designed to validate the format of suffixes in semantic versions.
    ///
    /// The suffix must:
    /// - Start with a dash (`-`)
    /// - Contain only alphanumeric characters, dashes (`-`), or dots (`.`)
    ///
    /// This pattern is primarily used to ensure proper validation of optional suffix components in semantic versioning.
    ///
    /// Example of valid suffixes:
    /// - `-alpha`
    /// - `-1.0.0`
    /// - `-beta.2`
    ///
    /// Example of invalid suffixes:
    /// - `_alpha` (does not start with a dash)
    /// - `alpha` (does not start with a dash)
    public static final String SUFFIX_REGEX_PATTERN = "^-[a-zA-Z0-9-.]+$";

    /// Constructs a new instance of SemanticVersion with the specified major, minor, patch,
    /// and optional suffix components.
    /// Validates that the major, minor, and patch numbers are non-negative and that the optional suffix,
    /// if present, adheres to the required format.
    ///
    /// @param major  the major version number must be a non-negative integer
    /// @param minor  the minor version number must be a non-negative integer
    /// @param patch  the patch version number must be a non-negative integer
    /// @param suffix an optional suffix for the version must start with a dash and may contain alphanumeric characters, dashes, or dots
    /// @throws IllegalArgumentException if any of the version numbers are negative, or the suffix does not match the required format
    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version parts must be non-negative");
        }
        suffix = Objects.requireNonNullElseGet(suffix, Optional::<String>empty)
                .filter(Predicate.not(String::isEmpty));
        suffix.ifPresent(SemanticVersion::validateSuffix);
    }

    /// Parses a semantic version string and creates a `SemanticVersion` instance.
    /// The version string must comply with the semantic versioning format:
    /// `major.minor.patch` or `major.minor.patch-suffix`.
    /// Major, minor, and patch must be non-negative integers.
    /// The optional suffix must start with a dash and may contain alphanumeric characters, dashes, or dots.
    ///
    /// @param version the semantic version string to parse
    /// @return a new `SemanticVersion` instance representing the parsed version
    /// @throws IllegalArgumentException if the version string is blank, does not match the semantic versioning format, or contains invalid components
    /// @throws NullPointerException     if the version string is null
    public static SemanticVersion of(String version) throws IllegalArgumentException, NullPointerException {
        version = Objects.requireNonNull(version, "`version` must not be null").strip();
        Matcher matches = REGEX.matcher(version);
        if (!matches.matches()) {
            throw new IllegalArgumentException("Invalid semantic version format: %s, should match the regex %s".formatted(version, REGEX.pattern()));
        }
        return new SemanticVersion(
                Integer.parseInt(matches.group(1)),
                Integer.parseInt(matches.group(2)),
                Integer.parseInt(matches.group(3)),
                Optional.ofNullable(matches.group(4))
                        .filter(Predicate.not(String::isEmpty))
        );
    }

    /// Validates that the provided suffix matches the expected format.
    /// The suffix must be alphanumeric, may contain dashes or dots, and cannot begin with a dash.
    ///
    /// @param suffix the suffix string to validate; must be in the correct format as defined by the [#SUFFIX_REGEX_PATTERN]
    /// @throws IllegalArgumentException if the suffix does not match the required format
    private static void validateSuffix(String suffix) throws IllegalArgumentException {
        if (!suffix.matches(SUFFIX_REGEX_PATTERN)) {
            throw new IllegalArgumentException("Suffix must be alphanumeric, dash, or dot, and should not start with a dash");
        }
    }

    /// Returns a string representation of the semantic version in the format "major.minor.patch-suffix",
    /// where the suffix is optional.
    ///
    /// @return the semantic version as a string in the format "major.minor.patch" or "major.minor.patch-suffix" if a suffix is present.
    @Override
    public String toString() {
        return "%d.%d.%d%s".formatted(major, minor, patch, suffix.orElse(""));
    }

    /// Increments the semantic version based on the specified type of version bump.
    /// The type of bump determines which component of the version (major, minor, or patch) is incremented.
    /// If the bump type is NONE, the version remains unchanged.
    ///
    /// @param bump the type of version increment to apply (MAJOR, MINOR, PATCH, or NONE)
    /// @return a new `SemanticVersion` instance with the incremented version, or the same instance if no change is required
    /// @throws NullPointerException the `bump` parameter is null
    public SemanticVersion bump(SemanticVersionBump bump) throws NullPointerException {
        return switch (Objects.requireNonNull(bump, "`bump` must not be null")) {
            case MAJOR -> new SemanticVersion(major + 1, 0, 0, suffix);
            case MINOR -> new SemanticVersion(major, minor + 1, 0, suffix);
            case PATCH -> new SemanticVersion(major, minor, patch + 1, suffix);
            case NONE -> this;
        };
    }

    /// Removes the optional suffix from the semantic version, if present, and returns a new `SemanticVersion`
    /// instance that consists only of the major, minor, and patch components.
    ///
    /// @return a new `SemanticVersion` instance without the suffix.
    public SemanticVersion stripSuffix() {
        if (suffix.isPresent()) {
            return new SemanticVersion(major, minor, patch, Optional.empty());
        }
        return this;
    }

    /// Returns a new `SemanticVersion` instance with the specified suffix.
    /// The suffix may contain additional information about the version, such as build metadata or pre-release identifiers.
    ///
    /// @param suffix the suffix to associate with the version must not be null
    /// @return a new `SemanticVersion` instance with the specified suffix, or the same instance if the suffix is already present.
    /// @throws NullPointerException if the `suffix` parameter is null
    public SemanticVersion withSuffix(String suffix) throws NullPointerException {
        Objects.requireNonNull(suffix, "`suffix` must not be null");
        validateSuffix(suffix);
        if (this.suffix.filter(suffix::equals).isPresent()) {
            return this;
        }
        return new SemanticVersion(major, minor, patch, Optional.of(suffix));
    }
}
