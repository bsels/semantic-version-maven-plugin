package io.github.bsels.semantic.version.utils.yaml.front.block;

import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(MockitoExtension.class)
public class YamlFrontMatterExtensionTest {

    @Mock
    Parser.Builder parserBuilderMock;

    @Test
    public void constructionThroughConstructor_NoErrors() {
        assertThatNoException()
                .isThrownBy(YamlFrontMatterExtension::new);
    }

    @Test
    public void constructionThroughStaticMethod_NoErrors() {
        assertThatNoException()
                .isThrownBy(YamlFrontMatterExtension::create);
    }

    @Test
    public void extend_NoErrors() {
        assertThatNoException()
                .isThrownBy(() -> YamlFrontMatterExtension.create().extend(parserBuilderMock));

        Mockito.verify(parserBuilderMock, Mockito.times(1))
                .customBlockParserFactory(Mockito.any(YamlFrontMatterBlockParser.Factory.class));
    }
}
