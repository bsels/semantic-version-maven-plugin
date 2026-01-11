package io.github.bsels.semantic.version.test.utils;

import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ReadMockedMavenSession {
    private static final String PROJECT = "project";
    private static final String GROUP_ID = "groupId";
    private static final String PARENT = "parent";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String VERSION = "version";
    private static final String PROPERTIES = "properties";
    private static final String REVISION = "revision";
    private static final Path POM_FILE = Path.of("pom.xml");
    private static final String $_REVISION = "${revision}";
    private static final String MODULE = "module";
    private static final String MODULES = "modules";
    private static final DocumentBuilder DOCUMENT_BUILDER = getDocumentBuilder();

    private ReadMockedMavenSession() {
        // No instance needed
    }

    public static MavenSession readMockedMavenSession(Path projectRoot, Path currentModule) {
        Map<Path, MavenProject> projects = readMavenProjectsAsMap(projectRoot);

        MavenSession session = Mockito.mock(MavenSession.class);

        Mockito.lenient()
                .when(session.getExecutionRootDirectory())
                .thenReturn(projectRoot.toAbsolutePath().toString());
        Path normalizeCurrentModule = projectRoot.resolve(currentModule).normalize();
        Mockito.lenient()
                .when(session.getCurrentProject())
                .thenReturn(projects.get(normalizeCurrentModule));
        Mockito.lenient()
                .when(session.getTopLevelProject())
                .thenReturn(projects.get(projectRoot.resolve(".").normalize()));

        List<MavenProject> sortedProjects = projects.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> entry.getKey().startsWith(normalizeCurrentModule))
                .map(Map.Entry::getValue)
                .toList();

        Mockito.lenient()
                .when(session.getResult())
                .thenReturn(new MavenExecutionResultMock(sortedProjects));
        return session;
    }

    private static Map<Path, MavenProject> readMavenProjectsAsMap(Path projectRoot) {
        return readMavenProjects(projectRoot)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Stream<Map.Entry<Path, MavenProject>> readMavenProjects(Path path) {
        Path pomFile = path.resolve(POM_FILE).toAbsolutePath();
        MavenProject mavenProject = new MavenProject();
        mavenProject.setFile(pomFile.toFile());

        Document pom = readPom(pomFile);
        String revision = walk(pom, List.of(PROJECT, PROPERTIES, REVISION), 0)
                .map(Node::getTextContent)
                .orElse($_REVISION);

        String groupId = walk(pom, List.of(PROJECT, GROUP_ID), 0)
                .or(() -> walk(pom, List.of(PROJECT, PARENT, GROUP_ID), 0))
                .map(Node::getTextContent)
                .orElseThrow();
        String artifactId = walk(pom, List.of(PROJECT, ARTIFACT_ID), 0)
                .map(Node::getTextContent)
                .orElseThrow();
        String version = walk(pom, List.of(PROJECT, VERSION), 0)
                .or(() -> walk(pom, List.of(PROJECT, PARENT, VERSION), 0))
                .map(Node::getTextContent)
                .map(text -> $_REVISION.equals(text) ? revision : text)
                .orElseThrow();

        mavenProject.setGroupId(groupId);
        mavenProject.setArtifactId(artifactId);
        mavenProject.setVersion(version);

        Optional<Node> modules = walk(pom, List.of(PROJECT, MODULES), 0);
        Stream<Map.Entry<Path, MavenProject>> currentProject = Stream.of(Map.entry(path.normalize(), mavenProject));
        if (modules.isPresent()) {
            NodeList nodeList = modules.get().getChildNodes();
            List<String> modulesString = IntStream.range(0, nodeList.getLength())
                    .mapToObj(nodeList::item)
                    .filter(node -> MODULE.equals(node.getNodeName()))
                    .map(Node::getTextContent)
                    .toList();
            mavenProject.getModules().addAll(modulesString);
            return Stream.concat(
                    currentProject,
                    modulesString.stream()
                            .map(path::resolve)
                            .flatMap(ReadMockedMavenSession::readMavenProjects)
            );
        } else {
            return currentProject;
        }
    }

    private static Optional<Node> walk(Node parent, List<String> path, int currentElementIndex) throws IllegalStateException {
        if (currentElementIndex == path.size()) {
            return Optional.of(parent);
        }
        String currentElementName = path.get(currentElementIndex);
        NodeList childNodes = parent.getChildNodes();
        return IntStream.range(0, childNodes.getLength())
                .mapToObj(childNodes::item)
                .filter(child -> currentElementName.equals(child.getNodeName()))
                .findFirst()
                .flatMap(child -> walk(child, path, currentElementIndex + 1));
    }

    public static Document readPom(Path pomFile) {
        try (InputStream inputStream = Files.newInputStream(pomFile)) {
            return DOCUMENT_BUILDER.parse(inputStream);
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private static DocumentBuilder getDocumentBuilder() {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setIgnoringElementContentWhitespace(false);
        documentBuilderFactory.setIgnoringComments(false);
        try {
            return documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private record MavenExecutionResultMock(List<MavenProject> topologicallySortedProjects)
            implements MavenExecutionResult {

        public MavenExecutionResultMock {
            Objects.requireNonNull(topologicallySortedProjects, "`topologicallySortedProjects` must not be null");
            topologicallySortedProjects.forEach(Objects::requireNonNull);
            topologicallySortedProjects = List.copyOf(topologicallySortedProjects);
        }

        @Override
        public MavenExecutionResult setProject(MavenProject project) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MavenProject getProject() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MavenExecutionResult setTopologicallySortedProjects(List<MavenProject> projects) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MavenProject> getTopologicallySortedProjects() {
            return topologicallySortedProjects();
        }

        @Override
        public MavenExecutionResult setDependencyResolutionResult(DependencyResolutionResult result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DependencyResolutionResult getDependencyResolutionResult() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Throwable> getExceptions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MavenExecutionResult addException(Throwable e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasExceptions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BuildSummary getBuildSummary(MavenProject project) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addBuildSummary(BuildSummary summary) {
            throw new UnsupportedOperationException();
        }
    }
}
