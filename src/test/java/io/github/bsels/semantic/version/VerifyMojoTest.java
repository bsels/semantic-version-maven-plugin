package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.parameters.ArtifactIdentifier;
import io.github.bsels.semantic.version.parameters.Git;
import io.github.bsels.semantic.version.parameters.Modus;
import io.github.bsels.semantic.version.parameters.VerificationMode;
import io.github.bsels.semantic.version.test.utils.ReadMockedMavenSession;
import io.github.bsels.semantic.version.test.utils.TestLog;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class VerifyMojoTest extends AbstractBaseMojoTest {

    private static final Scenario SINGLE_MAJOR = new Scenario(
            "single-major",
            Path.of("single"),
            Path.of("."),
            Path.of("versioning", "single", "major"),
            Modus.PROJECT_VERSION,
            ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
            true,
            true,
            true,
            true,
            false
    );
    private static final Scenario MULTI_DEPENDENCY = new Scenario(
            "multi-dependency",
            Path.of("multi"),
            Path.of("."),
            Path.of("versioning", "multi", "dependency"),
            Modus.PROJECT_VERSION,
            ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
            false,
            false,
            true,
            true,
            false
    );
    private static final Scenario MULTI_RECURSIVE_PARENT = new Scenario(
            "multi-recursive-parent",
            Path.of("multi-recursive"),
            Path.of("."),
            Path.of("versioning", "multi-recursive"),
            Modus.PROJECT_VERSION,
            ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
            false,
            false,
            true,
            true,
            false
    );
    private static final Scenario LEAVES_INCONSISTENT = new Scenario(
            "leaves-inconsistent",
            Path.of("leaves"),
            Path.of("."),
            Path.of("versioning", "leaves", "single"),
            Modus.PROJECT_VERSION_ONLY_LEAFS,
            ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
            true,
            true,
            true,
            false,
            false
    );
    private static final Scenario REVISION_SINGLE_MAJOR = new Scenario(
            "revision-single-major",
            Path.of("revision", "single"),
            Path.of("."),
            Path.of("versioning", "revision", "single", "major"),
            Modus.REVISION_PROPERTY,
            ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
            true,
            true,
            true,
            true,
            false
    );
    private static final Scenario REVISION_MULTI_MAJOR = new Scenario(
            "revision-multi-major",
            Path.of("revision", "multi"),
            Path.of("."),
            Path.of("versioning", "revision", "multi", "major"),
            Modus.REVISION_PROPERTY,
            ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
            true,
            true,
            true,
            true,
            false
    );
    private static final Scenario SINGLE_UNKNOWN_PROJECT = new Scenario(
            "single-unknown-project",
            Path.of("single"),
            Path.of("."),
            Path.of("versioning", "single", "unknown-project"),
            Modus.PROJECT_VERSION,
            ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
            false,
            false,
            true,
            true,
            true
    );
    private static final Scenario EMPTY_VERSIONING = new Scenario(
            "empty-versioning",
            Path.of("single"),
            Path.of("."),
            Path.of("versioning"),
            Modus.PROJECT_VERSION,
            ArtifactIdentifier.GROUP_ID_AND_ARTIFACT_ID,
            false,
            true,
            false,
            true,
            false
    );
    private static final Scenario LEAVES_ARTIFACT_ONLY = new Scenario(
            "leaves-artifact-only",
            Path.of("leaves"),
            Path.of("."),
            Path.of("versioning", "leaves", "single-artifact-only"),
            Modus.PROJECT_VERSION_ONLY_LEAFS,
            ArtifactIdentifier.ONLY_ARTIFACT_ID,
            true,
            true,
            true,
            false,
            false
    );

    @Mock
    Process processMock;

    private VerifyMojo classUnderTest;
    private TestLog testLog;
    private List<List<String>> mockedExecutedProcesses;
    private MockedConstruction<ProcessBuilder> mockedProcessBuilderConstruction;

    @BeforeEach
    void setUp() throws Exception {
        classUnderTest = new VerifyMojo();
        testLog = new TestLog(TestLog.LogLevel.DEBUG);
        classUnderTest.setLog(testLog);
        mockedExecutedProcesses = new ArrayList<>();

        mockedProcessBuilderConstruction = Mockito.mockConstruction(ProcessBuilder.class, (mock, context) -> {
            Mockito.when(mock.command(Mockito.anyList()))
                    .thenAnswer(invocation -> {
                        mockedExecutedProcesses.add(invocation.getArgument(0));
                        return mock;
                    });
            Mockito.when(mock.inheritIO()).thenReturn(mock);
            Mockito.when(mock.start()).thenReturn(processMock);
        });
        Mockito.lenient().when(processMock.waitFor()).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        mockedProcessBuilderConstruction.close();
    }

    @ParameterizedTest(name = "{0} mode={1} consistent={2}")
    @MethodSource("verifyCombinations")
    void execute_validatesConfigurationCombos(
            Scenario scenario,
            VerificationMode mode,
            boolean consistent
    ) {
        configureScenario(scenario, mode, consistent, Git.NO_GIT);

        ExpectedOutcome expected = expectedOutcome(scenario, mode, consistent);

        if (expected.success()) {
            assertThatNoException().isThrownBy(classUnderTest::execute);
        } else {
            assertThatThrownBy(classUnderTest::execute)
                    .isInstanceOf(MojoFailureException.class)
                    .hasMessageContaining(expected.failureMessage());
        }
    }

    @Test
    void noExecutionOnSubProjectIfDisabled_SkipExecution() {
        classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                resolveResources(Path.of("leaves")),
                Path.of("child-1")
        );

        assertThatNoException().isThrownBy(classUnderTest::execute);

        assertThat(testLog.getLogRecords())
                .hasSize(1)
                .satisfiesExactly(validateLogRecordInfo(
                        "Skipping execution for subproject org.example.itests.leaves:child-1:5.0.0-child-1"
                ));

        assertThat(mockedExecutedProcesses).isEmpty();
    }

    @Test
    void dependentProjectsModeFailsWhenDependentsNotBumped() {
        configureScenario(MULTI_RECURSIVE_PARENT, VerificationMode.DEPENDENT_PROJECTS, false, Git.NO_GIT);

        assertThatThrownBy(classUnderTest::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessage("Versioning verification failed.");
    }

    @ParameterizedTest
    @EnumSource(value = Git.class, names = {"STASH", "COMMIT"})
    void gitStatusRunsWhenGitEnabled(Git git) {
        configureScenario(SINGLE_MAJOR, VerificationMode.NONE, false, git);

        assertThatNoException().isThrownBy(classUnderTest::execute);

        assertThat(mockedExecutedProcesses)
                .containsExactly(List.of("git", "status"));
    }

    private void configureScenario(
            Scenario scenario,
            VerificationMode mode,
            boolean consistent,
            Git git
    ) {
        classUnderTest.session = ReadMockedMavenSession.readMockedMavenSession(
                resolveResources(scenario.projectRoot()),
                scenario.currentModule()
        );
        classUnderTest.modus = scenario.modus();
        classUnderTest.identifier = scenario.identifier();
        classUnderTest.versionDirectory = resolveResources(scenario.versioningDir());
        classUnderTest.mode = mode;
        classUnderTest.consistentVersionBumps = consistent;
        classUnderTest.git = git;
    }

    private ExpectedOutcome expectedOutcome(Scenario scenario, VerificationMode mode, boolean consistent) {
        if (scenario.hasUnknownArtifacts()) {
            return ExpectedOutcome.failureOutcome("not present in the project scope");
        }
        boolean valid = switch (mode) {
            case NONE -> true;
            case AT_LEAST_ONE_PROJECT -> scenario.hasVersionMarkdowns();
            case DEPENDENT_PROJECTS -> scenario.dependentProjectsMatch();
            case ALL_PROJECTS -> scenario.allProjectsMatch();
        };
        if (!valid) {
            return ExpectedOutcome.failureOutcome("Versioning verification failed.");
        }
        if (consistent) {
            if (!scenario.hasVersionMarkdowns()) {
                return ExpectedOutcome.successOutcome();
            }
            if (!scenario.consistentBumps()) {
                return ExpectedOutcome.failureOutcome("Version bumps are not consistent across all projects.");
            }
        }
        return ExpectedOutcome.successOutcome();
    }

    private Path resolveResources(Path relativePath) {
        return getResourcesPath(relativePath.toString());
    }

    private static Stream<Arguments> verifyCombinations() {
        return scenarios().flatMap(scenario -> Stream.of(VerificationMode.values())
                .flatMap(mode -> Stream.of(false, true)
                        .map(consistent -> Arguments.of(scenario, mode, consistent))));
    }

    private static Stream<Scenario> scenarios() {
        return Stream.of(
                SINGLE_MAJOR,
                MULTI_DEPENDENCY,
                MULTI_RECURSIVE_PARENT,
                LEAVES_INCONSISTENT,
                REVISION_SINGLE_MAJOR,
                REVISION_MULTI_MAJOR,
                SINGLE_UNKNOWN_PROJECT,
                EMPTY_VERSIONING,
                LEAVES_ARTIFACT_ONLY
        );
    }

    private record ExpectedOutcome(boolean success, String failureMessage) {
        private static ExpectedOutcome successOutcome() {
            return new ExpectedOutcome(true, null);
        }

        private static ExpectedOutcome failureOutcome(String message) {
            return new ExpectedOutcome(false, message);
        }
    }

    private record Scenario(
            String name,
            Path projectRoot,
            Path currentModule,
            Path versioningDir,
            Modus modus,
            ArtifactIdentifier identifier,
            boolean allProjectsMatch,
            boolean dependentProjectsMatch,
            boolean hasVersionMarkdowns,
            boolean consistentBumps,
            boolean hasUnknownArtifacts
    ) {
        @Override
        public String toString() {
            return name;
        }
    }
}
