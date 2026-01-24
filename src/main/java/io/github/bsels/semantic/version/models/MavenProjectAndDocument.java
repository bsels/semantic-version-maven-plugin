package io.github.bsels.semantic.version.models;

import org.w3c.dom.Document;

import java.nio.file.Path;
import java.util.Objects;

/// Represents a combination of a Maven project artifact, its associated POM file path,
/// and the XML document of the POM file's contents.
///
/// This class is designed as a record to provide an immutable data container for
/// conveniently managing and accessing Maven project-related information.
///
/// @param artifact the Maven artifact associated with the project; must not be null
/// @param pomFile  the path to the POM file for the project; must not be null
/// @param document the XML document representing the POM file's contents; must not be null
public record MavenProjectAndDocument(MavenArtifact artifact, Path pomFile, Document document) {

    /// Constructs a new instance of the MavenProjectAndDocument record.
    ///
    /// @param artifact the Maven artifact associated with the project; must not be null
    /// @param pomFile  the path to the POM file for the project; must not be null
    /// @param document the XML document representing the POM file's contents; must not be null
    /// @throws NullPointerException if any of the provided parameters are null
    public MavenProjectAndDocument {
        Objects.requireNonNull(artifact, "`artifact` must not be null");
        Objects.requireNonNull(pomFile, "`pomFile` must not be null");
        Objects.requireNonNull(document, "`document` must not be null");
    }
}
