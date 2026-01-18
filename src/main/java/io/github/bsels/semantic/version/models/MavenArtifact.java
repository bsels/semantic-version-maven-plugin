package io.github.bsels.semantic.version.models;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Comparator;
import java.util.Objects;

/// Represents a Maven artifact consisting of a group ID and an artifact ID.
///
/// Maven artifacts are uniquely identified by their group ID and artifact ID within a specific repository or context.
/// The group ID typically represents the organization or project that produces the artifact,
/// while the artifact ID identifies the specific library, tool, or application.
///
/// Instances of this record validate that both the group ID and artifact ID are non-null during construction.
///
/// @param groupId    the group ID of the Maven artifact; must not be null
/// @param artifactId the artifact ID of the Maven artifact; must not be null
public record MavenArtifact(String groupId, String artifactId)
        implements Comparable<MavenArtifact> {
    /// A comparator used to define the natural ordering of `MavenArtifact` instances.
    /// This comparator first compares Maven artifacts by their `groupId` and, if they are equal,
    /// it proceeds to compare them by their `artifactId`.
    ///
    /// The comparison ensures a consistent and logical sorting order for `MavenArtifact` objects
    /// based on their group and artifact identifiers.
    public static final Comparator<MavenArtifact> COMPARATOR = Comparator.comparing(MavenArtifact::groupId)
            .thenComparing(MavenArtifact::artifactId);

    /// Constructs a new instance of `MavenArtifact` with the specified group ID and artifact ID.
    /// Validates that neither the group ID nor the artifact ID are null.
    ///
    /// @param groupId    the group ID of the artifact must not be null
    /// @param artifactId the artifact ID must not be null
    /// @throws NullPointerException if `groupId` or `artifactId` is null
    public MavenArtifact {
        Objects.requireNonNull(groupId, "`groupId` must not be null");
        Objects.requireNonNull(artifactId, "`artifactId` must not be null");
    }

    /// Creates a new `MavenArtifact` instance by parsing a string in the format `<group-id>:<artifact-id>`.
    ///
    /// The input string is expected to contain exactly one colon separating the group ID and the artifact ID.
    ///
    /// @param colonSeparatedString the string representing the Maven artifact in the format `<group-id>:<artifact-id>`
    /// @return a new `MavenArtifact` instance constructed using the parsed group ID and artifact ID
    /// @throws IllegalArgumentException if the input string does not conform to the expected format
    /// @throws NullPointerException     if the `colonSeparatedString` parameter is null
    @JsonCreator
    public static MavenArtifact of(String colonSeparatedString) {
        String[] parts = Objects.requireNonNull(colonSeparatedString, "`colonSeparatedString` must not be null")
                .split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid Maven artifact format: %s, expected <group-id>:<artifact-id>".formatted(
                            colonSeparatedString
                    )
            );
        }
        return new MavenArtifact(parts[0], parts[1]);
    }

    /// Returns a string representation of the Maven artifact in the format "groupId:artifactId".
    ///
    /// @return a string representation of the Maven artifact, combining the group ID and artifact ID separated by a colon
    @Override
    public String toString() {
        return "%s:%s".formatted(groupId, artifactId);
    }

    /// Compares this MavenArtifact instance with the specified MavenArtifact for order.
    /// The comparison is based on the string representations of the MavenArtifact instances,
    /// which are formatted as "groupId:artifactId".
    ///
    /// @param other the MavenArtifact to be compared with this instance
    /// @return a negative integer, zero, or a positive integer as the string representation of this MavenArtifact is less than, equal to, or greater than the string representation of the specified MavenArtifact
    @Override
    public int compareTo(MavenArtifact other) {
        return COMPARATOR.compare(this, other);
    }
}
