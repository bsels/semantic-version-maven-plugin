package io.github.bsels.semantic.version.utils;

import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionChange;
import io.github.bsels.semantic.version.parameters.Modus;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class POMUtilsTest {
    private static final Path POM_FILE = Path.of("project", "pom.xml");
    private static final Path POM_BACKUP_FILE = Path.of("project", "pom.xml.backup");

    @Mock
    Node nodeMock;

    private static void clearFieldOnPOMUtils(String field) {
        try {
            Field transformerField = POMUtils.class.getDeclaredField(field);
            transformerField.setAccessible(true);
            transformerField.set(null, null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static DocumentBuilder getDocumentBuilder() {
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        return documentBuilder;
    }

    private static Document createEmptyPom() {
        Document document = getDocumentBuilder().newDocument();
        document.appendChild(document.createElement("project"));
        return document;
    }

    @Nested
    class UpdateVersionNodeIfOldVersionMatchesTest {

        @Test
        void nullVersionChange_ThrowsNullPointerException() {
            assertThatThrownBy(() -> POMUtils.updateVersionNodeIfOldVersionMatches(null, nodeMock))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`versionChange` must not be null");
        }

        @Test
        void nullNode_ThrowsNullPointerException() {
            VersionChange versionChange = new VersionChange("1.2.3", "1.2.4");
            assertThatThrownBy(() -> POMUtils.updateVersionNodeIfOldVersionMatches(versionChange, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`node` must not be null");
        }

        @Test
        void versionChangeDoesNotMatch_DoesNotUpdateNode() {
            VersionChange versionChange = new VersionChange("1.2.3", "1.2.4");
            Mockito.when(nodeMock.getTextContent())
                    .thenReturn("1.0.0");

            assertThatNoException()
                    .isThrownBy(() -> POMUtils.updateVersionNodeIfOldVersionMatches(versionChange, nodeMock));

            Mockito.verify(nodeMock, Mockito.times(1))
                    .getTextContent();
            Mockito.verify(nodeMock, Mockito.never())
                    .setTextContent(Mockito.anyString());
            Mockito.verifyNoMoreInteractions(nodeMock);
        }

        @Test
        void versionChangeMatches_UpdatesNode() {
            VersionChange versionChange = new VersionChange("1.2.3", "1.2.4");
            Mockito.when(nodeMock.getTextContent())
                    .thenReturn("1.2.3");

            assertThatNoException()
                    .isThrownBy(() -> POMUtils.updateVersionNodeIfOldVersionMatches(versionChange, nodeMock));

            Mockito.verify(nodeMock, Mockito.times(1))
                    .getTextContent();
            Mockito.verify(nodeMock, Mockito.times(1))
                    .setTextContent(versionChange.newVersion());
            Mockito.verifyNoMoreInteractions(nodeMock);
        }
    }

    @Nested
    class UpdateVersionTest {

        @Test
        void nullNodeElement_ThrowsNullPointerException() {
            assertThatThrownBy(() -> POMUtils.updateVersion(null, SemanticVersionBump.NONE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`nodeElement` must not be null");
        }

        @Test
        void nullBump_ThrowsNullPointerException() {
            assertThatThrownBy(() -> POMUtils.updateVersion(nodeMock, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`bump` must not be null");
        }

        @ParameterizedTest
        @CsvSource({
                "1.2.3,MAJOR,2.0.0",
                "1.2.3,MINOR,1.3.0",
                "1.2.3,PATCH,1.2.4",
                "1.2.3,NONE,1.2.3",
                "1.2.3-SNAPSHOT,MAJOR,2.0.0-SNAPSHOT",
                "1.2.3-SNAPSHOT,MINOR,1.3.0-SNAPSHOT",
                "1.2.3-SNAPSHOT,PATCH,1.2.4-SNAPSHOT",
                "1.2.3-SNAPSHOT,NONE,1.2.3-SNAPSHOT"
        })
        void happyFlow_Success(String currentVersion, SemanticVersionBump bump, String expectedVersion) {
            Mockito.when(nodeMock.getTextContent())
                    .thenReturn(currentVersion);

            assertThatNoException()
                    .isThrownBy(() -> POMUtils.updateVersion(nodeMock, bump));

            Mockito.verify(nodeMock, Mockito.times(1))
                    .getTextContent();
            Mockito.verify(nodeMock, Mockito.times(1))
                    .setTextContent(expectedVersion);
            Mockito.verifyNoMoreInteractions(nodeMock);
        }
    }

    @Nested
    class StreamWalkTest {

        private Constructor<?> streamWalkConstructor;

        @BeforeEach
        public void setUp() throws NoSuchMethodException {
            streamWalkConstructor = Stream.of(POMUtils.class.getDeclaredClasses())
                    .filter(clazz -> "StreamWalk".equals(clazz.getSimpleName()))
                    .findFirst()
                    .orElseThrow()
                    .getDeclaredConstructor(String.class, Stream.class);
            streamWalkConstructor.setAccessible(true);
        }

        @Test
        void nullCurrentElementName_ThrowsNullPointerException() {
            String nullString = null;
            Stream<Node> emptyStream = Stream.empty();
            assertThatThrownBy(() -> streamWalkConstructor.newInstance(nullString, emptyStream))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasRootCauseInstanceOf(NullPointerException.class)
                    .hasRootCauseMessage("`currentElementName` must not be null");
        }

        @Test
        void nullNodeStream_ThrowsNullPointerException() {
            String name = "name";
            Stream<Node> nullStream = null;
            assertThatThrownBy(() -> streamWalkConstructor.newInstance(name, nullStream))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasRootCauseInstanceOf(NullPointerException.class)
                    .hasRootCauseMessage("`nodeStream` must not be null");
        }

        @Test
        void happyFlow_Success() throws InvocationTargetException, InstantiationException, IllegalAccessException {
            String name = "name";
            Stream<Node> stream = Stream.empty();

            Object instance = streamWalkConstructor.newInstance(name, stream);
            assertThat(instance)
                    .hasNoNullFieldsOrProperties()
                    .hasFieldOrPropertyWithValue("currentElementName", name)
                    .hasFieldOrPropertyWithValue("nodeStream", stream);
        }
    }

    @Nested
    class GetOrCreateTransformerTest {

        @Mock
        Transformer transformerMock;

        @Mock
        TransformerFactory transformerFactoryMock;

        private Method getOrCreateTransformerMethod;

        @BeforeEach
        public void setUp() throws NoSuchMethodException {
            getOrCreateTransformerMethod = POMUtils.class.getDeclaredMethod("getOrCreateTransformer");
            getOrCreateTransformerMethod.setAccessible(true);
            // Make sure transformer is cleared before each test
            clearFieldOnPOMUtils("transformer");
        }

        @AfterEach
        public void tearDown() {
            // Make sure transformer is cleared after each test
            clearFieldOnPOMUtils("transformer");
        }

        @Test
        void transformerCreationFailed_ThrowsMojoFailureException() throws TransformerConfigurationException {
            try (MockedStatic<TransformerFactory> transformerFactoryStatic = Mockito.mockStatic(TransformerFactory.class)) {
                transformerFactoryStatic.when(TransformerFactory::newInstance)
                        .thenReturn(transformerFactoryMock);

                Mockito.when(transformerFactoryMock.newTransformer())
                        .thenThrow(new TransformerConfigurationException("Transformer configuration issues"));

                assertThatThrownBy(() -> getOrCreateTransformerMethod.invoke(null))
                        .isInstanceOf(InvocationTargetException.class)
                        .hasCauseInstanceOf(MojoFailureException.class)
                        .satisfies(
                                throwable -> assertThat(throwable.getCause())
                                        .isInstanceOf(MojoFailureException.class)
                                        .hasMessage("Unable to construct XML transformer")
                        )
                        .hasRootCauseInstanceOf(TransformerConfigurationException.class)
                        .hasRootCauseMessage("Transformer configuration issues");

                transformerFactoryStatic.verify(TransformerFactory::newInstance, Mockito.times(1));
                Mockito.verify(transformerFactoryMock, Mockito.times(1))
                        .newTransformer();

                Mockito.verifyNoMoreInteractions(transformerFactoryMock);
                transformerFactoryStatic.verifyNoMoreInteractions();
            }
        }

        @Test
        void happyFlow_Success() throws InvocationTargetException, IllegalAccessException, TransformerConfigurationException {
            try (MockedStatic<TransformerFactory> transformerFactoryStatic = Mockito.mockStatic(TransformerFactory.class)) {
                transformerFactoryStatic.when(TransformerFactory::newInstance)
                        .thenReturn(transformerFactoryMock);

                Mockito.when(transformerFactoryMock.newTransformer())
                        .thenReturn(transformerMock);

                assertThat(getOrCreateTransformerMethod.invoke(null))
                        .isSameAs(transformerMock);

                // Next call should return the same transformer
                assertThat(getOrCreateTransformerMethod.invoke(null))
                        .isSameAs(transformerMock);

                transformerFactoryStatic.verify(TransformerFactory::newInstance, Mockito.times(1));
                Mockito.verify(transformerFactoryMock, Mockito.times(1))
                        .newTransformer();

                Mockito.verify(transformerMock, Mockito.times(1))
                        .setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

                Mockito.verifyNoMoreInteractions(transformerFactoryMock, transformerMock);
                transformerFactoryStatic.verifyNoMoreInteractions();
            }
        }
    }

    @Nested
    class GetOrCreateDocumentBuilderTest {

        @Mock
        DocumentBuilder documentBuilderMock;

        @Mock
        DocumentBuilderFactory documentBuilderFactoryMock;

        private Method getOrCreateDocumentBuilderMethod;

        @BeforeEach
        public void setUp() throws NoSuchMethodException {
            getOrCreateDocumentBuilderMethod = POMUtils.class.getDeclaredMethod("getOrCreateDocumentBuilder");
            getOrCreateDocumentBuilderMethod.setAccessible(true);
            // Make sure transformer is cleared before each test
            clearFieldOnPOMUtils("documentBuilder");
        }

        @AfterEach
        public void tearDown() {
            // Make sure transformer is cleared before each test
            clearFieldOnPOMUtils("documentBuilder");
        }

        @Test
        void documentBuilderCreationFailed_ThrowsMojoFailureException() throws ParserConfigurationException {
            try (MockedStatic<DocumentBuilderFactory> documentBuilderFactoryStatic = Mockito.mockStatic(DocumentBuilderFactory.class)) {
                documentBuilderFactoryStatic.when(DocumentBuilderFactory::newInstance)
                        .thenReturn(documentBuilderFactoryMock);

                Mockito.when(documentBuilderFactoryMock.newDocumentBuilder())
                        .thenThrow(new ParserConfigurationException("Parser configuration failure"));

                assertThatThrownBy(() -> getOrCreateDocumentBuilderMethod.invoke(null))
                        .isInstanceOf(InvocationTargetException.class)
                        .hasCauseInstanceOf(MojoFailureException.class)
                        .satisfies(
                                throwable -> assertThat(throwable.getCause())
                                        .isInstanceOf(MojoFailureException.class)
                                        .hasMessage("Unable to construct XML document builder")
                        )
                        .hasRootCauseInstanceOf(ParserConfigurationException.class)
                        .hasRootCauseMessage("Parser configuration failure");

                documentBuilderFactoryStatic.verify(DocumentBuilderFactory::newInstance, Mockito.times(1));
                Mockito.verify(documentBuilderFactoryMock, Mockito.times(1))
                        .setNamespaceAware(true);
                Mockito.verify(documentBuilderFactoryMock, Mockito.times(1))
                        .setIgnoringElementContentWhitespace(false);
                Mockito.verify(documentBuilderFactoryMock, Mockito.times(1))
                        .setIgnoringComments(false);
                Mockito.verify(documentBuilderFactoryMock, Mockito.times(1))
                        .newDocumentBuilder();

                Mockito.verifyNoMoreInteractions(documentBuilderFactoryMock);
                documentBuilderFactoryStatic.verifyNoMoreInteractions();
            }
        }

        @Test
        void happyFlow_Success() throws InvocationTargetException, IllegalAccessException, ParserConfigurationException {
            try (MockedStatic<DocumentBuilderFactory> documentBuilderFactoryStatic = Mockito.mockStatic(DocumentBuilderFactory.class)) {
                documentBuilderFactoryStatic.when(DocumentBuilderFactory::newInstance)
                        .thenReturn(documentBuilderFactoryMock);

                Mockito.when(documentBuilderFactoryMock.newDocumentBuilder())
                        .thenReturn(documentBuilderMock);

                assertThat(getOrCreateDocumentBuilderMethod.invoke(null))
                        .isSameAs(documentBuilderMock);

                // Second call return same element
                assertThat(getOrCreateDocumentBuilderMethod.invoke(null))
                        .isSameAs(documentBuilderMock);

                documentBuilderFactoryStatic.verify(DocumentBuilderFactory::newInstance, Mockito.times(1));
                Mockito.verify(documentBuilderFactoryMock, Mockito.times(1))
                        .setNamespaceAware(true);
                Mockito.verify(documentBuilderFactoryMock, Mockito.times(1))
                        .setIgnoringElementContentWhitespace(false);
                Mockito.verify(documentBuilderFactoryMock, Mockito.times(1))
                        .setIgnoringComments(false);
                Mockito.verify(documentBuilderFactoryMock, Mockito.times(1))
                        .newDocumentBuilder();

                Mockito.verifyNoMoreInteractions(documentBuilderFactoryMock, documentBuilderMock);
                documentBuilderFactoryStatic.verifyNoMoreInteractions();
            }
        }
    }

    @Nested
    class GetProjectVersionNodeTest {

        @ParameterizedTest
        @EnumSource(Modus.class)
        void nullDocument_ThrowsNullPointerException(Modus modus) {
            assertThatThrownBy(() -> POMUtils.getProjectVersionNode(null, modus))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`document` must not be null");
        }

        @Test
        void nullModus_ThrowsNullPointerException() {
            Document document = createDummyPom();
            assertThatThrownBy(() -> POMUtils.getProjectVersionNode(document, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`modus` must not be null");
        }

        @ParameterizedTest
        @CsvSource({
                "REVISION_PROPERTY,project->properties->revision,properties",
                "PROJECT_VERSION,project->version,version",
                "PROJECT_VERSION_ONLY_LEAFS,project->version,version",
        })
        void emptyDocument_ThrowsMojoExecutionException(Modus modus, String propertyPath, String firstMissingElement) {
            Document emptyPom = createEmptyPom();
            assertThatThrownBy(() -> POMUtils.getProjectVersionNode(emptyPom, modus))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Unable to find project version on the path: %s".formatted(propertyPath))
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Unable to find element '%s' in 'project'".formatted(firstMissingElement));
        }

        @ParameterizedTest
        @CsvSource({
                "REVISION_PROPERTY,2.0.0",
                "PROJECT_VERSION,1.0.0",
                "PROJECT_VERSION_ONLY_LEAFS,1.0.0",
        })
        void happyFlow_Success(Modus modus, String expectedVersion) throws MojoExecutionException {
            Document pom = createDummyPom();
            Node node = POMUtils.getProjectVersionNode(pom, modus);
            assertThat(node.getTextContent())
                    .isEqualTo(expectedVersion);
        }

        private Document createDummyPom() {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            Document document = documentBuilder.newDocument();
            Node project = document.appendChild(document.createElement("project"));
            project.appendChild(document.createElement("version")).setTextContent("1.0.0");
            Node properties = project.appendChild(document.createElement("properties"));
            properties.appendChild(document.createElement("revision")).setTextContent("2.0.0");
            return document;
        }
    }

    @Nested
    class GetMavenArtifactsTest {

        @Test
        void nullDocument_ThrowsNullPointerException() {
            assertThatThrownBy(() -> POMUtils.getMavenArtifacts(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`document` must not be null");
        }

        @Test
        void emptyDocument_ReturnEmpty() {
            Document pom = createEmptyPom();
            assertThat(POMUtils.getMavenArtifacts(pom))
                    .isNotNull()
                    .isEmpty();
        }

        @Test
        void fullPom_ReturnProcessableMavenArtifacts() {
            Document pom = createDummyPom();
            String groupId = "com.example";
            assertThat(POMUtils.getMavenArtifacts(pom))
                    .isNotNull()
                    .isNotEmpty()
                    .hasSize(5)
                    .hasEntrySatisfying(
                            new MavenArtifact(groupId, "parent"),
                            list -> assertThat(list)
                                    .hasSize(1)
                                    .extracting(Node::getTextContent)
                                    .containsExactlyInAnyOrder("1.0.0")
                    )
                    .hasEntrySatisfying(
                            new MavenArtifact(groupId, "dependency"),
                            list -> assertThat(list)
                                    .hasSize(2)
                                    .extracting(Node::getTextContent)
                                    .containsExactlyInAnyOrder("1.0.1", "0.0.1")
                    )
                    .hasEntrySatisfying(
                            new MavenArtifact(groupId, "dependencyManagement"),
                            list -> assertThat(list)
                                    .hasSize(1)
                                    .extracting(Node::getTextContent)
                                    .containsExactlyInAnyOrder("1.0.2")
                    )
                    .hasEntrySatisfying(
                            new MavenArtifact(groupId, "plugin"),
                            list -> assertThat(list)
                                    .hasSize(1)
                                    .extracting(Node::getTextContent)
                                    .containsExactlyInAnyOrder("1.0.3")
                    )
                    .hasEntrySatisfying(
                            new MavenArtifact(groupId, "pluginManagement"),
                            list -> assertThat(list)
                                    .hasSize(1)
                                    .extracting(Node::getTextContent)
                                    .containsExactlyInAnyOrder("1.0.4")
                    );
        }

        private Document createDummyPom() {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            Document document = documentBuilder.newDocument();
            Node project = document.appendChild(document.createElement("project"));

            // Properties
            Node properties = project.appendChild(document.createElement("properties"));
            properties.appendChild(document.createElement("revision")).setTextContent("2.0.0");
            properties.appendChild(document.createElement("property.version")).setTextContent("1.0.0-alpha");

            // Parent
            Node parent = project.appendChild(document.createElement("parent"));
            parent.appendChild(document.createElement("version")).setTextContent("1.0.0");
            parent.appendChild(document.createElement("artifactId")).setTextContent("parent");
            parent.appendChild(document.createElement("groupId")).setTextContent("com.example");

            // Dependencies
            Node dependencies = project.appendChild(document.createElement("dependencies"));
            Node dependencyWithVersion = dependencies.appendChild(document.createElement("dependency"));
            dependencyWithVersion.appendChild(document.createElement("version")).setTextContent("1.0.1");
            dependencyWithVersion.appendChild(document.createElement("artifactId")).setTextContent("dependency");
            dependencyWithVersion.appendChild(document.createElement("groupId")).setTextContent("com.example");
            Node dependencyWithoutVersion = dependencies.appendChild(document.createElement("dependency"));
            dependencyWithoutVersion.appendChild(document.createElement("artifactId")).setTextContent("dependencyManagement");
            dependencyWithoutVersion.appendChild(document.createElement("groupId")).setTextContent("com.example");

            // DependencyManagement
            Node dependencyManagement = project.appendChild(document.createElement("dependencyManagement"));
            Node dependencyManagementDependencies = dependencyManagement.appendChild(document.createElement("dependencies"));
            Node dependencyManagementDependency0 = dependencyManagementDependencies.appendChild(document.createElement("dependency"));
            dependencyManagementDependency0.appendChild(document.createElement("artifactId")).setTextContent("dependencyManagement");
            dependencyManagementDependency0.appendChild(document.createElement("groupId")).setTextContent("com.example");
            dependencyManagementDependency0.appendChild(document.createElement("version")).setTextContent("1.0.2");
            Node dependencyManagementDependency1 = dependencyManagementDependencies.appendChild(document.createElement("dependency"));
            dependencyManagementDependency1.appendChild(document.createElement("artifactId")).setTextContent("dependencyManagement2");
            dependencyManagementDependency1.appendChild(document.createElement("groupId")).setTextContent("com.example");
            dependencyManagementDependency1.appendChild(document.createElement("version")).setTextContent("${property.version}");
            Node dependencyManagementDependencyDuplicatedDependency = dependencyManagementDependencies.appendChild(document.createElement("dependency"));
            dependencyManagementDependencyDuplicatedDependency.appendChild(document.createElement("artifactId")).setTextContent("dependency");
            dependencyManagementDependencyDuplicatedDependency.appendChild(document.createElement("groupId")).setTextContent("com.example");
            dependencyManagementDependencyDuplicatedDependency.appendChild(document.createElement("version")).setTextContent("0.0.1");

            // Build plugins
            Node buildPlugins = project.appendChild(document.createElement("build"));
            Node buildPluginsPlugins = buildPlugins.appendChild(document.createElement("plugins"));
            Node buildPluginsPluginWithVersion = buildPluginsPlugins.appendChild(document.createElement("plugin"));
            buildPluginsPluginWithVersion.appendChild(document.createElement("version")).setTextContent("1.0.3");
            buildPluginsPluginWithVersion.appendChild(document.createElement("artifactId")).setTextContent("plugin");
            buildPluginsPluginWithVersion.appendChild(document.createElement("groupId")).setTextContent("com.example");
            Node buildPluginsPluginWithoutVersion = buildPluginsPlugins.appendChild(document.createElement("plugin"));
            buildPluginsPluginWithoutVersion.appendChild(document.createElement("artifactId")).setTextContent("pluginManagement");
            buildPluginsPluginWithoutVersion.appendChild(document.createElement("groupId")).setTextContent("com.example");

            // Build plugin management
            Node buildPluginManagement = buildPlugins.appendChild(document.createElement("pluginManagement"));
            Node buildPluginManagementPlugins = buildPluginManagement.appendChild(document.createElement("plugins"));
            Node buildPluginManagementPlugin0 = buildPluginManagementPlugins.appendChild(document.createElement("plugin"));
            buildPluginManagementPlugin0.appendChild(document.createElement("artifactId")).setTextContent("pluginManagement");
            buildPluginManagementPlugin0.appendChild(document.createElement("groupId")).setTextContent("com.example");
            buildPluginManagementPlugin0.appendChild(document.createElement("version")).setTextContent("1.0.4");
            Node buildPluginManagementPlugin1 = buildPluginManagementPlugins.appendChild(document.createElement("plugin"));
            buildPluginManagementPlugin1.appendChild(document.createElement("artifactId")).setTextContent("pluginManagement2");
            buildPluginManagementPlugin1.appendChild(document.createElement("groupId")).setTextContent("com.example");
            buildPluginManagementPlugin1.appendChild(document.createElement("version")).setTextContent("${property.version}");
            return document;
        }
    }

    @Nested
    class WritePomTest {

        @Nested
        class WriterFlowTest {

            @Mock
            Transformer transformerMock;

            @Mock
            TransformerFactory transformerFactoryMock;

            @Mock
            Writer writerMock;

            @Test
            void nullDocument_ThrowsNullPointerException() {
                assertThatThrownBy(() -> POMUtils.writePom(null, writerMock))
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("`document` must not be null");
            }

            @Test
            void nullWriter_ThrowsNullPointerException() {
                Document pom = createEmptyPom();
                assertThatThrownBy(() -> POMUtils.writePom(pom, null))
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("`writer` must not be null");
            }

            @Test
            void ioExceptionHappened_ThrowsMojoExecutionException()
                    throws IOException {
                Document pom = createEmptyPom();
                Mockito.doThrow(IOException.class)
                        .when(writerMock)
                        .write(Mockito.anyString());

                assertThatThrownBy(() -> POMUtils.writePom(pom, writerMock))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to write XML document")
                        .hasCauseInstanceOf(IOException.class);
            }

            @Test
            void transformerExceptionHappened_ThrowsMojoExecutionException()
                    throws TransformerException {
                clearFieldOnPOMUtils("transformer");
                try (MockedStatic<TransformerFactory> transformerFactoryStatic = Mockito.mockStatic(TransformerFactory.class)) {
                    transformerFactoryStatic.when(TransformerFactory::newInstance)
                            .thenReturn(transformerFactoryMock);
                    Mockito.when(transformerFactoryMock.newTransformer())
                            .thenReturn(transformerMock);
                    Mockito.doThrow(TransformerException.class)
                            .when(transformerMock)
                            .transform(Mockito.any(), Mockito.any());

                    Document pom = createEmptyPom();
                    assertThatThrownBy(() -> POMUtils.writePom(pom, writerMock))
                            .isInstanceOf(MojoExecutionException.class)
                            .hasMessage("Unable to write XML document")
                            .hasCauseInstanceOf(TransformerException.class);
                } finally {
                    clearFieldOnPOMUtils("transformer");
                }
            }

            @Test
            void happyFlow_CorrectWritten() {
                Document pom = createEmptyPom();
                StringWriter writer = new StringWriter();
                assertThatNoException()
                        .isThrownBy(() -> POMUtils.writePom(pom, writer));

                assertThat(writer.toString())
                        .isEqualTo("""
                                <?xml version="1.0" encoding="UTF-8"?>
                                <project/>\
                                """);
            }
        }

        @Nested
        class FileFlowTest {

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void nullDocument_ThrowsNullPointerException(boolean backup) {
                assertThatThrownBy(() -> POMUtils.writePom(null, POM_FILE, backup))
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("`document` must not be null");
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void nullPomFile_ThrowsNullPointerException(boolean backup) {
                Document pom = createEmptyPom();
                assertThatThrownBy(() -> POMUtils.writePom(pom, null, backup))
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("`pomFile` must not be null");
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void openingWriterFails_ThrowsMojoExecutionException(boolean backup) throws IOException {
                Document pom = createEmptyPom();
                try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                    filesMockedStatic.when(() -> Files.exists(POM_FILE))
                            .thenReturn(backup);
                    filesMockedStatic.when(() -> Files.newBufferedWriter(
                            POM_FILE,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    )).thenThrow(new IOException("Unable to open writer"));

                    assertThatThrownBy(() -> POMUtils.writePom(pom, POM_FILE, backup))
                            .isInstanceOf(MojoExecutionException.class)
                            .hasMessage("Unable to write to %s".formatted(POM_FILE))
                            .hasRootCauseInstanceOf(IOException.class)
                            .hasRootCauseMessage("Unable to open writer");

                    filesMockedStatic.verify(() -> Files.copy(
                            POM_FILE,
                            POM_BACKUP_FILE,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.COPY_ATTRIBUTES,
                            StandardCopyOption.REPLACE_EXISTING
                    ), Mockito.times(backup ? 1 : 0));
                }
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void happyFlow_CorrectlyWritten(boolean backup) throws IOException {
                Document pom = createEmptyPom();
                try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                    filesMockedStatic.when(() -> Files.exists(POM_FILE))
                            .thenReturn(backup);
                    StringWriter writer = new StringWriter();
                    BufferedWriter bufferedWriter = new BufferedWriter(writer);

                    filesMockedStatic.when(() -> Files.newBufferedWriter(
                            POM_FILE,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    )).thenReturn(bufferedWriter);

                    assertThatNoException()
                            .isThrownBy(() -> POMUtils.writePom(pom, POM_FILE, backup));

                    assertThat(writer.toString())
                            .isEqualTo("""
                                    <?xml version="1.0" encoding="UTF-8"?>
                                    <project/>\
                                    """);

                    filesMockedStatic.verify(() -> Files.copy(
                            POM_FILE,
                            POM_BACKUP_FILE,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.COPY_ATTRIBUTES,
                            StandardCopyOption.REPLACE_EXISTING
                    ), Mockito.times(backup ? 1 : 0));
                }
            }
        }
    }

    @Nested
    class ReadPomTest {

        @Test
        void nullPomFile_ThrowsNullPointerException() {
            assertThatThrownBy(() -> POMUtils.readPom(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`pomFile` must not be null");
        }

        @Test
        void openInputStreamFailed_ThrowsMojoExecutionException() {
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.newInputStream(Mockito.any()))
                        .thenThrow(new IOException("Unable to open input stream"));

                assertThatThrownBy(() -> POMUtils.readPom(POM_FILE))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to read '%s' file".formatted(POM_FILE))
                        .hasRootCauseInstanceOf(IOException.class)
                        .hasRootCauseMessage("Unable to open input stream");

                filesMockedStatic.verify(() -> Files.newInputStream(POM_FILE), Mockito.times(1));
                filesMockedStatic.verifyNoMoreInteractions();
            }
        }

        @Test
        void nonXMLDocument_ThrowsMojoExecutionException() {
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.newInputStream(Mockito.any()))
                        .thenReturn(new ByteArrayInputStream("Not an XML File, just a normal text file".getBytes()));

                assertThatThrownBy(() -> POMUtils.readPom(POM_FILE))
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("Unable to read '%s' file".formatted(POM_FILE))
                        .hasRootCauseInstanceOf(SAXException.class)
                        .hasRootCauseMessage("Content is not allowed in prolog.");

                filesMockedStatic.verify(() -> Files.newInputStream(POM_FILE), Mockito.times(1));
                filesMockedStatic.verifyNoMoreInteractions();
            }
        }

        @Test
        void validXMLDocument_ReturnCorrectDocument() throws MojoExecutionException, MojoFailureException {
            try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
                filesMockedStatic.when(() -> Files.newInputStream(Mockito.any()))
                        .thenReturn(new ByteArrayInputStream("""
                                <project>\
                                <groupId>com.example</groupId>\
                                <artifactId>project</artifactId>\
                                <version>1.0.0</version>\
                                </project>
                                """.getBytes()));

                Document document = POMUtils.readPom(POM_FILE);
                assertThat(document)
                        .returns("project", d -> d.getDocumentElement().getNodeName())
                        .satisfies(
                                d -> assertThat(d.getDocumentElement().getChildNodes())
                                        .returns(3, NodeList::getLength)
                                        .satisfies(
                                                nodes -> assertThat(nodes.item(0))
                                                        .returns("groupId", Node::getNodeName)
                                                        .returns("com.example", Node::getTextContent),
                                                nodes -> assertThat(nodes.item(1))
                                                        .returns("artifactId", Node::getNodeName)
                                                        .returns("project", Node::getTextContent),
                                                nodes -> assertThat(nodes.item(2))
                                                        .returns("version", Node::getNodeName)
                                                        .returns("1.0.0", Node::getTextContent)
                                        )
                        );

                filesMockedStatic.verify(() -> Files.newInputStream(POM_FILE), Mockito.times(1));
                filesMockedStatic.verifyNoMoreInteractions();
            }
        }

        private InputStream stringToInputStream(String string) {
            return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
        }
    }
}
