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


def test_main_activity_reports_alr_loader_help_probe():
    text = MAIN.read_text()
    runner = RUNNER.read_text()
    assert "runAlrRuntimeTrampolineLoaderHelpProbe" in runner
    assert "runAlrRuntimeTrampolineGlibcHelloProbe" in runner
    assert "runAlrRuntimeTrampolineCatOsReleaseProbe" in runner
    assert "runAlrRuntimeTrampolineEntryBenchmark" in runner
    assert "runAlrRuntimeTrampolineGlibcHelloBenchmark" in runner
    assert "runProotRootfsHelloLoopBenchmark" in runner
    assert "runProotRootfsGlibcHelloLoopBenchmark" in runner
    assert "runNativeBionicForkBenchmark" in runner
    assert "runAlrRuntimeTrampolineSyscallBench" in runner
    assert "runAlrRuntimeTrampolineSyscallBenchPreload" in runner
    assert "runProotRootfsSyscallBench" in runner
    assert "translateGuestPath" in runner
    assert "ALR_TRAMPOLINE_REPEAT_COUNT" in runner
    assert '"/lib/ld-linux-aarch64.so.1"' in runner
    assert '"--help"' in runner
    assert '"--library-path"' in runner
    assert '"--argv0"' in runner
    assert '"/bin/glibc-hello"' in runner
    assert '"/etc/os-release"' in runner
    assert "alr loader help probe exit=" in text
    assert "alr loader help probe fixed required=" in text
    assert "alr loader help probe load bias=" in text
    assert "alr loader help probe stdout=" in text
    assert "alr glibc hello probe exit=" in text
    assert "alr glibc hello probe handoff=" in text
    assert "alr glibc hello probe handoff elapsed ms=" in text
    assert "alr glibc hello probe stdout=" in text
    assert "alr direct dynamic glibc hello=" in text
    assert "alr cat os-release probe exit=" in text
    assert "alr cat os-release probe handoff=" in text
    assert "alr cat os-release probe handoff elapsed ms=" in text
    assert "alr cat os-release probe stdout=" in text
    assert "alr translated guest path cat=" in text
    assert "ID=androlinux-tiny" in text
    assert "alr static hello elapsed ms=" in text
    assert "proot static hello elapsed ms=" in text
    assert "alr static hello elapsed ratio pct=" in text
    assert "alr static hello faster than proot=" in text
    assert "alr dynamic glibc elapsed ms=" in text
    assert "proot dynamic glibc elapsed ms=" in text
    assert "alr dynamic glibc elapsed ratio pct=" in text
    assert "alr dynamic glibc faster than proot=" in text
    assert "alr hot path measured faster count=" in text
    assert "alr hot path perf evidence=" in text
    assert "alr translated cat elapsed ms=" in text
    assert "alr static handoff benchmark=" in text
    assert "alr static handoff benchmark requested=" in text
    assert "alr static handoff benchmark pass=" in text
    assert "alr static handoff benchmark average ms=" in text
    assert "native bionic fork benchmark=" in text
    assert "native bionic fork benchmark average us=" in text
    assert "alr static handoff vs native fork ratio pct=" in text
    assert "proot static hello loop benchmark elapsed ms=" in text
    assert "proot static hello loop benchmark average ms=" in text
    assert "alr static handoff vs proot loop ratio pct=" in text
    assert "alr static handoff faster than proot loop=" in text
    assert "alr dynamic glibc handoff benchmark=" in text
    assert "alr dynamic glibc handoff benchmark requested=" in text
    assert "alr dynamic glibc handoff benchmark pass=" in text
    assert "alr dynamic glibc handoff benchmark average ms=" in text
    assert "alr dynamic glibc handoff vs native fork ratio pct=" in text
    assert "proot dynamic glibc loop benchmark elapsed ms=" in text
    assert "proot dynamic glibc loop benchmark average ms=" in text
    assert "alr dynamic glibc handoff vs proot loop ratio pct=" in text
    assert "alr dynamic glibc handoff faster than proot loop=" in text
    assert "alr loop hot path measured faster count=" in text
    assert "alr loop hot path perf evidence=" in text
    assert "ALR SYSCALL STAT BENCH EXECUTION:" in text
    assert "ALR SYSCALL OPENREAD BENCH EXECUTION:" in text
    assert "ALR SYSCALL STAT PRELOAD BENCH EXECUTION:" in text
    assert "ALR SYSCALL OPENREAD PRELOAD BENCH EXECUTION:" in text
    assert "ALR SYSCALL SPAWN BENCH EXECUTION:" in text
    assert "PROOT SYSCALL STAT BENCH EXECUTION:" in text
    assert "PROOT SYSCALL OPENREAD BENCH EXECUTION:" in text
    assert "PROOT SYSCALL SPAWN BENCH EXECUTION:" in text
    assert "rootfs /usr/bin/alr-syscall-bench exists=" in text
    assert "alr syscall stat benchmark average us=" in text
    assert "alr syscall stat path rewrite cache hits=" in text
    assert "proot syscall stat benchmark average us=" in text
    assert "alr syscall stat vs proot ratio pct=" in text
    assert "alr syscall stat preload benchmark average us=" in text
    assert "alr syscall stat preload vs proot ratio pct=" in text
    assert "alr syscall stat preload faster than proot=" in text
    assert "alr syscall openread benchmark average us=" in text
    assert "alr syscall openread path rewrite cache hits=" in text
    assert "proot syscall openread benchmark average us=" in text
    assert "alr syscall openread vs proot ratio pct=" in text
    assert "alr syscall openread preload benchmark average us=" in text
    assert "alr syscall openread preload vs proot ratio pct=" in text
    assert "alr syscall openread preload faster than proot=" in text
    assert "alr syscall spawn benchmark average us=" in text
    assert "proot syscall spawn benchmark average us=" in text
    assert "alr syscall spawn vs proot ratio pct=" in text
    assert "alr syscall hot path measured faster count=" in text
    assert "alr syscall hot path perf evidence=" in text
    assert "alr syscall preload hot path measured faster count=" in text
    assert "alr syscall preload hot path perf evidence=" in text


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


def test_identity_nss_smoke_uses_raw_rootfs_to_avoid_host_etc_binds():
    text = RUNNER.read_text()
    assert "runProotRootfsIdAsRoot" in text
    assert "rawRootfs = true" in text
    assert 'if (rawRootfs) "-r" else "-R"' in text


def test_native_command_runner_can_attempt_dpkg_version_smoke():
    text = RUNNER.read_text()
    assert "runProotRootfsDpkgVersion" in text
    assert '"/usr/bin/dpkg"' in text
    assert '"--version"' in text


def test_main_activity_reports_dpkg_version_smoke_result():
    text = MAIN.read_text()
    assert "DPKG VERSION EXECUTION:" in text
    assert "rootfs /usr/bin/dpkg exists=" in text
    assert "rootfs libmd exists=" in text
    assert "proot dpkg --version exit=" in text
    assert "proot dpkg --version stdout=" in text
    assert "proot dpkg --version stderr=" in text


def test_main_activity_reports_dpkg_arch_and_query_smoke_results():
    text = MAIN.read_text()
    runner = RUNNER.read_text()
    assert "DPKG ARCH EXECUTION:" in text
    assert "DPKG QUERY EXECUTION:" in text
    assert "ALR DPKG VERSION EXECUTION:" in text
    assert "ALR DPKG ARCH EXECUTION:" in text
    assert "ALR DPKG QUERY EXECUTION:" in text
    assert "runAlrRuntimeTrampolineDpkgVersion" in runner
    assert "runAlrRuntimeTrampolineDpkgPrintArchitecture" in runner
    assert "runAlrRuntimeTrampolineDpkgQueryVersion" in runner
    assert "rootfs /usr/bin/dpkg-query exists=" in text
    assert "rootfs /usr/share/dpkg/cputable exists=" in text
    assert "rootfs /usr/share/dpkg/tupletable exists=" in text
    assert "proot dpkg --print-architecture exit=" in text
    assert "proot dpkg-query --version exit=" in text
    assert "alr dpkg --version handoff=" in text
    assert "alr dpkg --print-architecture stdout=" in text
    assert "alr dpkg-query --version path rewrite=" in text


def test_main_activity_reports_apt_base_bundle_smoke_results():
    text = MAIN.read_text()
    runner = RUNNER.read_text()
    assert "APT VERSION EXECUTION:" in text
    assert "APT-GET VERSION EXECUTION:" in text
    assert "APT-CACHE VERSION EXECUTION:" in text
    assert "APT-CONFIG VERSION EXECUTION:" in text
    assert "ALR APT VERSION EXECUTION:" in text
    assert "ALR APT-GET VERSION EXECUTION:" in text
    assert "ALR APT-CACHE VERSION EXECUTION:" in text
    assert "ALR APT-CONFIG VERSION EXECUTION:" in text
    assert "runAlrRuntimeTrampolineAptVersion" in runner
    assert "runAlrRuntimeTrampolineAptGetVersion" in runner
    assert "runAlrRuntimeTrampolineAptCacheVersion" in runner
    assert "runAlrRuntimeTrampolineAptConfigVersion" in runner
    assert "pathRewriteIdleSyscallLimit: Int = 32" in runner
    assert "rootfs /usr/bin/apt exists=" in text
    assert "rootfs /usr/bin/apt-get exists=" in text
    assert "rootfs libapt-pkg exists=" in text
    assert "rootfs apt http method exists=" in text
    assert "proot apt --version exit=" in text
    assert "proot apt-get --version exit=" in text
    assert "proot apt-cache --version exit=" in text
    assert "proot apt-config --version exit=" in text
    assert "alr apt --version handoff=" in text
    assert "alr apt-get --version stdout=" in text
    assert "alr apt-cache --version path rewrite=" in text
    assert "alr apt-config --version stdout=" in text


def test_main_activity_reports_local_deb_install_smoke_results():
    text = MAIN.read_text()
    runner = RUNNER.read_text()
    assert "DPKG LOCAL INSTALL EXECUTION:" in text
    assert "ALR DPKG LOCAL INSTALL EXECUTION:" in text
    assert "INSTALLED PACKAGE EXECUTION:" in text
    assert "runAlrRuntimeTrampolineDpkgInstallLocalSmoke" in runner
    assert "libalr_glibc_loader.so" in runner
    assert "rootfs local deb exists=" in text
    assert "rootfs /usr/bin/dpkg-deb exists=" in text
    assert "rootfs installed alr smoke exists=" in text
    assert "alr dpkg -i local deb handoff=" in text
    assert "alr dpkg -i local deb identity virtualized=" in text
    assert "alr dpkg -i local deb execve attempts=" in text
    assert "alr dpkg -i local deb execve loader rewrites=" in text
    assert "alr dpkg -i local deb traced processes=" in text
    assert "alr dpkg -i local deb last exec requested=" in text
    assert "alr dpkg -i local deb last status syscall=" in text
    assert "alr dpkg -i local deb last status request=" in text
    assert "alr dpkg -i local deb path rewrite=" in text
    assert "proot dpkg -i local deb exit=" in text
    assert "proot installed package smoke exit=" in text


def test_package_manager_install_uses_minimal_raw_rootfs_device_binds():
    text = RUNNER.read_text()
    assert "minimalPackageManagerBinds" in text
    assert "-b" in text
    assert "/dev/null:/dev/null" in text
    assert "/dev/zero:/dev/zero" in text
    assert "/dev/urandom:/dev/urandom" in text


def test_main_activity_reports_dpkg_install_state_placeholders():
    text = MAIN.read_text()
    assert "rootfs /dev/null placeholder exists=" in text
    assert "rootfs dpkg triggers File exists=" in text
    assert "rootfs dpkg triggers Unincorp exists=" in text


def test_package_manager_path_includes_sbin_helpers():
    text = RUNNER.read_text()
    assert "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" in text


def test_main_activity_reports_dpkg_helper_programs():
    text = MAIN.read_text()
    for label in [
        "rootfs helper rm exists=",
        "rootfs helper tar exists=",
        "rootfs helper diff exists=",
        "rootfs helper ldconfig exists=",
        "rootfs helper ldconfig.real exists=",
        "rootfs helper start-stop-daemon exists=",
    ]:
        assert label in text
