from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "app/src/main/java/dev/chanwoo/androlinux/NativeCommandRunner.kt"
MAIN = ROOT / "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"


def test_native_command_runner_can_attempt_proot_rootfs_program():
    text = RUNNER.read_text()
    assert "runProotRootfsProgram" in text
    assert '"-R"' in text
    assert '"-w"' in text
    assert '"/"' in text
    assert "runProotRootfsProgramVerbose" in text


def test_main_activity_reports_proot_hello_attempt_result():
    text = MAIN.read_text()
    assert "ROOTFS EXECUTION:" in text
    assert "proot hello quiet exit=" in text
    assert "proot hello quiet stdout=" in text
    assert "proot hello verbose on failure" in text


def test_native_command_runner_can_attempt_proot_shell_command():
    text = RUNNER.read_text()
    assert "runProotRootfsShell" in text
    assert '"/bin/sh"' in text
    assert '"-c"' in text


def test_main_activity_reports_shell_and_script_smoke_results():
    text = MAIN.read_text()
    assert "SHELL SCRIPT EXECUTION:" in text
    assert "proot script exit=" in text
    assert "proot script stdout=" in text
    assert "proot shell -c exit=" in text
    assert "proot shell -c stdout=" in text


def test_main_activity_reports_glibc_dynamic_smoke_result():
    text = MAIN.read_text()
    assert "GLIBC DYNAMIC EXECUTION:" in text
    assert "rootfs /bin/glibc-hello exists=" in text
    assert "rootfs glibc loader exists=" in text
    assert "rootfs libc exists=" in text
    assert "proot glibc exit=" in text
    assert "proot glibc stdout=" in text
    assert "proot glibc stderr=" in text


def test_native_command_runner_can_attempt_proot_dash_command():
    text = RUNNER.read_text()
    assert "runProotRootfsDash" in text
    assert '"/bin/dash"' in text
    assert '"-c"' in text


def test_main_activity_reports_real_distro_userland_smoke_result():
    text = MAIN.read_text()
    assert "DISTRO USERLAND EXECUTION:" in text
    assert "rootfs /bin/dash exists=" in text
    assert "rootfs /usr/bin/env exists=" in text
    assert "proot dash exit=" in text
    assert "proot dash stdout=" in text
    assert "proot dash stderr=" in text


def test_native_command_runner_clears_inherited_android_environment_before_launch():
    text = RUNNER.read_text()
    assert "processBuilder.environment().clear()" in text
    assert "processBuilder.environment().putAll(environment)" in text


def test_main_activity_reports_clean_guest_environment_smoke():
    text = MAIN.read_text()
    assert "CLEAN GUEST ENVIRONMENT:" in text
    assert "guest env leaked android vars=" in text
    assert "ANDROID_ROOT=" in text
    assert "BOOTCLASSPATH=" in text
    assert "DEX2OATBOOTCLASSPATH=" in text


def test_native_command_runner_can_attempt_proot_root_identity_command():
    text = RUNNER.read_text()
    assert "runProotRootfsProgramAsRoot" in text
    assert '"-0"' in text
    assert '"/usr/bin/id"' in text


def test_main_activity_reports_identity_nss_smoke_result():
    text = MAIN.read_text()
    assert "IDENTITY NSS EXECUTION:" in text
    assert "rootfs /etc/passwd exists=" in text
    assert "rootfs /etc/group exists=" in text
    assert "rootfs /etc/nsswitch.conf exists=" in text
    assert "rootfs /usr/bin/id exists=" in text
    assert "rootfs libselinux exists=" in text
    assert "rootfs libpcre2 exists=" in text
    assert "rootfs libnss_files exists=" in text
    assert "identity numeric root=" in text
    assert "identity named root=" in text
    assert "proot id exit=" in text
    assert "proot id stdout=" in text
    assert "proot id stderr=" in text
