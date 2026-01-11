package io.github.bsels.semantic.version.utils;

import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersion;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionChange;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/// Utility class for handling various operations related to Project Object Model (POM) files, such as reading, writing,
/// version updates, and backups.
/// Provides methods for XML parsing and manipulation with the goal of managing POM files effectively
/// in a Maven project context.
///
/// This class is not intended to be instantiated, and all methods are designed to be used in a static context.
public final class POMUtils {
    /// Represents the artifact identifier of a Maven project, commonly referred to as "artifactId".
    /// This field holds a string value corresponding to the unique name that distinguishes a particular artifact
    /// within a Maven group.
    /// It is critical in identifying the artifact during resolution and publishing phases when working
    /// with Maven repositories.
    public static final String ARTIFACT_ID = "artifactId";
    /// Represents the constant identifier for a build configuration or process in a system.
    /// This variable typically signifies a specific context or property related to a build operation.
    /// It is a constant value set to "build".
    public static final String BUILD = "build";
    /// A constant string that represents the key or identifier used for referencing
    /// dependencies in a specific context, such as configuration files or dependency management systems.
    /// This variable is intended to be immutable and globally accessible.
    public static final String DEPENDENCIES = "dependencies";
    /// A constant string representing the term "dependency".
    /// This variable may be used to denote a dependency within a system, configuration, or software component.
    public static final String DEPENDENCY = "dependency";
    /// Represents the identifier for a Maven project group within the Project Object Model (POM).
    /// The group ID serves as a unique namespace for the project, typically following a reverse-domain
    /// naming convention.
    /// It is a fundamental element used to identify Maven artifacts in a repository.
    public static final String GROUP_ID = "groupId";
    /// A constant string representing the key or identifier for a plugin.
    /// This variable is typically used to denote the context or type of plugin in a system where plugins are managed
    /// or used.
    public static final String PLUGIN = "plugin";
    /// A constant representing the key or identifier for plugins.
    /// Typically used to denote a group, category, or configuration related to plugins in the application.
    public static final String PLUGINS = "plugins";
    /// A constant representing the string literal "project".
    /// This variable is often used as an identifier, key, or label to refer to project-related contexts,
    /// configurations, or data.
    public static final String PROJECT = "project";
    /// Represents the name of the version field or property within a POM (Project Object Model) file.
    /// This constant serves as a key used for identifying and interacting with the version-related elements
    /// or properties within Maven-based projects.
    public static final String VERSION = "version";

    /// A constant list of directory names representing the path segments typically used to locate build-related plugins
    /// within a project structure.
    ///
    /// This list contains predefined values that are commonly used in typical build systems
    /// or configurations to point to the directory where plugins are stored,
    /// ensuring consistency and reuse across the application.
    private static final List<String> BUILD_PLUGINS_PATH = List.of(PROJECT, BUILD, PLUGINS, PLUGIN);
    /// A constant list that represents the structured path to the plugins section under plugin management in
    /// a build configuration.
    /// The elements in the list represent successive levels of hierarchy needed to navigate to the "plugins"
    /// node within the build configuration structure.
    private static final List<String> BUILD_PLUGIN_MANAGEMENT_PLUGINS_PATH = List.of(PROJECT, BUILD, "pluginManagement", PLUGINS, PLUGIN);
    /// A constant that represents the path segments used to locate dependency management dependencies within
    /// a project's configuration structure.
    /// It consists of a fixed list containing elements that define the hierarchical path: "project",
    /// "dependencyManagement", and "dependencies".
    ///
    /// This variable is used to navigate or reference the dependency management section
    /// of a project's configuration file or data structure.
    private static final List<String> DEPENDENCY_MANAGEMENT_DEPENDENCIES_PATH = List.of(PROJECT, "dependencyManagement", DEPENDENCIES, DEPENDENCY);
    /// A constant list containing the paths for project dependencies.
    /// This list is intended to hold predefined directory paths or identifiers
    /// used within the application to reference dependency-related resources.
    private static final List<String> DEPENDENCIES_PATH = List.of(PROJECT, DEPENDENCIES, DEPENDENCY);
    /// A constant list representing the parent path components.
    /// This list includes predefined elements such as the project identifier and the "parent" string.
    /// Used to define or identify the hierarchical structure of a parent directory or entity.
    private static final List<String> PARENT_PATH = List.of(PROJECT, "parent");
    /// A constant list of strings representing the XML traversal path to locate the "revision" property
    /// within a Maven POM file.
    /// This path defines the sequential hierarchy of nodes that need to be traversed in the XML document,
    /// starting with the "project" node, followed by the "properties" node, and finally the "revision" node.
    ///
    /// This is primarily used in scenarios where the "revision" property value needs to be accessed or
    /// modified programmatically within the POM file.
    /// It serves as a predefined navigation path, ensuring a consistent and
    /// error-free location of the "revision" property across operations.
    private static final List<String> REVISION_PROPERTY_PATH = List.of(PROJECT, "properties", "revision");
    /// Defines the path to locate the project version element within a POM (Project Object Model) file.
    /// The path is expressed as a list of strings, where each string represents a hierarchical element
    /// from the root of the XML document to the target "version" node.
    ///
    /// This path is primarily used by methods that traverse or manipulate the XML document structure
    /// to locate and update the version information in a Maven project.
    private static final List<String> VERSION_PROPERTY_PATH = List.of(PROJECT, VERSION);

    /// Represents a set of required fields for a Maven artifact.
    ///
    /// This constant defines the essential attributes that must be present
    /// in a Maven artifact's metadata: "groupId", "artifactId", and "version".
    /// These fields are critical for uniquely identifying and resolving a Maven artifact
    /// in a repository or during the build process.
    private static final Set<String> REQUIRED_MAVEN_ARTIFACT_FIELDS = Set.of(GROUP_ID, ARTIFACT_ID, VERSION);

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
    /// @throws MojoExecutionException if the project version node cannot be located in the document
    public static Node getProjectVersionNode(Document document, Modus modus)
            throws NullPointerException, MojoExecutionException {
        Objects.requireNonNull(document, "`document` must not be null");
        Objects.requireNonNull(modus, "`modus` must not be null");
        List<String> versionPropertyPath = switch (modus) {
            case REVISION_PROPERTY -> REVISION_PROPERTY_PATH;
            case PROJECT_VERSION, PROJECT_VERSION_ONLY_LEAFS -> VERSION_PROPERTY_PATH;
        };
        try {
            return walk(document, versionPropertyPath, 0);
        } catch (IllegalStateException e) {
            throw new MojoExecutionException("Unable to find project version on the path: %s".formatted(
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
            throw new MojoExecutionException("Unable to write to %s".formatted(pomFile), e);
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

    /// Updates the version value of the given XML node based on the specified semantic version bump type.
    /// The method retrieves the current semantic version from the node, increments the version according
    /// to the provided bump type, and updates the node with the new version value.
    ///
    /// @param nodeElement the XML node whose version value is to be updated; must not be null
    /// @param bump        the type of semantic version increment to be applied; must not be null
    /// @throws NullPointerException     if either nodeElement or bump is null
    /// @throws IllegalArgumentException if the content of nodeElement cannot be parsed into a valid semantic version
    public static void updateVersion(Node nodeElement, SemanticVersionBump bump)
            throws NullPointerException, IllegalArgumentException {
        Objects.requireNonNull(nodeElement, "`nodeElement` must not be null");
        Objects.requireNonNull(bump, "`bump` must not be null");

        SemanticVersion version = SemanticVersion.of(nodeElement.getTextContent());
        SemanticVersion updatedVersion = version.bump(bump);
        nodeElement.setTextContent(updatedVersion.toString());
    }

    /// Extracts Maven artifacts and their corresponding nodes from the given XML document.
    /// This method processes dependency and plugin-related paths in the document to identify Maven artifacts
    /// and their associated XML nodes.
    ///
    /// @param document the XML document representing a Maven POM file
    /// @return a map where the keys are MavenArtifact objects representing the artifacts and the values are lists of XML nodes associated with those artifacts
    /// @throws NullPointerException if the `document` argument is null
    public static Map<MavenArtifact, List<Node>> getMavenArtifacts(Document document) throws NullPointerException {
        Objects.requireNonNull(document, "`document` must not be null");
        Stream<Node> dependencyNodes = Stream.concat(
                walkStream(document, DEPENDENCIES_PATH, 0),
                walkStream(document, DEPENDENCY_MANAGEMENT_DEPENDENCIES_PATH, 0)
        );
        Stream<Node> pluginNodes = Stream.concat(
                walkStream(document, BUILD_PLUGINS_PATH, 0),
                walkStream(document, BUILD_PLUGIN_MANAGEMENT_PLUGINS_PATH, 0)
        );
        Stream<Node> allNodes = Stream.concat(dependencyNodes, pluginNodes);
        return Stream.concat(allNodes, walkStream(document, PARENT_PATH, 0))
                .map(POMUtils::handleArtifactNode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Utils.groupingByImmutable(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Utils.asImmutableList())
                ));
    }

    /// Updates the text content of the specified node with a new version
    /// if the current text content matches the old version specified in the version change.
    ///
    /// @param versionChange the version change object containing the old and new version values
    /// @param node          the node whose text content is to be updated
    /// @throws NullPointerException if `versionChange` or `node` is null
    public static void updateVersionNodeIfOldVersionMatches(VersionChange versionChange, Node node)
            throws NullPointerException {
        Objects.requireNonNull(versionChange, "`versionChange` must not be null");
        Objects.requireNonNull(node, "`node` must not be null");
        String version = node.getTextContent();
        if (versionChange.oldVersion().equals(version)) {
            node.setTextContent(versionChange.newVersion());
        }
    }

    /// Processes a given XML [Node] to extract Maven artifact details such as groupId, artifactId, and version,
    /// validates the semantic version,
    /// and returns an optional mapping of MavenArtifact to its corresponding version [Node].
    ///
    /// @param element the XML Node to be processed, typically representing an artifact element in a Maven POM-like structure
    /// @return an [Optional] containing a [Map.Entry] where the key is a [MavenArtifact] object and the value is the version [Node], or an empty [Optional] if the required fields are missing or the version is invalid
    private static Optional<Map.Entry<MavenArtifact, Node>> handleArtifactNode(Node element) {
        NodeList childNodes = element.getChildNodes();
        Map<String, Node> tagContent = IntStream.range(0, childNodes.getLength())
                .mapToObj(childNodes::item)
                .filter(node -> REQUIRED_MAVEN_ARTIFACT_FIELDS.contains(node.getNodeName()))
                .collect(Collectors.toMap(Node::getNodeName, Function.identity()));

        if (!tagContent.keySet().containsAll(REQUIRED_MAVEN_ARTIFACT_FIELDS)) {
            return Optional.empty();
        }
        Node version = tagContent.get(VERSION);
        try {
            SemanticVersion.of(version.getTextContent());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        String groupId = tagContent.get(GROUP_ID).getTextContent();
        String artifactId = tagContent.get(ARTIFACT_ID).getTextContent();
        return Optional.of(Map.entry(new MavenArtifact(groupId, artifactId), version));
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
        StreamWalk result = getStreamWalk(parent, path, currentElementIndex);
        return result.nodeStream()
                .findFirst()
                .map(child -> walk(child, path, currentElementIndex + 1))
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to find element '%s' in '%s'".formatted(result.currentElementName(), parent.getNodeName())
                ));
    }

    /// Recursively traverses a hierarchical structure of nodes based on a specified path and returns a stream of
    /// matching nodes.
    ///
    /// @param parent              the starting parent node to traverse from
    /// @param path                a list of strings representing the path to navigate through the hierarchy
    /// @param currentElementIndex the current index in the path being processed
    /// @return a stream of nodes that match the specified path
    private static Stream<Node> walkStream(Node parent, List<String> path, int currentElementIndex) {
        if (currentElementIndex == path.size()) {
            return Stream.of(parent);
        }
        StreamWalk result = getStreamWalk(parent, path, currentElementIndex);
        return result.nodeStream()
                .flatMap(child -> walkStream(child, path, currentElementIndex + 1));
    }

    /// Retrieves a [StreamWalk] object by filtering child nodes of the parent node based on the current element name
    /// from the specified path.
    ///
    /// @param parent              the parent Node from which child nodes are retrieved
    /// @param path                a [List] of Strings representing the path to traverse
    /// @param currentElementIndex the index of the current element in the path
    /// @return a [StreamWalk] object containing the current element name and a stream of matching child nodes
    private static StreamWalk getStreamWalk(Node parent, List<String> path, int currentElementIndex) {
        String currentElementName = path.get(currentElementIndex);
        NodeList childNodes = parent.getChildNodes();
        Stream<Node> nodeStream = IntStream.range(0, childNodes.getLength())
                .mapToObj(childNodes::item)
                .filter(child -> currentElementName.equals(child.getNodeName()));
        return new StreamWalk(currentElementName, nodeStream);
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

    /// A record representing a traversal context within a stream of nodes.
    /// Instances of this class encapsulate the name of the current element and its associated stream of nodes.
    ///
    /// This record is immutable and designed to ensure non-null safety for its parameters.
    /// It is primarily intended for use in operations involving structured node traversals.
    ///
    /// @param currentElementName the name of the current element; must not be null
    /// @param nodeStream         the stream of nodes associated with the element; must not be null
    private record StreamWalk(String currentElementName, Stream<Node> nodeStream) {

        /// Constructs a new instance of StreamWalk.
        /// Ensures that the provided parameters are non-null.
        ///
        /// @param currentElementName the name of the current element; must not be null
        /// @param nodeStream         the stream of nodes associated with the element; must not be null
        /// @throws NullPointerException if either currentElementName or nodeStream is null
        private StreamWalk {
            Objects.requireNonNull(currentElementName, "`currentElementName` must not be null");
            Objects.requireNonNull(nodeStream, "`nodeStream` must not be null");
        }
    }
}
