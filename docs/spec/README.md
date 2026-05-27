# Native Build Tools Specification

This is the maintainer-facing specification. It follows the component-doc pattern: each substantial
component owns a `functional-spec.md` (user- or build-visible behavior) and/or `architecture.md`
(ownership, dependency direction, implementation structure). The folder gives the component, the ID
kind gives the artifact type. Declarations use numbered subsections as feature-level citation
targets, so implementation and test comments can cite narrow behavior such as
`§FS-001-gradle-plugin-native-image-workflow.5.3`.

The canonical registry of ID kinds and where each kind lives is the project map in
[AGENTS.md](../../AGENTS.md); [`.agents/grund.toml`](../../.agents/grund.toml) is the
machine-readable source. Run `grund list` to enumerate every ID and `grund <ID>` to resolve a
citation rather than maintaining a second copy of the registry here.

## Component documents

| File | Scope |
| --- | --- |
| [repository/architecture.md](repository/architecture.md) | Repository structure and ownership |
| [gradle-plugin/functional-spec.md](gradle-plugin/functional-spec.md) | Gradle plugin behavior |
| [gradle-plugin/architecture.md](gradle-plugin/architecture.md) | Gradle plugin architecture |
| [maven-plugin/functional-spec.md](maven-plugin/functional-spec.md) | Maven plugin behavior |
| [maven-plugin/architecture.md](maven-plugin/architecture.md) | Maven plugin architecture |
| [common/functional-spec.md](common/functional-spec.md) | Shared metadata and resource behavior |
| [common/architecture.md](common/architecture.md) | Shared common library architecture |
| [testing/functional-spec.md](testing/functional-spec.md) | Native test behavior |
| [testing/architecture.md](testing/architecture.md) | Samples and fixtures architecture |
| [build-infra/architecture.md](build-infra/architecture.md) | Build, docs, release, and CI architecture |

Single-file kinds — motivation (`grund.md`), goals (`goals.md`), non-goals (`non-goals.md`),
requirements (`requirements.md`), decisions (`decisions.md`), roadmap (`roadmap.md`), and glossary
(`glossary.md`) — live beside these folders; see the project map above for the authoritative kind
list.

## Citation shape

Functional specs describe user-visible or build-visible behavior. Architecture specs describe
ownership, dependency direction, and implementation structure. Prefer the deepest applicable
section citation when writing code comments, tests, or future docs.
