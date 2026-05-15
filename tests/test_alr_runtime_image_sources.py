from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
IMAGE_HPP = ROOT / "app/src/main/cpp/alr_runtime/alr_image.hpp"
IMAGE_CPP = ROOT / "app/src/main/cpp/alr_runtime/alr_image.cpp"
ELF_HPP = ROOT / "app/src/main/cpp/alr_runtime/alr_elf.hpp"
CMAKE = ROOT / "app/src/main/cpp/CMakeLists.txt"
NATIVE_SCRIPT = ROOT / "scripts/test-native-core.sh"
NATIVE_TEST = ROOT / "tests/native_alr_runtime_image_test.cpp"


def test_static_image_plan_api_exists():
    header = IMAGE_HPP.read_text()
    source = IMAGE_CPP.read_text()
    assert "struct StaticImageSegmentMap" in header
    assert "struct StaticImagePlan" in header
    assert "build_static_image_plan" in header
    assert "load_static_image_for_preflight" in header
    assert "ALR STATIC IMAGE MAP PLAN: " in source
    assert "ALR STATIC IMAGE ENTRY READY: " in source
    assert "ALR STATIC IMAGE LOAD PREFLIGHT: " in source
    assert "PT_LOAD file offset and vaddr page alignment differ" in source
    assert "mprotect static image segment" in source


def test_elf_load_plan_preserves_segments():
    header = ELF_HPP.read_text()
    assert "std::vector<ElfLoadSegment> load_segments" in header


def test_static_image_plan_is_built_and_host_tested():
    cmake = CMAKE.read_text()
    script = NATIVE_SCRIPT.read_text()
    assert "alr_runtime/alr_image.cpp" in cmake
    assert "native_alr_runtime_image_test.cpp" in script
    assert "alr-native-runtime-image-test" in script
    assert "ALR STATIC IMAGE ENTRY READY: PASS" in NATIVE_TEST.read_text()
