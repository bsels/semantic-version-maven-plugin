---
'io.github.bsels:semantic-version-maven-plugin': patch
---

Bugfix:

- Optimized artifact grouping in `UpdatePomMojo` by replacing a redundant `reducing` collector with `flatMapping`.
- Added multi-project dependency tests for `UpdatePomMojo`.
