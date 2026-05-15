from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"
PAYLOAD = ROOT / "app" / "src" / "main" / "assets" / "rootfs" / "payloads" / "tiny-rootfs.tar"


def test_android_assets_include_rootfs_manifest_and_payload():
    assert MANIFEST.is_file()
    assert PAYLOAD.is_file()
    assert PAYLOAD.stat().st_size == 542720


def test_android_asset_manifest_matches_host_manifest():
    host_manifest = ROOT / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"
    assert MANIFEST.read_text() == host_manifest.read_text()


def test_android_asset_payload_matches_host_payload():
    host_payload = ROOT / "rootfs" / "tiny-rootfs.tar"
    assert PAYLOAD.read_bytes() == host_payload.read_bytes()


def test_tiny_rootfs_hello_is_static_arm64_elf_not_shell_script():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        member = archive.extractfile("./bin/hello")
        assert member is not None
        blob = member.read(4)
    assert blob == b"\x7fELF"


def test_tiny_rootfs_does_not_depend_on_missing_bin_sh_for_hello():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
    assert "./bin/hello" in names
    assert "./bin/sh" not in names
