from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "app/build.gradle.kts"
RUNNER = ROOT / "app/src/main/java/dev/chanwoo/androlinux/NativeCommandRunner.kt"
PREBUILT_DIR = ROOT / "app/src/main/prebuiltNative/arm64-v8a"


def test_arm64_proot_loader_prebuilt_is_packaged():
    assert (PREBUILT_DIR / "libproot-loader.so").is_file()
    text = BUILD.read_text()
    assert "libproot-loader.so" in text


def test_proot_runner_sets_loader_and_tmp_environment():
    text = RUNNER.read_text()
    assert "PROOT_LOADER" in text
    assert "PROOT_TMP_DIR" in text
    assert "PROOT_VERBOSE" in text
    assert "libproot-loader.so" in text
