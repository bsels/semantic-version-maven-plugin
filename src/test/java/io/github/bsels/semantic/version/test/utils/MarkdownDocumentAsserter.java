package io.github.bsels.semantic.version.test.utils;

import org.assertj.core.api.AbstractObjectAssert;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

public class MarkdownDocumentAsserter {
    private MarkdownDocumentAsserter() {
        // No instances allowed
    }

    public static UnaryOperator<AbstractObjectAssert<?, Node>> hasHeading(
            int level,
            String literal
    ) {
        return objectAssert -> objectAssert.isInstanceOf(Heading.class)
                .hasFieldOrPropertyWithValue("level", level)
                .satisfies(heading -> assertThat(heading.getFirstChild())
                        .isNotNull()
                        .isInstanceOf(Text.class)
                        .hasFieldOrPropertyWithValue("literal", literal)
                        .extracting(Node::getNext)
                        .isNull()
                )
                .extracting(Node::getNext);
    }

    public static UnaryOperator<AbstractObjectAssert<?, Node>> hasParagraph(
            String literal
    ) {
        return objectAssert -> objectAssert.isInstanceOf(Paragraph.class)
                .satisfies(paragraph -> assertThat(paragraph.getFirstChild())
                        .isNotNull()
                        .isInstanceOf(Text.class)
                        .hasFieldOrPropertyWithValue("literal", literal)
                        .extracting(Node::getNext)
                        .isNull()
                )
                .extracting(Node::getNext);
    }

    public static UnaryOperator<AbstractObjectAssert<?, Node>> hasThematicBreak() {
        return objectAssert -> objectAssert.isInstanceOf(ThematicBreak.class)
                .hasFieldOrPropertyWithValue("firstChild", null)
                .extracting(Node::getNext);
    }

    @SafeVarargs
    public static void assertThatDocument(
            Node document,
            UnaryOperator<AbstractObjectAssert<?, Node>>... asserts
    ) {
        AbstractObjectAssert<?, Node> asserter = assertThat(document)
                .isNotNull()
                .isInstanceOf(Document.class)
                .extracting(Node::getFirstChild);
        for (UnaryOperator<AbstractObjectAssert<?, Node>> assertFunction : asserts) {
            asserter = assertFunction.apply(asserter);
        }
        asserter.isNull();
    }
}
