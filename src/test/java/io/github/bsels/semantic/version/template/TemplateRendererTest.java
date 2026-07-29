package io.github.bsels.semantic.version.template;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class TemplateRendererTest {

	private TemplateDefinition definition(String body) {
		return new TemplateDefinition(body, List.of(), Map.of(), List.of());
	}

	@Nested
	class RenderTest {

		@Test
		void variable_SubstitutesEveryOccurrenceWithoutHtmlEscaping() {
			String rendered = TemplateRenderer.render(
					definition("{{description}} / {{description}}\n"),
					Map.of("description", "<setup> & deploy")
			);

			assertThat(rendered).isEqualTo("<setup> & deploy / <setup> & deploy\n");
		}

		@Test
		void section_RendersEveryIterationOnce() {
			TemplateDefinition definition = definition("""
					## Changes
					{{#entries}}
					- [{{issueKey}}] {{description}}
					{{/entries}}
					""");

			String rendered = TemplateRenderer.render(
					definition,
					Map.of(
							"entries",
							List.of(
									Map.of(
											"issueKey", "ISSUE-235",
											"description", "Setup repository"
									),
									Map.of(
											"issueKey", "ISSUE-236",
											"description", "Fix pipeline"
									)
							)
					)
			);

			assertThat(rendered).isEqualTo("""
					## Changes
					- [ISSUE-235] Setup repository
					- [ISSUE-236] Fix pipeline
					""");
		}

		@Test
		void nestedSections_RenderInnerIterationsAndOuterContext() {
			TemplateDefinition definition = definition("""
					{{#issues}}
					- {{issueKey}}
					{{#descriptions}}
					    - {{issueKey}}: {{description}}
					{{/descriptions}}
					{{/issues}}
					""");

			String rendered = TemplateRenderer.render(
					definition,
					Map.of(
							"issues",
							List.of(
									Map.of(
											"issueKey", "ISSUE-235",
											"descriptions", List.of(
													Map.of("description", "Setup repository"),
													Map.of("description", "Configure CI")
											)
									),
									Map.of(
											"issueKey", "ISSUE-236",
											"descriptions", List.of(
													Map.of("description", "Fix pipeline")
											)
									)
							)
					)
			);

			assertThat(rendered).isEqualTo("""
					- ISSUE-235
					    - ISSUE-235: Setup repository
					    - ISSUE-235: Configure CI
					- ISSUE-236
					    - ISSUE-236: Fix pipeline
					""");
		}

		@Test
		void nullArgument_Throws() {
			assertThatNullPointerException()
					.isThrownBy(() -> TemplateRenderer.render(null, Map.of()))
					.withMessage("`definition` must not be null");
			assertThatNullPointerException()
					.isThrownBy(() -> TemplateRenderer.render(definition("body"), null))
					.withMessage("`data` must not be null");
		}
	}
}
