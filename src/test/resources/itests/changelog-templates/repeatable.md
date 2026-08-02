---
variables:
  issueKey:
    prompt: "Jira issue key"
    pattern: "[A-Z]+-\\d+"
  description:
    prompt: "What changed?"
sections:
  entries:
    addPrompt: "Add another entry?"
---
{{#entries}}
- [{{issueKey}}](https://company.atlassian.net/browse/{{issueKey}})
    - {{description}}
{{/entries}}
