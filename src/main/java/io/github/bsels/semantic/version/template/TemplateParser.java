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
import java.util.stream.Collectors;

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

		TemplateContent templateContent = splitFrontMatter(content);
		FrontMatter frontMatter = readFrontMatter(templateContent.yaml);
		if (frontMatter == null) {
			throw new MojoFailureException("Template front matter must not be empty");
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

		Map<String, VariableDeclaration> variableDeclarations = frontMatter.variables() == null
				? Map.of()
				: frontMatter.variables();

		List<TemplateVariable> variables = buildVariables(variableDeclarations);
		TemplateAnalysis analysis = analyzeBody(templateContent.body);
		validateVariables(log, variables, analysis.usedVariables());
		Map<String, String> sectionAddPrompts = buildSectionAddPrompts(
				frontMatter.sections(),
				analysis.usedSections()
		);
		return new TemplateDefinition(templateContent.body, variables, sectionAddPrompts, analysis.promptPlan());
	}

	///
	/// Splits the provided content into YAML front matter and body sections based on
	/// a fixed delimiter.
	///
	/// @param content the template content which includes YAML front matter at the beginning
	///                and a body section. The front matter must be delimited at the start
	///                and end by `---`.
	/// @return a TemplateContent object containing the extracted YAML front matter and body sections.
	/// @throws MojoFailureException if the content does not start or end with the expected
	///                              front matter delimiters, or if the front matter block is incomplete.
	///
	private static TemplateContent splitFrontMatter(String content) throws MojoFailureException {
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
		return new TemplateContent(yaml, body);
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
			Map<String, VariableDeclaration> declarations
	) throws MojoFailureException {
		List<TemplateVariable> variables = new ArrayList<>(declarations.size());

		for (Map.Entry<String, VariableDeclaration> entry : declarations.entrySet()) {
			String name = entry.getKey();
			validateName("variable", name);
			VariableDeclaration declaration = entry.getValue();
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

	///
	/// Constructs a prompt plan by parsing the body content of a Mustache-like template.
	/// The method analyzes the template structure, identifies sections and variables,
	/// and builds a nested representation of the prompt structure.
	///
	/// @param body the content of the Mustache template to be processed; must not be null
	/// @return a list of {@code PromptNode} objects representing the parsed prompt plan
	/// @throws MojoFailureException if the template is malformed, contains mismatched sections,
	///                              or uses unsupported Mustache tags
	///
	private static List<PromptNode> buildPromptPlan(String body) throws MojoFailureException {
		MutableBlock root = new MutableBlock(null);
		Deque<MutableBlock> blocks = new ArrayDeque<>();
		blocks.push(root);
		int cursor = 0;
		int start;
		while ((start = body.indexOf("{{", cursor)) != -1) {
			boolean triple = body.startsWith("{{{", start);
			int openingLength = triple ? 3 : 2;
			String close = triple ? "}}}" : "}}";
			int end = body.indexOf(close, start + openingLength);
			if (end == -1) {
				break;
			}
			String tag = body.substring(start + openingLength, end).strip();
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

	///
	/// Converts a list of mixed objects into an immutable list of {@code PromptNode} instances.
	/// Each object in the input list is either retyped as a {@code PromptNode}, if already an instance of it,
	/// or transformed into a {@code SectionNode} if it represents a {@code MutableBlock}.
	///
	/// @param entries a list of objects, where each object is either an instance of {@code PromptNode}
	///                or a {@code MutableBlock}. Must not be null.
	/// @return an immutable list of {@code PromptNode} instances representing the parsed structure.
	///
	private static List<PromptNode> toPromptNodes(List<Object> entries) {
		List<PromptNode> nodes = new ArrayList<>(entries.size());
		for (Object entry : entries) {
			if (entry instanceof PromptNode node) {
				nodes.add(node);
			} else if (entry instanceof MutableBlock section) {
				nodes.add(new SectionNode(section.name, toPromptNodes(section.entries)));
			} else {
				throw new RuntimeException("Unknown prompt entry");
			}
		}
		return List.copyOf(nodes);
	}

	///
	/// Validates the alignment between declared and used variables in a template.
	/// <ul>
	///   <li>Throws an exception if there are undeclared variables used in the template body.</li>
	///   <li>Logs warnings if there are declared variables that are never used.</li>
	///
	/// @param log       the logger to record warnings about unused declarations; must not be null
	/// @param variables the list of declared template variables; must not be null
	/// @param used      the set of template variable names used in the template body; must not be null
	/// @throws MojoFailureException if there are undeclared variables used within the template body
	///
	private static void validateVariables(
			Log log,
			List<TemplateVariable> variables,
			Set<String> used
	)
			throws MojoFailureException {
		Set<String> declared = variables.stream()
				.map(TemplateVariable::name)
				.collect(Collectors.toSet());
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

	///
	/// Builds a map of section names to their corresponding add prompts based on the
	/// provided section declarations and used sections. If a section declaration includes
	/// an add prompt, it is used; otherwise, a default prompt is generated.
	///
	/// @param declarations a map where the key is the section name and the value is the
	///                     corresponding section declaration; may be null if there are
	///                     no declared sections
	/// @param usedSections a set of section names that have been identified as used in
	///                     the template; must not be null
	/// @return a map where the key is a used section name and the value is the
	///         corresponding add prompt
	/// @throws MojoFailureException if a section name is invalid, or if the add prompt
	///                              of a declared section is set but blank
	///
	private static Map<String, String> buildSectionAddPrompts(
			Map<String, SectionDeclaration> declarations,
			Set<String> usedSections
	)
			throws MojoFailureException {
		declarations = Objects.requireNonNullElseGet(declarations, Map::of);
		for (Map.Entry<String, SectionDeclaration> entry : declarations.entrySet()) {
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
		Map<String, String> prompts = new LinkedHashMap<>();
		for (String name : usedSections.stream().sorted().toList()) {
			SectionDeclaration declaration = declarations.get(name);
			String prompt = declaration != null && declaration.addPrompt() != null
					? declaration.addPrompt()
					: "Add another %s?".formatted(name);
			prompts.put(name, prompt);
		}
		return prompts;
	}

	///
	/// Validates the format of a provided name based on predefined rules for a specific type.
	/// Throws an exception if the name is null or does not match the expected pattern.
	///
	/// @param type the type of the template to which the name belongs; must not be null
	/// @param name the name to be validated; may be null or malformed
	/// @throws MojoFailureException if the name is null or does not match the expected format
	///
	private static void validateName(
			String type,
			String name
	) throws MojoFailureException {
		if (name == null || !NAME.matcher(name).matches()) {
			throw new MojoFailureException("Template %s name `%s` is not valid".formatted(type, name));
		}
	}

	///
	/// Validates the given name for a specific type while processing visited elements.
	/// If the name does not match the expected pattern, an {@code InvalidNameException} is thrown.
	///
	/// @param type the type of the entity being validated; must not be null
	/// @param name the name to be validated; must not be null and must conform to the required format
	/// @throws InvalidNameException if the name does not match the expected format
	///
	private static void validateVisitedName(
			String type,
			String name
	) {
		if (!NAME.matcher(name).matches()) {
			throw new InvalidNameException(type, name);
		}
	}

	///
	/// Constructs and returns a {@link MojoFailureException} indicating that a specific
	/// Mustache tag type is not supported in changelog templates.
	///
	/// @param type The type of the Mustache tag.
	/// @param name The name of the Mustache tag, or null if no specific name is provided.
	/// @return A {@link MojoFailureException} with a detailed error message about the unsupported tag.
	///
	private static MojoFailureException unsupportedTag(
			String type,
			String name
	) {
		String label = name == null ? type : "%s `%s`".formatted(type, name);
		return new MojoFailureException(
				"Mustache %s are not supported in changelog templates".formatted(label)
		);
	}

	///
	/// Represents the front matter metadata of a document or configuration file.
	///
	/// This record encapsulates information about variables, sections, and
	/// file details such as remote origin, path, and reference.
	///
	/// The front matter typically serves as structured metadata that provides
	/// content organization, configuration, or descriptive details for a document.
	///
	/// Components:
	/// - variables: A mapping of variable names to their respective declarations.
	/// - sections: A mapping of section names to their respective declarations.
	/// - remote: The remote origin associated with the file.
	/// - path: The relative or absolute path of the file.
	/// - ref: A reference, such as a version or branch, tied to the file.
	///
	private record FrontMatter(
			LinkedHashMap<String, VariableDeclaration> variables,
			LinkedHashMap<String, SectionDeclaration> sections,
			String remote,
			String path,
			String ref
	) {
	}

	///
	/// Represents a variable declaration with an associated prompt and pattern.
	///
	/// This class is a record that encapsulates two pieces of information:
	/// - A prompt used to provide a descriptive message or label for the variable.
	/// - A pattern used to define a constraint or structure for the variable.
	///
	private record VariableDeclaration(String prompt, String pattern) {
	}

	///
	/// Represents a declaration of a section with a specific prompt.
	///
	/// This class is implemented as a record which is a special kind of Java class
	/// designed to model immutable data. It encapsulates a single field:
	///
	/// - addPrompt: The prompt associated with this section declaration.
	///
	/// Instances of this class are immutable and provide built-in methods for
	/// accessing the encapsulated data, equality checks, and string representation.
	///
	private record SectionDeclaration(String addPrompt) {
	}

	///
	/// Represents an analysis of a prompt template structure with details on its
	/// components, including the planned prompt flow, utilized variables, and used
	/// sections within the template.
	///
	/// This record is a compact and immutable container for template analysis details
	/// and provides a structured way to represent the metadata related to the structure
	/// and content of a template.
	///
	/// Components:
	/// - promptPlan: A sequential plan representing the structure and flow of the prompts.
	/// - usedVariables: A set of variables referenced within the template.
	/// - usedSections: A set of sections utilized in the template.
	///
	private record TemplateAnalysis(
			List<PromptNode> promptPlan,
			Set<String> usedVariables,
			Set<String> usedSections
	) {
	}

	///
	/// Represents the content of a template consisting of a YAML configuration and a body.
	///
	/// This record is immutable and encapsulates:
	/// - A YAML string representing configuration or metadata.
	/// - A body string containing the main content of the template.
	///
	private record TemplateContent(String yaml, String body) {}

	///
	/// MutableBlock is a private static final class that represents a block structure
	/// with a name, a collection of entries, and a set of variables.
	/// It provides an immutable name but allows its collection-based fields
	/// for entries and variables to be modified.
	///
	/// This class is designed to be used internally and is not accessible outside
	/// the enclosing class.
	///
	private static final class MutableBlock {

		private final String name;
		private final List<Object> entries = new ArrayList<>();
		private final Set<String> variables = new HashSet<>();

		private MutableBlock(String name) {
			this.name = name;
		}
	}

	///
	/// An exception thrown to indicate that an unsupported tag has been encountered.
	///
	/// This exception is used to signal situations where a tag with a specific type and name
	/// is not recognized or supported during processing.
	///
	/// The {@code type} represents the category of the tag (for example, its classification or grouping),
	/// while the {@code name} identifies the specific tag within that category.
	///
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

	///
	/// Exception thrown to indicate that a provided name is invalid for a specific type.
	///
	/// This exception is intended to be used when validating and enforcing naming rules
	/// for various types of entities. It captures both the type of entity and the invalid
	/// name that caused the exception to be raised.
	///
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
