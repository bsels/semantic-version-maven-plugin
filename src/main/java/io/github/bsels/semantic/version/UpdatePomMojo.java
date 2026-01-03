package io.github.bsels.semantic.version;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

@Mojo(name = "update", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.NONE)
public class UpdatePomMojo extends AbstractMojo {
    /// Represents the default filename of the Maven Project Object Model (POM) file, typically used in Maven projects.
    /// The constant value "pom.xml" corresponds to the standard filename for the main POM configuration file,
    /// which defines the project's dependencies, build configuration, and other metadata.
    /// This variable is used within the plugin to resolve or reference the POM file in the Maven project directory.
    private static final String POM_XML = "pom.xml";

    /// Represents the base directory of the Maven project. This directory is resolved to the "basedir"
    /// property of the Maven build, typically corresponding to the root directory containing the
    /// `pom.xml` file.
    /// This variable is used as a reference point for resolving relative paths in the build process
    /// and is essential for various plugin operations.
    /// The value is immutable during execution and must be provided as it is a required parameter.
    /// Configuration:
    /// - `readonly`: Ensures the value remains constant throughout the execution.
    /// - `required`: Denotes that this parameter must be set.
    /// - `defaultValue`: Defaults to Maven's `${basedir}` property, which refers to the root project directory.
    @Parameter(readonly = true, required = true, defaultValue = "${basedir}")
    protected Path baseDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        Path pom = baseDirectory.resolve(POM_XML);
        DocumentBuilder documentBuilder = createDocumentBuilder();

        Document document;
        try (InputStream reader = Files.newInputStream(pom)) {
            document = documentBuilder.parse(reader);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to read %s file".formatted(POM_XML), e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }

        NodeList childNodes = document.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if ("project".equals(node.getNodeName())) {
                NodeList properties = node.getChildNodes();
                for (int j = 0; j < properties.getLength(); j++) {
                    Node n = properties.item(j);
                    if ("version".equals(n.getNodeName())) {
                        String version = n.getTextContent();
                        String newVersion = "1.0.0";
                        log.info("Updating version from %s to %s".formatted(version, newVersion));
                        n.setTextContent(newVersion);
                    }
                }
            }
        }

        Source source = new DOMSource(document);
        try (StringWriter writer = new StringWriter()) {
            StreamResult result = new StreamResult(writer);
            Transformer identity = TransformerFactory.newInstance()
                    .newTransformer();
            identity.transform(source, result);

            log.info(writer.toString());
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write %s file".formatted(POM_XML), e);
        } catch (TransformerConfigurationException e) {
            throw new MojoFailureException("Unable to configure XML transformer", e);
        } catch (TransformerException e) {
            throw new MojoFailureException("Unable to transform XML document", e);
        }
    }

    private DocumentBuilder createDocumentBuilder() throws MojoFailureException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setIgnoringElementContentWhitespace(false);
        documentBuilderFactory.setIgnoringComments(false);
        try {
            return documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new MojoFailureException("Unable to construct XML document builder", e);
        }
    }
}
