package io.github.bsels.semantic.version.models.graph;

import io.github.bsels.semantic.version.models.MavenArtifact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ArtifactLocationTest {
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String FOLDER = "folder/path";

    @Nested
    class ConstructorTest {

        @ParameterizedTest
        @CsvSource(value = {
                "null,null,artifact",
                "null," + FOLDER + ",artifact",
                GROUP_ID + ":" + ARTIFACT_ID + ",null,folder"
        }, nullValues = "null")
        void nullInput_ThrowsNullPointerException(String artifactString, String folder, String exceptionParameter) {
            MavenArtifact artifact = artifactString == null ? null : MavenArtifact.of(artifactString);
            assertThatThrownBy(() -> new ArtifactLocation(artifact, folder))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`%s` must not be null", exceptionParameter);
        }

        @Test
        void validInputs_ReturnsCorrectArtifactLocation() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            ArtifactLocation location = new ArtifactLocation(artifact, FOLDER);
            assertThat(location)
                    .isNotNull()
                    .hasFieldOrPropertyWithValue("artifact", artifact)
                    .hasFieldOrPropertyWithValue("folder", FOLDER);
        }
    }

    @Nested
    class AccessorTest {

        @Test
        void artifact_ReturnsCorrectValue() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            ArtifactLocation location = new ArtifactLocation(artifact, FOLDER);
            assertThat(location.artifact())
                    .isEqualTo(artifact);
        }

        @Test
        void folder_ReturnsCorrectValue() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            ArtifactLocation location = new ArtifactLocation(artifact, FOLDER);
            assertThat(location.folder())
                    .isEqualTo(FOLDER);
        }
    }

    @Nested
    class EqualsAndHashCodeTest {

        @Test
        void sameValues_AreEqual() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            ArtifactLocation location1 = new ArtifactLocation(artifact, FOLDER);
            ArtifactLocation location2 = new ArtifactLocation(artifact, FOLDER);
            assertThat(location1)
                    .isEqualTo(location2)
                    .hasSameHashCodeAs(location2);
        }

        @Test
        void differentArtifact_AreNotEqual() {
            MavenArtifact artifact1 = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            MavenArtifact artifact2 = new MavenArtifact("otherGroup", ARTIFACT_ID);
            ArtifactLocation location1 = new ArtifactLocation(artifact1, FOLDER);
            ArtifactLocation location2 = new ArtifactLocation(artifact2, FOLDER);
            assertThat(location1)
                    .isNotEqualTo(location2);
        }

        @Test
        void differentFolder_AreNotEqual() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            ArtifactLocation location1 = new ArtifactLocation(artifact, FOLDER);
            ArtifactLocation location2 = new ArtifactLocation(artifact, "other/folder");
            assertThat(location1)
                    .isNotEqualTo(location2);
        }
    }
}
