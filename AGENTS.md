# Native Build Tools — agent instructions

## Grounding with grund (v2)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, non-goal, requirement, decision, roadmap item, component spec, workflow spec, and glossary term has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, NGOAL, REQ, FS, AR, CI, E2E, DEC, RM, GLOSS}`), cited with the marker `§` — e.g. `§gradle/FS-gradle-plugin.3.1` (the `FS-gradle-plugin` here is a shape illustration, not necessarily the point you need). Type `$$` in a grund-aware editor and it becomes `§`. Java Checkstyle allows the `§` marker as the only non-ASCII citation exception. Bare ID-shaped tokens are ignored because `[reference] strict = true`.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
- `grund list` / `grund list --kind FS,AR` — discover IDs if you get lost
- Cross-namespace citations use `§alias/<ID>`, such as `§gradle/FS-gradle-plugin` from root docs and `§root/FS-plugin-common-behavior` from plugin docs.

### Project map

- [GRUND](docs/spec/grund.md): Project motivation
- [GOAL](docs/spec/goals.md): Project direction and outcomes
- [NGOAL](docs/spec/non-goals.md): Project non-goals and out-of-scope proposals
- [REQ](docs/spec/requirements.md): Cross-cutting requirements and constraints
- [FS](docs/spec): Functional specifications in root specs plus plugin-local `docs/functional-spec.md` files
- [AR](docs/spec): Architecture specifications in root specs plus plugin-local `docs/architecture.md` files
- [CI](docs/spec/ci.md): Pull request and repository CI workflows
- [E2E](native-gradle-plugin/docs/e2e.md, native-maven-plugin/docs/e2e.md): Plugin end-to-end and functional test execution
- [DEC](docs/spec/decisions.md): Project decisions and tradeoffs
- [RM](docs/spec/roadmap.md): Planned milestones and sequencing
- [GLOSS](docs/spec/glossary.md): Glossary of domain terms

Workspace members:

- `root` → [docs/spec/README.md](docs/spec/README.md): repository-wide contracts, common libraries, CI, build infrastructure, native tests, decisions, roadmap, and glossary.
- `gradle` → [native-gradle-plugin/AGENTS.md](native-gradle-plugin/AGENTS.md): Gradle plugin namespace. Local Gradle citations use `§<ID>` inside that member; root docs cite Gradle facts with `§gradle/<ID>`.
- `maven` → [native-maven-plugin/AGENTS.md](native-maven-plugin/AGENTS.md): Maven plugin namespace. Local Maven citations use `§<ID>` inside that member; root docs cite Maven facts with `§maven/<ID>`.

### Declarations and citations

Declarations are heading lines `# FS-gradle-plugin: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// FS-gradle-plugin: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. Plain headings or bold labels are fine for non-citable local structure. One doc-comment may declare multiple IDs, such as a `CI-` and an `E2E-` declaration in the same file; each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 12 lines; ≤ 160 columns.
- **Always cite the most-specific point.**
- **Citations climb to reasons.** Goals cite project grounding; repo-specific specs cite goals or narrower specs; code and executable tests cite the most specific repo-kind spec. Run `grund check` from the repository root so workspace aliases resolve.
