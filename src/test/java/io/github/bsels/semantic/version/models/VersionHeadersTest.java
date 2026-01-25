package io.github.bsels.semantic.version.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VersionHeadersTest {
    @Test
    void defaultConstructor_UsesHeaderConstants() {
        VersionHeaders headers = new VersionHeaders();

        assertThat(headers.major()).isEqualTo(VersionHeaders.MAJOR_HEADER);
        assertThat(headers.minor()).isEqualTo(VersionHeaders.MINOR_HEADER);
        assertThat(headers.patch()).isEqualTo(VersionHeaders.PATCH_HEADER);
        assertThat(headers.other()).isEqualTo(VersionHeaders.OTHER_HEADER);
    }

    @Test
    void validConstruction_ReturnProvidedHeaders() {
        VersionHeaders headers = new VersionHeaders("Big", "Small", "Fix", "Meta");

        assertThat(headers.major()).isEqualTo("Big");
        assertThat(headers.minor()).isEqualTo("Small");
        assertThat(headers.patch()).isEqualTo("Fix");
        assertThat(headers.other()).isEqualTo("Meta");
    }

    @ParameterizedTest
    @MethodSource("nullHeaderInputs")
    void nullHeaderInputs_ThrowsNullPointerException(String major, String minor, String patch, String other, String message) {
        assertThatThrownBy(() -> new VersionHeaders(major, minor, patch, other))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(message);
    }

    @Test
    void nullVersionBump_ThrowsNullPointerException() {
        VersionHeaders headers = new VersionHeaders();

        assertThatThrownBy(() -> headers.getHeader(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`versionBump` must not be null");
    }

    @ParameterizedTest
    @MethodSource("headerMapping")
    void versionBump_ReturnsMatchingHeader(SemanticVersionBump bump, String expectedHeader) {
        VersionHeaders headers = new VersionHeaders("Major", "Minor", "Patch", "Other");

        assertThat(headers.getHeader(bump))
                .isEqualTo(expectedHeader);
    }

    private static Stream<Arguments> nullHeaderInputs() {
        return Stream.of(
                Arguments.of(null, "Minor", "Patch", "Other", "`major` header cannot be null"),
                Arguments.of("Major", null, "Patch", "Other", "`minor` header cannot be null"),
                Arguments.of("Major", "Minor", null, "Other", "`patch` header cannot be null"),
                Arguments.of("Major", "Minor", "Patch", null, "`other` header cannot be null")
        );
    }

    private static Stream<Arguments> headerMapping() {
        return Stream.of(
                Arguments.of(SemanticVersionBump.MAJOR, "Major"),
                Arguments.of(SemanticVersionBump.MINOR, "Minor"),
                Arguments.of(SemanticVersionBump.PATCH, "Patch"),
                Arguments.of(SemanticVersionBump.NONE, "Other")
        );
    }
}
