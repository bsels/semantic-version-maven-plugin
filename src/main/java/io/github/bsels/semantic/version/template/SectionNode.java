package io.github.bsels.semantic.version.template;

import java.util.List;
import java.util.Objects;

/// A repeatable section and the prompts in one section iteration.
///
/// @param name     section name; never null
/// @param children prompts in document order; never null
public record SectionNode(String name, List<PromptNode> children) implements PromptNode {

	/// Validates the section and copies its child list.
	public SectionNode {
		Objects.requireNonNull(name, "`name` must not be null");
		Objects.requireNonNull(children, "`children` must not be null");
		children = List.copyOf(children);
	}
}
