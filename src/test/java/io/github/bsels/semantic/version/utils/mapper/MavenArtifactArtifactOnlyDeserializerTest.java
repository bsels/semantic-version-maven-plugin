package io.github.bsels.semantic.version.utils.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.bsels.semantic.version.models.MavenArtifact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenArtifactArtifactOnlyDeserializerTest {
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";

    @Test
    void nullGroupId_ThrowsNullPointerException() {
        assertThatThrownBy(() -> new MavenArtifactArtifactOnlyDeserializer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`groupId` must not be null");
    }

    @Test
    void deserializeValue_ReturnsMavenArtifact() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new SimpleModule()
                        .addDeserializer(MavenArtifact.class, new MavenArtifactArtifactOnlyDeserializer(GROUP_ID))
                );

        MavenArtifact artifact = mapper.readValue("\"" + ARTIFACT_ID + "\"", MavenArtifact.class);

        assertThat(artifact)
                .isNotNull()
                .hasFieldOrPropertyWithValue("groupId", GROUP_ID)
                .hasFieldOrPropertyWithValue("artifactId", ARTIFACT_ID);
    }
}
