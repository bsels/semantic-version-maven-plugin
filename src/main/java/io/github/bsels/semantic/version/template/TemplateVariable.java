package io.github.bsels.semantic.version.template;

import java.util.Objects;
import java.util.regex.Pattern;

/// A single variable declared in a template's YAML front matter.
///
/// @param name    the placeholder name used as `{name}` in the template body; never null
/// @param prompt  the text shown when prompting for this variable; never null
/// @param pattern optional validation pattern the input must fully match; may be null
public record TemplateVariable(String name, String prompt, Pattern pattern) {

	/// Validates the record components.
	public TemplateVariable {
		Objects.requireNonNull(name, "`name` must not be null");
		Objects.requireNonNull(prompt, "`prompt` must not be null");
	}
}
