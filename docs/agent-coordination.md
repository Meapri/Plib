# Agent Coordination Guide

## Purpose

Plib is now operated as a Codex-owned project. Earlier multi-agent/Hermes handoffs are historical only and should not be used for new work.

Codex owns:

- clean-room ALR runtime implementation
- Android GUI/GPU bridge implementation
- APK/device evidence capture
- optional backend black-box probes
- repository integration, tests, issues, and PRs

External agent branches or PRs are not integration sources unless the user explicitly re-enables that workflow. If an old external branch contains a useful behavior, Codex must re-derive and reimplement it from project specs, public docs, tests, and clean black-box evidence.

## Communication Model

Use the repo as a durable project log, not as an inter-agent chat surface.

Shared files:

- `docs/agent-sync.md`: append-only project status and bundle-completion notes.
- `docs/plans/parallel-workstreams.md`: Codex-owned workstream map.
- `docs/prompts/`: reusable prompts; external-agent prompts may remain archived but are not active workflow instructions.

Default cadence:

- Work in large implementation/evidence bundles.
- Open one branch per bundle.
- Attach tests, APK proof, and claim boundaries in the PR body.
- Merge only after local verification passes.
- Do not add frequent status-only commits or coordination comments.

Rules:

- Update `docs/agent-sync.md` at bundle completion or when a real blocker changes the project path.
- Each update must include branch/worktree, touched files, commands run, tests run, evidence, and blockers.
- Keep previous historical entries intact.
- Do not claim Android device behavior from host-only tests.
- Do not claim performance superiority without a benchmark harness and device evidence.

## Branching

Recommended branch names:

```text
codex/alr-static-image-plan
codex/alr-entry-trampoline
codex/gui-backend-labels
codex/device-evidence
codex/optional-backend-probe
```

Use one local checkout unless a long-running device/evidence branch genuinely needs isolation.

## Ownership Boundaries

### Codex-Owned Implementation Areas

- `app/src/main/cpp/alr_runtime/`
- `app/src/main/cpp/alr_runtime_*`
- `app/src/main/cpp/runtime_*`
- Kotlin app/device evidence surfaces under `app/src/main/java/`
- ALR, graphics, device, and optional-backend tests
- specs and plans under `docs/`

### Clean-Room Boundaries

Codex may use:

- project specs and tests
- public Linux, Android, glibc, ELF, EGL, GLES, Vulkan, Wayland, and X11 documentation
- compatible open-source projects under their licenses
- black-box command inputs/outputs from optional external runtimes

Codex must not use:

- proprietary disassembly or decompilation as implementation input
- copied closed-runtime algorithms
- undocumented private Android APIs as required product dependencies
- old external-agent implementation diffs as code to transplant without clean review

## Bundle Entry Format

Append entries to `docs/agent-sync.md` when a bundle is merged or a true blocker appears:

```markdown
## 2026-05-15 HH:MM KST - Codex - <workstream>

Branch/worktree:
- `<branch or path>`

Touched files:
- `<file>`

What changed:
- `<short summary>`

Commands/tests:
- `<command>` -> `<PASS/FAIL/SKIP>`

Evidence:
- `<APK hash/device/log/report section>`

Blockers:
- `<none or concrete blocker>`

Next recommended action:
- `<one or two concrete steps>`
```

## Conflict Rules

- If the local worktree contains unrelated user changes, do not overwrite them.
- If an old external PR is dirty or stale, close it and reimplement needed behavior on a fresh Codex branch.
- If a device result contradicts a host-side assumption, preserve the device evidence and update specs/tests before continuing.
- If closed/proprietary runtime behavior is involved, record only black-box input/output facts and convert them into clean tests.

## Stop Conditions

Stop and ask before proceeding if:

- A task requires modifying proprietary binaries.
- A task requires copying implementation details from closed binaries.
- Licensing obligations are unclear for a new vendored dependency.
- Android policy blocks the planned architecture on target devices and no clean alternative is obvious.
- A destructive local/device action could remove user data.
