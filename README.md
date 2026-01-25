# semantic-version-maven-plugin

[![Latest version](https://img.shields.io/github/v/release/bsels/semantic-version-maven-plugin?color=blue&label=GitHub+Tag&logo=GitHub)](https://github.com/bsels/semantic-version-maven-plugin/releases)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.bsels/semantic-version-maven-plugin?color=blue&label=Maven+Central&logo=apachemaven)](https://search.maven.org/artifact/io.github.bsels/semantic-version-maven-plugin)

[![Push create release](https://github.com/bsels/semantic-version-maven-plugin/actions/workflows/push-release.yaml/badge.svg)](https://github.com/bsels/semantic-version-maven-plugin/actions/workflows/push-release.yaml)
[![Release Build](https://github.com/bsels/semantic-version-maven-plugin/actions/workflows/release-build.yaml/badge.svg?event=release)](https://github.com/bsels/semantic-version-maven-plugin/actions/workflows/release-build.yaml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Java Version 17](https://img.shields.io/badge/Java_Version-17-purple?logo=data:image/svg+xml;base64,PHN2ZyBoZWlnaHQ9IjIwMHB4IiB3aWR0aD0iMjAwcHgiIHZlcnNpb249IjEuMSIgaWQ9IkNhcGFfMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayIgdmlld0JveD0iMCAwIDUwMi42MzIgNTAyLjYzMiIgeG1sOnNwYWNlPSJwcmVzZXJ2ZSIgZmlsbD0iIzAwMDAwMCI+PGcgaWQ9IlNWR1JlcG9fYmdDYXJyaWVyIiBzdHJva2Utd2hpdGVpZHRoPSIwIj48L2c+PGcgaWQ9IlNWR1JlcG9fdHJhY2VyQ2FycmllciIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48L2c+PGcgaWQ9IlNWR1JlcG9faWNvbkNhcnJpZXIiPiA8Zz4gPGc+IDxwYXRoIHN0eWxlPSJmaWxsOndoaXRlOyIgZD0iTTI0MC44NjQsMjY5Ljg5NGMwLDAtMjguMDItNTMuOTkyLTI2Ljk4NS05My40NDVjMC43NTUtMjguMTkzLDY0LjMyNC01Ni4wNjIsODkuMjgxLTk2LjUyOSBDMzI4LjA3NCwzOS40MzEsMzAwLjA1NCwwLDMwMC4wNTQsMHM2LjIzNCwyOS4wNzctMTAuMzc2LDU5LjE0N2MtMTYuNjA5LDMwLjExMy03Ny45MTQsNDcuNzc5LTEwMS43NDksOTkuNjc5IFMyNDAuODY0LDI2OS44OTQsMjQwLjg2NCwyNjkuODk0eiI+PC9wYXRoPiA8cGF0aCBzdHlsZT0iZmlsbDp3aGl0ZTsiIGQ9Ik0zNDUuNzQxLDEwNS44NjljMCwwLTk1LjQ5NCwzNi4zNDctOTUuNDk0LDc3Ljg0OWMwLDQxLjU0NSwyNS45MjgsNTUuMDI3LDMwLjExMyw2OC41MDkgYzQuMTQyLDEzLjUyNS03LjI2OSwzNi4zNDctNy4yNjksMzYuMzQ3czM3LjM2MS0yNS45NSwzMS4xMDUtNTYuMDYyYy02LjIzNC0zMC4xMTMtMzUuMjktMzkuNDc1LTE4LjY1OS02OS41NDQgQzI5Ni42NDYsMTQyLjc5OSwzNDUuNzQxLDEwNS44NjksMzQ1Ljc0MSwxMDUuODY5eiI+PC9wYXRoPiA8cGF0aCBzdHlsZT0iZmlsbDp3aGl0ZTsiIGQ9Ik0yMzAuNTEsMzI0Ljc0OGM4OC4yNDYtMy4xNDksMTIwLjQzLTMwLjk5NywxMjAuNDMtMzAuOTk3IGMtNTcuMDc2LDE1LjU1My0yMDguNjU0LDE0LjUzOS0yMDkuNzExLDMuMTI4Yy0xLjAxNC0xMS40MTEsNDYuNzAxLTIwLjc3Myw0Ni43MDEtMjAuNzczcy03NC43MjEsMC04MC45NTUsMTguNjggQzEwMC43NCwzMTMuNDY3LDE0Mi4zMjgsMzI3LjgzMywyMzAuNTEsMzI0Ljc0OHoiPjwvcGF0aD4gPHBhdGggc3R5bGU9ImZpbGw6d2hpdGU7IiBkPSJNMzU4LjE4NywzNjguNDk0YzAsMCw4Ni4zNjktMTguNDIxLDc3LjgyNy02NS4zMzhjLTEwLjM1NC01Ny4xMTktNzAuNTgtMjQuOTM2LTcwLjU4LTI0LjkzNiBzNDIuNjAyLDAsNDYuNzIyLDI1LjkyOEM0MTYuMzIsMzMwLjA5OCwzNTguMTg3LDM2OC40OTQsMzU4LjE4NywzNjguNDk0eiI+PC9wYXRoPiA8cGF0aCBzdHlsZT0iZmlsbDp3aGl0ZTsiIGQ9Ik0zMTUuNjI4LDM0My42MDFjMCwwLTIxLjc2NSw1LjcxNi01NC4wMTMsOS4zNGMtNDMuMjI4LDQuODUzLTk1LjQ5NCwxLjAxNC05OS42NTctNi4yNTYgYy00LjA5OC03LjI2OSw3LjI2OS0xMS40MTEsNy4yNjktMTEuNDExYy01MS45MjEsMTIuNDY4LTIzLjUxMiwzNC4yMzMsMzcuMzM5LDM4LjQxOGM1Mi4xNTgsMy41NTksMTI5Ljc5MS0xNS41NzQsMTI5Ljc5MS0xNS41NzQgTDMxNS42MjgsMzQzLjYwMXoiPjwvcGF0aD4gPHBhdGggc3R5bGU9ImZpbGw6d2hpdGU7IiBkPSJNMTgxLjczOCwzODguOTQzYzAsMC0yMy41NTUsMC42NjktMjQuOTM2LDEzLjEzN2MtMS4zNTksMTIuMzgyLDE0LjQ5NiwyMy41MTIsNzIuNjUsMjYuOTY0IGM1OC4xMzMsMy40NTEsOTguOTg4LTE1Ljg5OCw5OC45ODgtMTUuODk4bC0yNi4yOTUtMTUuOTYyYzAsMC0xNi42MzEsMy40OTQtNDIuMjM2LDYuOTQ2IGMtMjUuNjI2LDMuNDczLTc4LjE3My0yLjc4My04MC4yNDMtNy41OTNDMTc3LjU1MywzOTEuNjgyLDE4MS43MzgsMzg4Ljk0MywxODEuNzM4LDM4OC45NDN6Ij48L3BhdGg+IDxwYXRoIHN0eWxlPSJmaWxsOndoaXRlOyIgZD0iTTQwNy45OTQsNDQ1LjAwNWM4Ljk5NS05LjcwNy0yLjc4My0xNy4zMjEtMi43ODMtMTcuMzIxczQuMTQyLDQuODUzLTEuMzM3LDEwLjM3NiBjLTUuNTQ0LDUuNTIyLTU2LjA4NCwxOS4zNDktMTM3LjA2MSwyMy41MTJjLTgwLjk1NSw0LjE2My0xNjguODU2LTcuNjE1LTE3MS42MzktMTcuOTkgYy0yLjY5Ni0xMC4zNzYsNDUuMDE4LTE4LjY1OSw0NS4wMTgtMTguNjU5Yy01LjUyMiwwLjY5LTcxLjk2LDIuMDcxLTc0LjA3NCwyMC4wODJjLTIuMDcxLDE3Ljk2OCwyOS4wNTYsMzIuNTA3LDE1My42NywzMi41MDcgQzM0NC4zMzksNDc3LjQ5MSwzOTkuMDQyLDQ1NC42NDcsNDA3Ljk5NCw0NDUuMDA1eiI+PC9wYXRoPiA8cGF0aCBzdHlsZT0iZmlsbDp3aGl0ZTsiIGQ9Ik0zNTkuNTY4LDQ4NS44MTdjLTU0LjY4MiwxMS4wNDQtMjIwLjczNCw0LjA3Ny0yMjAuNzM0LDQuMDc3czEwNy45MTksMjUuNjI2LDIzMS4xMDksNC4xODUgYzU4Ljg4OC0xMC4yNjgsNjIuMzE4LTM4Ljc2Myw2Mi4zMTgtMzguNzYzUzQxNC4yNSw0NzQuNzA4LDM1OS41NjgsNDg1LjgxN3oiPjwvcGF0aD4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA<Zz4gPC9nPiA8Zz4gPC9nPiA8Zz4gPC9nPiA8L2c+IDwvZz48L3N2Zz4=)

A Maven plugin for automated semantic versioning with Markdown-based changelog management.

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Goals](#goals)
    - [create](#create)
    - [update](#update)
    - [verify](#verify)
- [Configuration Properties](#configuration-properties)
- [Examples](#examples)
- [License](#license)

## Overview

The Semantic Version Maven Plugin automates version management using semantic versioning principles (MAJOR.MINOR.PATCH).
It integrates changelog management through Markdown files, enabling you to:

- Create version bump specifications with Markdown changelog entries
- Automatically update POM file versions based on semantic versioning rules
- Maintain synchronized changelog files across single and multi-module Maven projects
- Support multiple versioning strategies for different project structures

## Requirements

- **Java**: 17 or higher
- **Maven**: 3.9.12 or higher

## Installation

Add the plugin to your `pom.xml`:

```xml

<build>
    <plugins>
        <plugin>
            <groupId>io.github.bsels</groupId>
            <artifactId>semantic-version-maven-plugin</artifactId>
            <version>1.2.0</version>
        </plugin>
    </plugins>
</build>
```

## Goals

### create

**Full name**: `io.github.bsels:semantic-version-maven-plugin:create`

**Description**: Creates a version Markdown file that specifies which projects should receive which type of semantic
version bump (PATCH, MINOR, or MAJOR). The goal provides an interactive interface to select projects and their version
bump types, and allows you to write changelog entries either inline or via an external editor.

**Phase**: Not bound to any lifecycle phase (standalone goal)

#### Configuration Properties

| Property                           | Type                 | Default                                                           | Description                                                                                                                                                                                                                         |
|------------------------------------|----------------------|-------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `versioning.modus`                 | `Modus`              | `PROJECT_VERSION`                                                 | Versioning strategy:<br/>• `PROJECT_VERSION`: All projects in multi-module builds<br/>• `REVISION_PROPERTY`: Only current project using the `revision` property<br/>• `PROJECT_VERSION_ONLY_LEAFS`: Only leaf projects (no modules) |
| `versioning.identifier`            | `ArtifactIdentifier` | `GROUP_ID_AND_ARTIFACT_ID`                                        | Artifact key format in version Markdown files:<br/>• `GROUP_ID_AND_ARTIFACT_ID`: use `groupId:artifactId` keys (default)<br/>• `ONLY_ARTIFACT_ID`: use artifactId only when all modules share the same groupId                      |
| `versioning.directory`             | `Path`               | `.versioning`                                                     | Directory for storing version Markdown files                                                                                                                                                                                        |
| `versioning.dryRun`                | `boolean`            | `false`                                                           | Preview changes without writing files                                                                                                                                                                                               |
| `versioning.backup`                | `boolean`            | `false`                                                           | Create backup of files before modification                                                                                                                                                                                          |
| `versioning.commit.message.create` | `String`             | `Created version Markdown file for {numberOfProjects} project(s)` | Commit message template for version Markdown file creation. Use `{numberOfProjects}` placeholder for project count                                                                                                                  |
| `versioning.git`                   | `Git`                | `NO_GIT`                                                          | Defines the git operation mode:<br/>• `NO_GIT`: no git operations will be performed<br/>• `STASH`: added changed files to the git stash<br/>• `COMMIT`: commit all changed files with the configured commit message                 |

#### Example Usage

**Basic usage** (interactive mode):

```bash
mvn io.github.bsels:semantic-version-maven-plugin:create
```

**With custom versioning directory**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:create \
  -Dversioning.directory=.versions
```

**Dry-run to preview**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:create \
  -Dversioning.dryRun=true
```

**Multi-module project (leaf projects only)**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:create \
  -Dversioning.modus=PROJECT_VERSION_ONLY_LEAFS
```

---

### update

**Full name**: `io.github.bsels:semantic-version-maven-plugin:update`

**Description**: Updates POM file versions and CHANGELOG.md files based on version Markdown files created by the
`create` goal. The goal reads version bump specifications from Markdown files, applies semantic versioning to project
versions, updates dependencies in multi-module projects, and merges changelog entries into CHANGELOG.md files.

**Phase**: Not bound to any lifecycle phase (standalone goal)

#### Configuration Properties

| Property                             | Type                 | Default                                                   | Description                                                                                                                                                                                                                                                                          |
|--------------------------------------|----------------------|-----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `versioning.bump`                    | `VersionBump`        | `FILE_BASED`                                              | Version bump strategy:<br/>• `FILE_BASED`: Use version Markdown files from `.versioning` directory<br/>• `MAJOR`: Apply MAJOR version bump to all projects<br/>• `MINOR`: Apply MINOR version bump to all projects<br/>• `PATCH`: Apply PATCH version bump to all projects           |
| `versioning.modus`                   | `Modus`              | `PROJECT_VERSION`                                         | Versioning strategy:<br/>• `PROJECT_VERSION`: All projects in multi-module builds<br/>• `REVISION_PROPERTY`: Only current project using the `revision` property<br/>• `PROJECT_VERSION_ONLY_LEAFS`: Only leaf projects (no modules)                                                  |
| `versioning.identifier`              | `ArtifactIdentifier` | `GROUP_ID_AND_ARTIFACT_ID`                                | Artifact key format in version Markdown files:<br/>• `GROUP_ID_AND_ARTIFACT_ID`: use `groupId:artifactId` keys (default)<br/>• `ONLY_ARTIFACT_ID`: use artifactId only when all modules share the same groupId                                                                       |
| `versioning.directory`               | `Path`               | `.versioning`                                             | Directory containing version Markdown files                                                                                                                                                                                                                                          |
| `versioning.dryRun`                  | `boolean`            | `false`                                                   | Preview changes without writing files                                                                                                                                                                                                                                                |
| `versioning.backup`                  | `boolean`            | `false`                                                   | Create backup of POM and CHANGELOG files before modification                                                                                                                                                                                                                         |
| `versioning.commit.message.update`   | `String`             | `Updated {numberOfProjects} project version(s) [skip ci]` | Commit message template for version updates. Use `{numberOfProjects}` placeholder for project count                                                                                                                                                                                  |
| `versioning.dependency.bump.message` | `String`             | `Project version bumped as result of dependency bumps`    | Changelog entry text used when a project version is bumped because of dependency updates                                                                                                                                                                                             |
| `versioning.update.scripts`          | `String`             | `-`                                                       | Script paths to execute per updated module, separated by the OS path separator. Each script runs in the module directory with `CURRENT_VERSION`, `NEW_VERSION`, `DRY_RUN` (`true` or `false`), `GIT_STASH` (`true` or `false`), `EXECUTION_DATE` (YYYY-MM-DD) environment variables. |
| `versioning.version.header`          | `String`             | `{version} - {date#YYYY-MM-DD}`                           | Header format for version Markdown files. Supports `{version}` and `{date#YYYY-MM-DD}` placeholders; date uses a `DateTimeFormatter` pattern.                                                                                                                                        |
| `versioning.changelog.header`        | `String`             | `Changelog`                                               | Header label for the changelog title (H1)                                                                                                                                                                                                                                            |
| `versioning.major.header`            | `String`             | `Major`                                                   | Header label for major changes in the changelog                                                                                                                                                                                                                                      |
| `versioning.minor.header`            | `String`             | `Minor`                                                   | Header label for minor changes in the changelog                                                                                                                                                                                                                                      |
| `versioning.patch.header`            | `String`             | `Patch`                                                   | Header label for patch changes in the changelog                                                                                                                                                                                                                                      |
| `versioning.other.header`            | `String`             | `Other`                                                   | Header label for non-semantic changes in the changelog                                                                                                                                                                                                                               |
| `versioning.git`                     | `Git`                | `NO_GIT`                                                  | Defines the git operation mode:<br/>• `NO_GIT`: no git operations will be performed<br/>• `STASH`: added changed files to the git stash<br/>• `COMMIT`: commit all changed files with the configured commit message                                                                  |

#### Example Usage

**Basic usage** (file-based versioning):

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update
```

**Force MAJOR version bump** (override version files):

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update \
  -Dversioning.bump=MAJOR
```

**Force MINOR version bump**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update \
  -Dversioning.bump=MINOR
```

**Force PATCH version bump**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update \
  -Dversioning.bump=PATCH
```

**Dry-run to preview changes**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update \
  -Dversioning.dryRun=true
```

**With backup files**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update \
  -Dversioning.backup=true
```

**Custom versioning directory**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update \
  -Dversioning.directory=.versions
```

**Custom version file header**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update \
  -Dversioning.version.header='Release {version} ({date#YYYY-MM-DD})'
```

**Multi-module project with revision property**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update \
  -Dversioning.modus=REVISION_PROPERTY
```

---

### verify

**Full name**: `io.github.bsels:semantic-version-maven-plugin:verify`

**Description**: Verifies that version Markdown files are consistent with the project scope. The goal reads version
Markdown files, validates that all referenced artifacts exist in scope, enforces the selected verification mode, and
optionally requires consistent version bumps across all projects. When `versioning.git` is not `NO_GIT`, it runs
`git status` before verification. This goal does not write files; `versioning.dryRun` and `versioning.backup` have no
effect.

**Phase**: Not bound to any lifecycle phase (standalone goal)

#### Configuration Properties

| Property                             | Type                 | Default                    | Description                                                                                                                                                                                                                                                                                     |
|--------------------------------------|----------------------|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `versioning.verification.mode`       | `VerificationMode`   | `ALL_PROJECTS`             | Verification scope:<br/>• `NONE`: skip verification<br/>• `AT_LEAST_ONE_PROJECT`: require at least one version-marked project<br/>• `DEPENDENT_PROJECTS`: require all dependent projects to be version-marked<br/>• `ALL_PROJECTS`: all projects in scope must be version-marked                |
| `versioning.verification.consistent` | `boolean`            | `false`                    | When `true`, all version-marked projects must share the same version bump type                                                                                                                                                                                                                  |
| `versioning.modus`                   | `Modus`              | `PROJECT_VERSION`          | Project scope for verification:<br/>• `PROJECT_VERSION`: all projects in multi-module builds<br/>• `REVISION_PROPERTY`: only current project using the `revision` property<br/>• `PROJECT_VERSION_ONLY_LEAFS`: only leaf projects (no modules)                                                  |
| `versioning.identifier`              | `ArtifactIdentifier` | `GROUP_ID_AND_ARTIFACT_ID` | Artifact key format in version Markdown files:<br/>• `GROUP_ID_AND_ARTIFACT_ID`: use `groupId:artifactId` keys (default)<br/>• `ONLY_ARTIFACT_ID`: use artifactId only when all modules share the same groupId                                                                                  |
| `versioning.directory`               | `Path`               | `.versioning`              | Directory containing version Markdown files                                                                                                                                                                                                                                                     |
| `versioning.git`                     | `Git`                | `NO_GIT`                   | Git mode. Any value other than `NO_GIT` triggers a `git status` check before verification. Supported modes:<br/>• `NO_GIT`: no git operations will be performed<br/>• `STASH`: added changed files to the git stash<br/>• `COMMIT`: commit all changed files with the configured commit message |

#### Example Usage

**Basic verification**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:verify
```

**Verify dependent projects and require consistent bumps**:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:verify \
  -Dversioning.verification.mode=DEPENDENT_PROJECTS \
  -Dversioning.verification.consistent=true
```

## Configuration Properties

### Common Properties

These properties apply to `create`, `update`, and `verify` goals. The `verify` goal does not use
`versioning.dryRun` or `versioning.backup`:

| Property                | Type                 | Default                    | Description                                                                                                                                                                                                                                                                                       |
|-------------------------|----------------------|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `versioning.modus`      | `Modus`              | `PROJECT_VERSION`          | Defines versioning strategy for project structure:<br/>• `PROJECT_VERSION`: Process all projects in topological order<br/>• `REVISION_PROPERTY`: Process only the current project using the `revision` property<br/>• `PROJECT_VERSION_ONLY_LEAFS`: Process only leaf projects (no child modules) |
| `versioning.identifier` | `ArtifactIdentifier` | `GROUP_ID_AND_ARTIFACT_ID` | Artifact key format in version Markdown files:<br/>• `GROUP_ID_AND_ARTIFACT_ID`: use `groupId:artifactId` keys (default)<br/>• `ONLY_ARTIFACT_ID`: use artifactId only when all modules share the same groupId                                                                                    |
| `versioning.directory`  | `Path`               | `.versioning`              | Directory path for version Markdown files (absolute or relative to project root)                                                                                                                                                                                                                  |
| `versioning.dryRun`     | `boolean`            | `false`                    | When `true`, performs all operations without writing files (logs output instead)                                                                                                                                                                                                                  |
| `versioning.backup`     | `boolean`            | `false`                    | When `true`, creates `.bak` backup files before modifying POM and CHANGELOG files                                                                                                                                                                                                                 |
| `versioning.git`        | `Git`                | `NO_GIT`                   | Defines the git operation mode:<br/>• `NO_GIT`: no git operations will be performed<br/>• `STASH`: added changed files to the git stash<br/>• `COMMIT`: commit all changed files with the configured commit message                                                                               |

### create-Specific Properties

| Property                           | Type     | Default                                                           | Description                                                                                                                               |
|------------------------------------|----------|-------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `versioning.commit.message.create` | `String` | `Created version Markdown file for {numberOfProjects} project(s)` | Commit message template used when creating version Markdown files. The `{numberOfProjects}` placeholder is replaced with the actual count |

### update-Specific Properties

| Property                             | Type          | Default                                                   | Description                                                                                                                                                                                                                                                                                                                                    |
|--------------------------------------|---------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `versioning.bump`                    | `VersionBump` | `FILE_BASED`                                              | Determines version increment strategy:<br/>• `FILE_BASED`: Read version bumps from Markdown files in `.versioning` directory<br/>• `MAJOR`: Force MAJOR version increment (X.0.0) for all projects<br/>• `MINOR`: Force MINOR version increment (0.X.0) for all projects<br/>• `PATCH`: Force PATCH version increment (0.0.X) for all projects |
| `versioning.commit.message.update`   | `String`      | `Updated {numberOfProjects} project version(s) [skip ci]` | Commit message template used when updating project versions. The `{numberOfProjects}` placeholder is replaced with the actual count                                                                                                                                                                                                            |
| `versioning.dependency.bump.message` | `String`      | `Project version bumped as result of dependency bumps`    | Changelog entry text used when a project version is bumped because of dependency updates                                                                                                                                                                                                                                                       |
| `versioning.update.scripts`          | `String`      | `-`                                                       | Script paths to execute per updated module, separated by the OS path separator. Each script runs in the module directory with `CURRENT_VERSION`, `NEW_VERSION`, `DRY_RUN` (`true` or `false`), `GIT_STASH` (`true` or `false`), `EXECUTION_DATE` (YYYY-MM-DD) environment variables.                                                           |
| `versioning.version.header`          | `String`      | `{version} - {date#YYYY-MM-DD}`                           | Header format for version Markdown files. Supports `{version}` and `{date#YYYY-MM-DD}` placeholders; date uses a `DateTimeFormatter` pattern.                                                                                                                                                                                                  |
| `versioning.changelog.header`        | `String`      | `Changelog`                                               | Header label for the changelog title (H1)                                                                                                                                                                                                                                                                                                      |
| `versioning.major.header`            | `String`      | `Major`                                                   | Header label for major changes in the changelog                                                                                                                                                                                                                                                                                                |
| `versioning.minor.header`            | `String`      | `Minor`                                                   | Header label for minor changes in the changelog                                                                                                                                                                                                                                                                                                |
| `versioning.patch.header`            | `String`      | `Patch`                                                   | Header label for patch changes in the changelog                                                                                                                                                                                                                                                                                                |
| `versioning.other.header`            | `String`      | `Other`                                                   | Header label for non-semantic changes in the changelog                                                                                                                                                                                                                                                                                         |

### verify-Specific Properties

| Property                             | Type               | Default        | Description                                                                                                                                                                                                                                                                      |
|--------------------------------------|--------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `versioning.verification.mode`       | `VerificationMode` | `ALL_PROJECTS` | Verification scope:<br/>• `NONE`: skip verification<br/>• `AT_LEAST_ONE_PROJECT`: require at least one version-marked project<br/>• `DEPENDENT_PROJECTS`: require all dependent projects to be version-marked<br/>• `ALL_PROJECTS`: all projects in scope must be version-marked |
| `versioning.verification.consistent` | `boolean`          | `false`        | When `true`, all version-marked projects must share the same version bump type                                                                                                                                                                                                   |

## Examples

### Example 1: Single Project Workflow

1. **Create version specification**:
   ```bash
   mvn io.github.bsels:semantic-version-maven-plugin:create
   ```
    - Select MINOR version bump
    - Enter changelog: "Added new user authentication feature"

2. **Preview changes**:
   ```bash
   mvn io.github.bsels:semantic-version-maven-plugin:update -Dversioning.dryRun=true
   ```

3. **Apply version update**:
   ```bash
   mvn io.github.bsels:semantic-version-maven-plugin:update
   ```

### Example 2: Multi-Module Project Workflow

1. **Create version specifications for multiple modules**:
   ```bash
   mvn io.github.bsels:semantic-version-maven-plugin:create
   ```
    - Select `module-api` → MAJOR (breaking changes)
    - Select `module-core` → MINOR (new features)
    - Enter changelog for each module

2. **Update with backups**:
   ```bash
   mvn io.github.bsels:semantic-version-maven-plugin:update -Dversioning.backup=true
   ```

### Example 3: Emergency Patch Release

Skip version file creation and force PATCH bump:

```bash
mvn io.github.bsels:semantic-version-maven-plugin:update -Dversioning.bump=PATCH
```

### Example 4: POM Configuration

Configure the plugin directly in `pom.xml`:

```xml

<build>
    <plugins>
        <plugin>
            <groupId>io.github.bsels</groupId>
            <artifactId>semantic-version-maven-plugin</artifactId>
            <version>1.2.0</version>
            <configuration>
                <modus>PROJECT_VERSION</modus>
                <versionDirectory>.versioning</versionDirectory>
                <dryRun>false</dryRun>
                <backupFiles>true</backupFiles>
                <versionBump>FILE_BASED</versionBump>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
