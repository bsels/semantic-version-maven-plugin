package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MarkdownMapping;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.parameters.VersionBump;
import io.github.bsels.semantic.version.utils.MarkdownUtils;
import io.github.bsels.semantic.version.utils.POMUtils;
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

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.github.bsels.semantic.version.utils.MarkdownUtils.readMarkdown;

@Mojo(name = "update", requiresDependencyResolution = ResolutionScope.RUNTIME)
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

    /// Indicates whether the original POM file and CHANGELOG file should be backed up before modifying its content.
    ///
    /// This parameter is configurable via the Maven property `versioning.backup`.
    /// When set to `true`, a backup of the POM/CHANGELOG file will be created before any updates are applied.
    /// The default value for this parameter is `false`, meaning no backup will be created unless explicitly specified.
    @Parameter(property = "versioning.backup", defaultValue = "false")
    boolean backupFiles = false;

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

    /// Executes the main logic for updating Maven POM versions based on the configured update mode.
    ///
    /// This method handles different modes of version updates, including
    /// - Updating the revision property on the root project.
    /// - Updating the project version for all Maven projects.
    /// - Updating only the versions of leaf projects without modules.
    ///
    /// The method processes version updates by creating a MarkdownMapping instance, determining
    /// the projects to update, and invoking the appropriate update mechanism based on the selected mode.
    ///
    /// Upon successful execution, logs the outcome, indicating whether any changes were made.
    ///
    /// @throws MojoExecutionException if an error occurs during the execution process.
    /// @throws MojoFailureException   if a failure occurs during the version update process.
    @Override
    public void internalExecute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        List<VersionMarkdown> versionMarkdowns = getVersionMarkdowns();
        MarkdownMapping mapping = getMarkdownMapping(versionMarkdowns);

        boolean hasChanges = switch (modus) {
            case PROJECT_VERSION -> handleProjectVersionUpdates(mapping, Utils.alwaysTrue());
            case REVISION_PROPERTY -> handleSingleVersionUpdate(mapping, session.getCurrentProject());
            case PROJECT_VERSION_ONLY_LEAFS -> handleProjectVersionUpdates(mapping, Utils.mavenProjectHasNoModules());
        };
        if (hasChanges) {
            log.info("Version update completed successfully");
        } else {
            log.info("No version updates were performed");
        }
    }

    /// Handles the process of updating project versions for Maven projects filtered based on a specified condition.
    /// This method identifies relevant projects, determines whether a single or multiple version update process
    /// should occur, and executes the appropriate update logic.
    ///
    /// @param markdownMapping a mapping of Maven artifacts to their corresponding semantic version bumps and version-specific markdown entries
    /// @param filter          a predicate used to filter Maven projects that should be updated
    /// @return true if any projects had their versions updated, false otherwise
    /// @throws MojoExecutionException if an error occurs during the execution of version updates
    /// @throws MojoFailureException   if a failure occurs in the version update process
    private boolean handleProjectVersionUpdates(MarkdownMapping markdownMapping, Predicate<MavenProject> filter)
            throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        List<MavenProject> sortedProjects = session.getResult()
                .getTopologicallySortedProjects()
                .stream()
                .filter(filter)
                .toList();

        if (sortedProjects.isEmpty()) {
            log.info("No projects found matching filter");
            return false;
        }
        if (sortedProjects.size() == 1) {
            log.info("Updating version for single project");
            return handleSingleVersionUpdate(markdownMapping, sortedProjects.get(0));
        }
        log.info("Updating version for multiple projects");
        return handleMultiVersionUpdate(markdownMapping, sortedProjects);
    }

    /// Handles the process of performing a single version update within a Maven project.
    ///
    /// This method determines the semantic version increment to apply, updates the project version
    /// in the corresponding POM file, and either performs an actual update or demonstrates the proposed
    /// changes in a dry-run mode.
    ///
    /// Key Operations:
    /// - Resolves the POM file from the base directory.
    /// - Reads the project version node from the POM using the specified update mode.
    /// - Calculates the appropriate semantic version increment to apply.
    /// - Logs the type of semantic version modification being applied.
    /// - Updates the POM version node with the new version.
    /// - Performs a dry-run if enabled, writing the proposed changes to a log instead of modifying the file.
    ///
    /// @param markdownMapping the Markdown version file mappings
    /// @param project         the Maven project for which the version update is being performed
    /// @return `true` if their where changes, `false` otherwise
    /// @throws MojoExecutionException if the POM cannot be read or written, or it cannot update the version node.
    /// @throws MojoFailureException   if the runtime system fails to initial the XML reader and writer helper classes
    private boolean handleSingleVersionUpdate(MarkdownMapping markdownMapping, MavenProject project)
            throws MojoExecutionException, MojoFailureException {
        Path pom = project.getFile()
                .toPath();
        MavenArtifact artifact = new MavenArtifact(project.getGroupId(), project.getArtifactId());

        Document document = POMUtils.readPom(pom);

        SemanticVersionBump semanticVersionBump = getSemanticVersionBumpForSingleProject(markdownMapping, artifact);
        Optional<String> version = updateProjectVersion(semanticVersionBump, document);
        if (version.isEmpty()) {
            return false;
        }

        writeUpdatedPom(document, pom);

        updateMarkdownFile(markdownMapping, artifact, pom, version.get());
        return true;
    }

    /// Determines the semantic version bump for a single Maven project based on the provided Markdown mapping.
    /// Validates that only the specified project artifact is being updated.
    ///
    /// @param markdownMapping the mapping that contains information about version bumps for multiple artifacts
    /// @param projectArtifact the Maven artifact representing the single project whose version bump is to be determined
    /// @return the semantic version bump for the provided project artifact
    /// @throws MojoExecutionException if the version bump map contains artifacts other than the provided project artifact
    private SemanticVersionBump getSemanticVersionBumpForSingleProject(
            MarkdownMapping markdownMapping,
            MavenArtifact projectArtifact
    ) throws MojoExecutionException {
        Map<MavenArtifact, SemanticVersionBump> versionBumpMap = markdownMapping.versionBumpMap();
        if (!Set.of(projectArtifact).equals(versionBumpMap.keySet())) {
            throw new MojoExecutionException(
                    "Single version update expected to update only the project %s, found: %s".formatted(
                            projectArtifact,
                            versionBumpMap.keySet()
                    )
            );
        }
        return versionBumpMap.get(projectArtifact);
    }

    private boolean handleMultiVersionUpdate(MarkdownMapping markdownMapping, List<MavenProject> projects)
            throws MojoExecutionException, MojoFailureException {
        Map<MavenArtifact, MavenProjectAndDocument> documents = new HashMap<>();
        for (MavenProject project : projects) {
            MavenArtifact mavenArtifact = new MavenArtifact(project.getGroupId(), project.getArtifactId());
            documents.put(
                    mavenArtifact,
                    new MavenProjectAndDocument(project, mavenArtifact, POMUtils.readPom(project.getFile().toPath())));
        }
        documents = Map.copyOf(documents);
        Collection<MavenProjectAndDocument> documentsCollection = documents.values();
        Set<MavenArtifact> reactorArtifacts = documentsCollection.stream()
                .map(MavenProjectAndDocument::artifact)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), Set::copyOf));
        Map<MavenArtifact, List<Node>> updatableDependencies = mergeUpdatableDependencies(
                documentsCollection,
                reactorArtifacts
        );
        Map<MavenArtifact, List<MavenArtifact>> dependencyToProjectArtifact = createDependencyToProjectArtifactMapping(
                documentsCollection,
                reactorArtifacts
        );

        Set<MavenArtifact> updatedArtifacts = new HashSet<>();
        for (MavenArtifact artifact : reactorArtifacts) {
            // TODO
        }

        return !updatedArtifacts.isEmpty();
    }

    /// Updates the project version in the provided document by applying the semantic version bump specified in
    /// the Markdown mapping for the given project artifact.
    ///
    /// @param semanticVersionBump the semantic version bump to apply to the project version
    /// @param document            the XML document representing the project's POM
    /// @return an [Optional] containing the updated version string if the version is updated, or an empty [Optional] if no update is required
    /// @throws MojoExecutionException if a semantic version bump cannot be applied, or if the input mapping is invalid for the given project artifact
    private Optional<String> updateProjectVersion(
            SemanticVersionBump semanticVersionBump,
            Document document
    ) throws MojoExecutionException {
        Log log = getLog();
        Node versionNode = POMUtils.getProjectVersionNode(document, modus);

        log.info("Updating version with a %s semantic version".formatted(semanticVersionBump));
        if (SemanticVersionBump.NONE.equals(semanticVersionBump)) {
            log.info("No version update required");
            return Optional.empty();
        }
        try {
            POMUtils.updateVersion(versionNode, semanticVersionBump);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Unable to update version changelog", e);
        }
        return Optional.of(versionNode.getTextContent());
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

    /// Creates a MarkdownMapping instance based on a list of [VersionMarkdown] objects.
    ///
    /// This method processes a list of [VersionMarkdown] entries to generate a mapping
    /// between Maven artifacts and their respective semantic version bumps.
    ///
    /// @param versionMarkdowns the list of [VersionMarkdown] objects representing version updates; must not be null
    /// @return a MarkdownMapping instance encapsulating the calculated semantic version bumps and an empty Markdown map
    private MarkdownMapping getMarkdownMapping(List<VersionMarkdown> versionMarkdowns) {
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
            try (StringWriter writer = new StringWriter()) {
                POMUtils.writePom(document, writer);
                getLog().info("Dry-run: new pom at %s:%n%s".formatted(pom, writer));
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to open output stream for writing", e);
            }
        } else {
            POMUtils.writePom(document, pom, backupFiles);
        }
    }

    /// Updates the Markdown file by reading the current changelog, merging version-specific markdown changes,
    /// and writing the updated changelog to the file system.
    ///
    /// @param markdownMapping the mapping between Maven artifacts and their associated Markdown changes
    /// @param projectArtifact the Maven artifact representing the project for which the Markdown file is being updated
    /// @param pom             the path to the pom.xml file, used as a reference to locate the Markdown file
    /// @param newVersion      the version information to be updated in the Markdown file
    /// @throws MojoExecutionException if an error occurs during the update process
    private void updateMarkdownFile(
            MarkdownMapping markdownMapping,
            MavenArtifact projectArtifact,
            Path pom,
            String newVersion
    ) throws MojoExecutionException {
        Log log = getLog();
        Path changelogFile = pom.getParent().resolve(CHANGELOG_MD);
        org.commonmark.node.Node changelog = readMarkdown(log, changelogFile);
        log.debug("Original changelog");
        MarkdownUtils.printMarkdown(log, changelog, 0);
        MarkdownUtils.mergeVersionMarkdownsInChangelog(
                log,
                changelog,
                newVersion,
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
    private void writeUpdatedChangelog(org.commonmark.node.Node changelog, Path changelogFile)
            throws MojoExecutionException {
        if (dryRun) {
            try (StringWriter writer = new StringWriter()) {
                MarkdownUtils.writeMarkdown(writer, changelog);
                getLog().info("Dry-run: new changelog at %s:%n%s".formatted(changelogFile, writer));
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to open output stream for writing", e);
            }
        } else {
            MarkdownUtils.writeMarkdownFile(changelogFile, changelog, backupFiles && Files.exists(changelogFile));
        }
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

    /// Represents a combination of a MavenProject and its associated Document.
    /// This class is a record that holds a Maven project and corresponding document,
    /// ensuring that both parameters are non-null during instantiation.
    ///
    /// @param project  the Maven project must not be null
    /// @param artifact the Maven artifact must not be null
    /// @param document the associated document must not be null
    private record MavenProjectAndDocument(MavenProject project, MavenArtifact artifact, Document document) {

        /// Constructs an instance of MavenProjectAndDocument with the specified Maven project and document.
        ///
        /// @param project  the Maven project must not be null
        /// @param artifact the Maven artifact must not be null
        /// @param document the associated document must not be null
        /// @throws NullPointerException if the project or document is null
        private MavenProjectAndDocument {
            Objects.requireNonNull(project, "`project` must not be null");
            Objects.requireNonNull(artifact, "`artifact` must not be null");
            Objects.requireNonNull(document, "`document` must not be null");
        }
    }
}
