from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]
PAYLOAD = ROOT / "app/src/main/assets/rootfs/payloads/tiny-rootfs.tar"
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"
RUNNER = ROOT / "app/src/main/java/dev/chanwoo/androlinux/NativeCommandRunner.kt"
CPP = ROOT / "app/src/main/cpp/runtime_report.cpp"


def test_rootfs_contains_guest_gpu_client():
    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./usr/bin/alr-gpu-client" in names
        member = archive.getmember("./usr/bin/alr-gpu-client")
        assert member.mode & 0o111
        payload = archive.extractfile("./usr/share/androlinux/gpu-bridge.txt").read().decode()
        assert "guest gpu bridge clear-color-v1" in payload


def test_android_runs_guest_gpu_client_and_reports_bridge_command():
    assert "runProotRootfsGuestGpuClient" in RUNNER.read_text()
    text = MAIN.read_text()
    assert "GUEST GPU BRIDGE COMMAND EXECUTION" in text
    assert "GUEST GPU BRIDGE SURFACE EXECUTION" in text
    assert "parseGuestGpuCommand" in text
    assert "ALR_GPU_CLEAR" in text
    assert "Linux guest-controlled GPU surface renderer" in text


def test_native_surface_renderer_accepts_guest_color_and_reports_bridge():
    text = CPP.read_text()
    assert "surface clear color=" in text
    assert "surface guest command tag=" in text
    assert "guest gpu bridge hardware render=" in text
    assert "glClearColor(red, green, blue" in text
