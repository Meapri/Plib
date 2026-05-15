from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"
PAYLOAD = ROOT / "app" / "src" / "main" / "assets" / "rootfs" / "payloads" / "tiny-rootfs.tar"


def test_android_assets_include_rootfs_manifest_and_payload():
    assert MANIFEST.is_file()
    assert PAYLOAD.is_file()
    assert PAYLOAD.stat().st_size == 10240


def test_android_asset_manifest_matches_host_manifest():
    host_manifest = ROOT / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"
    assert MANIFEST.read_text() == host_manifest.read_text()


def test_android_asset_payload_matches_host_payload():
    host_payload = ROOT / "rootfs" / "tiny-rootfs.tar"
    assert PAYLOAD.read_bytes() == host_payload.read_bytes()
