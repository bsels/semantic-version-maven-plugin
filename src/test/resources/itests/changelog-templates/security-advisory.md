---
variables:
  repository:
    prompt: "GitHub repository (owner/name)"
    pattern: "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"
  advisory:
    prompt: "GitHub security advisory ID"
    pattern: "GHSA-[23456789cfghjmpqrvwx]{4}-[23456789cfghjmpqrvwx]{4}-[23456789cfghjmpqrvwx]{4}"
  cve:
    prompt: "CVE identifier"
    pattern: "CVE-\\d{4}-\\d{4,}"
  severity:
    prompt: "Severity"
    pattern: "(?i:low|moderate|high|critical)"
  affectedVersions:
    prompt: "Affected version range"
  patchedVersion:
    prompt: "First patched version"
    pattern: "\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?"
  summary:
    prompt: "Vulnerability summary"
  mitigation:
    prompt: "Mitigation for users unable to upgrade"
---
### Security

> [!IMPORTANT]
> **{{severity}} severity:** {{summary}}

| Detail | Value |
|---|---|
| Advisory | [{{advisory}}](https://github.com/{{repository}}/security/advisories/{{advisory}}) |
| CVE | [{{cve}}](https://www.cve.org/CVERecord?id={{cve}}) |
| Affected versions | `{{affectedVersions}}` |
| Patched version | `{{patchedVersion}}` |

**Mitigation:** {{mitigation}}
