package io.github.bsels.semantic.version.parameters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ModusTest {

    @Test
    void numberOfEnumElements_Return3() {
        assertThat(Modus.values())
                .hasSize(3)
                .extracting(Modus::name)
                .containsExactlyInAnyOrder("PROJECT_VERSION", "REVISION_PROPERTY", "PROJECT_VERSION_ONLY_LEAFS");
    }

    @ParameterizedTest
    @EnumSource(Modus.class)
    void toString_ReturnsCorrectValue(Modus modus) {
        assertThat(modus.toString())
                .isEqualTo(modus.name());

    }

    @ParameterizedTest
    @EnumSource(Modus.class)
    void valueOf_ReturnCorrectValue(Modus modus) {
        assertThat(Modus.valueOf(modus.toString()))
                .isEqualTo(modus);
    }
}
