# Codex Workstreams

## Goal

Track the major Plib workstreams under a single Codex-owned workflow. The work may be split into branches, but responsibility stays with Codex unless the user explicitly changes that again.

## Workstream A: Clean ALR Runtime

Mission:

- Build the clean-room low-overhead Android Linux runtime.
- Preserve PRoot as a compatibility fallback while ALR matures.
- Keep guest execution claims conservative until real device evidence exists.

Write scope:

- `app/src/main/cpp/alr_runtime/`
- `app/src/main/cpp/alr_runtime_*`
- `app/src/main/cpp/runtime_*`
- ALR-specific host/native tests
- ALR-specific documentation updates

Current focus:

1. Static ELF image mapping preflight.
2. Static AArch64 entry trampoline.
3. Dynamic glibc loader handoff.
4. Syscall/path/proc/fd identity coverage.
5. Performance harness versus PRoot.

Recent completed acceptance:

```text
ALR RUNTIME LAUNCHER AVAILABLE: PASS
ALR RUNTIME CONFIG BUILD: PASS
ALR RUNTIME DIRECT APP-DATA EXEC POLICY: PASS
ALR ELF LOAD PLAN: PASS
ALR TRAMPOLINE AVAILABLE: PASS
ALR TRAMPOLINE CONFIG HANDOFF: PASS
ALR TRAMPOLINE POLICY PREFLIGHT: PASS
```

## Workstream B: Android Device Evidence and Optional Backend Probe

Mission:

- Keep device evidence real and reproducible.
- Compare PRoot, optional external backends, and ALR through black-box command results.
- Preserve clean-room boundaries.

Write scope:

- `docs/evidence/`
- `docs/research/`
- Android smoke/probe code
- APK/device run summaries
- optional backend metadata docs

Current focus:

1. Re-run APK smoke on current main.
2. Capture device model, Android version, ABI, APK hash, stdout/stderr, exit code, and elapsed time.
3. Benchmark PRoot baseline commands.
4. Add optional backend probes only when a concrete artifact is available and licensing/source metadata is recorded.

Acceptance:

```text
PROOT BACKEND EXECUTION: PASS
OPTIONAL LOW-OVERHEAD BACKEND AVAILABLE: PASS/FAIL/SKIP
OPTIONAL LOW-OVERHEAD ROOTFS EXECUTION: PASS/FAIL/SKIP
OPTIONAL LOW-OVERHEAD DPKG LOCAL INSTALL: PASS/FAIL/SKIP
```

## Workstream C: Android GUI/GPU Bridge

Mission:

- Route Linux GUI/GPU output to Android-owned Surface/EGL/GLES/Vulkan paths.
- Keep host rendering evidence separate from guest execution evidence.
- Preserve actual frame/loss/renderer metrics.

Write scope:

- Android Surface/EGL/GLES/Vulkan app code
- guest GUI/GPU bridge clients and shims
- graphics reports and tests
- device evidence for rendered frames

Current focus:

1. Backend report labels.
2. Surface frame loss and renderer evidence.
3. Guest GLES shim stability.
4. Vulkan research path after GLES is stable.

Acceptance:

```text
HOST GPU EGL/GLES EXECUTION: PASS
GUEST GUI GPU SURFACE EXECUTION: PASS/FAIL/SKIP/PENDING_SURFACE_CALLBACK
surface frame lossless=true
surface graphics backend=<backend>
```

## Shared Contract

All workstreams must preserve:

- safe rootfs extraction
- no direct writable app-data executable entrypoint policy
- clean guest environment
- PRoot fallback
- deterministic report strings
- clean-room boundary
- honest PASS/FAIL/SKIP report semantics

## Merge Order

Prefer large complete bundles, not small alternating sync commits:

1. Create a Codex branch for one concrete bundle.
2. Add source, tests, docs, and APK/device proof where relevant.
3. Run the documented verification commands.
4. Open a PR with claim boundaries.
5. Merge after verification, then open the next bundle.
