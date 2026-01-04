package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.MarkdownMapping;
import io.github.bsels.semantic.version.models.MavenArtifact;
import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.parameters.VersionBump;
import io.github.bsels.semantic.version.utils.POMUtils;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /// Indicates whether the original POM file should be backed up before modifying its content.
    ///
    /// This parameter is configurable via the Maven property `versioning.backup`.
    /// When set to `true`, a backup of the POM file will be created before any updates are applied.
    /// The default value for this parameter is `false`, meaning no backup will be created unless explicitly specified.
    @Parameter(property = "versioning.backup", defaultValue = "false")
    boolean backupOldPom = false;

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

    @Override
    public void internalExecute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        List<VersionMarkdown> versionMarkdowns = getVersionMarkdowns();
        MarkdownMapping markdownMapping = getMarkdownMapping(versionMarkdowns);

        switch (modus) {
            case REVISION_PROPERTY, SINGLE_PROJECT_VERSION -> handleSingleVersionUpdate(markdownMapping);
            case MULTI_PROJECT_VERSION ->
                    log.warn("Versioning mode is set to MULTI_PROJECT_VERSION, skipping execution not yet implemented");
            case MULTI_PROJECT_VERSION_ONLY_LEAFS ->
                    log.warn("Versioning mode is set to MULTI_PROJECT_VERSION_ONLY_LEAFS, skipping execution not yet implemented");
        }
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
    /// @param markdownMapping the markdown version file mappings
    /// @throws MojoExecutionException if the POM cannot be read or written, or it cannot update the version node.
    /// @throws MojoFailureException   if the runtime system fails to initial the XML reader and writer helper classes
    private void handleSingleVersionUpdate(MarkdownMapping markdownMapping)
            throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        MavenProject currentProject = session.getCurrentProject();
        Path pom = currentProject
                .getFile()
                .toPath();
        MavenArtifact projectArtifact = new MavenArtifact(currentProject.getGroupId(), currentProject.getArtifactId());

        Document document = POMUtils.readPom(pom);
        Node versionNode = POMUtils.getProjectVersionNode(document, modus);

        Map<MavenArtifact, SemanticVersionBump> versionBumpMap = markdownMapping.versionBumpMap();
        if (!Set.of(projectArtifact).equals(versionBumpMap.keySet())) {
            throw new MojoExecutionException(
                    "Single version update expected to update only the project %s, found: %s".formatted(
                            currentProject,
                            versionBumpMap.keySet()
                    )
            );
        }

        SemanticVersionBump semanticVersionBump = getSemanticVersionBump(projectArtifact, versionBumpMap);
        log.info("Updating version with a %s semantic version".formatted(semanticVersionBump));
        try {
            POMUtils.updateVersion(versionNode, semanticVersionBump);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Unable to update version node", e);
        }

        writeUpdatedPom(document, pom);
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
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.reducing(SemanticVersionBump.NONE, Map.Entry::getValue, SemanticVersionBump::max)
                ));
        Map<MavenArtifact, List<VersionMarkdown>> markdownMap = versionMarkdowns.stream()
                .<Map.Entry<MavenArtifact, VersionMarkdown>>mapMulti((item, consumer) -> {
                    for (MavenArtifact artifact : item.bumps().keySet()) {
                        consumer.accept(Map.entry(artifact, item));
                    }
                })
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(
                                Map.Entry::getValue,
                                Collectors.collectingAndThen(Collectors.toList(), List::copyOf)
                        )
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
            POMUtils.writePom(document, pom, backupOldPom);
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
}
