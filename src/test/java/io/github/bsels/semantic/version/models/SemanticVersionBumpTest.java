package io.github.bsels.semantic.version.models;

import io.github.bsels.semantic.version.test.utils.ArrayArgumentConverter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SemanticVersionBumpTest {

    @Nested
    class ArchitectureTest {

        @Test
        void numberOfEnumElements_Return3() {
            assertThat(SemanticVersionBump.values())
                    .hasSize(4)
                    .extracting(SemanticVersionBump::name)
                    .containsExactlyInAnyOrder("MAJOR", "MINOR", "PATCH", "NONE");
        }

        @ParameterizedTest
        @EnumSource(SemanticVersionBump.class)
        void toString_ReturnsCorrectValue(SemanticVersionBump semanticVersionBump) {
            assertThat(semanticVersionBump.toString())
                    .isEqualTo(semanticVersionBump.name());

        }

        @ParameterizedTest
        @EnumSource(SemanticVersionBump.class)
        void valueOf_ReturnCorrectValue(SemanticVersionBump semanticVersionBump) {
            assertThat(SemanticVersionBump.valueOf(semanticVersionBump.toString()))
                    .isEqualTo(semanticVersionBump);
        }
    }

    @Nested
    class FromStringTest {

        @Test
        void nullInput_ReturnsNone() {
            assertThat(SemanticVersionBump.fromString(null))
                    .isEqualTo(SemanticVersionBump.NONE);
        }

        @ParameterizedTest
        @CsvSource({
                "major,MAJOR",
                "Major,MAJOR",
                "MAJOR,MAJOR",
                "minor,MINOR",
                "Minor,MINOR",
                "MINOR,MINOR",
                "patch,PATCH",
                "Patch,PATCH",
                "PATCH,PATCH",
                "none,NONE",
                "None,NONE",
                "NONE,NONE"
        })
        void validInput_ReturnsCorrectValue(String input, SemanticVersionBump expected) {
            assertThat(SemanticVersionBump.fromString(input))
                    .isEqualTo(expected);
        }

        @Test
        void invalidInput_ThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> SemanticVersionBump.fromString("unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class MaxArrayInputTest {

        @Test
        void nullPointerArray_ThrowsNullPointerException() {
            SemanticVersionBump[] array = null;
            assertThatThrownBy(() -> SemanticVersionBump.max(array))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`bumps` must not be null");
        }

        @Test
        void emptyArray_ReturnsNone() {
            assertThat(SemanticVersionBump.max())
                    .isEqualTo(SemanticVersionBump.NONE);
        }

        @Test
        void singleNullElementArray_ReturnsNone() {
            SemanticVersionBump o = null;
            assertThat(SemanticVersionBump.max(o))
                    .isEqualTo(SemanticVersionBump.NONE);
        }

        @ParameterizedTest
        @CsvSource({
                "MAJOR,MAJOR",
                "MINOR,MINOR",
                "PATCH,PATCH",
                "NONE,NONE",
                "PATCH,NONE;PATCH",
                "PATCH,PATCH;NONE",
                "MINOR,NONE;MINOR",
                "MINOR,MINOR;NONE",
                "MINOR,PATCH;MINOR",
                "MINOR,MINOR;PATCH",
                "MAJOR,NONE;MAJOR",
                "MAJOR,MAJOR;NONE",
                "MAJOR,PATCH;MAJOR",
                "MAJOR,MAJOR;PATCH",
                "MAJOR,MINOR;MAJOR",
                "MAJOR,MAJOR;MINOR",
                "MINOR,NONE;PATCH;MINOR",
                "MAJOR,NONE;PATCH;MAJOR",
                "MAJOR,PATCH;MINOR;MAJOR",
                "MAJOR,NONE;MINOR;MAJOR",
                "MAJOR,NONE;PATCH;MINOR;MAJOR"
        })
        void nonEmptyArray_ReturnsCorrectValue(
                SemanticVersionBump expected,
                @ConvertWith(ArrayArgumentConverter.class)
                SemanticVersionBump... input
        ) {
            assertThat(SemanticVersionBump.max(input))
                    .isEqualTo(expected);
        }
    }

    @Nested
    class MaxCollectionInputTest {

        @Test
        void nullPointerCollection_ThrowsNullPointerException() {
            Collection<SemanticVersionBump> collection = null;
            assertThatThrownBy(() -> SemanticVersionBump.max(collection))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`bumps` must not be null");
        }

        @Test
        void nullPointerList_ThrowsNullPointerException() {
            List<SemanticVersionBump> collection = null;
            assertThatThrownBy(() -> SemanticVersionBump.max(collection))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`bumps` must not be null");
        }

        @Test
        void nullPointerSet_ThrowsNullPointerException() {
            Set<SemanticVersionBump> collection = null;
            assertThatThrownBy(() -> SemanticVersionBump.max(collection))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`bumps` must not be null");
        }

        @Test
        void emptyList_ReturnsNone() {
            assertThat(SemanticVersionBump.max(List.of()))
                    .isEqualTo(SemanticVersionBump.NONE);
        }

        @Test
        void emptySet_ReturnsNone() {
            assertThat(SemanticVersionBump.max(Set.of()))
                    .isEqualTo(SemanticVersionBump.NONE);
        }

        @Test
        void singleNullElementList_ReturnsNone() {
            assertThat(SemanticVersionBump.max(Collections.singletonList(null)))
                    .isEqualTo(SemanticVersionBump.NONE);
        }

        @Test
        void singleNullElementSet_ReturnsNone() {
            assertThat(SemanticVersionBump.max(Collections.singleton(null)))
                    .isEqualTo(SemanticVersionBump.NONE);
        }

        @ParameterizedTest
        @CsvSource({
                "MAJOR,MAJOR",
                "MINOR,MINOR",
                "PATCH,PATCH",
                "NONE,NONE",
                "PATCH,NONE;PATCH",
                "PATCH,PATCH;NONE",
                "MINOR,NONE;MINOR",
                "MINOR,MINOR;NONE",
                "MINOR,PATCH;MINOR",
                "MINOR,MINOR;PATCH",
                "MAJOR,NONE;MAJOR",
                "MAJOR,MAJOR;NONE",
                "MAJOR,PATCH;MAJOR",
                "MAJOR,MAJOR;PATCH",
                "MAJOR,MINOR;MAJOR",
                "MAJOR,MAJOR;MINOR",
                "MINOR,NONE;PATCH;MINOR",
                "MAJOR,NONE;PATCH;MAJOR",
                "MAJOR,PATCH;MINOR;MAJOR",
                "MAJOR,NONE;MINOR;MAJOR",
                "MAJOR,NONE;PATCH;MINOR;MAJOR"
        })
        void nonEmptyList_ReturnsCorrectValue(
                SemanticVersionBump expected,
                @ConvertWith(ArrayArgumentConverter.class)
                List<SemanticVersionBump> input
        ) {
            assertThat(SemanticVersionBump.max(input))
                    .isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "MAJOR,MAJOR",
                "MINOR,MINOR",
                "PATCH,PATCH",
                "NONE,NONE",
                "PATCH,NONE;PATCH",
                "PATCH,PATCH;NONE",
                "MINOR,NONE;MINOR",
                "MINOR,MINOR;NONE",
                "MINOR,PATCH;MINOR",
                "MINOR,MINOR;PATCH",
                "MAJOR,NONE;MAJOR",
                "MAJOR,MAJOR;NONE",
                "MAJOR,PATCH;MAJOR",
                "MAJOR,MAJOR;PATCH",
                "MAJOR,MINOR;MAJOR",
                "MAJOR,MAJOR;MINOR",
                "MINOR,NONE;PATCH;MINOR",
                "MAJOR,NONE;PATCH;MAJOR",
                "MAJOR,PATCH;MINOR;MAJOR",
                "MAJOR,NONE;MINOR;MAJOR",
                "MAJOR,NONE;PATCH;MINOR;MAJOR"
        })
        void nonEmptySet_ReturnsCorrectValue(
                SemanticVersionBump expected,
                @ConvertWith(ArrayArgumentConverter.class)
                Set<SemanticVersionBump> input
        ) {
            assertThat(SemanticVersionBump.max(input))
                    .isEqualTo(expected);
        }
    }
}
