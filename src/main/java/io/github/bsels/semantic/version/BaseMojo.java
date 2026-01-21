package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MarkdownMapping;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.parameters.Git;
import io.github.bsels.semantic.version.parameters.Modus;
import io.github.bsels.semantic.version.utils.MarkdownUtils;
import io.github.bsels.semantic.version.utils.ProcessUtils;
import io.github.bsels.semantic.version.utils.Utils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.commonmark.node.Node;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Base class for Maven plugin goals, providing foundational functionality for Mojo execution.
/// This class extends [AbstractMojo] and serves as the base for plugins managing versioning
/// or other project configurations in Maven builds.
/// Subclasses must implement the abstract `internalExecute` method to define specific behaviors.
///
/// This class handles:
/// - Resolving the base directory of the project.
/// - Configurable execution modes for versioning.
/// - Determining whether the plugin executes for subprojects in multi-module builds.
/// - Accessing the current Maven session and project structure.
/// - Delegating core plugin functionality to subclasses.
///
/// Permissions:
/// - This sealed class can only be extended by specific classes stated in its `permits` clause.
///
/// Parameters:
/// - `baseDirectory`: Resolved to the project's `basedir` property.
/// - `modus`: Execution strategy for versioning (e.g., single or multi-module).
/// - `session`: Current Maven session instance.
/// - `executeForSubproject`: Determines whether the plugin should apply logic to subprojects.
/// - `dryRun`: Indicates whether the plugin should execute in dry-run mode.
///
/// The `execute()` method handles plugin execution flow by determining the project type
/// and invoking the `internalExecute()` method of subclasses where necessary.
///
/// Any issues encountered during plugin execution may result in a [MojoExecutionException]
/// or a [MojoFailureException] being thrown.
public abstract sealed class BaseMojo extends AbstractMojo permits CreateVersionMarkdownMojo, UpdatePomMojo {

    /// A constant string representing the filename of the changelog file, "CHANGELOG.md".
    ///
    /// This file typically contains information about the changes, updates, and version history for a project.
    /// It can be used or referenced in Maven plugin implementations to locate
    /// or process the changelog file content during build processes.
    public static final String CHANGELOG_MD = "CHANGELOG.md";

    /// Represents the mode in which project versioning is handled within the Maven plugin.
    /// This parameter is used to define the strategy for managing version numbers across single or multi-module projects.
    ///
    /// Configuration:
    /// - `property`: "versioning.modus", allows external configuration via Maven plugin properties.
    /// - `required`: This parameter is mandatory and must be explicitly defined during plugin execution.
    /// - `defaultValue`: Defaults to `PROJECT_VERSION` mode, where versioning is executed based on the project version.
    ///
    /// Supported Modes:
    /// - [Modus#PROJECT_VERSION]: Handles versioning for projects using the project version property.
    /// - [Modus#REVISION_PROPERTY]: Handles versioning for projects using the revision property.
    /// - [Modus#PROJECT_VERSION_ONLY_LEAFS]: Handles versioning for projects using the project version property,
    ///   but only for leaf projects in a multi-module setup.
    @Parameter(property = "versioning.modus", required = true, defaultValue = "PROJECT_VERSION")
    protected Modus modus = Modus.PROJECT_VERSION;

    /// Represents the current Maven session during the execution of the plugin.
    /// Provides access to details such as the projects being built, current settings,
    /// system properties, and organizational workflow defined in the Maven runtime.
    ///
    /// This parameter is injected by Maven and is critical for accessing and manipulating
    /// the build lifecycle, including resolving the state of the project or session-specific
    /// configurations.
    ///
    /// Configuration:
    /// - `readonly`: Ensures the session remains immutable during plugin execution.
    /// - `required`: The session parameter is mandatory for the plugin to function.
    /// - `defaultValue`: Defaults to Maven's `${session}`, representing the active Maven session.
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    /// Indicates whether the plugin should execute in dry-run mode.
    /// When set to `true`, the plugin performs all operations and logs outputs
    /// without making actual changes to files or the project configuration.
    /// This is useful for testing and verifying the plugin's behavior before applying changes.
    ///
    /// Configuration:
    /// - `property`: Maps to the Maven plugin property `versioning.dryRun`.
    /// - `defaultValue`: Defaults to `false`, meaning dry-run mode is disabled by default.
    @Parameter(property = "versioning.dryRun", defaultValue = "false")
    protected boolean dryRun = false;

    /// Represents the directory used for storing versioning-related files during the Maven plugin execution.
    ///
    /// This field is a configuration parameter for the plugin,
    /// allowing users to specify a custom directory in the version-specific Markdown files resides.
    /// By default, it points to the `.versioning` directory relative to the root project directory.
    ///
    /// Key Characteristics:
    /// - Defined as a `Path` object to represent the directory in a file system-agnostic manner.
    /// - Configurable via the Maven property `versioning.directory`.
    /// - Marked as a required field, meaning the plugin execution will fail if it is not set or cannot be resolved.
    /// - Defaults to the `.versioning` directory, unless explicitly overridden.
    ///
    /// This field is commonly used by methods or processes within the containing class to locate
    /// and operate on files related to versioning functionality.
    @Parameter(property = "versioning.directory", required = true, defaultValue = ".versioning")
    protected Path versionDirectory = Path.of(".versioning");

    /// Represents the Git version control system configuration for the Maven plugin.
    /// Used to manage versioning-related operations specific to Git.
    ///
    /// By default, the value is set to [Git#NO_GIT],
    /// indicating that no Git-specific actions will be performed unless explicitly configured.
    ///
    /// This field can be overridden by specifying the Maven property `versioning.git`.
    @Parameter(property = "versioning.git", defaultValue = "NO_GIT")
    protected Git git = Git.NO_GIT;

    /// Indicates whether the original POM file and CHANGELOG file should be backed up before modifying its content.
    ///
    /// This parameter is configurable via the Maven property `versioning.backup`.
    /// When set to `true`, a backup of the POM/CHANGELOG file will be created before any updates are applied.
    /// The default value for this parameter is `false`, meaning no backup will be created unless explicitly specified.
    @Parameter(property = "versioning.backup", defaultValue = "false")
    boolean backupFiles = false;

    /// Default constructor for the BaseMojo class.
    /// Initializes the instance by invoking the superclass constructor.
    /// Maven framework typically uses this constructor during the build process.
    protected BaseMojo() {
        super();
    }

    /// Executes the Mojo.
    /// This method is the main entry point for the Maven plugin execution.
    /// It handles the execution logic related to ensuring the plugin is executed for the correct Maven project in a
    /// multi-module project scenario and delegates the core functionality to the [#internalExecute()] method
    /// for further implementation by subclasses.
    ///
    /// The execution process includes:
    /// - Determining whether the current Maven project is the root project or a subproject.
    /// - Skipping execution for subprojects unless explicitly allowed by the `executeForSubproject` field.
    /// - Logging appropriate messages regarding execution status.
    /// - Delegating the plugin-specific functionality to the `internalExecute` method.
    ///
    /// @throws MojoExecutionException if there is an issue during the execution causing it to fail irrecoverably.
    /// @throws MojoFailureException   if the execution fails due to a known configuration or logic failure.
    public final void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        MavenProject rootProject = session.getTopLevelProject();
        MavenProject currentProject = session.getCurrentProject();
        if (!rootProject.equals(currentProject)) {
            log.info("Skipping execution for subproject %s:%s:%s".formatted(
                    currentProject.getGroupId(),
                    currentProject.getArtifactId(),
                    currentProject.getVersion()
            ));
            return;
        }

        log.info("Execution for project: %s:%s:%s".formatted(
                currentProject.getGroupId(),
                currentProject.getArtifactId(),
                currentProject.getVersion()
        ));

        internalExecute();
    }

    /// Executes the core functionality of the Maven plugin.
    /// This method is intended to be implemented by subclasses to define the specific behavior of the plugin.
    ///
    /// The method is called internally by the `execute()` method of the containing class,
    /// after performing necessary checks and setup steps related to the Maven project context.
    ///
    /// Subclasses should override this method to provide the actual logic for the plugin operation.
    ///
    /// @throws MojoExecutionException if an unexpected error occurs during the execution, causing it to fail irrecoverably.
    /// @throws MojoFailureException   if the execution fails due to a recoverable or known issue, such as an invalid configuration.
    protected abstract void internalExecute() throws MojoExecutionException, MojoFailureException;

    /// Reads all Markdown files from the `.versioning` directory within the execution root directory,
    /// parses their content, and converts them into a list of [VersionMarkdown] objects.
    ///
    /// The method recursively iterates through the `.versioning` directory, filtering for files with a `.md` extension,
    /// and processes each Markdown file using the [MarkdownUtils#readVersionMarkdown] method.
    /// The parsed results are returned as immutable instances of [VersionMarkdown].
    ///
    /// @return a [List] of [VersionMarkdown] objects representing the parsed Markdown content and versioning metadata
    /// @throws MojoExecutionException if an I/O error occurs while accessing the `.versioning` directory or its contents, or if there is an error in parsing the Markdown files
    protected final List<VersionMarkdown> getVersionMarkdowns() throws MojoExecutionException {
        Log log = getLog();
        Path versioningFolder = getVersioningFolder();
        if (!Files.exists(versioningFolder)) {
            log.warn("No versioning files found in %s as folder does not exists".formatted(versioningFolder));
            return List.of();
        }

        List<VersionMarkdown> versionMarkdowns;
        try (Stream<Path> markdownFileStream = Files.walk(versioningFolder, 1)) {
            List<Path> markdownFiles = markdownFileStream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".md"))
                    .toList();
            List<VersionMarkdown> parsedMarkdowns = new ArrayList<>();
            for (Path markdownFile : markdownFiles) {
                parsedMarkdowns.add(MarkdownUtils.readVersionMarkdown(log, markdownFile));
            }
            versionMarkdowns = List.copyOf(parsedMarkdowns);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to read versioning folder", e);
        }
        return versionMarkdowns;
    }

    /// Determines and retrieves the path to the versioning folder used for storing version-related files.
    /// If the `versionDirectory` field is configured as an absolute path, it is returned as-is.
    /// Otherwise, a relative path is resolved against the current Maven execution's root directory.
    ///
    /// @return the path to the versioning folder as a [Path] object
    protected Path getVersioningFolder() {
        Path versioningFolder;
        if (versionDirectory.isAbsolute()) {
            versioningFolder = versionDirectory;
        } else {
            versioningFolder = Path.of(session.getExecutionRootDirectory()).resolve(versionDirectory);
        }
        return versioningFolder;
    }

    /// Creates a MarkdownMapping instance based on a list of [VersionMarkdown] objects.
    ///
    /// This method processes a list of [VersionMarkdown] entries to generate a mapping
    /// between Maven artifacts and their respective semantic version bumps.
    ///
    /// @param versionMarkdowns the list of [VersionMarkdown] objects representing version updates; must not be null
    /// @return a MarkdownMapping instance encapsulating the calculated semantic version bumps and an empty Markdown map
    protected MarkdownMapping getMarkdownMapping(List<VersionMarkdown> versionMarkdowns) {
        Map<MavenArtifact, SemanticVersionBump> versionBumpMap = versionMarkdowns.stream()
                .map(VersionMarkdown::bumps)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Utils.groupingByImmutable(
                        Map.Entry::getKey,
                        Collectors.reducing(SemanticVersionBump.NONE, Map.Entry::getValue, SemanticVersionBump::max)
                ));
        Map<MavenArtifact, List<VersionMarkdown>> markdownMap = versionMarkdowns.stream()
                .<Map.Entry<MavenArtifact, VersionMarkdown>>mapMulti((item, consumer) -> {
                    for (MavenArtifact artifact : item.bumps().keySet()) {
                        consumer.accept(Map.entry(artifact, item));
                    }
                })
                .collect(Utils.groupingByImmutable(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Utils.asImmutableList())
                ));
        return new MarkdownMapping(versionBumpMap, markdownMap);
    }

    /// Validates that the artifacts defined in the MarkdownMapping are present within the scope of the Maven project
    /// execution.
    ///
    /// This method compares the artifacts in the provided MarkdownMapping against the artifacts derived from the Maven
    /// projects currently in scope.
    /// If any artifacts in the MarkdownMapping are not present in the project scope,
    /// a [MojoFailureException] is thrown.
    ///
    /// @param markdownMapping the MarkdownMapping object containing the artifacts and their corresponding semantic version bumps; must not be null
    /// @throws MojoFailureException if any artifacts in the MarkdownMapping are not part of the current Maven project scope
    protected void validateMarkdowns(MarkdownMapping markdownMapping) throws MojoFailureException {
        Set<MavenArtifact> artifactsInMarkdown = markdownMapping.versionBumpMap().keySet();
        Stream<MavenProject> projectsInScope = getProjectsInScope();
        Set<MavenArtifact> artifacts = projectsInScope.map(project -> new MavenArtifact(project.getGroupId(), project.getArtifactId()))
                .collect(Utils.asImmutableSet());

        if (!artifacts.containsAll(artifactsInMarkdown)) {
            String unknownArtifacts = artifactsInMarkdown.stream()
                    .filter(Predicate.not(artifacts::contains))
                    .map(MavenArtifact::toString)
                    .collect(Collectors.joining(", "));

            throw new MojoFailureException(
                    "The following artifacts in the Markdown files are not present in the project scope: %s".formatted(
                            unknownArtifacts
                    )
            );
        }
    }

    /// Retrieves a stream of Maven projects that are within the current execution scope.
    /// The scope varies based on the value of the field `modus`:
    /// - [Modus#PROJECT_VERSION]: Returns all projects in the session, sorted topologically.
    /// - [Modus#REVISION_PROPERTY]: Returns only the current project in the session.
    /// - [Modus#PROJECT_VERSION_ONLY_LEAFS]: Returns only leaf projects in the session, sorted topologically.
    ///
    /// @return a [Stream] of [MavenProject] objects representing the projects within the defined execution scope
    protected Stream<MavenProject> getProjectsInScope() {
        return switch (modus) {
            case PROJECT_VERSION -> session.getResult()
                    .getTopologicallySortedProjects()
                    .stream();
            case REVISION_PROPERTY -> Stream.of(session.getCurrentProject());
            case PROJECT_VERSION_ONLY_LEAFS -> session.getResult()
                    .getTopologicallySortedProjects()
                    .stream()
                    .filter(Utils.mavenProjectHasNoModules());
        };
    }

    /// Writes a changelog to a Markdown file.
    /// If the dry-run mode is enabled, the method simulates the writing operation
    /// and logs the result instead of physically creating or modifying the file.
    /// Otherwise, it directly writes to the specified Markdown file,
    /// potentially backing up the previous file if required.
    ///
    /// @param markdownNode the [Node] representing the changelog content to write; must not be null
    /// @param markdownFile the [Path] representing the target Markdown file; must not be null
    /// @throws MojoExecutionException if an unexpected error occurs during execution, such as an I/O issue
    /// @throws MojoFailureException   if the writing operation fails due to known issues or invalid configuration
    protected void writeMarkdownFile(Node markdownNode, Path markdownFile)
            throws MojoExecutionException, MojoFailureException {
        if (dryRun) {
            dryRunWriteFile(
                    writer -> MarkdownUtils.writeMarkdown(writer, markdownNode),
                    markdownFile, "Dry-run: new markdown file at %s:%n%s"
            );
        } else {
            MarkdownUtils.writeMarkdownFile(markdownFile, markdownNode, backupFiles);
        }
        stashFiles(List.of(markdownFile));
    }

    /// Simulates writing to a file by using a [StringWriter].
    /// The provided consumer is responsible for writing content to the [StringWriter].
    /// Logs the specified logLine upon successful completion.
    ///
    /// @param consumer the functional interface used to write content to the [StringWriter]
    /// @param file     the file path representing the target file for writing (used for logging)
    /// @param logLine  the log message that will be logged, formatted with the file and written content
    /// @throws MojoExecutionException if an I/O error occurs while attempting to write
    /// @throws MojoFailureException   if any Mojo-related failure occurs during execution
    protected void dryRunWriteFile(MojoThrowingConsumer<StringWriter> consumer, Path file, String logLine)
            throws MojoExecutionException, MojoFailureException {
        try (StringWriter writer = new StringWriter()) {
            consumer.accept(writer);
            getLog().info(logLine.formatted(file, writer));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to open output stream for writing", e);
        }
    }

    /// Stashes the provided list of file paths using Git if stashing is enabled and not in dry-run mode.
    ///
    /// @param files the list of file paths to be stashed
    /// @throws MojoExecutionException if an error occurs during the stashing process
    protected void stashFiles(List<Path> files) throws MojoExecutionException {
        if (git.isStash() && !dryRun) {
            ProcessUtils.gitStashFiles(files);
        }
    }

    /// Commits changes to a Git repository if specific conditions are met.
    /// The commit operation will only be performed when:
    /// - Git commit mode is enabled ([Git#isCommit] returns true)
    /// - Dry-run mode is disabled (dryRun is false)
    ///
    /// @param message The commit message to use for the commit operation.
    /// @throws MojoExecutionException If the commit operation fails.
    protected void commit(String message) throws MojoExecutionException {
        if (git.isCommit() && !dryRun) {
            ProcessUtils.gitCommit(message);
        }
    }

    /// Functional interface that represents an operation that accepts a single input
    /// and can throw [MojoExecutionException] and [MojoFailureException].
    ///
    /// @param <T> the type of the input to the operation
    protected interface MojoThrowingConsumer<T> {

        /// Performs the given operation on the specified input.
        ///
        /// @param t the input parameter on which the operation will be performed
        /// @throws MojoExecutionException if an error occurs during execution
        /// @throws MojoFailureException   if the operation fails
        void accept(T t) throws MojoExecutionException, MojoFailureException;
    }

}
