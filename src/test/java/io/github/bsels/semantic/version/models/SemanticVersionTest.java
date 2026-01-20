package io.github.bsels.semantic.version.models;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SemanticVersionTest {

    @Nested
    class ConstructorTest {

        @ParameterizedTest
        @CsvSource({
                "-1,-1,-1",
                "-1,-1,0",
                "-1,0,-1",
                "0,-1,-1",
                "0,0,-1",
                "0,-1,0",
                "-1,0,0"
        })
        void invalidVersionNumbers_ThrowsIllegalArgumentException(int major, int minor, int patch) {
            assertThatThrownBy(() -> new SemanticVersion(major, minor, patch, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Version parts must be non-negative");
        }

        @Test
        void validVersionNumbersNullSuffix_ValidObject() {
            SemanticVersion semanticVersion = new SemanticVersion(1, 2, 3, null);
            assertThat(semanticVersion)
                    .hasFieldOrPropertyWithValue("major", 1)
                    .hasFieldOrPropertyWithValue("minor", 2)
                    .hasFieldOrPropertyWithValue("patch", 3)
                    .hasFieldOrPropertyWithValue("suffix", Optional.empty());
        }

        @ParameterizedTest
        @NullAndEmptySource
        void validVersionNumberNoSuffix_ValidObject(String suffix) {
            SemanticVersion semanticVersion = new SemanticVersion(1, 2, 3, Optional.ofNullable(suffix));
            assertThat(semanticVersion)
                    .hasFieldOrPropertyWithValue("major", 1)
                    .hasFieldOrPropertyWithValue("minor", 2)
                    .hasFieldOrPropertyWithValue("patch", 3)
                    .hasFieldOrPropertyWithValue("suffix", Optional.empty());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-alpha?", "alpha-", "alpha.1", "alpha-1"})
        void invalidSuffix_ThrowsIllegalArgumentException(String suffix) {
            assertThatThrownBy(() -> new SemanticVersion(1, 2, 3, Optional.of(suffix)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Suffix must be alphanumeric, dash, or dot, and should not start with a dash");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-alpha", "-ALPHA", "-Alpha.1", "-SNAPSHOT"})
        void validSuffix_ValidObject(String suffix) {
            SemanticVersion semanticVersion = new SemanticVersion(1, 2, 3, Optional.of(suffix));
            assertThat(semanticVersion)
                    .hasFieldOrPropertyWithValue("major", 1)
                    .hasFieldOrPropertyWithValue("minor", 2)
                    .hasFieldOrPropertyWithValue("patch", 3)
                    .hasFieldOrPropertyWithValue("suffix", Optional.of(suffix));
        }
    }

    @Nested
    class BumpTest {

        @Test
        void nullBump_ThrowsNullPointerException() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.empty());
            assertThatThrownBy(() -> version.bump(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`bump` must not be null");
        }

        @ParameterizedTest
        @CsvSource({
                "1.2.3,MAJOR,2.0.0",
                "1.2.3,MINOR,1.3.0",
                "1.2.3,PATCH,1.2.4",
                "1.2.3,NONE,1.2.3",
                "1.2.3-alpha,MAJOR,2.0.0-alpha",
                "1.2.3-alpha,MINOR,1.3.0-alpha",
                "1.2.3-alpha,PATCH,1.2.4-alpha",
                "1.2.3-alpha,NONE,1.2.3-alpha"
        })
        void validBump_ReturnsNewObject(String oldVersion, SemanticVersionBump bump, String expectedNewVersion) {
            SemanticVersion semanticVersion = SemanticVersion.of(oldVersion);
            SemanticVersion expected = SemanticVersion.of(expectedNewVersion);
            if (SemanticVersionBump.NONE.equals(bump)) {
                assertThat(semanticVersion.bump(bump))
                        .isSameAs(semanticVersion)
                        .isEqualTo(expected);
            } else {
                assertThat(semanticVersion.bump(bump))
                        .isNotSameAs(semanticVersion)
                        .isEqualTo(expected);
            }
        }
    }

    @Nested
    class OfTest {

        @Test
        void nullInput_ThrowsNullPointerException() {
            assertThatThrownBy(() -> SemanticVersion.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`version` must not be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "1.2.3",
                "0.0.0",
                "10.20.30",
                "999.999.999",
                "  1.2.3  "
        })
        void validVersionWithoutSuffix_ReturnsValidObject(String version) {
            SemanticVersion semanticVersion = SemanticVersion.of(version);
            assertThat(semanticVersion).isNotNull();
            assertThat(semanticVersion.suffix()).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
                "1.2.3-SNAPSHOT,1,2,3",
                "0.0.0-alpha,0,0,0",
                "10.20.30-beta.1,10,20,30",
                "1.0.0-rc.1,1,0,0",
                "2.3.4-SNAPSHOT,2,3,4",
                "  5.6.7-dev  ,5,6,7"
        })
        void validVersionWithSuffix_ReturnsValidObject(String input, int major, int minor, int patch) {
            SemanticVersion semanticVersion = SemanticVersion.of(input);
            assertThat(semanticVersion)
                    .hasFieldOrPropertyWithValue("major", major)
                    .hasFieldOrPropertyWithValue("minor", minor)
                    .hasFieldOrPropertyWithValue("patch", patch);
            assertThat(semanticVersion.suffix()).isPresent();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "   ",
                "1",
                "1.2",
                "1.2.3.4",
                "a.b.c",
                "1.2.a",
                "1.a.3",
                "a.2.3",
                "1.2.3-",
                "1.2.3-suffix with spaces",
                "v1.2.3",
                "1.2.3-suffix!",
                "-1.2.3",
                "1.-2.3",
                "1.2.-3",
                "1..3",
                ".1.2.3",
                "1.2.3."
        })
        void invalidVersionFormat_ThrowsIllegalArgumentException(String version) {
            assertThatThrownBy(() -> SemanticVersion.of(version))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid semantic version format");
        }
    }

    @Nested
    class StripSuffixTest {

        @Test
        void nullSuffix_ReturnsSameObject() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.empty());
            assertThat(version.stripSuffix())
                    .isSameAs(version);
        }

        @Test
        void nonNullSuffix_ReturnsNewObject() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.of("-alpha"));
            assertThat(version.stripSuffix())
                    .isNotSameAs(version)
                    .hasFieldOrPropertyWithValue("suffix", Optional.empty())
                    .hasFieldOrPropertyWithValue("major", 1)
                    .hasFieldOrPropertyWithValue("minor", 2)
                    .hasFieldOrPropertyWithValue("patch", 3);
        }
    }

    @Nested
    class ToStringTest {

        @Test
        void withSuffix_ReturnsCorrectFormat() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.of("-alpha"));
            assertThat(version.toString())
                    .isEqualTo("1.2.3-alpha");
        }

        @Test
        void withoutSuffix_ReturnsCorrectFormat() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.empty());
            assertThat(version.toString())
                    .isEqualTo("1.2.3");
        }
    }

    @Nested
    class WithSuffixTest {

        @Test
        void nullSuffixInput_ThrowsNullPointerException() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.empty());
            assertThatThrownBy(() -> version.withSuffix(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`suffix` must not be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-alpha?", "alpha-", "alpha.1", "alpha-1"})
        void invalidSuffix_ThrowsIllegalArgumentException(String suffix) {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.empty());
            assertThatThrownBy(() -> version.withSuffix(suffix))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Suffix must be alphanumeric, dash, or dot, and should not start with a dash");
        }

        @Test
        void withoutSuffix_SuffixAdded() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.empty());
            assertThat(version.withSuffix("-alpha"))
                    .isNotSameAs(version)
                    .hasFieldOrPropertyWithValue("suffix", Optional.of("-alpha"))
                    .hasFieldOrPropertyWithValue("major", 1)
                    .hasFieldOrPropertyWithValue("minor", 2)
                    .hasFieldOrPropertyWithValue("patch", 3);
        }

        @Test
        void withOtherSuffix_SuffixReplaced() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.of("-alpha"));
            assertThat(version.withSuffix("-beta"))
                    .isNotSameAs(version)
                    .hasFieldOrPropertyWithValue("suffix", Optional.of("-beta"))
                    .hasFieldOrPropertyWithValue("major", 1)
                    .hasFieldOrPropertyWithValue("minor", 2)
                    .hasFieldOrPropertyWithValue("patch", 3);
        }

        @Test
        void withSameSuffix_ReturnsSameObject() {
            SemanticVersion version = new SemanticVersion(1, 2, 3, Optional.of("-alpha"));
            assertThat(version.withSuffix("-alpha"))
                    .isSameAs(version);
        }
    }
}
