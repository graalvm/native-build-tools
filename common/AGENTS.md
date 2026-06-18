# Common Libraries — agent instructions

## Grounding with grund (v3)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, REQ, FS, AR, E2E}`), cited with the marker `§` — e.g. `FS-user-login.3.1` is the shape of a section ID, and a real citation prefixes that shape with `§`. Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — `[reference] strict = true` is set in `.agents/grund.toml`, so only `§`-prefixed citations are checked.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
- `grund list` / `grund list --kind FS,AR` — discover IDs if you get lost

### Project map

- [GRUND](docs/grund.md): Common library purpose
- [GOAL](docs/goals.md): Common library goals
- [REQ](docs/requirements.md): Common library requirements
- [FS](docs/functional-spec.md): Common library functional specification
- [AR](docs/architecture.md): Common library architecture
- [E2E](docs/e2e.md): Common library executable tests

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `.agents/grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>.

- [`common`](AGENTS.md): Build-tool-neutral shared libraries for Native Image utilities and metadata workflows
- [`gradle`](../native-gradle-plugin/AGENTS.md): Gradle plugin specs and implementation for Native Image workflows
- [`maven`](../native-maven-plugin/AGENTS.md): Maven plugin specs and implementation for Native Image workflows
- [`root`](../AGENTS.md): Workspace-level specs, release, CI, and shared Native Build Tools behavior

### Declarations and citations

Declarations are heading lines `# FS-user-login: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// FS-user-login: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. Plain headings or bold labels are fine for non-citable local structure. One doc-comment may declare multiple IDs (e.g. an `AR-` and an `FS-` on the same class) — each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 12 lines; ≤ 160 columns.
- **Always cite the most-specific point.**

### Citation directions

- **GOAL** should cite */GRUND or */GOAL.
- **REQ** should cite */GRUND or */GOAL or */REQ.
- **FS** should cite */REQ or */GOAL or */FS; avoid citing */AR.
- **AR** should cite */FS or */REQ or */GOAL or */AR.
- **E2E** should cite */FS.
- **code** (any file outside a kind home) should cite */FS or */AR or */E2E.
Unlisted kinds and pairs are fine.

## Local Grounding Notes

Repository-wide grounding, non-goals, shared plugin behavior, and CI remain in the `root`
namespace under `../docs/spec`. Gradle and Maven adapter behavior lives in the `gradle` and
`maven` namespaces. Prefer local IDs such as `§FS-common-libraries.5.1` for shared library
behavior, and cite root IDs such as `§root/FS-plugin-common` only for cross-project
contracts.

Use `grund common/<ID>`, `grund common/<ID> --toc`, and `grund common/<ID> --full` from the
repository root to resolve common citations. Run `grund check` from the repository root before
changing common specs or source citations.

## Module Map

Use this map before editing: pick the owning path, then resolve the cited spec before changing
behavior.

- `utils/src/main/java/org/graalvm/buildtools/utils/`: shared Native Image argument handling,
  resource analysis, agent configuration models, schema validation, JUnit dependency helpers, and
  constants. [§FS-common-libraries.1](docs/functional-spec.md#1-shared-native-image-utilities), [§FS-common-libraries.2](docs/functional-spec.md#2-resource-configuration), [§FS-common-libraries.3](docs/functional-spec.md#3-native-image-tracing-agent),
  [§FS-common-libraries.4](docs/functional-spec.md#4-agent-metadata-post-processing), [§FS-common-libraries.7](docs/functional-spec.md#7-schema-validation)
- `graalvm-reachability-metadata/src/main/java/org/graalvm/reachability/`: reachability metadata
  repository parsing, version selection, query classification, and missing metadata reporting.
  [§FS-common-libraries.5](docs/functional-spec.md#5-reachability-metadata-repository), [§FS-common-libraries.6](docs/functional-spec.md#6-missing-metadata-reporting), [§AR-common-libraries.3](docs/architecture.md#3-reachability-metadata-architecture)
- `junit-platform-native/src/main/java/org/graalvm/junit/platform/`: JUnit native launcher,
  Native Image feature registration, and test-engine configuration providers.
  [§AR-common-libraries.4](docs/architecture.md#4-junit-native-architecture)
