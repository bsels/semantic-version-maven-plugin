package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MarkdownMapping;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.PlaceHolderWithType;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionChange;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.parameters.VersionBump;
import io.github.bsels.semantic.version.utils.MarkdownUtils;
import io.github.bsels.semantic.version.utils.POMUtils;
import io.github.bsels.semantic.version.utils.ProcessUtils;
import io.github.bsels.semantic.version.utils.Utils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.github.bsels.semantic.version.utils.MarkdownUtils.readMarkdown;

/// The UpdatePomMojo class provides functionality for updating Maven project POM files during a build process.
/// It integrates into the Maven lifecycle as a Mojo and enables version updates, dependency management,
/// and synchronization with supporting Markdown files.
///
/// This class supports the following key functionalities:
/// - Applies semantic versioning to update project versions.
/// - Processes dependencies and updates related files accordingly.
/// - Handles single or multiple Maven projects.
/// - Provides backup capabilities to safeguard original POM files.
/// - Offers dry-run functionality to preview changes without modifying files.
@Mojo(name = "update", aggregator = true, requiresDependencyResolution = ResolutionScope.NONE)
@Execute(phase = LifecyclePhase.NONE)
public final class UpdatePomMojo extends BaseMojo {

    /// Represents the strategy or mechanism for handling version increments or updates during the execution
    /// of the Maven plugin. This parameter defines how the versioning process is managed in the project, whether
    /// it's based on semantic versioning principles or custom file-based mechanisms.
    ///
    /// Configuration:
    /// - `property`: "versioning.bump", allows external configuration via Maven plugin properties.
    /// - `required`: This parameter is mandatory and must be explicitly defined during plugin execution.
    /// - `defaultValue`: Defaults to `FILE_BASED`, where version determination relies on file-based mechanisms.
    ///
    /// Supported Strategies:
    /// - [VersionBump#FILE_BASED]: Determines version information or increments based on file-based mechanisms,
    ///   such as reading specific configuration or version files.
    /// - [VersionBump#MAJOR]: Represents an increment to the major version component,
    ///   used for changes that break backward compatibility.
    /// - [VersionBump#MINOR]: Represents an increment to the minor version component,
    ///   used for adding new backward-compatible features.
    /// - [VersionBump#PATCH]: Represents an increment to the patch version component,
    ///   used for backward-compatible bug fixes.
    @Parameter(property = "versioning.bump", required = true, defaultValue = "FILE_BASED")
    VersionBump versionBump = VersionBump.FILE_BASED;

    /// Represents the commit message template used during version updates.
    /// This variable is essential for customizing the commit message applied when updating project versions.
    ///
    /// The placeholder `"{numberOfProjects}"` is used to dynamically insert the number of project versions updated into
    /// the message.
    ///
    /// Attributes:
    /// - property: Specifies the configuration property key to override this value.
    /// - required: Signifies that this parameter is mandatory.
    /// - defaultValue: If not explicitly specified,
    ///   defaults to `"Updated {numberOfProjects} project version(s)[skip ci]"`.
    @Parameter(
            property = "versioning.commit.message.update",
            required = true,
            defaultValue = "Updated {numberOfProjects} project version(s) [skip ci]"
    )
    String commitMessage = "Updated {numberOfProjects} project version(s) [skip ci]";

    /// Specifies the scripts or paths to the scripts used in versioning updates.
    /// These scripts can be used to manage or modify version-related data or configuration during the build process in
    /// the working directory of each processed module.
    /// The scripts will be executed in the order they are specified.
    ///
    /// The scripts will be called with the following environment variables:
    /// - `CURRENT_VERSION`: The current version of the project.
    /// - `NEW_VERSION`: The new version of the project after the update.
    /// - `DRY_RUN`: A flag indicating whether the script is being executed in dry-run mode (true) or not (false).
    /// - `GIT_STASH`: A flag indicating whether the script should stash the files or not (true) or not (false).
    /// - `EXECUTION_DATE`: The date and time when the script was executed formatted as ISO 8601: `YYYY-MM-DD`.
    ///
    /// The scripts should be separated by the OS file path separator
    ///
    /// This parameter is optional.
    @Parameter(property = "versioning.update.scripts", required = false)
    String scripts;

    /// Specifies the header format for the version file.
    /// The header is used to define the versioning structure within the file,
    /// including placeholders that can be dynamically replaced.
    ///
    /// The default format includes the version number and the current date.
    /// Placeholders like `{version}` and `{date#YYYY-MM-DD}` are used for injecting the version
    /// and date details respectively.
    ///
    /// - `{version}`: Represents the version of the application or component.
    /// - `{date#YYYY-MM-DD}`: Represents the current date in the specified format
    ///   (the date format is processed by the [DateTimeFormatter]).
    ///
    /// This property is mandatory and must be defined for versioning tasks.
    @Parameter(property = "versioning.version.header", required = true, defaultValue = "{version} - {date#YYYY-MM-DD}")
    String versionHeader = "{version} - {date#YYYY-MM-DD}";

    /// A list that holds file system paths pointing to script files.
    /// Will be derived on execution from the `scripts` parameter.
    /// Each path represented by this list is of type [Path], allowing interaction with the file system.
    private List<Path> scriptPaths = List.of();

    /// Default constructor for the UpdatePomMojo class.
    ///
    /// Initializes an instance of the UpdatePomMojo class by invoking the superclass constructor.
    /// This class is responsible for handling version updates in Maven POM files during the build process.
    ///
    /// Key Responsibilities of UpdatePomMojo:
    /// - Determines the type of semantic version bump to apply.
    /// - Updates Maven POM version information based on the configured parameters.
    /// - Supports dry-run for reviewing changes without making actual file updates.
    /// - Provides backup functionality to preserve the original POM file before modifications.
    ///
    /// Intended to be used within the Maven build lifecycle by plugins requiring POM version updates.
    public UpdatePomMojo() {
        super();
    }

    /// Executes the core logic of the Mojo.
    ///
    /// This method performs the following steps:
    /// 1. Retrieves the logger instance for logging operations.
    /// 2. Fetches and processes Markdown version information.
    /// 3. Validates the provided Markdown mappings to ensure correctness.
    /// 4. Collects Maven projects that are within the scope for processing.
    /// 5. Based on the number of scoped projects:
    ///    - Logs a message if no projects are found.
    ///    - Handles processing for a single project if only one is found.
    ///    - Handles processing for multiple projects if more than one is found.
    /// 6. If any project is updated based on the files, the version Markdown files are deleted.
    ///
    /// @throws MojoExecutionException if an unexpected problem occurs during execution. This is typically a critical error that causes the Mojo to fail.
    /// @throws MojoFailureException   if a failure condition specific to the plugin occurs. This indicates a detected issue that halts further execution.
    @Override
    public void internalExecute() throws MojoExecutionException, MojoFailureException {
        commitMessage = Utils.prepareFormatString(
                commitMessage,
                List.of(new PlaceHolderWithType("numberOfProjects", "d"))
        );
        scriptPaths = Optional.ofNullable(scripts)
                .map(s -> s.split(File.pathSeparator))
                .stream()
                .flatMap(Arrays::stream)
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .toList();

        Log log = getLog();
        List<VersionMarkdown> versionMarkdowns = getVersionMarkdowns();
        MarkdownMapping mapping = getMarkdownMapping(versionMarkdowns);
        validateMarkdowns(mapping);

        List<MavenProject> projectsInScope = getProjectsInScope()
                .collect(Utils.asImmutableList());

        int changedProjects;
        if (projectsInScope.isEmpty()) {
            log.warn("No projects found in scope");
            changedProjects = 0;
        } else if (projectsInScope.size() == 1) {
            log.info("Single project in scope");
            changedProjects = handleSingleProject(mapping, projectsInScope.get(0));
        } else {
            log.info("Multiple projects in scope");
            changedProjects = handleMultiProjects(mapping, projectsInScope);
        }

        if (!dryRun && changedProjects > 0 && VersionBump.FILE_BASED.equals(versionBump)) {
            List<Path> paths = versionMarkdowns.stream()
                    .map(VersionMarkdown::path)
                    .filter(Objects::nonNull)
                    .toList();
            Utils.deleteFilesIfExists(paths);
            stashFiles(paths);
        }
        commit(commitMessage.formatted(changedProjects));
    }

    /// Handles the processing of a single Maven project by determining the semantic version bump,
    /// updating the project's version, and synchronizing the changes with a Markdown file.
    ///
    /// @param markdownMapping the mapping that contains the version bump map and Markdown file details
    /// @param project         the Maven project to be processed
    /// @return `1` if a version update was performed, `0` otherwise.
    /// @throws MojoExecutionException if an error occurs during processing the project's POM file
    /// @throws MojoFailureException   if a failure occurs due to semantic version bump or other operations
    private int handleSingleProject(MarkdownMapping markdownMapping, MavenProject project)
            throws MojoExecutionException, MojoFailureException {
        Path pom = project.getFile()
                .toPath();
        MavenArtifact artifact = new MavenArtifact(project.getGroupId(), project.getArtifactId());

        Document document = POMUtils.readPom(pom);

        SemanticVersionBump semanticVersionBump = getSemanticVersionBump(artifact, markdownMapping.versionBumpMap());
        Optional<VersionChange> version = updateProjectVersion(semanticVersionBump, document);
        if (version.isPresent()) {
            String newVersion = version.get()
                    .newVersion();

            writeUpdatedPom(document, pom);
            updateMarkdownFile(markdownMapping, artifact, pom, newVersion);
            executeScripts(pom.getParent(), version.get());
        }
        return version.isPresent() ? 1 : 0;
    }

    /// Handles multiple Maven projects by processing their POM files, dependencies, and versions, updating the projects as necessary.
    ///
    /// @param markdownMapping an instance of [MarkdownMapping] that contains mapping details for Markdown processing.
    /// @param projects        a list of [MavenProject] objects, representing the Maven projects to be processed.
    /// @return the number of projects that were updated based on the version changes.
    /// @throws MojoExecutionException if there's an execution error while handling the projects.
    /// @throws MojoFailureException   if a failure is encountered during the processing of the projects.
    private int handleMultiProjects(MarkdownMapping markdownMapping, List<MavenProject> projects)
            throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        Map<MavenArtifact, MavenProjectAndDocument> documents = readAllPoms(projects);
        Set<MavenArtifact> reactorArtifacts = documents.keySet();
        log.info("Found %d projects in scope".formatted(documents.size()));

        Map<MavenArtifact, List<Node>> updatableDependencies = mergeUpdatableDependencies(
                documents.values(),
                reactorArtifacts
        );
        Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifacts = createDependencyToProjectArtifactMapping(
                documents.values(),
                reactorArtifacts
        );

        UpdatedAndToUpdateArtifacts result = processMarkdownVersions(
                markdownMapping,
                reactorArtifacts,
                documents,
                dependencyToProjectArtifacts,
                updatableDependencies
        );

        handleDependencyMavenProjects(
                markdownMapping,
                result,
                documents,
                dependencyToProjectArtifacts,
                updatableDependencies
        );

        writeUpdatedProjects(result.updatedArtifacts(), documents);
        return result.updatedArtifacts().size();
    }

    /// Executes a series of scripts provided in the [#scriptPaths] collection.
    ///
    /// This method iterates over the available script paths
    /// and invokes the `ProcessUtils.executeScripts` method for each script,
    /// passing the required parameters for execution.
    ///
    /// @param projectPath   the base path of the project where scripts will be executed
    /// @param versionChange the object representing the version change details for the script execution
    /// @throws MojoExecutionException if an error occurs during script execution
    private void executeScripts(Path projectPath, VersionChange versionChange) throws MojoExecutionException {
        for (Path scriptPath : scriptPaths) {
            ProcessUtils.executeScripts(
                    scriptPath,
                    projectPath,
                    versionChange,
                    dryRun,
                    git.isStash()
            );
        }
    }

    /// Handles Maven projects and their dependencies to update versions and related metadata.
    /// This method processes dependencies and updates the project versions accordingly,
    /// ensuring that affected dependencies and documentation are updated.
    ///
    /// @param markdownMapping              the mapping of Markdown files for recording version changes
    /// @param result                       the object containing artifacts to be updated and those already updated
    /// @param documents                    a mapping of Maven artifacts to their associated project and document representation
    /// @param dependencyToProjectArtifacts a mapping of Maven artifacts to the list of project artifacts depending on them
    /// @param updatableDependencies        a mapping of Maven artifacts to their corresponding updatable dependency nodes in POM files
    /// @throws MojoExecutionException if an error occurs during the execution of the Maven plugin
    /// @throws MojoFailureException   if any Mojo-related failure occurs during execution
    private void handleDependencyMavenProjects(
            MarkdownMapping markdownMapping,
            UpdatedAndToUpdateArtifacts result,
            Map<MavenArtifact, MavenProjectAndDocument> documents,
            Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifacts,
            Map<MavenArtifact, List<Node>> updatableDependencies
    ) throws MojoExecutionException, MojoFailureException {
        Set<MavenArtifact> updatedArtifacts = result.updatedArtifacts();
        Queue<MavenArtifact> toBeUpdated = result.toBeUpdated();
        while (!toBeUpdated.isEmpty()) {
            MavenArtifact artifact = toBeUpdated.poll();
            toBeUpdated.remove(artifact);
            updatedArtifacts.add(artifact);

            MavenProjectAndDocument mavenProjectAndDocument = documents.get(artifact);
            VersionChange change = updateProjectVersion(
                    SemanticVersionBump.PATCH,
                    mavenProjectAndDocument.document()
            ).orElseThrow();

            dependencyToProjectArtifacts.getOrDefault(artifact, List.of())
                    .stream()
                    .filter(Predicate.not(updatedArtifacts::contains))
                    .forEach(toBeUpdated::offer);

            updateMarkdownFile(markdownMapping, artifact, mavenProjectAndDocument.pomFile(), change.newVersion());
            executeScripts(mavenProjectAndDocument.pomFile().getParent(), change);

            updatableDependencies.getOrDefault(artifact, List.of())
                    .forEach(node -> POMUtils.updateVersionNodeIfOldVersionMatches(change, node));
        }
    }

    /// Processes the Markdown versions for the provided Maven artifacts and updates the required dependencies,
    /// Markdown files, and version nodes as needed.
    ///
    /// @param markdownMapping              the mapping containing information about the Markdown files and version bump rules
    /// @param reactorArtifacts             the set of Maven artifacts that are part of the current reactor build
    /// @param documents                    a mapping of Maven artifacts to their corresponding Maven project and document
    /// @param dependencyToProjectArtifacts a mapping of Maven artifacts to lists of dependent project artifacts
    /// @param updatableDependencies        a mapping of Maven artifacts to lists of dependencies in the form of XML nodes that can be updated in the POM files
    /// @return an object containing the set of updated artifacts and the queue of artifacts to be updated
    /// @throws MojoExecutionException if there is an error during version processing or Markdown update
    /// @throws MojoFailureException   if any Mojo-related failure occurs during execution
    private UpdatedAndToUpdateArtifacts processMarkdownVersions(
            MarkdownMapping markdownMapping,
            Set<MavenArtifact> reactorArtifacts,
            Map<MavenArtifact, MavenProjectAndDocument> documents,
            Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifacts,
            Map<MavenArtifact, List<Node>> updatableDependencies
    ) throws MojoExecutionException, MojoFailureException {
        Set<MavenArtifact> updatedArtifacts = new HashSet<>();
        Queue<MavenArtifact> toBeUpdated = new ArrayDeque<>(reactorArtifacts.size());
        for (MavenArtifact artifact : reactorArtifacts) {
            SemanticVersionBump bump = getSemanticVersionBump(artifact, markdownMapping.versionBumpMap());
            MavenProjectAndDocument mavenProjectAndDocument = documents.get(artifact);
            Optional<VersionChange> versionChange = updateProjectVersion(bump, mavenProjectAndDocument.document());
            if (versionChange.isPresent()) {
                VersionChange change = versionChange.get();
                updatedArtifacts.add(artifact);

                dependencyToProjectArtifacts.getOrDefault(artifact, List.of())
                        .stream()
                        .filter(Predicate.not(updatedArtifacts::contains))
                        .forEach(toBeUpdated::offer);

                updateMarkdownFile(markdownMapping, artifact, mavenProjectAndDocument.pomFile(), change.newVersion());

                updatableDependencies.getOrDefault(artifact, List.of())
                        .forEach(node -> POMUtils.updateVersionNodeIfOldVersionMatches(change, node));
                executeScripts(mavenProjectAndDocument.pomFile().getParent(), change);
            }
        }
        return new UpdatedAndToUpdateArtifacts(updatedArtifacts, toBeUpdated);
    }

    /// Updates the Maven projects based on the provided set of updated artifacts and their associated
    /// Maven project documents.
    ///
    /// @param updatedArtifacts a set of Maven artifacts that have been updated and need their projects to be modified
    /// @param documents        a map that associates Maven artifacts with their corresponding Maven project and document details
    /// @throws MojoExecutionException if an error occurs during the project update process
    /// @throws MojoFailureException   if the update process fails due to a misconfiguration or other failure
    private void writeUpdatedProjects(
            Set<MavenArtifact> updatedArtifacts,
            Map<MavenArtifact, MavenProjectAndDocument> documents
    ) throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        for (MavenArtifact artifact : updatedArtifacts) {
            log.debug("Updating project %s".formatted(artifact));
            MavenProjectAndDocument mavenProjectAndDocument = documents.get(artifact);
            Path pomFile = mavenProjectAndDocument.pomFile();
            writeUpdatedPom(mavenProjectAndDocument.document(), pomFile);
        }
    }

    /// Reads and processes the POM files for a list of Maven projects
    /// and returns a mapping of Maven artifacts to their corresponding project and document representations.
    ///
    /// @param projects the list of Maven projects whose POMs need to be read
    /// @return an immutable map where the key is the Maven artifact representing a project and the value is its associated Maven project and document representation
    /// @throws MojoExecutionException if an error occurs while executing the Mojo
    /// @throws MojoFailureException   if the Mojo fails due to an expected problem
    private Map<MavenArtifact, MavenProjectAndDocument> readAllPoms(List<MavenProject> projects)
            throws MojoExecutionException, MojoFailureException {
        Map<MavenArtifact, MavenProjectAndDocument> documents = new HashMap<>();
        for (MavenProject project : projects) {
            MavenArtifact mavenArtifact = new MavenArtifact(project.getGroupId(), project.getArtifactId());
            Path pomFile = project.getFile().toPath();
            MavenProjectAndDocument projectAndDocument = new MavenProjectAndDocument(
                    mavenArtifact,
                    pomFile,
                    POMUtils.readPom(pomFile)
            );
            documents.put(mavenArtifact, projectAndDocument);
        }
        return Map.copyOf(documents);
    }

    /// Updates the project version based on the specified semantic version bump and document.
    /// If no version update is required, an empty [Optional] is returned.
    ///
    /// @param semanticVersionBump the type of semantic version change to apply (e.g., major, minor, patch)
    /// @param document            the XML document representing the project's POM file
    /// @return an [Optional] containing a [VersionChange] object representing the original and updated version, or an empty [Optional] if no update was performed
    /// @throws MojoExecutionException if an error occurs while updating the version
    private Optional<VersionChange> updateProjectVersion(
            SemanticVersionBump semanticVersionBump,
            Document document
    ) throws MojoExecutionException {
        Log log = getLog();
        Node versionNode = POMUtils.getProjectVersionNode(document, modus);
        String originalVersion = versionNode.getTextContent();

        log.info("Updating version with a %s semantic version".formatted(semanticVersionBump));
        if (SemanticVersionBump.NONE.equals(semanticVersionBump)) {
            log.info("No version update required");
            return Optional.empty();
        }
        POMUtils.updateVersion(versionNode, semanticVersionBump);
        return Optional.of(new VersionChange(originalVersion, versionNode.getTextContent()));
    }

    /// Creates a mapping between dependency artifacts and project artifacts based on the provided
    /// Maven project documents and reactor artifacts.
    /// The method identifies dependencies in the projects that match artifacts in the reactor and associates
    /// them with their corresponding project artifacts.
    ///
    /// @param documents        a collection of [MavenProjectAndDocument] representing the Maven projects and their associated model documents.
    /// @param reactorArtifacts a set of [MavenArtifact] objects representing the artifacts present in the reactor.
    /// @return a map where keys are dependency artifacts (from the reactor) and values are lists of project artifacts they are associated with.
    private Map<MavenArtifact, List<MavenArtifact>> createDependencyToProjectArtifactMapping(
            Collection<MavenProjectAndDocument> documents,
            Set<MavenArtifact> reactorArtifacts
    ) {
        return documents.stream()
                .flatMap(
                        projectAndDocument -> POMUtils.getMavenArtifacts(projectAndDocument.document())
                                .keySet()
                                .stream()
                                .filter(reactorArtifacts::contains)
                                .map(artifact -> Map.entry(artifact, projectAndDocument.artifact()))
                )
                .collect(Utils.groupingByImmutable(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Utils.asImmutableList())
                ));
    }

    /// Merges updatable dependencies from a list of Maven project documents and a set of reactor artifacts.
    /// Filters and groups Maven artifacts and associated information based on the given reactor artifacts.
    ///
    /// @param documents        a collection of [MavenProjectAndDocument] objects representing the Maven projects and their associated documents
    /// @param reactorArtifacts a set of [MavenArtifact] objects representing the reactor build artifacts to be processed
    /// @return a map where keys are [MavenArtifact] objects and values are immutable lists of dependency nodes associated with those artifacts
    private Map<MavenArtifact, List<Node>> mergeUpdatableDependencies(
            Collection<MavenProjectAndDocument> documents,
            Set<MavenArtifact> reactorArtifacts
    ) {
        return documents.stream()
                .map(MavenProjectAndDocument::document)
                .map(POMUtils::getMavenArtifacts)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .filter(entry -> reactorArtifacts.contains(entry.getKey()))
                .collect(Utils.groupingByImmutable(
                        Map.Entry::getKey,
                        Collectors.mapping(
                                Map.Entry::getValue,
                                Utils.asImmutableList(Collectors.reducing(
                                        new ArrayList<>(),
                                        Utils.consumerToOperator(List::addAll)
                                ))
                        )
                ));
    }

    /// Writes the updated Maven POM file. This method either writes the updated POM to the specified path or performs a dry-run
    /// where the updated POM content is logged for review without making any file changes.
    ///
    /// If dry-run mode is enabled, the new POM content is created as a string and logged. Otherwise, the updated POM is
    /// written to the provided file path, with an option to back up the original file before overwriting.
    ///
    /// @param document the XML Document representation of the Maven POM file to be updated
    /// @param pom      the path to the POM file where the updated content will be written
    /// @throws MojoExecutionException if an I/O error occurs while writing the updated POM or processing the dry-run
    /// @throws MojoFailureException   if the operation fails due to an XML parsing or writing error
    private void writeUpdatedPom(Document document, Path pom) throws MojoExecutionException, MojoFailureException {
        if (dryRun) {
            dryRunWriteFile(writer -> POMUtils.writePom(document, writer), pom, "Dry-run: new pom at %s:%n%s");
        } else {
            POMUtils.writePom(document, pom, backupFiles);
        }
        stashFiles(List.of(pom));
    }

    /// Updates the Markdown file by reading the current changelog, merging version-specific Markdown changes,
    /// and writing the updated changelog to the file system.
    ///
    /// @param markdownMapping the mapping between Maven artifacts and their associated Markdown changes
    /// @param projectArtifact the Maven artifact representing the project for which the Markdown file is being updated
    /// @param pom             the path to the pom.xml file, used as a reference to locate the Markdown file
    /// @param newVersion      the version information to be updated in the Markdown file
    /// @throws MojoExecutionException if an error occurs during the update process
    /// @throws MojoFailureException   if any Mojo-related failure occurs during execution
    private void updateMarkdownFile(
            MarkdownMapping markdownMapping,
            MavenArtifact projectArtifact,
            Path pom,
            String newVersion
    ) throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        Path changelogFile = pom.getParent().resolve(CHANGELOG_MD);
        org.commonmark.node.Node changelog = readMarkdown(log, changelogFile);
        log.debug("Original changelog");
        MarkdownUtils.printMarkdown(log, changelog, 0);
        MarkdownUtils.mergeVersionMarkdownsInChangelog(
                changelog,
                newVersion,
                versionHeader,
                markdownMapping.markdownMap()
                        .getOrDefault(
                                projectArtifact,
                                List.of(MarkdownUtils.createSimpleVersionBumpDocument(projectArtifact))
                        )
                        .stream()
                        .collect(Utils.groupingByImmutable(
                                entry -> entry.bumps().get(projectArtifact),
                                Collectors.mapping(VersionMarkdown::content, Utils.asImmutableList())
                        ))
        );
        log.debug("Updated changelog");
        MarkdownUtils.printMarkdown(log, changelog, 0);

        writeUpdatedChangelog(changelog, changelogFile);
    }

    /// Writes the updated changelog to the specified changelog file.
    /// If the dry-run mode is enabled, the updated changelog is logged instead of being written to the file.
    /// Otherwise, the changelog is saved to the specified path, with an optional backup of the existing file.
    ///
    /// @param changelog     the commonmark node representing the updated changelog content to be written
    /// @param changelogFile the path to the file where the updated changelog should be saved
    /// @throws MojoExecutionException if an I/O error occurs during writing the changelog
    /// @throws MojoFailureException   if any Mojo-related failure occurs during execution
    private void writeUpdatedChangelog(org.commonmark.node.Node changelog, Path changelogFile)
            throws MojoExecutionException, MojoFailureException {
        writeMarkdownFile(changelog, changelogFile);
    }

    /// Determines the semantic version bump for a given Maven artifact based on the provided map of version bumps
    /// and the current version bump configuration.
    ///
    /// @param artifact the Maven artifact for which the semantic version bump is to be determined
    /// @param bumps    a map containing Maven artifacts as keys and their corresponding semantic version bumping as values
    /// @return the semantic version bump that should be applied to the given artifact
    private SemanticVersionBump getSemanticVersionBump(
            MavenArtifact artifact,
            Map<MavenArtifact, SemanticVersionBump> bumps
    ) {
        return switch (versionBump) {
            case FILE_BASED -> bumps.getOrDefault(artifact, SemanticVersionBump.NONE);
            case MAJOR -> SemanticVersionBump.MAJOR;
            case MINOR -> SemanticVersionBump.MINOR;
            case PATCH -> SemanticVersionBump.PATCH;
        };
    }

    /// Represents a combination of a Maven project artifact, its associated POM file path,
    /// and the XML document of the POM file's contents.
    ///
    /// This class is designed as a record to provide an immutable data container for
    /// conveniently managing and accessing Maven project-related information.
    ///
    /// @param artifact the Maven artifact associated with the project; must not be null
    /// @param pomFile  the path to the POM file for the project; must not be null
    /// @param document the XML document representing the POM file's contents; must not be null
    private record MavenProjectAndDocument(MavenArtifact artifact, Path pomFile, Document document) {

        /// Constructs a new instance of the MavenProjectAndDocument record.
        ///
        /// @param artifact the Maven artifact associated with the project; must not be null
        /// @param pomFile  the path to the POM file for the project; must not be null
        /// @param document the XML document representing the POM file's contents; must not be null
        /// @throws NullPointerException if any of the provided parameters are null
        private MavenProjectAndDocument {
            Objects.requireNonNull(artifact, "`artifact` must not be null");
            Objects.requireNonNull(pomFile, "`pomFile` must not be null");
            Objects.requireNonNull(document, "`document` must not be null");
        }
    }

    /// Represents a data structure that holds a set of updated Maven artifacts
    /// and a queue of Maven artifacts to be updated.
    ///
    /// This class is immutable and ensures non-null constraints on the provided parameters.
    ///
    /// @param updatedArtifacts a set of [MavenArtifact] instances that represent the artifacts already updated
    /// @param toBeUpdated      a queue of [MavenArtifact] instances representing the artifacts yet to be updated
    private record UpdatedAndToUpdateArtifacts(Set<MavenArtifact> updatedArtifacts, Queue<MavenArtifact> toBeUpdated) {

        /// Constructs an instance of UpdatedAndToUpdateArtifacts, ensuring the provided parameters are not null.
        ///
        /// @param updatedArtifacts a set of [MavenArtifact] objects that have been updated; must not be null
        /// @param toBeUpdated      a queue of [MavenArtifact] objects that are yet to be updated; must not be null
        private UpdatedAndToUpdateArtifacts {
            Objects.requireNonNull(updatedArtifacts, "`updatedArtifacts` must not be null");
            Objects.requireNonNull(toBeUpdated, "`toBeUpdated` must not be null");
        }
    }
}
