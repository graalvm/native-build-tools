# github-issue-fix

Fix one issue in `graalvm/native-build-tools` through a spec-aware,
reviewable workflow. The template creates an isolated worktree, treats issue
content as untrusted evidence, discovers the applicable `AGENTS.md` and grund
rules, records a spec-fit verdict, requires an authorized proposal approval,
implements and validates the fix, runs focused reviews, and publishes a draft
pull request when the result is ready.

This is an NBT-local template. Repository and publication settings are fixed so
the only template input is the issue number or URL.

## Input

| Name | Type | Default | Description |
|---|---|---|---|
| `issue` | string | required | Native Build Tools issue number or URL. |

## NBT defaults

| Setting | Value |
|---|---|
| Repository | `graalvm/native-build-tools` |
| Checkout | Current Native Build Tools git root (`.`) |
| Base branch | `master` |
| Issue branch | `rhei/issue-<issue>` |
| Worktrees | `../native-build-tools-rhei-worktrees` |
| Publication | Draft pull request |
| Proposal actor | `jormundur00` |
| Push remote | `origin` |
| PR head owner | `graalvm` |
| PR labels | Existing `rhei` label, when available |
| Proposal attempts | 3 |
| Review passes | 1 |
| Review repair attempts | 2 |

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

1. Intake creates or reuses an NBT worktree from `master`, snapshots the
   issue, reads repository instructions, and records issue adequacy and spec fit.
2. Compatible issues receive a content-addressed proposal. Proposal comments
   and decisions are recovered from GitHub across fresh runs.
3. An exact `/rhei approve <proposal-id>` comment from a repository member
   with write, maintain, or admin permission authorizes implementation.
4. Implementation follows NBT's spec-first and grounding rules, then runs
   focused validation discovered from the applicable repository instructions.
5. Separate requirements, spec, implementation, and validation reviews feed an
   aggregate publication-readiness decision.
6. Ready work is pushed to `origin` and opened or updated as a draft PR from
   `graalvm:rhei/issue-<issue>`. Blocked or underspecified work produces a
   local handoff instead of speculative changes.

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
