package io.github.bsels.semantic.version.utils;

import io.github.bsels.semantic.version.models.SemanticVersion;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.parameters.Modus;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/// Utility class for handling various operations related to Project Object Model (POM) files, such as reading, writing,
/// version updates, and backups.
/// Provides methods for XML parsing and manipulation with the goal of managing POM files effectively
/// in a Maven project context.
///
/// This class is not intended to be instantiated, and all methods are designed to be used in a static context.
public final class POMUtils {

    /// Defines the path to locate the project version element within a POM (Project Object Model) file.
    /// The path is expressed as a list of strings, where each string represents a hierarchical element
    /// from the root of the XML document to the target "version" node.
    ///
    /// This path is primarily used by methods that traverse or manipulate the XML document structure
    /// to locate and update the version information in a Maven project.
    private static final List<String> VERSION_PROPERTY_PATH = List.of("project", "version");
    /// A constant list of strings representing the XML traversal path to locate the "revision" property
    /// within a Maven POM file.
    /// This path defines the sequential hierarchy of nodes that need to be traversed in the XML document,
    /// starting with the "project" node, followed by the "properties" node, and finally the "revision" node.
    ///
    /// This is primarily used in scenarios where the "revision" property value needs to be accessed or
    /// modified programmatically within the POM file.
    /// It serves as a predefined navigation path, ensuring a consistent and
    /// error-free location of the "revision" property across operations.
    private static final List<String> REVISION_PROPERTY_PATH = List.of("project", "properties", "revision");

    /// A static and lazily initialized instance of [DocumentBuilder] used for XML parsing operations.
    /// This field serves as a shared resource across methods in the class, preventing the need to
    /// repeatedly create new [DocumentBuilder] instances.
    /// The instance is configured with specific settings, such as namespace awareness,
    /// ignoring of whitespace, and inclusion of comments in parsed documents.
    ///
    /// This variable is intended to facilitate efficient and consistent XML document parsing in the context
    /// of handling Project Object Model (POM) files.
    ///
    /// The initialization and configuration of this [DocumentBuilder] instance are managed by
    /// the [#getOrCreateDocumentBuilder] method.
    /// Access to this field should be done only through that method to ensure proper initialization and error handling.
    private static DocumentBuilder documentBuilder = null;
    /// A static instance of the [Transformer] class used for XML transformation tasks within the utility.
    /// The `transformer` is lazily initialized when required to perform operations such as
    /// writing and formatting XML documents.
    /// It is configured to work with XML-related tasks in the context of processing POM (Project Object Model) files.
    ///
    /// This variable is managed internally to ensure a single instance is reused, avoiding
    /// repetitive creation and enhancing performance during XML transformations.
    /// If creation of the [Transformer] instance fails,
    /// it throws an exception managed by the utility's methods leveraging this variable.
    ///
    /// The `transformer` is shared across various operations in this utility class,
    /// ensuring consistency in XML transformation behavior.
    ///
    /// The initialization and configuration of this [Transformer] instance are managed by
    /// the [#getOrCreateTransformer] method.
    /// Access to this field should be done only through that method to ensure proper initialization and error handling.
    private static Transformer transformer = null;

    /// Utility class for handling various operations related to Project Object Model (POM) files,
    /// such as reading, writing, version updates, and backups.
    /// Provides methods for XML parsing and manipulation with the goal of managing POM files effectively
    /// in a Maven project context.
    ///
    /// This class is not intended to be instantiated, and all methods are designed to be used in a static context.
    private POMUtils() {
        // No instance needed
    }

    /// Retrieves the project version node from the provided XML document, based on the specified mode.
    /// The mode determines the traversal path used to locate the version node within the document.
    ///
    /// @param document the XML document from which to retrieve the project version node; must not be null
    /// @param modus    the mode that specifies the traversal logic for locating the version node; must not be null
    /// @return the XML node representing the project version
    /// @throws NullPointerException  if the document or modus argument is null
    /// @throws IllegalStateException if the project version node cannot be located in the document
    public static Node getProjectVersionNode(Document document, Modus modus)
            throws NullPointerException, IllegalStateException {
        Objects.requireNonNull(document, "`document` must not be null");
        Objects.requireNonNull(modus, "`modus` must not be null");
        List<String> versionPropertyPath = switch (modus) {
            case REVISION_PROPERTY -> REVISION_PROPERTY_PATH;
            case PROJECT_VERSION, PROJECT_VERSION_ONLY_LEAFS -> VERSION_PROPERTY_PATH;
        };
        try {
            return walk(document, versionPropertyPath, 0);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Unable to find project version on the path: %s".formatted(
                    String.join("->", versionPropertyPath)
            ), e);
        }
    }

    /// Parses the specified POM (Project Object Model) file and returns it as an XML Document object.
    /// This method attempts to read and parse the provided file, constructing a Document representation
    /// of the XML content.
    ///
    /// @param pomFile the path to the POM file to be read; must not be null
    /// @return the parsed XML Document representing the contents of the POM file
    /// @throws NullPointerException   if the provided pomFile is null
    /// @throws MojoExecutionException if an error occurs while reading or parsing the POM file
    /// @throws MojoFailureException   if the DocumentBuilder cannot be initialized
    public static Document readPom(Path pomFile)
            throws NullPointerException, MojoExecutionException, MojoFailureException {
        Objects.requireNonNull(pomFile, "`pomFile` must not be null");
        DocumentBuilder documentBuilder = getOrCreateDocumentBuilder();
        try (InputStream inputStream = Files.newInputStream(pomFile)) {
            return documentBuilder.parse(inputStream);
        } catch (IOException | SAXException e) {
            throw new MojoExecutionException("Unable to read '%s' file".formatted(pomFile), e);
        }
    }

    /// Writes the given XML Document to the specified POM file.
    /// Optionally, a backup of the old POM file can be created before writing the new document.
    /// The method ensures the POM file is written using UTF-8 encoding and handles any necessary transformations
    /// or I/O operations.
    ///
    /// @param document  the XML Document to be written; must not be null
    /// @param pomFile   the path to the POM file where the document will be written; must not be null
    /// @param backupOld a boolean indicating whether to create a backup of the old POM file before writing
    /// @throws NullPointerException   if the document or pomFile argument is null
    /// @throws MojoExecutionException if an error occurs during the writing or backup operation
    /// @throws MojoFailureException   if the required XML Transformer cannot be created
    public static void writePom(Document document, Path pomFile, boolean backupOld)
            throws NullPointerException, MojoExecutionException, MojoFailureException {
        Objects.requireNonNull(document, "`document` must not be null");
        Objects.requireNonNull(pomFile, "`pomFile` must not be null");
        if (backupOld) {
            Utils.backupFile(pomFile);
        }
        try (Writer writer = Files.newBufferedWriter(pomFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
            writePom(document, writer);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write %s".formatted(pomFile), e);
        }
    }

    /// Writes the given XML Document to the specified writer.
    /// The method transforms the provided XML document into a stream format and writes it using the given writer.
    /// If an error occurs during the transformation or writing process, it throws an appropriate exception.
    ///
    /// @param document the XML Document to be written; must not be null
    /// @param writer   the writer to which the XML Document will be written; must not be null
    /// @throws NullPointerException   if the document or writer argument is null
    /// @throws MojoExecutionException if an error occurs during the transformation process
    /// @throws MojoFailureException   if the XML Transformer cannot be created or fails to execute
    public static void writePom(Document document, Writer writer)
            throws NullPointerException, MojoExecutionException, MojoFailureException {
        Objects.requireNonNull(document, "`document` must not be null");
        Objects.requireNonNull(writer, "`writer` must not be null");
        try {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            Source source = new DOMSource(document);
            Result result = new StreamResult(writer);
            getOrCreateTransformer()
                    .transform(source, result);
        } catch (IOException | TransformerException e) {
            throw new MojoExecutionException("Unable to write XML document", e);
        }
    }

    /**
     * Updates the version value of the given XML node based on the specified semantic version bump type.
     * The method retrieves the current semantic version from the node, increments the version according
     * to the provided bump type, and updates the node with the new version value.
     *
     * @param nodeElement the XML node whose version value is to be updated; must not be null
     * @param bump        the type of semantic version increment to be applied; must not be null
     * @throws NullPointerException     if either nodeElement or bump is null
     * @throws IllegalArgumentException if the content of nodeElement cannot be parsed into a valid semantic version
     */
    public static void updateVersion(Node nodeElement, SemanticVersionBump bump)
            throws NullPointerException, IllegalArgumentException {
        Objects.requireNonNull(nodeElement, "`nodeElement` must not be null");
        Objects.requireNonNull(bump, "`bump` must not be null");

        SemanticVersion version = SemanticVersion.of(nodeElement.getTextContent());
        SemanticVersion updatedVersion = version.bump(bump).stripSuffix();
        nodeElement.setTextContent(updatedVersion.toString());
    }

    /// Traverses the XML document tree starting from the given parent node, following the specified path,
    /// and returns the child node at the end of the path.
    /// If no child node matching the path is found, throws an exception.
    ///
    /// @param parent              the starting node of the tree traversal
    /// @param path                the list of node names defining the path to traverse
    /// @param currentElementIndex the current index in the path being processed
    /// @return the `Node` at the end of the specified path
    /// @throws IllegalStateException if a node in the path cannot be found or traversed
    private static Node walk(Node parent, List<String> path, int currentElementIndex) throws IllegalStateException {
        if (currentElementIndex == path.size()) {
            return parent;
        }
        String currentElementName = path.get(currentElementIndex);
        NodeList childNodes = parent.getChildNodes();
        return IntStream.range(0, childNodes.getLength())
                .mapToObj(childNodes::item)
                .filter(child -> currentElementName.equals(child.getNodeName()))
                .findFirst()
                .map(child -> walk(child, path, currentElementIndex + 1))
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to find element %s in %s".formatted(currentElementName, parent.getNodeName())
                ));
    }

    /// Retrieves an existing instance of `DocumentBuilder` or creates a new one if it does not already exist.
    /// Configures the `DocumentBuilderFactory` to enable namespace awareness,
    /// to disallow ignoring of element content whitespace, and to include comments in the parsed documents.
    /// If an error occurs during the configuration or creation of the `DocumentBuilder`,
    /// a `MojoFailureException` is thrown.
    ///
    /// @return the `DocumentBuilder` instance, either existing or newly created
    /// @throws MojoFailureException if the creation of a new `DocumentBuilder` fails due to a configuration issue
    private static DocumentBuilder getOrCreateDocumentBuilder() throws MojoFailureException {
        if (documentBuilder != null) {
            return documentBuilder;
        }
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setIgnoringElementContentWhitespace(false);
        documentBuilderFactory.setIgnoringComments(false);
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            return documentBuilder;
        } catch (ParserConfigurationException e) {
            throw new MojoFailureException("Unable to construct XML document builder", e);
        }
    }

    /// Retrieves the existing instance of `Transformer` or creates a new one if it does not already exist.
    /// This method uses a `TransformerFactory` to create a new `Transformer` instance when needed.
    /// If an error occurs during the creation of the `Transformer`, a `MojoFailureException` is thrown.
    ///
    /// @return the `Transformer` instance, either existing or newly created
    /// @throws MojoFailureException if the creation of a new `Transformer` fails due to a configuration issue
    private static Transformer getOrCreateTransformer() throws MojoFailureException {
        if (transformer != null) {
            return transformer;
        }
        try {
            transformer = TransformerFactory.newInstance()
                    .newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            return transformer;
        } catch (TransformerConfigurationException e) {
            throw new MojoFailureException("Unable to construct XML transformer", e);
        }
    }
}
