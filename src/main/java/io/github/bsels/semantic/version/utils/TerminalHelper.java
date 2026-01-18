package io.github.bsels.semantic.version.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Pattern;

/// A utility class providing methods for interacting with terminal input,
/// including functionality for reading multi-line inputs,
/// handling single-choice and multi-choice selections, and validating input data.
///
/// The class is designed to simplify and standardize terminal operations for applications that require user input via
/// a console.
/// It supports flexible input parsing using defined patterns and ensures that the input is processed consistently
/// across use cases.
///
/// All methods in this class are static, and instantiation of the class is not allowed.
public final class TerminalHelper {
    /// A compiled regular expression pattern used to identify separators in multi-choice input strings.
    /// The separators can include commas (,), semicolons (;), or spaces.
    ///
    /// This pattern is used to split user input into distinct components,
    /// usually when multiple choices are entered in a single line separated by the defined delimiters.
    /// It ensures consistent parsing of input strings for multi-choice selection functionality in the application.
    private static final Pattern MULTI_CHOICE_SEPARATOR = Pattern.compile("[,; ]");

    /// A private constructor to prevent instantiation of the `TerminalHelper` class.
    ///
    /// The `TerminalHelper` class is designed to provide static utility methods for terminal input management,
    /// including functionality for reading multi-line input, single-choice selection, multi-choice selection,
    /// and related operations.
    /// Since all functionality is provided through static methods, there is no need to create instances of this class.
    private TerminalHelper() {
        // No instance needed
    }

    /// Reads multi-line input from the user via the console.
    /// The method prompts the user with a message,
    /// then reads lines of text input until it encounters two consecutive blank lines,
    /// treating this as the end of input.
    /// The input is returned as a single string containing all the lines, separated by new line characters.
    /// If no meaningful input is provided (the first line is blank), the method returns an empty [Optional].
    ///
    /// @param prompt the message to display to the user before starting input; must not be null
    /// @return an [Optional] containing the concatenated multi-line input if provided, or an empty [Optional] if the input was blank
    public static Optional<String> readMultiLineInput(String prompt) {
        System.out.println(prompt);
        Scanner scanner = new Scanner(System.in);
        StringBuilder builder = new StringBuilder();
        String line = scanner.nextLine();
        if (line.isBlank()) {
            return Optional.empty();
        }
        builder.append(line).append("\n");
        boolean lastLineEmpty = line.isBlank();
        line = scanner.nextLine();
        while (!line.isBlank() || !lastLineEmpty) {
            builder.append(line).append("\n");
            lastLineEmpty = line.isBlank();
            line = scanner.nextLine();
        }
        return Optional.of(builder.toString());
    }

    /// Displays a list of choices to the user and allows selection of a single option
    /// by entering its corresponding number or name (for enum values).
    ///
    /// @param <T>          the type of items in the choice list
    /// @param choiceHeader a header message displayed above the list of choices; must not be null
    /// @param promptObject a string describing the individual choice objects, used in the prompt message; must not be null
    /// @param choices      a list of selectable options; must not be null or empty, and each item in the list must not be null
    /// @return the selected item from the choices, based on the user's input
    /// @throws NullPointerException     if `choiceHeader`, `promptObject`, `choices`, or any element in the `choices` list is null
    /// @throws IllegalArgumentException if the `choices` list is empty
    public static <T> T singleChoice(String choiceHeader, String promptObject, List<T> choices)
            throws NullPointerException, IllegalArgumentException {
        validateChoiceMethodHeader(choiceHeader, promptObject, choices);
        boolean isEnum = Enum.class.isAssignableFrom(choices.get(0).getClass());
        Scanner scanner = new Scanner(System.in);
        Optional<T> item = Optional.empty();
        while (item.isEmpty()) {
            System.out.println(choiceHeader);
            for (int i = 0; i < choices.size(); i++) {
                System.out.printf("  %d: %s%n", i + 1, choices.get(i));
            }
            if (isEnum) {
                System.out.printf("Enter %s name or number: ", promptObject);
            } else {
                System.out.printf("Enter %s number: ", promptObject);
            }
            String line = scanner.nextLine();
            item = parseIndexOrEnum(line, choices);
        }
        return item.get();
    }

    /// Displays a list of choices to the user and allows them to select multiple options by entering their corresponding numbers.
    /// The user's choices are captured based on the provided prompt and returned as a list.
    ///
    /// @param <T>          the type of items in the choice list
    /// @param choiceHeader a header message displayed above the list of choices; must not be null
    /// @param promptObject a string describing the individual choice objects, used in the prompt message; must not be null
    /// @param choices      a list of selectable options; must not be null or empty
    /// @return a list of selected items from the choices, based on the user's input
    /// @throws NullPointerException     if `choiceHeader`, `promptObject`, `choices`, or any element in the `choices` list is null
    /// @throws IllegalArgumentException if the `choices` list is empty
    public static <T> List<T> multiChoice(String choiceHeader, String promptObject, List<T> choices)
            throws NullPointerException, IllegalArgumentException {
        validateChoiceMethodHeader(choiceHeader, promptObject, choices);
        if (choices.isEmpty()) {
            return List.of();
        }
        boolean isEnum = Enum.class.isAssignableFrom(choices.get(0).getClass());
        Scanner scanner = new Scanner(System.in);
        List<T> selectedChoices = null;
        while (selectedChoices == null) {
            System.out.println(choiceHeader);
            for (int i = 0; i < choices.size(); i++) {
                System.out.printf("  %d: %s%n", i + 1, choices.get(i));
            }
            if (isEnum) {
                System.out.printf("Enter %s names or number separated by spaces, commas or semicolons: ", promptObject);
            } else {
                System.out.printf("Enter %s numbers separated by spaces, commas or semicolons: ", promptObject);
            }
            String line = scanner.nextLine();
            if (!line.isBlank()) {
                List<T> currentSelection = new ArrayList<>(choices.size());
                for (String choice : MULTI_CHOICE_SEPARATOR.split(line)) {
                    Optional<T> item = parseIndexOrEnum(choice, choices);
                    if (item.isPresent()) {
                        currentSelection.add(item.get());
                    } else {
                        System.out.printf("Invalid %s: %s%n", promptObject, choice);
                        currentSelection = null;
                        break;
                    }
                }
                selectedChoices = currentSelection;
            }
        }
        return selectedChoices;
    }

    /// Validates the parameters for choice-related methods to ensure all required inputs are provided and not null.
    ///
    /// @param <T>          the type of items in the choice list
    /// @param choiceHeader a header message displayed above the list of choices; must not be null
    /// @param promptObject a string describing the individual choice objects, used in the prompt message; must not be null
    /// @param choices      a list of selectable options; each item in the list must not be null
    /// @throws NullPointerException     if `choiceHeader`, `promptObject`, `choices`, or any element in the `choices` list is null
    /// @throws IllegalArgumentException if `choices` is empty
    private static <T> void validateChoiceMethodHeader(String choiceHeader, String promptObject, List<T> choices)
            throws NullPointerException, IllegalArgumentException {
        Objects.requireNonNull(choiceHeader, "`choiceHeader` must not be null");
        Objects.requireNonNull(promptObject, "`promptObject` must not be null");
        Objects.requireNonNull(choices, "`choices` must not be null");
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("No choices provided");
        }
        for (T choice : choices) {
            Objects.requireNonNull(choice, "All choices must not be null");
        }
    }

    /// Parses a given string value to determine if it corresponds to an index or matches an enum name
    /// in a provided list of choices.
    /// If the value is a valid index, retrieves the corresponding item.
    /// If the value matches the name of an enum (ignoring case), retrieves the matched enum.
    ///
    /// @param <T>     the type of items in the choice list; can include enums or other types
    /// @param value   the string input to be parsed; must not be null
    /// @param choices a list of selectable options; must not be null or empty
    /// @return an [Optional] containing the matched item from the choices, or an empty [Optional] if no matching item is found or the input is invalid
    private static <T> Optional<T> parseIndexOrEnum(String value, List<T> choices) {
        String stripped = value.strip();
        try {
            return Optional.of(choices.get(Integer.parseInt(stripped) - 1));
        } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
            return choices.stream()
                    .filter(Enum.class::isInstance)
                    .filter(item -> ((Enum<?>) item).name().equalsIgnoreCase(stripped))
                    .findFirst();
        }
    }
}
