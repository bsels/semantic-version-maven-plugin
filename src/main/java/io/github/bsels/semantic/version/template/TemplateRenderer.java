package io.github.bsels.semantic.version.template;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Renders a template body by substituting `{name}` placeholders.
public final class TemplateRenderer {

	/// Pattern matching supported template placeholders.
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");

	/// No instance needed.
	private TemplateRenderer() {
		// No instance needed
	}

	/// Renders the template once per value set and concatenates the blocks.
	///
	/// @param definition template definition; must not be null
	/// @param valueSets  values for each rendered block; must not be null or empty
	/// @return rendered Markdown with one trailing newline
	/// @throws NullPointerException     if an argument or value set is null
	/// @throws IllegalArgumentException if no value sets are supplied or a value is missing
	public static String render(
			TemplateDefinition definition,
			List<Map<String, String>> valueSets
	)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(definition, "`definition` must not be null");
		Objects.requireNonNull(valueSets, "`valueSets` must not be null");
		if (valueSets.isEmpty()) {
			throw new IllegalArgumentException("`valueSets` must not be empty");
		}
		return valueSets.stream()
				.map(values -> renderBlock(definition, values))
				.collect(Collectors.joining("\n")) + "\n";
	}

	/// Renders one template block.
	///
	/// @param definition template definition
	/// @param values     values for this block
	/// @return rendered block without trailing whitespace
	private static String renderBlock(
			TemplateDefinition definition,
			Map<String, String> values
	) {
		Objects.requireNonNull(values, "`values` in `valueSets` must not be null");
		Matcher matcher = PLACEHOLDER.matcher(definition.body());
		StringBuilder rendered = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			String value = values.get(name);
			if (value == null) {
				throw new IllegalArgumentException(
						"Missing value for template variable `%s`".formatted(name)
				);
			}
			matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(rendered);
		return rendered.toString().stripTrailing();
	}
}
