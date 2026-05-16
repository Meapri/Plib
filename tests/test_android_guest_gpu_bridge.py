from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]
PAYLOAD = ROOT / "app/src/main/assets/rootfs/payloads/tiny-rootfs.tar"
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"
RUNNER = ROOT / "app/src/main/java/dev/chanwoo/androlinux/NativeCommandRunner.kt"
CPP = ROOT / "app/src/main/cpp/runtime_report.cpp"


def test_rootfs_contains_guest_gpu_ipc_client_and_gles_shim():
    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        for name in [
            "./usr/bin/alr-gpu-client",
            "./usr/bin/alr-gles-shim-smoke",
            "./usr/bin/alr-gles-abi-smoke",
            "./usr/bin/alr-gles-demo-gears",
            "./usr/bin/alr-gles-procaddr-demo",
            "./usr/bin/alr-vulkan-discovery-client",
            "./usr/bin/alr-vulkan-proxy-smoke",
            "./usr/bin/alr-vulkan-icd-manifest-smoke",
            "./usr/bin/alr-vulkan-loader-info",
            "./usr/lib/androlinux/libalr_gles_shim.so",
            "./usr/lib/androlinux/libEGL.so",
            "./usr/lib/androlinux/libGLESv2.so",
            "./usr/lib/androlinux/libvulkan.so.1",
            "./usr/bin/alr-wayland-gpu-client",
            "./usr/bin/alr-x11-gpu-client",
            "./usr/bin/alr-wayland-display-client",
            "./usr/bin/alr-simple-gui-demo",
        ]:
            assert name in names
            assert archive.getmember(name).mode & 0o111
        assert "./usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json" in names
        icd_manifest = archive.extractfile("./usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json").read().decode()
        assert '"library_path": "libvulkan.so.1"' in icd_manifest
        assert '"api_version": "1.3.247"' in icd_manifest
        payload = archive.extractfile("./usr/share/androlinux/gpu-bridge.txt").read().decode()
        assert "tcp-loopback" in payload
        assert "multi-frame" in payload
        assert "gles-shim-smoke" in payload
        assert "gles-abi-lib-names" in payload
        assert "gles-demo-gears" in payload
        assert "gles-procaddr-demo" in payload
        assert "libEGL.so" in payload
        assert "libGLESv2.so" in payload
        assert "gles-shim-api-subset" in payload
        assert "gles-frame-workload" in payload
        assert "gles-triangle-draw-workload" in payload
        assert "surface-present" in payload
        assert "timing" in payload
        gui_payload = archive.extractfile("./usr/share/androlinux/gui-gpu-bridge.txt").read().decode()
        assert "Wayland/X11" in gui_payload
        assert "ALR_GUI_FRAME" in gui_payload
        assert "unix-abstract-gui" in gui_payload
        assert "WAYLAND_DISPLAY" in gui_payload
        assert "ALR_WL_SURFACE_COMMIT" in gui_payload
        assert "ALR_WL_BUFFER_ATTACH" in gui_payload
        assert "shared-file" in gui_payload
        assert "unix-abstract-wayland" in gui_payload
        wayland_display = archive.extractfile("./usr/bin/alr-wayland-display-client").read()
        assert wayland_display.startswith(b"\x7fELF")
        assert b"ALR_WAYLAND_DISPLAY_SOCKET" in wayland_display
        assert b"WAYLAND_DISPLAY" in wayland_display
        assert b"ALR_WL_SURFACE_COMMIT" in wayland_display
        assert b"ALR_WL_SHM_POOL_CREATE" in wayland_display
        assert b"ALR_WL_SHM_POOL_FD" in wayland_display
        assert b"ALR_WL_BUFFER_ATTACH" in wayland_display
        assert b"ALR_WAYLAND_PAYLOAD_DIR" in wayland_display
        assert b"transport=shared-file" in wayland_display
        assert b"scm-rights-memfd" in wayland_display
        assert b"layout=triple-buffer" in wayland_display
        assert b"ALR_WL_BINARY_STREAM" in wayland_display
        assert b"wayland-binary-v1" in wayland_display
        simple_demo = archive.extractfile("./usr/bin/alr-simple-gui-demo").read()
        assert simple_demo.startswith(b"\x7fELF")
        assert b"/lib/ld-linux-aarch64.so.1" in simple_demo
        assert b"ALR_SIMPLE_GUI_DEMO_BEGIN" in simple_demo
        assert b"ALR_SIMPLE_GUI_DEMO ok" in simple_demo
        assert b"simple-gui-demo" in simple_demo


def test_android_runs_loopback_ipc_bridge_and_reports_loss_metrics():
    runner = RUNNER.read_text()
    assert "runProotRootfsGuestGpuClientIpc" in runner
    assert "runAlrRuntimeTrampolineGuestGpuClientIpc" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageGpuSmoke" in runner
    assert "runAlrRuntimeTrampolineGuestGuiClientIpc" in runner
    assert "runAlrRuntimeTrampolineGuestGuiClientIpcUnix" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageGuiClientIpcUnix" in runner
    assert "runAlrRuntimeTrampolineGuestGlesShimSmoke" in runner
    assert "pathRewrite = true" in runner
    assert "pathRewriteLimit = 1" in runner
    assert "extraGuestEnvironment" in runner
    assert "ALR_GPU_BRIDGE_PORT" in runner
    text = MAIN.read_text() + CPP.read_text()
    assert "ServerSocket(0, 1, InetAddress.getByName(host))" in text
    assert "GUEST GPU IPC BRIDGE EXECUTION" in text
    assert "GUEST GLES SHIM SMOKE EXECUTION" in text
    assert "GUEST EGL/GLES ABI LIB EXECUTION" in text
    assert "ALR GUEST EGL/GLES ABI LIB EXECUTION" in text
    assert "GUEST GLES DEMO GEARS EXECUTION" in text
    assert "ALR GUEST GLES DEMO GEARS EXECUTION" in text
    assert "GUEST GLES PROCADDR DEMO EXECUTION" in text
    assert "ALR GUEST GLES PROCADDR DEMO EXECUTION" in text
    assert "GUEST GLES SHIM FRAME WORKLOAD EXECUTION:" in text
    assert "ALR GUEST GLES SHIM FRAME WORKLOAD EXECUTION:" in text
    assert "GUEST GLES DRAW VIA SHIM EXECUTION:" in text
    assert "ALR GUEST GLES DRAW VIA SHIM EXECUTION:" in text
    assert "GUEST GPU MULTI-FRAME SURFACE EXECUTION" in text
    assert "guest gpu ipc received frames" in text
    assert "guest gpu ipc lossless" in text
    assert "Linux guest Wayland/X11 GUI GPU surface renderer" in text
    assert "GUEST WAYLAND GUI GPU BRIDGE EXECUTION" in text
    assert "GUEST X11 GUI GPU BRIDGE EXECUTION" in text
    assert "GUEST GUI GPU SURFACE EXECUTION" in text
    assert "runGuestGuiBridge" in text
    assert "runGuestGuiBridgeUnix" in text
    assert "ALR_GUI_IPC_ACK" in text
    assert "ALR_GUI_UNIX_BRIDGE_SOCKET" in text
    assert "guest wayland gui ipc seq gaps" in text
    assert "guest x11 gui ipc seq gaps" in text
    assert "ALR GUEST GPU IPC BRIDGE EXECUTION" in text
    assert "ALR INSTALLED PACKAGE GPU IPC EXECUTION" in text
    assert "ALR INSTALLED PACKAGE GLES DEMO EXECUTION" in text
    assert "ALR INSTALLED PACKAGE GLES IPC EXECUTION" in text
    assert "ALR INSTALLED PACKAGE GLES PROCADDR EXECUTION" in text
    assert "runInstalledPackageGpuIpcBridge" in text
    assert "runInstalledPackageGlesIpcBridge" in text
    assert "runAlrRuntimeTrampolineInstalledPackageGlesDemo" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageGlesProcaddrDemo" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageGlesDemoIpc" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageGlesDemoIpcUnix" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageGlesDemoIpcUnixBatch" in runner
    assert "alr installed package gles demo command parsed count" in text
    assert "alr installed package gles demo draw command count" in text
    assert "alr installed package gles procaddr command parsed count" in text
    assert "alr installed package gles procaddr draw command count" in text
    assert "installed package compatibility table=" in text
    assert "gles-procaddr:" in text
    assert "gles-unix-ack:${if (alrInstalledPackageGlesUnixIpcBridgePassed)" in text
    assert "gles-unix-batch:${if (alrInstalledPackageGlesUnixBatchIpcBridgePassed)" in text
    assert "wayland:${if (alrInstalledPackageWaylandGuiBridgePassed)" in text
    assert "x11:${if (alrInstalledPackageX11GuiBridgePassed)" in text
    assert "wayland-unix:${if (alrInstalledPackageWaylandGuiUnixBridgePassed)" in text
    assert "x11-unix:${if (alrInstalledPackageX11GuiUnixBridgePassed)" in text
    assert "vulkan-discovery:${if (alrInstalledPackageVulkanDiscoveryPassed)" in text
    assert "vulkan-icd:${if (alrInstalledPackageVulkanIcdPassed)" in text
    assert "vulkan-loader:${if (alrInstalledPackageVulkanLoaderInfoPassed)" in text
    assert "vulkan-loader-unix:${if (alrInstalledPackageVulkanUnixLoaderInfoPassed)" in text
    assert "alr installed package gles ipc draw frames" in text
    assert "alr installed package gles ipc ack frames" in text
    assert "alr installed package gles ipc ack raw" in text
    assert "alr installed package gles ipc lossless" in text
    assert "alr installed package gles unix ipc ack raw" in text
    assert "alr installed package gles unix ipc lossless" in text
    assert "alr installed package gles unix batch ipc ack raw" in text
    assert "alr installed package gles unix batch ipc lossless" in text
    assert "GLES BRIDGE UNIX TRANSPORT EXECUTION:" in text
    assert "GLES BRIDGE UNIX BATCH TRANSPORT EXECUTION:" in text
    assert "gles bridge transport unix vs tcp ratio pct=" in text
    assert "gles bridge transport unix batch vs unix ack ratio pct=" in text
    assert "alr installed package gpu ipc lossless" in text
    assert "alr installed package gpu ipc execve loader rewrites" in text
    assert "alr installed package gpu ipc client" in text
    assert "ALR GUEST GLES SHIM SMOKE EXECUTION" in text
    assert "ALR GUEST WAYLAND GUI GPU BRIDGE EXECUTION" in text
    assert "ALR GUEST X11 GUI GPU BRIDGE EXECUTION" in text
    assert "ALR INSTALLED PACKAGE WAYLAND GUI GPU BRIDGE EXECUTION" in text
    assert "ALR INSTALLED PACKAGE X11 GUI GPU BRIDGE EXECUTION" in text
    assert "ALR INSTALLED PACKAGE WAYLAND GUI GPU UNIX BRIDGE EXECUTION" in text
    assert "ALR INSTALLED PACKAGE X11 GUI GPU UNIX BRIDGE EXECUTION" in text
    assert "GUI BRIDGE UNIX TRANSPORT EXECUTION:" in text
    assert "HOST VULKAN DISCOVERY EXECUTION" in text
    assert "ALR INSTALLED PACKAGE VULKAN DISCOVERY EXECUTION" in text
    assert "runAlrRuntimeTrampolineInstalledPackageGuiClientIpc" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageVulkanDiscovery" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageWaylandDisplayClientUnix" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageSimpleGuiDemoUnix" in runner
    assert "ALR_WAYLAND_DISPLAY_SOCKET" in runner
    assert "WAYLAND_DISPLAY" in runner
    assert "XDG_RUNTIME_DIR" in runner
    assert "alr installed package wayland gui ipc received frames" in text
    assert "alr installed package x11 gui ipc received frames" in text
    assert "alr installed package wayland gui unix ipc ack raw" in text
    assert "alr installed package x11 gui unix ipc ack raw" in text
    assert "WAYLAND DISPLAY SOCKET AVAILABLE:" in text
    assert "WAYLAND DISPLAY COMMIT SURFACE EXECUTION:" in text
    assert "ALR_DEVICE_EVIDENCE" in text
    assert "ALR_SURFACE_EVIDENCE" in text
    assert "runInstalledPackageWaylandDisplayBridge" in text
    assert "ALR_WL_SURFACE_COMMIT" in text
    assert "ALR_WL_DISPLAY_ACK" in text
    assert "alr installed package wayland display ipc ack raw" in text
    assert "wayland-display:${if (alrInstalledPackageWaylandDisplayBridgePassed)" in text
    assert "simple-gui-demo:${if (alrInstalledPackageSimpleGuiDemoPassed)" in text
    assert "SIMPLE GUI DEMO EXECUTION:" in text
    assert "SIMPLE GUI DEMO GLIBC DYNAMIC EXECUTION:" in text
    assert "simple gui demo glibc dynamic=" in text
    assert "simple gui demo android surface candidate=" in text
    assert "rootfs installed alr simple gui demo exists=" in text
    assert "rootfs /usr/bin/alr-simple-gui-demo exists=" in text
    assert "wayland display surface commits=" in text
    assert "wayland display shared payload frames=" in text
    assert "wayland display shared payload bytes=" in text
    assert "wayland display fd payload frames=" in text
    assert "wayland display fd payload bytes=" in text
    assert "wayland display wire messages=" in text
    assert "wayland display wire subset ready=" in text
    assert "wayland display wire surface lifecycle=" in text
    assert "wayland display binary messages=" in text
    assert "wayland display binary bytes=" in text
    assert "wayland display binary subset ready=" in text
    assert "ALR_WL_BINARY_STREAM" in text
    assert "ALR_WL_BINARY_DECODE" in text
    assert "ALR_WL_BINARY_MESSAGE" in text
    assert "binary_messages=${binaryMessages.size}" in text
    assert "binary_header_ready=$binaryHeaderReady" in text
    assert "binary_subset_ready=$binaryDecodeReady" in text
    assert "wayland display ahardwarebuffer backed frames=" in text
    assert "wayland display dirty rect frames=" in text
    assert "wayland display dirty rect bytes=" in text
    assert "wayland display partial upload ratio pct=" in text
    assert "payload_verified=true" in text
    assert "fd_payload_verified=true" in text
    assert "fd_received=${fdPayloads.size}" in text
    assert "ahb_state_ready=true" in text
    assert "zero_copy_candidate=true" in text
    assert "layout=triple-buffer" in text
    assert "ANDROID HOST AHARDWAREBUFFER EXECUTION:" in text
    assert "WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION:" in text
    assert "WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION:" in text
    assert "ahardwarebuffer host managed triple buffer=" in text
    assert "ahardwarebuffer wayland display backing=" in text
    assert "ahardwarebuffer wayland state machine backing=" in text
    assert "ahardwarebuffer dirty rect bytes=" in text
    assert "ahardwarebuffer partial upload ratio pct=" in text
    assert "ahardwarebuffer cpu write dirty rect locks=" in text
    assert "ahardwarebuffer sync fence accounting=ok" in text
    assert "ahardwarebuffer egl image import=" in text
    assert "nativeRenderWaylandHardwareBufferSurface" in text
    assert "wayland ahardwarebuffer surface compositor=egl-image-texture-to-android-surface" in text
    assert "wayland ahardwarebuffer surface buffer pool mode=slot-reuse" in text
    assert "wayland ahardwarebuffer surface buffer pool reuses=" in text
    assert "wayland ahardwarebuffer surface fence wait candidates=" in text
    assert "wayland ahardwarebuffer surface fence pacing mode=reuse-slot-fence-handoff" in text
    assert "wayland ahardwarebuffer surface hardware render=" in text
    assert "wayland ahardwarebuffer surface presented frames=" in text
    assert "wayland ahardwarebuffer surface sync fence accounting=ok" in text
    assert "transport=unix-abstract-wayland-scm-rights" in text
    assert "gui bridge transport wayland unix vs tcp ratio pct=" in text
    assert "gui bridge transport x11 unix vs tcp ratio pct=" in text
    assert "alr installed package vulkan discovery ack" in text
    assert "alr installed package vulkan discovery device record" in text
    assert "alr installed package vulkan discovery feature record" in text
    assert "alr installed package vulkan surface clear request" in text
    assert "alr installed package vulkan surface clear accepted" in text
    assert "alr installed package vulkan proxy surface clear request" in text
    assert "alr installed package vulkan proxy surface clear accepted" in text
    assert "alr installed package vulkan proxy stdout" in text


def test_v104_adb_verifier_checks_gimp3_wayland_evidence():
    script = (ROOT / "scripts/verify-android-v104-gimp3-wayland.sh").read_text()
    text = MAIN.read_text()
    runner = RUNNER.read_text()
    display_source = (ROOT / "rootfs/guest-src/gui/alr_wayland_display_client.c").read_text()
    assert "--es ALR_VERIFY_MODE gimp3-wayland" in script
    assert "WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: ${if (passed) \"PASS\" else \"FAIL\"}" in text
    assert "wayland display continuous stream ready=" in text
    assert "wayland display wire messages=" in text
    assert "wayland display binary bytes=" in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface compositor=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface replay passes=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface continuous guest commits=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface simple gui demo candidate=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface total frame submissions=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface buffer pool reuses=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface hardware render=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface dirty rect bytes=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface fence pacing mode=")' in text
    assert 'lineStartingWith("wayland ahardwarebuffer surface sync fence accounting=")' in text
    assert "surface vulkan hardware render=" in text
    assert "SIMPLE GUI DEMO EXECUTION:" in text
    assert "SIMPLE GUI DEMO GLIBC DYNAMIC EXECUTION:" in text
    assert "simple gui demo glibc dynamic=" in text
    assert "simple gui demo android surface candidate=" in text
    assert "GIMP DEMO PROFILE EXECUTION: PASS" in script
    assert "GIMP CLI HELP PROBE EXECUTION: PASS" in script
    assert "GIMP CONSOLE VERSION PROBE EXECUTION: PASS" in script
    assert "GIMP CORE QUIT PROBE EXECUTION:" in script
    assert "GIMP CORE QUIT BLOCKER: CORE_QUIT_TIMEOUT" in script
    assert "GIMP CONSOLE BATCH QUIT PROBE EXECUTION:" in script
    assert "GIMP CONSOLE BATCH QUIT BLOCKER: CORE_BATCH_TIMEOUT" in script
    assert "full gimp probe mode=fast-scout" in script
    assert "GIMP GTK WAYLAND PROBE EXECUTION: PASS" in script
    assert "GIMP GTK WINDOW WAYLAND PROBE EXECUTION: PASS" in script
    assert "GIMP GUI QUIT WAYLAND PROBE EXECUTION: PASS" in script
    assert "GIMP GUI WAYLAND PROBE EXECUTION:" in script
    assert "GIMP GUI WAYLAND BLOCKER: PRE_WAYLAND_CONNECT" in script
    assert "ALR_GIMP_DEMO_PROFILE_READY target=gimp" in script
    assert "ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=wayland WAYLAND_DISPLAY=alr-gimp-0 XDG_RUNTIME_DIR=/tmp" in script
    assert "ALR_GIMP_DEMO_BUNDLE_LOCK present=true suite=trixie package_count=313" in script
    assert "ALR_GIMP_DEMO_MATERIALIZED present=true package_count=313 gimp_version=3." in script
    assert "ALR_GIMP_DEMO_BINARY present=true path=/usr/bin/gimp" in script
    assert "ALR_GIMP_DEMO_VERSION_EXIT 0" in script
    assert "ALR_GIMP_DEMO_VERSION_STDOUT GNU Image Manipulation Program version 3." in script
    assert "gimp cli help handoff=ALR STATIC ENTRY HANDOFF: PASS" in script
    assert "gimp console version handoff=ALR STATIC ENTRY HANDOFF: PASS" in script
    assert "gimp console version stdout=GNU Image Manipulation Program version 3." in script
    assert "gimp core quit handoff=ALR STATIC ENTRY HANDOFF: FAIL" in script
    assert "gimp core quit exit=0" in script
    assert "gimp core quit blocker=core-quit-timeout" in script
    assert "gimp core quit timed out=alr handoff timed out=true" in script
    assert "gimp core quit child signaled=alr handoff child signaled=true" in script
    assert "gimp core quit handoff exit=alr handoff exit code=-1" in script
    assert "gimp core quit handoff signal=alr handoff signal=9" in script
    assert "gimp core quit fault syscall=alr handoff fault syscall=439" in script
    assert "gimp core quit path rewrite syscalls=alr handoff path rewrite syscall count=" in script
    assert "gimp console batch quit handoff=ALR STATIC ENTRY HANDOFF: FAIL" in script
    assert "gimp console batch quit interpreter=plug-in-script-fu-eval" in script
    assert "gimp console batch quit blocker=core-batch-timeout" in script
    assert "gimp console batch quit timed out=alr handoff timed out=true" in script
    assert "gimp gtk wayland object=1" in script
    expanded_gdk_trace = "wl_display.get_registry,wl_display.sync,wl_registry.bind:wl_compositor,wl_registry.bind:wl_shm,wl_registry.bind:wl_output,wl_display.sync,wl_registry.bind:wl_subcompositor,wl_registry.bind:wl_data_device_manager,wl_registry.bind:gtk_shell1,wl_shm.create_pool,wl_shm_pool.resize,wl_shm_pool.resize,wl_shm_pool.resize,wl_registry.bind:wl_seat,wl_compositor.create_surface,wl_data_device_manager.get_data_device,wl_compositor.create_surface,wl_display.sync"
    expanded_gdk_binds = "wl_compositor,wl_shm,wl_output,wl_subcompositor,wl_data_device_manager,gtk_shell1,wl_seat"
    expanded_gdk_globals = "wl_compositor,wl_shm,xdg_wm_base,wl_seat,wl_output,wl_subcompositor,wl_data_device_manager,gtk_shell1,zxdg_shell_v6,wl_shell,xdg_shell"
    assert f"gimp gtk wayland server request trace={expanded_gdk_trace}" in script
    assert f"gimp gtk wayland server bind trace={expanded_gdk_binds}" in script
    assert "gimp gtk wayland server last request=wl_display.sync" in script
    assert "gimp gtk window wayland connected=true" in script
    assert f"gimp gtk window wayland server request trace={expanded_gdk_trace}" in script
    assert f"gimp gtk window wayland server bind trace={expanded_gdk_binds}" in script
    assert "gimp gtk window wayland server last request=wl_display.sync" in script
    assert "gimp gtk window wayland handoff=ALR STATIC ENTRY HANDOFF: FAIL" in script
    assert "GIMP GDK SURFACE WAYLAND PROBE EXECUTION: PASS" in script
    assert f"gimp gdk surface wayland server request trace={expanded_gdk_trace}" in script
    assert "gimp gui quit wayland connected=true" in script
    assert "gimp gui quit wayland request=wl_display.get_registry" in script
    assert "gimp gui quit wayland handoff=ALR STATIC ENTRY HANDOFF: FAIL" in script
    assert "gimp gui quit wayland server requests=" in script
    assert f"gimp gui quit wayland server request trace={expanded_gdk_trace}" in script
    assert f"gimp gui quit wayland server bind trace={expanded_gdk_binds}" in script
    assert "gimp gui quit wayland server last request=wl_display.sync" in script
    assert "gimp gtk wayland opcode=1" in script
    assert "gimp gtk wayland size=12" in script
    assert "gimp gtk wayland request=wl_display.get_registry" in script
    assert "gimp gtk wayland server requests=" in script
    assert "gimp gtk wayland server response bytes=" in script
    assert f"gimp gtk wayland server globals={expanded_gdk_globals}" in script
    assert "gimp gtk wayland server surfaces created=2" in script
    assert "gimp gtk wayland server data devices=1" in script
    assert "gimp gtk wayland server shell roles=" in script
    assert "gimp gtk window wayland server surfaces created=2" in script
    assert "gimp gdk surface wayland server surfaces created=2" in script
    assert "gimp gtk wayland handoff=ALR STATIC ENTRY HANDOFF: PASS" in script
    assert "gimp gtk wayland stdout=ALR_GIMP3_GTK_WAYLAND_PROBE ok" in script
    assert "gimp gui quit wayland connected=" in script
    assert "gimp gui wayland blocker=pre-wayland-connect" in script
    assert "gimp gui wayland handoff=ALR STATIC ENTRY HANDOFF: FAIL" in script
    assert "versionName=0.4.104-gimp3-wayland" in script
    assert "ALR_WL_BINARY_STREAM bytes=%zu messages=%d checksum=%08x wire=wayland-binary-v1 endian=little" in display_source
    assert "ALR_WL_APP_STREAM_BEGIN frames=%d mode=%s" in display_source
    assert "ALR_WL_APP_STREAM_END frames=%d commits=%d mode=%s" in display_source
    assert "ALR_SIMPLE_GUI_DEMO_BEGIN app=%s" in display_source
    assert "emit_wayland_binary_subset" in display_source
    assert "append_wayland_binary_request" in display_source
    assert "put_u32_le" in display_source
    assert "alr installed package vulkan icd surface clear request" in text
    assert "alr installed package vulkan icd surface clear accepted" in text
    assert "alr installed package vulkan icd stdout" in text
    assert "alr installed package vulkan loader info surface clear request" in text
    assert "alr installed package vulkan loader info surface clear accepted" in text
    assert "alr installed package vulkan loader info stdout" in text
    assert "alr installed package vulkan unix loader info surface clear request" in text
    assert "alr installed package vulkan unix loader info surface clear accepted" in text
    assert "alr installed package vulkan unix loader info stdout" in text
    assert "vulkan bridge transport unix vs tcp ratio pct=" in text
    assert "alr installed package vulkan discovery ack lines" in text
    assert "alr installed package vulkan discovery stdout" in text
    assert "nativeHostVulkanProbe" in text
    assert "host vulkan hardware candidate=" in text
    assert "alr guest gles shim smoke path rewrite" in text
    assert "GUEST EGL INIT VIA SHIM EXECUTION:" in text
    assert "GUEST EGL CONTEXT VIA SHIM EXECUTION:" in text
    assert "GUEST GLES CLEAR VIA SHIM EXECUTION:" in text
    assert "GUEST EGL SWAP COMMAND VIA SHIM EXECUTION:" in text
    assert "GUEST EGL INIT VIA SHIM UPDATE:" in text
    assert "GUEST EGL CONTEXT VIA SHIM UPDATE:" in text
    assert "GUEST GLES CLEAR VIA SHIM UPDATE:" in text
    assert "GUEST EGL SWAP VIA ANDROID SURFACE UPDATE:" in text
    assert "GUEST GLES HARDWARE RENDER UPDATE:" in text
    assert "surface gles shim frames rendered=" in text
    assert "surface render elapsed us=" in text
    assert "surface average frame render us=" in text
    assert "surface gles shim average frame render us=" in text
    assert "surface gles shim draw frames rendered=" in text
    assert "surface gles shim draw average frame render us=" in text
    assert "guest gles draw via android surface=" in text
    assert "native gles baseline frame workload commands=" in text
    assert "buildNativeGlesBaselineCommands" in text
    assert "surface native gles frames rendered=" in text
    assert "surface native gles average frame render us=" in text
    assert "surface gles shim vs native average ratio pct=" in text
    assert "ANDROID HOST VULKAN SURFACE EXECUTION:" in text
    assert "GUEST VULKAN SURFACE CLEAR REQUEST EXECUTION:" in text
    assert "GUEST VULKAN LOADER INFO SURFACE CLEAR EXECUTION:" in text
    assert "GUEST VULKAN UNIX SOCKET LOADER INFO SURFACE CLEAR EXECUTION:" in text
    assert "surface vulkan clear request source=guest-request" in text
    assert "surface vulkan present=ok" in text
    assert "surface vulkan hardware render=true" in text
    assert "runProotRootfsGuestGlesShimBenchmark" in runner
    assert "runAlrRuntimeTrampolineGuestGlesShimBenchmark" in runner
    assert "runProotRootfsGuestGlesShimDrawBenchmark" in runner
    assert "runAlrRuntimeTrampolineGuestGlesShimDrawBenchmark" in runner
    assert "runProotRootfsGuestGlesAbiSmoke" in runner
    assert "runAlrRuntimeTrampolineGuestGlesAbiSmoke" in runner
    assert "runProotRootfsGuestGlesDemoGears" in runner
    assert "runAlrRuntimeTrampolineGuestGlesDemoGears" in runner
    assert "runProotRootfsGuestGlesProcaddrDemo" in runner
    assert "runAlrRuntimeTrampolineGuestGlesProcaddrDemo" in runner
    assert "hasGlesApiSteps" in text
    assert "alrHandoffStdoutText" in text


def test_guest_gles_shim_is_source_built_api_subset():
    source_root = ROOT / "rootfs/guest-src/gles-shim"
    shim_source = (source_root / "alr_gles_shim.c").read_text()
    smoke_source = (source_root / "alr_gles_api_smoke.c").read_text()
    abi_smoke_source = (source_root / "alr_gles_abi_smoke.c").read_text()
    demo_source = (source_root / "alr_gles_demo_gears.c").read_text()
    procaddr_source = (source_root / "alr_gles_procaddr_demo.c").read_text()
    build_script = (ROOT / "scripts/build-guest-gles-shim.sh").read_text()

    for symbol in [
        "alr_egl_get_display",
        "alr_egl_initialize",
        "alr_egl_choose_config",
        "alr_egl_create_context",
        "alr_egl_make_current",
        "alr_gl_viewport",
        "alr_gl_clear_color",
        "alr_gl_clear",
        "alr_egl_swap_buffers",
        "alr_egl_destroy_context",
        "alr_egl_terminate",
        "alr_gl_use_program",
        "alr_gl_enable_vertex_attrib_array",
        "alr_gl_vertex_attrib_pointer",
        "alr_gl_draw_arrays",
        "alr_gl_create_shader",
        "alr_gl_create_program",
    ]:
        assert symbol in shim_source
    assert "ALR_GLES_API_STEP %s %s" in smoke_source
    assert '"eglGetDisplay"' in smoke_source
    assert '"eglSwapBuffers"' in smoke_source
    assert "ALR_GLES_SHIM_FRAME_COUNT" in smoke_source
    assert "ALR_GLES_FRAME_WORKLOAD requested=%d submitted=%d" in smoke_source
    assert "ALR_GLES_DRAW_FRAME_COUNT" in smoke_source
    assert "ALR_GLES_DRAW_WORKLOAD requested=%d submitted=%d" in smoke_source
    assert "ALR_GLES_COMPAT_SUBMIT" in smoke_source
    assert "ALR_GLES_SHIM_COMMAND ALR_GPU_CLEAR" in shim_source
    assert "ALR_GLES_SHIM_COMMAND ALR_GPU_DRAW_TRIANGLE" in shim_source
    assert "ALR_GPU_IPC_HELLO gles-shim-v1" in shim_source
    assert "ALR_GPU_BRIDGE_PORT" in shim_source
    assert "ALR_GPU_BRIDGE_SOCKET" in shim_source
    assert "AF_UNIX" in shim_source
    assert "ALR_GPU_BRIDGE_ACK" in shim_source
    assert "ALR_GPU_BRIDGE_BATCH" in shim_source
    assert "ALR_GPU_BATCH_BEGIN" in shim_source
    assert "ALR_GLES_BATCH_ACK_SUMMARY requested=%d received=%d batches=%d" in shim_source
    assert "ALR_GLES_IPC_ACK_SUMMARY requested=%d received=%d" in shim_source
    assert "alr_bridge_send_command" in shim_source
    for public_symbol in [
        "eglGetDisplay",
        "eglInitialize",
        "eglChooseConfig",
        "eglCreateContext",
        "eglMakeCurrent",
        "eglGetProcAddress",
        "glViewport",
        "glClearColor",
        "glClear",
        "glUseProgram",
        "glVertexAttribPointer",
        "glDrawArrays",
        "glCreateShader",
        "glCreateProgram",
        "glUniform4f",
    ]:
        assert public_symbol in shim_source
        assert public_symbol in (abi_smoke_source + demo_source + procaddr_source)
    assert "ALR_GLES_DEMO_KIND es2gears-like-triangle-strip-subset" in demo_source
    assert "ALR_GLES_DEMO_WORKLOAD requested=%d submitted=%d" in demo_source
    assert "ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset" in procaddr_source
    assert "ALR_GLES_PROC_DEMO_WORKLOAD requested=%d submitted=%d" in procaddr_source
    assert "eglGetProcAddress(\"glDrawArrays\")" not in procaddr_source
    assert "load_gles_functions" in procaddr_source
    assert "ALR_GLES_PROC_DEMO_PROC %s %s" in procaddr_source
    assert "glCreateShader" in demo_source
    assert "glLinkProgram" in demo_source
    assert "glUniform4f" in demo_source
    assert "ALR_GLES_ABI_LIBS visible libEGL.so libGLESv2.so" in abi_smoke_source
    assert "-target aarch64-linux-gnu" in build_script
    assert "libEGL.so" in build_script
    assert "libGLESv2.so" in build_script
    assert "alr-gles-abi-smoke" in build_script
    assert "alr-gles-demo-gears" in build_script
    assert "alr-gles-procaddr-demo" in build_script


def test_guest_vulkan_discovery_client_is_source_built_ipc_probe():
    source = (ROOT / "rootfs/guest-src/vulkan/alr_vulkan_discovery_client.c").read_text()
    proxy_source = (ROOT / "rootfs/guest-src/vulkan/alr_vulkan_proxy.c").read_text()
    proxy_smoke = (ROOT / "rootfs/guest-src/vulkan/alr_vulkan_proxy_smoke.c").read_text()
    icd_smoke = (ROOT / "rootfs/guest-src/vulkan/alr_vulkan_icd_manifest_smoke.c").read_text()
    loader_info = (ROOT / "rootfs/guest-src/vulkan/alr_vulkan_loader_info.c").read_text()
    icd_manifest = (ROOT / "rootfs/guest-src/vulkan/alr_vulkan_icd_manifest.aarch64.json").read_text()
    build_script = (ROOT / "scripts/build-guest-vulkan-probe.sh").read_text()
    cmake = (ROOT / "app/src/main/cpp/CMakeLists.txt").read_text()
    native_report = (ROOT / "app/src/main/cpp/runtime_report.cpp").read_text()

    assert "ALR_VK_DISCOVERY_HELLO" in source
    assert "ALR_VK_DISCOVERY_ACK status=PASS" in source
    assert "ALR_VK_DEVICE_RECORD" in source
    assert "ALR_VK_FEATURE_RECORD" in source
    assert "ALR_VK_SURFACE_CLEAR_REQUEST" in source
    assert "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS" in source
    assert "ALR_VK_DISCOVERY_DEVICE_RECORD ok" in source
    assert "ALR_VK_DISCOVERY_FEATURE_RECORD ok" in source
    assert "ALR_VK_SURFACE_CLEAR_REQUEST_ACCEPTED ok" in source
    assert "ALR_VK_BRIDGE_HOST" in source
    assert "ALR_VK_BRIDGE_PORT" in source
    assert "vkEnumerateInstanceVersion" in proxy_source
    assert "vkGetInstanceProcAddr" in proxy_source
    assert "alrVkProxyRequestSurfaceClear" in proxy_source
    assert "ALR_VK_BRIDGE_SOCKET" in proxy_source
    assert "AF_UNIX" in proxy_source
    assert '"ALVB"' in proxy_source
    assert '"ALVR"' in proxy_source
    assert "ALR_VK_BINARY_BRIDGE_ACK" in proxy_source
    assert 'dlopen("libvulkan.so.1"' in proxy_smoke
    assert "ALR_VK_PROXY_BINARY_BRIDGE ok" in proxy_smoke
    assert "ALR_VK_PROXY_SURFACE_CLEAR_REQUEST_ACCEPTED ok" in proxy_smoke
    assert "ALR_VK_ICD_MANIFEST path=" in icd_smoke
    assert "ALR_VK_ICD_LIBRARY_PATH" in icd_smoke
    assert "ALR_VK_ICD_BINARY_BRIDGE ok" in icd_smoke
    assert "ALR_VK_ICD_SURFACE_CLEAR_REQUEST_ACCEPTED ok" in icd_smoke
    assert "VK_DRIVER_FILES" in loader_info
    assert "VK_ICD_FILENAMES" in loader_info
    assert "ALR_VK_LOADER_SELECTED_MANIFEST" in loader_info
    assert "ALR_VK_LOADER_VULKANINFO_DEVICE_RECORD ok" in loader_info
    assert "ALR_VK_LOADER_BINARY_BRIDGE ok" in loader_info
    assert "ALR_VK_LOADER_DONE ok" in loader_info
    assert '"library_path": "libvulkan.so.1"' in icd_manifest
    assert '"api_version": "1.3.247"' in icd_manifest
    assert "-target aarch64-linux-gnu" in build_script
    assert "alr-vulkan-discovery-client" in build_script
    assert "libvulkan.so.1" in build_script
    assert "alr-vulkan-proxy-smoke" in build_script
    assert "alr-vulkan-icd-manifest-smoke" in build_script
    assert "alr-vulkan-loader-info" in build_script
    assert "vulkan)" in cmake
    assert "#include <vulkan/vulkan.h>" in native_report
    assert "vkCreateInstance" in native_report
    assert "vkEnumeratePhysicalDevices" in native_report
    assert "vkGetPhysicalDeviceFeatures" in native_report
    assert "vkCreateAndroidSurfaceKHR" in native_report
    assert "vkQueuePresentKHR" in native_report
    assert "vkCreateDevice" in native_report
    assert "host vulkan feature robust buffer access=" in native_report


def test_guest_syscall_bench_is_source_built_fixture():
    source = (ROOT / "rootfs/guest-src/bench/alr_syscall_bench.c").read_text()
    build_script = (ROOT / "scripts/build-guest-syscall-bench.sh").read_text()
    assert "ALR SYSCALL BENCH:" in source
    assert "bench_stat" in source
    assert "bench_open_read_close" in source
    assert "bench_fsmeta" in source
    assert '"fsmeta"' in source
    assert "bench_spawn" in source
    assert "alr-syscall-bench" in build_script


def test_guest_path_preload_is_source_built_fast_path_shim():
    source = (ROOT / "rootfs/guest-src/preload/alr_path_preload.c").read_text()
    build_script = (ROOT / "scripts/build-guest-path-preload.sh").read_text()
    assert "alr_rewrite_path" in source
    assert "ALR_ROOTFS" in source
    assert "SYS_openat" in source
    assert "SYS_newfstatat" in source
    assert "#include <dlfcn.h>" not in source
    assert "ALR_RTLD_NEXT" in source
    assert "dlsym" in source
    assert "openat" in source
    assert "fstatat" in source
    assert "__xstat" in source
    assert "__fxstatat" in source
    assert "access" in source
    assert "readlink" in source
    assert "chdir" in source
    assert "getcwd" in source
    assert "opendir" in source
    assert "fopen" in source
    assert "realpath" in source
    assert "canonicalize_file_name" in source
    assert "mkstemp" in source
    assert "renameat" in source
    assert "mkdirat" in source
    assert "unlinkat" in source
    assert "rmdir" in source
    assert "ALR_PRELOAD_FAKE_ROOT" in source
    assert "fchownat" in source
    assert "libalr_path_preload.so" in build_script


def test_guest_gles_shim_binaries_link_rootfs_libraries_without_libdl():
    import subprocess
    import tempfile

    with tarfile.open(PAYLOAD) as archive, tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        smoke = tmp_path / "alr-gles-shim-smoke"
        abi_smoke = tmp_path / "alr-gles-abi-smoke"
        demo = tmp_path / "alr-gles-demo-gears"
        procaddr = tmp_path / "alr-gles-procaddr-demo"
        shim = tmp_path / "libalr_gles_shim.so"
        egl = tmp_path / "libEGL.so"
        glesv2 = tmp_path / "libGLESv2.so"
        smoke.write_bytes(archive.extractfile("./usr/bin/alr-gles-shim-smoke").read())
        abi_smoke.write_bytes(archive.extractfile("./usr/bin/alr-gles-abi-smoke").read())
        demo.write_bytes(archive.extractfile("./usr/bin/alr-gles-demo-gears").read())
        procaddr.write_bytes(archive.extractfile("./usr/bin/alr-gles-procaddr-demo").read())
        shim.write_bytes(archive.extractfile("./usr/lib/androlinux/libalr_gles_shim.so").read())
        egl.write_bytes(archive.extractfile("./usr/lib/androlinux/libEGL.so").read())
        glesv2.write_bytes(archive.extractfile("./usr/lib/androlinux/libGLESv2.so").read())
        smoke_dynamic = subprocess.check_output(["readelf", "-d", smoke], text=True)
        abi_smoke_dynamic = subprocess.check_output(["readelf", "-d", abi_smoke], text=True)
        demo_dynamic = subprocess.check_output(["readelf", "-d", demo], text=True)
        procaddr_dynamic = subprocess.check_output(["readelf", "-d", procaddr], text=True)
        shim_dynamic = subprocess.check_output(["readelf", "-d", shim], text=True)
        egl_dynamic = subprocess.check_output(["readelf", "-d", egl], text=True)
        glesv2_dynamic = subprocess.check_output(["readelf", "-d", glesv2], text=True)

    assert "NEEDED" in smoke_dynamic
    assert "libalr_gles_shim.so" in smoke_dynamic
    assert "libEGL.so" in abi_smoke_dynamic
    assert "libGLESv2.so" in abi_smoke_dynamic
    assert "libEGL.so" in demo_dynamic
    assert "libGLESv2.so" in demo_dynamic
    assert "libEGL.so" in procaddr_dynamic
    assert "libGLESv2.so" not in procaddr_dynamic
    assert "RUNPATH" in smoke_dynamic
    assert "RUNPATH" in abi_smoke_dynamic
    assert "RUNPATH" in demo_dynamic
    assert "RUNPATH" in procaddr_dynamic
    assert "/usr/lib/androlinux" in smoke_dynamic
    assert "/usr/lib/androlinux" in abi_smoke_dynamic
    assert "/usr/lib/androlinux" in demo_dynamic
    assert "/usr/lib/androlinux" in procaddr_dynamic
    assert "libdl.so" not in smoke_dynamic
    assert "libdl.so" not in abi_smoke_dynamic
    assert "libdl.so" not in demo_dynamic
    assert "libdl.so" not in procaddr_dynamic
    assert "SONAME" in shim_dynamic
    assert "libalr_gles_shim.so" in shim_dynamic
    assert "SONAME" in egl_dynamic
    assert "libEGL.so" in egl_dynamic
    assert "SONAME" in glesv2_dynamic
    assert "libGLESv2.so" in glesv2_dynamic


def test_native_surface_renderer_accepts_multi_frame_stream_and_reports_bridge():
    text = CPP.read_text()
    assert "parse_surface_frames" in text
    assert "surface requested frames=" in text
    assert "surface wayland frames rendered=" in text
    assert "surface x11 frames rendered=" in text
    assert "surface gles shim frames rendered=" in text
    assert "surface render elapsed us=" in text
    assert "surface average frame render us=" in text
    assert "surface gles shim render elapsed us=" in text
    assert "surface gles shim average frame render us=" in text
    assert "surface gles shim draw render elapsed us=" in text
    assert "surface gles shim draw average frame render us=" in text
    assert "surface native gles frames rendered=" in text
    assert "surface native gles render elapsed us=" in text
    assert "surface native gles average frame render us=" in text
    assert "surface gles shim vs native average ratio pct=" in text
    assert "guest gles draw via android surface=" in text
    assert "surface gui total frames rendered=" in text
    assert "surface frames rendered=" in text
    assert "surface frames dropped=" in text
    assert "surface frame lossless=" in text
    assert "guest gpu ipc bridge hardware render=" in text
    assert "guest gui gpu compositor hardware render=" in text
    assert "guest wayland/x11 gui gpu surface hardware render=" in text
    assert "guest egl swap via android surface=" in text
    assert "guest gles hardware render=" in text
    assert "eglSwapBuffers" in text


def test_guest_gui_client_sources_support_unix_socket_transport():
    source = (ROOT / "rootfs/guest-src/gui/alr_gui_gpu_client.c").read_text()
    display_source = (ROOT / "rootfs/guest-src/gui/alr_wayland_display_client.c").read_text()
    build_script = (ROOT / "scripts/build-guest-gui-client.sh").read_text()

    assert "ALR_GUI_BRIDGE_SOCKET" in source
    assert "AF_UNIX" in source
    assert "ALR_GUI_IPC_HELLO protocol=%s frames=%d transport=%s" in source
    assert "ALR_GUI_FRAME %s seq=%d" in source
    assert "ALR_GUI_IPC_CLIENT ok sent=%d transport=%s ack=%s" in source
    assert "ALR_WAYLAND_DISPLAY_SOCKET" in display_source
    assert "WAYLAND_DISPLAY" in display_source
    assert "XDG_RUNTIME_DIR" in display_source
    assert "AF_UNIX" in display_source
    assert "ALR_WL_CONNECT display=%s runtime=%s transport=unix-abstract-wayland" in display_source
    assert "ALR_WL_REGISTRY global=wl_compositor" in display_source
    assert "ALR_WL_SURFACE_CREATE id=10 compositor=1" in display_source
    assert "ALR_WL_WIRE object=%u opcode=%u size=%u header=0x%08x name=%s" in display_source
    assert "emit_wayland_wire_subset" in display_source
    assert "wl_display.get_registry" in display_source
    assert "wl_registry.bind" in display_source
    assert "wl_compositor.create_surface" in display_source
    assert "wl_shm.create_pool" in display_source
    assert "wl_shm_pool.create_buffer" in display_source
    assert "wl_surface.damage_buffer" in display_source
    assert "wl_surface.commit" in display_source
    assert "ALR_WL_BUFFER_CREATE id=20 width=%d height=%d stride=%d format=argb8888 payload=shared-file" in display_source
    assert "ALR_WL_SHM_POOL_CREATE id=30 path=%s bytes=%zu checksum=%08x buffers=%d layout=triple-buffer-file" in display_source
    assert "ALR_WL_SHM_POOL_FD id=%d fd_index=%d bytes=%zu checksum=%08x transport=scm-rights-memfd layout=triple-buffer" in display_source
    assert "ALR_WL_AHB_BACKING_ADVERTISE version=1 allocator=android-host" in display_source
    assert "ALR_WL_DAMAGE surface=10 buffer=20 seq=%d" in display_source
    assert "ALR_WL_BUFFER_ATTACH surface=10 buffer=20 seq=%d path=%s" in display_source
    assert "backing=host-ahardwarebuffer buffer_slot=%d dirty_x=%d dirty_y=%d dirty_w=%d dirty_h=%d dirty_bytes=%zu update=partial" in display_source
    assert "send_fd_preamble" in display_source
    assert "create_memfd_payload" in display_source
    assert "SCM_RIGHTS" in display_source
    assert "payload_reds[frame_count]" in display_source
    assert "alr-wl-buffer-20-seq%02d.rgba" in display_source
    assert "ALR_WAYLAND_PAYLOAD_DIR" in display_source
    assert "fnv1a32" in display_source
    assert "write_rgba_payload" in display_source
    assert "ALR_WL_SURFACE_COMMIT surface=10 buffer=20 seq=%d" in display_source
    assert "ALR_WL_DISPLAY_CLIENT ok display=%s commits=%d app=%s mode=%s glibc_dynamic=%s ack=%s" in display_source
    assert "alr-wayland-gpu-client" in build_script
    assert "alr-x11-gpu-client" in build_script
    assert "alr-wayland-display-client" in build_script
    assert "alr-simple-gui-demo" in build_script
    assert "aarch64-linux-gnu" in build_script
