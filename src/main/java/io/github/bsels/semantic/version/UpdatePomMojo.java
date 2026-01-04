package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.SemanticVersionBump;
import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.parameters.VersionBump;
import io.github.bsels.semantic.version.utils.MarkdownUtils;
import io.github.bsels.semantic.version.utils.POMUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.commonmark.parser.Parser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

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
        switch (modus) {
            case REVISION_PROPERTY, SINGLE_PROJECT_VERSION -> handleSingleVersionUpdate();
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
    /// @throws MojoExecutionException if the POM cannot be read or written, or it cannot update the version node.
    /// @throws MojoFailureException   if the runtime system fails to initial the XML reader and writer helper classes
    private void handleSingleVersionUpdate() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        Path pom = session.getCurrentProject()
                .getFile()
                .toPath();
        Document document = POMUtils.readPom(pom);
        Node versionNode = POMUtils.getProjectVersionNode(document, modus);

        VersionMarkdown markdown = MarkdownUtils.readMarkdown(
                log,
                baseDirectory.resolve(".versioning").resolve("20250104-132200.md")
        );

        SemanticVersionBump semanticVersionBump = getSemanticVersionBump(document);
        log.info("Updating version with a %s semantic version".formatted(semanticVersionBump));
        try {
            POMUtils.updateVersion(versionNode, semanticVersionBump);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Unable to update version node", e);
        }

        writeUpdatedPom(document, pom);
    }

    /// Writes the updated Maven POM file. This method either writes the updated POM to the specified path or performs a dry-run
    /// where the updated POM content is logged for review without making any file changes.
    ///
     /// If dry-run mode is enabled, the new POM content is created as a string and logged. Otherwise, the updated POM is
    /// written to the provided file path, with an option to back up the original file before overwriting.
    ///
    /// @param document the XML Document representation of the Maven POM file to be updated
    /// @param pom the path to the POM file where the updated content will be written
    /// @throws MojoExecutionException if an I/O error occurs while writing the updated POM or processing the dry-run
    /// @throws MojoFailureException if the operation fails due to an XML parsing or writing error
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

    /// Determines the type of semantic version increment to be applied based on the current configuration
    /// and the provided document.
    ///
    /// @param document the XML document used to determine the semantic version bump, typically representing the content of a POM file or similar configuration.
    /// @return the type of semantic version bump to apply, which can be one of the predefined values in [SemanticVersionBump], such as MAJOR, MINOR, PATCH, or a custom determination based on file-based analysis.
    private SemanticVersionBump getSemanticVersionBump(Document document) {
        return switch (versionBump) {
            case FILE_BASED -> getSemanticVersionBumpFromFile(document);
            case MAJOR -> SemanticVersionBump.MAJOR;
            case MINOR -> SemanticVersionBump.MINOR;
            case PATCH -> SemanticVersionBump.PATCH;
        };
    }

    private SemanticVersionBump getSemanticVersionBumpFromFile(Document document) {
        throw new UnsupportedOperationException("File based versioning not yet implemented");
    }
}
