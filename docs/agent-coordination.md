# Agent Coordination Guide

## Purpose

This project can be worked by multiple agents in parallel if each agent has a clear ownership boundary and communicates through durable repo files instead of relying on chat memory.

The default split:

```text
Codex:
  Clean-room ALR implementation, specs, tests, repo integration.

Hermes:
  Device evidence, black-box backend probes, Android APK smoke, GUI/GPU runtime observations.
```

## Communication Model

Agents should not need direct live chat with each other. Use the repo as the coordination surface.

Shared files:

- `docs/agent-sync.md`: append-only current status and handoff notes.
- `docs/plans/parallel-workstreams.md`: workstream ownership and active tasks.
- `docs/prompts/`: reusable prompts for external agents.

Rules:

- Each agent updates `docs/agent-sync.md` at the start and end of a work session.
- Each update must include branch/worktree, touched files, commands run, tests run, and blockers.
- Do not overwrite another agent's status entry.
- Do not rewrite another agent's docs or code unless explicitly assigned.
- Prefer adding a new section or follow-up note over editing an active note written by the other agent.

## Branching

Recommended branch names:

```text
codex/alr-clean-runtime-spec
codex/alr-runtime-launcher
codex/alr-path-hook

hermes/proroot-ab-probe
hermes/device-gpu-evidence
hermes/apk-smoke-reports
```

If both agents work in the same local checkout, coordinate before switching branches. Safer option: use separate worktrees.

Example:

```bash
git worktree add ../androlinux-codex codex/alr-runtime-launcher
git worktree add ../androlinux-hermes hermes/proroot-ab-probe
```

## Ownership Boundaries

### Codex-Owned Areas

Default write scope:

- `docs/product-requirements.md`
- `docs/clean-room-protocol.md`
- `docs/alr-execution-backend-spec.md`
- `docs/plans/implementation-milestones.md`
- `app/src/main/cpp/alr_runtime/`
- future ALR runtime launcher/hook/bridge source files
- host/native tests for ALR logic

Codex should avoid editing:

- Device evidence captured by Hermes.
- APK smoke result logs.
- Optional external backend binary artifacts unless assigned.

### Hermes-Owned Areas

Default write scope:

- `docs/evidence/`
- `docs/research/`
- optional backend source/version/sha notes
- device run reports
- APK smoke output summaries
- focused fixes for Android-device-only smoke reporting when assigned

Hermes should avoid editing:

- ALR runtime implementation internals.
- Clean-room implementation code based on proprietary runtime behavior.
- Core specs unless proposing changes through `docs/agent-sync.md`.

## Handoff Entry Format

Append entries to `docs/agent-sync.md`:

```markdown
## 2026-05-15 HH:MM KST - <agent> - <workstream>

Branch/worktree:
- `<branch or path>`

Touched files:
- `<file>`

What changed:
- `<short summary>`

Commands/tests:
- `<command>` -> `<PASS/FAIL/SKIP>`

Evidence:
- `<device/model/log path/report section>`

Blockers:
- `<none or concrete blocker>`

Next recommended action:
- `<one or two concrete steps>`
```

## Conflict Rules

- If both agents need the same file, pause and split the change by section.
- If Hermes finds a device failure that contradicts Codex specs, Hermes records evidence first; Codex updates specs/tests after reviewing it.
- If Codex changes expected report strings, Codex must update tests and note the change in `docs/agent-sync.md` so Hermes can adjust device smoke checks.
- If a closed/proprietary runtime behavior is involved, Hermes records black-box inputs/outputs only; Codex converts that into clean tests/spec language.

## Stop Conditions

Stop and ask before proceeding if:

- A task requires modifying proprietary binaries.
- A task requires copying implementation details from closed binaries.
- A branch contains unrelated user changes that would be overwritten.
- Device evidence shows Android policy blocks a planned architecture.
- Licensing obligations are unclear for a new vendored dependency.

## Best Parallel Split

Run two agents like this:

```text
Agent 1 / Codex:
  Build the clean ALR runtime path from docs to tests to source.

Agent 2 / Hermes:
  Build the evidence pipeline: device smoke, optional proroot A/B, GPU reports.
```

This keeps the critical clean-room boundary intact:

```text
Hermes observes and reports behavior.
Codex implements from specs and tests.
```

