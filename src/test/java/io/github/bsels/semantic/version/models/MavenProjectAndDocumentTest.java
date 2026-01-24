package io.github.bsels.semantic.version.models;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenProjectAndDocumentTest {

    @Test
    void constructsRecordWithComponents() throws Exception {
        MavenArtifact artifact = new MavenArtifact("group", "artifact");
        Path pomFile = Path.of("pom.xml");
        Document document = newDocument();

        MavenProjectAndDocument result = new MavenProjectAndDocument(artifact, pomFile, document);

        assertThat(result.artifact()).isSameAs(artifact);
        assertThat(result.pomFile()).isSameAs(pomFile);
        assertThat(result.document()).isSameAs(document);
    }

    @Test
    void requiresNonNullArtifact() throws Exception {
        Path pomFile = Path.of("pom.xml");
        Document document = newDocument();

        assertThatThrownBy(() -> new MavenProjectAndDocument(null, pomFile, document))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`artifact` must not be null");
    }

    @Test
    void requiresNonNullPomFile() throws Exception {
        MavenArtifact artifact = new MavenArtifact("group", "artifact");
        Document document = newDocument();

        assertThatThrownBy(() -> new MavenProjectAndDocument(artifact, null, document))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`pomFile` must not be null");
    }

    @Test
    void requiresNonNullDocument() {
        MavenArtifact artifact = new MavenArtifact("group", "artifact");
        Path pomFile = Path.of("pom.xml");

        assertThatThrownBy(() -> new MavenProjectAndDocument(artifact, pomFile, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("`document` must not be null");
    }

    private Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();
    }
}
