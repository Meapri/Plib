from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import PurePosixPath


@dataclass(frozen=True)
class LaunchPlan:
    executable: str
    native_library_dir: str
    rootfs_dir: str
    argv: list[str]
    env: dict[str, str] = field(default_factory=dict)

    def __post_init__(self) -> None:
        native_dir = _normalize(self.native_library_dir)
        executable = _normalize(self.executable)
        if not executable.startswith(native_dir + "/"):
            raise ValueError("executable must live in native_library_dir")


def build_launch_plan(
    *,
    package_name: str,
    native_library_dir: str,
    app_files_dir: str,
    rootfs_name: str,
    program: str,
) -> LaunchPlan:
    if not package_name:
        raise ValueError("package_name is required")
    if not program.startswith("/"):
        raise ValueError("program must be an absolute path inside the rootfs")

    native_dir = _normalize(native_library_dir)
    files_dir = _normalize(app_files_dir)
    rootfs_dir = _normalize(str(PurePosixPath(files_dir) / "rootfs" / rootfs_name))
    executable = _normalize(str(PurePosixPath(native_dir) / "libalr-loader.so"))

    argv = [
        executable,
        "--rootfs",
        rootfs_dir,
        "--program",
        program,
    ]
    env = {
        "ALR_PACKAGE": package_name,
        "ALR_ROOTFS": rootfs_dir,
        "ALR_PROGRAM": program,
        "HOME": "/root",
        "TMPDIR": "/tmp",
        "PATH": "/bin:/usr/bin:/usr/local/bin",
    }
    return LaunchPlan(
        executable=executable,
        native_library_dir=native_dir,
        rootfs_dir=rootfs_dir,
        argv=argv,
        env=env,
    )


def _normalize(path: str) -> str:
    if not path.startswith("/"):
        raise ValueError(f"absolute path required: {path}")
    return str(PurePosixPath(path))
