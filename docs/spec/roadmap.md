# RM-expand-spec-coverage-from-module-boundaries-to-feature-details: Expand the grund spec from repository boundaries to feature-level contracts

This item follows the initial adoption decision in
§DEC-adopt-component-functional-architecture-docs.
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

# RM-connect-code-and-tests-to-grund-citations: Grow citations from high-risk implementation and tests to the spec

The initial source-comment citation pass is in place for high-risk implementation paths and
`[reference] strict = true` requires marked citations. Keep `require_grounding = false` until test,
fixture, sample, and lower-risk source coverage is intentional enough to enforce in CI.

## 1. First citation targets

Continue with focused test and fixture citations for high-risk behavior: Native Image command-line
construction, executable lookup, reachability metadata resolution, schema validation, agent mode
command lines, native test launcher selection, Maven main-class discovery, Gradle binary task
registration, and regression fixtures that preserve scenario behavior.
