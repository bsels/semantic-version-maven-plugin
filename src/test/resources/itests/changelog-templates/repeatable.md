---
repeatable: true
variables:
  issueKey:
    prompt: "Jira issue key"
    pattern: "[A-Z]+-\\d+"
  description:
    prompt: "What changed?"
---
- [{issueKey}](https://company.atlassian.net/browse/{issueKey})
    - {description}
