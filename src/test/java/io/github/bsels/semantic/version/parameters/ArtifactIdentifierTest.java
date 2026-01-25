package io.github.bsels.semantic.version.parameters;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactIdentifierTest {

    @Test
    void valuesContainExpectedOrder() {
        assertThat(ArtifactIdentifier.values()).containsExactly(
                ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
                ArtifactIdentifier.ONLY_ARTIFACT_ID
        );
    }

    @Test
    void valueOfRoundTripMatchesName() {
        for (ArtifactIdentifier identifier : ArtifactIdentifier.values()) {
            assertThat(ArtifactIdentifier.valueOf(identifier.name())).isSameAs(identifier);
        }
    }
}
