package io.github.bsels.semantic.version.template;

import java.util.Objects;

/// A variable prompt in its enclosing block.
///
/// @param name variable name; never null
public record VariableNode(String name) implements PromptNode {

    /// Validates the variable name.
    public VariableNode {
        Objects.requireNonNull(name, "`name` must not be null");
    }
}
