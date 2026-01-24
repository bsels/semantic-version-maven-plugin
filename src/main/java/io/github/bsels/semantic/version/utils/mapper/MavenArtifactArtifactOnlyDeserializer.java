package io.github.bsels.semantic.version.utils.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.github.bsels.semantic.version.models.MavenArtifact;

import java.io.IOException;
import java.util.Objects;

/// A custom deserializer for creating instances of `MavenArtifact` using only the artifact ID
/// from JSON input combined with a predefined group ID.
///
/// This class extends the `JsonDeserializer` class to facilitate deserialization of JSON input
/// into `MavenArtifact` objects with a known group ID and a dynamically parsed artifact ID.
/// It is primarily intended for scenarios where the group ID is constant and only the artifact ID
/// is variable within the JSON data.
///
/// The deserialization process extracts the artifact ID from the JSON input
/// and constructs the `MavenArtifact` by pairing it with the predefined group ID.
///
/// Thread Safety:
/// Instances of this deserializer are thread-safe, as the `groupId` is immutable
/// and the deserialization process does not rely on any shared mutable state.
public class MavenArtifactArtifactOnlyDeserializer extends JsonDeserializer<MavenArtifact> {
    /// Represents the predefined group ID associated with a Maven artifact.
    ///
    /// This field is used to specify the group ID that will be combined with an artifact ID
    /// during the deserialization process to construct a `MavenArtifact` object.
    /// It is a constant value set during the instantiation of the deserializer
    /// and remains immutable throughout the lifecycle of the deserializer.
    ///
    /// Thread Safety: This field is declared as `final` and is therefore thread-safe.
    private final String groupId;

    /// Constructs a `MavenArtifactArtifactOnlyDeserializer` with the specified group ID.
    ///
    /// This deserializer is used to deserialize a JSON input into a `MavenArtifact`
    /// by combining the predefined group ID with the artifact ID parsed from the JSON.
    ///
    /// @param groupId the predefined group ID to be associated with the Maven artifact; must not be null
    /// @throws NullPointerException if `groupId` is null
    public MavenArtifactArtifactOnlyDeserializer(String groupId) throws NullPointerException {
        super();
        this.groupId = Objects.requireNonNull(groupId, "`groupId` must not be null");
    }

    /// Deserializes a JSON string into a `MavenArtifact` instance.
    /// The deserialization process reads the artifact ID of the Maven artifact from the JSON input
    /// and combines it with the predefined group ID to construct a `MavenArtifact` object.
    ///
    /// @param p    the `JsonParser` used to parse the JSON input; must not be null
    /// @param ctxt the `DeserializationContext` that can be used to access information about the deserialization process; must not be null
    /// @return a new `MavenArtifact` instance constructed with the predefined group ID and the artifact ID obtained from the JSON input
    /// @throws IOException if an I/O error occurs during parsing
    @Override
    public MavenArtifact deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String artifactId = p.readValueAs(String.class);
        return new MavenArtifact(groupId, artifactId);
    }
}
