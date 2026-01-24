package io.github.bsels.semantic.version.parameters;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationModeTest {

    @Test
    void valuesContainExpectedOrder() {
        assertThat(VerificationMode.values()).containsExactly(
                VerificationMode.NONE,
                VerificationMode.AT_LEAST_ONE_PROJECT,
                VerificationMode.DEPENDENT_PROJECTS,
                VerificationMode.ALL_PROJECTS
        );
    }

    @Test
    void valueOfRoundTripMatchesName() {
        for (VerificationMode mode : VerificationMode.values()) {
            assertThat(VerificationMode.valueOf(mode.name())).isSameAs(mode);
        }
    }
}
