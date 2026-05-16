# Android Graphics Bridge Specification

## Status

Draft v0.1. This document defines the graphics and GUI bridge target for running Linux GUI/GPU clients inside an Android APK without depending on guest access to Linux DRM/KMS devices.

## Objectives

- Present Linux GUI output through an Android-owned `Surface`.
- Use Android host GPU APIs for hardware acceleration.
- Keep execution backend and graphics bridge independent.
- Support multiple Android GPU vendors through public APIs.
- Build from small, measurable protocol stages rather than claiming full Wayland/X11/OpenGL/Vulkan compatibility early.

## Non-Goals

- Direct guest access to `/dev/dri`, KMS, GBM, or DRM nodes as the default path.
- Full Wayland compositor implementation in early milestones.
- Full X11 server implementation in early milestones.
- Full OpenGL desktop profile compatibility.
- Full Vulkan, DXVK, Wine, or Proton compatibility in early milestones.
- GPU driver reverse engineering.

## Architecture

```text
Linux guest process
  GUI client or GL/Vulkan client
        |
        v
Guest shim or proxy client
  Wayland-shaped, X11-shaped, EGL/GLES, or Vulkan ICD subset
        |
        v
  IPC transport
  loopback TCP, Unix socket, SCM_RIGHTS memfd payload descriptors
        |
        v
Android host bridge service
  validates messages, tracks frames, owns renderer state
        |
        v
Native renderer
  EGL/GLES first, Vulkan later
        |
        v
Android Surface / ANativeWindow
        |
        v
Android vendor GPU driver
```

## Host Responsibilities

The Android host APK owns:

- Activity and Service lifecycle.
- `SurfaceView` or `TextureView`.
- `ANativeWindow` conversion.
- Renderer context creation.
- GPU API selection.
- IPC server lifecycle.
- Permission prompts.
- Device capability reporting.
- Frame pacing and ACK.
- Diagnostics visible in the app UI.

## Guest Responsibilities

The Linux guest side owns:

- Small command clients for smoke tests.
- Protocol-shaped GUI bridge clients.
- `libEGL`/`libGLES` shim for constrained GL workloads.
- Future Vulkan ICD/proxy.
- Environment-driven bridge connection settings.

Guest bridge environment:

```text
ALR_GPU_BRIDGE_HOST=127.0.0.1
ALR_GPU_BRIDGE_PORT=<port>
ALR_GPU_BRIDGE_TRANSPORT=tcp-loopback

ALR_GUI_BRIDGE_HOST=127.0.0.1
ALR_GUI_BRIDGE_PORT=<port>
ALR_GUI_BRIDGE_PROTOCOL=WAYLAND|X11

WAYLAND_DISPLAY=alr-wayland-0
XDG_RUNTIME_DIR=/usr/share/alr-smoke/alr-wayland-runtime
ALR_WAYLAND_DISPLAY_SOCKET=@<android-local-server-socket>
ALR_WAYLAND_PAYLOAD_DIR=/usr/share/alr-smoke/alr-wayland-runtime
DISPLAY=<future display name>
```

## Transport Stages

### Stage 1: Text Command TCP

Purpose:

- Prove guest-to-host communication.
- Prove frame sequencing and ACK.
- Keep debugging easy.

Example message:

```text
FRAME seq=1 protocol=WAYLAND r=0.05 g=0.18 b=0.45 label=guest
```

Example ACK:

```text
ALR_GUI_IPC_ACK protocol=WAYLAND received=4 lossless=true
```

### Stage 2: Binary Framing

Purpose:

- Reduce parsing overhead.
- Add message lengths and versioning.
- Prepare for buffer handles and shared-memory descriptors.

Header draft:

```text
magic: ALRG
version: u16
type: u16
length: u32
seq: u64
payload: bytes
```

### Stage 3: Shared Memory or Buffer Upload

Purpose:

- Avoid sending full frames as text or tiny commands.
- Support dirty rectangles.
- Measure copy count and latency.

Current paths:

- App-private shared-file RGBA fallback for ordinary APK UIDs.
- Unix `SCM_RIGHTS` memfd payload descriptors.
- V92 triple-buffer layout: three frame payload FDs, per-frame `fd_index`,
  byte count, and FNV-1a checksum verification.
- V93 Android `AHardwareBuffer` host-managed triple-buffer probe with CPU
  write/read checksum verification and EGLImage/GL texture import.
- V94 Wayland display AHardwareBuffer backing probe: Android replays the
  verified Wayland display commits into host-owned native buffers and requires
  the visible byte count to match the guest FD payload byte count.

Candidate next path:

- Wire `AHardwareBuffer` allocation/import into the Wayland display bridge as
  the host-owned backing store for selected `wl_buffer` objects.

### Stage 4: Fence and Frame Pacing

Purpose:

- Avoid guest overproduction.
- Track present completion.
- Add frame deadlines and backpressure.

## Renderer Stages

### Renderer v0: Host EGL/GLES Pbuffer Probe

Proves Android EGL context creation and hardware renderer detection without a UI surface.

Report:

```text
host gpu egl available=true
host gpu renderer=<renderer>
host gpu hardware candidate=true
```

### Renderer v1: Android Surface Clear

Proves rendering to an app-owned visible `Surface`.

Report:

```text
surface frames submitted=<n>
surface frames rendered=<n>
surface frames dropped=0
surface gles shim render elapsed us=576046
surface gles shim average frame render us=16001
surface gles shim draw frames rendered=140
surface gles shim draw render elapsed us=1656444
surface gles shim draw average frame render us=11831
surface native gles frames rendered=32
surface native gles render elapsed us=348498
surface native gles average frame render us=10890
surface gles shim vs native average ratio pct=146
surface gpu hardware render=true
```

### Renderer v2: Guest Command Surface

Guest commands drive host clear colors and frame labels.

Report:

```text
guest gpu bridge command execution=PASS
guest gpu ipc bridge execution=PASS
surface frame lossless=true
```

### Renderer v3: GUI Protocol-Shaped Surface

Wayland-shaped and X11-shaped clients send frame commits.

Report:

```text
guest wayland gui bridge execution=PASS
guest x11 gui bridge execution=PASS
surface wayland frames rendered=4
surface x11 frames rendered=4
surface gui total frames rendered=8
```

### Renderer v3.5: Wayland Display Socket With FD Payloads

The Android host exposes an app-private `WAYLAND_DISPLAY` endpoint through
`LocalServerSocket`. The guest connects over Unix domain sockets, sends
Wayland-shaped compositor/shm/surface records, and passes frame payload memfds
through `SCM_RIGHTS`.

Report:

```text
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
wayland display shared payload frames=3/3
wayland display fd payload frames=3/3
wayland display fd payload bytes=691200
fd_received=3
layout=triple-buffer
transport=unix-abstract-wayland-scm-rights
```

### Renderer v3.6: Host-Managed AHardwareBuffer Probe

Android allocates three `AHardwareBuffer` objects with CPU read/write and GPU
sample/color-output usage, writes deterministic RGBA payloads, verifies visible
bytes by checksum, then imports each buffer as an EGLImage-backed GL texture.
This does not replace the v92 memfd bridge yet; it proves the Android-owned
native-buffer half of the next bridge.

Report:

```text
ANDROID HOST AHARDWAREBUFFER EXECUTION: PASS
ahardwarebuffer allocated buffers=3
ahardwarebuffer cpu verified buffers=3
ahardwarebuffer egl imported buffers=3
ahardwarebuffer visible payload bytes=691200
ahardwarebuffer host managed triple buffer=true
ahardwarebuffer egl image import=ok
```

### Renderer v3.7: Wayland Display AHardwareBuffer Backing

The host takes the parsed `ALR_WL_SURFACE_COMMIT` records from the
`WAYLAND_DISPLAY` bridge and uses those frame colors/tags as the source for a
second AHardwareBuffer pass. This keeps the v92 memfd path as guest evidence
while proving the same Wayland display frames can be represented as host-owned
GPU-importable buffers.

Report:

```text
WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS
ahardwarebuffer source=wayland-display-commits
ahardwarebuffer requested buffers=3
ahardwarebuffer cpu verified buffers=3
ahardwarebuffer egl imported buffers=3
ahardwarebuffer visible payload bytes=691200
ahardwarebuffer wayland display backing=true
ahardwarebuffer egl image import=ok
```

### Renderer v4: Guest GLES Shim

Guest `libEGL`/`libGLES` shim forwards a constrained command subset to host GLES.

Required subset:

- `eglGetDisplay`
- `eglInitialize`
- `eglChooseConfig`
- `eglCreateContext`
- `eglMakeCurrent`
- `glClearColor`
- `glClear`
- `eglSwapBuffers`

Report:

```text
guest egl init via shim=PASS
guest gles clear via shim=PASS
guest egl swap via android surface=PASS
guest gles hardware render=PASS
```

### Renderer v5: Vulkan Host Renderer

Host Android Vulkan instance/device/swapchain proof.

Report:

```text
android host vulkan surface execution=PASS
host vulkan device=<name>
host vulkan present mode=<mode>
```

### Renderer v6: Guest Vulkan ICD/Proxy

Guest Vulkan ICD reports device info and forwards clear/present subset.

Report:

```text
guest vulkan icd smoke execution=PASS
guest vulkan clear present execution=PASS
```

## Wayland-Shaped Path

Early Wayland work should not claim full compositor support.

Initial model:

- Provide a guest endpoint that looks like a display target to project-owned clients.
- Encode minimal surface create/commit semantics.
- Translate commits into host renderer frame commands.
- Add ACK and sequence tracking.

Future model:

- Implement enough protocol framing to run a tiny known client.
- Consider using or studying open-source Wayland components if license and scope fit.
- Keep protocol support documented as a subset.

## X11-Shaped Path

Early X11 work should not claim full X server support.

Initial model:

- Project-owned X11-shaped smoke client sends drawable updates.
- Host bridge renders frames and reports ACK.

Future model:

- Evaluate Xvfb/VNC comparison path.
- Evaluate a tiny X proxy for simple windows.
- Translate image or GL-present paths into host renderer frames.

## GLES Shim Model

Guest-side shim responsibilities:

- Export the small EGL/GLES symbols required by smoke apps.
- Track fake display/config/context/surface handles.
- Serialize GL commands to host bridge.
- Return deterministic errors for unsupported symbols.

Host-side responsibilities:

- Maintain real EGL context and surface.
- Map guest context state to host context state.
- Execute supported commands.
- Send swap ACK with frame id.

Unsupported command behavior:

```text
return documented failure
log unsupported symbol once
report GUEST GLES UNSUPPORTED SYMBOL=<name>
```

## Vulkan Proxy Model

Vulkan comes after GLES.

The Vulkan bridge direction should follow the same process boundary as the
current ALR GUI/GPU probes: the glibc guest must not load Android vendor
`libvulkan.so` directly. A May 15, 2026 proroot issue proposed the same split:
guest-side Vulkan bridge ICD plus host-side Android Vulkan backend using the
Android loader/NDK Vulkan stack, with `vulkan-wrapper-android`-style WSI and
quirk handling as a candidate backend reference:
<https://github.com/coderredlab/proroot/issues/7>.

Initial guest ICD responsibilities:

- Register as an ICD inside the rootfs.
- Report one virtual physical device backed by Android host Vulkan.
- Support instance/device creation subset.
- Support one clear/present path.
- Ship an ICD JSON inside a Plib-owned prefix such as
  `/opt/plib-gfx/icd.d/plib_android_vulkan_bridge_icd.aarch64.json`.
- Honor `VK_DRIVER_FILES` and `PLIB_VK_BRIDGE_SOCKET`/transport environment.

Host responsibilities:

- Create Android Vulkan instance.
- Select physical device.
- Create swapchain for Android Surface.
- Execute clear/present command.
- Keep bionic/Android Vulkan loader state inside the APK process.
- Own `ANativeWindow`, `AHardwareBuffer`/gralloc, sync fence, and vendor
  workaround handling.
- Expose enough diagnostics for `vulkaninfo`, `vkcube`, and Zink smoke tests.

Non-goals:

- DXVK.
- Full descriptor/pipeline ecosystem.
- WSI parity beyond the test surface.
- Mixing glibc guest code and bionic vendor Vulkan libraries in one process.

Guest environment draft:

```text
VK_DRIVER_FILES=/opt/plib-gfx/icd.d/plib_android_vulkan_bridge_icd.aarch64.json
PLIB_VK_BRIDGE_SOCKET=/tmp/plib-vk.sock
PLIB_GPU_BACKEND=android-vulkan-wrapper
MESA_LOADER_DRIVER_OVERRIDE=zink
GALLIUM_DRIVER=zink
OCL_ICD_VENDORS=/opt/plib-gfx/OpenCL/vendors
```

Vulkan/OpenGL/OpenCL smoke ladder:

```text
vulkaninfo
vkcube
vkmark
glxinfo -B
glxgears
glmark2
clinfo
clpeak
```

## Synchronization and Backpressure

Every frame-carrying protocol must track:

- `seq`.
- submit time.
- host receive time.
- render start.
- render completion.
- ACK send time.
- guest ACK receive time where available.

Lossless criteria:

```text
received == submitted
rendered == received
seq gaps == 0
duplicates == 0
out_of_order == false
frames dropped == 0
```

## Security and Robustness

IPC parsers must:

- Limit message size.
- Reject malformed numeric fields.
- Reject unknown protocol versions.
- Bound frame queues.
- Close idle clients.
- Avoid blocking UI thread.
- Avoid trusting guest file paths for host resource access.

Renderer must:

- Release EGL/Vulkan resources on lifecycle events.
- Survive Surface recreation.
- Report lost context/device conditions.
- Avoid leaking host Android details to guest clients.

## Device Matrix

Minimum device categories:

- Mali device.
- Adreno device.
- Xclipse or other Vulkan-capable Samsung device where available.
- Emulator or desktop Android target for CI-adjacent smoke.

Per-device evidence:

```text
device model
android version
sdk
gpu renderer
egl version
vulkan availability
surface frame report
guest bridge report
thermal or stability note
```

## Integration With Execution Backends

The graphics bridge must work with:

- PRoot baseline.
- Optional external low-overhead backend.
- ALR runtime.

Rules:

- Bridge protocol must not depend on backend-private behavior.
- Guest clients receive connection data through environment variables.
- Report format includes backend name.
- Graphics tests must run while execution backend A/B tests remain comparable.

## Milestone Acceptance

### GUI/GPU MVP

```text
HOST GPU EGL/GLES EXECUTION: PASS
HOST GPU SURFACE EXECUTION: PASS
GUEST GPU IPC BRIDGE EXECUTION: PASS
GUEST WAYLAND GUI GPU BRIDGE EXECUTION: PASS
GUEST X11 GUI GPU BRIDGE EXECUTION: PASS
GUEST GUI GPU SURFACE EXECUTION: PASS
```

Current device evidence from build `0.4.46-gpu-surface-callback-evidence` records the
Surface callback result separately from the pre-callback execution summary:

```text
HOST GPU SURFACE EXECUTION UPDATE: PASS
GUEST GPU MULTI-FRAME SURFACE EXECUTION UPDATE: PASS
GUEST GUI GPU SURFACE EXECUTION UPDATE: PASS
surface gl renderer=Mali-G615 MC2
surface frames rendered=16
surface frames dropped=0
surface render elapsed us=2930426
surface average frame render us=12740
surface gles shim render elapsed us=548132
surface gles shim average frame render us=16121
surface native gles frames rendered=32
surface native gles render elapsed us=490015
surface native gles average frame render us=15312
surface gles shim vs native average ratio pct=105
surface frame lossless=true
surface gpu hardware render=true
guest wayland/x11 gui gpu surface hardware render=true
surface wayland frames rendered=8
surface x11 frames rendered=8
```

### GLES MVP

```text
GUEST EGL INIT VIA SHIM: PASS
GUEST GLES CLEAR VIA SHIM: PASS
GUEST EGL SWAP VIA ANDROID SURFACE: PASS
GUEST GLES HARDWARE RENDER: PASS
```

Current device evidence from build `0.4.53-gles-procaddr-demo`:

```text
GUEST GLES SHIM SMOKE EXECUTION: PASS
ALR GUEST GLES SHIM SMOKE EXECUTION: PASS
GUEST EGL/GLES ABI LIB EXECUTION: PASS
ALR GUEST EGL/GLES ABI LIB EXECUTION: PASS
GUEST GLES DEMO GEARS EXECUTION: PASS
ALR GUEST GLES DEMO GEARS EXECUTION: PASS
GUEST GLES PROCADDR DEMO EXECUTION: PASS
ALR GUEST GLES PROCADDR DEMO EXECUTION: PASS
GUEST EGL INIT VIA SHIM EXECUTION: PASS
GUEST EGL CONTEXT VIA SHIM EXECUTION: PASS
GUEST GLES CLEAR VIA SHIM EXECUTION: PASS
GUEST EGL SWAP COMMAND VIA SHIM EXECUTION: PASS
GUEST GLES SHIM FRAME WORKLOAD EXECUTION: PASS
ALR GUEST GLES SHIM FRAME WORKLOAD EXECUTION: PASS
GUEST GLES DRAW VIA SHIM EXECUTION: PASS
ALR GUEST GLES DRAW VIA SHIM EXECUTION: PASS
GUEST EGL INIT VIA SHIM UPDATE: PASS
GUEST EGL CONTEXT VIA SHIM UPDATE: PASS
GUEST GLES CLEAR VIA SHIM UPDATE: PASS
GUEST GLES DRAW VIA SHIM UPDATE: PASS
GUEST EGL SWAP VIA ANDROID SURFACE UPDATE: PASS
GUEST GLES HARDWARE RENDER UPDATE: PASS
ALR_GLES_API_STEP eglGetDisplay ok
ALR_GLES_API_STEP eglInitialize ok
ALR_GLES_API_STEP eglChooseConfig ok
ALR_GLES_API_STEP eglCreateContext ok
ALR_GLES_API_STEP eglMakeCurrent ok
ALR_GLES_API_STEP glViewport ok
ALR_GLES_API_STEP glClearColor ok
ALR_GLES_API_STEP glClear ok
ALR_GLES_API_STEP glUseProgram ok
ALR_GLES_API_STEP glEnableVertexAttribArray ok
ALR_GLES_API_STEP glVertexAttribPointer ok
ALR_GLES_API_STEP glDrawArrays ok
ALR_GLES_API_STEP eglSwapBuffers ok
ALR_GLES_ABI_LIBS visible libEGL.so libGLESv2.so
ALR_GLES_ABI_STEP eglGetProcAddress ok
ALR_GLES_ABI_STEP eglSwapBuffersDraw ok
ALR_GLES_DEMO_KIND es2gears-like-triangle-strip-subset
ALR_GLES_DEMO_WORKLOAD requested=60 submitted=60
ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset
ALR_GLES_PROC_DEMO_WORKLOAD requested=45 submitted=45
surface frames rendered=230
surface frames dropped=0
surface gles shim render elapsed us=576046
surface gles shim average frame render us=16001
surface gles shim draw frames rendered=140
surface gles shim draw render elapsed us=1656444
surface gles shim draw average frame render us=11831
surface native gles frames rendered=32
surface native gles render elapsed us=348498
surface native gles average frame render us=10890
surface gles shim vs native average ratio pct=146
surface gpu hardware render=true
surface gles shim frames rendered=36
guest egl swap via android surface=true
guest gles hardware render=true
guest gles draw via android surface=true
```

### Vulkan MVP

```text
ANDROID HOST VULKAN SURFACE EXECUTION: PASS
GUEST VULKAN SURFACE CLEAR REQUEST EXECUTION: PASS
GUEST VULKAN PROXY SURFACE CLEAR EXECUTION: PASS
surface vulkan clear request source=guest-request
surface vulkan clear request tag=guest-vulkan-proxy-clear-0001
surface vulkan device=Mali-G615 MC2
surface vulkan present mode=mailbox
surface vulkan swapchain image count=7
surface vulkan clear command=ok color=0.33,0.22,0.88,1
surface vulkan present=ok
surface vulkan hardware render=true
ALR_VK_PROXY_STEP vkEnumerateInstanceVersion ok api=1.3.247
ALR_VK_PROXY_SURFACE_CLEAR_REQUEST_ACCEPTED ok
ALR_VK_PROXY_DONE ok
GUEST VULKAN ICD SMOKE EXECUTION: PASS
GUEST VULKAN CLEAR PRESENT EXECUTION: PASS
```

Build `0.4.81-guest-vulkan-proxy-smoke` packages the first guest
`libvulkan.so.1` proxy smoke. The guest binary loads the Vulkan ABI name from
the rootfs and routes a bounded clear request to the Android host bridge. The
host renders and presents through the native Android Vulkan swapchain, so this
is still a proxy/ICD seed rather than full Vulkan command forwarding.

Build `0.4.82-vulkan-binary-proxy-bridge` changes the proxy transport from
line-oriented clear commands to a bounded binary frame:

```text
ALVB request: magic, version, opcode, payload bytes, flags, rgba millesimals, tag length, source length, tag, source
ALVR response: magic, version, status, payload bytes, record count, bounded text records
ALR_VK_BINARY_BRIDGE_ACK status=PASS protocol=alr-vk-bin-v1
ALR_VK_PROXY_BINARY_BRIDGE ok
surface vulkan clear request tag=guest-vulkan-proxy-clear-0001
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST ... protocol=binary-frame-v1
surface vulkan present=ok
surface vulkan hardware render=true
```

Build `0.4.83-vulkan-icd-manifest-smoke` adds a guest-visible Vulkan ICD
manifest and proves that the installed glibc smoke can discover the proxy via
manifest metadata before issuing the same binary-framed Surface clear:

```text
VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json
VK_DRIVER_FILES=/usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json
ALR_VK_ICD_LIBRARY_PATH libvulkan.so.1
ALR_VK_ICD_BINARY_BRIDGE ok
GUEST VULKAN ICD MANIFEST SURFACE CLEAR EXECUTION: PASS
```

Build `0.4.84-vulkan-loader-info-smoke` adds the next guest-facing loader
shape. The installed package contains `/usr/local/bin/alr-package-vulkan-loader-info`;
the direct rootfs also contains `/usr/bin/alr-vulkan-loader-info`. The probe
selects the ICD manifest from `VK_DRIVER_FILES`/`VK_ICD_FILENAMES`, loads
`libvulkan.so.1`, checks `vkEnumerateInstanceVersion`, and sends the clear
request through the existing binary proxy bridge:

```text
ALR_VK_LOADER_SELECTED_MANIFEST /usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json
ALR_VK_LOADER_ICD_LIBRARY_PATH libvulkan.so.1
ALR_VK_LOADER_VULKANINFO_INSTANCE_VERSION ok
ALR_VK_LOADER_VULKANINFO_DEVICE_RECORD ok
ALR_VK_LOADER_BINARY_BRIDGE ok
ALR_VK_LOADER_DONE ok
GUEST VULKAN LOADER INFO SURFACE CLEAR EXECUTION: PASS
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST ... protocol=binary-frame-v1
surface vulkan device=Mali-G615 MC2
surface vulkan present=ok
surface vulkan hardware render=true
```

Build `0.4.85-vulkan-unix-loader-bridge` moves the active loader-info Surface
clear path off loopback TCP and onto an abstract Unix-domain socket. The same
`ALVB`/`ALVR` binary frame protocol is preserved, but the guest proxy now
honors `ALR_VK_BRIDGE_SOCKET=@...` and Android accepts it through
`LocalServerSocket`:

```text
GUEST VULKAN UNIX SOCKET LOADER INFO SURFACE CLEAR EXECUTION: PASS
VULKAN BRIDGE UNIX TRANSPORT EXECUTION: PASS
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST ... protocol=binary-frame-v1 transport=unix-abstract
surface vulkan device=Mali-G615 MC2
surface vulkan present=ok
surface vulkan hardware render=true
vulkan bridge transport tcp loader elapsed ms=303
vulkan bridge transport unix loader elapsed ms=202
vulkan bridge transport unix vs tcp ratio pct=66
vulkan bridge transport unix faster than tcp=true
```

Build `0.4.86-gles-unix-bridge` gives the GLES shim the same transport shape:
`ALR_GPU_BRIDGE_SOCKET=@...` selects an abstract Unix-domain socket, while the
existing TCP host/port path remains the fallback. Android now executes both TCP
and Unix installed-package GLES ACK smokes and feeds the Unix command stream
into the Surface renderer:

```text
GLES BRIDGE UNIX TRANSPORT EXECUTION: PASS
gles bridge transport tcp loader elapsed ms=7555
gles bridge transport unix loader elapsed ms=12768
gles bridge transport unix vs tcp ratio pct=169
gles bridge transport unix faster than tcp=false
surface gles shim vs native average ratio pct=101
```

This confirms transport compatibility, not a final optimization result. The
per-frame ACK workload is still too chatty, so the next graphics bridge step is
batching commands across the Unix control path rather than tuning socket setup.

Build `0.4.87-gles-unix-batch-bridge` adds that GLES batch path. The guest shim
keeps the same GLES command text shape, but `ALR_GPU_BRIDGE_BATCH=1` delays
delivery until close and sends one bounded batch frame over the abstract
Unix-domain socket. Android parses the batch as the same command stream and
replies once with `ALR_GPU_BATCH_ACK`:

```text
GLES BRIDGE UNIX TRANSPORT EXECUTION: PASS
GLES BRIDGE UNIX BATCH TRANSPORT EXECUTION: PASS
gles bridge transport tcp loader elapsed ms=6849
gles bridge transport unix loader elapsed ms=15708
gles bridge transport unix batch loader elapsed ms=908
gles bridge transport unix vs tcp ratio pct=229
gles bridge transport unix faster than tcp=false
gles bridge transport unix batch vs tcp ratio pct=13
gles bridge transport unix batch vs unix ack ratio pct=5
gles bridge transport unix batch faster than unix ack=true
surface gles shim vs native average ratio pct=100
surface vulkan present=ok
surface vulkan hardware render=true
```

This is still a smoke-level bridge, not a Mesa-grade GL driver, but it proves the
right direction for Plib's graphics path: keep Android-native rendering on the
host side and aggressively reduce guest/host synchronization points before
adding larger Linux GUI stacks.

Build `0.4.88-gui-unix-bridge` applies the Unix control path to GUI protocol
smokes as well. The old opaque Wayland/X11 client binaries were replaced with
`rootfs/guest-src/gui/alr_gui_gpu_client.c`, a source-built static client that
can use either TCP or `ALR_GUI_BRIDGE_SOCKET=@...`.

```text
GUI BRIDGE UNIX TRANSPORT EXECUTION: PASS
WAYLAND GUI UNIX TRANSPORT EXECUTION: PASS
X11 GUI UNIX TRANSPORT EXECUTION: PASS
gui bridge transport wayland tcp loader elapsed ms=102
gui bridge transport wayland unix loader elapsed ms=101
gui bridge transport wayland unix vs tcp ratio pct=99
gui bridge transport x11 tcp loader elapsed ms=103
gui bridge transport x11 unix loader elapsed ms=102
gui bridge transport x11 unix vs tcp ratio pct=99
gui bridge wayland unix frames=4/4
gui bridge x11 unix frames=4/4
surface wayland frames rendered=16
surface x11 frames rendered=16
surface vulkan present=ok
surface vulkan hardware render=true
```

This keeps the GUI path aligned with the GLES/Vulkan direction: TCP remains only
as a fallback and measurement baseline, while Android-native Surface rendering
is driven through app-private Unix-domain control sockets.

Build `0.4.89-wayland-display-bridge` adds the first minimal
`WAYLAND_DISPLAY`-shaped endpoint. The guest side is the new source-built static
client `rootfs/guest-src/gui/alr_wayland_display_client.c`. The host side
creates an Android `LocalServerSocket`, passes
`ALR_WAYLAND_DISPLAY_SOCKET=@...`, `WAYLAND_DISPLAY=alr-wayland-0`, and
`XDG_RUNTIME_DIR=/tmp/alr-wayland-runtime`, and then ACKs the commit stream with
`ALR_WL_DISPLAY_ACK`.

```text
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
alr installed package wayland display ipc received frames=3/3
alr installed package wayland display ipc ack raw=ALR_WL_DISPLAY_ACK display=alr-wayland-0 commits=3 expected=3 lossless=true transport=unix-abstract-wayland
surface wayland frames rendered=19
surface x11 frames rendered=16
surface vulkan present=ok
surface vulkan hardware render=true
```

Current V89 protocol records:

```text
ALR_WL_CONNECT display=alr-wayland-0 runtime=/tmp/alr-wayland-runtime transport=unix-abstract-wayland
ALR_WL_REGISTRY global=wl_compositor version=4 id=1
ALR_WL_REGISTRY global=wl_shm version=1 id=2
ALR_WL_BIND name=wl_compositor id=1 version=4
ALR_WL_SURFACE_CREATE id=10 compositor=1
ALR_WL_BUFFER_CREATE id=20 width=320 height=180 format=argb8888
ALR_WL_SURFACE_COMMIT surface=10 buffer=20 seq=1 ...
```

This is still a clean-room bridge probe, not a drop-in Wayland compositor. The
important design decision is that Android remains the native renderer owner,
while the guest sees a progressively more Linux-like display endpoint.

Build `0.4.90-wayland-shared-payload` adds verified buffer payload staging to
that endpoint. The guest writes a bounded RGBA payload under an app-private
rootfs staging directory, sends `ALR_WL_SHM_POOL_CREATE`, sends
`ALR_WL_BUFFER_ATTACH` records with bytes and checksum, then commits the same
buffer three times. Android resolves the staged file under the verified rootfs,
rejects path escapes, checks length and FNV-1a checksum, and only then emits
Surface frames.

```text
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
alr installed package wayland display ipc received frames=3/3
wayland display shared payload frames=3/3
wayland display shared payload bytes=691200
alr installed package wayland display ipc ack raw=ALR_WL_DISPLAY_ACK display=alr-wayland-0 commits=3 expected=3 lossless=true payloads=3 payload_bytes=691200 payload_verified=true transport=unix-abstract-wayland-shared-file
surface wayland frames rendered=19
surface vulkan present=ok
surface vulkan hardware render=true
```

Current V90 payload records:

```text
ALR_WL_SHM_POOL_CREATE id=30 path=/usr/share/alr-smoke/alr-wayland-runtime/alr-wl-buffer-20.rgba bytes=230400 checksum=...
ALR_WL_BUFFER_CREATE id=20 width=320 height=180 stride=1280 format=argb8888 payload=shared-file
ALR_WL_BUFFER_ATTACH surface=10 buffer=20 seq=1 path=... bytes=230400 checksum=... transport=shared-file
ALR_WL_SURFACE_COMMIT surface=10 buffer=20 seq=1 ... payload=... bytes=230400 checksum=... transport=shared-file
```

Build `0.4.95-wayland-ahb-dirty-state` extends that same clean-room display
path through the host-owned buffer decision. The current client still sends the
v92 triple memfd payloads, but those payloads are now the verified fallback
baseline; the active attach state declares `backing=host-ahardwarebuffer`, a
buffer slot, and a dirty rectangle for every commit. Android verifies the
`ALR_WL_DAMAGE` and `ALR_WL_BUFFER_ATTACH` state before acknowledging the stream
with `ahb_state_ready=true`, then passes the parsed dirty rects to native code
so the AHardwareBuffer probe writes only the changed rectangles.

Current V95 backing records:

```text
ALR_WL_AHB_BACKING_ADVERTISE version=1 allocator=android-host format=R8G8B8A8_UNORM usage=cpu-read-write+gpu-sampled+gpu-color-output max_buffers=3 dirty_rect=true
ALR_WL_DAMAGE surface=10 buffer=20 seq=1 x=0 y=0 width=160 height=90 bytes=57600 type=buffer-damage backing=host-ahardwarebuffer update=partial
ALR_WL_BUFFER_ATTACH surface=10 buffer=20 seq=1 ... layout=triple-buffer backing=host-ahardwarebuffer buffer_slot=0 dirty_x=0 dirty_y=0 dirty_w=160 dirty_h=90 dirty_bytes=57600 update=partial
ALR_WL_SURFACE_COMMIT surface=10 buffer=20 seq=1 ... backing=host-ahardwarebuffer buffer_slot=0 dirty_x=0 dirty_y=0 dirty_w=160 dirty_h=90 dirty_bytes=57600 update=partial
ALR_WL_DISPLAY_ACK ... backing=host-ahardwarebuffer ahb_backed=3 dirty_rects=3 dirty_bytes=172800 partial_updates=3 ahb_state_ready=true zero_copy_candidate=true
```

Expected V95 evidence:

```text
WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS
wayland display ahardwarebuffer backed frames=3/3
wayland display dirty rect frames=3/3
wayland display dirty rect bytes=172800
wayland display partial upload ratio pct=25
ahardwarebuffer backing mode=host-ahardwarebuffer
ahardwarebuffer wayland state machine backing=true
ahardwarebuffer dirty rect frames=3/3
ahardwarebuffer dirty rect bytes=172800
ahardwarebuffer partial upload ratio pct=25
ahardwarebuffer visible payload bytes=172800
```

## Open Questions

- Can the V90 file-backed payload bridge move to ashmem/memfd FD passing without relying on privileged APIs?
- Can `AHardwareBuffer` provide a practical cross-boundary buffer path for guest-generated or host-managed Wayland buffers?
- How much Wayland protocol is worth implementing before using an existing compositor/proxy component?
- Should X11 support begin with image transport, GLX proxy research, or Xvfb/VNC comparison?
- Which Vulkan subset is small enough to be credible but useful enough to guide the ICD design?
