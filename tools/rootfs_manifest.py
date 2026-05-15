from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

_SAFE_NAME = re.compile(r"^[A-Za-z0-9._-]+$")
_SHA256 = re.compile(r"^[a-fA-F0-9]{64}$")


@dataclass(frozen=True)
class RootfsAsset:
    path: str
    sha256: str
    size_bytes: int

    def __post_init__(self) -> None:
        _validate_relative_asset_path(self.path)
        if not _SHA256.fullmatch(self.sha256):
            raise ValueError("sha256 must be 64 hex characters")
        if self.size_bytes < 0:
            raise ValueError("size_bytes must be non-negative")

    def verify_file(self, path: str | Path) -> bool:
        file_path = Path(path)
        if not file_path.is_file():
            return False
        if file_path.stat().st_size != self.size_bytes:
            return False
        digest = hashlib.sha256()
        with file_path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest().lower() == self.sha256.lower()


@dataclass(frozen=True)
class RootfsManifest:
    name: str
    version: str
    assets: list[RootfsAsset]

    def __post_init__(self) -> None:
        _validate_safe_name(self.name, "name")
        _validate_safe_name(self.version, "version")
        if not self.assets:
            raise ValueError("at least one rootfs asset is required")


@dataclass(frozen=True)
class RootfsInstallPlan:
    rootfs_dir: str
    marker_path: str
    asset_destinations: dict[str, str]


def plan_rootfs_install(manifest: RootfsManifest, *, app_files_dir: str) -> RootfsInstallPlan:
    files_dir = _normalize_absolute(app_files_dir)
    rootfs_base = PurePosixPath(files_dir) / "rootfs"
    rootfs_dir = rootfs_base / manifest.name
    download_dir = rootfs_base / ".downloads" / manifest.name / manifest.version
    marker_path = rootfs_dir / f".alr-installed-{manifest.version}"
    destinations = {
        asset.path: str(download_dir / asset.path)
        for asset in manifest.assets
    }
    return RootfsInstallPlan(
        rootfs_dir=str(rootfs_dir),
        marker_path=str(marker_path),
        asset_destinations=destinations,
    )


def _validate_safe_name(value: str, field: str) -> None:
    if not _SAFE_NAME.fullmatch(value):
        raise ValueError(f"{field} must contain only letters, numbers, dot, underscore, or dash")


def _validate_relative_asset_path(path: str) -> None:
    candidate = PurePosixPath(path)
    if candidate.is_absolute() or ".." in candidate.parts or path in {"", "."}:
        raise ValueError("asset path must be relative and safe")


def _normalize_absolute(path: str) -> str:
    if not path.startswith("/"):
        raise ValueError(f"absolute path required: {path}")
    return str(PurePosixPath(path))
