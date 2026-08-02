---
variables:
  changeType:
    prompt: "Change category"
    pattern: "Added|Changed|Deprecated|Removed|Fixed|Security"
  component:
    prompt: "Affected component"
    pattern: "[A-Za-z][A-Za-z0-9 ._-]*"
  summary:
    prompt: "User-facing summary"
  repository:
    prompt: "GitHub repository (owner/name)"
    pattern: "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"
  issue:
    prompt: "Issue number"
    pattern: "\\d+"
  pullRequest:
    prompt: "Pull request number"
    pattern: "\\d+"
---
### {{changeType}}

- **{{component}}:** {{summary}}
    - Issue: [#{{issue}}](https://github.com/{{repository}}/issues/{{issue}})
    - Pull request: [#{{pullRequest}}](https://github.com/{{repository}}/pull/{{pullRequest}})
