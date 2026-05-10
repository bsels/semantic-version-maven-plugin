package io.github.bsels.semantic.version.utils.mapper;

import io.github.bsels.semantic.version.models.MavenArtifact;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.io.IOException;

/// A custom serializer for the [MavenArtifact] class that serializes only the artifact ID of the Maven artifact.
///
/// This serializer is designed to selectively output the `artifactId` component of a [MavenArtifact].
/// The [#serialize(MavenArtifact, JsonGenerator, SerializerProvider)] method leverages the [MavenArtifact#artifactId()]
/// method to retrieve the artifact ID and write it as a JSON string.
///
/// The purpose of this serializer is to provide a minimalist representation of Maven artifacts in JSON format,
/// omitting other properties such as the group ID.
///
/// This class extends `JsonSerializer<MavenArtifact>` and is intended to be used with Jackson's object mapping
/// framework.
///
/// Thread Safety: This class is stateless and thread-safe.
public final class MavenArtifactArtifactOnlySerializer extends ValueSerializer<MavenArtifact> {

    /// Constructs a new instance of `MavenArtifactArtifactOnlySerializer`.
    ///
    /// This is a custom serializer for the [MavenArtifact] class used to serialize only the `artifactId` property
    /// of a [MavenArtifact] instance.
    /// The serializer is stateless and does not require any initialization.
    ///
    /// This constructor does not perform any additional setup or configuration,
    /// as the serializer is designed for minimalist representation of [MavenArtifact] objects,
    /// focusing solely on the `artifactId`.
    public MavenArtifactArtifactOnlySerializer() {
        super();
    }

    /// Serializes an [MavenArtifact] instance by writing its artifact ID as a JSON string.
    ///
    /// @param mavenArtifact      the [MavenArtifact] instance to serialize; must not be null
    /// @param jsonGenerator      the [JsonGenerator] used to write JSON content; must not be null
    /// @param serializerProvider the [SerializerProvider] that can be used to get serializers for serializing other types of objects if necessary; must not be null
    @Override
    public void serialize(
            MavenArtifact mavenArtifact,
            JsonGenerator jsonGenerator,
            SerializationContext serializerProvider
    ) {
        TokenStreamContext outputContext = jsonGenerator.streamWriteContext();
        if (outputContext.hasCurrentName() || outputContext.getParent() == null) {
            jsonGenerator.writeString(mavenArtifact.artifactId());
        } else {
            jsonGenerator.writeName(mavenArtifact.artifactId());
        }
    }
}
