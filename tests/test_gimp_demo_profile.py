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


def test_gimp_bundle_lock_resolves_debian_arm64_gimp_depends_closure():
    lock = json.loads(LOCK.read_text())

    assert lock["name"] == "plib-gimp-demo-bundle"
    assert lock["suite"] == "bookworm"
    assert lock["architecture"] == "arm64"
    assert lock["targets"] == ["gimp"]
    assert lock["dependency_fields"] == ["Pre-Depends", "Depends"]
    assert lock["package_count"] == 246
    assert lock["download_size_mib"] == 122.27
    assert lock["missing_relations"] == []
    assert any(package["package"] == "gimp" for package in lock["packages"])
    assert any(package["package"] == "gimp-data" for package in lock["packages"])
    assert any(package["package"] == "libgtk2.0-0" for package in lock["packages"])


def test_gimp_profile_targets_wayland_android_surface_launch():
    profile = json.loads(PROFILE.read_text())

    assert profile["target_app"] == "gimp"
    assert profile["program"] == "/usr/bin/gimp"
    assert profile["argv"] == ["gimp", "--new-instance", "--no-data", "--no-fonts"]
    assert profile["env"]["GDK_BACKEND"] == "wayland"
    assert profile["env"]["WAYLAND_DISPLAY"] == "alr-gimp-0"
    assert "Android Surface" in profile["presentation"]


def test_rootfs_contains_gimp_demo_launcher_profile_and_lock():
    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./usr/local/bin/alr-package-gimp-demo" in names
        assert archive.getmember("./usr/local/bin/alr-package-gimp-demo").mode & 0o111
        assert "./usr/share/androlinux/gimp-demo-profile.json" in names
        assert "./usr/share/androlinux/gimp-demo-bundle.lock.json" in names
        launcher = archive.extractfile("./usr/local/bin/alr-package-gimp-demo").read()
        lock = archive.extractfile("./usr/share/androlinux/gimp-demo-bundle.lock.json").read()

    assert b"ALR_GIMP_DEMO_PROFILE_READY target=gimp" in launcher
    assert b"ALR_GIMP_DEMO_NEXT_STEP install_debian_arm64_bundle_from_lock" in launcher
    assert b'"package_count": 246' in lock


def test_android_reports_gimp_demo_profile_without_claiming_gimp_execution():
    text = MAIN.read_text()
    runner = RUNNER.read_text()

    assert "runAlrRuntimeTrampolineInstalledPackageGimpDemoProfile" in runner
    assert "GDK_BACKEND" in runner
    assert "WAYLAND_DISPLAY" in runner
    assert "GIMP DEMO PROFILE EXECUTION:" in text
    assert "GIMP DEMO BUNDLE LOCK:" in text
    assert "rootfs /usr/bin/gimp exists=" in text
    assert "ALR_GIMP_DEMO_BINARY present=false" in text


def test_gimp_bundle_resolver_defaults_to_minimal_depends_profile():
    text = RESOLVER.read_text()

    assert "DEFAULT_PACKAGES_URL" in text
    assert "bookworm/main/binary-arm64/Packages.xz" in text
    assert "DEFAULT_TARGETS = [\"gimp\"]" in text
    assert "--include-recommends" in text
    assert "include_fields = [\"Pre-Depends\", \"Depends\"]" in text
