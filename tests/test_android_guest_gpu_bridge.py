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
            "./usr/lib/androlinux/libalr_gles_shim.so",
            "./usr/bin/alr-wayland-gpu-client",
            "./usr/bin/alr-x11-gpu-client",
        ]:
            assert name in names
            assert archive.getmember(name).mode & 0o111
        payload = archive.extractfile("./usr/share/androlinux/gpu-bridge.txt").read().decode()
        assert "tcp-loopback" in payload
        assert "multi-frame" in payload
        assert "gles-shim-smoke" in payload
        assert "gles-shim-api-subset" in payload
        assert "surface-present" in payload
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
    assert "hasGlesApiSteps" in text
    assert "alrHandoffStdoutText" in text


def test_guest_gles_shim_is_source_built_api_subset():
    source_root = ROOT / "rootfs/guest-src/gles-shim"
    shim_source = (source_root / "alr_gles_shim.c").read_text()
    smoke_source = (source_root / "alr_gles_api_smoke.c").read_text()
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
    ]:
        assert symbol in shim_source
    assert "ALR_GLES_API_STEP %s %s" in smoke_source
    assert '"eglGetDisplay"' in smoke_source
    assert '"eglSwapBuffers"' in smoke_source
    assert "ALR_GLES_SHIM_COMMAND ALR_GPU_CLEAR" in shim_source
    assert "-target aarch64-linux-gnu" in build_script
    assert "-Wl,-rpath,/usr/lib/androlinux" in build_script


def test_guest_gles_shim_binary_links_rootfs_library_without_libdl():
    import subprocess
    import tempfile

    with tarfile.open(PAYLOAD) as archive, tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        smoke = tmp_path / "alr-gles-shim-smoke"
        shim = tmp_path / "libalr_gles_shim.so"
        smoke.write_bytes(archive.extractfile("./usr/bin/alr-gles-shim-smoke").read())
        shim.write_bytes(archive.extractfile("./usr/lib/androlinux/libalr_gles_shim.so").read())
        smoke_dynamic = subprocess.check_output(["readelf", "-d", smoke], text=True)
        shim_dynamic = subprocess.check_output(["readelf", "-d", shim], text=True)

    assert "NEEDED" in smoke_dynamic
    assert "libalr_gles_shim.so" in smoke_dynamic
    assert "RUNPATH" in smoke_dynamic
    assert "/usr/lib/androlinux" in smoke_dynamic
    assert "libdl.so" not in smoke_dynamic
    assert "SONAME" in shim_dynamic
    assert "libalr_gles_shim.so" in shim_dynamic


def test_native_surface_renderer_accepts_multi_frame_stream_and_reports_bridge():
    text = CPP.read_text()
    assert "parse_surface_frames" in text
    assert "surface requested frames=" in text
    assert "surface wayland frames rendered=" in text
    assert "surface x11 frames rendered=" in text
    assert "surface gles shim frames rendered=" in text
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
