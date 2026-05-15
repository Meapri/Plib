from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"
PAYLOAD = ROOT / "app" / "src" / "main" / "assets" / "rootfs" / "payloads" / "tiny-rootfs.tar"


def test_android_assets_include_rootfs_manifest_and_payload():
    assert MANIFEST.is_file()
    assert PAYLOAD.is_file()
    assert PAYLOAD.stat().st_size == 36126720


def test_android_asset_manifest_matches_host_manifest():
    host_manifest = ROOT / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"
    assert MANIFEST.read_text() == host_manifest.read_text()


def test_android_asset_payload_matches_host_payload():
    host_payload = ROOT / "rootfs" / "tiny-rootfs.tar"
    assert PAYLOAD.read_bytes() == host_payload.read_bytes()


def test_tiny_rootfs_hello_is_static_arm64_elf_not_shell_script():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        member = archive.extractfile("./bin/hello")
        assert member is not None
        blob = member.read(4)
    assert blob == b"\x7fELF"


def test_tiny_rootfs_hello_remains_static_even_when_shell_is_available():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        hello = archive.extractfile("./bin/hello").read(4)
        shell = archive.extractfile("./bin/sh").read(4)
    assert "./bin/hello" in names
    assert "./bin/sh" in names
    assert hello == b"\x7fELF"
    assert shell == b"\x7fELF"


def test_tiny_rootfs_contains_static_busybox_shell_userland():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./bin/busybox" in names
        assert "./bin/sh" in names
        assert "./bin/cat" in names
        assert archive.extractfile("./bin/busybox").read(4) == b"\x7fELF"
        assert archive.extractfile("./bin/sh").read(4) == b"\x7fELF"
        assert archive.extractfile("./bin/cat").read(4) == b"\x7fELF"


def test_tiny_rootfs_contains_shell_script_smoke_fixture():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./bin/script-hello" in names
        script = archive.extractfile("./bin/script-hello").read().decode()
    assert script.startswith("#!/bin/sh\n")
    assert "hello from shell script rootfs" in script


def test_tiny_rootfs_contains_glibc_dynamic_smoke_files():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./bin/glibc-hello" in names
        assert "./lib/ld-linux-aarch64.so.1" in names
        assert "./lib/aarch64-linux-gnu/libc.so.6" in names
        assert archive.extractfile("./bin/glibc-hello").read(4) == b"\x7fELF"
        assert archive.extractfile("./lib/ld-linux-aarch64.so.1").read(4) == b"\x7fELF"
        assert archive.extractfile("./lib/aarch64-linux-gnu/libc.so.6").read(4) == b"\x7fELF"


def test_tiny_rootfs_glibc_hello_requests_rootfs_loader():
    import subprocess
    import tarfile
    import tempfile

    with tarfile.open(PAYLOAD) as archive, tempfile.NamedTemporaryFile() as tmp:
        tmp.write(archive.extractfile("./bin/glibc-hello").read())
        tmp.flush()
        program_headers = subprocess.check_output(["readelf", "-l", tmp.name], text=True)
    assert "Requesting program interpreter: /lib/ld-linux-aarch64.so.1" in program_headers


def test_tiny_rootfs_contains_syscall_benchmark_fixture():
    import subprocess
    import tarfile
    import tempfile

    with tarfile.open(PAYLOAD) as archive, tempfile.NamedTemporaryFile() as tmp:
        names = set(archive.getnames())
        assert "./usr/bin/alr-syscall-bench" in names
        assert "./usr/share/androlinux/syscall-bench.txt" in names
        bench = archive.extractfile("./usr/bin/alr-syscall-bench").read()
        assert bench[:4] == b"\x7fELF"
        tmp.write(bench)
        tmp.flush()
        program_headers = subprocess.check_output(["readelf", "-l", tmp.name], text=True)
    assert "Requesting program interpreter: /lib/ld-linux-aarch64.so.1" in program_headers


def test_tiny_rootfs_contains_path_preload_fast_path_shim():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        archive_names = archive.getnames()
        names = set(archive_names)
        assert "./usr/lib/androlinux/libalr_path_preload.so" in names
        assert archive_names.count("./usr/lib/androlinux/libalr_path_preload.so") == 1
        assert "./lib/aarch64-linux-gnu/libdl.so.2" in names
        assert "./usr/share/androlinux/path-preload.txt" in names
        assert "./usr/share/androlinux/path-preload-link" in names
        assert archive.getmember("./lib/aarch64-linux-gnu/libdl.so.2").issym()
        assert archive.getmember("./lib/aarch64-linux-gnu/libdl.so.2").linkname == "libc.so.6"
        assert archive.getmember("./usr/share/androlinux/path-preload-link").issym()
        assert archive.extractfile("./usr/lib/androlinux/libalr_path_preload.so").read(4) == b"\x7fELF"


def test_tiny_rootfs_contains_real_distro_dynamic_userland_binaries():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./bin/dash" in names
        assert "./usr/bin/env" in names
        assert archive.extractfile("./bin/dash").read(4) == b"\x7fELF"
        assert archive.extractfile("./usr/bin/env").read(4) == b"\x7fELF"


def test_real_distro_dynamic_userland_binaries_request_rootfs_loader():
    import subprocess
    import tarfile
    import tempfile

    with tarfile.open(PAYLOAD) as archive:
        for member in ["./bin/dash", "./usr/bin/env"]:
            with tempfile.NamedTemporaryFile() as tmp:
                tmp.write(archive.extractfile(member).read())
                tmp.flush()
                program_headers = subprocess.check_output(["readelf", "-l", tmp.name], text=True)
            assert "Requesting program interpreter: /lib/ld-linux-aarch64.so.1" in program_headers


def test_tiny_rootfs_contains_identity_nss_files_and_id_binary():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        expected = [
            "./etc/passwd",
            "./etc/group",
            "./etc/nsswitch.conf",
            "./usr/bin/id",
            "./lib/aarch64-linux-gnu/libselinux.so.1",
            "./lib/aarch64-linux-gnu/libpcre2-8.so.0",
            "./lib/aarch64-linux-gnu/libnss_files.so.2",
        ]
        for member in expected:
            assert member in names
        passwd = archive.extractfile("./etc/passwd").read().decode()
        group = archive.extractfile("./etc/group").read().decode()
        nsswitch = archive.extractfile("./etc/nsswitch.conf").read().decode()
        assert "root:x:0:0:root:/root:/bin/dash" in passwd
        assert "root:x:0:" in group
        assert "passwd: files" in nsswitch
        assert "group: files" in nsswitch
        assert archive.extractfile("./usr/bin/id").read(4) == b"\x7fELF"
        assert archive.extractfile("./lib/aarch64-linux-gnu/libselinux.so.1").read(4) == b"\x7fELF"
        assert archive.extractfile("./lib/aarch64-linux-gnu/libpcre2-8.so.0").read(4) == b"\x7fELF"
        assert archive.extractfile("./lib/aarch64-linux-gnu/libnss_files.so.2").read(4) == b"\x7fELF"


def test_real_distro_id_requests_rootfs_loader():
    import subprocess
    import tarfile
    import tempfile

    with tarfile.open(PAYLOAD) as archive, tempfile.NamedTemporaryFile() as tmp:
        tmp.write(archive.extractfile("./usr/bin/id").read())
        tmp.flush()
        program_headers = subprocess.check_output(["readelf", "-l", tmp.name], text=True)
        dynamic = subprocess.check_output(["readelf", "-d", tmp.name], text=True)
    assert "Requesting program interpreter: /lib/ld-linux-aarch64.so.1" in program_headers
    assert "libselinux.so.1" in dynamic
    assert "libc.so.6" in dynamic


def test_tiny_rootfs_contains_dpkg_version_smoke_files():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./usr/bin/dpkg" in names
        assert "./lib/aarch64-linux-gnu/libmd.so.0" in names
        assert "./etc/dpkg" in names
        assert "./etc/dpkg/dpkg.cfg" in names
        assert "./etc/dpkg/dpkg.cfg.d" in names
        assert "./usr/bin/dpkg-query" in names
        assert "./usr/share/dpkg/abitable" in names
        assert "./usr/share/dpkg/cputable" in names
        assert "./usr/share/dpkg/ostable" in names
        assert "./usr/share/dpkg/tupletable" in names
        assert "./var/lib/dpkg/info" in names
        assert "./var/lib/dpkg/updates" in names
        assert archive.extractfile("./usr/bin/dpkg").read(4) == b"\x7fELF"
        assert archive.extractfile("./usr/bin/dpkg-query").read(4) == b"\x7fELF"
        assert archive.extractfile("./lib/aarch64-linux-gnu/libmd.so.0").read(4) == b"\x7fELF"


def test_real_distro_dpkg_requests_rootfs_loader_and_libmd():
    import subprocess
    import tarfile
    import tempfile

    with tarfile.open(PAYLOAD) as archive, tempfile.NamedTemporaryFile() as tmp:
        tmp.write(archive.extractfile("./usr/bin/dpkg").read())
        tmp.flush()
        program_headers = subprocess.check_output(["readelf", "-l", tmp.name], text=True)
        dynamic = subprocess.check_output(["readelf", "-d", tmp.name], text=True)
    assert "Requesting program interpreter: /lib/ld-linux-aarch64.so.1" in program_headers
    assert "libmd.so.0" in dynamic
    assert "libselinux.so.1" in dynamic
    assert "libc.so.6" in dynamic


def test_real_distro_dpkg_query_requests_rootfs_loader_and_libmd():
    import subprocess
    import tarfile
    import tempfile

    with tarfile.open(PAYLOAD) as archive, tempfile.NamedTemporaryFile() as tmp:
        tmp.write(archive.extractfile("./usr/bin/dpkg-query").read())
        tmp.flush()
        program_headers = subprocess.check_output(["readelf", "-l", tmp.name], text=True)
        dynamic = subprocess.check_output(["readelf", "-d", tmp.name], text=True)
    assert "Requesting program interpreter: /lib/ld-linux-aarch64.so.1" in program_headers
    assert "libmd.so.0" in dynamic
    assert "libc.so.6" in dynamic


def test_tiny_rootfs_contains_bulk_apt_base_bundle():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        required = [
            "./usr/bin/apt",
            "./usr/bin/apt-get",
            "./usr/bin/apt-cache",
            "./usr/bin/apt-config",
            "./usr/lib/apt/apt-helper",
            "./usr/lib/apt/methods/http",
            "./usr/lib/apt/methods/https",
            "./lib/aarch64-linux-gnu/libapt-pkg.so.6.0",
            "./lib/aarch64-linux-gnu/libapt-private.so.0.0",
            "./lib/aarch64-linux-gnu/libstdc++.so.6",
            "./lib/aarch64-linux-gnu/libzstd.so.1",
            "./lib/aarch64-linux-gnu/libsystemd.so.0",
            "./etc/apt/apt.conf.d",
            "./var/lib/apt/lists/partial",
            "./var/cache/apt/archives/partial",
        ]
        for member in required:
            assert member in names
        for member in ["./usr/bin/apt", "./usr/bin/apt-get", "./usr/bin/apt-cache", "./usr/bin/apt-config", "./lib/aarch64-linux-gnu/libapt-pkg.so.6.0"]:
            assert archive.extractfile(member).read(4) == b"\x7fELF"


def test_tiny_rootfs_contains_local_deb_install_smoke_package():
    import io
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./var/cache/apt/archives/alr-smoke_1.0_arm64.deb" in names
        assert "./usr/bin/dpkg-deb" in names
        assert "./bin/tar" in names
        assert "./var/log" in names
        deb = archive.extractfile("./var/cache/apt/archives/alr-smoke_1.0_arm64.deb").read()
        assert deb.startswith(b"!<arch>\n")
        members = {}
        offset = 8
        while offset < len(deb):
            header = deb[offset:offset + 60]
            if len(header) < 60:
                break
            name = header[:16].decode("ascii").strip().removesuffix("/")
            size = int(header[48:58].decode("ascii").strip())
            payload_start = offset + 60
            payload_end = payload_start + size
            members[name] = deb[payload_start:payload_end]
            offset = payload_end + (payload_end % 2)
        with tarfile.open(fileobj=io.BytesIO(members["data.tar.gz"]), mode="r:gz") as data_archive:
            script = data_archive.extractfile("./usr/local/bin/alr-package-smoke").read()
            gpu_script = data_archive.extractfile("./usr/local/bin/alr-package-gpu-smoke").read()
            gles_demo = data_archive.extractfile("./usr/local/bin/alr-package-gles-demo").read()
            gles_procaddr_demo = data_archive.extractfile("./usr/local/bin/alr-package-gles-procaddr-demo").read()
            wayland_gui = data_archive.extractfile("./usr/local/bin/alr-package-wayland-gpu-client").read()
            x11_gui = data_archive.extractfile("./usr/local/bin/alr-package-x11-gpu-client").read()
            vulkan_discovery = data_archive.extractfile("./usr/local/bin/alr-package-vulkan-discovery-client").read()
            vulkan_proxy_smoke = data_archive.extractfile("./usr/local/bin/alr-package-vulkan-proxy-smoke").read()
            vulkan_icd_smoke = data_archive.extractfile("./usr/local/bin/alr-package-vulkan-icd-manifest-smoke").read()
            vulkan_loader_info = data_archive.extractfile("./usr/local/bin/alr-package-vulkan-loader-info").read()
            gles_shim_lib = data_archive.extractfile("./usr/lib/androlinux/libalr_gles_shim.so").read()
            vulkan_proxy_lib = data_archive.extractfile("./usr/lib/androlinux/libvulkan.so.1").read()
            vulkan_icd_manifest = data_archive.extractfile("./usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json").read()
        assert b"ALR_SMOKE_PACKAGE_SCRIPT=1" in script
        assert b"ALR_SMOKE_ARCH=$(dpkg --print-architecture" in script
        assert b"ALR_SMOKE_ENV_ARCH=$(/usr/bin/env dpkg --print-architecture" in script
        assert gpu_script.startswith(b"\x7fELF")
        assert gles_demo.startswith(b"\x7fELF")
        assert gles_procaddr_demo.startswith(b"\x7fELF")
        assert wayland_gui.startswith(b"\x7fELF")
        assert x11_gui.startswith(b"\x7fELF")
        assert vulkan_discovery.startswith(b"\x7fELF")
        assert b"ALR_VK_DEVICE_RECORD" in vulkan_discovery
        assert b"ALR_VK_DISCOVERY_DEVICE_RECORD ok" in vulkan_discovery
        assert b"ALR_VK_SURFACE_CLEAR_REQUEST" in vulkan_discovery
        assert b"ALR_VK_SURFACE_CLEAR_REQUEST_ACCEPTED ok" in vulkan_discovery
        assert vulkan_proxy_smoke.startswith(b"\x7fELF")
        assert vulkan_icd_smoke.startswith(b"\x7fELF")
        assert vulkan_loader_info.startswith(b"\x7fELF")
        assert vulkan_proxy_lib.startswith(b"\x7fELF")
        assert b"ALR_VK_PROXY_SURFACE_CLEAR_REQUEST_ACCEPTED ok" in vulkan_proxy_smoke
        assert b"ALR_VK_PROXY_BINARY_BRIDGE ok" in vulkan_proxy_smoke
        assert b"ALR_VK_ICD_LIBRARY_PATH" in vulkan_icd_smoke
        assert b"ALR_VK_ICD_SURFACE_CLEAR_REQUEST_ACCEPTED ok" in vulkan_icd_smoke
        assert b"ALR_VK_LOADER_VULKANINFO_DEVICE_RECORD ok" in vulkan_loader_info
        assert b"ALR_VK_LOADER_DONE ok" in vulkan_loader_info
        assert b'"library_path": "libvulkan.so.1"' in vulkan_icd_manifest
        assert b'"api_version": "1.3.247"' in vulkan_icd_manifest
        assert b"libvulkan-proxy-binary" in vulkan_proxy_lib
        assert b"ALR_VK_BINARY_BRIDGE_ACK" in vulkan_proxy_lib
        assert b"alr-guest-libvulkan-proxy-v1" in vulkan_proxy_lib
        assert b"ALR_VK_BRIDGE_SOCKET" in vulkan_proxy_lib
        assert b"ALR_GPU_BRIDGE_SOCKET" in gles_shim_lib
        assert archive.extractfile("./usr/bin/dpkg-deb").read(4) == b"\x7fELF"
        assert archive.extractfile("./bin/tar").read(4) == b"\x7fELF"
        dpkg_deb = archive.extractfile("./usr/bin/dpkg-deb").read()
        assert b"--warning=no-timestamp" not in dpkg_deb
        assert b"--warning=none" in dpkg_deb


def test_tiny_rootfs_contains_dpkg_install_state_and_dev_placeholders():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        for member in [
            "./dev",
            "./dev/null",
            "./var/lib/dpkg/triggers",
            "./var/lib/dpkg/triggers/File",
            "./var/lib/dpkg/triggers/Unincorp",
            "./var/lib/dpkg/triggers/Lock",
            "./var/lib/dpkg/lock",
            "./var/lib/dpkg/lock-frontend",
            "./var/lib/dpkg/available",
        ]:
            assert member in names


def test_tiny_rootfs_contains_dpkg_install_helper_programs():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        for member in [
            "./usr/bin/rm",
            "./usr/bin/tar",
            "./usr/bin/diff",
            "./usr/sbin/ldconfig",
            "./sbin/ldconfig.real",
            "./usr/sbin/start-stop-daemon",
            "./usr/bin/dpkg-trigger",
            "./lib/aarch64-linux-gnu/libacl.so.1",
        ]:
            assert member in names


def test_tiny_rootfs_cleans_host_dpkg_needrestart_and_contains_dpkg_split():
    import tarfile

    with tarfile.open(PAYLOAD) as archive:
        names = set(archive.getnames())
        assert "./etc/dpkg/dpkg.cfg.d/needrestart" not in names
        assert "./etc/dpkg/dpkg.cfg.d/00-androlinux-minimal" in names
        assert "./usr/bin/dpkg-split" in names
        minimal = archive.extractfile("./etc/dpkg/dpkg.cfg.d/00-androlinux-minimal").read().decode()
        assert "needrestart" in minimal
