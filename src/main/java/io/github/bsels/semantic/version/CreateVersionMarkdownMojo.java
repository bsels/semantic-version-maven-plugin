package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.utils.MarkdownUtils;
import io.github.bsels.semantic.version.utils.ProcessUtils;
import io.github.bsels.semantic.version.utils.TerminalHelper;
import io.github.bsels.semantic.version.utils.Utils;
import io.github.bsels.semantic.version.utils.yaml.front.block.YamlFrontMatterBlock;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.commonmark.node.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/// Mojo for creating a version Markdown file based on semantic versioning.
///
/// This class is an implementation of a Maven plugin goal that facilitates the creation of a semantic
/// version Markdown file for documenting version changes in projects within a specified scope.
/// It provides functionality to select semantic version bumps, manage changelog entries,
/// and generate Markdown content according to the determined versioning structure.
///
/// The Mojo operates in the following steps:
/// 1. Collects the list of projects within a scope defined by the Maven build lifecycle.
/// 2. Allows the user to define semantic version bumps for each project.
/// 3. Creates and updates a version Markdown file with the required versioning details.
///
/// This goal ensures consistency in semantic versioning practices across multi-module Maven projects.
@Mojo(name = "create", aggregator = true, requiresDependencyResolution = ResolutionScope.NONE)
@Execute(phase = LifecyclePhase.NONE)
public final class CreateVersionMarkdownMojo extends BaseMojo {
    /// A static list containing the semantic version bump types in ascending order of significance:
    /// PATCH, MINOR, and MAJOR.
    ///
    /// This list defines the standard sequence of semantic version increments allowed in the application.
    /// It is used to determine the type of version bump that can be considered
    /// or applied during semantic versioning operations.
    ///
    /// - PATCH: Represents the smallest increment, typically for backward-compatible bug fixes.
    /// - MINOR: Represents an intermediate increment, typically for adding backward-compatible functionality.
    /// - MAJOR: Represents the largest increment, typically involving breaking changes.
    ///
    /// Being immutable and final, this list ensures a consistent
    /// and predefined order for semantic version bump evaluations or operations across the application.
    private static final List<SemanticVersionBump> SEMANTIC_VERSION_BUMPS = List.of(
            SemanticVersionBump.PATCH, SemanticVersionBump.MINOR, SemanticVersionBump.MAJOR
    );

    /// Default constructor for the CreateVersionMarkdownMojo class.
    /// Invokes the superclass constructor to initialize the instance.
    /// This constructor is typically used by the Maven framework during the build lifecycle.
    public CreateVersionMarkdownMojo() {
        super();
    }

    /// Executes the primary logic of the CreateVersionMarkdownMojo goal.
    /// This method is triggered during the Maven build lifecycle and is responsible for creating a version Markdown
    /// entry based on the semantic versioning bumps determined for projects within the specified scope.
    ///
    /// The method performs the following actions:
    /// 1. Retrieves the list of projects in scope and validates its existence.
    /// 2. Determines and selects the semantic version bumps for these projects.
    /// 3. Constructs a version bump header in YAML front matter format to append to the changelog entry.
    /// 4. Creates the changelog entry and prepends the version bump header.
    /// 5. Validates the existence of the versioning folder, creating it if necessary.
    /// 6. Resolves the target versioning file path and writes the updated Markdown content to it.
    ///
    /// @throws MojoExecutionException if an error occurs during execution, such as issues creating directories or writing the versioning file.
    /// @throws MojoFailureException   if the operation to process or create the version Markdown file fails.
    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        List<MavenArtifact> projects = getProjectsInScope()
                .map(mavenProject -> new MavenArtifact(mavenProject.getGroupId(), mavenProject.getArtifactId()))
                .toList();
        if (projects.isEmpty()) {
            log.warn("No projects found in scope");
            return;
        }
        Map<MavenArtifact, SemanticVersionBump> selectedProjects = determineVersionBumps(projects);
        if (selectedProjects == null) {
            log.warn("No projects selected");
            return;
        }

        YamlFrontMatterBlock versionBumpHeader = MarkdownUtils.createVersionBumpsHeader(log, selectedProjects);
        Node inputMarkdown = createChangelogEntry();
        inputMarkdown.prependChild(versionBumpHeader);

        Path versioningFolder = getVersioningFolder();
        if (!Files.exists(versioningFolder)) {
            try {
                Files.createDirectories(versioningFolder);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to create versioning folder", e);
            }
        }
        Path versioningFile = Utils.resolveVersioningFile(versioningFolder);
        writeMarkdownFile(inputMarkdown, versioningFile);
    }

    /// Creates a changelog entry by either taking user input directly or by leveraging an external editor.
    /// This method prompts the user to enter multiline input for the changelog entry, where two consecutive empty lines
    /// terminate the input.
    /// If the user enters an empty line initially,
    /// the method invokes an external editor to create the changelog content.
    ///
    /// @return a [Node] representing the parsed Markdown content of the changelog entry.
    /// @throws MojoExecutionException if an error occurs during the execution of the changelog entry creation.
    /// @throws MojoFailureException   if the operation to create or process the changelog fails.
    private Node createChangelogEntry() throws MojoExecutionException, MojoFailureException {
        Optional<String> input = TerminalHelper.readMultiLineInput(
                """
                        Please type the changelog entry here (enter empty line to open external editor, \
                        two empty lines after your input to end):\
                        """
        );
        Node inputMarkdown;
        if (input.isPresent()) {
            inputMarkdown = MarkdownUtils.parseMarkdown(input.get());
        } else {
            inputMarkdown = createVersionMarkdownInExternalEditor();
        }
        return inputMarkdown;
    }

    /// Determines the semantic version bumps for a list of Maven artifacts based on user selection.
    /// This method allows the user to define semantic version bumps for one or multiple projects from the provided list
    /// of Maven artifacts.
    /// If no projects are selected during the process, the method returns null.
    ///
    /// @param projects a list of [MavenArtifact] objects representing the projects for which version bumps will be determined; must not be null
    /// @return a map where the keys are [MavenArtifact] objects and the values are the corresponding [SemanticVersionBump] selected by the user, or null if no projects are selected
    private Map<MavenArtifact, SemanticVersionBump> determineVersionBumps(List<MavenArtifact> projects) {
        Map<MavenArtifact, SemanticVersionBump> selectedProjects = new HashMap<>(projects.size());
        if (projects.size() == 1) {
            MavenArtifact mavenArtifact = projects.get(0);
            System.out.printf("Project %s%n", mavenArtifact);
            SemanticVersionBump versionBump = TerminalHelper.singleChoice(
                    "Select semantic version bump: ", "semantic version", SEMANTIC_VERSION_BUMPS
            );
            selectedProjects.put(mavenArtifact, versionBump);
        } else {
            List<MavenArtifact> projectSelections = TerminalHelper.multiChoice("Select projects:", "project", projects);
            if (projectSelections.isEmpty()) {
                getLog().warn("No projects selected");
                return null;
            }
            System.out.printf("Selected projects: %s%n", projectSelections.stream().map(MavenArtifact::toString).collect(Collectors.joining(", ")));
            for (MavenArtifact mavenArtifact : projectSelections) {
                SemanticVersionBump versionBump = TerminalHelper.singleChoice(
                        "Select semantic version bump for %s: ".formatted(mavenArtifact),
                        "semantic version",
                        SEMANTIC_VERSION_BUMPS
                );
                selectedProjects.put(mavenArtifact, versionBump);
            }
        }
        System.out.printf(
                "Version bumps: %s%n",
                selectedProjects.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> "'%s': %s".formatted(entry.getKey(), entry.getValue()))
                        .collect(Collectors.joining(", "))
        );
        return selectedProjects;
    }

    /// Creates a Markdown file in an external editor, processes and returns its content as a [Node] object.
    /// This method creates a temporary Markdown file, opens it in an external editor for editing,
    /// and subsequently reads its content into a [Node] representation.
    /// The temporary file is deleted after the operation, regardless of its success or failure.
    ///
    /// @return A [Node] object representing the content of the created and processed Markdown file.
    /// @throws MojoExecutionException If there is an issue during the creation or reading process.
    /// @throws MojoFailureException   If the operation fails to create or edit a Markdown file successfully.
    private Node createVersionMarkdownInExternalEditor() throws MojoExecutionException, MojoFailureException {
        Path temporaryMarkdownFile = Utils.createTemporaryMarkdownFile();
        try {
            boolean valid = ProcessUtils.executeEditor(temporaryMarkdownFile);
            if (!valid) {
                throw new MojoFailureException("Unable to create a new Markdown file");
            }
            return MarkdownUtils.readMarkdown(getLog(), temporaryMarkdownFile);
        } finally {
            Utils.deleteFileIfExists(temporaryMarkdownFile);
        }
    }
}
