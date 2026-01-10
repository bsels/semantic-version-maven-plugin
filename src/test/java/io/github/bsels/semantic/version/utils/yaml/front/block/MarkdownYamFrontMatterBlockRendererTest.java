package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.markdown.MarkdownWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class MarkdownYamFrontMatterBlockRendererTest {
    private static final Renderer MARKDOWN_RENDERER = MarkdownRenderer.builder()
            .nodeRendererFactory(MarkdownYamFrontMatterBlockRendererFactory.getInstance())
            .build();

    @Mock
    MarkdownWriter writerMock;

    @Mock
    MarkdownNodeRendererContext contextMock;

    @BeforeEach
    void setUp() {
        Mockito.lenient()
                .when(contextMock.getWriter())
                .thenReturn(writerMock);
    }

    @Nested
    class InstanceMethodsTest {

        @Test
        void constructorNullParameter_ThrowsNullPointerException() {
            assertThatThrownBy(() -> new MarkdownYamFrontMatterBlockRenderer(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`context` must not be null");

            Mockito.verifyNoInteractions(contextMock, writerMock);
        }

        @Test
        void getNodeTypes_ReturnYamlFrontMatterBlock() {
            MarkdownYamFrontMatterBlockRenderer classUnderTest = new MarkdownYamFrontMatterBlockRenderer(contextMock);
            Set<Class<? extends Node>> nodeTypes = classUnderTest.getNodeTypes();
            assertThat(nodeTypes)
                    .isNotNull()
                    .hasSize(1)
                    .containsExactly(YamlFrontMatterBlock.class)
                    .isSameAs(Set.copyOf(nodeTypes));

            Mockito.verify(contextMock, Mockito.times(1))
                    .getWriter();
            Mockito.verifyNoMoreInteractions(contextMock);
            Mockito.verifyNoInteractions(writerMock);
        }

        @Test
        void nullInputRender_DoNothing() {
            MarkdownYamFrontMatterBlockRenderer classUnderTest = new MarkdownYamFrontMatterBlockRenderer(contextMock);
            classUnderTest.render(null);


            Mockito.verify(contextMock, Mockito.times(1))
                    .getWriter();
            Mockito.verifyNoMoreInteractions(contextMock);
            Mockito.verifyNoInteractions(writerMock);
        }

        @ParameterizedTest
        @ValueSource(classes = {Document.class, Text.class, Paragraph.class, Heading.class})
        void unsupportedNodeTypes_DoNothing(Class<? extends Node> clazz)
                throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
            Node node = clazz.getConstructor().newInstance();

            MarkdownYamFrontMatterBlockRenderer classUnderTest = new MarkdownYamFrontMatterBlockRenderer(contextMock);
            classUnderTest.render(node);

            Mockito.verify(contextMock, Mockito.times(1))
                    .getWriter();
            Mockito.verifyNoMoreInteractions(contextMock);
            Mockito.verifyNoInteractions(writerMock);
        }

        @Test
        void renderFrontMatter_CorrectlyCalledMocks() {
            YamlFrontMatterBlock block = new YamlFrontMatterBlock("test: data");

            MarkdownYamFrontMatterBlockRenderer classUnderTest = new MarkdownYamFrontMatterBlockRenderer(contextMock);
            classUnderTest.render(block);

            Mockito.verify(contextMock, Mockito.times(1))
                    .getWriter();
            Mockito.verify(writerMock, Mockito.times(2))
                    .raw("---");
            Mockito.verify(writerMock, Mockito.times(1))
                    .raw("test: data");
            Mockito.verify(writerMock, Mockito.times(4))
                    .line();

            Mockito.verifyNoMoreInteractions(contextMock, writerMock);
        }
    }

    @Nested
    class IntegrationTest {

        @Test
        void withoutFrontMatter_ValidMarkdown() {
            Document document = new Document();
            Paragraph paragraph = new Paragraph();
            paragraph.appendChild(new Text("Test"));
            document.appendChild(paragraph);

            String markdown = MARKDOWN_RENDERER.render(document);
            assertThat(markdown)
                    .isEqualTo("Test\n");
        }

        @Test
        void withFrontMatter_ValidMarkdown() {
            Document document = new Document();
            YamlFrontMatterBlock block = new YamlFrontMatterBlock("test: data");
            document.appendChild(block);
            Paragraph paragraph = new Paragraph();
            paragraph.appendChild(new Text("Test"));
            document.appendChild(paragraph);

            String markdown = MARKDOWN_RENDERER.render(document);
            assertThat(markdown)
                    .isEqualTo("""
                            ---
                            test: data
                            ---
                            
                            Test
                            """);
        }
    }
}
