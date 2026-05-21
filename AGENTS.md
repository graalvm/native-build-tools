# Native Build Tools — agent instructions

## Grounding with grund (v2)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, decision, and roadmap item has a stable ID `<KIND>-<NNN>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, REPO, FS, AR, DEC, RM}`), cited with the marker `§` — e.g. `§FS-042-user-login.3.1` (the `FS-042-user-login` here is a shape illustration, not a real ID in this repo). Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are also recognized as citations for backward compatibility; set `[reference] strict = true` in `.agents/grund.toml` to require the `§` marker (run `grund fmt --marker` first to upgrade existing bare citations).

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
- [REPO](docs/spec/repository): Repository structure and ownership boundaries
- [FS](docs/spec): Functional specifications in component `functional-spec.md` files
- [AR](docs/spec): Architecture specifications in component `architecture.md` files
- [DEC](docs/spec/decisions.md): Project decisions and tradeoffs
- [RM](docs/spec/roadmap.md): Planned milestones and sequencing

### Declarations and citations

Declarations are heading lines `# FS-042-user-login: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// FS-042-user-login: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. Plain headings or bold labels are fine for non-citable local structure. One doc-comment may declare multiple IDs, such as an `AR-` and an `FS-` declaration on the same class; each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 3 lines; ≤ 100 columns.
- **Always cite the most-specific point.**
- **Citations climb to reasons.** Goals cite project grounding; repo-specific specs cite goals or narrower specs; code and executable tests cite the most specific repo-kind spec.
