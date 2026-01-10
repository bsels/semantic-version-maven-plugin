package io.github.bsels.semantic.version.models;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MarkdownMappingTest {

    @Test
    void versionBumpMapIsNull_ThrowsNullPointerException() {
        assertThatThrownBy(() -> new MarkdownMapping(null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`versionBumpMap` must not be null");
    }

    @Test
    void markdownMapIsNull_ThrowsNullPointerException() {
        assertThatThrownBy(() -> new MarkdownMapping(Map.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`markdownMap` must not be null");
    }

    @Test
    void immutableMap_SameValue() {
        Map<MavenArtifact, SemanticVersionBump> versionBumpMap = Map.of();
        Map<MavenArtifact, List<VersionMarkdown>> markdownMap = Map.of();

        MarkdownMapping mapping = new MarkdownMapping(versionBumpMap, markdownMap);
        assertThat(mapping.versionBumpMap())
                .isSameAs(versionBumpMap);
        assertThat(mapping.markdownMap())
                .isSameAs(markdownMap);
    }

    @Test
    void mutableMap_StoryAsImmutableCopy() {
        Map<MavenArtifact, SemanticVersionBump> versionBumpMap = new HashMap<>();
        Map<MavenArtifact, List<VersionMarkdown>> markdownMap = new HashMap<>();

        MarkdownMapping mapping = new MarkdownMapping(versionBumpMap, markdownMap);
        assertThat(mapping.versionBumpMap())
                .isNotSameAs(versionBumpMap);
        assertThat(mapping.markdownMap())
                .isNotSameAs(markdownMap);

        assertThat(Map.copyOf(mapping.versionBumpMap()))
                .isSameAs(mapping.versionBumpMap());
        assertThat(Map.copyOf(mapping.markdownMap()))
                .isSameAs(mapping.markdownMap());
    }
}
