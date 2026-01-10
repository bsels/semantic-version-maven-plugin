package io.github.bsels.semantic.version.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VersionChangeTest {

    @ParameterizedTest
    @CsvSource(value = {
            "null,null,oldVersion",
            "null,1.0.0,oldVersion",
            "1.0.0,null,newVersion"
    }, nullValues = "null")
    void nullInput_ThrowsNullPointerException(String oldVersion, String newVersion, String exceptionParameter) {
        assertThatThrownBy(() -> new VersionChange(oldVersion, newVersion))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`%s` must not be null", exceptionParameter);
    }

    @Test
    void validConstruction_ReturnProvidedInputs() {
        VersionChange versionChange = new VersionChange("1.0.0", "2.0.0");
        assertThat(versionChange)
                .hasFieldOrPropertyWithValue("oldVersion", "1.0.0")
                .hasFieldOrPropertyWithValue("newVersion", "2.0.0");
    }
}
