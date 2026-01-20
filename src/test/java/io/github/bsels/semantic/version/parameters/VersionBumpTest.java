package io.github.bsels.semantic.version.parameters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionBumpTest {

    @Test
    void numberOfEnumElements_Return3() {
        assertThat(VersionBump.values())
                .hasSize(4)
                .extracting(VersionBump::name)
                .containsExactlyInAnyOrder("FILE_BASED", "MAJOR", "MINOR", "PATCH");
    }

    @ParameterizedTest
    @EnumSource(VersionBump.class)
    void toString_ReturnsCorrectValue(VersionBump versionBump) {
        assertThat(versionBump.toString())
                .isEqualTo(versionBump.name());

    }

    @ParameterizedTest
    @EnumSource(VersionBump.class)
    void valueOf_ReturnCorrectValue(VersionBump versionBump) {
        assertThat(VersionBump.valueOf(versionBump.toString()))
                .isEqualTo(versionBump);
    }
}
