# Native Maven Plugin — agent instructions

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

- [GRUND](docs/grund.md): Maven plugin purpose
- [GOAL](docs/goals.md): Maven plugin goals
- [REQ](docs/requirements.md): Maven plugin requirements
- [FS](docs/functional): Maven plugin functional specifications
- [AR](docs/architecture.md): Maven plugin architecture
- [E2E](docs/e2e.md): Maven plugin end-to-end tests

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `.agents/grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>.

- [`common`](../common/AGENTS.md): Build-tool-neutral shared libraries for Native Image utilities and metadata workflows
- [`gradle`](../native-gradle-plugin/AGENTS.md): Gradle plugin specs and implementation for Native Image workflows
- [`maven`](AGENTS.md): Maven plugin specs and implementation for Native Image workflows
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
namespace under `../docs/spec`. Common library behavior lives in the `common` namespace under
`../common/docs`. Prefer local IDs such as `§FS-goal-surface.1` for Maven behavior, and cite root
or common IDs such as `§root/FS-plugin-common`,
`§root/NGOAL-no-flag-mirroring`, or `§common/FS-common-libraries` only for
cross-project contracts. Java comments use the same marked citation shape because Checkstyle allows
`§` as the only non-ASCII citation exception.

Use `grund maven/<ID>`, `grund maven/<ID> --toc`, and `grund maven/<ID> --full` from the
repository root to resolve Maven citations. Run `grund check` from the repository root before
changing Maven plugin specs or source citations.

## Module Map

Use this map before editing: pick the owning path, then resolve the cited spec before changing
behavior.

- `src/main/java/org/graalvm/buildtools/maven/*Mojo.java`: user-visible Maven goals, lifecycle
  bindings, parameters, and goal execution. [§FS-goal-surface](docs/functional/goal-surface.md#fs-goal-surface-maven-goals-expose-native-image-workflows), [§FS-native-builds](docs/functional/native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state),
  [§AR-maven-plugin.2](docs/architecture.md#2-mojo-hierarchy)
- `src/main/java/org/graalvm/buildtools/maven/AbstractNativeImageMojo.java`: native-image
  invocation, executable lookup, shared configuration handling, and resource/metadata workflow
  support. [§FS-native-builds](docs/functional/native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state), [§FS-config-model.4](docs/functional/configuration-model.md#4-toolchain-and-executable-lookup), [§AR-maven-plugin.4](docs/architecture.md#4-native-image-invocation-architecture)
- `src/main/java/org/graalvm/buildtools/maven/NativeCompileNoForkMojo.java`: lifecycle-bound
  native compilation, main-class discovery, skipping, generated resources, dynamic access metadata,
  and SBOM behavior. [§FS-goal-surface.1](docs/functional/goal-surface.md#1-build-goals), [§FS-native-builds](docs/functional/native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state)
- `src/main/java/org/graalvm/buildtools/maven/NativeTestMojo.java`: native test build and
  execution through Maven. [§FS-goal-surface.2](docs/functional/goal-surface.md#2-test-goal), [§FS-native-tests](docs/functional/native-tests.md#fs-native-tests-maven-goals-compile-and-run-native-junit-tests)
- `src/main/java/org/graalvm/buildtools/maven/MetadataCopyMojo.java` and
  `MergeAgentFilesMojo.java`: tracing-agent metadata merge and copy workflows.
  [§FS-tracing-agent](docs/functional/tracing-agent.md#fs-tracing-agent-maven-goals-attach-and-post-process-native-image-tracing-agent-metadata)
- `src/functionalTest/`: isolated Maven executor scenarios and generated/sample project coverage.
  [§E2E-functional-tests](docs/e2e.md#e2e-functional-tests-maven-functional-tests-exercise-real-maven-native-image-builds)
- `reproducers/`: issue reproducer projects that protect Maven-specific regressions.
  [§AR-maven-plugin.6](docs/architecture.md#6-functional-test-infrastructure)
