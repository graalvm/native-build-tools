# AR-repository-architecture: Native Build Tools repository architecture

This is the structural map of Native Build Tools: what the major components are, how they fit
together, and how a change flows from implementation to verified Gradle and Maven plugin
artifacts. The behavioral contract for the product surface is §FS-repository-functional-spec, and
the component documents cited below own the detailed behavior and implementation boundaries.

## 1. What the repository is

Native Build Tools is a composite Gradle workspace that produces two build-tool plugins and the
shared libraries those plugins use to invoke GraalVM Native Image, run native tests, manage
metadata, and keep Gradle and Maven behavior aligned. The repository also owns samples,
functional-test fixtures, user documentation, CI, and release infrastructure.

The product plugins are the externally visible deliverables. Shared libraries hold cross-cutting
Native Image behavior. Samples, fixtures, documentation, and infrastructure support development
and verification without becoming product API.

## 2. Components

| Component | Paths | Role | Spec |
| --- | --- | --- | --- |
| Gradle product plugin | `native-gradle-plugin/` | Gradle plugin API, DSL, tasks, command-line providers, Gradle functional tests, and Gradle publication metadata. | §GRADLE-plugin |
| Maven product plugin | `native-maven-plugin/` | Maven mojos, plugin descriptor generation, Maven configuration objects, Maven functional tests, SBOM behavior, and issue reproducers. | §MAVEN-plugin |
| Shared libraries | `common/utils/`, `common/graalvm-reachability-metadata/`, `common/junit-platform-native/` | Build-tool-neutral Native Image utilities, metadata repository lookup, resource analysis, agent modes, and JUnit native runtime support. | §COMMON-libraries |
| Native tests, samples, and fixtures | `samples/`, `test-support/`, plugin `src/functionalTest/`, plugin `src/testFixtures/`, `native-maven-plugin/reproducers/` | Realistic projects and reusable test artifacts that verify plugin behavior. | §TESTING-native-tests-and-fixtures |
| Build infrastructure | `build-logic/`, root Gradle files, `gradle/`, `config/`, `schemas/` | Repository conventions, aggregation, publication, validation, schemas, and generated support artifacts. | §BUILD-infrastructure |
| CI workflows | `.github/workflows/`, `.github/actions/` | Pull request gates, dev-build checks, documentation deployment, snapshot deployment, and shared action setup. | §CI-pull-request-ci |
| User and maintainer docs | `docs/`, `README.md`, `DEVELOPING.md`, `AGENTS.md` | User guides, changelog, developer guide, and grounded maintainer specification. | §BUILD-infrastructure.3 |

## 3. Dependency direction

Repository boundaries are enforced by dependency direction rather than by identical module shapes
for Gradle and Maven. Product plugins may depend on common modules for Native Image command-line
utilities, metadata repository behavior, resource analysis, schema validation, and native test
support. Common modules must not depend on Gradle or Maven plugin implementation classes. If
common code needs build-tool state, product plugins convert it into plain Java values before
calling common APIs.

Build and CI infrastructure may depend on product modules to assemble, test, publish, or document
them. Product runtime code must not depend on infrastructure implementation classes. Fixtures may
depend on product artifacts under test and support artifacts from `test-support/`, but they should
not be used as shared runtime libraries for product code.

## 4. How work flows through the system

1. A behavior change starts in the most specific component spec: §GRADLE-plugin for Gradle,
   §MAVEN-plugin for Maven, §COMMON-libraries for shared behavior,
   §TESTING-native-tests-and-fixtures for native-test or fixture behavior, or
   §BUILD-infrastructure for build and release infrastructure.
2. Product or common code implements the behavior with citations to the component section that
   owns it. Java source comments use bare ID citations because Java checkstyle is ASCII-only.
3. Unit tests, functional tests, and samples validate the changed behavior locally through the
   commands specified by §E2E-functional-test-suite.
4. Pull request CI runs the matching workflow gates from §CI-pull-request-ci and validates grund
   citations through §CI-check-grund-spec.
5. Release and snapshot infrastructure publishes the externally visible plugin artifacts only
   through the repository's build and CI boundaries (§BUILD-infrastructure.5).

## 5. Specification layout

Top-level `functional-spec.md` and `architecture.md` describe the repository-level behavior and
structure. Component documents live as single Markdown files under `docs/spec/`, with their own
kind prefixes because they combine functional behavior, architecture, and verification concerns:
`GRADLE`, `MAVEN`, `COMMON`, `TESTING`, `BUILD`, `CI`, and `E2E`.

Functional specs state externally observable behavior, user workflows, build-tool contracts,
metadata behavior, and verification expectations. Architecture sections state module ownership,
dependency direction, internal structure, and implementation responsibilities. Code, tests, YAML,
and scripts should cite the most specific component or workflow section that justifies the
behavior.
