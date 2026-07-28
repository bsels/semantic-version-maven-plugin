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
import java.util.Objects;
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
		void fullTemplate_ReturnsDefinitionInDeclarationOrder() throws Exception {
			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					readTemplateResource("repeatable.md")
			);

			assertThat(definition.repeatable()).isTrue();
			assertThat(definition.variables()).extracting(TemplateVariable::name)
					.containsExactly("issueKey", "description");
			assertThat(definition.variables().get(0).prompt()).isEqualTo("Jira issue key");
			assertThat(definition.variables().get(0).pattern().pattern()).isEqualTo("[A-Z]+-\\d+");
			assertThat(definition.body()).isEqualTo("""
					- [{issueKey}](https://company.atlassian.net/browse/{issueKey})
					    - {description}
					""");
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
		void defaultsPromptAndRepeatable() throws MojoFailureException {
			String template = """
					---
					variables:
					  description: {}
					---
					{description}
					""";

			TemplateDefinition definition = (TemplateDefinition) TemplateParser.parse(
					new SystemStreamLog(),
					template
			);

			assertThat(definition.repeatable()).isFalse();
			assertThat(definition.variables().get(0).prompt()).isEqualTo("description");
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
		void missingFrontMatter_Throws() {
			assertThatThrownBy(() -> TemplateParser.parse(
					new SystemStreamLog(),
					"- {description}"
			)).isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Template must start with a YAML front matter block (`---`)");
		}

		@Test
		void unclosedFrontMatter_Throws() {
			String template = """
					---
					variables:
					  description: {}
					{description}
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
		void missingVariablesAndRemote_Throws() {
			String template = """
					---
					repeatable: true
					---
					static body
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Template front matter must declare `variables` (or a `remote` reference)"
					);
		}

		@Test
		void emptyVariables_Throws() {
			String template = """
					---
					variables: {}
					---
					static body
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Template front matter must declare `variables` (or a `remote` reference)"
					);
		}

		@Test
		void invalidVariableName_Throws() {
			String template = """
					---
					variables:
					  issue-key: {}
					---
					{issueKey}
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
					{issueKey}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Prompt of template variable `issueKey` must not be blank");
		}

		@Test
		void undeclaredPlaceholder_ThrowsAndListsDeclarations() {
			String template = """
					---
					variables:
					  issueKey: {}
					---
					{issueKey} {descriptionTypo}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Template body references undeclared placeholder(s) [descriptionTypo]; "
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
					{issueKey}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageStartingWith(
							"Pattern of template variable `issueKey` is not a valid regular expression:"
					)
					.hasCauseInstanceOf(PatternSyntaxException.class);
		}

		@Test
		void remoteCombinedWithVariables_Throws() {
			String template = """
					---
					remote: git@github.com:org/standards.git
					variables:
					  issueKey: {}
					---
					{issueKey}
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Template front matter must not combine `remote` with `variables` or `repeatable`"
					);
		}

		@Test
		void remoteCombinedWithRepeatable_Throws() {
			String template = """
					---
					remote: git@github.com:org/standards.git
					repeatable: false
					---
					""";

			assertThatThrownBy(() -> TemplateParser.parse(new SystemStreamLog(), template))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Template front matter must not combine `remote` with `variables` or `repeatable`"
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
					{issueKey}
					""";
			TestLog log = new TestLog(TestLog.LogLevel.DEBUG);

			TemplateParser.parse(log, template);

			assertThat(log.getLogRecords()).anySatisfy(record -> assertThat(record)
					.hasFieldOrPropertyWithValue("level", TestLog.LogLevel.WARN)
					.hasFieldOrPropertyWithValue(
							"message",
							java.util.Optional.of(
									"Template variable `unusedVariable` is declared but never used in the body"
							)
					));
		}
	}
}
