package io.github.bsels.semantic.version.template;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A fully parsed, locally usable changelog entry template.
///
/// @param body              the Markdown Mustache template body; never null
/// @param variables         the declared variables in declaration order; never null
/// @param sectionAddPrompts resolved add-another prompt for each used section; never null
/// @param promptPlan        prompts in document order; never null
public record TemplateDefinition(
		String body,
		List<TemplateVariable> variables,
		Map<String, String> sectionAddPrompts,
		List<PromptNode> promptPlan
)
		implements ParsedTemplate {

	/// Validates the record components and copies collections to immutable collections.
	public TemplateDefinition {
		Objects.requireNonNull(body, "`body` must not be null");
		Objects.requireNonNull(variables, "`variables` must not be null");
		Objects.requireNonNull(sectionAddPrompts, "`sectionAddPrompts` must not be null");
		Objects.requireNonNull(promptPlan, "`promptPlan` must not be null");
		variables = List.copyOf(variables);
		sectionAddPrompts = Map.copyOf(sectionAddPrompts);
		promptPlan = List.copyOf(promptPlan);
	}
}
