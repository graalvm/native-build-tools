# RM-001-expand-spec-coverage-from-module-boundaries-to-feature-details: Expand the grund spec from repository boundaries to feature-level contracts

This item follows the initial adoption decision in
§DEC-001-adopt-component-functional-architecture-docs.
The initial grund specification captured repository module boundaries and the main product
behaviors. The expanded specification now adds feature-level sections for Gradle tasks, Maven
goals, agent modes, reachability metadata resolution, SBOM generation, resource generation,
dynamic access metadata, layered image support, native tests, fixtures, CI, and release
infrastructure.

## 1. Remaining detail work

Future spec work should add or refine sections when implementation changes expose narrower
contracts than the current component sections. Likely follow-up areas include exact task input
contracts, native-image option precedence tables, Maven descriptor parameter tables, and fixture
coverage matrices.

# RM-002-connect-code-and-tests-to-grund-citations: Add citations from high-risk implementation and tests to the spec

Once the team agrees on the spec shape, source comments, test fixtures, and documentation sections
can cite the relevant grund IDs. Keep `require_grounding = false` until citation coverage is
intentional enough to enforce in CI.

## 1. First citation targets

Start with high-risk behavior: Native Image command-line construction, executable lookup,
reachability metadata resolution, schema validation, agent mode command lines, native test
launcher selection, Maven main-class discovery, Gradle binary task registration, and fixture tests
that preserve regression scenarios.
