# Native Build Tools — agent instructions

## Grounding with grund (v2)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, non-goal, requirement, decision, roadmap item, component spec, workflow spec, and glossary term has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, NGOAL, REQ, FS, AR, GRADLE, MAVEN, COMMON, TESTING, BUILD, CI, E2E, DEC, RM, GLOSS}`), cited with the marker `§` — e.g. `§GRADLE-plugin.3.1` (the `GRADLE-plugin` here is a shape illustration, not necessarily the point you need). Type `$$` in a grund-aware editor and it becomes `§`. Java source comments use bare ID citations because Java checkstyle is ASCII-only; `grund` recognizes those while `[reference] strict = false`. Bare ID-shaped tokens are also recognized as citations for backward compatibility; set `[reference] strict = true` in `.agents/grund.toml` to require the `§` marker (run `grund fmt --marker` first to upgrade existing bare citations).

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
- `grund list` / `grund list --kind FS,AR` — discover IDs if you get lost

### Project map

- [GRUND](docs/spec/grund.md): Project motivation
- [GOAL](docs/spec/goals.md): Project direction and outcomes
- [NGOAL](docs/spec/non-goals.md): Project non-goals and out-of-scope proposals
- [REQ](docs/spec/requirements.md): Cross-cutting requirements and constraints
- [FS](docs/spec/functional-spec.md): Repository functional specification
- [AR](docs/spec/architecture.md): Repository architecture
- [GRADLE](docs/spec/gradle-plugin.md): Gradle plugin behavior and architecture
- [MAVEN](docs/spec/maven-plugin.md): Maven plugin behavior and architecture
- [COMMON](docs/spec/common.md): Shared common library behavior and architecture
- [TESTING](docs/spec/testing.md): Native test, sample, and fixture behavior
- [BUILD](docs/spec/build-infra.md): Build, documentation, and release infrastructure
- [CI](docs/spec/ci.md): Pull request and repository CI workflows
- [E2E](docs/spec/e2e.md): End-to-end and functional test execution
- [DEC](docs/spec/decisions.md): Project decisions and tradeoffs
- [RM](docs/spec/roadmap.md): Planned milestones and sequencing
- [GLOSS](docs/spec/glossary.md): Glossary of domain terms

### Declarations and citations

Declarations are heading lines `# GRADLE-plugin: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// GRADLE-plugin: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. Plain headings or bold labels are fine for non-citable local structure. One doc-comment may declare multiple IDs, such as a `CI-` and an `E2E-` declaration in the same file; each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 3 lines; ≤ 100 columns.
- **Always cite the most-specific point.**
- **Citations climb to reasons.** Goals cite project grounding; repo-specific specs cite goals or narrower specs; code and executable tests cite the most specific repo-kind spec.
