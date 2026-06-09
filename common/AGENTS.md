# Common Libraries — agent instructions

## Grounding with grund (v2)

This subproject is the `common` grund workspace member for shared libraries. Resolve local
behavior from the local docs first:

- [GRUND](docs/grund.md): Common library purpose
- [GOAL](docs/goals.md): Common library goals
- [REQ](docs/requirements.md): Common library requirements
- [FS](docs/functional-spec.md): Common library functional specification
- [AR](docs/architecture.md): Common library architecture
- [E2E](docs/e2e.md): Common library executable tests

### Workspace members

Cross-project citations use §alias/<ID>.

- `common` → [AGENTS.md](AGENTS.md)
- `gradle` → [../native-gradle-plugin/AGENTS.md](../native-gradle-plugin/AGENTS.md)
- `maven` → [../native-maven-plugin/AGENTS.md](../native-maven-plugin/AGENTS.md)
- `root` → [../AGENTS.md](../AGENTS.md)

Repository-wide grounding, non-goals, shared plugin behavior, and CI remain in the `root`
namespace under `../docs/spec`. Gradle and Maven adapter behavior lives in the `gradle` and
`maven` namespaces. Prefer local IDs such as `§FS-common-libraries.5.1` for shared library
behavior, and cite root IDs such as `§root/FS-plugin-common` only for cross-project
contracts.

Use `grund common/<ID>`, `grund common/<ID> --toc`, and `grund common/<ID> --full` from the
repository root to resolve common citations. Run `grund check` from the repository root before
changing common specs or source citations.

### Module Map

Use this map before editing: pick the owning path, then resolve the cited spec before changing
behavior.

- `utils/src/main/java/org/graalvm/buildtools/utils/`: shared Native Image argument handling,
  resource analysis, agent configuration models, schema validation, JUnit dependency helpers, and
  constants. §FS-common-libraries.1, §FS-common-libraries.2, §FS-common-libraries.3,
  §FS-common-libraries.4, §FS-common-libraries.7
- `graalvm-reachability-metadata/src/main/java/org/graalvm/reachability/`: reachability metadata
  repository parsing, version selection, query classification, and missing metadata reporting.
  §FS-common-libraries.5, §FS-common-libraries.6, §AR-common-libraries.3
- `junit-platform-native/src/main/java/org/graalvm/junit/platform/`: JUnit native launcher,
  Native Image feature registration, and test-engine configuration providers.
  §AR-common-libraries.4
