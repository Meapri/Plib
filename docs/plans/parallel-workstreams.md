# Parallel Workstreams

## Goal

Run two agents in parallel without contaminating the clean-room implementation or fighting over the same files.

## Workstream A: Clean ALR Runtime

Recommended agent:

- Codex

Mission:

- Turn the ALR execution backend specification into tests and implementation.
- Keep implementation clean-room.
- Preserve PRoot fallback.

Primary docs:

- `docs/product-requirements.md`
- `docs/clean-room-protocol.md`
- `docs/alr-execution-backend-spec.md`
- `docs/plans/implementation-milestones.md`

Write scope:

- `app/src/main/cpp/alr_runtime/`
- future `app/src/main/cpp/alr_runtime_launcher*`
- future `app/src/main/cpp/alr_runtime_hook*`
- ALR-specific host/native tests
- ALR-specific documentation updates

Do not touch by default:

- Optional proprietary backend artifacts.
- Device evidence logs.
- Hermes-owned smoke reports.

First tasks:

1. Add ALR launcher skeleton and launch-plan report section.
2. Add tests for ALR backend report lines.
3. Add config serialization model for rootfs/cwd/env/binds.
4. Add hook-library design stubs without claiming execution.
5. Only then implement path hook MVP.

Acceptance for first bundle:

```text
ALR RUNTIME LAUNCHER AVAILABLE: PASS
ALR RUNTIME CONFIG BUILD: PASS
ALR RUNTIME DIRECT APP-DATA EXEC POLICY: PASS
```

## Workstream B: Device Evidence and Optional Backend Probe

Recommended agent:

- Hermes

Mission:

- Keep device evidence real.
- Compare PRoot with optional proroot-class backend through black-box behavior only.
- Maintain GPU/GUI proof reports.

Primary docs:

- `docs/clean-room-protocol.md`
- `docs/android-graphics-bridge-spec.md`
- `docs/plans/implementation-milestones.md`
- `docs/plans/2026-05-15-zero-overhead-linux-gpu-runtime-roadmap.md`

Write scope:

- `docs/evidence/`
- `docs/research/`
- optional backend metadata docs
- APK smoke result notes
- narrow Android report fixes only when assigned

Do not touch by default:

- `app/src/main/cpp/alr_runtime/`
- ALR clean-room implementation files.
- Clean-room specs except by proposing changes in `docs/agent-sync.md`.

First tasks:

1. Confirm current APK build/install/smoke path.
2. Capture current PRoot baseline on device.
3. Package optional proroot-class backend only if license/source/sha are recorded.
4. Run hello, shell, dpkg/apt preflight A/B.
5. Capture GPU/GUI bridge evidence and known false-fail report lines.

Acceptance for first bundle:

```text
PROOT BACKEND EXECUTION: PASS
OPTIONAL LOW-OVERHEAD BACKEND AVAILABLE: PASS/FAIL/SKIP
OPTIONAL LOW-OVERHEAD ROOTFS EXECUTION: PASS/FAIL/SKIP
OPTIONAL LOW-OVERHEAD DPKG LOCAL INSTALL: PASS/FAIL/SKIP
HOST GPU EGL/GLES EXECUTION: PASS
GUEST GUI GPU SURFACE EXECUTION: PASS or known-fail with evidence
```

## Shared Contract

Both workstreams must preserve:

- Safe rootfs extraction.
- No direct writable app-data executable entrypoint policy.
- Clean guest environment.
- PRoot fallback.
- Deterministic report strings.
- Clean-room boundary.

## Merge Order

Prefer this order:

1. Documentation and tests.
2. Hermes evidence-only additions.
3. Codex ALR launcher skeleton.
4. Hermes optional backend A/B additions.
5. Codex ALR path hook MVP.
6. Joint benchmark/report schema updates.

## Cross-Agent Questions

Questions should be written into `docs/agent-sync.md` as:

```markdown
Question for <agent>:
- <specific question>

Needed by:
- <date or milestone>

Why it matters:
- <one sentence>
```

The other agent answers in a new entry. This is slower than live chat, but far less fragile.

