# REPO-001-module-boundaries: The repository is divided into product plugins, shared libraries, fixtures, documentation, and build infrastructure

The repository should be specified as a composite workspace with these module groups:

| Module group | Paths | Responsibility |
| --- | --- | --- |
| Gradle product plugin | `native-gradle-plugin/` | Gradle plugin API, DSL, tasks, command-line providers, Gradle functional tests, and Gradle publication metadata. |
| Maven product plugin | `native-maven-plugin/` | Maven mojos, plugin descriptor generation, Maven configuration objects, Maven functional tests, SBOM behavior, and issue reproducers. |
| Shared libraries | `common/utils/`, `common/graalvm-reachability-metadata/`, `common/junit-platform-native/` | Build-tool-neutral support used by one or both product plugins. |
| Samples and fixtures | `samples/`, `test-support/`, plugin `src/functionalTest/`, plugin `src/testFixtures/`, `native-maven-plugin/reproducers/` | Realistic projects and reusable test artifacts that verify plugin behavior. |
| Documentation | `docs/`, `README.md`, `DEVELOPING.md` | User guides, changelog, developer guide, and this grund specification. |
| Build and CI infrastructure | `build-logic/`, `.github/`, `gradle/`, `config/`, `schemas/`, root Gradle files | Repository conventions, aggregation, publication, validation, schemas, and CI workflows. |

The product plugins are the externally visible deliverables. Shared libraries hold cross-cutting
Native Image behavior. Samples, fixtures, documentation, and infrastructure support development
and verification without becoming product API.

## 1. Product boundaries

The repository has two build-tool product plugins and several support modules.

### 1.1 Gradle product plugin

`native-gradle-plugin/` owns the Gradle user surface and must keep Gradle API usage inside that
module. It implements §FS-001-gradle-plugin-native-image-workflow and follows
§AR-001-gradle-plugin-boundary.

### 1.2 Maven product plugin

`native-maven-plugin/` owns the Maven user surface and must keep Maven API usage inside that
module. It implements §FS-002-maven-plugin-native-image-workflow and follows
§AR-002-maven-plugin-boundary.

### 1.3 Shared libraries

`common/` owns build-tool-neutral Native Image behavior, reachability metadata lookup, utility
code, and JUnit native runtime support. It follows §AR-003-shared-common-libraries and implements
the shared behavior in §FS-003-metadata-and-resource-workflows and §FS-004-native-test-execution.

### 1.4 Fixtures and samples

`samples/`, `test-support/`, plugin test fixtures, and Maven reproducers provide executable
evidence for product behavior. They follow §AR-004-samples-and-functional-fixtures.

### 1.5 Infrastructure and documentation

`build-logic/`, `.github/`, `gradle/`, `config/`, `schemas/`, root build files, and `docs/`
support build, validation, documentation, CI, and release workflows. They follow
§AR-005-build-documentation-and-release-infrastructure.

## 2. Dependency direction

Repository boundaries are enforced by dependency direction rather than by identical module shapes
for Gradle and Maven.

### 2.1 Product to common

Product plugins may depend on common modules for Native Image command-line utilities, metadata
repository behavior, resource analysis, schema validation, and native test support.

### 2.2 Common independence

Common modules must not depend on Gradle or Maven plugin implementation classes. If common code
needs build-tool state, product plugins convert it into plain Java values before calling common
APIs.

### 2.3 Infrastructure to product

Build and CI infrastructure may depend on product modules to assemble, test, publish, or document
them. Product runtime code must not depend on infrastructure implementation classes.

### 2.4 Fixture dependencies

Fixtures may depend on product artifacts under test and support artifacts from `test-support/`.
They should not be used as shared runtime libraries for product code.

## 3. Specification rule

New specification work should follow the component-doc pattern. Put behavior contracts in the
component's `functional-spec.md`; put implementation structure and ownership boundaries in the
component's `architecture.md`. Use `FS` IDs for functional behavior, `AR` IDs for architecture,
and keep the component visible in the slug and path.

The current component architecture specs are §AR-001-gradle-plugin-boundary,
§AR-002-maven-plugin-boundary, §AR-003-shared-common-libraries,
§AR-004-samples-and-functional-fixtures, and
§AR-005-build-documentation-and-release-infrastructure.

### 3.1 Functional specifications

Functional specs state externally observable behavior, user workflows, build-tool contracts,
metadata behavior, and verification expectations. Code and tests should cite the most specific
functional section that justifies behavior.

### 3.2 Architecture specifications

Architecture specs state module ownership, dependency direction, internal structure, and
implementation responsibilities. Code that documents placement or dependency decisions should cite
the most specific architecture section.

### 3.3 Decisions and roadmap

Decisions explain tradeoffs that should not be rediscovered. Roadmap items track planned spec and
coverage expansion. They should cite goals, specs, or repository boundaries when they depend on
them.
