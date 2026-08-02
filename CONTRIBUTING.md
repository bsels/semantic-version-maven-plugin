# Contributing to semantic-version-maven-plugin

Thank you for your interest in contributing to the Semantic Version Maven Plugin!
All contributions are welcome, including bug reports, feature requests, and pull requests.

## Code Style

To maintain a consistent codebase, this project uses specific code style configurations.
Before submitting a pull request, please ensure your code follows these standards.

You can find the configuration files in the `code-style` directory:

- **EditorConfig**: `.editorconfig` is provided for general editor settings (indentation, charset, etc.).
- **IntelliJ IDEA**: `idea.xml` contains the code style scheme for IntelliJ IDEA.
- **Eclipse**: `eclipse.xml` contains the formatter configuration for Eclipse.

Please import the appropriate configuration into your IDE before you start coding.

### Deprecations

When deprecating code, use the `{DEPRECATION_VERSION}` placeholder in the `@deprecated` Javadoc tag or `@Deprecated`
annotation's `since` attribute.
This placeholder will be automatically replaced with the actual version during the release process by the
`update-deprecation-version.sh` script.

Example:
```java
/// @deprecated Use {@link #newMethod()} instead. Since {DEPRECATION_VERSION}.
@Deprecated(since = "{DEPRECATION_VERSION}", forRemoval = true)
public void oldMethod() { ... }
```

## How to Contribute

### 1. Reporting Issues
If you find a bug or have a suggestion for a new feature, please open an issue on GitHub. When reporting a bug, include:
- A clear and descriptive title.
- Steps to reproduce the issue.
- Expected and actual behavior.
- Relevant logs or screenshots.

### 2. Pull Requests
1. **Fork the repository** and create your branch from `main`.
2. **Implement your changes**. Ensure that your code follows the [Code Style](#code-style) guidelines.
3. **Write tests** for your changes to prevent regressions.
4. **Verify your changes** by running the existing tests:
   ```bash
   ./mvnw clean verify
   ```
5. **Update documentation** if your changes introduce new features or change existing behavior.
6. **Submit a pull request** with a clear description of what your changes do.

### 3. Commit Messages
We recommend using clear and concise commit messages.
If your change is related to an existing issue, please reference it in the commit message.

## Development Environment
- **Java**: 17 or higher
- **Maven**: 3.9.12 or higher (or use the provided `./mvnw` wrapper)

## License
By contributing to this project, you agree that your contributions will be licensed under the project's
[MIT License](LICENSE).
