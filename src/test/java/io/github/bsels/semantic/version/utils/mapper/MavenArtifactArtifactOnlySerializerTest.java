package io.github.bsels.semantic.version.utils.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.bsels.semantic.version.models.MavenArtifact;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenArtifactArtifactOnlySerializerTest {
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";

    @Test
    void serializeValue_WritesArtifactIdString() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new SimpleModule()
                        .addSerializer(MavenArtifact.class, new MavenArtifactArtifactOnlySerializer())
                );

        String json = mapper.writeValueAsString(Map.of("artifact", new MavenArtifact(GROUP_ID, ARTIFACT_ID)));

        assertThat(json)
                .isEqualTo("{\"artifact\":\"" + ARTIFACT_ID + "\"}");
    }

    @Test
    void serializeKey_WritesArtifactIdFieldName() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new SimpleModule()
                        .addKeySerializer(MavenArtifact.class, new MavenArtifactArtifactOnlySerializer())
                );

        String json = mapper.writeValueAsString(Map.of(new MavenArtifact(GROUP_ID, ARTIFACT_ID), "value"));

        assertThat(json)
                .isEqualTo("{\"" + ARTIFACT_ID + "\":\"value\"}");
    }
}
