package io.github.bsels.semantic.version.template;

import org.apache.maven.plugin.MojoFailureException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

///
/// Utility class for prompting and collecting values for a template definition.
/// This class is designed to interactively prompt users for input values based
/// on a provided template structure that includes variables and sections.
/// The collected data is organized in a format suitable for rendering with Mustache.
///
/// Note: This class cannot be instantiated.
///
public final class TemplatePrompter {

	///
	/// Private constructor for the TemplatePrompter class.
	///
	/// This constructor is intentionally defined as private to prevent instantiation
	/// of the TemplatePrompter class, as it is designed to serve only as a utility class
	/// containing static methods.
	///
	private TemplatePrompter() {
		// No instance needed
	}

	/// Prompts recursively in template document order.
	///
	/// @param definition template definition; must not be null
	/// @return nested data ready for Mustache rendering
	/// @throws NullPointerException if the definition is null
	/// @throws MojoFailureException if input is exhausted while a value is required
	public static Map<String, Object> promptForValues(TemplateDefinition definition)
			throws NullPointerException, MojoFailureException {
		Objects.requireNonNull(definition, "`definition` must not be null");
		Scanner scanner = new Scanner(System.in);
		Map<String, TemplateVariable> variables = definition.variables().stream()
				.collect(Collectors.toMap(
						TemplateVariable::name,
						Function.identity(),
						(first, second) -> first,
						LinkedHashMap::new
				));
		return collectBlock(scanner, definition, variables, definition.promptPlan());
	}

	/// Recursively collects values for a block of prompt nodes.
	///
	/// @param scanner    input scanner used to read terminal values
	/// @param definition template definition containing section prompt configuration
	/// @param variables  variables keyed by name for resolving variable nodes
	/// @param nodes      prompt nodes to collect in document order
	/// @return immutable nested values for the block, ready for rendering
	/// @throws MojoFailureException if input is exhausted while a required value is being collected
	private static Map<String, Object> collectBlock(
			Scanner scanner,
			TemplateDefinition definition,
			Map<String, TemplateVariable> variables,
			List<PromptNode> nodes
	) throws MojoFailureException {
		Map<String, Object> values = new LinkedHashMap<>();
		for (PromptNode node : nodes) {
			if (node instanceof VariableNode variableNode) {
				TemplateVariable variable = variables.get(variableNode.name());
				values.put(variable.name(), promptSingleValue(scanner, variable));
			} else {
				SectionNode section = (SectionNode) node;
				List<Map<String, Object>> iterations = new ArrayList<>();
				do {
					iterations.add(collectBlock(scanner, definition, variables, section.children()));
				} while (promptAddAnother(scanner, definition.sectionAddPrompts().get(section.name())));
				values.put(section.name(), List.copyOf(iterations));
			}
		}
		return Map.copyOf(values);
	}

	/// Prompts until one valid value is entered.
	///
	/// @param scanner  input scanner
	/// @param variable variable declaration
	/// @return validated input
	/// @throws MojoFailureException if input is exhausted
	private static String promptSingleValue(
			Scanner scanner,
			TemplateVariable variable
	) throws MojoFailureException {
		while (true) {
			System.out.printf("%s: ", variable.prompt());
			if (!scanner.hasNextLine()) {
				throw new MojoFailureException(
						"No input available while prompting for template variable `%s`".formatted(variable.name())
				);
			}
			String value = scanner.nextLine().strip();
			if (value.isBlank()) {
				System.out.println("Value must not be blank");
			} else if (variable.pattern() != null && !variable.pattern().matcher(value).matches()) {
				System.out.printf("Value does not match pattern `%s`%n", variable.pattern().pattern());
			} else {
				return value;
			}
		}
	}

	/// Asks whether another section iteration should be collected.
	private static boolean promptAddAnother(
			Scanner scanner,
			String addPrompt
	) {
		System.out.printf("%s [y/N]: ", addPrompt);
		return scanner.hasNextLine() && "y".equalsIgnoreCase(scanner.nextLine().strip());
	}
}
