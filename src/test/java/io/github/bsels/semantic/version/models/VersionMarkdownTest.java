package io.github.bsels.semantic.version.models;

import org.commonmark.node.Document;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VersionMarkdownTest {
    public static final Document CONTENT = new Document();
    private static final MavenArtifact MAVEN_ARTIFACT = new MavenArtifact("groupId", "artifactId");

    @Test
    void nullNode_ThrowsNullPointerException() {
        assertThatThrownBy(() -> new VersionMarkdown(null, null, Map.of(MAVEN_ARTIFACT, SemanticVersionBump.NONE)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`content` must not be null");
    }

    @Test
    void nullBumps_ThrowsNullPointerException() {
        assertThatThrownBy(() -> new VersionMarkdown(null, CONTENT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`bumps` must not be null");
    }

    @Test
    void emptyBumps_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new VersionMarkdown(null, CONTENT, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("`bumps` must not be empty");
    }

    @Test
    void mutableMapInput_MakeImmutable() {
        Map<MavenArtifact, SemanticVersionBump> bumps = new HashMap<>();
        bumps.put(MAVEN_ARTIFACT, SemanticVersionBump.NONE);

        VersionMarkdown markdown = new VersionMarkdown(null, CONTENT, bumps);
        assertThat(markdown)
                .hasFieldOrPropertyWithValue("content", CONTENT)
                .hasFieldOrPropertyWithValue("bumps", bumps)
                .satisfies(
                        m -> assertThat(m.bumps())
                                .isNotSameAs(bumps)
                                .isSameAs(Map.copyOf(m.bumps()))
                );
    }

    @Test
    void immutableMapInput_KeepsImmutable() {
        Map<MavenArtifact, SemanticVersionBump> bumps = Map.of(MAVEN_ARTIFACT, SemanticVersionBump.NONE);
        VersionMarkdown markdown = new VersionMarkdown(null, CONTENT, bumps);
        assertThat(markdown)
                .hasFieldOrPropertyWithValue("content", CONTENT)
                .hasFieldOrPropertyWithValue("bumps", bumps)
                .satisfies(
                        m -> assertThat(m.bumps())
                                .isSameAs(bumps)
                                .isSameAs(Map.copyOf(m.bumps()))
                );
    }
}
