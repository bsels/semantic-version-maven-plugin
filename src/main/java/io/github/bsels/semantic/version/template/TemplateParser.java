package io.github.bsels.semantic.version.template;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Parses changelog entry templates with YAML front matter and a Markdown body.
public final class TemplateParser {

	/// Delimiter line of a YAML front matter block.
	private static final String FRONT_MATTER_DELIMITER = "---";

	/// Pattern matching supported `{name}` placeholders.
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");

	/// YAML mapper for deserializing front matter.
	private static final ObjectMapper YAML_MAPPER = YAMLMapper.builder().build();

	/// No instance needed.
	private TemplateParser() {
		// No instance needed
	}

	/// Parses raw template content.
	///
	/// @param log     Maven log used for warnings; must not be null
	/// @param content raw template content; must not be null
	/// @return a local definition or remote reference
	/// @throws NullPointerException if an argument is null
	/// @throws MojoFailureException if the template is malformed or inconsistent
	public static ParsedTemplate parse(
			Log log,
			String content
	)
			throws NullPointerException, MojoFailureException {
		Objects.requireNonNull(log, "`log` must not be null");
		Objects.requireNonNull(content, "`content` must not be null");

		String[] frontMatterAndBody = splitFrontMatter(content);
		FrontMatter frontMatter = readFrontMatter(frontMatterAndBody[0]);
		String body = frontMatterAndBody[1];

		if (frontMatter == null) {
			throw new MojoFailureException("Template front matter must not be empty");
		}
		if (frontMatter.remote() != null) {
			if (frontMatter.remote().isBlank()) {
				throw new MojoFailureException("Template `remote` must not be blank");
			}
			if (frontMatter.variables() != null || frontMatter.repeatable() != null) {
				throw new MojoFailureException(
						"Template front matter must not combine `remote` with `variables` or `repeatable`"
				);
			}
			String path = frontMatter.path() == null
					? RemoteTemplateReference.DEFAULT_PATH
					: frontMatter.path();
			if (path.isBlank()) {
				throw new MojoFailureException("Remote template `path` must not be blank");
			}
			return new RemoteTemplateReference(frontMatter.remote(), path, frontMatter.ref());
		}

		if (frontMatter.variables() == null || frontMatter.variables().isEmpty()) {
			throw new MojoFailureException("Template front matter must declare `variables` (or a `remote` reference)");
		}
		List<TemplateVariable> variables = buildVariables(frontMatter.variables());
		validatePlaceholders(log, variables, body);
		return new TemplateDefinition(Boolean.TRUE.equals(frontMatter.repeatable()), variables, body);
	}

	/// Splits YAML front matter from the Markdown body.
	///
	/// @param content raw template content
	/// @return YAML and Markdown body
	/// @throws MojoFailureException if front matter is missing or unclosed
	private static String[] splitFrontMatter(String content) throws MojoFailureException {
		List<String> lines = content.lines().toList();
		if (lines.isEmpty() || !FRONT_MATTER_DELIMITER.equals(lines.get(0).strip())) {
			throw new MojoFailureException("Template must start with a YAML front matter block (`---`)");
		}
		int end = -1;
		for (int index = 1; index < lines.size(); index++) {
			if (FRONT_MATTER_DELIMITER.equals(lines.get(index).strip())) {
				end = index;
				break;
			}
		}
		if (end == -1) {
			throw new MojoFailureException("Template YAML front matter block is not closed (`---`)");
		}
		String yaml = String.join("\n", lines.subList(1, end));
		String body = lines.size() > end + 1
				? String.join("\n", lines.subList(end + 1, lines.size())) + "\n"
				: "";
		return new String[]{yaml, body};
	}

	/// Deserializes YAML front matter.
	///
	/// @param yaml YAML text
	/// @return deserialized front matter
	/// @throws MojoFailureException if YAML is malformed
	private static FrontMatter readFrontMatter(String yaml) throws MojoFailureException {
		if (yaml.isBlank()) {
			throw new MojoFailureException("Template front matter must not be empty");
		}
		try {
			return YAML_MAPPER.readValue(yaml, FrontMatter.class);
		} catch (JacksonException e) {
			throw new MojoFailureException("Template front matter is not valid: %s".formatted(e.getMessage()), e);
		}
	}

	/// Builds variables and compiles validation patterns.
	///
	/// @param declarations raw variable declarations
	/// @return variables in declaration order
	/// @throws MojoFailureException if a name or regex is invalid
	private static List<TemplateVariable> buildVariables(
			Map<String, FrontMatter.VariableDeclaration> declarations
	) throws MojoFailureException {
		List<TemplateVariable> variables = new ArrayList<>(declarations.size());
		for (Map.Entry<String, FrontMatter.VariableDeclaration> entry : declarations.entrySet()) {
			String name = entry.getKey();
			if (!name.matches("[A-Za-z][A-Za-z0-9]*")) {
				throw new MojoFailureException("Template variable name `%s` is not valid".formatted(name));
			}
			FrontMatter.VariableDeclaration declaration = entry.getValue();
			String prompt = declaration != null && declaration.prompt() != null
					? declaration.prompt()
					: name;
			if (prompt.isBlank()) {
				throw new MojoFailureException("Prompt of template variable `%s` must not be blank".formatted(name));
			}
			Pattern pattern = null;
			if (declaration != null && declaration.pattern() != null) {
				try {
					pattern = Pattern.compile(declaration.pattern());
				} catch (PatternSyntaxException e) {
					throw new MojoFailureException(
							"Pattern of template variable `%s` is not a valid regular expression: %s"
									.formatted(name, e.getMessage()),
							e
					);
				}
			}
			variables.add(new TemplateVariable(name, prompt, pattern));
		}
		return variables;
	}

	/// Validates body placeholders and warns about unused declarations.
	///
	/// @param log       Maven log
	/// @param variables declared variables
	/// @param body      template body
	/// @throws MojoFailureException if the body references an undeclared placeholder
	private static void validatePlaceholders(
			Log log,
			List<TemplateVariable> variables,
			String body
	)
			throws MojoFailureException {
		Set<String> declared = new HashSet<>();
		variables.forEach(variable -> declared.add(variable.name()));
		Set<String> used = new HashSet<>();
		Matcher matcher = PLACEHOLDER.matcher(body);
		while (matcher.find()) {
			used.add(matcher.group(1));
		}
		Set<String> undeclared = new HashSet<>(used);
		undeclared.removeAll(declared);
		if (!undeclared.isEmpty()) {
			throw new MojoFailureException(
					"Template body references undeclared placeholder(s) %s; declared variables: %s"
							.formatted(undeclared.stream().sorted().toList(), declared.stream().sorted().toList())
			);
		}
		for (TemplateVariable variable : variables) {
			if (!used.contains(variable.name())) {
				log.warn("Template variable `%s` is declared but never used in the body".formatted(variable.name()));
			}
		}
	}

	/// Raw template front matter.
	///
	/// @param repeatable whether the body may be rendered repeatedly; may be null
	/// @param variables  declared variables in declaration order; may be null
	/// @param remote     remote git URL; may be null
	/// @param path       path inside the remote repository; may be null
	/// @param ref        branch or tag; may be null
	record FrontMatter(
			Boolean repeatable,
			LinkedHashMap<String, VariableDeclaration> variables,
			String remote,
			String path,
			String ref
	) {

		/// Raw declaration of one variable.
		///
		/// @param prompt  prompt text; may be null
		/// @param pattern validation regex; may be null
		record VariableDeclaration(String prompt, String pattern) {
		}
	}
}
