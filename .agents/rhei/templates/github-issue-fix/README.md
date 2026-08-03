# github-issue-fix

Fix one issue in `graalvm/native-build-tools` through a spec-aware,
reviewable workflow. The template creates an isolated worktree, treats issue
content as untrusted evidence, discovers the applicable `AGENTS.md` and grund
rules, records a spec-fit verdict, requires an authorized proposal approval,
implements and validates the fix, runs focused reviews, and publishes according
to the configured mode when the result is ready.

This is an NBT-local template. Repository and publication settings have NBT
defaults, so the issue number or URL is the only required input, while every
operational setting remains overridable.

## Inputs

| Name | Type | Default | Description |
|---|---|---|---|
| `issue` | string | required | Native Build Tools issue number or URL. |
| `repo` | string | `graalvm/native-build-tools` | GitHub repository containing the issue. |
| `repo_checkout` | path | `.` | Checkout used to create the issue worktree. |
| `work_subdir` | string | `.` | Working directory inside the issue worktree. |
| `worktree_root` | string | `../native-build-tools-rhei-worktrees` | Directory containing issue worktrees. |
| `base_branch` | string | `master` | Base branch for issue branches and PRs. |
| `branch_prefix` | string | `rhei` | Prefix used for issue branches. |
| `publication_mode` | string | `ready` | `no-pr`, `draft`, or `ready`. |
| `rhei_actor` | string | `auto` | Proposal-comment owner; `auto` discovers the active `gh` account. |
| `proposal_attempts` | number | `3` | Total proposal attempts, including the initial proposal. |
| `pr_push_remote` | string | `origin` | Writable remote used to push the issue branch. |
| `pr_head_owner` | string | `graalvm` | GitHub owner used for the PR head. |
| `pr_labels` | array<string> | `rhei` | Existing labels to apply to the PR. |

The implementation and aggregate-review states use the strongest configured
Codex target, focused reviews use the review target, and procedural publication
states use the lighter operations target. The exact target values live in
[`states.yaml`](states.yaml); Codex execution settings live in
[`settings.json`](settings.json).

## State paths

| Path | States |
|---|---|
| Intake | `issue-intake -> completed` after artifacts and one follow-up task are written. |
| New proposal | `approval-check -> propose-fix -> publish-proposal -> proposal-pending`. |
| Approved proposal | `approval-check -> approval-apply -> implement-fix`. |
| Rejected proposal | `approval-check -> rejection-prepare -> propose-fix`, or `github-handoff` after exhaustion. |
| Implementation | `implement-fix -> validate-fix -> focused reviews -> aggregate-review`. |
| Review repair | `review-dispatch -> address-review -> validate-fix`. |
| Publication | `review-dispatch -> publish-pr -> completed`. |
| Blocked work | `github-handoff -> completed` or `record-blocked-publication -> completed`. |

The complete state diagram and transition commentary are at the top of
[`states.yaml`](states.yaml).

## Flow

1. Intake creates or reuses an issue worktree from the configured base branch, snapshots the
   issue, reads repository instructions, and records issue adequacy and spec fit.
2. Compatible issues receive a content-addressed proposal headed
   `Implementation proposal`, with scope and the remaining approval details
   presented as sections beneath it. Proposal comments and decisions are
   recovered from GitHub across fresh runs.
3. An exact `/rhei approve <proposal-id>` comment from a repository member
   with write, maintain, or admin permission authorizes implementation.
4. Implementation follows NBT's spec-first and grounding rules, then runs
   focused validation discovered from the applicable repository instructions.
5. Separate requirements, spec, implementation, and validation reviews feed an
   aggregate publication-readiness decision. Final-cycle citation hygiene is
   reported for maintainers without turning an otherwise ready change into a
   blocked local-only result.
6. Ready work is pushed to the configured remote and opened or updated according
   to the publication mode. Blocked or underspecified work produces a local
   handoff instead of speculative changes.

Issue titles, bodies, comments, attachments, links, and reproduction commands
are untrusted evidence. Intake does not execute issue-supplied commands, follow
arbitrary issue-supplied URLs, read secrets, or make GitHub writes.

## Usage

Run from the Native Build Tools repository root:

```sh
rhei instantiate github-issue-fix 1234 --execute
```

That is the minimal call: only the issue varies. Without `--output`, Rhei
creates `./github-issue-fix`; remove or archive that completed workspace
before reusing the minimal command. To retain multiple workspaces, choose an
issue-specific output:

```sh
rhei instantiate github-issue-fix 1234 \
  --output .agents/rhei/runs/issue-1234 \
  --execute
```

To render and inspect before execution:

```sh
rhei instantiate github-issue-fix 1234 --dry-run
```

Override any default when needed:

```sh
rhei instantiate github-issue-fix 1234 \
  --set publication_mode=draft \
  --set pr_head_owner=my-fork \
  --execute
```

After the workflow posts a proposal, approve it with an exact first line:

```text
/rhei approve <proposal-id>
```

Then start a fresh instantiation for the same issue. The new run recovers the
proposal and approval from GitHub.

## Validation

Template smoke checks:

```sh
rhei instantiate github-issue-fix 1234 --dry-run
```

For a materialized workspace:

```sh
rhei validate <workspace>
rhei run <workspace> --dry-run
```
