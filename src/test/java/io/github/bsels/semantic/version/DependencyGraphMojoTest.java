package io.github.bsels.semantic.version;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.graph.ArtifactLocation;
import io.github.bsels.semantic.version.parameters.GraphOutput;
import io.github.bsels.semantic.version.test.utils.ReadMockedMavenSession;
import io.github.bsels.semantic.version.test.utils.TestLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(MockitoExtension.class)
class DependencyGraphMojoTest extends AbstractBaseMojoTest {

    private DependencyGraphMojo classUnderTest;
    private TestLog testLog;
    private Map<Path, StringWriter> mockedOutputFiles;
    private MockedStatic<Files> filesMockedStatic;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalSystemOut;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        classUnderTest = new DependencyGraphMojo();
        testLog = new TestLog(TestLog.LogLevel.NONE);
        classUnderTest.setLog(testLog);
        mockedOutputFiles = new HashMap<>();
        objectMapper = new ObjectMapper();

        filesMockedStatic = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS);
        filesMockedStatic.when(() -> Files.newBufferedWriter(Mockito.any(), Mockito.any(), Mockito.any(OpenOption[].class)))
                .thenAnswer(answer -> {
                    Path path = answer.getArgument(0);
                    mockedOutputFiles.put(path, new StringWriter());
                    return new BufferedWriter(mockedOutputFiles.get(path));
                });

        outputStream = new ByteArrayOutputStream();
        originalSystemOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalSystemOut);
        filesMockedStatic.close();
    }

    @Nested
    class SingleProjectTests {

        @Test
        void internalExecute_SingleProject_ArtifactAndFolder_RelativePaths_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(1);
            MavenArtifact singleArtifact = new MavenArtifact("org.example.itests.single", "project");
            assertThat(graph).containsKey(singleArtifact);
            assertThat(graph.get(singleArtifact)).hasSize(1);
            assertThat(mockedOutputFiles).isEmpty();
        }

        @Test
        void internalExecute_SingleProject_ArtifactOnly_RelativePaths_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_ONLY;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<String>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(1);
            MavenArtifact singleArtifact = new MavenArtifact("org.example.itests.single", "project");
            assertThat(graph).containsKey(singleArtifact);
            assertThat(graph.get(singleArtifact)).hasSize(1);
            assertThat(mockedOutputFiles).isEmpty();
        }

        @Test
        void internalExecute_SingleProject_FolderOnly_RelativePaths_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.FOLDER_ONLY;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<String>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(1);
            MavenArtifact singleArtifact = new MavenArtifact("org.example.itests.single", "project");
            assertThat(graph).containsKey(singleArtifact);
            assertThat(graph.get(singleArtifact)).hasSize(1);
            assertThat(mockedOutputFiles).isEmpty();
        }

        @Test
        void internalExecute_SingleProject_AbsolutePaths_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = false;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(1);
            MavenArtifact singleArtifact = new MavenArtifact("org.example.itests.single", "project");
            assertThat(graph).containsKey(singleArtifact);
            assertThat(graph.get(singleArtifact)).hasSize(1);
            assertThat(mockedOutputFiles).isEmpty();
        }

        @Test
        void internalExecute_SingleProject_WriteToFile() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            Path outputFile = Path.of("/tmp/graph-output.json");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = outputFile;

            // Act
            classUnderTest.internalExecute();

            // Assert
            assertThat(mockedOutputFiles).containsKey(outputFile);
            String fileContent = mockedOutputFiles.get(outputFile).toString();
            assertThat(fileContent).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    fileContent,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(1);
            MavenArtifact singleArtifact = new MavenArtifact("org.example.itests.single", "project");
            assertThat(graph).containsKey(singleArtifact);
            assertThat(outputStream.toString()).isEmpty();
        }
    }

    @Nested
    class MultiProjectTests {

        @Test
        void internalExecute_MultiProject_ArtifactAndFolder_RelativePaths_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("multi");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            // Verify all projects are present
            assertThat(graph).containsKeys(
                    new MavenArtifact("org.example.itests.multi", "parent"),
                    new MavenArtifact("org.example.itests.multi", "dependency"),
                    new MavenArtifact("org.example.itests.multi", "plugin"),
                    new MavenArtifact("org.example.itests.multi", "plugin-management"),
                    new MavenArtifact("org.example.itests.multi", "dependency-management"),
                    new MavenArtifact("org.example.itests.multi", "combination"),
                    new MavenArtifact("org.example.itests.multi", "excluded")
            );

            // Verify transitive dependencies for combination
            MavenArtifact combination = new MavenArtifact("org.example.itests.multi", "combination");
            List<ArtifactLocation> combinationDeps = graph.get(combination);
            assertThat(combinationDeps).hasSize(6);

            // Verify dependencies are in build order (topological sort)
            List<MavenArtifact> depArtifacts = combinationDeps.stream()
                    .map(ArtifactLocation::artifact)
                    .toList();

            assertThat(depArtifacts).containsExactlyInAnyOrder(
                    new MavenArtifact("org.example.itests.multi", "parent"),
                    new MavenArtifact("org.example.itests.multi", "dependency-management"),
                    new MavenArtifact("org.example.itests.multi", "plugin-management"),
                    new MavenArtifact("org.example.itests.multi", "plugin"),
                    new MavenArtifact("org.example.itests.multi", "dependency"),
                    new MavenArtifact("org.example.itests.multi", "combination")
            );

            assertThat(mockedOutputFiles).isEmpty();
        }

        @Test
        void internalExecute_MultiProject_ArtifactOnly_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("multi");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_ONLY;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<String>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(7);

            // Verify combination has correct dependencies as strings
            MavenArtifact combination = new MavenArtifact("org.example.itests.multi", "combination");
            List<String> combinationDeps = graph.get(combination);
            assertThat(combinationDeps).containsExactlyInAnyOrder(
                    "org.example.itests.multi:parent",
                    "org.example.itests.multi:dependency-management",
                    "org.example.itests.multi:plugin-management",
                    "org.example.itests.multi:plugin",
                    "org.example.itests.multi:dependency",
                    "org.example.itests.multi:combination"
            );
        }

        @Test
        void internalExecute_MultiProject_FolderOnly_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("multi");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.FOLDER_ONLY;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<String>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(7);

            // Verify combination has correct folder paths
            MavenArtifact combination = new MavenArtifact("org.example.itests.multi", "combination");
            List<String> combinationFolders = graph.get(combination);
            assertThat(combinationFolders).hasSize(6);
            assertThat(combinationFolders).allMatch(folder -> !folder.startsWith("/"));
        }

        @Test
        void internalExecute_MultiProject_AbsolutePaths_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("multi");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = false;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(7);

            // Verify paths are absolute
            MavenArtifact combination = new MavenArtifact("org.example.itests.multi", "combination");
            List<ArtifactLocation> combinationDeps = graph.get(combination);
            assertThat(combinationDeps).hasSize(6);
            assertThat(combinationDeps).allMatch(dep -> dep.folder().startsWith("/"));
        }

        @Test
        void internalExecute_MultiProject_WriteToFile() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("multi");
            Path outputFile = Path.of("/tmp/multi-graph.json");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = outputFile;

            // Act
            classUnderTest.internalExecute();

            // Assert
            assertThat(mockedOutputFiles).containsKey(outputFile);
            String fileContent = mockedOutputFiles.get(outputFile).toString();
            assertThat(fileContent).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    fileContent,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(7);
            assertThat(outputStream.toString()).isEmpty();
        }
    }

    @Nested
    class ChainedDependencyTests {

        @Test
        void internalExecute_ChainedDependency_ArtifactAndFolder_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("chained-dependency");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            // Verify all projects are present
            assertThat(graph).containsKeys(
                    new MavenArtifact("org.example.itests.chained", "root"),
                    new MavenArtifact("org.example.itests.chained", "a"),
                    new MavenArtifact("org.example.itests.chained", "b"),
                    new MavenArtifact("org.example.itests.chained", "c"),
                    new MavenArtifact("org.example.itests.chained", "d"),
                    new MavenArtifact("org.example.itests.chained", "e")
            );

            // Verify chained dependencies: e -> d -> c -> b -> a
            MavenArtifact e = new MavenArtifact("org.example.itests.chained", "e");
            List<ArtifactLocation> eDeps = graph.get(e);
            assertThat(eDeps).hasSize(5);
            assertThat(eDeps.stream().map(ArtifactLocation::artifact)).containsExactlyInAnyOrder(
                    new MavenArtifact("org.example.itests.chained", "a"),
                    new MavenArtifact("org.example.itests.chained", "b"),
                    new MavenArtifact("org.example.itests.chained", "c"),
                    new MavenArtifact("org.example.itests.chained", "d"),
                    new MavenArtifact("org.example.itests.chained", "e")
            );
        }

        @ParameterizedTest
        @EnumSource(GraphOutput.class)
        void internalExecute_ChainedDependency_AllGraphOutputs(GraphOutput graphOutput) throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("chained-dependency");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = graphOutput;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act & Assert
            assertThatNoException().isThrownBy(() -> classUnderTest.internalExecute());

            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();
        }
    }

    @Nested
    class LeavesProjectTests {

        @Test
        void internalExecute_LeavesProject_ArtifactAndFolder_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("leaves");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            // Verify all projects are present
            assertThat(graph).containsKeys(
                    new MavenArtifact("org.example.itests.leaves", "root"),
                    new MavenArtifact("org.example.itests.leaves", "child-1"),
                    new MavenArtifact("org.example.itests.leaves", "intermediate"),
                    new MavenArtifact("org.example.itests.leaves", "child-2"),
                    new MavenArtifact("org.example.itests.leaves", "child-3")
            );
        }
    }

    @Nested
    class MultiRecursiveProjectTests {

        @Test
        void internalExecute_MultiRecursiveProject_ArtifactAndFolder_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("multi-recursive");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            // Verify all projects are present
            assertThat(graph).containsKeys(
                    new MavenArtifact("org.example.itests.multi-recursive", "parent"),
                    new MavenArtifact("org.example.itests.multi-recursive", "child-1"),
                    new MavenArtifact("org.example.itests.multi-recursive", "child-2")
            );
        }
    }

    @Nested
    class RevisionProjectTests {

        @Test
        void internalExecute_RevisionMultiProject_ArtifactAndFolder_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("revision", "multi");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            // Verify all projects are present
            assertThat(graph).containsKeys(
                    new MavenArtifact("org.example.itests.revision.multi", "parent"),
                    new MavenArtifact("org.example.itests.revision.multi", "child1"),
                    new MavenArtifact("org.example.itests.revision.multi", "child2")
            );
        }

        @Test
        void internalExecute_RevisionSingleProject_ArtifactAndFolder_Console() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("revision", "single");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            assertThat(output).isNotEmpty();

            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            assertThat(graph).hasSize(1);
            assertThat(graph).containsKey(
                    new MavenArtifact("org.example.itests.revision.single", "project")
            );
        }
    }

    @Nested
    class ExceptionTests {

        @Test
        void internalExecute_IoExceptionWhenWritingFile_ThrowsMojoExecutionException() {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            Path outputFile = Path.of("/tmp/failing-output.json");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = outputFile;

            // Mock IOException when writing to file
            filesMockedStatic.when(() -> Files.newBufferedWriter(Mockito.eq(outputFile), Mockito.any(), Mockito.any(OpenOption[].class)))
                    .thenThrow(new IOException("Simulated IO error"));

            // Act & Assert
            assertThatExceptionOfType(MojoExecutionException.class)
                    .isThrownBy(() -> classUnderTest.internalExecute())
                    .withMessageContaining("Unable to write to output file")
                    .withMessageContaining("/tmp/failing-output.json")
                    .withCauseInstanceOf(IOException.class);
        }

        @Test
        void internalExecute_IoExceptionDuringWrite_ThrowsMojoExecutionException() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            Path outputFile = Path.of("/tmp/write-error.json");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = outputFile;

            // Create a BufferedWriter that throws IOException on write
            BufferedWriter failingWriter = Mockito.mock(BufferedWriter.class);
            Mockito.doThrow(new IOException("Write failed")).when(failingWriter).write(Mockito.anyString());

            filesMockedStatic.when(() -> Files.newBufferedWriter(Mockito.eq(outputFile), Mockito.any(), Mockito.any(OpenOption[].class)))
                    .thenReturn(failingWriter);

            // Act & Assert
            assertThatExceptionOfType(MojoExecutionException.class)
                    .isThrownBy(() -> classUnderTest.internalExecute())
                    .withMessageContaining("Unable to write to output file")
                    .withCauseInstanceOf(IOException.class);
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void internalExecute_RootProjectPath_UsesCurrentDirectory() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            // Root project should have "." as folder when using relative paths
            assertThat(graph).hasSize(1);
        }

        @Test
        void internalExecute_EmptyDependencies_ProducesValidGraph() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("single");
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;

            // Act
            classUnderTest.internalExecute();

            // Assert
            String output = outputStream.toString().trim();
            Map<MavenArtifact, List<ArtifactLocation>> graph = objectMapper.readValue(
                    output,
                    new TypeReference<>() {
                    }
            );

            MavenArtifact singleArtifact = new MavenArtifact("org.example.itests.single", "project");
            assertThat(graph.get(singleArtifact)).hasSize(1);
        }

        @Test
        void internalExecute_MultipleGraphOutputFormats_ProduceConsistentResults() throws Exception {
            // Arrange
            Path projectRoot = getResourcesPath("multi");

            // Test ARTIFACT_AND_FOLDER
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_AND_FOLDER;
            classUnderTest.useRelativePaths = true;
            classUnderTest.outputFile = null;
            classUnderTest.internalExecute();
            String outputFull = outputStream.toString().trim();
            outputStream.reset();

            // Test ARTIFACT_ONLY
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.ARTIFACT_ONLY;
            classUnderTest.internalExecute();
            String outputArtifact = outputStream.toString().trim();
            outputStream.reset();

            // Test FOLDER_ONLY
            classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(projectRoot, Path.of("."));
            classUnderTest.graphOutput = GraphOutput.FOLDER_ONLY;
            classUnderTest.internalExecute();
            String outputFolder = outputStream.toString().trim();

            // Assert all produce valid JSON
            assertThat(outputFull).isNotEmpty();
            assertThat(outputArtifact).isNotEmpty();
            assertThat(outputFolder).isNotEmpty();

            // Verify they all have the same number of keys
            Map<MavenArtifact, ?> graphFull = objectMapper.readValue(outputFull, new TypeReference<>() {
            });
            Map<MavenArtifact, ?> graphArtifact = objectMapper.readValue(outputArtifact, new TypeReference<>() {
            });
            Map<MavenArtifact, ?> graphFolder = objectMapper.readValue(outputFolder, new TypeReference<>() {
            });

            assertThat(graphFull).hasSameSizeAs(graphArtifact);
            assertThat(graphFull).hasSameSizeAs(graphFolder);
        }
    }
}
