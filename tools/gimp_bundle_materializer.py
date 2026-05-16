from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path


DEFAULT_MIRROR = "https://deb.debian.org/debian"
DEFAULT_LOCK = Path("rootfs/gimp-demo-bundle.lock.json")
DEFAULT_INPUT_ROOTFS = Path("rootfs/tiny-rootfs.tar")
DEFAULT_OUTPUT_ROOTFS = Path("rootfs/tiny-rootfs.tar")
DEFAULT_ANDROID_ASSET = Path("app/src/main/assets/rootfs/payloads/tiny-rootfs.tar")
DEFAULT_DOWNLOAD_DIR = Path(".cache/debian-gimp-bookworm-arm64")
DEFAULT_WORK_DIR = Path(".cache/gimp-rootfs-materialize")
DEFAULT_PROFILE = Path("rootfs/gimp-demo-profile.json")
DEFAULT_LAUNCHER = Path("rootfs/guest-src/gui/alr_package_gimp_demo.sh")

PRUNE_PREFIXES = (
    "usr/share/doc/",
    "usr/share/man/",
    "usr/share/info/",
    "usr/share/lintian/",
    "usr/share/bug/",
    "usr/share/locale/",
)

PRESERVE_FROM_BASE_ROOTFS = (
    "usr/bin/dpkg-deb",
)


@dataclass(frozen=True)
class LockedPackage:
    name: str
    version: str
    architecture: str
    filename: str
    size: int
    sha256: str

    @property
    def url(self) -> str:
        return f"{DEFAULT_MIRROR}/{self.filename}"

    @property
    def deb_name(self) -> str:
        return Path(self.filename).name


def load_locked_packages(lock_path: Path) -> list[LockedPackage]:
    lock = json.loads(lock_path.read_text())
    packages = []
    for record in lock["packages"]:
        packages.append(
            LockedPackage(
                name=record["package"],
                version=record["version"],
                architecture=record["architecture"],
                filename=record["filename"],
                size=int(record["size"]),
                sha256=record["sha256"],
            )
        )
    return packages


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_package(package: LockedPackage, download_dir: Path) -> Path:
    download_dir.mkdir(parents=True, exist_ok=True)
    deb_path = download_dir / package.deb_name
    if deb_path.is_file() and deb_path.stat().st_size == package.size and sha256_file(deb_path) == package.sha256:
        return deb_path
    tmp_path = deb_path.with_suffix(deb_path.suffix + ".partial")
    with urllib.request.urlopen(package.url, timeout=120) as response, tmp_path.open("wb") as output:
        shutil.copyfileobj(response, output)
    if tmp_path.stat().st_size != package.size:
        raise RuntimeError(f"{package.name}: size mismatch, got {tmp_path.stat().st_size}, expected {package.size}")
    digest = sha256_file(tmp_path)
    if digest != package.sha256:
        raise RuntimeError(f"{package.name}: sha256 mismatch, got {digest}, expected {package.sha256}")
    tmp_path.replace(deb_path)
    return deb_path


def iter_ar_members(path: Path) -> list[tuple[str, bytes]]:
    data = path.read_bytes()
    if not data.startswith(b"!<arch>\n"):
        raise RuntimeError(f"{path} is not a Debian ar archive")
    offset = 8
    members: list[tuple[str, bytes]] = []
    while offset + 60 <= len(data):
        header = data[offset : offset + 60]
        raw_name = header[:16].decode("utf-8", "replace").strip()
        name = raw_name.removesuffix("/")
        size_text = header[48:58].decode("ascii", "replace").strip()
        if not size_text:
            break
        size = int(size_text)
        start = offset + 60
        end = start + size
        members.append((name, data[start:end]))
        offset = end + (size % 2)
    return members


def format_ar_member(name: str, payload: bytes) -> bytes:
    encoded_name = f"{name}/".encode("ascii")
    if len(encoded_name) > 16:
        raise RuntimeError(f"ar member name too long: {name}")
    header = (
        encoded_name.ljust(16, b" ")
        + b"0".ljust(12, b" ")
        + b"0".ljust(6, b" ")
        + b"0".ljust(6, b" ")
        + b"100644".ljust(8, b" ")
        + str(len(payload)).encode("ascii").ljust(10, b" ")
        + b"`\n"
    )
    padding = b"\n" if len(payload) % 2 else b""
    return header + payload + padding


def build_ar_archive(members: list[tuple[str, bytes]]) -> bytes:
    return b"!<arch>\n" + b"".join(format_ar_member(name, payload) for name, payload in members)


def extract_tar_payload(payload: bytes, member_name: str, destination: Path) -> None:
    suffix = "".join(Path(member_name).suffixes)
    with tempfile.NamedTemporaryFile(suffix=suffix) as tmp:
        tmp.write(payload)
        tmp.flush()
        if member_name.endswith(".tar.zst"):
            if shutil.which("zstd") is None:
                raise RuntimeError("zstd is required to extract .deb data.tar.zst payloads")
            with subprocess.Popen(["zstd", "-dc", tmp.name], stdout=subprocess.PIPE) as zstd:
                assert zstd.stdout is not None
                subprocess.run(["tar", "-xf", "-", "-C", str(destination)], stdin=zstd.stdout, check=True)
                if zstd.wait() != 0:
                    raise RuntimeError(f"zstd failed while extracting {member_name}")
            return
        if member_name.endswith(".tar.xz"):
            mode = "r:xz"
        elif member_name.endswith(".tar.gz"):
            mode = "r:gz"
        else:
            mode = "r:"
        with tarfile.open(tmp.name, mode) as archive:
            extract_tar_members(archive, destination)


def extract_tar_members(archive: tarfile.TarFile, destination: Path) -> None:
    root = destination.resolve()
    for member in archive.getmembers():
        name = member.name.removeprefix("./")
        if not name or name == ".":
            continue
        relative = Path(name)
        if relative.is_absolute() or ".." in relative.parts:
            raise RuntimeError(f"refusing unsafe tar member path: {member.name}")
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        resolved_parent = target.parent.resolve()
        if not resolved_parent.is_relative_to(root):
            raise RuntimeError(f"refusing tar member outside rootfs: {member.name}")
        if member.isdir():
            if target.exists() and not target.is_dir():
                target.unlink()
            target.mkdir(parents=True, exist_ok=True)
            target.chmod(member.mode & 0o777)
        elif member.isreg():
            if target.exists() or target.is_symlink():
                if target.is_dir() and not target.is_symlink():
                    shutil.rmtree(target)
                else:
                    target.unlink()
            source = archive.extractfile(member)
            if source is None:
                raise RuntimeError(f"tar regular file has no payload: {member.name}")
            with source, target.open("wb") as output:
                shutil.copyfileobj(source, output)
            target.chmod(member.mode & 0o777)
        elif member.issym():
            if target.exists() or target.is_symlink():
                if target.is_dir() and not target.is_symlink():
                    shutil.rmtree(target)
                else:
                    target.unlink()
            os.symlink(member.linkname, target)
        elif member.islnk():
            link_target = Path(member.linkname.removeprefix("./"))
            if link_target.is_absolute() or ".." in link_target.parts:
                raise RuntimeError(f"refusing unsafe hardlink target: {member.name} -> {member.linkname}")
            source = destination / link_target
            if source.exists():
                if target.exists() or target.is_symlink():
                    if target.is_dir() and not target.is_symlink():
                        shutil.rmtree(target)
                    else:
                        target.unlink()
                os.link(source, target)
        else:
            continue


def make_data_tar_gz(source_dir: Path) -> bytes:
    buffer = io.BytesIO()
    with gzip.GzipFile(fileobj=buffer, mode="wb", mtime=0) as gzip_file:
        with tarfile.open(fileobj=gzip_file, mode="w") as archive:
            for path in sorted(source_dir.rglob("*")):
                arcname = "./" + path.relative_to(source_dir).as_posix()
                archive.add(path, arcname=arcname, recursive=False)
    return buffer.getvalue()


def extract_deb(deb_path: Path, destination: Path) -> None:
    data_member: tuple[str, bytes] | None = None
    for name, payload in iter_ar_members(deb_path):
        if name.startswith("data.tar"):
            data_member = (name, payload)
            break
    if data_member is None:
        raise RuntimeError(f"{deb_path} has no data.tar payload")
    extract_tar_payload(data_member[1], data_member[0], destination)


def prune_rootfs(rootfs_dir: Path) -> None:
    for prefix in PRUNE_PREFIXES:
        target = rootfs_dir / prefix
        if target.exists():
            shutil.rmtree(target)


def write_minimal_dpkg_status(rootfs_dir: Path, packages: list[LockedPackage]) -> None:
    status_path = rootfs_dir / "var/lib/dpkg/status"
    status_path.parent.mkdir(parents=True, exist_ok=True)
    existing = status_path.read_text(errors="replace") if status_path.exists() else ""
    existing_names = {
        line.split(":", 1)[1].strip()
        for line in existing.splitlines()
        if line.startswith("Package:")
    }
    additions = []
    for package in packages:
        if package.name in existing_names:
            continue
        additions.append(
            "\n".join(
                [
                    f"Package: {package.name}",
                    "Status: install ok installed",
                    f"Version: {package.version}",
                    f"Architecture: {package.architecture}",
                    "Description: Plib materialized GIMP demo dependency",
                    "",
                ]
            )
        )
    if additions:
        suffix = "\n" if existing and not existing.endswith("\n") else ""
        status_path.write_text(existing + suffix + "\n".join(additions) + "\n")


def write_materialized_marker(rootfs_dir: Path, packages: list[LockedPackage]) -> None:
    marker = rootfs_dir / "usr/share/androlinux/gimp-demo-materialized.txt"
    marker.parent.mkdir(parents=True, exist_ok=True)
    total = sum(package.size for package in packages)
    marker.write_text(
        "\n".join(
            [
                "ALR_GIMP_DEMO_MATERIALIZED=true",
                f"package_count={len(packages)}",
                f"download_size_bytes={total}",
                "target=/usr/bin/gimp",
                "suite=bookworm",
                "architecture=arm64",
                "",
            ]
        )
    )


def verify_debian_libc(rootfs_dir: Path) -> None:
    libc = rootfs_dir / "lib/aarch64-linux-gnu/libc.so.6"
    if not libc.is_file():
        raise RuntimeError("materialized rootfs is missing /lib/aarch64-linux-gnu/libc.so.6")
    if libc.stat().st_size < 1_000_000:
        raise RuntimeError(f"materialized libc is unexpectedly small: {libc.stat().st_size} bytes")
    payload = libc.read_bytes()
    for version in (b"GLIBC_2.33", b"GLIBC_2.34", b"GLIBC_2.36"):
        if version not in payload:
            raise RuntimeError(f"materialized libc is missing required symbol version marker {version.decode()}")


def capture_preserved_files(rootfs_dir: Path) -> dict[str, bytes]:
    preserved: dict[str, bytes] = {}
    for relative in PRESERVE_FROM_BASE_ROOTFS:
        path = rootfs_dir / relative
        if path.is_file():
            preserved[relative] = path.read_bytes()
    return preserved


def restore_preserved_files(rootfs_dir: Path, preserved: dict[str, bytes]) -> None:
    for relative, payload in preserved.items():
        path = rootfs_dir / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        mode = path.stat().st_mode & 0o777 if path.exists() else 0o755
        path.write_bytes(payload)
        path.chmod(mode)


def patch_android_dpkg_deb(rootfs_dir: Path) -> None:
    path = rootfs_dir / "usr/bin/dpkg-deb"
    if not path.is_file():
        return
    payload = path.read_bytes()
    old = b"--warning=no-timestamp"
    new = b"--warning=none" + b"\0" * (len(old) - len(b"--warning=none"))
    if old in payload:
        path.write_bytes(payload.replace(old, new))
        path.chmod(0o755)


def materialize_unsafe_symlinks(rootfs_dir: Path) -> None:
    root = rootfs_dir.resolve()
    for path in sorted(rootfs_dir.rglob("*")):
        if not path.is_symlink():
            continue
        target = os.readlink(path)
        target_parts = Path(target).parts
        if not target.startswith("/") and ".." not in target_parts:
            continue
        if target.startswith("/"):
            target_inside_rootfs = rootfs_dir / target.lstrip("/")
        else:
            target_inside_rootfs = path.parent / target
        try:
            resolved_target = target_inside_rootfs.resolve()
            if not resolved_target.is_relative_to(root):
                path.unlink()
                path.write_bytes(b"")
                continue
        except FileNotFoundError:
            path.unlink()
            path.write_bytes(b"")
            continue
        path.unlink()
        if resolved_target.is_dir():
            path.mkdir(parents=True, exist_ok=True)
        elif resolved_target.is_file():
            shutil.copy2(resolved_target, path)
        else:
            path.write_bytes(b"")


def overlay_plib_gimp_artifacts(rootfs_dir: Path, args: argparse.Namespace) -> None:
    profile_target = rootfs_dir / "usr/share/androlinux/gimp-demo-profile.json"
    lock_target = rootfs_dir / "usr/share/androlinux/gimp-demo-bundle.lock.json"
    launcher_target = rootfs_dir / "usr/local/bin/alr-package-gimp-demo"
    profile_target.parent.mkdir(parents=True, exist_ok=True)
    launcher_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.profile, profile_target)
    shutil.copy2(args.lock, lock_target)
    shutil.copy2(args.launcher, launcher_target)
    launcher_target.chmod(0o755)


def update_embedded_smoke_deb(rootfs_dir: Path, args: argparse.Namespace) -> None:
    deb_path = rootfs_dir / "var/cache/apt/archives/alr-smoke_1.0_arm64.deb"
    if not deb_path.is_file():
        return
    members = iter_ar_members(deb_path)
    rewritten_members: list[tuple[str, bytes]] = []
    with tempfile.TemporaryDirectory(prefix="alr-smoke-deb-", dir=args.work_dir) as tmp_dir:
        data_dir = Path(tmp_dir) / "data"
        data_dir.mkdir()
        data_name = ""
        for name, payload in members:
            if name.startswith("data.tar"):
                data_name = name
                extract_tar_payload(payload, name, data_dir)
                break
        if not data_name:
            raise RuntimeError(f"{deb_path} has no data.tar payload")
        shutil.copy2(args.launcher, data_dir / "usr/local/bin/alr-package-gimp-demo")
        (data_dir / "usr/local/bin/alr-package-gimp-demo").chmod(0o755)
        shutil.copy2(args.profile, data_dir / "usr/share/androlinux/gimp-demo-profile.json")
        shutil.copy2(args.lock, data_dir / "usr/share/androlinux/gimp-demo-bundle.lock.json")
        shutil.copy2(rootfs_dir / "usr/share/androlinux/gimp-demo-materialized.txt", data_dir / "usr/share/androlinux/gimp-demo-materialized.txt")
        new_data = make_data_tar_gz(data_dir)
        for name, payload in members:
            if name.startswith("data.tar"):
                rewritten_members.append(("data.tar.gz", new_data))
            else:
                rewritten_members.append((name, payload))
    deb_path.write_bytes(build_ar_archive(rewritten_members))


def materialize(args: argparse.Namespace) -> None:
    packages = load_locked_packages(args.lock)
    if not packages:
        raise RuntimeError("lock contains no packages")

    args.work_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="rootfs-", dir=args.work_dir) as tmp_dir:
        rootfs_dir = Path(tmp_dir) / "rootfs"
        rootfs_dir.mkdir()
        with tarfile.open(args.input_rootfs) as archive:
            archive.extractall(rootfs_dir)
        preserved = capture_preserved_files(rootfs_dir)
        for index, package in enumerate(packages, 1):
            deb_path = download_package(package, args.download_dir)
            print(f"[{index:03d}/{len(packages):03d}] extracting {package.name} {package.version}")
            extract_deb(deb_path, rootfs_dir)
        restore_preserved_files(rootfs_dir, preserved)
        patch_android_dpkg_deb(rootfs_dir)
        if args.prune:
            prune_rootfs(rootfs_dir)
        materialize_unsafe_symlinks(rootfs_dir)
        verify_debian_libc(rootfs_dir)
        write_minimal_dpkg_status(rootfs_dir, packages)
        write_materialized_marker(rootfs_dir, packages)
        overlay_plib_gimp_artifacts(rootfs_dir, args)
        update_embedded_smoke_deb(rootfs_dir, args)
        args.output_rootfs.parent.mkdir(parents=True, exist_ok=True)
        with tarfile.open(args.output_rootfs, "w") as archive:
            archive.add(rootfs_dir, arcname=".")

    if args.android_asset is not None:
        args.android_asset.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(args.output_rootfs, args.android_asset)
    print(f"wrote {args.output_rootfs} sha256={sha256_file(args.output_rootfs)} size={args.output_rootfs.stat().st_size}")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Download and materialize the locked Debian arm64 GIMP demo bundle into the Plib rootfs tar.")
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK)
    parser.add_argument("--input-rootfs", type=Path, default=DEFAULT_INPUT_ROOTFS)
    parser.add_argument("--output-rootfs", type=Path, default=DEFAULT_OUTPUT_ROOTFS)
    parser.add_argument("--android-asset", type=Path, default=DEFAULT_ANDROID_ASSET)
    parser.add_argument("--download-dir", type=Path, default=DEFAULT_DOWNLOAD_DIR)
    parser.add_argument("--work-dir", type=Path, default=DEFAULT_WORK_DIR)
    parser.add_argument("--profile", type=Path, default=DEFAULT_PROFILE)
    parser.add_argument("--launcher", type=Path, default=DEFAULT_LAUNCHER)
    parser.add_argument("--no-prune", dest="prune", action="store_false")
    args = parser.parse_args(argv)
    materialize(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
