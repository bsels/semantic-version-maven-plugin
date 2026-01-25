package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MarkdownMapping;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.MavenProjectAndDocument;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.parameters.Git;
import io.github.bsels.semantic.version.parameters.VerificationMode;
import io.github.bsels.semantic.version.utils.ProcessUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/// A Maven Mojo implementation that verifies versioning consistency and project state during the build process.
/// The `VerifyMojo` class is responsible for performing various validation tasks,
/// such as checking version bumps across projects, enforcing consistency rules,
/// and verifying the project scope based on the specified verification mode.
///
/// Key Features:
/// - Configurable verification mode that determines how validation is applied across projects.
/// - Optional enforcement of consistent version bumps across Maven artifacts.
/// - Validation of Markdown files that map Maven artifacts to version bump information.
/// - Integration with Git for additional repository checks when applicable.
///
/// This class extends the `BaseMojo` class and serves as a specialized plugin goal for
/// performing advanced versioning checks within Maven builds.
///
/// Parameters:
/// - `mode`: Specifies the verification mode to govern the validation logic.
///   Acceptable values correspond to the constants in the `VerificationMode` enum.
/// - `consistentVersionBumps`: A flag that enforces consistency in version bumps across projects when set to `true`.
///   Defaults to `false`.
///
/// Exceptions:
/// - Throws `MojoExecutionException` for unexpected errors during the execution phase.
/// - Throws `MojoFailureException` if validation failures occur based on the defined logic
///   or if the project state does not meet the validation criteria.
///
/// This class is designed to be a final implementation, and its extensibility is deliberately
/// restricted to prevent modifications that could interfere with its strict validation rules.
@Mojo(name = "verify", aggregator = true, requiresDependencyResolution = ResolutionScope.NONE)
@Execute(phase = LifecyclePhase.NONE)
public final class VerifyMojo extends BaseMojo {

    /// Defines the verification mode to be used for validating projects within the context of the Maven build process.
    /// The `mode` variable specifies how version-related checks, validations,
    /// and consistency rules should be applied across projects in a multi-project or single-project setup.
    ///
    /// - Acceptable values correspond to the constants defined in the `VerificationMode` enum:
    ///   1. `NONE`: Skips all verification logic.
    ///   2. `AT_LEAST_ONE_PROJECT`: Ensures that at least one project is included in the verification scope.
    ///   3. `DEPENDENT_PROJECTS`: Applies checks only to projects with defined dependencies.
    ///   4. `ALL_PROJECTS` (default): Validates all projects within the current context.
    /// - The chosen mode determines which validation rules are enforced, how failures are handled,
    ///   and the scope of the checks.
    ///
    /// This variable is required and defaults to `ALL_PROJECTS` unless explicitly changed by the user.
    @Parameter(property = "versioning.verification.mode", required = true, defaultValue = "ALL_PROJECTS")
    VerificationMode mode = VerificationMode.ALL_PROJECTS;

    /// Specifies whether to enforce consistent version bumps across all Maven artifacts
    /// within the project during versioning verification.
    ///
    /// If enabled, the plugin ensures that all projects in the scope have identical types
    /// of semantic version bumps (e.g., all projects must have either a major, minor, or patch
    /// version bump). Inconsistencies will result in a validation failure.
    ///
    /// The consistency check is applied only when this parameter is explicitly set to `true`.
    ///
    /// This parameter is mandatory and defaults to `false`. When set to `false`, the plugin
    /// skips the consistency validation step for version bumps.
    ///
    /// Property: `versioning.verification.consistent`
    @Parameter(property = "versioning.verification.consistent", required = true, defaultValue = "false")
    boolean consistentVersionBumps = false;

    /// Default constructor for the `VerifyMojo` class.
    /// Initializes a new instance of the VerifyMojo by invoking the superclass constructor.
    /// This constructor is typically used by the Maven framework during the build lifecycle to configure
    /// and execute the associated Mojo's functionality.
    public VerifyMojo() {
        super();
    }

    /// Executes the primary logic for the Mojo, including validation of versioning, consistency, and project scope.
    /// This method performs the following tasks:
    ///
    /// 1. Checks if Git-related functionality is enabled. If a Git repository is associated,
    ///    additional checks can be performed to validate the repository state (e.g., using `git status`).
    /// 2. Retrieves the set of Maven projects in scope. Each project is represented by a `MavenArtifact`
    ///    containing its group ID and artifact ID.
    /// 3. Processes all version Markdown files and maps them into a `MarkdownMapping` that associates
    ///    Maven artifacts with their corresponding version bump information.
    /// 4. Performs the following validation steps:
    ///    1. Validates that all Markdown files are correctly constructed and usable.
    ///    2. Validates that the detected version bumps align with the set of projects in scope.
    ///    3. Ensures that version bumps are consistent across all affected projects,
    ///       if the consistency check is enabled.
    ///
    /// Throws exceptions in case of validation failures, depending on the current verification mode.
    ///
    /// @throws MojoExecutionException if an unexpected error occurs during execution.
    /// @throws MojoFailureException   if version or consistency validation fails, or if the Maven project state is invalid based on the defined validation logic.
    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException {
        if (Git.NO_GIT != git) {
            ProcessUtils.gitStatus();
        }

        Set<MavenArtifact> projects = getProjectsInScope()
                .map(project -> new MavenArtifact(project.getGroupId(), project.getArtifactId()))
                .collect(Collectors.toSet());

        List<VersionMarkdown> versionMarkdowns = getVersionMarkdowns();
        MarkdownMapping mapping = getMarkdownMapping(versionMarkdowns);

        validateMarkdowns(mapping);
        validateVersionBumps(projects, mapping);
        validateConsistentVersionBumps(mapping);
    }

    /// Validates version bumps across the provided set of Maven artifacts using the specified mapping.
    /// The validation logic is determined by the current verification mode.
    /// Throws an exception if the validation fails in modes other than `NONE`.
    ///
    /// @param projects a set of MavenArtifact objects representing all projects to be validated; must not be null
    /// @param mapping  the MarkdownMapping containing the mapping of Maven artifacts to their corresponding version bump information; must not be null
    /// @throws MojoFailureException   if the validation fails based on the current verification mode
    /// @throws MojoExecutionException if an error occurs while executing the Mojo
    private void validateVersionBumps(Set<MavenArtifact> projects, MarkdownMapping mapping)
            throws MojoFailureException, MojoExecutionException {
        Set<MavenArtifact> versionMarkdownProjects = mapping.versionBumpMap().keySet();
        boolean valid = switch (mode) {
            case NONE -> verificationModeNone();
            case AT_LEAST_ONE_PROJECT -> verificationModeAtLeastOneProject(versionMarkdownProjects);
            case DEPENDENT_PROJECTS -> verificationModeDependentProjects(projects, mapping);
            case ALL_PROJECTS -> verificationModeAllProjects(projects, versionMarkdownProjects);
        };
        if (!valid) {
            throw new MojoFailureException("Versioning verification failed.");
        } else {
            getLog().debug("Versioning verification succeeded.");
        }
    }

    /// Verifies whether all dependent projects in the provided set of Maven artifacts have corresponding
    /// version-markdown mappings, based on the current verification mode.
    /// This method processes the dependencies of the provided projects
    /// and validates that all required projects are accounted for in the mapping.
    ///
    /// @param projects a set of MavenArtifact objects representing the dependent projects to be validated; must not be null
    /// @param mapping  the [MarkdownMapping] containing the mapping of Maven artifacts to their corresponding version bump information; must not be null
    /// @return `true` if all expected dependent projects have corresponding version markdown mappings; `false` otherwise
    /// @throws MojoExecutionException if an unexpected error occurs during the verification process
    /// @throws MojoFailureException   if required dependent projects are missing version markdown mappings
    private boolean verificationModeDependentProjects(Set<MavenArtifact> projects, MarkdownMapping mapping)
            throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        log.debug("Verification mode is DEPENDENT_PROJECTS.");
        Map<MavenArtifact, MavenProjectAndDocument> documents = readAllPoms(getProjectsInScope().toList());
        Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifactMapping =
                createDependencyToProjectArtifactMapping(documents.values(), projects);
        Queue<MavenArtifact> toBeProcessed = new ArrayDeque<>(mapping.versionBumpMap().keySet());
        Set<MavenArtifact> expectedProjects = new HashSet<>();
        while (!toBeProcessed.isEmpty()) {
            MavenArtifact artifact = toBeProcessed.poll();
            expectedProjects.add(artifact);
            toBeProcessed.addAll(dependencyToProjectArtifactMapping.getOrDefault(artifact, List.of()));
        }
        Set<MavenArtifact> versionMarkdownProjects = mapping.versionBumpMap().keySet();
        boolean allMatched = versionMarkdownProjects.containsAll(expectedProjects);
        if (allMatched) {
            log.info("All dependent projects have version Markdown files.");
        } else {
            log.error("Some dependent projects are missing version Markdown files.");
            log.error("Expected projects: " + expectedProjects);
            log.error("Found projects: " + versionMarkdownProjects);
        }
        return allMatched;
    }

    /// Verifies if all projects in the given set of Maven artifacts are present in the set of version-marked projects.
    ///
    /// Logs an informational message if all projects are found in the scope,
    /// otherwise logs error messages indicating the mismatch between the expected and found projects.
    ///
    /// @param projects                a set of [MavenArtifact] objects representing all projects; must not be null
    /// @param versionMarkdownProjects a set of [MavenArtifact] objects representing the projects with version markings; must not be null
    /// @return `true` if all projects are present in the version-marked projects set, `false` otherwise
    private boolean verificationModeAllProjects(
            Set<MavenArtifact> projects,
            Set<MavenArtifact> versionMarkdownProjects
    ) {
        Log log = getLog();
        log.debug("Verification mode is ALL_PROJECTS.");
        boolean allProjects = projects.equals(versionMarkdownProjects);
        if (allProjects) {
            log.info("All projects found in scope.");
        } else {
            log.error("Not all projects found in scope.");
            log.error("Expected: %d projects, found: %d projects.".formatted(projects.size(), versionMarkdownProjects.size()));
        }
        return allProjects;
    }

    /// Determines if the verification mode requires at least one project to be present in the provided set
    /// of version-marked Maven projects.
    ///
    /// Logs debug information and an error or info message based on whether any projects are found in scope.
    ///
    /// @param versionMarkdownProjects a set of MavenArtifact objects representing the projects involved in the verification process; must not be null
    /// @return `true` if at least one project exists in the provided set; `false` otherwise
    private boolean verificationModeAtLeastOneProject(Set<MavenArtifact> versionMarkdownProjects) {
        Log log = getLog();
        log.debug("Verification mode is AT_LEAST_ONE_PROJECT.");
        boolean atLeastOnePresent = !versionMarkdownProjects.isEmpty();
        if (atLeastOnePresent) {
            log.info("Projects found in scope: %s".formatted(versionMarkdownProjects));
        } else {
            log.error("No projects found in scope.");
        }
        return atLeastOnePresent;
    }

    /// Determines if the verification mode is set to NONE and skips the verification process.
    /// Logs a debug message indicating that the verification is bypassed when in NONE mode.
    ///
    /// @return `true` always, as the method signifies that verification is intentionally disabled.
    private boolean verificationModeNone() {
        getLog().debug("Verification mode is NONE. Skipping verification.");
        return true;
    }

    /// Validates that the version bumps across all projects in the provided mapping are consistent.
    /// Ensures that all projects have the same type of version bump if consistency checks are enabled.
    /// Logs debug messages indicating the state of version bump consistency or if the check is skipped.
    /// Throws an exception if inconsistent version bumps are detected.
    ///
    /// @param mapping the [MarkdownMapping] containing the mapping of Maven artifacts to their corresponding semantic version bumps; must not be null
    /// @throws MojoFailureException if version bump consistency checks are enabled, and the version bumps are not consistent across all projects
    private void validateConsistentVersionBumps(MarkdownMapping mapping) throws MojoFailureException {
        Log log = getLog();
        if (!consistentVersionBumps) {
            log.info("Version bump consistency check disabled.");
            return;
        }
        if (mapping.versionBumpMap().isEmpty()) {
            log.info("No projects found in scope.");
            return;
        }
        boolean consistentVersions = mapping.versionBumpMap()
                .values()
                .stream()
                .distinct()
                .count() == 1;
        if (!consistentVersions) {
            throw new MojoFailureException("Version bumps are not consistent across all projects.");
        } else {
            log.info("Version bumps are consistent across all projects.");
        }
    }
}
