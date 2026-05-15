from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CMAKE = ROOT / "app/src/main/cpp/CMakeLists.txt"
TRAMPOLINE_MAIN = ROOT / "app/src/main/cpp/alr_runtime_trampoline.cpp"
GRADLE = ROOT / "app/build.gradle.kts"
LAUNCH_CPP = ROOT / "app/src/main/cpp/alr_runtime/alr_launch.cpp"
TRAMPOLINE_HPP = ROOT / "app/src/main/cpp/alr_runtime/alr_trampoline.hpp"
TRAMPOLINE_CPP = ROOT / "app/src/main/cpp/alr_runtime/alr_trampoline.cpp"
TRAMPOLINE_MAIN = ROOT / "app/src/main/cpp/alr_runtime_trampoline.cpp"
NATIVE_SCRIPT = ROOT / "scripts/test-native-core.sh"
NATIVE_TEST = ROOT / "tests/native_alr_runtime_trampoline_test.cpp"
PLAN_CPP = ROOT / "app/src/main/cpp/runtime_plan.cpp"


def test_packaged_trampoline_is_declared_and_packaged():
    cmake = CMAKE.read_text()
    gradle = GRADLE.read_text()
    assert "alr_runtime/alr_trampoline.cpp" in cmake
    assert "add_executable(alr_runtime_trampoline" in cmake
    assert 'OUTPUT_NAME "alr-runtime-trampoline"' in cmake
    assert "libalr_runtime_trampoline.so" in gradle
    assert "target_link_libraries(alr_runtime_trampoline PRIVATE alr_runtime)" in cmake


def test_trampoline_report_contract_exists():
    header = TRAMPOLINE_HPP.read_text()
    source = TRAMPOLINE_CPP.read_text()
    main = TRAMPOLINE_MAIN.read_text()
    for line in [
        "ALR TRAMPOLINE AVAILABLE: ",
        "ALR TRAMPOLINE CONFIG HANDOFF: ",
        "ALR TRAMPOLINE POLICY PREFLIGHT: ",
        "ALR STATIC HELLO VIA TRAMPOLINE: ",
        "alr trampoline path=",
        "alr trampoline exit=",
    ]:
        assert line in source
    assert "TrampolineAttemptPolicy" in header
    assert "ALR TRAMPOLINE PREFLIGHT: PASS" in main
    assert "ALR_TRAMPOLINE_TARGET_HOST_PATH" in source
    assert "build_static_image_plan" in source
    assert "load_static_image_for_preflight" in main
    assert "ALR_TRAMPOLINE_EXECUTE_ENTRY" in main
    assert "ALR_TRAMPOLINE_REPEAT_COUNT" in main
    assert "ALR_TRAMPOLINE_EXTRA_ARG_COUNT" in main
    assert "maybe_run_static_entry_handoff" in main
    assert "ALR STATIC ENTRY HANDOFF BENCHMARK:" in main
    assert "alr handoff repeat average elapsed ms=" in main
    handoff = (ROOT / "app/src/main/cpp/alr_runtime/alr_handoff.cpp").read_text()
    assert "ALR STATIC ENTRY HANDOFF:" in handoff
    assert "alr handoff stdout=" in handoff
    assert "pipe static entry handoff stdout" in handoff
    assert '"mov x16, x0\\n"' in handoff
    assert '"mov x0, xzr\\n"' in handoff
    assert "PTRACE_TRACEME" in handoff
    assert "alr handoff fault pc=" in handoff
    assert "alr handoff elapsed ms=" in handoff
    assert "::usleep(1000)" in handoff
    assert "emulate_android_seccomp_syscall" in handoff
    assert "alr handoff syscall emulated count=" in handoff
    assert "syscall_number == 144" in handoff
    image = (ROOT / "app/src/main/cpp/alr_runtime/alr_image.cpp").read_text()
    transfer = (ROOT / "app/src/main/cpp/alr_runtime/alr_transfer.cpp").read_text()
    entry = (ROOT / "app/src/main/cpp/alr_runtime/alr_entry.cpp").read_text()
    assert "alr image fixed vaddr required=" in image
    assert "elf_plan.type == \"dyn\"" in image
    assert "alr transfer image load bias=" in transfer
    assert "map_entry_stack_for_transfer(entry_plan, image_load_bias)" in transfer
    assert "runtime_entry_vaddr" in entry


def test_launch_report_and_runtime_plan_include_trampoline():
    launch = LAUNCH_CPP.read_text()
    plan = PLAN_CPP.read_text()
    assert "attempt_packaged_trampoline" in launch
    assert "trampoline.report" in launch
    assert "ALR STATIC IMAGE MAP PLAN: SKIP" in launch
    assert "ALR_TRAMPOLINE_PATH" in plan
    assert "alr runtime trampoline path=" in plan


def test_trampoline_has_native_coverage():
    script = NATIVE_SCRIPT.read_text()
    test = NATIVE_TEST.read_text()
    assert "native_alr_runtime_trampoline_test.cpp" in script
    assert "alr_runtime/alr_trampoline.cpp" in script
    assert "alr-native-runtime-trampoline-test" in script
    assert "ALR STATIC HELLO VIA TRAMPOLINE: SKIP" in test
