# Native Build Tools Specification

This is the maintainer-facing specification. The top-level `AR` file describes repository
architecture. Substantial components own separate functional and architecture files under their
component directory. Functional files declare `FS-*` IDs, and architecture files declare `AR-*`
IDs, so the ID kind gives the artifact type.
Declarations use numbered subsections as feature-level citation targets, so implementation and
test comments can cite narrow behavior such as
`§FS-gradle-plugin.5.3`.

The canonical registry of ID kinds and where each kind lives is the project map in
[AGENTS.md](../../AGENTS.md); [`.agents/grund.toml`](../../.agents/grund.toml) is the
machine-readable source. Run `grund list` to enumerate every ID and `grund <ID>` to resolve a
citation rather than maintaining a second copy of the registry here.

## Component documents

| File | Scope |
| --- | --- |
| [architecture.md](architecture.md) | Top-level repository architecture, deployment, and ownership boundaries |
| [gradle-plugin/functional-spec.md](gradle-plugin/functional-spec.md) | Gradle plugin behavior |
| [gradle-plugin/architecture.md](gradle-plugin/architecture.md) | Gradle plugin implementation architecture |
| [maven-plugin/functional-spec.md](maven-plugin/functional-spec.md) | Maven plugin behavior |
| [maven-plugin/architecture.md](maven-plugin/architecture.md) | Maven plugin implementation architecture |
| [common/functional-spec.md](common/functional-spec.md) | Shared metadata, resource, parity, and common-library behavior |
| [common/architecture.md](common/architecture.md) | Shared common-library architecture |
| [testing/functional-spec.md](testing/functional-spec.md) | Native test behavior |
| [testing/architecture.md](testing/architecture.md) | Samples, fixtures, and native test support architecture |
| [build-infra/functional-spec.md](build-infra/functional-spec.md) | Build, docs, release, and CI behavior |
| [build-infra/architecture.md](build-infra/architecture.md) | Build infrastructure architecture |
| [ci.md](ci.md) | Pull request CI workflows and shared actions |
| [e2e.md](e2e.md) | End-to-end and functional test execution |

Single-file kinds — motivation (`grund.md`), goals (`goals.md`), non-goals (`non-goals.md`),
requirements (`requirements.md`), decisions (`decisions.md`), roadmap (`roadmap.md`), and glossary
(`glossary.md`) — live beside the component files; see the project map above for the authoritative
kind list.

## Citation shape

Functional specs describe user-visible or build-visible behavior. Architecture specs describe
ownership, dependency direction, and implementation structure. Prefer the deepest applicable
section citation when writing code comments, tests, or future docs.
