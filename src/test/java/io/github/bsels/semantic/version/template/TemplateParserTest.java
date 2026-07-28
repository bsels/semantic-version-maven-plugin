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
			)).isInstanceOf(MojoFailureException.class)
					.hasMessageContaining("front matter");
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
					.isInstanceOf(MojoFailureException.class)
					.hasMessageContaining("descriptionTypo")
					.hasMessageContaining("issueKey");
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
					.isInstanceOf(MojoFailureException.class)
					.hasMessageContaining("issueKey");
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
					.isInstanceOf(MojoFailureException.class)
					.hasMessageContaining("remote");
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
