package io.github.bsels.semantic.version.utils.mapper;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import io.github.bsels.semantic.version.models.MavenArtifact;

import java.util.Objects;

/// A custom deserializer for YAML keys that only specify the artifact ID of a Maven artifact.
///
/// This deserializer maps YAML keys to [MavenArtifact] instances by combining a predefined group ID
/// with the artifact ID extracted from the YAML key.
/// The deserialized object represents a Maven artifact with both the group ID and artifact ID set.
///
/// Thread Safety: This class is immutable and thread-safe as it uses a final group ID
/// and does not retain the mutable state.
///
/// Responsibilities:
/// - Extract the artifact ID from the provided YAML key.
/// - Combine the artifact ID with the group ID supplied during construction to create a Maven artifact object.
///
/// This class extends the [KeyDeserializer] from Jackson's data-binding framework.
///
/// Constructor Parameters:
/// - `groupId`: The predefined group ID to be paired with the artifact ID from the YAML key.
///   This parameter must not be null.
///
/// Method Details:
/// - [#deserializeKey(String, DeserializationContext)]: Combines the predefined group ID with the artifact ID
///   (extracted from the YAML key) to create a new [MavenArtifact] instance.
///
/// Usage Context:
/// This deserializer is intended for use in scenarios where the YAML key contains only the artifact ID,
/// and a fixed group ID must be applied to construct the complete Maven artifact representation.
public final class MavenArtifactArtifactOnlyKeyDeserializer extends KeyDeserializer {

    /// The predefined group ID used to pair with the artifact ID extracted from the YAML key.
    ///
    /// Responsibilities:
    /// - This constant represents the fixed group ID combined with the artifact ID to construct a [MavenArtifact]
    ///   instance during key deserialization.
    ///
    /// Constraints:
    /// - This value is immutable and must not be null. It is initialized at construction time.
    ///
    /// Thread Safety:
    /// - This field is `final` and thus guarantees immutability, ensuring thread-safe usage.
    private final String groupId;

    /// Constructs a new instance of `MavenArtifactArtifactOnlyKeyDeserializer` with the specified group ID.
    ///
    /// This deserializer is used to handle deserialization of YAML keys into `MavenArtifact` objects by combining
    /// a predefined group ID with an artifact ID.
    /// The group ID is immutable and specified at construction time.
    ///
    /// @param groupId the predefined group ID to associate with the artifacts; must not be null
    /// @throws NullPointerException if `groupId` is null
    public MavenArtifactArtifactOnlyKeyDeserializer(String groupId) throws NullPointerException {
        this.groupId = Objects.requireNonNull(groupId, "`groupId` must not be null");
    }

    /// Deserializes a YAML key into a [MavenArtifact] object by combining a predefined group ID
    /// with the provided artifact ID.
    ///
    /// @param key  the YAML key representing the artifact ID; must not be null
    /// @param ctxt the Jackson deserialization context providing additional configuration information; may be null
    /// @return a new [MavenArtifact] instance constructed by combining the predefined group ID with the provided artifact ID
    /// @throws NullPointerException if the key is null
    @Override
    public MavenArtifact deserializeKey(String key, DeserializationContext ctxt) {
        return new MavenArtifact(groupId, key);
    }
}
