from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"
RUNNER = ROOT / "app/src/main/java/dev/chanwoo/androlinux/NativeCommandRunner.kt"
CPP_REPORT = ROOT / "app/src/main/cpp/runtime_report.cpp"


def test_runner_has_timeout_for_crash_or_hang_probes():
    text = RUNNER.read_text()
    assert "COMMAND_TIMEOUT_SECONDS" in text
    assert "waitFor(COMMAND_TIMEOUT_SECONDS" in text
    assert "destroyForcibly" in text


def test_runner_probes_proot_with_multiple_entrypoints():
    text = RUNNER.read_text()
    assert "runProotViaLinkerVersionProbe" in text
    assert "runProotNoEnvVersionProbe" in text
    assert "runProotLoaderDirectProbe" in text
    assert "runTallocViaLinkerProbe" in text
    assert 'File("/system/bin/linker64")' in text


def test_activity_reports_segv_probe_summary():
    text = MAIN.read_text()
    assert "build: 0.3.3-unstripped-proot" in text
    assert "probe dlopen talloc" in text
    assert "linker64 proot --version exit" in text
    assert "loader direct exit" in text


def test_native_report_has_dlopen_probe_for_talloc_and_proot():
    text = CPP_REPORT.read_text()
    assert "nativeLibraryProbe" in text
    assert "append_dlopen_probe(out, join_path(dir, \"libtalloc.so\")" in text
    assert "talloc_version_major" in text
    assert "append_dlopen_probe(out, join_path(dir, \"libalr_proot.so\")" in text
