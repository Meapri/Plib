package dev.chanwoo.androlinux

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("alr_loader")

        val rootfsManifest = RootfsManifest(
            name = "debian-arm64",
            version = "bookworm-slim-2026-05-gui-gpu-v40",
            assets = listOf(
                RootfsAsset(
                    path = "rootfs.tar.zst",
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                    sizeBytes = 0,
                ),
            ),
        )
        val rootfsPlan = buildRootfsInstallPlan(rootfsManifest, filesDir)
        val rootfsStatus = RootfsInstaller(this).prepareBundledTinyRootfs()
        val nativeCommandRunner = NativeCommandRunner(
            File(applicationInfo.nativeLibraryDir),
            File(cacheDir, "proot-tmp"),
        )
        val nativeCommandResult = nativeCommandRunner.runSmokeTest()
        val prootCandidateResult = nativeCommandRunner.runProotCandidateSmokeTest()
        val prootShortVersionResult = nativeCommandRunner.runProotShortVersionProbe()
        val prootHelpResult = nativeCommandRunner.runProotHelpProbe()
        val prootNoEnvResult = nativeCommandRunner.runProotNoEnvVersionProbe()
        val prootViaLinkerResult = nativeCommandRunner.runProotViaLinkerVersionProbe()
        val prootHelloResult = nativeCommandRunner.runProotRootfsProgram(rootfsStatus.rootfsDir, "/bin/hello")
        val prootScriptResult = nativeCommandRunner.runProotRootfsProgram(rootfsStatus.rootfsDir, "/bin/script-hello")
        val prootShellResult = nativeCommandRunner.runProotRootfsShell(
            rootfsStatus.rootfsDir,
            "echo shell-c ok; /bin/hello; /bin/cat /etc/os-release",
        )
        val prootGlibcResult = nativeCommandRunner.runProotRootfsProgram(rootfsStatus.rootfsDir, "/bin/glibc-hello")
        val prootDashResult = nativeCommandRunner.runProotRootfsDash(
            rootfsStatus.rootfsDir,
            "echo dash-c ok; /usr/bin/env | /bin/cat",
        )
        val prootIdResult = nativeCommandRunner.runProotRootfsIdAsRoot(rootfsStatus.rootfsDir)
        val prootDpkgVersionResult = nativeCommandRunner.runProotRootfsDpkgVersion(rootfsStatus.rootfsDir)
        val prootDpkgArchResult = nativeCommandRunner.runProotRootfsDpkgPrintArchitecture(rootfsStatus.rootfsDir)
        val prootDpkgQueryVersionResult = nativeCommandRunner.runProotRootfsDpkgQueryVersion(rootfsStatus.rootfsDir)
        val prootDpkgSplitVersionResult = nativeCommandRunner.runProotRootfsDpkgSplitVersion(rootfsStatus.rootfsDir)
        val prootAptVersionResult = nativeCommandRunner.runProotRootfsAptVersion(rootfsStatus.rootfsDir)
        val prootAptGetVersionResult = nativeCommandRunner.runProotRootfsAptGetVersion(rootfsStatus.rootfsDir)
        val prootAptCacheVersionResult = nativeCommandRunner.runProotRootfsAptCacheVersion(rootfsStatus.rootfsDir)
        val prootAptConfigVersionResult = nativeCommandRunner.runProotRootfsAptConfigVersion(rootfsStatus.rootfsDir)
        val prootDpkgInstallLocalResult = nativeCommandRunner.runProotRootfsDpkgInstallLocalSmoke(rootfsStatus.rootfsDir)
        val prootInstalledPackageSmokeResult = nativeCommandRunner.runProotRootfsInstalledPackageSmoke(rootfsStatus.rootfsDir)
        val prootGuestGpuClientResult = nativeCommandRunner.runProotRootfsGuestGpuClient(rootfsStatus.rootfsDir)
        val guestGpuCommands = parseGuestGpuCommands(prootGuestGpuClientResult.stdout)
        val guestGpuIpcBridgeResult = runGuestGpuIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val prootGuestGlesShimSmokeResult = nativeCommandRunner.runProotRootfsGuestGlesShimSmoke(rootfsStatus.rootfsDir)
        val guestGlesShimCommand = parseGuestGlesShimCommand(prootGuestGlesShimSmokeResult.stdout)
        val prootGuestWaylandGuiResult = nativeCommandRunner.runProotRootfsGuestGuiClient(rootfsStatus.rootfsDir, "WAYLAND")
        val prootGuestX11GuiResult = nativeCommandRunner.runProotRootfsGuestGuiClient(rootfsStatus.rootfsDir, "X11")
        val guestWaylandGuiBridgeResult = runGuestGuiBridge(nativeCommandRunner, rootfsStatus.rootfsDir, "WAYLAND")
        val guestX11GuiBridgeResult = runGuestGuiBridge(nativeCommandRunner, rootfsStatus.rootfsDir, "X11")
        val guestGuiSurfaceCommands = guestWaylandGuiBridgeResult.commands + guestX11GuiBridgeResult.commands
        val surfaceGpuCommands = when {
            guestGuiSurfaceCommands.isNotEmpty() -> guestGuiSurfaceCommands
            guestGpuIpcBridgeResult.commands.isNotEmpty() -> guestGpuIpcBridgeResult.commands
            guestGpuCommands.isNotEmpty() -> guestGpuCommands
            guestGlesShimCommand != null -> listOf(guestGlesShimCommand)
            else -> listOf(GuestGpuCommand(0.05f, 0.18f, 0.45f, "host-default"))
        }
        val prootHelloVerboseResult = if (prootHelloResult.exitCode == 0) {
            null
        } else {
            nativeCommandRunner.runProotRootfsProgramVerbose(rootfsStatus.rootfsDir, "/bin/hello")
        }
        val nativeProbe = nativeLibraryProbe(applicationInfo.nativeLibraryDir)
        val hostGpuProbe = nativeHostGpuProbe()
        val requestedPermissions = requestedPermissionNames()
        val internetPermissionDeclared = Manifest.permission.INTERNET in requestedPermissions
        val networkStatePermissionDeclared = Manifest.permission.ACCESS_NETWORK_STATE in requestedPermissions
        val broadStoragePermissionDeclared = "android.permission.MANAGE_EXTERNAL_STORAGE" in requestedPermissions
        val rootfsHelloFile = File(rootfsStatus.rootfsDir, "bin/hello")
        val rootfsShellFile = File(rootfsStatus.rootfsDir, "bin/sh")
        val rootfsCatFile = File(rootfsStatus.rootfsDir, "bin/cat")
        val rootfsScriptFile = File(rootfsStatus.rootfsDir, "bin/script-hello")
        val rootfsGlibcHelloFile = File(rootfsStatus.rootfsDir, "bin/glibc-hello")
        val rootfsGlibcLoaderFile = File(rootfsStatus.rootfsDir, "lib/ld-linux-aarch64.so.1")
        val rootfsLibcFile = File(rootfsStatus.rootfsDir, "lib/aarch64-linux-gnu/libc.so.6")
        val rootfsDashFile = File(rootfsStatus.rootfsDir, "bin/dash")
        val rootfsEnvFile = File(rootfsStatus.rootfsDir, "usr/bin/env")
        val rootfsPasswdFile = File(rootfsStatus.rootfsDir, "etc/passwd")
        val rootfsGroupFile = File(rootfsStatus.rootfsDir, "etc/group")
        val rootfsNsswitchFile = File(rootfsStatus.rootfsDir, "etc/nsswitch.conf")
        val rootfsIdFile = File(rootfsStatus.rootfsDir, "usr/bin/id")
        val rootfsLibselinuxFile = File(rootfsStatus.rootfsDir, "lib/aarch64-linux-gnu/libselinux.so.1")
        val rootfsLibpcre2File = File(rootfsStatus.rootfsDir, "lib/aarch64-linux-gnu/libpcre2-8.so.0")
        val rootfsLibnssFilesFile = File(rootfsStatus.rootfsDir, "lib/aarch64-linux-gnu/libnss_files.so.2")
        val rootfsDpkgFile = File(rootfsStatus.rootfsDir, "usr/bin/dpkg")
        val rootfsLibmdFile = File(rootfsStatus.rootfsDir, "lib/aarch64-linux-gnu/libmd.so.0")
        val rootfsDpkgConfigDir = File(rootfsStatus.rootfsDir, "etc/dpkg/dpkg.cfg.d")
        val rootfsDpkgConfigFile = File(rootfsStatus.rootfsDir, "etc/dpkg/dpkg.cfg")
        val rootfsNeedrestartConfigFile = File(rootfsStatus.rootfsDir, "etc/dpkg/dpkg.cfg.d/needrestart")
        val rootfsAndrolinuxDpkgConfigFile = File(rootfsStatus.rootfsDir, "etc/dpkg/dpkg.cfg.d/00-androlinux-minimal")
        val rootfsDpkgQueryFile = File(rootfsStatus.rootfsDir, "usr/bin/dpkg-query")
        val rootfsDpkgCpuTableFile = File(rootfsStatus.rootfsDir, "usr/share/dpkg/cputable")
        val rootfsDpkgTupleTableFile = File(rootfsStatus.rootfsDir, "usr/share/dpkg/tupletable")
        val rootfsAptFile = File(rootfsStatus.rootfsDir, "usr/bin/apt")
        val rootfsAptGetFile = File(rootfsStatus.rootfsDir, "usr/bin/apt-get")
        val rootfsAptCacheFile = File(rootfsStatus.rootfsDir, "usr/bin/apt-cache")
        val rootfsAptConfigFile = File(rootfsStatus.rootfsDir, "usr/bin/apt-config")
        val rootfsLibAptPkgFile = File(rootfsStatus.rootfsDir, "lib/aarch64-linux-gnu/libapt-pkg.so.6.0")
        val rootfsLibAptPrivateFile = File(rootfsStatus.rootfsDir, "lib/aarch64-linux-gnu/libapt-private.so.0.0")
        val rootfsAptHttpMethodFile = File(rootfsStatus.rootfsDir, "usr/lib/apt/methods/http")
        val rootfsAptListsPartialDir = File(rootfsStatus.rootfsDir, "var/lib/apt/lists/partial")
        val rootfsLocalDebFile = File(rootfsStatus.rootfsDir, "var/cache/apt/archives/alr-smoke_1.0_arm64.deb")
        val rootfsDpkgDebFile = File(rootfsStatus.rootfsDir, "usr/bin/dpkg-deb")
        val rootfsInstalledSmokeFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-smoke")
        val rootfsDpkgStatusFile = File(rootfsStatus.rootfsDir, "var/lib/dpkg/status")
        val rootfsDevNullFile = File(rootfsStatus.rootfsDir, "dev/null")
        val rootfsDpkgTriggersFile = File(rootfsStatus.rootfsDir, "var/lib/dpkg/triggers/File")
        val rootfsDpkgTriggersUnincorpFile = File(rootfsStatus.rootfsDir, "var/lib/dpkg/triggers/Unincorp")
        val rootfsRmFile = File(rootfsStatus.rootfsDir, "usr/bin/rm")
        val rootfsTarFile = File(rootfsStatus.rootfsDir, "usr/bin/tar")
        val rootfsDiffFile = File(rootfsStatus.rootfsDir, "usr/bin/diff")
        val rootfsLdconfigFile = File(rootfsStatus.rootfsDir, "usr/sbin/ldconfig")
        val rootfsLdconfigRealFile = File(rootfsStatus.rootfsDir, "sbin/ldconfig.real")
        val rootfsStartStopDaemonFile = File(rootfsStatus.rootfsDir, "usr/sbin/start-stop-daemon")
        val rootfsDpkgSplitFile = File(rootfsStatus.rootfsDir, "usr/bin/dpkg-split")
        val rootfsGuestGpuClientFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-gpu-client")
        val rootfsGuestGlesShimSmokeFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-gles-shim-smoke")
        val rootfsGuestGlesShimLibraryFile = File(rootfsStatus.rootfsDir, "usr/lib/androlinux/libalr_gles_shim.so")
        val rootfsWaylandGuiClientFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-wayland-gpu-client")
        val rootfsX11GuiClientFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-x11-gpu-client")
        val rootfsExecutionPassed = prootHelloResult.exitCode == 0 &&
            prootHelloResult.stdout.contains("hello from static arm64 rootfs")
        val shellScriptExecutionPassed = prootScriptResult.exitCode == 0 &&
            prootScriptResult.stdout.contains("hello from shell script rootfs")
        val shellCommandExecutionPassed = prootShellResult.exitCode == 0 &&
            prootShellResult.stdout.contains("shell-c ok") &&
            prootShellResult.stdout.contains("NAME=\"AndroLinux Tiny Rootfs\"")
        val glibcDynamicExecutionPassed = prootGlibcResult.exitCode == 0 &&
            prootGlibcResult.stdout.contains("hello from dynamic glibc rootfs") &&
            prootGlibcResult.stdout.contains("glibc version=")
        val androidEnvLeakPrefixes = listOf(
            "ANDROID_ROOT=",
            "ANDROID_DATA=",
            "BOOTCLASSPATH=",
            "DEX2OATBOOTCLASSPATH=",
            "SYSTEMSERVERCLASSPATH=",
            "ANDROID_SOCKET_",
            "EXTERNAL_STORAGE=",
            "KNOX_STORAGE=",
        )
        val guestEnvLeakedAndroidVars = prootDashResult.stdout.lineSequence().any { line ->
            androidEnvLeakPrefixes.any { prefix -> line.startsWith(prefix) }
        }
        val distroUserlandExecutionPassed = prootDashResult.exitCode == 0 &&
            prootDashResult.stdout.contains("dash-c ok") &&
            prootDashResult.stdout.contains("ALR_ROOTFS=") &&
            prootDashResult.stdout.contains("PATH=") &&
            !guestEnvLeakedAndroidVars
        val identityNumericRoot = prootIdResult.exitCode == 0 &&
            prootIdResult.stdout.contains("uid=0") &&
            prootIdResult.stdout.contains("gid=0")
        val identityNamedRoot = prootIdResult.stdout.contains("uid=0(root)") &&
            prootIdResult.stdout.contains("gid=0(root)")
        val identityNssExecutionPassed = identityNumericRoot && identityNamedRoot
        val dpkgVersionExecutionPassed = prootDpkgVersionResult.exitCode == 0 &&
            prootDpkgVersionResult.stdout.contains("Debian 'dpkg' package management program")
        val dpkgArchExecutionPassed = prootDpkgArchResult.exitCode == 0 &&
            prootDpkgArchResult.stdout.trim() == "arm64"
        val dpkgQueryExecutionPassed = prootDpkgQueryVersionResult.exitCode == 0 &&
            prootDpkgQueryVersionResult.stdout.contains("Debian dpkg-query package management program")
        val dpkgSplitExecutionPassed = prootDpkgSplitVersionResult.exitCode == 0 &&
            prootDpkgSplitVersionResult.stdout.contains("Debian 'dpkg-split' package split/join tool")
        val aptVersionExecutionPassed = prootAptVersionResult.exitCode == 0 &&
            prootAptVersionResult.stdout.contains("apt ") &&
            prootAptVersionResult.stdout.contains("arm64")
        val aptGetVersionExecutionPassed = prootAptGetVersionResult.exitCode == 0 &&
            prootAptGetVersionResult.stdout.contains("apt ") &&
            prootAptGetVersionResult.stdout.contains("arm64")
        val aptCacheVersionExecutionPassed = prootAptCacheVersionResult.exitCode == 0 &&
            prootAptCacheVersionResult.stdout.contains("apt ") &&
            prootAptCacheVersionResult.stdout.contains("arm64")
        val aptConfigVersionExecutionPassed = prootAptConfigVersionResult.exitCode == 0 &&
            prootAptConfigVersionResult.stdout.contains("apt ") &&
            prootAptConfigVersionResult.stdout.contains("arm64")
        val dpkgLocalInstallExecutionPassed = prootDpkgInstallLocalResult.exitCode == 0 &&
            !prootDpkgInstallLocalResult.stderr.contains("dpkg: error") &&
            (prootDpkgInstallLocalResult.stdout.contains("Setting up alr-smoke") ||
                prootDpkgInstallLocalResult.stderr.contains("Setting up alr-smoke") ||
                prootDpkgInstallLocalResult.stdout.contains("alr-smoke (1.0)") ||
                prootDpkgInstallLocalResult.stderr.contains("alr-smoke (1.0)") ||
                prootDpkgInstallLocalResult.stdout.contains("Selecting previously unselected package alr-smoke") ||
                prootDpkgInstallLocalResult.stderr.contains("Selecting previously unselected package alr-smoke"))
        val installedPackageExecutionPassed = prootInstalledPackageSmokeResult.exitCode == 0 &&
            prootInstalledPackageSmokeResult.stdout.contains("alr local deb package smoke ok")
        val guestGpuBridgeCommandPassed = prootGuestGpuClientResult.exitCode == 0 &&
            prootGuestGpuClientResult.stdout.contains("alr guest gpu client ok") &&
            guestGpuCommands.isNotEmpty()
        val guestGpuIpcBridgePassed = guestGpuIpcBridgeResult.clientResult.exitCode == 0 &&
            guestGpuIpcBridgeResult.commands.size == guestGpuIpcBridgeResult.expectedFrames &&
            guestGpuIpcBridgeResult.expectedFrames > 0 &&
            guestGpuIpcBridgeResult.error == null
        val guestGlesShimSmokePassed = prootGuestGlesShimSmokeResult.exitCode == 0 &&
            prootGuestGlesShimSmokeResult.stdout.contains("alr guest gles shim smoke ok") &&
            prootGuestGlesShimSmokeResult.stdout.contains("ALR_GLES_SHIM_LOAD ok") &&
            guestGlesShimCommand != null
        val guestWaylandGuiBridgePassed = guestWaylandGuiBridgeResult.clientResult.exitCode == 0 &&
            guestWaylandGuiBridgeResult.commands.size == guestWaylandGuiBridgeResult.expectedFrames &&
            guestWaylandGuiBridgeResult.expectedFrames > 0 &&
            guestWaylandGuiBridgeResult.error == null
        val guestX11GuiBridgePassed = guestX11GuiBridgeResult.clientResult.exitCode == 0 &&
            guestX11GuiBridgeResult.commands.size == guestX11GuiBridgeResult.expectedFrames &&
            guestX11GuiBridgeResult.expectedFrames > 0 &&
            guestX11GuiBridgeResult.error == null
        val hostGpuHardwareCandidate = hostGpuProbe.lineStartingWith("host gpu hardware candidate=") == "host gpu hardware candidate=true"

        val executionSummary = "build: 0.4.45-runtime-probe-scaffold-v45" +
            "\nexecution summary" +
            "\nROOTFS EXECUTION: ${if (rootfsExecutionPassed) "PASS" else "FAIL"}" +
            "\nSHELL SCRIPT EXECUTION: ${if (shellScriptExecutionPassed) "PASS" else "FAIL"}" +
            "\nSHELL -C EXECUTION: ${if (shellCommandExecutionPassed) "PASS" else "FAIL"}" +
            "\nGLIBC DYNAMIC EXECUTION: ${if (glibcDynamicExecutionPassed) "PASS" else "FAIL"}" +
            "\nDISTRO USERLAND EXECUTION: ${if (distroUserlandExecutionPassed) "PASS" else "FAIL"}" +
            "\nCLEAN GUEST ENVIRONMENT: ${if (!guestEnvLeakedAndroidVars) "PASS" else "FAIL"}" +
            "\nIDENTITY NSS EXECUTION: ${if (identityNssExecutionPassed) "PASS" else "FAIL"}" +
            "\nDPKG VERSION EXECUTION: ${if (dpkgVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nDPKG ARCH EXECUTION: ${if (dpkgArchExecutionPassed) "PASS" else "FAIL"}" +
            "\nDPKG QUERY EXECUTION: ${if (dpkgQueryExecutionPassed) "PASS" else "FAIL"}" +
            "\nDPKG SPLIT EXECUTION: ${if (dpkgSplitExecutionPassed) "PASS" else "FAIL"}" +
            "\nAPT VERSION EXECUTION: ${if (aptVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nAPT-GET VERSION EXECUTION: ${if (aptGetVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nAPT-CACHE VERSION EXECUTION: ${if (aptCacheVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nAPT-CONFIG VERSION EXECUTION: ${if (aptConfigVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nDPKG LOCAL INSTALL EXECUTION: ${if (dpkgLocalInstallExecutionPassed) "PASS" else "FAIL"}" +
            "\nINSTALLED PACKAGE EXECUTION: ${if (installedPackageExecutionPassed) "PASS" else "FAIL"}" +
            "\nHOST GPU EGL/GLES EXECUTION: ${if (hostGpuHardwareCandidate) "PASS" else "FAIL"}" +
            "\nHOST GPU SURFACE EXECUTION: PENDING_SURFACE_CALLBACK" +
            "\nGUEST GPU BRIDGE COMMAND EXECUTION: ${if (guestGpuBridgeCommandPassed) "PASS" else "FAIL"}" +
            "\nGUEST GPU IPC BRIDGE EXECUTION: ${if (guestGpuIpcBridgePassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES SHIM SMOKE EXECUTION: ${if (guestGlesShimSmokePassed) "PASS" else "FAIL"}" +
            "\nGUEST GPU MULTI-FRAME SURFACE EXECUTION: PENDING_SURFACE_CALLBACK" +
            "\nGUEST WAYLAND GUI GPU BRIDGE EXECUTION: ${if (guestWaylandGuiBridgePassed) "PASS" else "FAIL"}" +
            "\nGUEST X11 GUI GPU BRIDGE EXECUTION: ${if (guestX11GuiBridgePassed) "PASS" else "FAIL"}" +
            "\nGUEST GUI GPU SURFACE EXECUTION: PENDING_SURFACE_CALLBACK" +
            "\nANDROID PERMISSION MODEL: ${if (internetPermissionDeclared && networkStatePermissionDeclared && !broadStoragePermissionDeclared) "PASS" else "FAIL"}" +
            "\nidentity numeric root=$identityNumericRoot" +
            "\nidentity named root=$identityNamedRoot" +
            "\nidentity proot mode=raw -r" +
            "\nguest env leaked android vars=$guestEnvLeakedAndroidVars" +
            "\nrootfs verified=${rootfsStatus.verified} extracted=${rootfsStatus.extracted}" +
            "\nrootfs /bin/hello exists=${rootfsHelloFile.isFile} executable=${rootfsHelloFile.canExecute()} bytes=${rootfsHelloFile.length()}" +
            "\nrootfs /bin/sh exists=${rootfsShellFile.isFile} executable=${rootfsShellFile.canExecute()} bytes=${rootfsShellFile.length()}" +
            "\nrootfs /bin/cat exists=${rootfsCatFile.isFile} executable=${rootfsCatFile.canExecute()} bytes=${rootfsCatFile.length()}" +
            "\nrootfs /bin/script-hello exists=${rootfsScriptFile.isFile} executable=${rootfsScriptFile.canExecute()} bytes=${rootfsScriptFile.length()}" +
            "\nrootfs /bin/glibc-hello exists=${rootfsGlibcHelloFile.isFile} executable=${rootfsGlibcHelloFile.canExecute()} bytes=${rootfsGlibcHelloFile.length()}" +
            "\nrootfs glibc loader exists=${rootfsGlibcLoaderFile.isFile} executable=${rootfsGlibcLoaderFile.canExecute()} bytes=${rootfsGlibcLoaderFile.length()}" +
            "\nrootfs libc exists=${rootfsLibcFile.isFile} executable=${rootfsLibcFile.canExecute()} bytes=${rootfsLibcFile.length()}" +
            "\nrootfs /bin/dash exists=${rootfsDashFile.isFile} executable=${rootfsDashFile.canExecute()} bytes=${rootfsDashFile.length()}" +
            "\nrootfs /usr/bin/env exists=${rootfsEnvFile.isFile} executable=${rootfsEnvFile.canExecute()} bytes=${rootfsEnvFile.length()}" +
            "\nrootfs /etc/passwd exists=${rootfsPasswdFile.isFile} bytes=${rootfsPasswdFile.length()}" +
            "\nrootfs /etc/group exists=${rootfsGroupFile.isFile} bytes=${rootfsGroupFile.length()}" +
            "\nrootfs /etc/nsswitch.conf exists=${rootfsNsswitchFile.isFile} bytes=${rootfsNsswitchFile.length()}" +
            "\nrootfs /usr/bin/id exists=${rootfsIdFile.isFile} executable=${rootfsIdFile.canExecute()} bytes=${rootfsIdFile.length()}" +
            "\nrootfs libselinux exists=${rootfsLibselinuxFile.isFile} executable=${rootfsLibselinuxFile.canExecute()} bytes=${rootfsLibselinuxFile.length()}" +
            "\nrootfs libpcre2 exists=${rootfsLibpcre2File.isFile} executable=${rootfsLibpcre2File.canExecute()} bytes=${rootfsLibpcre2File.length()}" +
            "\nrootfs libnss_files exists=${rootfsLibnssFilesFile.isFile} executable=${rootfsLibnssFilesFile.canExecute()} bytes=${rootfsLibnssFilesFile.length()}" +
            "\nrootfs /usr/bin/dpkg exists=${rootfsDpkgFile.isFile} executable=${rootfsDpkgFile.canExecute()} bytes=${rootfsDpkgFile.length()}" +
            "\nrootfs libmd exists=${rootfsLibmdFile.isFile} executable=${rootfsLibmdFile.canExecute()} bytes=${rootfsLibmdFile.length()}" +
            "\nrootfs /etc/dpkg/dpkg.cfg exists=${rootfsDpkgConfigFile.isFile} bytes=${rootfsDpkgConfigFile.length()}" +
            "\nrootfs /etc/dpkg/dpkg.cfg.d exists=${rootfsDpkgConfigDir.isDirectory}" +
            "\nrootfs stale needrestart dpkg cfg exists=${rootfsNeedrestartConfigFile.isFile}" +
            "\nrootfs androlinux minimal dpkg cfg exists=${rootfsAndrolinuxDpkgConfigFile.isFile}" +
            "\nrootfs /usr/bin/dpkg-query exists=${rootfsDpkgQueryFile.isFile} executable=${rootfsDpkgQueryFile.canExecute()} bytes=${rootfsDpkgQueryFile.length()}" +
            "\nrootfs /usr/share/dpkg/cputable exists=${rootfsDpkgCpuTableFile.isFile} bytes=${rootfsDpkgCpuTableFile.length()}" +
            "\nrootfs /usr/share/dpkg/tupletable exists=${rootfsDpkgTupleTableFile.isFile} bytes=${rootfsDpkgTupleTableFile.length()}" +
            "\nrootfs /usr/bin/apt exists=${rootfsAptFile.isFile} executable=${rootfsAptFile.canExecute()} bytes=${rootfsAptFile.length()}" +
            "\nrootfs /usr/bin/apt-get exists=${rootfsAptGetFile.isFile} executable=${rootfsAptGetFile.canExecute()} bytes=${rootfsAptGetFile.length()}" +
            "\nrootfs /usr/bin/apt-cache exists=${rootfsAptCacheFile.isFile} executable=${rootfsAptCacheFile.canExecute()} bytes=${rootfsAptCacheFile.length()}" +
            "\nrootfs /usr/bin/apt-config exists=${rootfsAptConfigFile.isFile} executable=${rootfsAptConfigFile.canExecute()} bytes=${rootfsAptConfigFile.length()}" +
            "\nrootfs libapt-pkg exists=${rootfsLibAptPkgFile.isFile} executable=${rootfsLibAptPkgFile.canExecute()} bytes=${rootfsLibAptPkgFile.length()}" +
            "\nrootfs libapt-private exists=${rootfsLibAptPrivateFile.isFile} executable=${rootfsLibAptPrivateFile.canExecute()} bytes=${rootfsLibAptPrivateFile.length()}" +
            "\nrootfs apt http method exists=${rootfsAptHttpMethodFile.isFile} executable=${rootfsAptHttpMethodFile.canExecute()} bytes=${rootfsAptHttpMethodFile.length()}" +
            "\nrootfs apt lists partial exists=${rootfsAptListsPartialDir.isDirectory}" +
            "\nrootfs local deb exists=${rootfsLocalDebFile.isFile} bytes=${rootfsLocalDebFile.length()}" +
            "\nrootfs /usr/bin/dpkg-deb exists=${rootfsDpkgDebFile.isFile} executable=${rootfsDpkgDebFile.canExecute()} bytes=${rootfsDpkgDebFile.length()}" +
            "\nrootfs installed alr smoke exists=${rootfsInstalledSmokeFile.isFile} executable=${rootfsInstalledSmokeFile.canExecute()} bytes=${rootfsInstalledSmokeFile.length()}" +
            "\nrootfs dpkg status exists=${rootfsDpkgStatusFile.isFile} bytes=${rootfsDpkgStatusFile.length()}" +
            "\nrootfs /dev/null placeholder exists=${rootfsDevNullFile.isFile} bytes=${rootfsDevNullFile.length()}" +
            "\nrootfs dpkg triggers File exists=${rootfsDpkgTriggersFile.isFile} bytes=${rootfsDpkgTriggersFile.length()}" +
            "\nrootfs dpkg triggers Unincorp exists=${rootfsDpkgTriggersUnincorpFile.isFile} bytes=${rootfsDpkgTriggersUnincorpFile.length()}" +
            "\nrootfs helper rm exists=${rootfsRmFile.isFile} executable=${rootfsRmFile.canExecute()} bytes=${rootfsRmFile.length()}" +
            "\nrootfs helper tar exists=${rootfsTarFile.isFile} executable=${rootfsTarFile.canExecute()} bytes=${rootfsTarFile.length()}" +
            "\nrootfs helper diff exists=${rootfsDiffFile.isFile} executable=${rootfsDiffFile.canExecute()} bytes=${rootfsDiffFile.length()}" +
            "\nrootfs helper ldconfig exists=${rootfsLdconfigFile.isFile} executable=${rootfsLdconfigFile.canExecute()} bytes=${rootfsLdconfigFile.length()}" +
            "\nrootfs helper ldconfig.real exists=${rootfsLdconfigRealFile.isFile} executable=${rootfsLdconfigRealFile.canExecute()} bytes=${rootfsLdconfigRealFile.length()}" +
            "\nrootfs helper start-stop-daemon exists=${rootfsStartStopDaemonFile.isFile} executable=${rootfsStartStopDaemonFile.canExecute()} bytes=${rootfsStartStopDaemonFile.length()}" +
            "\nrootfs /usr/bin/dpkg-split exists=${rootfsDpkgSplitFile.isFile} executable=${rootfsDpkgSplitFile.canExecute()} bytes=${rootfsDpkgSplitFile.length()}" +
            "\nrootfs /usr/bin/alr-gpu-client exists=${rootfsGuestGpuClientFile.isFile} executable=${rootfsGuestGpuClientFile.canExecute()} bytes=${rootfsGuestGpuClientFile.length()}" +
            "\nrootfs /usr/bin/alr-gles-shim-smoke exists=${rootfsGuestGlesShimSmokeFile.isFile} executable=${rootfsGuestGlesShimSmokeFile.canExecute()} bytes=${rootfsGuestGlesShimSmokeFile.length()}" +
            "\nrootfs /usr/lib/androlinux/libalr_gles_shim.so exists=${rootfsGuestGlesShimLibraryFile.isFile} executable=${rootfsGuestGlesShimLibraryFile.canExecute()} bytes=${rootfsGuestGlesShimLibraryFile.length()}" +
            "\nrootfs /usr/bin/alr-wayland-gpu-client exists=${rootfsWaylandGuiClientFile.isFile} executable=${rootfsWaylandGuiClientFile.canExecute()} bytes=${rootfsWaylandGuiClientFile.length()}" +
            "\nrootfs /usr/bin/alr-x11-gpu-client exists=${rootfsX11GuiClientFile.isFile} executable=${rootfsX11GuiClientFile.canExecute()} bytes=${rootfsX11GuiClientFile.length()}" +
            "\nproot guest gpu client exit=${prootGuestGpuClientResult.exitCode}" +
            "\nproot guest gpu client stdout=${prootGuestGpuClientResult.stdout}" +
            "\nproot guest gpu client stderr=${prootGuestGpuClientResult.stderr}" +
            "\nguest gpu stdout commands parsed=${guestGpuCommands.size}" +
            "\nguest gpu ipc host=${guestGpuIpcBridgeResult.host}" +
            "\nguest gpu ipc port=${guestGpuIpcBridgeResult.port}" +
            "\nguest gpu ipc expected frames=${guestGpuIpcBridgeResult.expectedFrames}" +
            "\nguest gpu ipc received frames=${guestGpuIpcBridgeResult.commands.size}" +
            "\nguest gpu ipc dropped frames=${(guestGpuIpcBridgeResult.expectedFrames - guestGpuIpcBridgeResult.commands.size).coerceAtLeast(0)}" +
            "\nguest gpu ipc lossless=${guestGpuIpcBridgeResult.expectedFrames > 0 && guestGpuIpcBridgeResult.expectedFrames == guestGpuIpcBridgeResult.commands.size}" +
            "\nguest gpu ipc raw=${guestGpuIpcBridgeResult.rawLines.joinToString("|")}" +
            "\nguest gpu ipc error=${guestGpuIpcBridgeResult.error ?: "none"}" +
            "\nproot guest gpu ipc client exit=${guestGpuIpcBridgeResult.clientResult.exitCode}" +
            "\nproot guest gpu ipc client stdout=${guestGpuIpcBridgeResult.clientResult.stdout}" +
            "\nproot guest gpu ipc client stderr=${guestGpuIpcBridgeResult.clientResult.stderr}" +
            "\nproot guest gles shim smoke exit=${prootGuestGlesShimSmokeResult.exitCode}" +
            "\nproot guest gles shim smoke stdout=${prootGuestGlesShimSmokeResult.stdout}" +
            "\nproot guest gles shim smoke stderr=${prootGuestGlesShimSmokeResult.stderr}" +
            "\nguest gles shim command parsed=${guestGlesShimCommand != null}" +
            "\nproot guest wayland gui client exit=${prootGuestWaylandGuiResult.exitCode}" +
            "\nproot guest wayland gui client stdout=${prootGuestWaylandGuiResult.stdout}" +
            "\nproot guest x11 gui client exit=${prootGuestX11GuiResult.exitCode}" +
            "\nproot guest x11 gui client stdout=${prootGuestX11GuiResult.stdout}" +
            "\nguest wayland gui ipc expected frames=${guestWaylandGuiBridgeResult.expectedFrames}" +
            "\nguest wayland gui ipc received frames=${guestWaylandGuiBridgeResult.commands.size}" +
            "\nguest wayland gui ipc lossless=${guestWaylandGuiBridgeResult.expectedFrames > 0 && guestWaylandGuiBridgeResult.expectedFrames == guestWaylandGuiBridgeResult.commands.size}" +
            "\nguest wayland gui ipc seq gaps=${guiSeqGaps(guestWaylandGuiBridgeResult.commands, guestWaylandGuiBridgeResult.expectedFrames)}" +
            "\nguest wayland gui ipc duplicate seq=${guiDuplicateSeqCount(guestWaylandGuiBridgeResult.commands)}" +
            "\nguest wayland gui ipc out of order=${guiOutOfOrder(guestWaylandGuiBridgeResult.commands)}" +
            "\nguest wayland gui ipc raw=${guestWaylandGuiBridgeResult.rawLines.joinToString("|")}" +
            "\nguest wayland gui ipc error=${guestWaylandGuiBridgeResult.error ?: "none"}" +
            "\nguest x11 gui ipc expected frames=${guestX11GuiBridgeResult.expectedFrames}" +
            "\nguest x11 gui ipc received frames=${guestX11GuiBridgeResult.commands.size}" +
            "\nguest x11 gui ipc lossless=${guestX11GuiBridgeResult.expectedFrames > 0 && guestX11GuiBridgeResult.expectedFrames == guestX11GuiBridgeResult.commands.size}" +
            "\nguest x11 gui ipc seq gaps=${guiSeqGaps(guestX11GuiBridgeResult.commands, guestX11GuiBridgeResult.expectedFrames)}" +
            "\nguest x11 gui ipc duplicate seq=${guiDuplicateSeqCount(guestX11GuiBridgeResult.commands)}" +
            "\nguest x11 gui ipc out of order=${guiOutOfOrder(guestX11GuiBridgeResult.commands)}" +
            "\nguest x11 gui ipc raw=${guestX11GuiBridgeResult.rawLines.joinToString("|")}" +
            "\nguest x11 gui ipc error=${guestX11GuiBridgeResult.error ?: "none"}" +
            "\nsurface gpu command source frames=${surfaceGpuCommands.size}" +
            "\nproot dpkg-split --version exit=${prootDpkgSplitVersionResult.exitCode}" +
            "\nproot dpkg-split --version stdout=${prootDpkgSplitVersionResult.stdout}" +
            "\nproot dpkg-split --version stderr=${prootDpkgSplitVersionResult.stderr}" +
            "\nnative smoke exit=${nativeCommandResult.exitCode}" +
            "\nnative smoke stdout=${nativeCommandResult.stdout}" +
            "\nproot --version exit=${prootCandidateResult.exitCode}" +
            "\nlinker64 proot --version exit=${prootViaLinkerResult.exitCode}" +
            "\nproot hello quiet exit=${prootHelloResult.exitCode}" +
            "\nproot hello quiet stdout=${prootHelloResult.stdout}" +
            "\nproot hello quiet stderr=${prootHelloResult.stderr}" +
            "\nproot script exit=${prootScriptResult.exitCode}" +
            "\nproot script stdout=${prootScriptResult.stdout}" +
            "\nproot script stderr=${prootScriptResult.stderr}" +
            "\nproot shell -c exit=${prootShellResult.exitCode}" +
            "\nproot shell -c stdout=${prootShellResult.stdout}" +
            "\nproot shell -c stderr=${prootShellResult.stderr}" +
            "\nproot glibc exit=${prootGlibcResult.exitCode}" +
            "\nproot glibc stdout=${prootGlibcResult.stdout}" +
            "\nproot glibc stderr=${prootGlibcResult.stderr}" +
            "\nproot dash exit=${prootDashResult.exitCode}" +
            "\nproot dash stdout=${prootDashResult.stdout}" +
            "\nproot dash stderr=${prootDashResult.stderr}" +
            "\nproot id exit=${prootIdResult.exitCode}" +
            "\nproot id stdout=${prootIdResult.stdout}" +
            "\nproot id stderr=${prootIdResult.stderr}" +
            "\nproot dpkg --version exit=${prootDpkgVersionResult.exitCode}" +
            "\nproot dpkg --version stdout=${prootDpkgVersionResult.stdout}" +
            "\nproot dpkg --version stderr=${prootDpkgVersionResult.stderr}" +
            "\nproot dpkg --print-architecture exit=${prootDpkgArchResult.exitCode}" +
            "\nproot dpkg --print-architecture stdout=${prootDpkgArchResult.stdout}" +
            "\nproot dpkg --print-architecture stderr=${prootDpkgArchResult.stderr}" +
            "\nproot dpkg-query --version exit=${prootDpkgQueryVersionResult.exitCode}" +
            "\nproot dpkg-query --version stdout=${prootDpkgQueryVersionResult.stdout}" +
            "\nproot dpkg-query --version stderr=${prootDpkgQueryVersionResult.stderr}" +
            "\nproot apt --version exit=${prootAptVersionResult.exitCode}" +
            "\nproot apt --version stdout=${prootAptVersionResult.stdout}" +
            "\nproot apt --version stderr=${prootAptVersionResult.stderr}" +
            "\nproot apt-get --version exit=${prootAptGetVersionResult.exitCode}" +
            "\nproot apt-get --version stdout=${prootAptGetVersionResult.stdout}" +
            "\nproot apt-get --version stderr=${prootAptGetVersionResult.stderr}" +
            "\nproot apt-cache --version exit=${prootAptCacheVersionResult.exitCode}" +
            "\nproot apt-cache --version stdout=${prootAptCacheVersionResult.stdout}" +
            "\nproot apt-cache --version stderr=${prootAptCacheVersionResult.stderr}" +
            "\nproot apt-config --version exit=${prootAptConfigVersionResult.exitCode}" +
            "\nproot apt-config --version stdout=${prootAptConfigVersionResult.stdout}" +
            "\nproot apt-config --version stderr=${prootAptConfigVersionResult.stderr}" +
            "\nproot dpkg -i local deb exit=${prootDpkgInstallLocalResult.exitCode}" +
            "\nproot dpkg -i local deb stdout=${prootDpkgInstallLocalResult.stdout}" +
            "\nproot dpkg -i local deb stderr=${prootDpkgInstallLocalResult.stderr}" +
            "\nproot installed package smoke exit=${prootInstalledPackageSmokeResult.exitCode}" +
            "\nproot installed package smoke stdout=${prootInstalledPackageSmokeResult.stdout}" +
            "\nproot installed package smoke stderr=${prootInstalledPackageSmokeResult.stderr}" +
            "\nprobe dlopen talloc=${nativeProbe.lineStartingWith("dlopen libtalloc.so")}" +
            "\nprobe dlopen proot=${nativeProbe.lineStartingWith("dlopen libalr_proot.so")}" +
            "\nproot loader=${prootCandidateResult.environment["PROOT_LOADER"]}" +
            "\nproot tmp=${prootCandidateResult.environment["PROOT_TMP_DIR"]}" +
            "\nproot verbose=${prootCandidateResult.environment["PROOT_VERBOSE"]}" +
            "\nhost gpu renderer=${hostGpuProbe.lineStartingWith("gl renderer=")}" +
            "\nhost gpu vendor=${hostGpuProbe.lineStartingWith("gl vendor=")}" +
            "\nhost gpu software renderer=${hostGpuProbe.lineStartingWith("host gpu software renderer=")}" +
            "\nhost gpu hardware candidate=${hostGpuProbe.lineStartingWith("host gpu hardware candidate=")}" +
            "\npermission INTERNET declared=$internetPermissionDeclared" +
            "\npermission ACCESS_NETWORK_STATE declared=$networkStatePermissionDeclared" +
            "\npermission broad storage declared=$broadStoragePermissionDeclared" +
            "\npermission runtime dangerous requested=false"

        val verboseReport = nativeRuntimeReport(
            packageName,
            applicationInfo.nativeLibraryDir,
            filesDir.absolutePath,
            cacheDir.absolutePath,
            rootfsManifest.name,
            "/bin/hello",
        ) + "\n\nrootfs install dir: ${rootfsPlan.rootfsDir.absolutePath}" +
            "\nrootfs marker: ${rootfsPlan.markerPath.absolutePath}" +
            "\nrootfs status: ${rootfsStatus.manifestName}/${rootfsStatus.assetPath} verified=${rootfsStatus.verified}" +
            " extracted=${rootfsStatus.extracted}" +
            "\nrootfs staged archive: ${rootfsStatus.stagedArchive.absolutePath}" +
            "\nrootfs extracted dir: ${rootfsStatus.rootfsDir.absolutePath}" +
            "\nrootfs marker=${rootfsStatus.markerPath.absolutePath}" +
            "\n\nnext executable backend: android-native-test-command" +
            "\npackaged command: ${applicationInfo.nativeLibraryDir}/libalr_test_command.so" +
            "\nnative command exit=${nativeCommandResult.exitCode}" +
            "\nnative command stdout=${nativeCommandResult.stdout}" +
            "\nnative command stderr=${nativeCommandResult.stderr}" +
            "\n\nnative library probe:" +
            "\n$nativeProbe" +
            "\n\nAndroid host GPU probe:" +
            "\n$hostGpuProbe" +
            "\n\nproot backend candidate: packaged native executable" +
            "\nproot actual env:" +
            prootCandidateResult.environment.entries.joinToString(separator = "") { "\n  ${it.key}=${it.value}" } +
            "\nproot candidate exit=${prootCandidateResult.exitCode}" +
            "\nproot candidate stdout=${prootCandidateResult.stdout}" +
            "\nproot candidate stderr=${prootCandidateResult.stderr}" +
            "\n\ndiagnostic note: libproot-loader.so is a PRoot loader executable, not a dlopen-able shared library; libtalloc.so is a dependency, not a standalone command. Direct crash probes are skipped in the default success report." +
            resultBlock("proot --version", prootCandidateResult) +
            resultBlock("proot -V", prootShortVersionResult) +
            resultBlock("proot --help", prootHelpResult) +
            resultBlock("proot no-env --version", prootNoEnvResult) +
            resultBlock("linker64 proot --version", prootViaLinkerResult) +
            resultBlock("proot hello quiet", prootHelloResult) +
            resultBlock("proot script", prootScriptResult) +
            resultBlock("proot shell -c", prootShellResult) +
            resultBlock("proot glibc", prootGlibcResult) +
            resultBlock("proot dash", prootDashResult) +
            resultBlock("proot id", prootIdResult) +
            resultBlock("proot dpkg --version", prootDpkgVersionResult) +
            resultBlock("proot dpkg --print-architecture", prootDpkgArchResult) +
            resultBlock("proot dpkg-query --version", prootDpkgQueryVersionResult) +
            resultBlock("proot dpkg-split --version", prootDpkgSplitVersionResult) +
            resultBlock("proot apt --version", prootAptVersionResult) +
            resultBlock("proot apt-get --version", prootAptGetVersionResult) +
            resultBlock("proot apt-cache --version", prootAptCacheVersionResult) +
            resultBlock("proot apt-config --version", prootAptConfigVersionResult) +
            resultBlock("proot dpkg -i local deb", prootDpkgInstallLocalResult) +
            resultBlock("proot installed package smoke", prootInstalledPackageSmokeResult) +
            resultBlock("proot guest gpu client", prootGuestGpuClientResult) +
            resultBlock("proot guest gpu ipc client", guestGpuIpcBridgeResult.clientResult) +
            resultBlock("proot guest gles shim smoke", prootGuestGlesShimSmokeResult) +
            resultBlock("proot guest wayland gui client", prootGuestWaylandGuiResult) +
            resultBlock("proot guest x11 gui client", prootGuestX11GuiResult) +
            resultBlock("proot guest wayland gui ipc client", guestWaylandGuiBridgeResult.clientResult) +
            resultBlock("proot guest x11 gui ipc client", guestX11GuiBridgeResult.clientResult) +
            optionalResultBlock("proot hello verbose on failure", prootHelloVerboseResult)

        val report = executionSummary + "\n\n--- verbose report ---\n" + verboseReport

        val view = TextView(this).apply {
            text = report
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
        }
        val surfaceView = SurfaceView(this).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    val encodedFrames = encodeSurfaceFrames(surfaceGpuCommands)
                    val surfaceReport = nativeRenderGpuSurfaceFrames(holder.surface, encodedFrames)
                    view.append("\n\n--- Linux guest Wayland/X11 GUI GPU surface renderer ---\n$surfaceReport")
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        }
        val surfaceHeight = (180 * resources.displayMetrics.density).toInt().coerceAtLeast(180)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(surfaceView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, surfaceHeight))
                addView(
                    ScrollView(this@MainActivity).apply { addView(view) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            },
        )
    }

    private data class GuestGpuCommand(
        val red: Float,
        val green: Float,
        val blue: Float,
        val tag: String,
        val protocol: String = "GPU",
        val seq: Int = 0,
    )

    private data class GuestGpuIpcBridgeResult(
        val host: String,
        val port: Int,
        val expectedFrames: Int,
        val commands: List<GuestGpuCommand>,
        val rawLines: List<String>,
        val error: String?,
        val clientResult: NativeCommandResult,
    )

    private fun runGuestGpuIpcBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GuestGpuIpcBridgeResult {
        val host = "127.0.0.1"
        val server = ServerSocket(0, 1, InetAddress.getByName(host)).apply { soTimeout = 3000 }
        val port = server.localPort
        val rawLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val acceptThread = thread(name = "alr-gpu-ipc-bridge", start = true) {
            try {
                server.use { srv ->
                    val socket = srv.accept()
                    socket.use { accepted ->
                        accepted.soTimeout = 3000
                        accepted.getInputStream().bufferedReader().useLines { lines ->
                            lines.forEach { rawLines += it }
                        }
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for guest gpu ipc client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = nativeCommandRunner.runProotRootfsGuestGpuClientIpc(rootfsDir, port)
        acceptThread.join(3500)
        if (acceptThread.isAlive) {
            errors += "accept thread still alive after join"
            server.close()
        }
        val commands = parseGuestGpuCommands(rawLines.joinToString("\n"))
        val expectedFrames = rawLines.firstOrNull { it.startsWith("ALR_GPU_IPC_HELLO ") }
            ?.substringAfter("frames=", "0")
            ?.substringBefore(" ")
            ?.toIntOrNull()
            ?: commands.size
        return GuestGpuIpcBridgeResult(
            host = host,
            port = port,
            expectedFrames = expectedFrames,
            commands = commands,
            rawLines = rawLines.toList(),
            error = errors.firstOrNull(),
            clientResult = clientResult,
        )
    }


    private fun runGuestGuiBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
        protocol: String,
    ): GuestGpuIpcBridgeResult {
        val host = "127.0.0.1"
        val server = ServerSocket(0, 1, InetAddress.getByName(host)).apply { soTimeout = 3000 }
        val port = server.localPort
        val rawLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val acceptThread = thread(name = "alr-gui-ipc-bridge-$protocol", start = true) {
            try {
                server.use { srv ->
                    val socket = srv.accept()
                    socket.use { accepted ->
                        accepted.soTimeout = 750
                        val reader = accepted.getInputStream().bufferedReader()
                        var expectedFrames = 0
                        while (true) {
                            val line = try {
                                reader.readLine()
                            } catch (timeout: SocketTimeoutException) {
                                if (expectedFrames > 0 && rawLines.count { it.startsWith("ALR_GUI_FRAME ") } >= expectedFrames) {
                                    null
                                } else {
                                    throw timeout
                                }
                            } ?: break
                            rawLines += line
                            if (line.startsWith("ALR_GUI_IPC_HELLO ")) {
                                expectedFrames = line.substringAfter("frames=", "0")
                                    .substringBefore(" ")
                                    .toIntOrNull()
                                    ?: 0
                            }
                            if (expectedFrames > 0 && rawLines.count { it.startsWith("ALR_GUI_FRAME ") } >= expectedFrames) {
                                break
                            }
                        }
                        val receivedFrames = rawLines.count { it.startsWith("ALR_GUI_FRAME ") }
                        val lossless = expectedFrames > 0 && receivedFrames == expectedFrames
                        val ack = "ALR_GUI_IPC_ACK protocol=$protocol received=$receivedFrames expected=$expectedFrames lossless=$lossless\n"
                        accepted.getOutputStream().write(ack.toByteArray())
                        accepted.getOutputStream().flush()
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for guest gui ipc client $protocol"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = nativeCommandRunner.runProotRootfsGuestGuiClientIpc(rootfsDir, protocol, port)
        acceptThread.join(3500)
        if (acceptThread.isAlive) {
            errors += "gui accept thread still alive after join $protocol"
            server.close()
        }
        val commands = parseGuestGuiCommands(rawLines.joinToString("\n"), protocol)
        val expectedFrames = rawLines.firstOrNull { it.startsWith("ALR_GUI_IPC_HELLO ") }
            ?.substringAfter("frames=", "0")
            ?.substringBefore(" ")
            ?.toIntOrNull()
            ?: commands.size
        return GuestGpuIpcBridgeResult(
            host = host,
            port = port,
            expectedFrames = expectedFrames,
            commands = commands,
            rawLines = rawLines.toList(),
            error = errors.firstOrNull(),
            clientResult = clientResult,
        )
    }

    private fun parseGuestGpuCommands(text: String): List<GuestGpuCommand> =
        text.lineSequence()
            .filter { it.startsWith("ALR_GPU_CLEAR ") }
            .mapNotNull { parseGuestGpuClearLine(it) }
            .toList()

    private fun parseGuestGuiCommands(text: String, expectedProtocol: String): List<GuestGpuCommand> =
        text.lineSequence()
            .filter { it.startsWith("ALR_GUI_FRAME ") }
            .mapNotNull { parseGuestGuiFrameLine(it, expectedProtocol) }
            .toList()

    private fun parseGuestGuiFrameLine(line: String, expectedProtocol: String): GuestGpuCommand? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 7 || parts[0] != "ALR_GUI_FRAME") return null
        val protocol = parts[1]
        if (protocol != expectedProtocol) return null
        val seq = parts[2].substringAfter("seq=", "0").toIntOrNull() ?: return null
        val red = parts[3].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val green = parts[4].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val blue = parts[5].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        return GuestGpuCommand(red, green, blue, parts[6], protocol, seq)
    }

    private fun parseGuestGlesShimCommand(stdout: String): GuestGpuCommand? =
        stdout.lineSequence()
            .firstOrNull { it.startsWith("ALR_GLES_SHIM_COMMAND ALR_GPU_CLEAR ") }
            ?.removePrefix("ALR_GLES_SHIM_COMMAND ")
            ?.let { parseGuestGpuClearLine(it) }

    private fun parseGuestGpuClearLine(line: String): GuestGpuCommand? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5 || parts[0] != "ALR_GPU_CLEAR") return null
        val red = parts[1].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val green = parts[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val blue = parts[3].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        return GuestGpuCommand(red, green, blue, parts[4])
    }

    private fun encodeSurfaceFrames(commands: List<GuestGpuCommand>): String =
        commands.joinToString(separator = "\n") { "${it.red} ${it.green} ${it.blue} ${it.protocol}-seq${it.seq}-${it.tag}" }

    private fun guiSeqGaps(commands: List<GuestGpuCommand>, expectedFrames: Int): Int {
        if (expectedFrames <= 0) return 0
        val seen = commands.map { it.seq }.toSet()
        return (1..expectedFrames).count { it !in seen }
    }

    private fun guiDuplicateSeqCount(commands: List<GuestGpuCommand>): Int =
        commands.groupingBy { it.seq }.eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }

    private fun guiOutOfOrder(commands: List<GuestGpuCommand>): Boolean =
        commands.map { it.seq }.zipWithNext().any { (left, right) -> right < left }

    private fun resultBlock(label: String, result: NativeCommandResult): String =
        "\n\n$label command=${result.command.absolutePath}" +
            "\n$label exit=${result.exitCode}" +
            "\n$label stdout=${result.stdout}" +
            "\n$label stderr=${result.stderr}"

    private fun optionalResultBlock(label: String, result: NativeCommandResult?): String =
        result?.let { resultBlock(label, it) } ?: "\n\n$label skipped=quiet rootfs execution passed"

    private fun String.lineStartingWith(prefix: String): String =
        lineSequence().firstOrNull { it.startsWith(prefix) } ?: "missing"

    private fun requestedPermissionNames(): Set<String> =
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toSet()
            .orEmpty()

    private external fun nativeRuntimeReport(
        packageName: String,
        nativeLibraryDir: String,
        appFilesDir: String,
        appCacheDir: String,
        rootfsName: String,
        program: String,
    ): String

    private external fun nativeLibraryProbe(nativeLibraryDir: String): String

    private external fun nativeHostGpuProbe(): String

    private external fun nativeRenderGpuSurfaceFrames(
        surface: android.view.Surface,
        encodedFrames: String,
    ): String
}
