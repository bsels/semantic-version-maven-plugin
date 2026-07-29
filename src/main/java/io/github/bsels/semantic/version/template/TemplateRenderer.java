package io.github.bsels.semantic.version.template;

import com.samskivert.mustache.Mustache;

import java.util.Map;
import java.util.Objects;

/// Renders changelog entry templates with JMustache.
public final class TemplateRenderer {

	/// No instance needed.
	private TemplateRenderer() {
		// No instance needed
	}

	/// Renders the template once with nested prompt data and without HTML escaping.
	///
	/// @param definition template definition; must not be null
	/// @param data       nested prompt data; must not be null
	/// @return rendered Markdown
	/// @throws NullPointerException if an argument is null
	public static String render(
			TemplateDefinition definition,
			Map<String, Object> data
	)
			throws NullPointerException {
		Objects.requireNonNull(definition, "`definition` must not be null");
		Objects.requireNonNull(data, "`data` must not be null");
		return Mustache.compiler()
				.escapeHTML(false)
				.compile(definition.body())
				.execute(data);
	}
}
