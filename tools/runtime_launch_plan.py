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


@dataclass(frozen=True)
class OptionalRuntimeBackendProbe:
    framework_status: str
    available_status: str
    source: str
    backend: str
    candidate_path: str
    can_execute: bool
    reason: str


def probe_optional_runtime_backend(
    *,
    backend: str = "none",
    candidate_path: str = "",
) -> OptionalRuntimeBackendProbe:
    """Describe optional low-overhead runtime backend availability without executing it.

    This is intentionally absent-safe scaffolding: no external runtime is bundled,
    launched, or required. A later OSS/proroot-style backend can replace this with
    a real file/signature/smoke probe while preserving these report fields.
    """
    if not candidate_path:
        return OptionalRuntimeBackendProbe(
            framework_status="PASS",
            available_status="SKIP",
            source="none",
            backend="none",
            candidate_path="",
            can_execute=False,
            reason="no optional external runtime backend configured",
        )
    if not candidate_path.startswith("/"):
        raise ValueError("absolute optional runtime backend path required")
    return OptionalRuntimeBackendProbe(
        framework_status="PASS",
        available_status="PASS",
        source="external",
        backend=backend or "external",
        candidate_path=_normalize(candidate_path),
        can_execute=False,
        reason="external candidate declared for future probe integration; not executed by absent-safe scaffolding",
    )


def build_launch_plan(
    *,
    package_name: str,
    native_library_dir: str,
    app_files_dir: str,
    rootfs_name: str,
    program: str,
    backend: str = "loader",
) -> LaunchPlan:
    if not package_name:
        raise ValueError("package_name is required")
    if not program.startswith("/"):
        raise ValueError("program must be an absolute path inside the rootfs")

    native_dir = _normalize(native_library_dir)
    files_dir = _normalize(app_files_dir)
    rootfs_dir = _normalize(str(PurePosixPath(files_dir) / "rootfs" / rootfs_name))
    if backend == "loader":
        executable = _normalize(str(PurePosixPath(native_dir) / "libalr-loader.so"))
        argv = [
            executable,
            "--rootfs",
            rootfs_dir,
            "--program",
            program,
        ]
    elif backend == "proot":
        executable = _normalize(str(PurePosixPath(native_dir) / "libalr_proot.so"))
        argv = [
            executable,
            "-R",
            rootfs_dir,
            "-w",
            "/",
            program,
        ]
    else:
        raise ValueError(f"unknown launch backend: {backend}")

    env = {
        "ALR_PACKAGE": package_name,
        "ALR_ROOTFS": rootfs_dir,
        "ALR_PROGRAM": program,
        "ALR_BACKEND": backend,
        "HOME": "/root",
        "TMPDIR": "/tmp",
        "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    }
    if backend == "proot":
        env["PROOT_NO_SECCOMP"] = "1"
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
