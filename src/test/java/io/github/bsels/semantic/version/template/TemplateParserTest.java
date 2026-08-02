package io.github.bsels.semantic.version.template;

import io.github.bsels.semantic.version.test.utils.TestLog;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TemplateParserTest {

	private String readTemplateResource(String fileName) throws IOException {
		try (
				InputStream input = Objects.requireNonNull(
						getClass().getResourceAsStream("/itests/changelog-templates/" + fileName)
				)
		) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Nested
	class ParseTest {

		@Test
		void nullArguments_ThrowWithParameterName() {
			assertThatThrownBy(() -> TemplateParser.parse(null, ""))
					.isExactlyInstanceOf(NullPointerException.class)
					.hasMessage("`log` must not be null");
			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), null))
					.isExactlyInstanceOf(NullPointerException.class)
					.hasMessage("`content` must not be null");
		}

		@Test
		void sectionedTemplate_ReturnsDefinitionAndPromptPlanInDocumentOrder() throws Exception {
			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					readTemplateResource("repeatable.md")
			);

			assertThat(definition.variables()).extracting(TemplateVariable::name)
					.containsExactly("issueKey", "description");
			assertThat(definition.variables().get(0).prompt()).isEqualTo("Jira issue key");
			assertThat(definition.variables().get(0).pattern().pattern()).isEqualTo("[A-Z]+-\\d+");
			assertThat(definition.sectionAddPrompts())
					.containsEntry("entries", "Add another entry?");
			assertThat(definition.promptPlan()).containsExactly(
					new SectionNode(
							"entries",
							List.of(
									new VariableNode("issueKey"),
									new VariableNode("description")
							)
					)
			);
			assertThat(definition.body()).contains("{{#entries}}", "{{issueKey}}", "{{/entries}}");
		}

		@Test
		void nestedTemplate_BuildsNestedPromptPlan() throws Exception {
			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					readTemplateResource("nested.md")
			);

			assertThat(definition.promptPlan()).containsExactly(
					new SectionNode(
							"issues",
							List.of(
									new VariableNode("issueKey"),
									new SectionNode(
											"descriptions",
											List.of(new VariableNode("description"))
									)
							)
					)
			);
			assertThat(definition.sectionAddPrompts())
					.containsEntry("issues", "Add another issue?")
					.containsEntry("descriptions", "More descriptions?");
		}

		@Test
		void localTemplateWithoutBody_ReturnsEmptyDefinition() throws MojoFailureException {
			String template = """
					---
					variables: {}
					---
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.body()).isEmpty();
			assertThat(definition.variables()).isEmpty();
			assertThat(definition.sectionAddPrompts()).isEmpty();
			assertThat(definition.promptPlan()).isEmpty();
		}

		@Test
		void frontMatterDelimitersWithSurroundingWhitespace_AreAccepted() throws MojoFailureException {
			String template = "  ---  \nvariables: {}\n\t--- \nStatic body";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.body()).isEqualTo("Static body\n");
			assertThat(definition.promptPlan()).isEmpty();
		}

		@Test
		void duplicateVariableInSameBlock_IsCollapsedAtFirstOccurrence() throws MojoFailureException {
			String template = """
					---
					variables:
					  outer: {}
					  inner: {}
					---
					{{outer}} {{outer}}
					{{#items}}{{inner}} {{inner}}{{/items}}
					{{outer}}
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.promptPlan()).containsExactly(
					new VariableNode("outer"),
					new SectionNode(
							"items",
							List.of(new VariableNode("inner"))
					)
			);
			assertThat(definition.sectionAddPrompts())
					.containsEntry("items", "Add another items?");
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"conventional-breaking-change.md",
				"keep-a-changelog.md",
				"security-advisory.md"
		})
		void advancedExample_ReturnsDefinition(String fileName) throws Exception {
			ParsedTemplate parsed = TemplateParser.parse(
					new SystemStreamLog(),
					readTemplateResource(fileName)
			);

			assertThat(parsed).isInstanceOf(TemplateDefinition.class);
			assertThat(((TemplateDefinition) parsed).variables()).isNotEmpty();
		}

		@Test
		void variablePrompt_DefaultsToName() throws MojoFailureException {
			String template = """
					---
					variables:
					  description: {}
					---
					{{description}}
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.variables().get(0).prompt()).isEqualTo("description");
		}

		@Test
		void nullVariableDeclaration_DefaultsPromptAndPattern() throws MojoFailureException {
			String template = """
					---
					variables:
					  description:
					---
					{{description}}
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.variables()).containsExactly(
					new TemplateVariable("description", "description", null)
			);
		}

		@Test
		void sectionWithoutVariables_NeedsNoVariableDeclarations() throws MojoFailureException {
			String template = """
					---
					sections:
					  separators: {}
					---
					{{#separators}}---{{/separators}}
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.variables()).isEmpty();
			assertThat(definition.promptPlan())
					.containsExactly(new SectionNode("separators", List.of()));
		}

		@Test
		void nullSectionDeclaration_UsesDefaultAddPrompt() throws MojoFailureException {
			String template = """
					---
					sections:
					  entries:
					---
					{{#entries}}entry{{/entries}}
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.sectionAddPrompts())
					.containsExactlyEntriesOf(java.util.Map.of("entries", "Add another entries?"));
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"{{>partial}}",
				"{{<parent}}{{$content}}{{description}}{{/content}}{{/parent}}",
				"{{$block}}{{description}}{{/block}}",
				"{{^missing}}{{description}}{{/missing}}"
		})
		void unsupportedMustacheTag_Throws(String body) {
			String template = """
					---
					variables:
					  description: {}
					---
					%s
					""".formatted(body);

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageContaining("not supported in changelog templates");
		}

		@Test
		void customDelimiters_Throw() {
			String template = """
					---
					variables:
					  description: {}
					---
					{{=<% %>=}}<%description%>
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Mustache custom delimiters are not supported in changelog templates");
		}

		@Test
		void tripleMustacheAmpersandAndComment_BuildOneVariableNode() throws MojoFailureException {
			String template = """
					---
					variables:
					  description: {}
					---
					{{! This comment is ignored }}
					{{{description}}} {{&description}}
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.promptPlan())
					.containsExactly(new VariableNode("description"));
		}

		@Test
		void emptyMustacheTag_ThrowsWithParseMessage() {
			String template = """
					---
					variables: {}
					---
					{{}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageStartingWith("Template body is not valid:");
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"{{invalid-name}}",
				"{{#invalid-name}}content{{/invalid-name}}"
		})
		void invalidNameInBody_Throws(String body) {
			String template = """
					---
					variables: {}
					---
					%s
					""".formatted(body);

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageMatching("Template (variable|section) name `invalid-name` is not valid");
		}

		@Test
		void unmatchedOpeningDelimiter_IsLiteralText() throws MojoFailureException {
			String template = """
					---
					variables: {}
					---
					literal {{
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.promptPlan()).isEmpty();
			assertThat(definition.body()).isEqualTo("literal {{\n");
		}

		@Test
		void malformedMustache_ThrowsWithParseMessage() {
			String template = """
					---
					variables:
					  description: {}
					---
					{{#items}}{{description}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageStartingWith("Template body is not valid:")
					.hasMessageContaining("Section missing close tag");
		}

		@Test
		void undeclaredVariable_ThrowsAndListsDeclarations() {
			String template = """
					---
					variables:
					  issueKey: {}
					---
					{{issueKey}} {{descriptionTypo}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Template body references undeclared variable(s) [descriptionTypo]; "
									+ "declared variables: [issueKey]"
					);
		}

		@Test
		void invalidRegex_Throws() {
			String template = """
					---
					variables:
					  issueKey:
					    pattern: "[unclosed"
					---
					{{issueKey}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageStartingWith(
							"Pattern of template variable `issueKey` is not a valid regular expression:"
					)
					.hasCauseInstanceOf(PatternSyntaxException.class);
		}

		@Test
		void invalidVariableName_Throws() {
			String template = """
					---
					variables:
					  issue-key: {}
					---
					{{issueKey}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template variable name `issue-key` is not valid");
		}

		@Test
		void blankVariablePrompt_Throws() {
			String template = """
					---
					variables:
					  issueKey:
					    prompt: " "
					---
					{{issueKey}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Prompt of template variable `issueKey` must not be blank");
		}

		@Test
		void invalidSectionName_Throws() {
			String template = """
					---
					sections:
					  entry-list: {}
					---
					static body
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template section name `entry-list` is not valid");
		}

		@Test
		void blankSectionAddPrompt_Throws() {
			String template = """
					---
					variables:
					  description: {}
					sections:
					  entries:
					    addPrompt: " "
					---
					{{#entries}}{{description}}{{/entries}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Add prompt of template section `entries` must not be blank");
		}

		@Test
		void remoteReference_UsesConfiguredValues() throws Exception {
			assertThat(TemplateParser.parse(
					new SystemStreamLog(),
					readTemplateResource("remote-reference.md")
			))
					.isEqualTo(new RemoteTemplateReference(
							"git@github.com:org/versioning-standards.git",
							"changelog-template.md",
							"main"
					));
		}

		@Test
		void remoteReference_DefaultsPath() throws MojoFailureException {
			String template = """
					---
					remote: git@github.com:org/standards.git
					---
					""";

			RemoteTemplateReference reference = (RemoteTemplateReference) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(reference.path()).isEqualTo(".versioning/template.md");
			assertThat(reference.ref()).isNull();
		}

		@Test
		void remoteCombinedWithSections_Throws() {
			String template = """
					---
					remote: git@github.com:org/standards.git
					sections:
					  entries: {}
					---
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Template front matter must not combine `remote` with `variables` or `sections`"
					);
		}

		@Test
		void remoteCombinedWithVariables_Throws() {
			String template = """
					---
					remote: git@github.com:org/standards.git
					variables: {}
					---
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Template front matter must not combine `remote` with `variables` or `sections`"
					);
		}

		@Test
		void blankRemote_Throws() {
			String template = """
					---
					remote: " "
					---
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template `remote` must not be blank");
		}

		@Test
		void missingFrontMatter_Throws() {
			assertThatThrownBy(() -> TemplateParser.parse(
					new SystemStreamLog(),
					"{{description}}"
			)).isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template must start with a YAML front matter block (`---`)");
		}

		@Test
		void emptyContent_ThrowsMissingFrontMatter() {
			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), ""))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template must start with a YAML front matter block (`---`)");
		}

		@Test
		void unclosedFrontMatter_Throws() {
			String template = """
					---
					variables:
					  description: {}
					{{description}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template YAML front matter block is not closed (`---`)");
		}

		@Test
		void emptyFrontMatter_Throws() {
			String template = """
					---
					---
					body
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template front matter must not be empty");
		}

		@Test
		void nullFrontMatter_Throws() {
			String template = """
					---
					null
					---
					body
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template front matter must not be empty");
		}

		@Test
		void malformedYaml_ThrowsWithJacksonCause() {
			String template = """
					---
					variables: [unclosed
					---
					body
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageStartingWith("Template front matter is not valid:")
					.hasCauseInstanceOf(tools.jackson.core.JacksonException.class);
		}

		@Test
		void unknownFrontMatterKey_Throws() {
			String template = """
					---
					variables:
					  description: {}
					unknown: true
					---
					{{description}}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageStartingWith("Template front matter is not valid:");
		}

		@Test
		void blankRemotePath_Throws() {
			String template = """
					---
					remote: git@github.com:org/standards.git
					path: " "
					---
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Remote template `path` must not be blank");
		}

		@Test
		void unusedVariable_LogsWarning() throws MojoFailureException {
			String template = """
					---
					variables:
					  issueKey: {}
					  unusedVariable: {}
					---
					{{issueKey}}
					""";
			TestLog log = new TestLog(TestLog.LogLevel.DEBUG);

			TemplateParser.parse(log, template);

			assertThat(log.getLogRecords()).anySatisfy(record -> assertThat(record)
					.hasFieldOrPropertyWithValue("level", TestLog.LogLevel.WARN)
					.hasFieldOrPropertyWithValue(
							"message",
							Optional.of(
									"Template variable `unusedVariable` is declared but never used in the body"
							)
					));
		}
	}
}
