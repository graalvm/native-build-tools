# Rhei: Native Build Tools GitHub Issue Fix
**States:** github-issue-fix

## Overview

This workspace fixes one GitHub issue from `graalvm/native-build-tools`: `{{issue}}`.

The first task creates or reuses an isolated worktree from `.`,
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
| Repository | `graalvm/native-build-tools` |
| Issue | `{{issue}}` |
| Source checkout | `.` |
| Work subdirectory | `.` |
| Worktree root | `../native-build-tools-rhei-worktrees` |
| Base branch | `master` |
| Branch prefix | `rhei` |
| Publication mode | `draft` |
| Rhei GitHub actor | `jormundur00` |
| Proposal attempt limit | `3` |
| PR push remote | `origin` |
| PR head owner | `graalvm` |
| PR labels | `["rhei"]` |

## Validation Commands

- Use validation commands discovered from the target repository's `AGENTS.md`.
