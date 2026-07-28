package io.github.bsels.semantic.version.template;

import java.util.Objects;

/// A reference to a template stored in another git repository.
///
/// @param remote the git URL of the repository holding the template; never null
/// @param path   the file path of the template inside the remote repository; never null
/// @param ref    the branch or tag to fetch; may be null for the remote default branch
public record RemoteTemplateReference(String remote, String path, String ref) implements ParsedTemplate {

	/// The default template path inside the remote repository.
	public static final String DEFAULT_PATH = ".versioning/template.md";

	/// Validates the record components.
	public RemoteTemplateReference {
		Objects.requireNonNull(remote, "`remote` must not be null");
		Objects.requireNonNull(path, "`path` must not be null");
	}
}
