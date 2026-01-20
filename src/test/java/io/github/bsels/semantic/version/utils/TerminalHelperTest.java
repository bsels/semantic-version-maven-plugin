package io.github.bsels.semantic.version.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TerminalHelperTest {

    private final InputStream originalSystemIn = System.in;
    private final PrintStream originalSystemOut = System.out;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalSystemIn);
        System.setOut(originalSystemOut);
    }

    private void setSystemIn(String input) {
        System.setIn(new ByteArrayInputStream(input.getBytes()));
    }

    private String getOutput() {
        return outputStream.toString();
    }

    private enum TestEnum {
        FIRST, SECOND, THIRD
    }

    @Nested
    class ReadMultiLineInputTest {

        @Test
        void nullPrompt_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.readMultiLineInput(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`prompt` must not be null");
        }

        @Test
        void firstLineBlank_ReturnsEmpty() {
            setSystemIn("\n");

            Optional<String> result = TerminalHelper.readMultiLineInput("Enter text:");

            assertThat(result).isEmpty();
            assertThat(getOutput()).contains("Enter text:");
        }

        @Test
        void singleLineInput_TwoBlankLines_ReturnsInput() {
            setSystemIn("First line\n\n\n");

            Optional<String> result = TerminalHelper.readMultiLineInput("Enter text:");

            assertThat(result)
                    .isPresent()
                    .hasValue("First line");
        }

        @Test
        void multiLineInput_TwoConsecutiveBlankLines_ReturnsAllLines() {
            setSystemIn("Line 1\nLine 2\nLine 3\n\n\n");

            Optional<String> result = TerminalHelper.readMultiLineInput("Enter text:");

            assertThat(result)
                    .isPresent()
                    .hasValue("Line 1\nLine 2\nLine 3");
        }

        @Test
        void multiLineInputWithSingleBlankLine_ThenTwoBlankLines_ReturnsAllLines() {
            setSystemIn("Line 1\n\nLine 2\n\n\n");

            Optional<String> result = TerminalHelper.readMultiLineInput("Enter text:");

            assertThat(result)
                    .isPresent()
                    .hasValue("Line 1\n\nLine 2");
        }

        @Test
        void promptIsDisplayed() {
            setSystemIn("\n");

            TerminalHelper.readMultiLineInput("Custom prompt message:");

            assertThat(getOutput()).isEqualTo("Custom prompt message:\n");
        }
    }

    @Nested
    class SingleChoiceTest {

        @Test
        void nullChoiceHeader_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.singleChoice(null, "item", List.of("A", "B")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`choiceHeader` must not be null");
        }

        @Test
        void nullPromptObject_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.singleChoice("Header", null, List.of("A", "B")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`promptObject` must not be null");
        }

        @Test
        void nullChoices_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.singleChoice("Header", "item", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`choices` must not be null");
        }

        @Test
        void emptyChoices_ThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> TerminalHelper.singleChoice("Header", "item", List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("No choices provided");
        }

        @Test
        void choicesContainsNull_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.singleChoice("Header", "item", Arrays.asList("A", null, "C")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("All choices must not be null");
        }

        @Test
        void validNumberInput_ReturnsCorrectChoice() {
            setSystemIn("2\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            String result = TerminalHelper.singleChoice("Select fruit:", "fruit", choices);

            assertThat(result).isEqualTo("Banana");
            assertThat(getOutput())
                    .contains("Select fruit:")
                    .contains("1: Apple")
                    .contains("2: Banana")
                    .contains("3: Cherry")
                    .contains("Enter fruit number:");
        }

        @Test
        void firstChoiceByNumber_ReturnsFirstChoice() {
            setSystemIn("1\n");
            List<String> choices = List.of("First", "Second", "Third");

            String result = TerminalHelper.singleChoice("Select:", "option", choices);

            assertThat(result).isEqualTo("First");
        }

        @Test
        void lastChoiceByNumber_ReturnsLastChoice() {
            setSystemIn("3\n");
            List<String> choices = List.of("First", "Second", "Third");

            String result = TerminalHelper.singleChoice("Select:", "option", choices);

            assertThat(result).isEqualTo("Third");
        }

        @Test
        void enumChoice_ValidNumber_ReturnsCorrectEnum() {
            setSystemIn("2\n");
            List<TestEnum> choices = List.of(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD);

            TestEnum result = TerminalHelper.singleChoice("Select enum:", "enum", choices);

            assertThat(result).isEqualTo(TestEnum.SECOND);
            assertThat(getOutput()).contains("Enter enum name or number:");
        }

        @Test
        void enumChoice_ValidName_ReturnsCorrectEnum() {
            setSystemIn("SECOND\n");
            List<TestEnum> choices = List.of(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD);

            TestEnum result = TerminalHelper.singleChoice("Select enum:", "enum", choices);

            assertThat(result).isEqualTo(TestEnum.SECOND);
        }

        @Test
        void enumChoice_ValidNameCaseInsensitive_ReturnsCorrectEnum() {
            setSystemIn("second\n");
            List<TestEnum> choices = List.of(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD);

            TestEnum result = TerminalHelper.singleChoice("Select enum:", "enum", choices);

            assertThat(result).isEqualTo(TestEnum.SECOND);
        }

        @Test
        void enumChoice_ValidNameMixedCase_ReturnsCorrectEnum() {
            setSystemIn("SeCOnD\n");
            List<TestEnum> choices = List.of(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD);

            TestEnum result = TerminalHelper.singleChoice("Select enum:", "enum", choices);

            assertThat(result).isEqualTo(TestEnum.SECOND);
        }

        @Test
        void invalidInputThenValid_RepromptsAndReturnsCorrectChoice() {
            setSystemIn("0\n2\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            String result = TerminalHelper.singleChoice("Select fruit:", "fruit", choices);

            assertThat(result).isEqualTo("Banana");
            assertThat(getOutput()).contains("Select fruit:");
        }

        @Test
        void numberOutOfRangeThenValid_RepromptsAndReturnsCorrectChoice() {
            setSystemIn("10\n1\n");
            List<String> choices = List.of("Apple", "Banana");

            String result = TerminalHelper.singleChoice("Select fruit:", "fruit", choices);

            assertThat(result).isEqualTo("Apple");
        }

        @Test
        void negativeNumberThenValid_RepromptsAndReturnsCorrectChoice() {
            setSystemIn("-1\n1\n");
            List<String> choices = List.of("Apple", "Banana");

            String result = TerminalHelper.singleChoice("Select fruit:", "fruit", choices);

            assertThat(result).isEqualTo("Apple");
        }

        @Test
        void nonNumericInputForNonEnum_RepromptsAndReturnsCorrectChoice() {
            setSystemIn("invalid\n2\n");
            List<String> choices = List.of("Apple", "Banana");

            String result = TerminalHelper.singleChoice("Select fruit:", "fruit", choices);

            assertThat(result).isEqualTo("Banana");
        }

        @Test
        void inputWithWhitespace_TrimsAndReturnsCorrectChoice() {
            setSystemIn("  2  \n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            String result = TerminalHelper.singleChoice("Select fruit:", "fruit", choices);

            assertThat(result).isEqualTo("Banana");
        }
    }

    @Nested
    class MultiChoiceTest {

        @Test
        void nullChoiceHeader_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.multiChoice(null, "item", List.of("A", "B")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`choiceHeader` must not be null");
        }

        @Test
        void nullPromptObject_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.multiChoice("Header", null, List.of("A", "B")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`promptObject` must not be null");
        }

        @Test
        void nullChoices_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.multiChoice("Header", "item", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("`choices` must not be null");
        }

        @Test
        void emptyChoices_ReturnsEmptyList() {
            assertThatThrownBy(() -> TerminalHelper.multiChoice("Header", "item", List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("No choices provided");
        }

        @Test
        void choicesContainsNull_ThrowsNullPointerException() {
            assertThatThrownBy(() -> TerminalHelper.multiChoice("Header", "item", Arrays.asList("A", null, "C")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("All choices must not be null");
        }

        @Test
        void singleNumberInput_ReturnsSingleChoice() {
            setSystemIn("2\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(1)
                    .containsExactly("Banana");
            assertThat(getOutput())
                    .contains("Select fruits:")
                    .contains("1: Apple")
                    .contains("2: Banana")
                    .contains("3: Cherry")
                    .contains("Enter fruit numbers separated by spaces, commas or semicolons:");
        }

        @Test
        void multipleNumbersSpaceSeparated_ReturnsMultipleChoices() {
            setSystemIn("1 2 3\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(3)
                    .containsExactly("Apple", "Banana", "Cherry");
        }

        @Test
        void multipleNumbersCommaSeparated_ReturnsMultipleChoices() {
            setSystemIn("1,2,3\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(3)
                    .containsExactly("Apple", "Banana", "Cherry");
        }

        @Test
        void multipleNumbersSemicolonSeparated_ReturnsMultipleChoices() {
            setSystemIn("1;2;3\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(3)
                    .containsExactly("Apple", "Banana", "Cherry");
        }

        @Test
        void multipleNumbersMixedSeparators_ReturnsMultipleChoices() {
            setSystemIn("1, 2; 3\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(3)
                    .containsExactly("Apple", "Banana", "Cherry");
        }

        @Test
        void enumChoice_ValidNumbers_ReturnsCorrectEnums() {
            setSystemIn("1 3\n");
            List<TestEnum> choices = List.of(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD);

            List<TestEnum> result = TerminalHelper.multiChoice("Select enums:", "enum", choices);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly(TestEnum.FIRST, TestEnum.THIRD);
            assertThat(getOutput()).contains("Enter enum names or number separated by spaces, commas or semicolons:");
        }

        @Test
        void enumChoice_ValidNames_ReturnsCorrectEnums() {
            setSystemIn("FIRST THIRD\n");
            List<TestEnum> choices = List.of(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD);

            List<TestEnum> result = TerminalHelper.multiChoice("Select enums:", "enum", choices);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly(TestEnum.FIRST, TestEnum.THIRD);
        }

        @Test
        void enumChoice_MixedNumbersAndNames_ReturnsCorrectEnums() {
            setSystemIn("1 THIRD\n");
            List<TestEnum> choices = List.of(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD);

            List<TestEnum> result = TerminalHelper.multiChoice("Select enums:", "enum", choices);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly(TestEnum.FIRST, TestEnum.THIRD);
        }

        @Test
        void enumChoice_CaseInsensitiveNames_ReturnsCorrectEnums() {
            setSystemIn("first third\n");
            List<TestEnum> choices = List.of(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD);

            List<TestEnum> result = TerminalHelper.multiChoice("Select enums:", "enum", choices);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly(TestEnum.FIRST, TestEnum.THIRD);
        }

        @Test
        void blankInputThenValid_RepromptsAndReturnsCorrectChoices() {
            setSystemIn("\n1 2\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly("Apple", "Banana");
        }

        @Test
        void invalidChoiceThenValid_RepromptsAndReturnsCorrectChoices() {
            setSystemIn("1 99\n1 2\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly("Apple", "Banana");
            assertThat(getOutput()).contains("Invalid fruit: 99");
        }

        @Test
        void invalidChoiceInMiddleThenValid_RepromptsAndReturnsCorrectChoices() {
            setSystemIn("1 invalid 2\n1 2\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly("Apple", "Banana");
            assertThat(getOutput()).contains("Invalid fruit: invalid");
        }

        @Test
        void inputWithExtraWhitespace_TrimsAndReturnsCorrectChoices() {
            setSystemIn("  1   2   3  \n\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(3)
                    .containsExactly("Apple", "Banana", "Cherry");
        }

        @Test
        void duplicateChoices_ReturnsWithDuplicates() {
            setSystemIn("1 1 2\n\n");
            List<String> choices = List.of("Apple", "Banana", "Cherry");

            List<String> result = TerminalHelper.multiChoice("Select fruits:", "fruit", choices);

            assertThat(result)
                    .hasSize(3)
                    .containsExactly("Apple", "Apple", "Banana");
        }
    }
}
