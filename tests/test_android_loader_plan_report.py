from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CPP = ROOT / "app/src/main/cpp/runtime_report.cpp"
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"


def test_jni_report_includes_loader_launch_plan():
    text = CPP.read_text()
    assert "build_loader_launch_plan" in text
    assert "loader argv:" in text
    assert "ALR_ROOTFS" in text
    assert "ALR_PROGRAM" in text


def test_main_activity_requests_tiny_rootfs_program():
    text = MAIN.read_text()
    assert '"/bin/hello"' in text
