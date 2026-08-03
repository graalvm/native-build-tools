# Rhei: Native Build Tools GitHub Issue Fix
**States:** github-issue-fix

## Overview

This workspace fixes one GitHub issue from `{{repo}}`: `{{issue}}`.

The first task creates or reuses an isolated worktree from `{{repo_checkout}}`,
fetches the issue, discovers repository instructions and grounding configuration,
records a spec-fit artifact, and writes exactly one follow-up task. The follow-up
task starts in proposal approval inspection, local proposal generation, or
GitHub handoff according to the recorded verdict and publication mode.
Compatible externally published issues recover or publish a content-addressed
proposal and require an authorized exact GitHub approval before implementation.
`no-pr` uses a local proposal and human gate with zero GitHub writes. Approved
work proceeds through validation, focused review/fix cycles, and optional PR
publication; blocked, incompatible, unclear, or attempt-exhausted work produces
a local handoff.

## Source

| Field | Value |
|---|---|
| Repository | `{{repo}}` |
| Issue | `{{issue}}` |
| Source checkout | `{{repo_checkout}}` |
| Work subdirectory | `{{work_subdir}}` |
| Worktree root | `{{worktree_root}}` |
| Base branch | `{{base_branch}}` |
| Branch prefix | `{{branch_prefix}}` |
| Publication mode | `{{publication_mode}}` |
| Rhei GitHub actor | `{% if rhei_actor == "auto" %}<active gh account>{% else %}{{rhei_actor}}{% endif %}` |
| Proposal attempt limit | `{{proposal_attempts}}` |
| Focused review cycle limit | `{{review_cycles}}` |
| PR push remote | `{{pr_push_remote}}` |
| PR head owner | `{{pr_head_owner}}` |
| PR labels | `{{pr_labels}}` |

## Validation Commands

- Use validation commands discovered from the target repository's `AGENTS.md`.
