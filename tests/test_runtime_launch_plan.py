import pytest

from tools.runtime_launch_plan import LaunchPlan, build_launch_plan


def test_launch_plan_executes_only_from_package_native_lib_dir():
    plan = build_launch_plan(
        package_name="dev.chanwoo.androlinux",
        native_library_dir="/data/app/~~token/dev.chanwoo.androlinux-abc/lib/arm64",
        app_files_dir="/data/user/0/dev.chanwoo.androlinux/files",
        rootfs_name="debian-arm64",
        program="/bin/bash",
    )

    assert isinstance(plan, LaunchPlan)
    assert plan.executable == "/data/app/~~token/dev.chanwoo.androlinux-abc/lib/arm64/libalr-loader.so"
    assert plan.rootfs_dir == "/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64"
    assert plan.argv == [
        "/data/app/~~token/dev.chanwoo.androlinux-abc/lib/arm64/libalr-loader.so",
        "--rootfs",
        "/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64",
        "--program",
        "/bin/bash",
    ]


def test_launch_plan_rejects_writable_app_data_executable():
    with pytest.raises(ValueError, match="executable must live in native_library_dir"):
        LaunchPlan(
            executable="/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64/bin/bash",
            native_library_dir="/data/app/~~token/dev.chanwoo.androlinux-abc/lib/arm64",
            rootfs_dir="/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64",
            argv=["/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64/bin/bash"],
            env={},
        )


def test_launch_plan_env_points_tools_inside_rootfs_without_claiming_chroot():
    plan = build_launch_plan(
        package_name="dev.chanwoo.androlinux",
        native_library_dir="/data/app/~~token/dev.chanwoo.androlinux-abc/lib/arm64",
        app_files_dir="/data/user/0/dev.chanwoo.androlinux/files",
        rootfs_name="debian-arm64",
        program="/usr/bin/python3",
    )

    assert plan.env["ALR_ROOTFS"] == "/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64"
    assert plan.env["ALR_PROGRAM"] == "/usr/bin/python3"
    assert plan.env["PATH"] == "/bin:/usr/bin:/usr/local/bin"
    assert "CHROOT" not in plan.env
