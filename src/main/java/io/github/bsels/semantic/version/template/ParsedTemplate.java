package io.github.bsels.semantic.version.template;

/// Result of parsing a template file: either a usable [TemplateDefinition]
/// or a [RemoteTemplateReference] pointing to a template in another repository.
public sealed interface ParsedTemplate permits TemplateDefinition, RemoteTemplateReference {
}
