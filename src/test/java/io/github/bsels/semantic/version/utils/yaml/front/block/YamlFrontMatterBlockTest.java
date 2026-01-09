package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class YamlFrontMatterBlockTest {

    @Test
    void nullPointerInConstructor_ThrowsNullPointerException() {
        assertThatThrownBy(() -> new YamlFrontMatterBlock(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`yaml` must not be null");
    }

    @Test
    void validConstructor_GetterReturnsValue() {
        String yaml = "test: data";
        YamlFrontMatterBlock block = new YamlFrontMatterBlock(yaml);
        assertThat(block.getYaml())
                .isEqualTo(yaml);
    }

    @Test
    void nullPointerInSetter_ThrowsNullPointerException() {
        YamlFrontMatterBlock block = new YamlFrontMatterBlock("");
        assertThatThrownBy(() -> block.setYaml(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`yaml` must not be null");
    }

    @Test
    void validSetter_GetterReturnsValue() {
        YamlFrontMatterBlock block = new YamlFrontMatterBlock("");
        block.setYaml("test: data");
        assertThat(block.getYaml())
                .isEqualTo("test: data");
    }
}
