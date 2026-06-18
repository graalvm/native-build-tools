# Native Gradle Plugin — agent instructions

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

- [GRUND](docs/grund.md): Gradle plugin purpose
- [GOAL](docs/goals.md): Gradle plugin goals
- [REQ](docs/requirements.md): Gradle plugin requirements
- [FS](docs/functional): Gradle plugin functional specifications
- [AR](docs/architecture.md): Gradle plugin architecture
- [E2E](docs/e2e.md): Gradle plugin end-to-end tests

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `.agents/grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>.

- [`common`](../common/AGENTS.md): Build-tool-neutral shared libraries for Native Image utilities and metadata workflows
- [`gradle`](AGENTS.md): Gradle plugin specs and implementation for Native Image workflows
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
namespace under `../docs/spec`. Common library behavior lives in the `common` namespace under
`../common/docs`. Prefer local IDs such as `§FS-native-tasks.1` for Gradle behavior, and cite
root or common IDs such as `§root/FS-plugin-common`,
`§root/NGOAL-no-flag-mirroring`, or `§common/FS-common-libraries` only for
cross-project contracts. Java comments use the same marked citation shape because Checkstyle allows
`§` as the only non-ASCII citation exception.

Use `grund gradle/<ID>`, `grund gradle/<ID> --toc`, and `grund gradle/<ID> --full` from the
repository root to resolve Gradle citations. Run `grund check` from the repository root before
changing Gradle plugin specs or source citations.

## Module Map

Use this map before editing: pick the owning path, then resolve the cited spec before changing
behavior.

- `src/main/java/org/graalvm/buildtools/gradle/NativeImagePlugin.java`: plugin registration,
  extension setup, default binary wiring, task registration, and Gradle model integration.
  [§FS-plugin-model](docs/functional/plugin-model.md#fs-plugin-model-gradle-plugin-activation-and-dsl-model), [§AR-gradle-plugin.2](docs/architecture.md#2-extension-and-option-model), [§AR-gradle-plugin.3](docs/architecture.md#3-task-graph-architecture)
- `src/main/java/org/graalvm/buildtools/gradle/tasks/`: user-visible Gradle tasks for native
  compile/run/test, resource generation, metadata collection, missing metadata reporting, dynamic
  access metadata, and agent metadata copy. [§FS-native-tasks](docs/functional/native-image-tasks.md#fs-native-tasks-gradle-native-image-tasks-build-and-run-native-image-outputs), [§FS-resources-and-metadata](docs/functional/resources-and-metadata.md#fs-resources-and-metadata-gradle-tasks-generate-resources-and-consume-reachability-metadata),
  [§FS-tracing-agent](docs/functional/tracing-agent.md#fs-tracing-agent-gradle-tasks-attach-and-post-process-native-image-tracing-agent-metadata), [§FS-native-tests](docs/functional/native-tests.md#fs-native-tests-gradle-tasks-compile-and-run-native-junit-tests)
- `src/main/java/org/graalvm/buildtools/gradle/internal/NativeImageCommandLineProvider.java`:
  native-image argument construction and argument-file behavior. [§FS-native-invocation.3](docs/functional/native-image-invocation.md#3-command-line-construction),
  [§FS-native-invocation.4](docs/functional/native-image-invocation.md#4-argument-files)
- `src/main/java/org/graalvm/buildtools/gradle/internal/NativeImageExecutableLocator.java`:
  GraalVM and native-image executable discovery for Gradle tasks. [§FS-native-invocation.1](docs/functional/native-image-invocation.md#1-executable-discovery),
  [§AR-gradle-plugin.4](docs/architecture.md#4-command-line-and-executable-services)
- `src/main/java/org/graalvm/buildtools/gradle/NativeImageService.java`: concurrency control for
  parallel native-image builds. [§FS-native-invocation.6](docs/functional/native-image-invocation.md#6-parallel-native-builds)
- `src/functionalTest/`: Gradle TestKit scenarios and generated/sample project coverage.
  [§E2E-functional-tests](docs/e2e.md#e2e-functional-tests-gradle-functional-tests-exercise-real-gradle-native-image-builds)
- `src/testFixtures/`: reusable Gradle functional-test fixtures and project builders.
  [§AR-gradle-plugin.6](docs/architecture.md#6-tests-and-fixtures)
