package io.github.bsels.semantic.version.utils.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.bsels.semantic.version.models.MavenArtifact;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenArtifactArtifactOnlyKeyDeserializerTest {
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";

    @Test
    void nullGroupId_ThrowsNullPointerException() {
        assertThatThrownBy(() -> new MavenArtifactArtifactOnlyKeyDeserializer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`groupId` must not be null");
    }

    @Test
    void deserializeKey_ReturnsMavenArtifact() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new SimpleModule()
                        .addKeyDeserializer(
                                MavenArtifact.class,
                                new MavenArtifactArtifactOnlyKeyDeserializer(GROUP_ID)
                        )
                );

        Map<MavenArtifact, String> artifacts = mapper.readValue(
                "{\"" + ARTIFACT_ID + "\":\"value\"}",
                new TypeReference<>() {
                }
        );

        assertThat(artifacts)
                .hasSize(1)
                .containsEntry(new MavenArtifact(GROUP_ID, ARTIFACT_ID), "value");
    }
}
