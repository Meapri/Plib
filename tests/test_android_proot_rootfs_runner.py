from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "app/src/main/java/dev/chanwoo/androlinux/NativeCommandRunner.kt"
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"


def test_native_command_runner_can_attempt_proot_rootfs_program():
    text = RUNNER.read_text()
    assert "runProotRootfsProgram" in text
    assert '"-R"' in text
    assert '"-w"' in text
    assert '"/"' in text
    assert "runProotRootfsProgramVerbose" in text


def test_main_activity_reports_proot_hello_attempt_result():
    text = MAIN.read_text()
    assert "ROOTFS EXECUTION:" in text
    assert "proot hello quiet exit=" in text
    assert "proot hello quiet stdout=" in text
    assert "proot hello verbose on failure" in text
