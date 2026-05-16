import json
import tarfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "rootfs/gimp-demo-bundle.lock.json"
PROFILE = ROOT / "rootfs/gimp-demo-profile.json"
PAYLOAD = ROOT / "app/src/main/assets/rootfs/payloads/tiny-rootfs.tar"
RUNNER = ROOT / "app/src/main/java/dev/chanwoo/androlinux/NativeCommandRunner.kt"
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"
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
    assert "runAlrRuntimeTrampolineGimp3GtkWaylandPythonProbe" in runner
    assert '"GDK_BACKEND" to "wayland"' in runner
    assert "WAYLAND_DISPLAY" in runner
    assert '"XDG_RUNTIME_DIR" to "/tmp"' in runner
    assert "GIMP DEMO PROFILE EXECUTION:" in text
    assert "GIMP GTK WAYLAND PROBE EXECUTION:" in text
    assert "GIMP GUI WAYLAND PROBE EXECUTION:" in text
    assert "GIMP GUI WAYLAND BLOCKER:" in text
    assert "gimp gtk wayland request=" in text
    assert "gimp gui wayland request=" in text
    assert "GIMP DEMO BUNDLE LOCK:" in text
    assert "rootfs /usr/bin/gimp exists=" in text
    assert "rootfs gimp demo materialized exists=" in text
    assert "ALR_GIMP_DEMO_BINARY present=true" in text
    assert "ALR_GIMP_DEMO_VERSION_EXIT 0" in text


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
