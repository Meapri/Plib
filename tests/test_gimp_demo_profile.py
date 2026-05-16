import json
import tarfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "rootfs/gimp-demo-bundle.lock.json"
PROFILE = ROOT / "rootfs/gimp-demo-profile.json"
PAYLOAD = ROOT / "app/src/main/assets/rootfs/payloads/tiny-rootfs.tar"
RUNNER = ROOT / "app/src/main/java/dev/chanwoo/androlinux/NativeCommandRunner.kt"
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"
HANDOFF = ROOT / "app/src/main/cpp/alr_runtime/alr_handoff.cpp"
RESOLVER = ROOT / "tools/gimp_bundle_resolver.py"
MATERIALIZER = ROOT / "tools/gimp_bundle_materializer.py"


def test_gimp_bundle_lock_resolves_debian_arm64_gimp_depends_closure():
    lock = json.loads(LOCK.read_text())

    assert lock["name"] == "plib-gimp-demo-bundle"
    assert lock["suite"] == "trixie"
    assert lock["architecture"] == "arm64"
    assert lock["targets"] == ["gimp"]
    assert lock["dependency_fields"] == ["Pre-Depends", "Depends"]
    assert lock["package_count"] == 313
    assert lock["download_size_mib"] == 122.13
    assert lock["missing_relations"] == []
    assert any(package["package"] == "gimp" for package in lock["packages"])
    assert any(package["package"] == "gimp-data" for package in lock["packages"])
    assert any(package["package"] == "libgtk-3-0t64" for package in lock["packages"])
    assert any(package["package"] == "libwayland-client0" for package in lock["packages"])


def test_gimp_profile_targets_wayland_android_surface_launch():
    profile = json.loads(PROFILE.read_text())

    assert profile["target_app"] == "gimp"
    assert profile["program"] == "/usr/bin/gimp"
    assert profile["argv"] == ["gimp", "--new-instance", "--no-data", "--no-fonts"]
    assert profile["version"] == "v104"
    assert profile["env"]["GDK_BACKEND"] == "wayland"
    assert profile["env"]["WAYLAND_DISPLAY"] == "alr-gimp-0"
    assert profile["env"]["XDG_RUNTIME_DIR"] == "/tmp"
    assert "Android Surface" in profile["presentation"]


def test_rootfs_contains_gimp_demo_launcher_profile_and_lock():
    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./usr/local/bin/alr-package-gimp-demo" in names
        assert archive.getmember("./usr/local/bin/alr-package-gimp-demo").mode & 0o111
        assert "./usr/share/androlinux/gimp-demo-profile.json" in names
        assert "./usr/share/androlinux/gimp-demo-bundle.lock.json" in names
        assert "./usr/share/androlinux/gimp-demo-materialized.txt" in names
        assert "./usr/bin/gimp" in names
        assert "./usr/bin/gimp-3.0" in names
        assert "./usr/lib/aarch64-linux-gnu/libgtk-3.so.0" in names
        assert "./usr/lib/aarch64-linux-gnu/libwayland-client.so.0" in names
        launcher = archive.extractfile("./usr/local/bin/alr-package-gimp-demo").read()
        lock = archive.extractfile("./usr/share/androlinux/gimp-demo-bundle.lock.json").read()
        materialized = archive.extractfile("./usr/share/androlinux/gimp-demo-materialized.txt").read()

    assert b"ALR_GIMP_DEMO_PROFILE_READY target=gimp" in launcher
    assert b"ALR_GIMP_DEMO_MATERIALIZED present=true package_count=" in launcher
    assert b"ALR_GIMP_DEMO_LAUNCH_MODE version-probe" in launcher
    assert b"ALR_GIMP_DEMO_VERSION_EXIT" in launcher
    assert b'"package_count": 313' in lock
    assert b'"suite": "trixie"' in lock
    assert b"ALR_GIMP_DEMO_MATERIALIZED=true" in materialized
    assert b"display_backend=wayland" in materialized


def test_materialized_gimp_rootfs_has_no_unsafe_symlinks_for_safe_android_extract():
    with tarfile.open(PAYLOAD) as archive:
        unsafe_links = [
            (member.name, member.linkname)
            for member in archive.getmembers()
            if member.issym() and (member.linkname.startswith("/") or ".." in Path(member.linkname).parts)
        ]

    assert unsafe_links == []


def test_android_reports_materialized_gimp_version_probe():
    text = MAIN.read_text()
    runner = RUNNER.read_text()

    assert "runAlrRuntimeTrampolineInstalledPackageGimpDemoProfile" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageGimpGuiWaylandProbe" in runner
    assert "runAlrRuntimeTrampolineInstalledPackageGimpGuiWaylandFastProbe" in runner
    assert "runAlrRuntimeTrampolineGimp3HelpProbe" in runner
    assert "runAlrRuntimeTrampolineGimp3ConsoleVersionProbe" in runner
    assert "runAlrRuntimeTrampolineGimp3CoreQuitProbe" in runner
    assert "runAlrRuntimeTrampolineGimp3ConsoleBatchQuitProbe" in runner
    assert "plug-in-script-fu-eval" in runner
    assert "runAlrRuntimeTrampolineGimp3GuiQuitWaylandProbe" in runner
    assert "runAlrRuntimeTrampolineGimp3ProgramProbe" in runner
    assert "runAlrRuntimeTrampolineGimp3GtkWaylandWindowPythonProbe" in runner
    assert "runAlrRuntimeTrampolineGimp3GdkSurfaceWaylandPythonProbe" in runner
    assert '"GIMP3_DIRECTORY" to "/tmp/alr-gimp3"' in runner
    assert '"GIMP3_CACHEDIR" to "/tmp/alr-gimp3-cache"' in runner
    assert "runAlrRuntimeTrampolineGimp3GtkWaylandPythonProbe" in runner
    assert '"GDK_BACKEND" to "wayland"' in runner
    assert "WAYLAND_DISPLAY" in runner
    assert '"WAYLAND_DEBUG" to "1"' in runner
    assert '"XDG_RUNTIME_DIR" to "/tmp"' in runner
    assert "GIMP DEMO PROFILE EXECUTION:" in text
    assert "GIMP CLI HELP PROBE EXECUTION:" in text
    assert "GIMP CONSOLE VERSION PROBE EXECUTION:" in text
    assert "GIMP CORE QUIT PROBE EXECUTION:" in text
    assert "GIMP CORE QUIT BLOCKER:" in text
    assert "GIMP CONSOLE BATCH QUIT PROBE EXECUTION:" in text
    assert "GIMP CONSOLE BATCH QUIT BLOCKER:" in text
    assert "GIMP GTK WAYLAND PROBE EXECUTION:" in text
    assert "GIMP GTK WINDOW WAYLAND PROBE EXECUTION:" in text
    assert "GIMP GDK SURFACE WAYLAND PROBE EXECUTION:" in text
    assert "GIMP GUI QUIT WAYLAND PROBE EXECUTION:" in text
    assert "GIMP GUI WAYLAND PROBE EXECUTION:" in text
    assert "GIMP GUI WAYLAND BLOCKER:" in text
    assert "gimp cli help handoff=" in text
    assert "gimp console version handoff=" in text
    assert "gimp core quit handoff=" in text
    assert "gimp core quit blocker=" in text
    assert "gimp core quit timed out=" in text
    assert "gimp core quit handoff exit=" in text
    assert "gimp core quit handoff signal=" in text
    assert "gimp core quit fault syscall=" in text
    assert "gimp core quit path rewrite syscalls=" in text
    assert "gimp console batch quit handoff=" in text
    assert "gimp console batch quit exit=" in text
    assert "gimp console batch quit interpreter=plug-in-script-fu-eval" in text
    assert "gimp console batch quit blocker=" in text
    assert "gimp console batch quit timed out=" in text
    assert "gimp console batch quit fault syscall=" in text
    assert "gimp gtk wayland request=" in text
    assert "gimp gtk wayland server requests=" in text
    assert "gimp gtk wayland server response bytes=" in text
    assert "gimp gtk wayland server globals=" in text
    assert "gimp gtk wayland server request trace=" in text
    assert "gimp gtk wayland server bind trace=" in text
    assert "gimp gtk wayland server fd count=" in text
    assert "gimp gtk wayland server shm pool buffers=" in text
    assert "gimp gtk wayland server surface commits=" in text
    assert "gimp gtk wayland server keyboard keymaps=" in text
    assert "minimalWaylandGlobals" in text
    assert "waylandRegistryGlobal" in text
    assert '"wl_shm_pool" -> when (opcode)' in text
    assert '"wl_seat" -> when (opcode)' in text
    assert '"wl_data_device_manager" -> when (opcode)' in text
    assert '"gtk_shell1" -> when (opcode)' in text
    assert '"zxdg_shell_v6" -> when (opcode)' in text
    assert "MinimalWaylandGlobal(7, \"wl_data_device_manager\", 3)" in text
    assert "MinimalWaylandGlobal(8, \"gtk_shell1\", 5)" in text
    assert "MinimalWaylandGlobal(9, \"zxdg_shell_v6\", 1)" in text
    assert "wl_seat.get_keyboard" in text
    assert "waylandKeyboardKeymap" in text
    assert "ParcelFileDescriptor.open" in text
    assert "socket.ancillaryFileDescriptors" in text
    assert "waylandFdSizeBytes" in text
    assert "gimp gtk window wayland server request trace=" in text
    assert "gimp gtk window wayland server bind trace=" in text
    assert "gimp gtk window wayland server fd count=" in text
    assert "gimp gtk window wayland server shm pool buffers=" in text
    assert "gimp gtk window wayland server surface commits=" in text
    assert "gimp gtk window wayland server seat trace=" in text
    assert "gimp gtk window wayland server keyboard keymaps=" in text
    assert "gimp gtk window wayland handoff=" in text
    assert "gimp gdk surface wayland server request trace=" in text
    assert "gimp gdk surface wayland server bind trace=" in text
    assert "gimp gdk surface wayland server fd count=" in text
    assert "gimp gdk surface wayland server shm pool buffers=" in text
    assert "gimp gdk surface wayland server surface attaches=" in text
    assert "gimp gdk surface wayland server surface commits=" in text
    assert "gimp gdk surface wayland handoff=" in text
    assert "gimp gui quit wayland request=" in text
    assert "gimp gui quit wayland server globals=" in text
    assert "gimp gui quit wayland server request trace=" in text
    assert "gimp gui quit wayland server bind trace=" in text
    assert "gimp gui quit wayland server fd count=" in text
    assert "gimp gui quit wayland server shm pool buffers=" in text
    assert "gimp gui quit wayland server surface commits=" in text
    assert "gimp gui quit wayland server keyboard keymaps=" in text
    assert "gimp gui wayland request=" in text
    assert "gimp gui wayland server fd count=" in text
    assert "gimp gui wayland server shm pool buffers=" in text
    assert "gimp gui wayland server surface commits=" in text
    assert "gimp gui wayland server keyboard keymaps=" in text
    assert "GIMP DEMO BUNDLE LOCK:" in text
    assert "rootfs /usr/bin/gimp exists=" in text
    assert "rootfs gimp demo materialized exists=" in text
    assert "ALR_GIMP_DEMO_BINARY present=true" in text
    assert "ALR_GIMP_DEMO_VERSION_EXIT 0" in text


def test_alr_handoff_timeout_reaps_traced_gimp_style_processes():
    text = HANDOFF.read_text()

    assert "reap_after_forced_kill" in text
    assert "wall_clock_timeout_expired" in text
    assert "monotonic_elapsed_ms(handoff_started) >= result.timeout_ms" in text
    assert "force_kill_and_reap" in text
    assert "result.timed_out = true" in text
    assert "waited_ms" not in text
    assert "::waitpid(child, &status, 0)" not in text
    assert "waited < 0 && !result.timed_out" in text
    assert "result.signal_number = SIGKILL" in text


def test_gimp_bundle_resolver_defaults_to_minimal_depends_profile():
    text = RESOLVER.read_text()

    assert "DEFAULT_PACKAGES_URL" in text
    assert 'DEFAULT_SUITE = "trixie"' in text
    assert "DEFAULT_TARGETS = [\"gimp\"]" in text
    assert "--include-recommends" in text
    assert "include_fields = [\"Pre-Depends\", \"Depends\"]" in text


def test_gimp_bundle_materializer_downloads_verifies_and_overlays_rootfs_artifacts():
    text = MATERIALIZER.read_text()

    assert "download_package" in text
    assert "sha256_file" in text
    assert "extract_deb" in text
    assert "write_minimal_dpkg_status" in text
    assert "overlay_plib_gimp_artifacts" in text
    assert "materialize_unsafe_symlinks" in text
    assert "gimp-demo-materialized.txt" in text
