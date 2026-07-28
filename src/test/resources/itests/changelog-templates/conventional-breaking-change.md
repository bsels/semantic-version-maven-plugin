---
variables:
  type:
    prompt: "Conventional Commit type"
    pattern: "feat|fix|perf|refactor|build|ci|docs|chore"
  scope:
    prompt: "Affected scope"
    pattern: "[a-z][a-z0-9-]*"
  summary:
    prompt: "Change summary"
  migration:
    prompt: "Required migration"
  repository:
    prompt: "GitHub repository (owner/name)"
    pattern: "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"
  pullRequest:
    prompt: "Pull request number"
    pattern: "\\d+"
  author:
    prompt: "GitHub author"
    pattern: "[A-Za-z0-9-]+"
  issueKey:
    prompt: "Tracking issue"
    pattern: "[A-Z]+-\\d+"
---
### Breaking Changes 🛠

- **{type}({scope})!** {summary}
    - **BREAKING CHANGE:** {migration}
    - Pull request: [#{pullRequest}](https://github.com/{repository}/pull/{pullRequest}) by [@{author}](https://github.com/{author})
    - Tracking issue: [{issueKey}](https://company.atlassian.net/browse/{issueKey})
