import json
from pathlib import Path

from tools.rootfs_manifest import RootfsManifest, load_rootfs_manifest


ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"


def test_load_rootfs_manifest_from_json_sample():
    manifest = load_rootfs_manifest(SAMPLE)

    assert isinstance(manifest, RootfsManifest)
    assert manifest.name == "debian-arm64"
    assert manifest.version == "trixie-slim-2026-05-gimp3-wayland-v104"
    assert manifest.assets[0].path == "tiny-rootfs.tar"
    assert manifest.assets[0].sha256 == "9ed659c149510393662754f2508805f84edef5721a49539c26fe820481fcd75e"
    assert manifest.assets[0].size_bytes == 1365166080


def test_sample_manifest_contains_explicit_unsupported_features():
    data = json.loads(SAMPLE.read_text())

    assert "unsupported_features" in data
    assert "systemd" in data["unsupported_features"]
    assert "docker" in data["unsupported_features"]
    assert "real_chroot" in data["unsupported_features"]
