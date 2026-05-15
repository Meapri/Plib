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
            "./usr/lib/androlinux/libalr_gles_shim.so",
            "./usr/lib/androlinux/libEGL.so",
            "./usr/lib/androlinux/libGLESv2.so",
            "./usr/bin/alr-wayland-gpu-client",
            "./usr/bin/alr-x11-gpu-client",
        ]:
            assert name in names
            assert archive.getmember(name).mode & 0o111
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


def test_android_runs_loopback_ipc_bridge_and_reports_loss_metrics():
    runner = RUNNER.read_text()
    assert "runProotRootfsGuestGpuClientIpc" in runner
    assert "runAlrRuntimeTrampolineGuestGpuClientIpc" in runner
    assert "runAlrRuntimeTrampolineGuestGuiClientIpc" in runner
    assert "runAlrRuntimeTrampolineGuestGlesShimSmoke" in runner
    assert "pathRewrite = true" in runner
    assert "pathRewriteLimit = 1" in runner
    assert "extraGuestEnvironment" in runner
    assert "ALR_GPU_BRIDGE_PORT" in runner
    text = MAIN.read_text()
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
    assert "ALR_GUI_IPC_ACK" in text
    assert "guest wayland gui ipc seq gaps" in text
    assert "guest x11 gui ipc seq gaps" in text
    assert "ALR GUEST GPU IPC BRIDGE EXECUTION" in text
    assert "ALR GUEST GLES SHIM SMOKE EXECUTION" in text
    assert "ALR GUEST WAYLAND GUI GPU BRIDGE EXECUTION" in text
    assert "ALR GUEST X11 GUI GPU BRIDGE EXECUTION" in text
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


def test_guest_syscall_bench_is_source_built_fixture():
    source = (ROOT / "rootfs/guest-src/bench/alr_syscall_bench.c").read_text()
    build_script = (ROOT / "scripts/build-guest-syscall-bench.sh").read_text()
    assert "ALR SYSCALL BENCH:" in source
    assert "bench_stat" in source
    assert "bench_open_read_close" in source
    assert "bench_spawn" in source
    assert "alr-syscall-bench" in build_script


def test_guest_path_preload_is_source_built_fast_path_shim():
    source = (ROOT / "rootfs/guest-src/preload/alr_path_preload.c").read_text()
    build_script = (ROOT / "scripts/build-guest-path-preload.sh").read_text()
    assert "alr_rewrite_path" in source
    assert "ALR_ROOTFS" in source
    assert "SYS_openat" in source
    assert "SYS_newfstatat" in source
    assert "RTLD_NEXT" not in source
    assert "openat" in source
    assert "fstatat" in source
    assert "__xstat" in source
    assert "__fxstatat" in source
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
