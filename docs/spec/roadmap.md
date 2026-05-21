# RM-001-expand-spec-coverage-from-module-boundaries-to-feature-details: Expand the grund spec from repository boundaries to feature-level contracts

This item follows the initial adoption decision in
§DEC-001-adopt-component-functional-architecture-docs.
This initial grund specification captures the repository's module boundaries and the main product
behaviors. Future spec work should add narrower specs for individual Gradle tasks, Maven goals,
agent modes, reachability metadata resolution cases, SBOM generation, layered image support, and
native test execution edge cases.

# RM-002-connect-code-and-tests-to-grund-citations: Add citations from high-risk implementation and tests to the spec

Once the team agrees on the spec shape, source comments, test fixtures, and documentation sections
can cite the relevant grund IDs. Keep `require_grounding = false` until citation coverage is
intentional enough to enforce in CI.
