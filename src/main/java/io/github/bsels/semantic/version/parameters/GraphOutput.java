package io.github.bsels.semantic.version.parameters;

/// Specifies the format of the dependency graph output.
///
/// This enum defines the available output formats for the `graph` Mojo.
/// The choice of output determines what information is included for each node in the dependency graph.
public enum GraphOutput {
    /// Includes only the Maven artifact identifier (groupId:artifactId).
    ARTIFACT_ONLY,
    /// Includes only the folder path of the project.
    FOLDER_ONLY,
    /// Includes both the Maven artifact identifier and the project folder path.
    ARTIFACT_AND_FOLDER
}
