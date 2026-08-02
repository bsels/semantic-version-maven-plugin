package io.github.bsels.semantic.version.template;

import com.samskivert.mustache.Mustache;

import java.util.Map;
import java.util.Objects;

///
/// Utility class for rendering templates with the JMustache library.
///
/// This class provides a static method to render changelog entry templates
/// using nested prompt data. The rendered output is returned as a Markdown string,
/// and HTML escaping is disabled during rendering. The class is designed to be
/// non-instantiable to ensure it is used in a purely static context.
///
public final class TemplateRenderer {

	///
	/// A utility class for rendering templates with the JMustache library.
	///
	/// This class provides static methods for rendering changelog entry templates
	/// using prompt data and supports configurations like disabling HTML escaping.
	/// It is designed as a non-instantiable class.
	///
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
	) throws NullPointerException {
		Objects.requireNonNull(definition, "`definition` must not be null");
		Objects.requireNonNull(data, "`data` must not be null");
		return Mustache.compiler()
				.escapeHTML(false)
				.compile(definition.body())
				.execute(data);
	}
}
