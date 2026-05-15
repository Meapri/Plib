from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "app/build.gradle.kts"
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"


def test_debug_apk_version_code_is_bumped_for_device_update():
    text = BUILD.read_text()
    assert "versionCode = 26" in text
    assert 'versionName = "0.4.13-clean-rootfs-surface-gpu"' in text


def test_main_activity_starts_with_visible_build_stamp_before_summary():
    text = MAIN.read_text()
    assert "build: 0.4.13-clean-rootfs-surface-gpu" in text
    assert text.index("build: 0.4.13-clean-rootfs-surface-gpu") < text.index("execution summary")
