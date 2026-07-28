package io.github.bsels.semantic.version.template;

import java.util.List;
import java.util.Objects;

/// A fully parsed, locally usable changelog entry template.
///
/// @param repeatable whether the whole body may be rendered multiple times in one create run
/// @param variables  the declared variables in declaration order; never null
/// @param body       the Markdown template body containing `{name}` placeholders; never null
public record TemplateDefinition(boolean repeatable, List<TemplateVariable> variables, String body)
		implements ParsedTemplate {

	/// Validates the record components and copies the variable list to an immutable list.
	public TemplateDefinition {
		Objects.requireNonNull(variables, "`variables` must not be null");
		Objects.requireNonNull(body, "`body` must not be null");
		variables = List.copyOf(variables);
	}
}
