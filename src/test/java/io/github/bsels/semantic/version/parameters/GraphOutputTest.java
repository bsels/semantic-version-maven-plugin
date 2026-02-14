package io.github.bsels.semantic.version.parameters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphOutputTest {

    @Test
    void numberOfEnumElements_Return3() {
        assertThat(GraphOutput.values())
                .hasSize(3)
                .extracting(GraphOutput::name)
                .containsExactlyInAnyOrder("ARTIFACT_ONLY", "FOLDER_ONLY", "ARTIFACT_AND_FOLDER");
    }

    @ParameterizedTest
    @EnumSource(GraphOutput.class)
    void toString_ReturnsCorrectValue(GraphOutput graphOutput) {
        assertThat(graphOutput.toString())
                .isEqualTo(graphOutput.name());

    }

    @ParameterizedTest
    @EnumSource(GraphOutput.class)
    void valueOf_ReturnCorrectValue(GraphOutput graphOutput) {
        assertThat(GraphOutput.valueOf(graphOutput.toString()))
                .isEqualTo(graphOutput);
    }
}
