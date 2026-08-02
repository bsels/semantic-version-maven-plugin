---
variables:
  issueKey:
    prompt: "Jira issue key"
    pattern: "[A-Z]+-\\d+"
  description:
    prompt: "What changed?"
sections:
  issues:
    addPrompt: "Add another issue?"
  descriptions:
    addPrompt: "More descriptions?"
---
{{#issues}}
- [{{issueKey}}](https://company.atlassian.net/browse/{{issueKey}})
{{#descriptions}}
    - {{description}}
{{/descriptions}}
{{/issues}}
