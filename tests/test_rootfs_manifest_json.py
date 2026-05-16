import json
from pathlib import Path

from tools.rootfs_manifest import RootfsManifest, load_rootfs_manifest


ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"


def test_load_rootfs_manifest_from_json_sample():
    manifest = load_rootfs_manifest(SAMPLE)

    assert isinstance(manifest, RootfsManifest)
    assert manifest.name == "debian-arm64"
    assert manifest.version == "bookworm-slim-2026-05-wayland-ahb-dirty-v95"
    assert manifest.assets[0].path == "tiny-rootfs.tar"
    assert manifest.assets[0].sha256 == "20493ea66546a74bfe79e384b7461f25891fa0474ab9fc6ea8226128873b75ac"
    assert manifest.assets[0].size_bytes == 35293184


def test_sample_manifest_contains_explicit_unsupported_features():
    data = json.loads(SAMPLE.read_text())

    assert "unsupported_features" in data
    assert "systemd" in data["unsupported_features"]
    assert "docker" in data["unsupported_features"]
    assert "real_chroot" in data["unsupported_features"]
