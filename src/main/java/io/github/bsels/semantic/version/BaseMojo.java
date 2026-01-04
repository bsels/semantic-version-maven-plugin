package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.parameters.Modus;
import io.github.bsels.semantic.version.utils.MarkdownUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
public abstract sealed class BaseMojo extends AbstractMojo permits UpdatePomMojo {

    /// Represents the base directory of the Maven project. This directory is resolved to the "basedir"
    /// property of the Maven build, typically corresponding to the root directory containing the
    /// `pom.xml` file.
    /// This variable is used as a reference point for resolving relative paths in the build process
    /// and is essential for various plugin operations.
    /// The value is immutable during execution and must be provided as it is a required parameter.
    /// Configuration:
    /// - `readonly`: Ensures the value remains constant throughout the execution.
    /// - `required`: Denotes that this parameter must be set.
    /// - `defaultValue`: Defaults to Maven's `${basedir}` property, which refers to the root project directory.
    @Parameter(readonly = true, required = true, defaultValue = "${basedir}")
    protected Path baseDirectory;

    /// Represents the mode in which project versioning is handled within the Maven plugin.
    /// This parameter is used to define the strategy for managing version numbers across single or multi-module projects.
    ///
    /// Configuration:
    /// - `property`: "versioning.modus", allows external configuration via Maven plugin properties.
    /// - `required`: This parameter is mandatory and must be explicitly defined during plugin execution.
    /// - `defaultValue`: Defaults to `SINGLE_PROJECT_VERSION` mode, where versioning is executed for a single project.
    ///
    /// Supported Modes:
    /// - [Modus#SINGLE_PROJECT_VERSION]: Handles versioning for a single project.
    /// - [Modus#REVISION_PROPERTY]: Handles versioning for projects using the revision property.
    /// - [Modus#MULTI_PROJECT_VERSION]: Handles versioning across multiple projects (including intermediary projects).
    /// - [Modus#MULTI_PROJECT_VERSION_ONLY_LEAFS]: Handles versioning for leaf projects in multi-module setups.
    @Parameter(property = "versioning.modus", required = true, defaultValue = "SINGLE_PROJECT_VERSION")
    protected Modus modus = Modus.SINGLE_PROJECT_VERSION;

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

    /// Determines whether the plugin should execute its logic for subprojects in a multi-module Maven project.
    ///
    /// Configuration:
    /// - `property`: "versioning.executeForSubProject", allows external configuration via Maven plugin properties.
    /// - `defaultValue`: Defaults to `false`, meaning the plugin will skip its execution for subprojects
    ///   unless explicitly enabled.
    ///
    /// When set to `true`, the plugin will apply its logic to subprojects as well as the root project.
    /// When set to `false`, it will only apply its logic to the root project.
    ///
    /// This parameter is useful in scenarios where selective execution of versioning logic is desired within a
    /// multi-module project hierarchy.
    @Parameter(property = "versioning.executeForSubproject", defaultValue = "false")
    protected boolean executeForSubproject = false;

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
        List<MavenProject> topologicallySortedProjects = session.getResult()
                .getTopologicallySortedProjects();
        MavenProject rootProject = topologicallySortedProjects
                .get(0);
        MavenProject currentProject = session.getCurrentProject();
        if (!rootProject.equals(currentProject) && !executeForSubproject) {
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

    /// Reads all Markdown files from the `.versioning` directory within the base directory,
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
        Path versioningFolder = baseDirectory.resolve(".versioning");
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
}
