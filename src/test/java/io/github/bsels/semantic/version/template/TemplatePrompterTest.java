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

    private final InputStream originalSystemIn = System.in;
    private final PrintStream originalSystemOut = System.out;
    private final TemplateVariable ISSUE_KEY = new TemplateVariable(
            "issueKey",
            "Jira issue key",
            Pattern.compile("[A-Z]+-\\d+")
    );
    private final TemplateVariable DESCRIPTION = new TemplateVariable(
            "description",
            "What changed?",
            null
    );
    private final TemplateDefinition SINGLE = new TemplateDefinition(
            "{{description}}\n",
            List.of(DESCRIPTION),
            Map.of(),
            List.of(new VariableNode("description"))
    );
    private final TemplateDefinition REPEATABLE = new TemplateDefinition(
            "{{#entries}}- [{{issueKey}}] {{description}}\n{{/entries}}",
            List.of(ISSUE_KEY, DESCRIPTION),
            Map.of("entries", "Add another entry?"),
            List.of(new SectionNode(
                    "entries",
                    List.of(
                            new VariableNode("issueKey"),
                            new VariableNode("description")
                    )
            ))
    );
    private final TemplateDefinition NESTED = new TemplateDefinition(
            "",
            List.of(ISSUE_KEY, DESCRIPTION),
            Map.of(
                    "issues", "Add another issue?",
                    "descriptions", "More descriptions?"
            ),
            List.of(new SectionNode(
                    "issues",
                    List.of(
                            new VariableNode("issueKey"),
                            new SectionNode(
                                    "descriptions",
                                    List.of(new VariableNode("description"))
                            )
                    )
            ))
    );
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
        void topLevelVariable_ReturnsOneDataMap() throws MojoFailureException {
            setInput("Setup repository\n");

            assertThat(TemplatePrompter.promptForValues(SINGLE))
                    .isEqualTo(Map.of("description", "Setup repository"));
            assertThat(output.toString()).contains("What changed?: ");
        }

        @Test
        void invalidAndBlankValues_RePrompt() throws MojoFailureException {
            setInput("\ninvalid\nISSUE-235\nSetup repository\nn\n");

            assertThat(TemplatePrompter.promptForValues(REPEATABLE))
                    .isEqualTo(Map.of(
                            "entries",
                            List.of(Map.of(
                                    "issueKey", "ISSUE-235",
                                    "description", "Setup repository"
                            ))
                    ));
            assertThat(output.toString())
                    .contains("Value must not be blank")
                    .contains("Value does not match pattern `[A-Z]+-\\d+`")
                    .contains("Add another entry? [y/N]: ");
        }

        @Test
        void section_CollectsAtLeastOneIterationWhenInputEnds() throws MojoFailureException {
            setInput("ISSUE-235\nSetup repository\n");

            assertThat(TemplatePrompter.promptForValues(REPEATABLE))
                    .isEqualTo(Map.of(
                            "entries",
                            List.of(Map.of(
                                    "issueKey", "ISSUE-235",
                                    "description", "Setup repository"
                            ))
                    ));
        }

        @Test
        void section_CollectsMultipleIterations() throws MojoFailureException {
            setInput("ISSUE-235\nSetup repository\ny\nISSUE-236\nFix pipeline\nn\n");

            assertThat(TemplatePrompter.promptForValues(REPEATABLE))
                    .isEqualTo(Map.of(
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
                    ));
        }

        @Test
        void nestedSections_CollectNestedDataInDocumentOrder() throws MojoFailureException {
            setInput("""
                    ISSUE-235
                    Setup repository
                    y
                    Configure CI
                    n
                    y
                    ISSUE-236
                    Fix pipeline
                    n
                    n
                    """);

            assertThat(TemplatePrompter.promptForValues(NESTED))
                    .isEqualTo(Map.of(
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
                    ));

            String prompts = output.toString();
            assertThat(prompts.indexOf("What changed?: "))
                    .isLessThan(prompts.indexOf("More descriptions? [y/N]: "));
            assertThat(prompts.indexOf("More descriptions? [y/N]: "))
                    .isLessThan(prompts.indexOf("Add another issue? [y/N]: "));
        }

        @Test
        void exhaustedInputWhileVariableRequired_Throws() {
            setInput("");

            assertThatThrownBy(() -> TemplatePrompter.promptForValues(SINGLE))
                    .isInstanceOf(MojoFailureException.class)
                    .hasMessage(
                            "No input available while prompting for template variable `description`"
                    );
        }
    }
}
