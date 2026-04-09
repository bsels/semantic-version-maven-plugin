# Changelog

## 1.3.3 - 2026-04-09

### Patch

Updated project dependencies:

- Bumped `commonmark` from **0.27.1** to **0.28.0**
- Bumped `jackson-databind` / `jackson-dataformat-yaml` from **2.21.0** to **2.21.2**
- Added an override for `plexus-utils` to **3.6.1** to address transitive dependency concerns

## 1.3.2 - 2026-03-21

### Patch

Fixed default date formatting

## 1.3.1 - 2026-03-14

### Patch

Bumped dependencies:

- `maven-core` from **3.9.12** to **3.9.14**
- `maven-surefire-plugin` from **3.5.4** to **3.5.5**
- `junit-jupiter` from **6.0.2** to **6.0.3**
- `mockito` from **5.21.0** to **5.23.0**

## 1.3.0 - 2026-02-14

### Minor

Added new `graph` goal, to list the different project and their internal dependencies.

### Patch

Updated the following project dependencies:

- `maven-dependency-plugin` from `3.9.0` to `3.10.0`
- `maven-compiler-plugin` from `3.14.1` to `3.15.0`
- `assertj-core` from `3.27.6` to `3.27.7`

## 1.2.0 - 2026-01-25

### Minor

Make the headers from the CHANGELOG.md configurable.

Added a verification mojo for validation in pull requests.

### Patch

Bumped the `commonmark` version from **0.27.0** to **0.27.1**.

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
