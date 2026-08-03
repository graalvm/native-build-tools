# FS-rhei-github-issue-fix: Rhei GitHub issue-fix workflow

The repository-local Rhei workflow turns an approved GitHub issue proposal into a validated pull request while preserving fast, bounded feedback. [§GOAL-fast-feedback](../goals.md#goal-fast-feedback-native-build-workflows-provide-feedback-as-fast-as-practical)

## 1. Focused review and repair budget

The workflow has one configured focused-review cycle limit shared by validation, specialist reviews, aggregate review, and deterministic review dispatch. Every cycle before the final cycle may route fixable blockers through one repair attempt and another validation/review cycle. The final cycle publishes ready work, hands off external blockers, or records remaining blockers locally.

The default permits four focused review cycles and therefore up to three review-repair attempts. This gives a third-cycle finding one bounded repair and re-review opportunity instead of terminating immediately, while retaining a finite publication gate.
