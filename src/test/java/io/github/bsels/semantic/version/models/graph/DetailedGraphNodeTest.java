package io.github.bsels.semantic.version.models.graph;

import io.github.bsels.semantic.version.models.MavenArtifact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DetailedGraphNodeTest {
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String FOLDER = "folder/path";

    @Nested
    class ConstructorTest {

        @ParameterizedTest
        @CsvSource(value = {
                "null,null,null,artifact",
                "null," + FOLDER + ",empty,artifact",
                GROUP_ID + ":" + ARTIFACT_ID + ",null,empty,folder",
                GROUP_ID + ":" + ARTIFACT_ID + "," + FOLDER + ",null,dependencies"
        }, nullValues = "null")
        void nullInput_ThrowsNullPointerException(String artifactString, String folder, String dependenciesType, String exceptionParameter) {
            MavenArtifact artifact = artifactString == null ? null : MavenArtifact.of(artifactString);
            List<ArtifactLocation> dependencies = dependenciesType == null ? null : List.of();
            assertThatThrownBy(() -> new DetailedGraphNode(artifact, folder, dependencies))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`%s` must not be null", exceptionParameter);
        }

        @Test
        void validInputs_ReturnsCorrectDetailedGraphNode() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            List<ArtifactLocation> dependencies = List.of();
            DetailedGraphNode node = new DetailedGraphNode(artifact, FOLDER, dependencies);
            assertThat(node)
                    .isNotNull()
                    .hasFieldOrPropertyWithValue("artifact", artifact)
                    .hasFieldOrPropertyWithValue("folder", FOLDER)
                    .hasFieldOrPropertyWithValue("dependencies", dependencies);
        }

        @Test
        void validInputsWithDependencies_ReturnsCorrectDetailedGraphNode() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            MavenArtifact depArtifact = new MavenArtifact("depGroup", "depArtifact");
            ArtifactLocation depLocation = new ArtifactLocation(depArtifact, "dep/folder");
            List<ArtifactLocation> dependencies = List.of(depLocation);
            DetailedGraphNode node = new DetailedGraphNode(artifact, FOLDER, dependencies);
            assertThat(node)
                    .isNotNull()
                    .hasFieldOrPropertyWithValue("artifact", artifact)
                    .hasFieldOrPropertyWithValue("folder", FOLDER)
                    .hasFieldOrPropertyWithValue("dependencies", dependencies);
        }
    }

    @Nested
    class AccessorTest {

        @Test
        void artifact_ReturnsCorrectValue() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            DetailedGraphNode node = new DetailedGraphNode(artifact, FOLDER, List.of());
            assertThat(node.artifact())
                    .isEqualTo(artifact);
        }

        @Test
        void folder_ReturnsCorrectValue() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            DetailedGraphNode node = new DetailedGraphNode(artifact, FOLDER, List.of());
            assertThat(node.folder())
                    .isEqualTo(FOLDER);
        }

        @Test
        void dependencies_ReturnsCorrectValue() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            MavenArtifact depArtifact = new MavenArtifact("depGroup", "depArtifact");
            ArtifactLocation depLocation = new ArtifactLocation(depArtifact, "dep/folder");
            List<ArtifactLocation> dependencies = List.of(depLocation);
            DetailedGraphNode node = new DetailedGraphNode(artifact, FOLDER, dependencies);
            assertThat(node.dependencies())
                    .isEqualTo(dependencies)
                    .hasSize(1)
                    .containsExactly(depLocation);
        }
    }

    @Nested
    class EqualsAndHashCodeTest {

        @Test
        void sameValues_AreEqual() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            List<ArtifactLocation> dependencies = List.of();
            DetailedGraphNode node1 = new DetailedGraphNode(artifact, FOLDER, dependencies);
            DetailedGraphNode node2 = new DetailedGraphNode(artifact, FOLDER, dependencies);
            assertThat(node1)
                    .isEqualTo(node2)
                    .hasSameHashCodeAs(node2);
        }

        @Test
        void differentArtifact_AreNotEqual() {
            MavenArtifact artifact1 = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            MavenArtifact artifact2 = new MavenArtifact("otherGroup", ARTIFACT_ID);
            DetailedGraphNode node1 = new DetailedGraphNode(artifact1, FOLDER, List.of());
            DetailedGraphNode node2 = new DetailedGraphNode(artifact2, FOLDER, List.of());
            assertThat(node1)
                    .isNotEqualTo(node2);
        }

        @Test
        void differentFolder_AreNotEqual() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            DetailedGraphNode node1 = new DetailedGraphNode(artifact, FOLDER, List.of());
            DetailedGraphNode node2 = new DetailedGraphNode(artifact, "other/folder", List.of());
            assertThat(node1)
                    .isNotEqualTo(node2);
        }

        @Test
        void differentDependencies_AreNotEqual() {
            MavenArtifact artifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID);
            MavenArtifact depArtifact = new MavenArtifact("depGroup", "depArtifact");
            ArtifactLocation depLocation = new ArtifactLocation(depArtifact, "dep/folder");
            DetailedGraphNode node1 = new DetailedGraphNode(artifact, FOLDER, List.of());
            DetailedGraphNode node2 = new DetailedGraphNode(artifact, FOLDER, List.of(depLocation));
            assertThat(node1)
                    .isNotEqualTo(node2);
        }
    }
}
