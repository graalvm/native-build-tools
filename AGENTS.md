# Native Build Tools — agent instructions

## Grounding with grund (v2)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, NGOAL, REQ, AR, FS, E2E, DEC, GLOSS}`), cited with the marker `§` — e.g. `FS-user-login.3.1` is the shape of a section ID, and a real citation prefixes that shape with `§`. Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — `[reference] strict = true` is set in `.agents/grund.toml`, so only `§`-prefixed citations are checked.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
- `grund list` / `grund list --kind FS,AR` — discover IDs if you get lost

### Project map

- [GRUND](docs/spec/grund.md): Why: project motivation
- [GOAL](docs/spec/goals.md): Where: project direction and outcomes
- [NGOAL](docs/spec/non-goals.md): Project non-goals and out-of-scope proposals
- [REQ](docs/spec/requirements.md): Cross-cutting requirements and constraints
- [AR](docs/spec/architecture): How: high-level implementation, structure, and design
- [FS](docs/spec): What: behavior, requirements, and constraints
- [E2E](docs/spec): Executable user scenarios
- [DEC](docs/spec/decisions): Project decisions and tradeoffs
- [GLOSS](docs/spec/glossary.md): Glossary of domain terms

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `.agents/grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>.

- `common` → [common/AGENTS.md](common/AGENTS.md)
- `gradle` → [native-gradle-plugin/AGENTS.md](native-gradle-plugin/AGENTS.md)
- `maven` → [native-maven-plugin/AGENTS.md](native-maven-plugin/AGENTS.md)
- `root` → [AGENTS.md](AGENTS.md)

### Declarations and citations

Declarations are heading lines `# FS-user-login: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// FS-user-login: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. Plain headings or bold labels are fine for non-citable local structure. One doc-comment may declare multiple IDs (e.g. an `AR-` and an `FS-` on the same class) — each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 6 lines; ≤ 160 columns.
- **Always cite the most-specific point.**
- **Citations climb to reasons (grund.md).** Goals cite reasons, specs cite goals; architecture cites specs; code and executable tests cite specs.
