package io.github.bsels.semantic.version.template;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Parses changelog entry templates with YAML front matter and a Markdown Mustache body.
public final class TemplateParser {

	/// Delimiter line of a YAML front matter block.
	private static final String FRONT_MATTER_DELIMITER = "---";

	/// Supported variable and section names.
	private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

	/// YAML mapper for deserializing front matter.
	private static final ObjectMapper YAML_MAPPER = YAMLMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	/// Private constructor to prevent instantiation of {@code TemplateParser}.
	///
	/// The {@code TemplateParser} class acts as a utility class with only static
	/// methods and constants. Instantiation of this class is unnecessary and prohibited.
	///
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
	) throws NullPointerException, MojoFailureException {
		Objects.requireNonNull(log, "`log` must not be null");
		Objects.requireNonNull(content, "`content` must not be null");

		String[] frontMatterAndBody = splitFrontMatter(content);
		FrontMatter frontMatter = readFrontMatter(frontMatterAndBody[0]);
		String body = frontMatterAndBody[1];

		if (frontMatter == null) {
			throw new MojoFailureException("Template front matter must not be empty");
		}
		if (frontMatter.repeatable() != null) {
			throw new MojoFailureException(
					"`repeatable` is no longer supported; mark the repeating block with a "
							+ "{{#section}} in the template body"
			);
		}
		if (frontMatter.remote() != null) {
			if (frontMatter.remote().isBlank()) {
				throw new MojoFailureException("Template `remote` must not be blank");
			}
			if (frontMatter.variables() != null || frontMatter.sections() != null) {
				throw new MojoFailureException(
						"Template front matter must not combine `remote` with `variables` or `sections`"
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

		Map<String, FrontMatter.VariableDeclaration> variableDeclarations = frontMatter.variables() == null
				? Map.of()
				: frontMatter.variables();

		List<TemplateVariable> variables = buildVariables(variableDeclarations);
		TemplateAnalysis analysis = analyzeBody(body);
		validateVariables(log, variables, analysis.usedVariables());
		Map<String, String> sectionAddPrompts = buildSectionAddPrompts(
				frontMatter.sections(),
				analysis.usedSections()
		);
		return new TemplateDefinition(body, variables, sectionAddPrompts, analysis.promptPlan());
	}

	///
	/// Splits the provided template content into its YAML front matter block and body content.
	/// The method expects the front matter to be delimited by a specific delimiter, typically `---`.
	///
	/// @param content the full text of the template, including YAML front matter and body; must not be null
	/// @return an array of two strings, where the first element is the YAML front matter block,
	///         and the second element is the remaining body content
	/// @throws MojoFailureException if the front matter is missing, not properly delimited,
	///                              or if the content is improperly formatted
	///
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

	///
	/// Reads and parses the YAML front matter into a {@code FrontMatter} object.
	///
	/// @param yaml the YAML content to be parsed; must not be blank
	/// @return a {@code FrontMatter} object representing the parsed front matter
	/// @throws MojoFailureException if the YAML content is blank or malformed
	///
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

	///
	/// Builds a list of template variables based on the provided variable declarations.
	/// Each variable includes its name, prompt, and an optional validation pattern.
	///
	/// @param declarations a map where the key is the variable name and the value is
	///                     the corresponding variable declaration; must not be null
	/// @return a list of {@code TemplateVariable} objects, each representing a template variable
	/// @throws MojoFailureException if a variable has an invalid name, a blank prompt,
	///                              or if its pattern is an invalid regular expression
	///
	private static List<TemplateVariable> buildVariables(
			Map<String, FrontMatter.VariableDeclaration> declarations
	) throws MojoFailureException {
		List<TemplateVariable> variables = new ArrayList<>(declarations.size());

		for (Map.Entry<String, FrontMatter.VariableDeclaration> entry : declarations.entrySet()) {
			String name = entry.getKey();
			validateName("variable", name);
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

	///
	/// Analyzes the body of a Mustache template to extract information about the used variables
	/// and sections, as well as building a prompt plan for further processing.
	///
	/// @param body the content of the Mustache template to be analyzed; must not be null
	/// @return a {@code TemplateAnalysis} object consisting of the prompt plan, the set of
	///         variables used in the template, and the set of sections used in the template
	/// @throws MojoFailureException if the body contains unsupported Mustache tags, invalid
	///         variable or section names, or if the body is otherwise malformed
	///
	private static TemplateAnalysis analyzeBody(String body) throws MojoFailureException {
		if (body.contains("{{=")) {
			throw unsupportedTag("custom delimiters", null);
		}
		Set<String> usedVariables = new HashSet<>();
		Set<String> usedSections = new HashSet<>();
		try {
			Template template = Mustache.compiler().escapeHTML(false).compile(body);
			template.visit(new Mustache.Visitor() {
				@Override
				public void visitText(String text) {
					// Text does not produce prompts.
				}

				@Override
				public void visitVariable(String name) {
					validateVisitedName("variable", name);
					usedVariables.add(name);
				}

				@Override
				public boolean visitInclude(String name) {
					throw new UnsupportedTagException("partial", name);
				}

				@Override
				public boolean visitParent(String name) {
					throw new UnsupportedTagException("parent", name);
				}

				@Override
				public boolean visitBlock(String name) {
					throw new UnsupportedTagException("block", name);
				}

				@Override
				public boolean visitSection(String name) {
					validateVisitedName("section", name);
					usedSections.add(name);
					return true;
				}

				@Override
				public boolean visitInvertedSection(String name) {
					throw new UnsupportedTagException("inverted section", name);
				}
			});
		} catch (UnsupportedTagException e) {
			throw unsupportedTag(e.type, e.name);
		} catch (InvalidNameException e) {
			throw new MojoFailureException(
					"Template %s name `%s` is not valid".formatted(e.type, e.name)
			);
		} catch (MustacheException e) {
			throw new MojoFailureException("Template body is not valid: %s".formatted(e.getMessage()), e);
		}
		return new TemplateAnalysis(
				buildPromptPlan(body),
				Set.copyOf(usedVariables),
				Set.copyOf(usedSections)
		);
	}

	/// Builds nesting from the fixed-delimiter body after JMustache has validated its syntax.
	private static List<PromptNode> buildPromptPlan(String body) throws MojoFailureException {
		MutableBlock root = new MutableBlock(null);
		Deque<MutableBlock> blocks = new ArrayDeque<>();
		blocks.push(root);
		int cursor = 0;
		while (true) {
			int start = body.indexOf("{{", cursor);
			if (start == -1) {
				break;
			}
			boolean triple = body.startsWith("{{{", start);
			String close = triple ? "}}}" : "}}";
			int end = body.indexOf(close, start + (triple ? 3 : 2));
			if (end == -1) {
				break;
			}
			String tag = body.substring(start + (triple ? 3 : 2), end).strip();
			cursor = end + close.length();
			if (tag.isEmpty() || tag.charAt(0) == '!') {
				continue;
			}
			char marker = triple ? ' ' : tag.charAt(0);
			boolean variable = triple || "#/&^><$=".indexOf(marker) == -1 || marker == '&';
			String name = switch (marker) {
				case '#', '/', '&', '^', '>', '<', '$', '=' -> tag.substring(1).strip();
				default -> tag;
			};
			if (marker == '#') {
				MutableBlock section = new MutableBlock(name);
				blocks.peek().entries.add(section);
				blocks.push(section);
			} else if (marker == '/') {
				MutableBlock section = blocks.pop();
				if (!section.name.equals(name)) {
					throw new MojoFailureException("Template body is not valid: mismatched section `%s`".formatted(name));
				}
			} else if (variable) {
				MutableBlock block = blocks.peek();
				if (block.variables.add(name)) {
					block.entries.add(new VariableNode(name));
				}
			}
		}
		return toPromptNodes(root.entries);
	}

	/// Converts mutable parser entries to the public immutable prompt plan.
	private static List<PromptNode> toPromptNodes(List<Object> entries) {
		List<PromptNode> nodes = new ArrayList<>(entries.size());
		for (Object entry : entries) {
			if (entry instanceof PromptNode node) {
				nodes.add(node);
			} else {
				MutableBlock section = (MutableBlock) entry;
				nodes.add(new SectionNode(section.name, toPromptNodes(section.entries)));
			}
		}
		return List.copyOf(nodes);
	}

	/// Verifies declarations against variables discovered in the body.
	private static void validateVariables(
			Log log,
			List<TemplateVariable> variables,
			Set<String> used
	)
			throws MojoFailureException {
		Set<String> declared = new HashSet<>();
		variables.forEach(variable -> declared.add(variable.name()));
		Set<String> undeclared = new HashSet<>(used);
		undeclared.removeAll(declared);
		if (!undeclared.isEmpty()) {
			throw new MojoFailureException(
					"Template body references undeclared variable(s) %s; declared variables: %s"
							.formatted(undeclared.stream().sorted().toList(), declared.stream().sorted().toList())
			);
		}
		for (TemplateVariable variable : variables) {
			if (!used.contains(variable.name())) {
				log.warn("Template variable `%s` is declared but never used in the body".formatted(variable.name()));
			}
		}
	}

	/// Resolves configured and default add-another prompts for used sections.
	private static Map<String, String> buildSectionAddPrompts(
			Map<String, FrontMatter.SectionDeclaration> declarations,
			Set<String> usedSections
	)
			throws MojoFailureException {
		if (declarations != null) {
			for (Map.Entry<String, FrontMatter.SectionDeclaration> entry : declarations.entrySet()) {
				String name = entry.getKey();
				validateName("section", name);
				if (entry.getValue() != null
						&& entry.getValue().addPrompt() != null
						&& entry.getValue().addPrompt().isBlank()) {
					throw new MojoFailureException(
							"Add prompt of template section `%s` must not be blank".formatted(name)
					);
				}
			}
		}
		Map<String, String> prompts = new LinkedHashMap<>();
		for (String name : usedSections.stream().sorted().toList()) {
			FrontMatter.SectionDeclaration declaration = declarations == null ? null : declarations.get(name);
			String prompt = declaration != null && declaration.addPrompt() != null
					? declaration.addPrompt()
					: "Add another %s?".formatted(name);
			prompts.put(name, prompt);
		}
		return prompts;
	}

	/// Validates a declared name.
	private static void validateName(
			String type,
			String name
	) throws MojoFailureException {
		if (name == null || !NAME.matcher(name).matches()) {
			throw new MojoFailureException("Template %s name `%s` is not valid".formatted(type, name));
		}
	}

	/// Validates a name from a visitor callback without a checked exception.
	private static void validateVisitedName(
			String type,
			String name
	) {
		if (!NAME.matcher(name).matches()) {
			throw new InvalidNameException(type, name);
		}
	}

	/// Creates the standard unsupported-tag failure.
	private static MojoFailureException unsupportedTag(
			String type,
			String name
	) {
		String label = name == null ? type : "%s `%s`".formatted(type, name);
		return new MojoFailureException(
				"Mustache %s are not supported in changelog templates".formatted(label)
		);
	}

	/// Raw template front matter.
	record FrontMatter(
			Boolean repeatable,
			LinkedHashMap<String, VariableDeclaration> variables,
			LinkedHashMap<String, SectionDeclaration> sections,
			String remote,
			String path,
			String ref
	) {

		/// Raw declaration of one variable.
		record VariableDeclaration(String prompt, String pattern) {
		}

		/// Raw declaration of one section.
		record SectionDeclaration(String addPrompt) {
		}
	}

	/// Compiled information extracted from the body.
	private record TemplateAnalysis(
			List<PromptNode> promptPlan,
			Set<String> usedVariables,
			Set<String> usedSections
	) {
	}

	/// Mutable block used only while reconstructing section nesting.
	private static final class MutableBlock {

		private final String name;
		private final List<Object> entries = new ArrayList<>();
		private final Set<String> variables = new HashSet<>();

		private MutableBlock(String name) {
			this.name = name;
		}
	}

	/// Signals an unsupported visitor callback.
	private static final class UnsupportedTagException extends RuntimeException {

		private final String type;
		private final String name;

		private UnsupportedTagException(
				String type,
				String name
		) {
			this.type = type;
			this.name = name;
		}
	}

	/// Signals an unsupported variable or section name from a visitor callback.
	private static final class InvalidNameException extends RuntimeException {

		private final String type;
		private final String name;

		private InvalidNameException(
				String type,
				String name
		) {
			this.type = type;
			this.name = name;
		}
	}
}
