package io.github.bsels.semantic.version.template;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TemplatePrompterTest {

	private static final TemplateDefinition SINGLE = new TemplateDefinition(
			false,
			List.of(new TemplateVariable("description", "What changed?", null)),
			"{description}\n"
	);

	private static final TemplateDefinition REPEATABLE = new TemplateDefinition(
			true,
			List.of(
					new TemplateVariable("issueKey", "Jira issue key", Pattern.compile("[A-Z]+-\\d+")),
					new TemplateVariable("description", "What changed?", null)
			),
			"- [{issueKey}] {description}\n"
	);

	private final InputStream originalSystemIn = System.in;
	private final PrintStream originalSystemOut = System.out;
	private ByteArrayOutputStream output;

	@BeforeEach
	void setUp() {
		output = new ByteArrayOutputStream();
		System.setOut(new PrintStream(output));
	}

	@AfterEach
	void tearDown() {
		System.setIn(originalSystemIn);
		System.setOut(originalSystemOut);
	}

	private void setInput(String input) {
		System.setIn(new ByteArrayInputStream(input.getBytes()));
	}

	@Nested
	class PromptForValuesTest {

		@Test
		void singleDefinition_ReturnsOneValueSet() throws MojoFailureException {
			setInput("Setup repository\n");

			assertThat(TemplatePrompter.promptForValues(SINGLE))
					.containsExactly(Map.of("description", "Setup repository"));
			assertThat(output.toString()).contains("What changed?");
		}

		@Test
		void invalidAndBlankValues_RePrompt() throws MojoFailureException {
			setInput("\ninvalid\nISSUE-235\nSetup repository\nn\n");

			assertThat(TemplatePrompter.promptForValues(REPEATABLE))
					.containsExactly(Map.of(
							"issueKey", "ISSUE-235",
							"description", "Setup repository"
					));
			assertThat(output.toString())
					.contains("must not be blank")
					.contains("does not match");
		}

		@Test
		void repeatableTemplate_CollectsMultipleSets() throws MojoFailureException {
			setInput("ISSUE-235\nSetup repository\ny\nISSUE-236\nFix pipeline\nn\n");

			assertThat(TemplatePrompter.promptForValues(REPEATABLE))
					.containsExactly(
							Map.of("issueKey", "ISSUE-235", "description", "Setup repository"),
							Map.of("issueKey", "ISSUE-236", "description", "Fix pipeline")
					);
		}

		@Test
		void unusedVariable_IsNotPrompted() throws MojoFailureException {
			TemplateDefinition definition = new TemplateDefinition(
					false,
					List.of(
							new TemplateVariable("description", "What changed?", null),
							new TemplateVariable("unused", "Do not ask", null)
					),
					"{description}\n"
			);
			setInput("Setup repository\n");

			assertThat(TemplatePrompter.promptForValues(definition))
					.containsExactly(Map.of("description", "Setup repository"));
			assertThat(output.toString()).doesNotContain("Do not ask");
		}

		@Test
		void exhaustedInput_Throws() {
			setInput("");

			assertThatThrownBy(() -> TemplatePrompter.promptForValues(SINGLE))
					.isInstanceOf(MojoFailureException.class)
					.hasMessageContaining("No input");
		}
	}
}
