package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class MarkdownYamFrontMatterBlockRendererFactoryTest {

    @Mock
    MarkdownWriter writerMock;

    @Mock
    MarkdownNodeRendererContext contextMock;

    @Test
    void getSpecialCharacters_ReturnEmptySet() {
        Set<Character> specialCharacters = MarkdownYamFrontMatterBlockRendererFactory.getInstance().getSpecialCharacters();
        assertThat(specialCharacters)
                .isSameAs(Set.of())
                .isEmpty();
    }

    @Test
    void createNullPointerContext_ThrowsNullPointerException() {
        MarkdownYamFrontMatterBlockRendererFactory instance = MarkdownYamFrontMatterBlockRendererFactory.getInstance();
        assertThatThrownBy(() -> instance.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`context` must not be null");
    }

    @Test
    void createValidContext_ReturnsInstance() {
        Mockito.when(contextMock.getWriter())
                .thenReturn(writerMock);

        MarkdownYamFrontMatterBlockRendererFactory instance = MarkdownYamFrontMatterBlockRendererFactory.getInstance();
        assertThat(instance.create(contextMock))
                .isNotNull()
                .isInstanceOf(MarkdownYamFrontMatterBlockRenderer.class);

        Mockito.verify(contextMock, Mockito.times(1))
                .getWriter();
    }
}
