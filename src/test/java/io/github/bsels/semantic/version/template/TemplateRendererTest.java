package io.github.bsels.semantic.version.template;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TemplateRendererTest {

	private static final TemplateDefinition DEFINITION = new TemplateDefinition(
			true,
			List.of(
					new TemplateVariable("issueKey", "Issue key", null),
					new TemplateVariable("description", "Description", null)
			),
			"""
					- [{issueKey}](https://example.com/{issueKey})
					    - {description}
					"""
	);

	@Nested
	class RenderTest {

		@Test
		void emptyValueSets_ThrowsIllegalArgumentException() {
			assertThatThrownBy(() -> TemplateRenderer.render(DEFINITION, List.of()))
					.isExactlyInstanceOf(IllegalArgumentException.class)
					.hasMessage("`valueSets` must not be empty");
		}

		@Test
		void singleValueSet_SubstitutesEveryOccurrence() {
			String rendered = TemplateRenderer.render(
					DEFINITION,
					List.of(Map.of(
							"issueKey", "ISSUE-235",
							"description", "Setup repository"
					))
			);

			assertThat(rendered).isEqualTo("""
					- [ISSUE-235](https://example.com/ISSUE-235)
					    - Setup repository
					""");
		}

		@Test
		void multipleValueSets_ConcatenatesBlocks() {
			String rendered = TemplateRenderer.render(
					DEFINITION,
					List.of(
							Map.of("issueKey", "ISSUE-235", "description", "Setup repository"),
							Map.of("issueKey", "ISSUE-236", "description", "Fix pipeline")
					)
			);

			assertThat(rendered).isEqualTo("""
					- [ISSUE-235](https://example.com/ISSUE-235)
					    - Setup repository
					- [ISSUE-236](https://example.com/ISSUE-236)
					    - Fix pipeline
					""");
		}

		@Test
		void missingUsedValue_Throws() {
			assertThatThrownBy(() -> TemplateRenderer.render(
					DEFINITION,
					List.of(Map.of("issueKey", "ISSUE-235"))
			)).isExactlyInstanceOf(IllegalArgumentException.class)
					.hasMessage("Missing value for template variable `description`");
		}

		@Test
		void valueContainingAnotherPlaceholder_IsNotSubstitutedAgain() {
			String rendered = TemplateRenderer.render(
					DEFINITION,
					List.of(Map.of(
							"issueKey", "{description}",
							"description", "$5 setup"
					))
			);

			assertThat(rendered).isEqualTo("""
					- [{description}](https://example.com/{description})
					    - $5 setup
					""");
		}
	}
}
