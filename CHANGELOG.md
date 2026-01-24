# Changelog

## 1.1.0 - 2026-01-24

### Minor

Support additional script execution during version bump for more customization

Added mode to only use artifact ID as identifier in the file based versioning.

Make the version header configurable with placeholders for the version and the date and allow custom date formats.

Added support for git for automated stashing or committing from files.

### Patch

Bumped dependencies:

- Jackson from 2.20.1 to 2.21.0
- JUnit from 6.0.1 to 6.0.2
- Central publishing maven plugin from 0.9.0 to 0.10.0

## 1.0.0 - 2026-01-20

### Major

Initial version of the **semantic-version-maven-plugin**.
