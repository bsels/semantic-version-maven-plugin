package io.github.bsels.semantic.version.models;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenArtifactTest {
    private static final String ARTIFACT_ID = "artifactId";
    private static final String GROUP_ID = "groupId";

    @Nested
    class ConstructorTest {

        @ParameterizedTest
        @CsvSource(value = {
                "null,null,groupId",
                "null," + ARTIFACT_ID + ",groupId",
                GROUP_ID + ",null,artifactId"
        }, nullValues = "null")
        void nullInput_ThrowsNullPointerException(String groupId, String artifact, String exceptionParameter) {
            assertThatThrownBy(() -> new MavenArtifact(groupId, artifact))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`%s` must not be null", exceptionParameter);
        }

        @Test
        void validInputs_ReturnsCorrectArtifact() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            assertThat(artifact)
                    .isNotNull()
                    .hasFieldOrPropertyWithValue("groupId", GROUP_ID)
                    .hasFieldOrPropertyWithValue("artifactId", ARTIFACT_ID);
        }
    }

    @Nested
    class OfTest {

        @Test
        void nullInput_ThrowsNullPointerException() {
            assertThatThrownBy(() -> MavenArtifact.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`colonSeparatedString` must not be null");
        }

        @ParameterizedTest
        @EmptySource
        @ValueSource(strings = {"data", "groupId:artifactId:version"})
        void invalidInput_ThrowsIllegalArgumentException(String colonSeparatedString) {
            assertThatThrownBy(() -> MavenArtifact.of(colonSeparatedString))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid Maven artifact format: %s, expected <group-id>:<artifact-id>".formatted(
                            colonSeparatedString
                    ));
        }

        @Test
        void validInput_ReturnsCorrectArtifact() {
            MavenArtifact artifact = MavenArtifact.of(GROUP_ID + ":" + ARTIFACT_ID);
            assertThat(artifact)
                    .isNotNull()
                    .hasFieldOrPropertyWithValue("groupId", GROUP_ID)
                    .hasFieldOrPropertyWithValue("artifactId", ARTIFACT_ID);
        }
    }

    @Nested
    class ToStringTest {

        @Test
        void toString_ReturnsCorrectFormat() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            assertThat(artifact.toString())
                    .isNotNull()
                    .isEqualTo("%s:%s".formatted(GROUP_ID, ARTIFACT_ID));
        }
    }
}
