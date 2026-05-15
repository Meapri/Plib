from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "rootfs" / "manifests" / "debian-arm64-bookworm-slim.json"
PAYLOAD = ROOT / "app" / "src" / "main" / "assets" / "rootfs" / "payloads" / "tiny-rootfs.tar"


def test_android_assets_include_rootfs_manifest_and_payload():
    assert MANIFEST.is_file()
    assert PAYLOAD.is_file()
    assert PAYLOAD.stat().st_size == 9431040


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
