package io.github.bsels.semantic.version.template;

import org.apache.maven.plugin.MojoFailureException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

/// Collects and validates terminal input for template variables.
public final class TemplatePrompter {

	/// No instance needed.
	private TemplatePrompter() {
		// No instance needed
	}

	/// Prompts for used variable values in declaration order.
	///
	/// @param definition template definition; must not be null
	/// @return one value map per collected block
	/// @throws NullPointerException if the definition is null
	/// @throws MojoFailureException if input is exhausted while a value is required
	public static List<Map<String, String>> promptForValues(TemplateDefinition definition)
			throws NullPointerException, MojoFailureException {
		Objects.requireNonNull(definition, "`definition` must not be null");
		Scanner scanner = new Scanner(System.in);
		List<Map<String, String>> valueSets = new ArrayList<>();
		boolean more = true;
		while (more) {
			Map<String, String> values = new LinkedHashMap<>();
			for (TemplateVariable variable : definition.variables()) {
				if (definition.body().contains("{" + variable.name() + "}")) {
					values.put(variable.name(), promptSingleValue(scanner, variable));
				}
			}
			valueSets.add(Map.copyOf(values));
			more = definition.repeatable() && promptAddAnother(scanner);
		}
		return List.copyOf(valueSets);
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
	)
			throws MojoFailureException {
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

	/// Asks whether another entry should be collected.
	///
	/// @param scanner input scanner
	/// @return true only for `y`, ignoring case
	private static boolean promptAddAnother(Scanner scanner) {
		System.out.print("Add another entry? [y/N]: ");
		return scanner.hasNextLine() && "y".equalsIgnoreCase(scanner.nextLine().strip());
	}
}
