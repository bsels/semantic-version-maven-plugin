package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.node.BulletList;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.bsels.semantic.version.test.utils.MarkdownDocumentAsserter.assertThatDocument;
import static io.github.bsels.semantic.version.test.utils.MarkdownDocumentAsserter.hasHeading;
import static io.github.bsels.semantic.version.test.utils.MarkdownDocumentAsserter.hasParagraph;
import static io.github.bsels.semantic.version.test.utils.MarkdownDocumentAsserter.hasThematicBreak;
import static org.assertj.core.api.Assertions.assertThat;

public class YamlFrontMatterBlockParserTest {
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(YamlFrontMatterExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    @Nested
    class NoFrontMatterBlockTest {

        @Test
        void noHeaderBlock_ReturnValidMarkdownTree() {
            String markdown = """
                    # No front matter
                    
                    This is a test
                    """;

            Node actual = PARSER.parse(markdown);

            assertThatDocument(
                    actual,
                    hasHeading(1, "No front matter"),
                    hasParagraph("This is a test")
            );
        }

        @Test
        void noFrontMatterBlockButHasHorizontalLine_ReturnValidMarkdownTree() {
            String markdown = """
                    Paragraph 1
                    
                    ---
                    
                    Paragraph 2
                    """;

            Node actual = PARSER.parse(markdown);

            assertThatDocument(
                    actual,
                    hasParagraph("Paragraph 1"),
                    hasThematicBreak(),
                    hasParagraph("Paragraph 2")
            );
        }

        @Test
        void noFrontMatterWithList_ReturnValidMarkdownTree() {
            String markdown = """
                    - Item 1
                    - Item 2
                    - Item 3
                    """;

            Node actual = PARSER.parse(markdown);

            assertThat(actual)
                    .isInstanceOf(Document.class)
                    .extracting(Node::getFirstChild)
                    .isNotNull()
                    .isInstanceOf(BulletList.class)
                    .satisfies(
                            list -> assertThat(list.getFirstChild())
                                    .isNotNull()
                                    .isInstanceOf(ListItem.class)
                                    .satisfies(
                                            listItem -> assertThat(listItem.getFirstChild())
                                                    .isInstanceOf(Paragraph.class)
                                                    .satisfies(
                                                            n -> assertThat(n.getFirstChild())
                                                                    .isNotNull()
                                                                    .isInstanceOf(Text.class)
                                                                    .hasFieldOrPropertyWithValue("literal", "Item 1")
                                                                    .extracting(Node::getNext)
                                                                    .isNull()
                                                    )
                                                    .extracting(Node::getNext)
                                                    .isNull()
                                    )
                                    .extracting(Node::getNext)
                                    .isInstanceOf(ListItem.class)
                                    .satisfies(
                                            listItem -> assertThat(listItem.getFirstChild())
                                                    .isInstanceOf(Paragraph.class)
                                                    .satisfies(
                                                            n -> assertThat(n.getFirstChild())
                                                                    .isNotNull()
                                                                    .isInstanceOf(Text.class)
                                                                    .hasFieldOrPropertyWithValue("literal", "Item 2")
                                                                    .extracting(Node::getNext)
                                                                    .isNull()
                                                    )
                                                    .extracting(Node::getNext)
                                                    .isNull()
                                    )
                                    .extracting(Node::getNext)
                                    .isInstanceOf(ListItem.class)
                                    .satisfies(
                                            listItem -> assertThat(listItem.getFirstChild())
                                                    .isInstanceOf(Paragraph.class)
                                                    .satisfies(
                                                            n -> assertThat(n.getFirstChild())
                                                                    .isNotNull()
                                                                    .isInstanceOf(Text.class)
                                                                    .hasFieldOrPropertyWithValue("literal", "Item 3")
                                                                    .extracting(Node::getNext)
                                                                    .isNull()
                                                    )
                                                    .extracting(Node::getNext)
                                                    .isNull()
                                    )
                                    .extracting(Node::getNext)
                                    .isNull()
                    )
                    .extracting(Node::getNext)
                    .isNull();
        }
    }

    @Nested
    class WithFrontMatterBlockTest {

        @Test
        void withYamlFrontMatterBlock_ReturnCorrectMarkdownAndYamlBlock() {

            String markdown = """
                    ---
                    test:
                        data: "Test data"
                        index: 0
                    ---
                    
                    # Front matter
                    
                    This is a test
                    """;

            Node actual = PARSER.parse(markdown);

            assertThat(actual)
                    .isInstanceOf(Document.class)
                    .extracting(Node::getFirstChild)
                    .isInstanceOf(YamlFrontMatterBlock.class)
                    .hasFieldOrPropertyWithValue(
                            "yaml",
                            """
                                    test:
                                        data: "Test data"
                                        index: 0\
                                    """
                    )
                    .hasFieldOrPropertyWithValue("firstChild", null)
                    .extracting(Node::getNext)
                    .isNotNull()
                    .isInstanceOf(Heading.class)
                    .hasFieldOrPropertyWithValue("level", 1)
                    .satisfies(
                            n -> assertThat(n.getFirstChild())
                                    .isNotNull()
                                    .isInstanceOf(Text.class)
                                    .hasFieldOrPropertyWithValue("literal", "Front matter")
                                    .extracting(Node::getNext)
                                    .isNull()
                    )
                    .extracting(Node::getNext)
                    .isNotNull()
                    .isInstanceOf(Paragraph.class)
                    .satisfies(
                            n -> assertThat(n.getFirstChild())
                                    .isNotNull()
                                    .isInstanceOf(Text.class)
                                    .hasFieldOrPropertyWithValue("literal", "This is a test")
                                    .extracting(Node::getNext)
                                    .isNull()
                    )
                    .extracting(Node::getNext)
                    .isNull();
        }
    }
}
