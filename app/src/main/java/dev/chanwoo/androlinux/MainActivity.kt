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
            version = "bookworm-slim-2026-05-gui-gpu-v70",
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
        val handoffBenchmarkRepeatCount = 10
        val syscallBenchStatCount = 1000
        val syscallBenchOpenReadCount = 1000
        val syscallBenchFsMetaCount = 1000
        val syscallBenchSpawnCount = 20
        val nativeBionicForkBenchmarkResult = nativeCommandRunner.runNativeBionicForkBenchmark(handoffBenchmarkRepeatCount)
        val alrTrampolinePreflightResult = nativeCommandRunner.runAlrRuntimeTrampolinePreflight(rootfsStatus.rootfsDir, "/bin/hello")
        val alrTrampolineEntryProbeResult = nativeCommandRunner.runAlrRuntimeTrampolineEntryProbe(rootfsStatus.rootfsDir, "/bin/hello")
        val alrTrampolineLoaderHelpProbeResult = nativeCommandRunner.runAlrRuntimeTrampolineLoaderHelpProbe(rootfsStatus.rootfsDir)
        val alrTrampolineGlibcHelloProbeResult = nativeCommandRunner.runAlrRuntimeTrampolineGlibcHelloProbe(rootfsStatus.rootfsDir)
        val alrTrampolineCatOsReleaseProbeResult = nativeCommandRunner.runAlrRuntimeTrampolineCatOsReleaseProbe(rootfsStatus.rootfsDir)
        val alrTrampolineEntryBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineEntryBenchmark(
            rootfsStatus.rootfsDir,
            repeatCount = handoffBenchmarkRepeatCount,
        )
        val alrTrampolineGlibcHelloBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineGlibcHelloBenchmark(
            rootfsStatus.rootfsDir,
            repeatCount = handoffBenchmarkRepeatCount,
        )
        val prootCandidateResult = nativeCommandRunner.runProotCandidateSmokeTest()
        val prootShortVersionResult = nativeCommandRunner.runProotShortVersionProbe()
        val prootHelpResult = nativeCommandRunner.runProotHelpProbe()
        val prootNoEnvResult = nativeCommandRunner.runProotNoEnvVersionProbe()
        val prootViaLinkerResult = nativeCommandRunner.runProotViaLinkerVersionProbe()
        val prootHelloResult = nativeCommandRunner.runProotRootfsProgram(rootfsStatus.rootfsDir, "/bin/hello")
        val prootHelloLoopBenchmarkResult = nativeCommandRunner.runProotRootfsHelloLoopBenchmark(
            rootfsStatus.rootfsDir,
            repeatCount = handoffBenchmarkRepeatCount,
        )
        val prootScriptResult = nativeCommandRunner.runProotRootfsProgram(rootfsStatus.rootfsDir, "/bin/script-hello")
        val prootShellResult = nativeCommandRunner.runProotRootfsShell(
            rootfsStatus.rootfsDir,
            "echo shell-c ok; /bin/hello; /bin/cat /etc/os-release",
        )
        val prootGlibcResult = nativeCommandRunner.runProotRootfsProgram(rootfsStatus.rootfsDir, "/bin/glibc-hello")
        val prootGlibcLoopBenchmarkResult = nativeCommandRunner.runProotRootfsGlibcHelloLoopBenchmark(
            rootfsStatus.rootfsDir,
            repeatCount = handoffBenchmarkRepeatCount,
        )
        val prootSyscallStatBenchmarkResult = nativeCommandRunner.runProotRootfsSyscallBench(
            rootfsStatus.rootfsDir,
            "stat",
            syscallBenchStatCount,
        )
        val prootSyscallOpenReadBenchmarkResult = nativeCommandRunner.runProotRootfsSyscallBench(
            rootfsStatus.rootfsDir,
            "openread",
            syscallBenchOpenReadCount,
        )
        val prootSyscallFsMetaBenchmarkResult = nativeCommandRunner.runProotRootfsSyscallBench(
            rootfsStatus.rootfsDir,
            "fsmeta",
            syscallBenchFsMetaCount,
        )
        val prootSyscallSpawnBenchmarkResult = nativeCommandRunner.runProotRootfsSyscallBench(
            rootfsStatus.rootfsDir,
            "spawn",
            syscallBenchSpawnCount,
        )
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
        val alrDpkgVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineDpkgVersion(rootfsStatus.rootfsDir)
        val alrDpkgArchResult = nativeCommandRunner.runAlrRuntimeTrampolineDpkgPrintArchitecture(rootfsStatus.rootfsDir)
        val alrDpkgArchPreloadResult = nativeCommandRunner.runAlrRuntimeTrampolineDpkgPrintArchitecturePreload(rootfsStatus.rootfsDir)
        val alrShellDpkgArchPreloadResult = nativeCommandRunner.runAlrRuntimeTrampolineShellDpkgPrintArchitecturePreload(rootfsStatus.rootfsDir)
        val alrDpkgQueryVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineDpkgQueryVersion(rootfsStatus.rootfsDir)
        val alrAptVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineAptVersion(rootfsStatus.rootfsDir)
        val alrAptPreloadVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineAptVersionPreload(rootfsStatus.rootfsDir)
        val alrAptGetVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineAptGetVersion(rootfsStatus.rootfsDir)
        val alrAptGetPreloadVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineAptGetVersionPreload(rootfsStatus.rootfsDir)
        val alrAptCacheVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineAptCacheVersion(rootfsStatus.rootfsDir)
        val alrAptCachePreloadVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineAptCacheVersionPreload(rootfsStatus.rootfsDir)
        val alrAptCachePolicyPreloadResult = nativeCommandRunner.runAlrRuntimeTrampolineAptCachePolicyPreload(rootfsStatus.rootfsDir)
        val alrAptCacheStatsPreloadResult = nativeCommandRunner.runAlrRuntimeTrampolineAptCacheStatsPreload(rootfsStatus.rootfsDir)
        val alrAptCachePkgNamesPreloadResult = nativeCommandRunner.runAlrRuntimeTrampolineAptCachePkgNamesPreload(rootfsStatus.rootfsDir)
        val alrAptConfigVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineAptConfigVersion(rootfsStatus.rootfsDir)
        val alrAptConfigPreloadVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineAptConfigVersionPreload(rootfsStatus.rootfsDir)
        val alrSyscallStatBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineSyscallBench(
            rootfsStatus.rootfsDir,
            "stat",
            syscallBenchStatCount,
        )
        val alrSyscallOpenReadBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineSyscallBench(
            rootfsStatus.rootfsDir,
            "openread",
            syscallBenchOpenReadCount,
        )
        val alrSyscallFsMetaBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineSyscallBench(
            rootfsStatus.rootfsDir,
            "fsmeta",
            syscallBenchFsMetaCount,
        )
        val alrSyscallStatPreloadBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineSyscallBenchPreload(
            rootfsStatus.rootfsDir,
            "stat",
            syscallBenchStatCount,
        )
        val alrSyscallOpenReadPreloadBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineSyscallBenchPreload(
            rootfsStatus.rootfsDir,
            "openread",
            syscallBenchOpenReadCount,
        )
        val alrSyscallFsMetaPreloadBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineSyscallBenchPreload(
            rootfsStatus.rootfsDir,
            "fsmeta",
            syscallBenchFsMetaCount,
        )
        val alrSyscallSpawnBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineSyscallBench(
            rootfsStatus.rootfsDir,
            "spawn",
            syscallBenchSpawnCount,
        )
        val alrDpkgInstallLocalResult = nativeCommandRunner.runAlrRuntimeTrampolineDpkgInstallLocalSmoke(rootfsStatus.rootfsDir)
        val alrDpkgInstallLocalPreloadResult = nativeCommandRunner.runAlrRuntimeTrampolineDpkgInstallLocalSmokePreload(rootfsStatus.rootfsDir)
        val alrInstalledPackageSmokePreloadResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageSmokePreload(rootfsStatus.rootfsDir)
        val prootDpkgInstallLocalResult = nativeCommandRunner.runProotRootfsDpkgInstallLocalSmoke(rootfsStatus.rootfsDir)
        val prootInstalledPackageSmokeResult = nativeCommandRunner.runProotRootfsInstalledPackageSmoke(rootfsStatus.rootfsDir)
        val prootGuestGpuClientResult = nativeCommandRunner.runProotRootfsGuestGpuClient(rootfsStatus.rootfsDir)
        val guestGpuCommands = parseGuestGpuCommands(prootGuestGpuClientResult.stdout)
        val alrGuestGpuClientResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGpuClient(rootfsStatus.rootfsDir)
        val alrGuestGpuCommands = parseGuestGpuCommands(alrGuestGpuClientResult.stdout.alrHandoffStdoutText())
        val guestGpuIpcBridgeResult = runGuestGpuIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val alrGuestGpuIpcBridgeResult = runGuestGpuIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir, useAlr = true)
        val glesShimBenchmarkFrameCount = 32
        val glesShimDrawFrameCount = 32
        val glesDemoFrameCount = 60
        val glesProcDemoFrameCount = 45
        val prootGuestGlesShimSmokeResult = nativeCommandRunner.runProotRootfsGuestGlesShimSmoke(rootfsStatus.rootfsDir)
        val alrGuestGlesShimSmokeResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGlesShimSmoke(rootfsStatus.rootfsDir)
        val prootGuestGlesAbiSmokeResult = nativeCommandRunner.runProotRootfsGuestGlesAbiSmoke(rootfsStatus.rootfsDir)
        val alrGuestGlesAbiSmokeResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGlesAbiSmoke(rootfsStatus.rootfsDir)
        val prootGuestGlesDemoGearsResult = nativeCommandRunner.runProotRootfsGuestGlesDemoGears(rootfsStatus.rootfsDir, glesDemoFrameCount)
        val alrGuestGlesDemoGearsResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGlesDemoGears(rootfsStatus.rootfsDir, glesDemoFrameCount)
        val prootGuestGlesProcaddrDemoResult = nativeCommandRunner.runProotRootfsGuestGlesProcaddrDemo(rootfsStatus.rootfsDir, glesProcDemoFrameCount)
        val alrGuestGlesProcaddrDemoResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGlesProcaddrDemo(rootfsStatus.rootfsDir, glesProcDemoFrameCount)
        val prootGuestGlesShimBenchmarkResult = nativeCommandRunner.runProotRootfsGuestGlesShimBenchmark(rootfsStatus.rootfsDir, glesShimBenchmarkFrameCount)
        val alrGuestGlesShimBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGlesShimBenchmark(rootfsStatus.rootfsDir, glesShimBenchmarkFrameCount)
        val prootGuestGlesShimDrawBenchmarkResult = nativeCommandRunner.runProotRootfsGuestGlesShimDrawBenchmark(rootfsStatus.rootfsDir, glesShimDrawFrameCount)
        val alrGuestGlesShimDrawBenchmarkResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGlesShimDrawBenchmark(rootfsStatus.rootfsDir, glesShimDrawFrameCount)
        val prootGuestGlesShimStdout = prootGuestGlesShimSmokeResult.stdout
        val alrGuestGlesShimStdout = alrGuestGlesShimSmokeResult.stdout.alrHandoffStdoutText()
        val prootGuestGlesAbiStdout = prootGuestGlesAbiSmokeResult.stdout
        val alrGuestGlesAbiStdout = alrGuestGlesAbiSmokeResult.stdout.alrHandoffStdoutText()
        val prootGuestGlesDemoGearsStdout = prootGuestGlesDemoGearsResult.stdout
        val alrGuestGlesDemoGearsStdout = alrGuestGlesDemoGearsResult.stdout.alrHandoffStdoutText()
        val prootGuestGlesProcaddrDemoStdout = prootGuestGlesProcaddrDemoResult.stdout
        val alrGuestGlesProcaddrDemoStdout = alrGuestGlesProcaddrDemoResult.stdout.alrHandoffStdoutText()
        val prootGuestGlesShimBenchmarkStdout = prootGuestGlesShimBenchmarkResult.stdout
        val alrGuestGlesShimBenchmarkStdout = alrGuestGlesShimBenchmarkResult.stdout.alrHandoffStdoutText()
        val prootGuestGlesShimDrawBenchmarkStdout = prootGuestGlesShimDrawBenchmarkResult.stdout
        val alrGuestGlesShimDrawBenchmarkStdout = alrGuestGlesShimDrawBenchmarkResult.stdout.alrHandoffStdoutText()
        val guestGlesShimCommands = parseGuestGlesShimCommands(prootGuestGlesShimStdout)
        val alrGuestGlesShimCommands = parseGuestGlesShimCommands(alrGuestGlesShimStdout)
        val guestGlesAbiCommands = parseGuestGlesShimCommands(prootGuestGlesAbiStdout)
        val alrGuestGlesAbiCommands = parseGuestGlesShimCommands(alrGuestGlesAbiStdout)
        val guestGlesDemoGearsCommands = parseGuestGlesShimCommands(prootGuestGlesDemoGearsStdout)
        val alrGuestGlesDemoGearsCommands = parseGuestGlesShimCommands(alrGuestGlesDemoGearsStdout)
        val guestGlesProcaddrDemoCommands = parseGuestGlesShimCommands(prootGuestGlesProcaddrDemoStdout)
        val alrGuestGlesProcaddrDemoCommands = parseGuestGlesShimCommands(alrGuestGlesProcaddrDemoStdout)
        val guestGlesShimBenchmarkCommands = parseGuestGlesShimCommands(prootGuestGlesShimBenchmarkStdout)
        val alrGuestGlesShimBenchmarkCommands = parseGuestGlesShimCommands(alrGuestGlesShimBenchmarkStdout)
        val guestGlesShimDrawBenchmarkCommands = parseGuestGlesShimCommands(prootGuestGlesShimDrawBenchmarkStdout)
        val alrGuestGlesShimDrawBenchmarkCommands = parseGuestGlesShimCommands(alrGuestGlesShimDrawBenchmarkStdout)
        val prootGuestWaylandGuiResult = nativeCommandRunner.runProotRootfsGuestGuiClient(rootfsStatus.rootfsDir, "WAYLAND")
        val prootGuestX11GuiResult = nativeCommandRunner.runProotRootfsGuestGuiClient(rootfsStatus.rootfsDir, "X11")
        val alrGuestWaylandGuiResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGuiClient(rootfsStatus.rootfsDir, "WAYLAND")
        val alrGuestX11GuiResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGuiClient(rootfsStatus.rootfsDir, "X11")
        val guestWaylandGuiBridgeResult = runGuestGuiBridge(nativeCommandRunner, rootfsStatus.rootfsDir, "WAYLAND")
        val guestX11GuiBridgeResult = runGuestGuiBridge(nativeCommandRunner, rootfsStatus.rootfsDir, "X11")
        val alrGuestWaylandGuiBridgeResult = runGuestGuiBridge(nativeCommandRunner, rootfsStatus.rootfsDir, "WAYLAND", useAlr = true)
        val alrGuestX11GuiBridgeResult = runGuestGuiBridge(nativeCommandRunner, rootfsStatus.rootfsDir, "X11", useAlr = true)
        val nativeGlesBaselineCommands = buildNativeGlesBaselineCommands(glesShimBenchmarkFrameCount)
        val guestGuiSurfaceCommands = alrGuestWaylandGuiBridgeResult.commands + alrGuestX11GuiBridgeResult.commands +
            guestWaylandGuiBridgeResult.commands + guestX11GuiBridgeResult.commands
        val surfaceGpuCommands = buildList {
            addAll(guestGuiSurfaceCommands)
            addAll(if (alrGuestGpuIpcBridgeResult.commands.isNotEmpty()) alrGuestGpuIpcBridgeResult.commands else guestGpuIpcBridgeResult.commands)
            addAll(if (alrGuestGpuCommands.isNotEmpty()) alrGuestGpuCommands else guestGpuCommands)
            addAll(if (alrGuestGlesShimBenchmarkCommands.isNotEmpty()) alrGuestGlesShimBenchmarkCommands else guestGlesShimBenchmarkCommands)
            addAll(if (alrGuestGlesShimDrawBenchmarkCommands.isNotEmpty()) alrGuestGlesShimDrawBenchmarkCommands else guestGlesShimDrawBenchmarkCommands)
            addAll(if (alrGuestGlesAbiCommands.isNotEmpty()) alrGuestGlesAbiCommands else guestGlesAbiCommands)
            addAll(if (alrGuestGlesDemoGearsCommands.isNotEmpty()) alrGuestGlesDemoGearsCommands else guestGlesDemoGearsCommands)
            addAll(if (alrGuestGlesProcaddrDemoCommands.isNotEmpty()) alrGuestGlesProcaddrDemoCommands else guestGlesProcaddrDemoCommands)
            addAll(nativeGlesBaselineCommands)
            addAll(if (alrGuestGlesShimCommands.isNotEmpty()) alrGuestGlesShimCommands else guestGlesShimCommands)
            if (isEmpty()) {
                add(GuestGpuCommand(0.05f, 0.18f, 0.45f, "host-default"))
            }
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
        val rootfsSyscallBenchFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-syscall-bench")
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
        val rootfsGuestGlesAbiSmokeFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-gles-abi-smoke")
        val rootfsGuestGlesDemoGearsFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-gles-demo-gears")
        val rootfsGuestGlesProcaddrDemoFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-gles-procaddr-demo")
        val rootfsGuestGlesShimLibraryFile = File(rootfsStatus.rootfsDir, "usr/lib/androlinux/libalr_gles_shim.so")
        val rootfsGuestEglLibraryFile = File(rootfsStatus.rootfsDir, "usr/lib/androlinux/libEGL.so")
        val rootfsGuestGlesv2LibraryFile = File(rootfsStatus.rootfsDir, "usr/lib/androlinux/libGLESv2.so")
        val rootfsPathPreloadLibraryFile = File(rootfsStatus.rootfsDir, "usr/lib/androlinux/libalr_path_preload.so")
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
        val alrDpkgVersionExecutionPassed = alrDpkgVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrDpkgVersionResult.stdout.alrHandoffStdoutText().contains("Debian 'dpkg' package management program")
        val alrDpkgArchExecutionPassed = alrDpkgArchResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrDpkgArchResult.stdout.alrHandoffStdoutText().trim() == "arm64"
        val alrDpkgArchPreloadExecutionPassed = alrDpkgArchPreloadResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrDpkgArchPreloadResult.stdout.alrHandoffStdoutText().trim() == "arm64"
        val alrDpkgQueryExecutionPassed = alrDpkgQueryVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrDpkgQueryVersionResult.stdout.alrHandoffStdoutText().contains("Debian dpkg-query package management program")
        val alrAptVersionExecutionPassed = alrAptVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptVersionResult.stdout.alrHandoffStdoutText().contains("apt ") &&
            alrAptVersionResult.stdout.alrHandoffStdoutText().contains("arm64")
        val alrAptPreloadVersionExecutionPassed = alrAptPreloadVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptPreloadVersionResult.stdout.alrHandoffStdoutText().contains("apt ") &&
            alrAptPreloadVersionResult.stdout.alrHandoffStdoutText().contains("arm64")
        val alrAptGetVersionExecutionPassed = alrAptGetVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptGetVersionResult.stdout.alrHandoffStdoutText().contains("apt ") &&
            alrAptGetVersionResult.stdout.alrHandoffStdoutText().contains("arm64")
        val alrAptGetPreloadVersionExecutionPassed = alrAptGetPreloadVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptGetPreloadVersionResult.stdout.alrHandoffStdoutText().contains("apt ") &&
            alrAptGetPreloadVersionResult.stdout.alrHandoffStdoutText().contains("arm64")
        val alrAptCacheVersionExecutionPassed = alrAptCacheVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptCacheVersionResult.stdout.alrHandoffStdoutText().contains("apt ") &&
            alrAptCacheVersionResult.stdout.alrHandoffStdoutText().contains("arm64")
        val alrAptCachePreloadVersionExecutionPassed = alrAptCachePreloadVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptCachePreloadVersionResult.stdout.alrHandoffStdoutText().contains("apt ") &&
            alrAptCachePreloadVersionResult.stdout.alrHandoffStdoutText().contains("arm64")
        val alrAptCachePolicyPreloadExecutionPassed = alrAptCachePolicyPreloadResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptCachePolicyPreloadResult.stdout.alrHandoffStdoutText().contains("Package files:") &&
            alrAptCachePolicyPreloadResult.stdout.alrHandoffStdoutText().contains("/var/lib/dpkg/status")
        val alrAptCacheStatsPreloadExecutionPassed = alrAptCacheStatsPreloadResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptCacheStatsPreloadResult.stdout.alrHandoffStdoutText().contains("Total package names:") &&
            alrAptCacheStatsPreloadResult.stdout.alrHandoffStdoutText().contains("Total space accounted for:")
        val alrAptCachePkgNamesPreloadExecutionPassed = alrAptCachePkgNamesPreloadResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptCachePkgNamesPreloadResult.stdout.lineStartingWith("alr handoff path rewrite count=") == "alr handoff path rewrite count=0"
        val alrAptConfigVersionExecutionPassed = alrAptConfigVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptConfigVersionResult.stdout.alrHandoffStdoutText().contains("apt ") &&
            alrAptConfigVersionResult.stdout.alrHandoffStdoutText().contains("arm64")
        val alrAptConfigPreloadVersionExecutionPassed = alrAptConfigPreloadVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrAptConfigPreloadVersionResult.stdout.alrHandoffStdoutText().contains("apt ") &&
            alrAptConfigPreloadVersionResult.stdout.alrHandoffStdoutText().contains("arm64")
        val alrSyscallStatBenchmarkPassed = alrSyscallStatBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrSyscallStatBenchmarkResult.stdout.alrHandoffStdoutText().contains("ALR SYSCALL BENCH: PASS")
        val alrSyscallOpenReadBenchmarkPassed = alrSyscallOpenReadBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrSyscallOpenReadBenchmarkResult.stdout.alrHandoffStdoutText().contains("ALR SYSCALL BENCH: PASS")
        val alrSyscallFsMetaBenchmarkPassed = alrSyscallFsMetaBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrSyscallFsMetaBenchmarkResult.stdout.alrHandoffStdoutText().contains("ALR SYSCALL BENCH: PASS")
        val alrSyscallStatPreloadBenchmarkPassed = alrSyscallStatPreloadBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrSyscallStatPreloadBenchmarkResult.stdout.alrHandoffStdoutText().contains("ALR SYSCALL BENCH: PASS")
        val alrSyscallOpenReadPreloadBenchmarkPassed = alrSyscallOpenReadPreloadBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrSyscallOpenReadPreloadBenchmarkResult.stdout.alrHandoffStdoutText().contains("ALR SYSCALL BENCH: PASS")
        val alrSyscallFsMetaPreloadBenchmarkPassed = alrSyscallFsMetaPreloadBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrSyscallFsMetaPreloadBenchmarkResult.stdout.alrHandoffStdoutText().contains("ALR SYSCALL BENCH: PASS")
        val alrSyscallSpawnBenchmarkPassed = alrSyscallSpawnBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrSyscallSpawnBenchmarkResult.stdout.alrHandoffStdoutText().contains("ALR SYSCALL BENCH: PASS")
        val prootSyscallStatBenchmarkPassed = prootSyscallStatBenchmarkResult.exitCode == 0 &&
            prootSyscallStatBenchmarkResult.stdout.contains("ALR SYSCALL BENCH: PASS")
        val prootSyscallOpenReadBenchmarkPassed = prootSyscallOpenReadBenchmarkResult.exitCode == 0 &&
            prootSyscallOpenReadBenchmarkResult.stdout.contains("ALR SYSCALL BENCH: PASS")
        val prootSyscallFsMetaBenchmarkPassed = prootSyscallFsMetaBenchmarkResult.exitCode == 0 &&
            prootSyscallFsMetaBenchmarkResult.stdout.contains("ALR SYSCALL BENCH: PASS")
        val prootSyscallSpawnBenchmarkPassed = prootSyscallSpawnBenchmarkResult.exitCode == 0 &&
            prootSyscallSpawnBenchmarkResult.stdout.contains("ALR SYSCALL BENCH: PASS")
        val alrDpkgInstallLocalGuestOutput =
            alrDpkgInstallLocalResult.stdout.alrHandoffStdoutText() + "\n" +
                alrDpkgInstallLocalResult.stdout.alrHandoffStderrText()
        val alrDpkgInstallLocalPreloadGuestOutput =
            alrDpkgInstallLocalPreloadResult.stdout.alrHandoffStdoutText() + "\n" +
                alrDpkgInstallLocalPreloadResult.stdout.alrHandoffStderrText()
        val alrDpkgLocalInstallExecutionPassed = alrDpkgInstallLocalResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            !alrDpkgInstallLocalGuestOutput.contains("dpkg: error") &&
            (alrDpkgInstallLocalGuestOutput.contains("Setting up alr-smoke") ||
                alrDpkgInstallLocalGuestOutput.contains("alr-smoke (1.0)") ||
                alrDpkgInstallLocalGuestOutput.contains("Selecting previously unselected package alr-smoke"))
        val alrDpkgLocalInstallPreloadExecutionPassed = alrDpkgInstallLocalPreloadResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            !alrDpkgInstallLocalPreloadGuestOutput.contains("dpkg: error") &&
            (alrDpkgInstallLocalPreloadGuestOutput.contains("Setting up alr-smoke") ||
                alrDpkgInstallLocalPreloadGuestOutput.contains("alr-smoke (1.0)") ||
                alrDpkgInstallLocalPreloadGuestOutput.contains("Selecting previously unselected package alr-smoke"))
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
        val alrShellDpkgArchPreloadExecutionPassed =
            alrShellDpkgArchPreloadResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                alrShellDpkgArchPreloadResult.stdout.alrHandoffStdoutText().trim() == "arm64"
        val alrInstalledPackagePreloadExecutionPassed =
            alrInstalledPackageSmokePreloadResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                alrInstalledPackageSmokePreloadResult.stdout.alrHandoffStdoutText().contains("alr local deb package smoke ok") &&
                alrInstalledPackageSmokePreloadResult.stdout.alrHandoffStdoutText().contains("ALR_SMOKE_ARCH=arm64") &&
                alrInstalledPackageSmokePreloadResult.stdout.alrHandoffStdoutText().contains("ALR_SMOKE_ENV_ARCH=arm64")
        val guestGpuBridgeCommandPassed = prootGuestGpuClientResult.exitCode == 0 &&
            prootGuestGpuClientResult.stdout.contains("alr guest gpu client ok") &&
            guestGpuCommands.isNotEmpty()
        val alrGuestGpuBridgeCommandPassed = alrGuestGpuClientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGpuClientResult.stdout.alrHandoffStdoutText().contains("alr guest gpu client ok") &&
            alrGuestGpuCommands.isNotEmpty()
        val guestGpuIpcBridgePassed = guestGpuIpcBridgeResult.clientResult.exitCode == 0 &&
            guestGpuIpcBridgeResult.commands.size == guestGpuIpcBridgeResult.expectedFrames &&
            guestGpuIpcBridgeResult.expectedFrames > 0 &&
            guestGpuIpcBridgeResult.error == null
        val alrGuestGpuIpcBridgePassed = alrGuestGpuIpcBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGpuIpcBridgeResult.commands.size == alrGuestGpuIpcBridgeResult.expectedFrames &&
            alrGuestGpuIpcBridgeResult.expectedFrames > 0 &&
            alrGuestGpuIpcBridgeResult.error == null
        val guestGlesShimSmokePassed = prootGuestGlesShimSmokeResult.exitCode == 0 &&
            prootGuestGlesShimSmokeResult.stdout.contains("alr guest gles shim smoke ok") &&
            prootGuestGlesShimSmokeResult.stdout.contains("ALR_GLES_SHIM_LOAD ok") &&
            guestGlesShimCommands.isNotEmpty()
        val alrGuestGlesShimSmokePassed = alrGuestGlesShimSmokeResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGlesShimStdout.contains("alr guest gles shim smoke ok") &&
            alrGuestGlesShimStdout.contains("ALR_GLES_SHIM_LOAD ok") &&
            alrGuestGlesShimCommands.isNotEmpty()
        val guestGlesAbiSmokePassed = prootGuestGlesAbiSmokeResult.exitCode == 0 &&
            prootGuestGlesAbiStdout.contains("alr guest gles abi smoke ok") &&
            prootGuestGlesAbiStdout.contains("ALR_GLES_ABI_LIBS visible libEGL.so libGLESv2.so") &&
            guestGlesAbiCommands.isNotEmpty()
        val alrGuestGlesAbiSmokePassed = alrGuestGlesAbiSmokeResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGlesAbiStdout.contains("alr guest gles abi smoke ok") &&
            alrGuestGlesAbiStdout.contains("ALR_GLES_ABI_LIBS visible libEGL.so libGLESv2.so") &&
            alrGuestGlesAbiCommands.isNotEmpty()
        val guestGlesDemoGearsPassed = prootGuestGlesDemoGearsResult.exitCode == 0 &&
            prootGuestGlesDemoGearsStdout.contains("ALR_GLES_DEMO_KIND es2gears-like-triangle-strip-subset") &&
            prootGuestGlesDemoGearsStdout.contains("ALR_GLES_DEMO_WORKLOAD requested=$glesDemoFrameCount submitted=$glesDemoFrameCount") &&
            guestGlesDemoGearsCommands.count { it.protocol == "GLES_DRAW" } == glesDemoFrameCount
        val alrGuestGlesDemoGearsPassed = alrGuestGlesDemoGearsResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGlesDemoGearsStdout.contains("ALR_GLES_DEMO_KIND es2gears-like-triangle-strip-subset") &&
            alrGuestGlesDemoGearsStdout.contains("ALR_GLES_DEMO_WORKLOAD requested=$glesDemoFrameCount submitted=$glesDemoFrameCount") &&
            alrGuestGlesDemoGearsCommands.count { it.protocol == "GLES_DRAW" } == glesDemoFrameCount
        val guestGlesProcaddrDemoPassed = prootGuestGlesProcaddrDemoResult.exitCode == 0 &&
            prootGuestGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset") &&
            prootGuestGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_WORKLOAD requested=$glesProcDemoFrameCount submitted=$glesProcDemoFrameCount") &&
            guestGlesProcaddrDemoCommands.count { it.protocol == "GLES_DRAW" } == glesProcDemoFrameCount
        val alrGuestGlesProcaddrDemoPassed = alrGuestGlesProcaddrDemoResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset") &&
            alrGuestGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_WORKLOAD requested=$glesProcDemoFrameCount submitted=$glesProcDemoFrameCount") &&
            alrGuestGlesProcaddrDemoCommands.count { it.protocol == "GLES_DRAW" } == glesProcDemoFrameCount
        val guestGlesShimBenchmarkPassed = prootGuestGlesShimBenchmarkResult.exitCode == 0 &&
            prootGuestGlesShimBenchmarkStdout.contains("ALR_GLES_FRAME_WORKLOAD requested=$glesShimBenchmarkFrameCount submitted=$glesShimBenchmarkFrameCount") &&
            guestGlesShimBenchmarkCommands.size == glesShimBenchmarkFrameCount
        val alrGuestGlesShimBenchmarkPassed = alrGuestGlesShimBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGlesShimBenchmarkStdout.contains("ALR_GLES_FRAME_WORKLOAD requested=$glesShimBenchmarkFrameCount submitted=$glesShimBenchmarkFrameCount") &&
            alrGuestGlesShimBenchmarkCommands.size == glesShimBenchmarkFrameCount
        val guestGlesShimDrawBenchmarkPassed = prootGuestGlesShimDrawBenchmarkResult.exitCode == 0 &&
            prootGuestGlesShimDrawBenchmarkStdout.contains("ALR_GLES_DRAW_WORKLOAD requested=$glesShimDrawFrameCount submitted=$glesShimDrawFrameCount") &&
            guestGlesShimDrawBenchmarkCommands.count { it.protocol == "GLES_DRAW" } == glesShimDrawFrameCount
        val alrGuestGlesShimDrawBenchmarkPassed = alrGuestGlesShimDrawBenchmarkResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGlesShimDrawBenchmarkStdout.contains("ALR_GLES_DRAW_WORKLOAD requested=$glesShimDrawFrameCount submitted=$glesShimDrawFrameCount") &&
            alrGuestGlesShimDrawBenchmarkCommands.count { it.protocol == "GLES_DRAW" } == glesShimDrawFrameCount
        val guestGlesShimInitPassed = (guestGlesShimSmokePassed && prootGuestGlesShimStdout.hasGlesApiSteps("eglGetDisplay", "eglInitialize", "eglChooseConfig")) ||
            (alrGuestGlesShimSmokePassed && alrGuestGlesShimStdout.hasGlesApiSteps("eglGetDisplay", "eglInitialize", "eglChooseConfig"))
        val guestGlesShimContextPassed = (guestGlesShimSmokePassed && prootGuestGlesShimStdout.hasGlesApiSteps("eglCreateContext", "eglMakeCurrent")) ||
            (alrGuestGlesShimSmokePassed && alrGuestGlesShimStdout.hasGlesApiSteps("eglCreateContext", "eglMakeCurrent"))
        val guestGlesShimClearPassed = (guestGlesShimSmokePassed && prootGuestGlesShimStdout.hasGlesApiSteps("glViewport", "glClearColor", "glClear")) ||
            (alrGuestGlesShimSmokePassed && alrGuestGlesShimStdout.hasGlesApiSteps("glViewport", "glClearColor", "glClear"))
        val guestGlesShimSwapPassed = (guestGlesShimSmokePassed && prootGuestGlesShimStdout.hasGlesApiSteps("eglSwapBuffers")) ||
            (alrGuestGlesShimSmokePassed && alrGuestGlesShimStdout.hasGlesApiSteps("eglSwapBuffers"))
        val guestGlesShimDrawApiPassed = (guestGlesShimSmokePassed && prootGuestGlesShimStdout.hasGlesApiSteps("glUseProgram", "glEnableVertexAttribArray", "glVertexAttribPointer", "glDrawArrays")) ||
            (alrGuestGlesShimSmokePassed && alrGuestGlesShimStdout.hasGlesApiSteps("glUseProgram", "glEnableVertexAttribArray", "glVertexAttribPointer", "glDrawArrays")) ||
            guestGlesShimDrawBenchmarkPassed ||
            alrGuestGlesShimDrawBenchmarkPassed
        val alrGuestWaylandGuiCommandPassed = alrGuestWaylandGuiResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestWaylandGuiResult.stdout.alrHandoffStdoutText().contains("alr-wayland-gpu-client ok")
        val alrGuestX11GuiCommandPassed = alrGuestX11GuiResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestX11GuiResult.stdout.alrHandoffStdoutText().contains("alr-x11-gpu-client ok")
        val guestWaylandGuiBridgePassed = guestWaylandGuiBridgeResult.clientResult.exitCode == 0 &&
            guestWaylandGuiBridgeResult.commands.size == guestWaylandGuiBridgeResult.expectedFrames &&
            guestWaylandGuiBridgeResult.expectedFrames > 0 &&
            guestWaylandGuiBridgeResult.error == null
        val guestX11GuiBridgePassed = guestX11GuiBridgeResult.clientResult.exitCode == 0 &&
            guestX11GuiBridgeResult.commands.size == guestX11GuiBridgeResult.expectedFrames &&
            guestX11GuiBridgeResult.expectedFrames > 0 &&
            guestX11GuiBridgeResult.error == null
        val alrGuestWaylandGuiBridgePassed = alrGuestWaylandGuiBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestWaylandGuiBridgeResult.commands.size == alrGuestWaylandGuiBridgeResult.expectedFrames &&
            alrGuestWaylandGuiBridgeResult.expectedFrames > 0 &&
            alrGuestWaylandGuiBridgeResult.error == null
        val alrGuestX11GuiBridgePassed = alrGuestX11GuiBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestX11GuiBridgeResult.commands.size == alrGuestX11GuiBridgeResult.expectedFrames &&
            alrGuestX11GuiBridgeResult.expectedFrames > 0 &&
            alrGuestX11GuiBridgeResult.error == null
        val hostGpuHardwareCandidate = hostGpuProbe.lineStartingWith("host gpu hardware candidate=") == "host gpu hardware candidate=true"

        val executionSummary = "build: 0.4.70-preload-env-child-chain" +
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
            "\nALR DPKG VERSION EXECUTION: ${if (alrDpkgVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR DPKG ARCH EXECUTION: ${if (alrDpkgArchExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR DPKG ARCH PRELOAD EXECUTION: ${if (alrDpkgArchPreloadExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR DPKG QUERY EXECUTION: ${if (alrDpkgQueryExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT VERSION EXECUTION: ${if (alrAptVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT PRELOAD EXECUTION: ${if (alrAptPreloadVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-GET VERSION EXECUTION: ${if (alrAptGetVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-GET PRELOAD EXECUTION: ${if (alrAptGetPreloadVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-CACHE VERSION EXECUTION: ${if (alrAptCacheVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-CACHE PRELOAD EXECUTION: ${if (alrAptCachePreloadVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-CACHE POLICY PRELOAD EXECUTION: ${if (alrAptCachePolicyPreloadExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-CACHE STATS PRELOAD EXECUTION: ${if (alrAptCacheStatsPreloadExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-CACHE PKGNAMES PRELOAD EXECUTION: ${if (alrAptCachePkgNamesPreloadExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-CONFIG VERSION EXECUTION: ${if (alrAptConfigVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR APT-CONFIG PRELOAD EXECUTION: ${if (alrAptConfigPreloadVersionExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR SYSCALL STAT BENCH EXECUTION: ${if (alrSyscallStatBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nALR SYSCALL OPENREAD BENCH EXECUTION: ${if (alrSyscallOpenReadBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nALR SYSCALL FSMETA BENCH EXECUTION: ${if (alrSyscallFsMetaBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nALR SYSCALL STAT PRELOAD BENCH EXECUTION: ${if (alrSyscallStatPreloadBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nALR SYSCALL OPENREAD PRELOAD BENCH EXECUTION: ${if (alrSyscallOpenReadPreloadBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nALR SYSCALL FSMETA PRELOAD BENCH EXECUTION: ${if (alrSyscallFsMetaPreloadBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nALR SYSCALL SPAWN BENCH EXECUTION: ${if (alrSyscallSpawnBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nPROOT SYSCALL STAT BENCH EXECUTION: ${if (prootSyscallStatBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nPROOT SYSCALL OPENREAD BENCH EXECUTION: ${if (prootSyscallOpenReadBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nPROOT SYSCALL FSMETA BENCH EXECUTION: ${if (prootSyscallFsMetaBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nPROOT SYSCALL SPAWN BENCH EXECUTION: ${if (prootSyscallSpawnBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nALR DPKG LOCAL INSTALL EXECUTION: ${if (alrDpkgLocalInstallExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR DPKG LOCAL INSTALL PRELOAD EXECUTION: ${if (alrDpkgLocalInstallPreloadExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR SHELL DPKG ARCH PRELOAD EXECUTION: ${if (alrShellDpkgArchPreloadExecutionPassed) "PASS" else "FAIL"}" +
            "\nDPKG LOCAL INSTALL EXECUTION: ${if (dpkgLocalInstallExecutionPassed) "PASS" else "FAIL"}" +
            "\nINSTALLED PACKAGE EXECUTION: ${if (installedPackageExecutionPassed) "PASS" else "FAIL"}" +
            "\nALR INSTALLED PACKAGE PRELOAD EXECUTION: ${if (alrInstalledPackagePreloadExecutionPassed) "PASS" else "FAIL"}" +
            "\nHOST GPU EGL/GLES EXECUTION: ${if (hostGpuHardwareCandidate) "PASS" else "FAIL"}" +
            "\nHOST GPU SURFACE EXECUTION: PENDING_SURFACE_CALLBACK" +
            "\nGUEST GPU BRIDGE COMMAND EXECUTION: ${if (guestGpuBridgeCommandPassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GPU BRIDGE COMMAND EXECUTION: ${if (alrGuestGpuBridgeCommandPassed) "PASS" else "FAIL"}" +
            "\nGUEST GPU IPC BRIDGE EXECUTION: ${if (guestGpuIpcBridgePassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GPU IPC BRIDGE EXECUTION: ${if (alrGuestGpuIpcBridgePassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES SHIM SMOKE EXECUTION: ${if (guestGlesShimSmokePassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GLES SHIM SMOKE EXECUTION: ${if (alrGuestGlesShimSmokePassed) "PASS" else "FAIL"}" +
            "\nGUEST EGL/GLES ABI LIB EXECUTION: ${if (guestGlesAbiSmokePassed) "PASS" else "FAIL"}" +
            "\nALR GUEST EGL/GLES ABI LIB EXECUTION: ${if (alrGuestGlesAbiSmokePassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES DEMO GEARS EXECUTION: ${if (guestGlesDemoGearsPassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GLES DEMO GEARS EXECUTION: ${if (alrGuestGlesDemoGearsPassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES PROCADDR DEMO EXECUTION: ${if (guestGlesProcaddrDemoPassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GLES PROCADDR DEMO EXECUTION: ${if (alrGuestGlesProcaddrDemoPassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES SHIM FRAME WORKLOAD EXECUTION: ${if (guestGlesShimBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GLES SHIM FRAME WORKLOAD EXECUTION: ${if (alrGuestGlesShimBenchmarkPassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES DRAW VIA SHIM EXECUTION: ${if (guestGlesShimDrawBenchmarkPassed || guestGlesShimDrawApiPassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GLES DRAW VIA SHIM EXECUTION: ${if (alrGuestGlesShimDrawBenchmarkPassed || guestGlesShimDrawApiPassed) "PASS" else "FAIL"}" +
            "\nGUEST EGL INIT VIA SHIM EXECUTION: ${if (guestGlesShimInitPassed) "PASS" else "FAIL"}" +
            "\nGUEST EGL CONTEXT VIA SHIM EXECUTION: ${if (guestGlesShimContextPassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES CLEAR VIA SHIM EXECUTION: ${if (guestGlesShimClearPassed) "PASS" else "FAIL"}" +
            "\nGUEST EGL SWAP COMMAND VIA SHIM EXECUTION: ${if (guestGlesShimSwapPassed) "PASS" else "FAIL"}" +
            "\nGUEST GPU MULTI-FRAME SURFACE EXECUTION: PENDING_SURFACE_CALLBACK" +
            "\nALR GUEST WAYLAND GUI COMMAND EXECUTION: ${if (alrGuestWaylandGuiCommandPassed) "PASS" else "FAIL"}" +
            "\nALR GUEST X11 GUI COMMAND EXECUTION: ${if (alrGuestX11GuiCommandPassed) "PASS" else "FAIL"}" +
            "\nGUEST WAYLAND GUI GPU BRIDGE EXECUTION: ${if (guestWaylandGuiBridgePassed) "PASS" else "FAIL"}" +
            "\nGUEST X11 GUI GPU BRIDGE EXECUTION: ${if (guestX11GuiBridgePassed) "PASS" else "FAIL"}" +
            "\nALR GUEST WAYLAND GUI GPU BRIDGE EXECUTION: ${if (alrGuestWaylandGuiBridgePassed) "PASS" else "FAIL"}" +
            "\nALR GUEST X11 GUI GPU BRIDGE EXECUTION: ${if (alrGuestX11GuiBridgePassed) "PASS" else "FAIL"}" +
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
            "\nrootfs /usr/bin/alr-syscall-bench exists=${rootfsSyscallBenchFile.isFile} executable=${rootfsSyscallBenchFile.canExecute()} bytes=${rootfsSyscallBenchFile.length()}" +
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
            "\nrootfs /usr/bin/alr-gles-abi-smoke exists=${rootfsGuestGlesAbiSmokeFile.isFile} executable=${rootfsGuestGlesAbiSmokeFile.canExecute()} bytes=${rootfsGuestGlesAbiSmokeFile.length()}" +
            "\nrootfs /usr/bin/alr-gles-demo-gears exists=${rootfsGuestGlesDemoGearsFile.isFile} executable=${rootfsGuestGlesDemoGearsFile.canExecute()} bytes=${rootfsGuestGlesDemoGearsFile.length()}" +
            "\nrootfs /usr/bin/alr-gles-procaddr-demo exists=${rootfsGuestGlesProcaddrDemoFile.isFile} executable=${rootfsGuestGlesProcaddrDemoFile.canExecute()} bytes=${rootfsGuestGlesProcaddrDemoFile.length()}" +
            "\nrootfs /usr/lib/androlinux/libalr_gles_shim.so exists=${rootfsGuestGlesShimLibraryFile.isFile} executable=${rootfsGuestGlesShimLibraryFile.canExecute()} bytes=${rootfsGuestGlesShimLibraryFile.length()}" +
            "\nrootfs /usr/lib/androlinux/libEGL.so exists=${rootfsGuestEglLibraryFile.isFile} executable=${rootfsGuestEglLibraryFile.canExecute()} bytes=${rootfsGuestEglLibraryFile.length()}" +
            "\nrootfs /usr/lib/androlinux/libGLESv2.so exists=${rootfsGuestGlesv2LibraryFile.isFile} executable=${rootfsGuestGlesv2LibraryFile.canExecute()} bytes=${rootfsGuestGlesv2LibraryFile.length()}" +
            "\nrootfs /usr/lib/androlinux/libalr_path_preload.so exists=${rootfsPathPreloadLibraryFile.isFile} executable=${rootfsPathPreloadLibraryFile.canExecute()} bytes=${rootfsPathPreloadLibraryFile.length()}" +
            "\nrootfs /usr/bin/alr-wayland-gpu-client exists=${rootfsWaylandGuiClientFile.isFile} executable=${rootfsWaylandGuiClientFile.canExecute()} bytes=${rootfsWaylandGuiClientFile.length()}" +
            "\nrootfs /usr/bin/alr-x11-gpu-client exists=${rootfsX11GuiClientFile.isFile} executable=${rootfsX11GuiClientFile.canExecute()} bytes=${rootfsX11GuiClientFile.length()}" +
            "\nproot guest gpu client exit=${prootGuestGpuClientResult.exitCode}" +
            "\nproot guest gpu client stdout=${prootGuestGpuClientResult.stdout}" +
            "\nproot guest gpu client stderr=${prootGuestGpuClientResult.stderr}" +
            "\nalr guest gpu client handoff=${alrGuestGpuClientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest gpu client stdout=${alrGuestGpuClientResult.stdout.alrHandoffStdoutText()}" +
            "\nguest gpu stdout commands parsed=${guestGpuCommands.size}" +
            "\nalr guest gpu stdout commands parsed=${alrGuestGpuCommands.size}" +
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
            "\nalr guest gpu ipc received frames=${alrGuestGpuIpcBridgeResult.commands.size}" +
            "\nalr guest gpu ipc lossless=${alrGuestGpuIpcBridgeResult.expectedFrames > 0 && alrGuestGpuIpcBridgeResult.expectedFrames == alrGuestGpuIpcBridgeResult.commands.size}" +
            "\nalr guest gpu ipc raw=${alrGuestGpuIpcBridgeResult.rawLines.joinToString("|")}" +
            "\nalr guest gpu ipc error=${alrGuestGpuIpcBridgeResult.error ?: "none"}" +
            "\nalr guest gpu ipc client handoff=${alrGuestGpuIpcBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest gpu ipc client stdout=${alrGuestGpuIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nproot guest gles shim smoke exit=${prootGuestGlesShimSmokeResult.exitCode}" +
            "\nproot guest gles shim smoke stdout=${prootGuestGlesShimSmokeResult.stdout}" +
            "\nproot guest gles shim smoke stderr=${prootGuestGlesShimSmokeResult.stderr}" +
            "\nguest gles shim command parsed count=${guestGlesShimCommands.size}" +
            "\nproot guest gles abi smoke exit=${prootGuestGlesAbiSmokeResult.exitCode}" +
            "\nproot guest gles abi smoke stdout=${prootGuestGlesAbiStdout}" +
            "\nproot guest gles abi smoke stderr=${prootGuestGlesAbiSmokeResult.stderr}" +
            "\nguest gles abi command parsed count=${guestGlesAbiCommands.size}" +
            "\nproot guest gles demo gears exit=${prootGuestGlesDemoGearsResult.exitCode}" +
            "\nproot guest gles demo gears stdout=${prootGuestGlesDemoGearsStdout}" +
            "\nproot guest gles demo gears stderr=${prootGuestGlesDemoGearsResult.stderr}" +
            "\nguest gles demo gears command parsed count=${guestGlesDemoGearsCommands.size}" +
            "\nproot guest gles procaddr demo exit=${prootGuestGlesProcaddrDemoResult.exitCode}" +
            "\nproot guest gles procaddr demo stdout=${prootGuestGlesProcaddrDemoStdout}" +
            "\nproot guest gles procaddr demo stderr=${prootGuestGlesProcaddrDemoResult.stderr}" +
            "\nguest gles procaddr demo command parsed count=${guestGlesProcaddrDemoCommands.size}" +
            "\nguest gles shim frame workload requested=$glesShimBenchmarkFrameCount" +
            "\nguest gles shim frame workload elapsed ms=${prootGuestGlesShimBenchmarkResult.elapsedMs}" +
            "\nguest gles shim frame workload commands=${guestGlesShimBenchmarkCommands.size}" +
            "\nguest gles shim frame workload stdout=${prootGuestGlesShimBenchmarkResult.stdout}" +
            "\nguest gles shim draw workload requested=$glesShimDrawFrameCount" +
            "\nguest gles shim draw workload elapsed ms=${prootGuestGlesShimDrawBenchmarkResult.elapsedMs}" +
            "\nguest gles shim draw workload commands=${guestGlesShimDrawBenchmarkCommands.count { it.protocol == "GLES_DRAW" }}" +
            "\nguest gles shim draw workload stdout=${prootGuestGlesShimDrawBenchmarkStdout}" +
            "\nnative gles baseline frame workload commands=${nativeGlesBaselineCommands.size}" +
            "\nalr guest gles shim smoke handoff=${alrGuestGlesShimSmokeResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest gles shim smoke path rewrite=${alrGuestGlesShimSmokeResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr guest gles shim smoke stdout=${alrGuestGlesShimSmokeResult.stdout.alrHandoffStdoutText()}" +
            "\nalr guest gles shim command parsed count=${alrGuestGlesShimCommands.size}" +
            "\nalr guest gles abi smoke handoff=${alrGuestGlesAbiSmokeResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest gles abi smoke stdout=${alrGuestGlesAbiStdout}" +
            "\nalr guest gles abi command parsed count=${alrGuestGlesAbiCommands.size}" +
            "\nalr guest gles demo gears handoff=${alrGuestGlesDemoGearsResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest gles demo gears stdout=${alrGuestGlesDemoGearsStdout}" +
            "\nalr guest gles demo gears command parsed count=${alrGuestGlesDemoGearsCommands.size}" +
            "\nalr guest gles procaddr demo handoff=${alrGuestGlesProcaddrDemoResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest gles procaddr demo stdout=${alrGuestGlesProcaddrDemoStdout}" +
            "\nalr guest gles procaddr demo command parsed count=${alrGuestGlesProcaddrDemoCommands.size}" +
            "\nalr guest gles shim frame workload elapsed ms=${alrGuestGlesShimBenchmarkResult.elapsedMs}" +
            "\nalr guest gles shim frame workload commands=${alrGuestGlesShimBenchmarkCommands.size}" +
            "\nalr guest gles shim frame workload handoff=${alrGuestGlesShimBenchmarkResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest gles shim frame workload stdout=${alrGuestGlesShimBenchmarkStdout}" +
            "\nalr guest gles shim draw workload elapsed ms=${alrGuestGlesShimDrawBenchmarkResult.elapsedMs}" +
            "\nalr guest gles shim draw workload commands=${alrGuestGlesShimDrawBenchmarkCommands.count { it.protocol == "GLES_DRAW" }}" +
            "\nalr guest gles shim draw workload handoff=${alrGuestGlesShimDrawBenchmarkResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest gles shim draw workload stdout=${alrGuestGlesShimDrawBenchmarkStdout}" +
            "\nproot guest wayland gui client exit=${prootGuestWaylandGuiResult.exitCode}" +
            "\nproot guest wayland gui client stdout=${prootGuestWaylandGuiResult.stdout}" +
            "\nproot guest x11 gui client exit=${prootGuestX11GuiResult.exitCode}" +
            "\nproot guest x11 gui client stdout=${prootGuestX11GuiResult.stdout}" +
            "\nalr guest wayland gui client handoff=${alrGuestWaylandGuiResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest wayland gui client stdout=${alrGuestWaylandGuiResult.stdout.alrHandoffStdoutText()}" +
            "\nalr guest x11 gui client handoff=${alrGuestX11GuiResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest x11 gui client stdout=${alrGuestX11GuiResult.stdout.alrHandoffStdoutText()}" +
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
            "\nalr guest wayland gui ipc received frames=${alrGuestWaylandGuiBridgeResult.commands.size}" +
            "\nalr guest wayland gui ipc lossless=${alrGuestWaylandGuiBridgeResult.expectedFrames > 0 && alrGuestWaylandGuiBridgeResult.expectedFrames == alrGuestWaylandGuiBridgeResult.commands.size}" +
            "\nalr guest wayland gui ipc raw=${alrGuestWaylandGuiBridgeResult.rawLines.joinToString("|")}" +
            "\nalr guest wayland gui ipc error=${alrGuestWaylandGuiBridgeResult.error ?: "none"}" +
            "\nalr guest wayland gui ipc client handoff=${alrGuestWaylandGuiBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr guest x11 gui ipc received frames=${alrGuestX11GuiBridgeResult.commands.size}" +
            "\nalr guest x11 gui ipc lossless=${alrGuestX11GuiBridgeResult.expectedFrames > 0 && alrGuestX11GuiBridgeResult.expectedFrames == alrGuestX11GuiBridgeResult.commands.size}" +
            "\nalr guest x11 gui ipc raw=${alrGuestX11GuiBridgeResult.rawLines.joinToString("|")}" +
            "\nalr guest x11 gui ipc error=${alrGuestX11GuiBridgeResult.error ?: "none"}" +
            "\nalr guest x11 gui ipc client handoff=${alrGuestX11GuiBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nsurface gpu command source frames=${surfaceGpuCommands.size}" +
            "\nproot dpkg-split --version exit=${prootDpkgSplitVersionResult.exitCode}" +
            "\nproot dpkg-split --version stdout=${prootDpkgSplitVersionResult.stdout}" +
            "\nproot dpkg-split --version stderr=${prootDpkgSplitVersionResult.stderr}" +
            "\nnative smoke exit=${nativeCommandResult.exitCode}" +
            "\nnative smoke stdout=${nativeCommandResult.stdout}" +
            "\nalr trampoline preflight exit=${alrTrampolinePreflightResult.exitCode}" +
            "\nalr trampoline image load=${alrTrampolinePreflightResult.stdout.lineStartingWith("ALR STATIC IMAGE LOAD PREFLIGHT:")}" +
            "\nalr trampoline entry stack=${alrTrampolinePreflightResult.stdout.lineStartingWith("ALR STATIC ENTRY STACK PLAN:")}" +
            "\nalr trampoline transfer context=${alrTrampolinePreflightResult.stdout.lineStartingWith("ALR STATIC ENTRY TRANSFER CONTEXT:")}" +
            "\nalr trampoline handoff=${alrTrampolinePreflightResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr entry probe exit=${alrTrampolineEntryProbeResult.exitCode}" +
            "\nalr entry probe handoff=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr entry probe jump ready=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("ALR STATIC ENTRY JUMP READY:")}" +
            "\nalr entry probe fixed image=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr transfer fixed image mapped=")}" +
            "\nalr entry probe attempted=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff attempted=")}" +
            "\nalr entry probe handoff elapsed ms=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff elapsed ms=")}" +
            "\nalr entry probe child exited=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff child exited=")}" +
            "\nalr entry probe child signaled=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff child signaled=")}" +
            "\nalr entry probe child signal=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff signal=")}" +
            "\nalr entry probe fault captured=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff fault captured=")}" +
            "\nalr entry probe fault addr=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff fault addr=")}" +
            "\nalr entry probe fault pc=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff fault pc=")}" +
            "\nalr entry probe fault syscall=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff fault syscall=")}" +
            "\nalr entry probe syscall emulated=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff syscall emulated count=")}" +
            "\nalr entry probe timeout=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff timed out=")}" +
            "\nalr entry probe stdout=${alrTrampolineEntryProbeResult.stdout.lineStartingWith("alr handoff stdout=")}" +
            "\nalr direct static hello=${if (alrTrampolineEntryProbeResult.stdout.contains("hello from static arm64 rootfs")) "PASS" else "SKIP"}" +
            "\nalr loader help probe exit=${alrTrampolineLoaderHelpProbeResult.exitCode}" +
            "\nalr loader help probe handoff=${alrTrampolineLoaderHelpProbeResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr loader help probe jump ready=${alrTrampolineLoaderHelpProbeResult.stdout.lineStartingWith("ALR STATIC ENTRY JUMP READY:")}" +
            "\nalr loader help probe fixed required=${alrTrampolineLoaderHelpProbeResult.stdout.lineStartingWith("alr transfer fixed vaddr required=")}" +
            "\nalr loader help probe load bias=${alrTrampolineLoaderHelpProbeResult.stdout.lineStartingWith("alr transfer image load bias=")}" +
            "\nalr loader help probe syscall emulated=${alrTrampolineLoaderHelpProbeResult.stdout.lineStartingWith("alr handoff syscall emulated count=")}" +
            "\nalr loader help probe stdout=${alrTrampolineLoaderHelpProbeResult.stdout.lineStartingWith("alr handoff stdout=")}" +
            "\nalr glibc hello probe exit=${alrTrampolineGlibcHelloProbeResult.exitCode}" +
            "\nalr glibc hello probe handoff=${alrTrampolineGlibcHelloProbeResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr glibc hello probe child exited=${alrTrampolineGlibcHelloProbeResult.stdout.lineStartingWith("alr handoff child exited=")}" +
            "\nalr glibc hello probe handoff elapsed ms=${alrTrampolineGlibcHelloProbeResult.stdout.lineStartingWith("alr handoff elapsed ms=")}" +
            "\nalr glibc hello probe child signaled=${alrTrampolineGlibcHelloProbeResult.stdout.lineStartingWith("alr handoff child signaled=")}" +
            "\nalr glibc hello probe fault syscall=${alrTrampolineGlibcHelloProbeResult.stdout.lineStartingWith("alr handoff fault syscall=")}" +
            "\nalr glibc hello probe syscall emulated=${alrTrampolineGlibcHelloProbeResult.stdout.lineStartingWith("alr handoff syscall emulated count=")}" +
            "\nalr glibc hello probe stdout=${alrTrampolineGlibcHelloProbeResult.stdout.lineStartingWith("alr handoff stdout=")}" +
            "\nalr direct dynamic glibc hello=${if (alrTrampolineGlibcHelloProbeResult.stdout.contains("hello from dynamic glibc rootfs")) "PASS" else "SKIP"}" +
            "\nalr cat os-release probe exit=${alrTrampolineCatOsReleaseProbeResult.exitCode}" +
            "\nalr cat os-release probe handoff=${alrTrampolineCatOsReleaseProbeResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr cat os-release probe handoff elapsed ms=${alrTrampolineCatOsReleaseProbeResult.stdout.lineStartingWith("alr handoff elapsed ms=")}" +
            "\nalr cat os-release probe syscall emulated=${alrTrampolineCatOsReleaseProbeResult.stdout.lineStartingWith("alr handoff syscall emulated count=")}" +
            "\nalr cat os-release probe stdout=${alrTrampolineCatOsReleaseProbeResult.stdout.lineStartingWith("alr handoff stdout=")}" +
            "\nalr translated guest path cat=${if (alrTrampolineCatOsReleaseProbeResult.stdout.contains("ID=androlinux-tiny")) "PASS" else "SKIP"}" +
            "\nalr static handoff benchmark=${alrTrampolineEntryBenchmarkResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF BENCHMARK:")}" +
            "\nalr static handoff benchmark requested=${alrTrampolineEntryBenchmarkResult.stdout.lineStartingWith("alr handoff repeat requested count=")}" +
            "\nalr static handoff benchmark pass=${alrTrampolineEntryBenchmarkResult.stdout.lineStartingWith("alr handoff repeat pass count=")}" +
            "\nalr static handoff benchmark average ms=${alrTrampolineEntryBenchmarkResult.stdout.lineStartingWith("alr handoff repeat average elapsed ms=")}" +
            "\nalr static handoff benchmark min ms=${alrTrampolineEntryBenchmarkResult.stdout.lineStartingWith("alr handoff repeat min elapsed ms=")}" +
            "\nalr static handoff benchmark max ms=${alrTrampolineEntryBenchmarkResult.stdout.lineStartingWith("alr handoff repeat max elapsed ms=")}" +
            "\nnative bionic fork benchmark=${nativeBionicForkBenchmarkResult.stdout.lineStartingWith("NATIVE BIONIC FORK BENCHMARK:")}" +
            "\nnative bionic fork benchmark requested=${nativeBionicForkBenchmarkResult.stdout.lineStartingWith("native fork repeat requested count=")}" +
            "\nnative bionic fork benchmark pass=${nativeBionicForkBenchmarkResult.stdout.lineStartingWith("native fork repeat pass count=")}" +
            "\nnative bionic fork benchmark average us=${nativeBionicForkBenchmarkResult.stdout.lineStartingWith("native fork repeat average elapsed us=")}" +
            "\nnative bionic fork benchmark min us=${nativeBionicForkBenchmarkResult.stdout.lineStartingWith("native fork repeat min elapsed us=")}" +
            "\nnative bionic fork benchmark max us=${nativeBionicForkBenchmarkResult.stdout.lineStartingWith("native fork repeat max elapsed us=")}" +
            "\nalr static handoff vs native fork ratio pct=${alrBenchmarkVsNativeForkRatioPct(alrTrampolineEntryBenchmarkResult, nativeBionicForkBenchmarkResult)}" +
            "\nproot static hello loop benchmark elapsed ms=${prootHelloLoopBenchmarkResult.elapsedMs}" +
            "\nproot static hello loop benchmark average ms=${averageElapsedMs(prootHelloLoopBenchmarkResult, handoffBenchmarkRepeatCount)}" +
            "\nproot static hello loop benchmark stdout=${prootHelloLoopBenchmarkResult.stdout}" +
            "\nalr static handoff vs proot loop ratio pct=${alrBenchmarkVsProotLoopRatioPct(alrTrampolineEntryBenchmarkResult, prootHelloLoopBenchmarkResult, handoffBenchmarkRepeatCount)}" +
            "\nalr static handoff faster than proot loop=${alrBenchmarkFasterThanProotLoop(alrTrampolineEntryBenchmarkResult, prootHelloLoopBenchmarkResult, handoffBenchmarkRepeatCount)}" +
            "\nalr dynamic glibc handoff benchmark=${alrTrampolineGlibcHelloBenchmarkResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF BENCHMARK:")}" +
            "\nalr dynamic glibc handoff benchmark requested=${alrTrampolineGlibcHelloBenchmarkResult.stdout.lineStartingWith("alr handoff repeat requested count=")}" +
            "\nalr dynamic glibc handoff benchmark pass=${alrTrampolineGlibcHelloBenchmarkResult.stdout.lineStartingWith("alr handoff repeat pass count=")}" +
            "\nalr dynamic glibc handoff benchmark average ms=${alrTrampolineGlibcHelloBenchmarkResult.stdout.lineStartingWith("alr handoff repeat average elapsed ms=")}" +
            "\nalr dynamic glibc handoff benchmark min ms=${alrTrampolineGlibcHelloBenchmarkResult.stdout.lineStartingWith("alr handoff repeat min elapsed ms=")}" +
            "\nalr dynamic glibc handoff benchmark max ms=${alrTrampolineGlibcHelloBenchmarkResult.stdout.lineStartingWith("alr handoff repeat max elapsed ms=")}" +
            "\nalr dynamic glibc handoff vs native fork ratio pct=${alrBenchmarkVsNativeForkRatioPct(alrTrampolineGlibcHelloBenchmarkResult, nativeBionicForkBenchmarkResult)}" +
            "\nproot dynamic glibc loop benchmark elapsed ms=${prootGlibcLoopBenchmarkResult.elapsedMs}" +
            "\nproot dynamic glibc loop benchmark average ms=${averageElapsedMs(prootGlibcLoopBenchmarkResult, handoffBenchmarkRepeatCount)}" +
            "\nproot dynamic glibc loop benchmark stdout=${prootGlibcLoopBenchmarkResult.stdout}" +
            "\nalr dynamic glibc handoff vs proot loop ratio pct=${alrBenchmarkVsProotLoopRatioPct(alrTrampolineGlibcHelloBenchmarkResult, prootGlibcLoopBenchmarkResult, handoffBenchmarkRepeatCount)}" +
            "\nalr dynamic glibc handoff faster than proot loop=${alrBenchmarkFasterThanProotLoop(alrTrampolineGlibcHelloBenchmarkResult, prootGlibcLoopBenchmarkResult, handoffBenchmarkRepeatCount)}" +
            "\nalr loop hot path measured faster count=${loopHotPathFasterCount(alrTrampolineEntryBenchmarkResult, prootHelloLoopBenchmarkResult, alrTrampolineGlibcHelloBenchmarkResult, prootGlibcLoopBenchmarkResult, handoffBenchmarkRepeatCount)}/2" +
            "\nalr loop hot path perf evidence=${loopHotPathPerfEvidence(alrTrampolineEntryBenchmarkResult, prootHelloLoopBenchmarkResult, alrTrampolineGlibcHelloBenchmarkResult, prootGlibcLoopBenchmarkResult, handoffBenchmarkRepeatCount)}" +
            "\nalr syscall stat benchmark average us=${syscallBenchAverageUs(alrSyscallStatBenchmarkResult, alr = true)}" +
            "\nalr syscall stat path rewrite cache hits=${alrSyscallStatBenchmarkResult.stdout.lineStartingWith("alr handoff path rewrite cache hit count=")}" +
            "\nproot syscall stat benchmark average us=${syscallBenchAverageUs(prootSyscallStatBenchmarkResult, alr = false)}" +
            "\nalr syscall stat vs proot ratio pct=${syscallBenchRatioPct(alrSyscallStatBenchmarkResult, prootSyscallStatBenchmarkResult)}" +
            "\nalr syscall stat faster than proot=${syscallBenchFasterThanProot(alrSyscallStatBenchmarkResult, prootSyscallStatBenchmarkResult)}" +
            "\nalr syscall stat preload benchmark average us=${syscallBenchAverageUs(alrSyscallStatPreloadBenchmarkResult, alr = true)}" +
            "\nalr syscall stat preload handoff=${alrSyscallStatPreloadBenchmarkResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr syscall stat preload stderr=${alrSyscallStatPreloadBenchmarkResult.stdout.lineStartingWith("alr handoff stderr=")}" +
            "\nalr syscall stat preload vs proot ratio pct=${syscallBenchRatioPct(alrSyscallStatPreloadBenchmarkResult, prootSyscallStatBenchmarkResult)}" +
            "\nalr syscall stat preload faster than proot=${syscallBenchFasterThanProot(alrSyscallStatPreloadBenchmarkResult, prootSyscallStatBenchmarkResult)}" +
            "\nalr syscall openread benchmark average us=${syscallBenchAverageUs(alrSyscallOpenReadBenchmarkResult, alr = true)}" +
            "\nalr syscall openread path rewrite cache hits=${alrSyscallOpenReadBenchmarkResult.stdout.lineStartingWith("alr handoff path rewrite cache hit count=")}" +
            "\nproot syscall openread benchmark average us=${syscallBenchAverageUs(prootSyscallOpenReadBenchmarkResult, alr = false)}" +
            "\nalr syscall openread vs proot ratio pct=${syscallBenchRatioPct(alrSyscallOpenReadBenchmarkResult, prootSyscallOpenReadBenchmarkResult)}" +
            "\nalr syscall openread faster than proot=${syscallBenchFasterThanProot(alrSyscallOpenReadBenchmarkResult, prootSyscallOpenReadBenchmarkResult)}" +
            "\nalr syscall openread preload benchmark average us=${syscallBenchAverageUs(alrSyscallOpenReadPreloadBenchmarkResult, alr = true)}" +
            "\nalr syscall openread preload handoff=${alrSyscallOpenReadPreloadBenchmarkResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr syscall openread preload stderr=${alrSyscallOpenReadPreloadBenchmarkResult.stdout.lineStartingWith("alr handoff stderr=")}" +
            "\nalr syscall openread preload vs proot ratio pct=${syscallBenchRatioPct(alrSyscallOpenReadPreloadBenchmarkResult, prootSyscallOpenReadBenchmarkResult)}" +
            "\nalr syscall openread preload faster than proot=${syscallBenchFasterThanProot(alrSyscallOpenReadPreloadBenchmarkResult, prootSyscallOpenReadBenchmarkResult)}" +
            "\nalr syscall fsmeta benchmark average us=${syscallBenchAverageUs(alrSyscallFsMetaBenchmarkResult, alr = true)}" +
            "\nalr syscall fsmeta path rewrite cache hits=${alrSyscallFsMetaBenchmarkResult.stdout.lineStartingWith("alr handoff path rewrite cache hit count=")}" +
            "\nproot syscall fsmeta benchmark average us=${syscallBenchAverageUs(prootSyscallFsMetaBenchmarkResult, alr = false)}" +
            "\nalr syscall fsmeta vs proot ratio pct=${syscallBenchRatioPct(alrSyscallFsMetaBenchmarkResult, prootSyscallFsMetaBenchmarkResult)}" +
            "\nalr syscall fsmeta faster than proot=${syscallBenchFasterThanProot(alrSyscallFsMetaBenchmarkResult, prootSyscallFsMetaBenchmarkResult)}" +
            "\nalr syscall fsmeta preload benchmark average us=${syscallBenchAverageUs(alrSyscallFsMetaPreloadBenchmarkResult, alr = true)}" +
            "\nalr syscall fsmeta preload handoff=${alrSyscallFsMetaPreloadBenchmarkResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr syscall fsmeta preload stderr=${alrSyscallFsMetaPreloadBenchmarkResult.stdout.lineStartingWith("alr handoff stderr=")}" +
            "\nalr syscall fsmeta preload vs proot ratio pct=${syscallBenchRatioPct(alrSyscallFsMetaPreloadBenchmarkResult, prootSyscallFsMetaBenchmarkResult)}" +
            "\nalr syscall fsmeta preload faster than proot=${syscallBenchFasterThanProot(alrSyscallFsMetaPreloadBenchmarkResult, prootSyscallFsMetaBenchmarkResult)}" +
            "\nalr syscall spawn benchmark average us=${syscallBenchAverageUs(alrSyscallSpawnBenchmarkResult, alr = true)}" +
            "\nproot syscall spawn benchmark average us=${syscallBenchAverageUs(prootSyscallSpawnBenchmarkResult, alr = false)}" +
            "\nalr syscall spawn vs proot ratio pct=${syscallBenchRatioPct(alrSyscallSpawnBenchmarkResult, prootSyscallSpawnBenchmarkResult)}" +
            "\nalr syscall spawn faster than proot=${syscallBenchFasterThanProot(alrSyscallSpawnBenchmarkResult, prootSyscallSpawnBenchmarkResult)}" +
            "\nalr syscall hot path measured faster count=${syscallHotPathFasterCount(alrSyscallStatBenchmarkResult, prootSyscallStatBenchmarkResult, alrSyscallOpenReadBenchmarkResult, prootSyscallOpenReadBenchmarkResult, alrSyscallSpawnBenchmarkResult, prootSyscallSpawnBenchmarkResult)}/3" +
            "\nalr syscall hot path perf evidence=${syscallHotPathPerfEvidence(alrSyscallStatBenchmarkResult, prootSyscallStatBenchmarkResult, alrSyscallOpenReadBenchmarkResult, prootSyscallOpenReadBenchmarkResult, alrSyscallSpawnBenchmarkResult, prootSyscallSpawnBenchmarkResult)}" +
            "\nalr syscall preload hot path measured faster count=${syscallHotPathFasterCount(alrSyscallStatPreloadBenchmarkResult, prootSyscallStatBenchmarkResult, alrSyscallOpenReadPreloadBenchmarkResult, prootSyscallOpenReadBenchmarkResult, alrSyscallFsMetaPreloadBenchmarkResult, prootSyscallFsMetaBenchmarkResult)}/3" +
            "\nalr syscall preload hot path perf evidence=${syscallStrictHotPathPerfEvidence(alrSyscallStatPreloadBenchmarkResult, prootSyscallStatBenchmarkResult, alrSyscallOpenReadPreloadBenchmarkResult, prootSyscallOpenReadBenchmarkResult, alrSyscallFsMetaPreloadBenchmarkResult, prootSyscallFsMetaBenchmarkResult)}" +
            "\nalr static hello elapsed ms=${alrTrampolineEntryProbeResult.elapsedMs}" +
            "\nproot static hello elapsed ms=${prootHelloResult.elapsedMs}" +
            "\nalr static hello elapsed ratio pct=${elapsedRatioPct(alrTrampolineEntryProbeResult, prootHelloResult)}" +
            "\nalr static hello faster than proot=${alrTrampolineEntryProbeResult.exitCode == 0 && prootHelloResult.exitCode == 0 && alrTrampolineEntryProbeResult.elapsedMs < prootHelloResult.elapsedMs}" +
            "\nalr dynamic glibc elapsed ms=${alrTrampolineGlibcHelloProbeResult.elapsedMs}" +
            "\nproot dynamic glibc elapsed ms=${prootGlibcResult.elapsedMs}" +
            "\nalr dynamic glibc elapsed ratio pct=${elapsedRatioPct(alrTrampolineGlibcHelloProbeResult, prootGlibcResult)}" +
            "\nalr dynamic glibc faster than proot=${alrTrampolineGlibcHelloProbeResult.exitCode == 0 && prootGlibcResult.exitCode == 0 && alrTrampolineGlibcHelloProbeResult.elapsedMs < prootGlibcResult.elapsedMs}" +
            "\nalr hot path measured faster count=${hotPathFasterCount(alrTrampolineEntryProbeResult, prootHelloResult, alrTrampolineGlibcHelloProbeResult, prootGlibcResult)}/2" +
            "\nalr hot path perf evidence=${hotPathPerfEvidence(alrTrampolineEntryProbeResult, prootHelloResult, alrTrampolineGlibcHelloProbeResult, prootGlibcResult)}" +
            "\nalr translated cat elapsed ms=${alrTrampolineCatOsReleaseProbeResult.elapsedMs}" +
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
            "\nalr dpkg --version handoff=${alrDpkgVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr dpkg --version path rewrite=${alrDpkgVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr dpkg --version stdout=${alrDpkgVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr dpkg --print-architecture handoff=${alrDpkgArchResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr dpkg --print-architecture path rewrite=${alrDpkgArchResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr dpkg --print-architecture stdout=${alrDpkgArchResult.stdout.alrHandoffStdoutText()}" +
            "\nalr dpkg --print-architecture preload handoff=${alrDpkgArchPreloadResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr dpkg --print-architecture preload path rewrite=${alrDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr dpkg --print-architecture preload stdout=${alrDpkgArchPreloadResult.stdout.alrHandoffStdoutText()}" +
            "\nalr dpkg --print-architecture preload stderr=${alrDpkgArchPreloadResult.stdout.alrHandoffStderrText()}" +
            "\nalr shell dpkg --print-architecture preload handoff=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr shell dpkg --print-architecture preload child exited=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff child exited=")}" +
            "\nalr shell dpkg --print-architecture preload child signaled=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff child signaled=")}" +
            "\nalr shell dpkg --print-architecture preload exit code=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff exit code=")}" +
            "\nalr shell dpkg --print-architecture preload signal=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff signal=")}" +
            "\nalr shell dpkg --print-architecture preload timed out=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff timed out=")}" +
            "\nalr shell dpkg --print-architecture preload fault syscall=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff fault syscall=")}" +
            "\nalr shell dpkg --print-architecture preload execve attempts=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff execve attempt count=")}" +
            "\nalr shell dpkg --print-architecture preload execve loader rewrites=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff execve loader rewrite count=")}" +
            "\nalr shell dpkg --print-architecture preload traced processes=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff traced process count=")}" +
            "\nalr shell dpkg --print-architecture preload last exec requested=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff last exec requested path=")}" +
            "\nalr shell dpkg --print-architecture preload last guest=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff last guest path=")}" +
            "\nalr shell dpkg --print-architecture preload last host=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff last host path=")}" +
            "\nalr shell dpkg --print-architecture preload path rewrite=${alrShellDpkgArchPreloadResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr shell dpkg --print-architecture preload stdout=${alrShellDpkgArchPreloadResult.stdout.alrHandoffStdoutText()}" +
            "\nalr shell dpkg --print-architecture preload stderr=${alrShellDpkgArchPreloadResult.stdout.alrHandoffStderrText()}" +
            "\nalr dpkg-query --version handoff=${alrDpkgQueryVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr dpkg-query --version path rewrite=${alrDpkgQueryVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr dpkg-query --version stdout=${alrDpkgQueryVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt --version handoff=${alrAptVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt --version path rewrite=${alrAptVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt --version stdout=${alrAptVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt --version preload handoff=${alrAptPreloadVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt --version preload path rewrite=${alrAptPreloadVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt --version preload stdout=${alrAptPreloadVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt --version preload stderr=${alrAptPreloadVersionResult.stdout.alrHandoffStderrText()}" +
            "\nalr apt-get --version handoff=${alrAptGetVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-get --version path rewrite=${alrAptGetVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-get --version stdout=${alrAptGetVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-get --version preload handoff=${alrAptGetPreloadVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-get --version preload path rewrite=${alrAptGetPreloadVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-get --version preload stdout=${alrAptGetPreloadVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-get --version preload stderr=${alrAptGetPreloadVersionResult.stdout.alrHandoffStderrText()}" +
            "\nalr apt-cache --version handoff=${alrAptCacheVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-cache --version path rewrite=${alrAptCacheVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-cache --version stdout=${alrAptCacheVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-cache --version preload handoff=${alrAptCachePreloadVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-cache --version preload path rewrite=${alrAptCachePreloadVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-cache --version preload stdout=${alrAptCachePreloadVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-cache --version preload stderr=${alrAptCachePreloadVersionResult.stdout.alrHandoffStderrText()}" +
            "\nalr apt-cache policy preload handoff=${alrAptCachePolicyPreloadResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-cache policy preload path rewrite=${alrAptCachePolicyPreloadResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-cache policy preload stdout=${alrAptCachePolicyPreloadResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-cache policy preload stderr=${alrAptCachePolicyPreloadResult.stdout.alrHandoffStderrText()}" +
            "\nalr apt-cache stats preload handoff=${alrAptCacheStatsPreloadResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-cache stats preload path rewrite=${alrAptCacheStatsPreloadResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-cache stats preload stdout=${alrAptCacheStatsPreloadResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-cache stats preload stderr=${alrAptCacheStatsPreloadResult.stdout.alrHandoffStderrText()}" +
            "\nalr apt-cache pkgnames preload handoff=${alrAptCachePkgNamesPreloadResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-cache pkgnames preload path rewrite=${alrAptCachePkgNamesPreloadResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-cache pkgnames preload stdout=${alrAptCachePkgNamesPreloadResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-cache pkgnames preload stderr=${alrAptCachePkgNamesPreloadResult.stdout.alrHandoffStderrText()}" +
            "\nalr apt-config --version handoff=${alrAptConfigVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-config --version path rewrite=${alrAptConfigVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-config --version stdout=${alrAptConfigVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-config --version preload handoff=${alrAptConfigPreloadVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr apt-config --version preload path rewrite=${alrAptConfigPreloadVersionResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr apt-config --version preload stdout=${alrAptConfigPreloadVersionResult.stdout.alrHandoffStdoutText()}" +
            "\nalr apt-config --version preload stderr=${alrAptConfigPreloadVersionResult.stdout.alrHandoffStderrText()}" +
            "\nalr dpkg -i local deb handoff=${alrDpkgInstallLocalResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr dpkg -i local deb identity virtualized=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff identity syscall virtualized count=")}" +
            "\nalr dpkg -i local deb execve attempts=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff execve attempt count=")}" +
            "\nalr dpkg -i local deb execve loader rewrites=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff execve loader rewrite count=")}" +
            "\nalr dpkg -i local deb traced processes=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff traced process count=")}" +
            "\nalr dpkg -i local deb last exec requested=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff last exec requested path=")}" +
            "\nalr dpkg -i local deb last status syscall=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff last status path syscall=")}" +
            "\nalr dpkg -i local deb last status request=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff last status path request=")}" +
            "\nalr dpkg -i local deb last status guest=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff last status path guest=")}" +
            "\nalr dpkg -i local deb last status host=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff last status path host=")}" +
            "\nalr dpkg -i local deb path rewrite=${alrDpkgInstallLocalResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr dpkg -i local deb stdout=${alrDpkgInstallLocalResult.stdout.alrHandoffStdoutText()}" +
            "\nalr dpkg -i local deb stderr=${alrDpkgInstallLocalResult.stdout.alrHandoffStderrText()}" +
            "\nalr dpkg -i local deb preload handoff=${alrDpkgInstallLocalPreloadResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr dpkg -i local deb preload execve attempts=${alrDpkgInstallLocalPreloadResult.stdout.lineStartingWith("alr handoff execve attempt count=")}" +
            "\nalr dpkg -i local deb preload execve loader rewrites=${alrDpkgInstallLocalPreloadResult.stdout.lineStartingWith("alr handoff execve loader rewrite count=")}" +
            "\nalr dpkg -i local deb preload traced processes=${alrDpkgInstallLocalPreloadResult.stdout.lineStartingWith("alr handoff traced process count=")}" +
            "\nalr dpkg -i local deb preload path rewrite=${alrDpkgInstallLocalPreloadResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr dpkg -i local deb preload stdout=${alrDpkgInstallLocalPreloadResult.stdout.alrHandoffStdoutText()}" +
            "\nalr dpkg -i local deb preload stderr=${alrDpkgInstallLocalPreloadResult.stdout.alrHandoffStderrText()}" +
            "\nalr installed package preload handoff=${alrInstalledPackageSmokePreloadResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package preload execve attempts=${alrInstalledPackageSmokePreloadResult.stdout.lineStartingWith("alr handoff execve attempt count=")}" +
            "\nalr installed package preload execve loader rewrites=${alrInstalledPackageSmokePreloadResult.stdout.lineStartingWith("alr handoff execve loader rewrite count=")}" +
            "\nalr installed package preload traced processes=${alrInstalledPackageSmokePreloadResult.stdout.lineStartingWith("alr handoff traced process count=")}" +
            "\nalr installed package preload last exec requested=${alrInstalledPackageSmokePreloadResult.stdout.lineStartingWith("alr handoff last exec requested path=")}" +
            "\nalr installed package preload last guest=${alrInstalledPackageSmokePreloadResult.stdout.lineStartingWith("alr handoff last guest path=")}" +
            "\nalr installed package preload last host=${alrInstalledPackageSmokePreloadResult.stdout.lineStartingWith("alr handoff last host path=")}" +
            "\nalr installed package preload path rewrite=${alrInstalledPackageSmokePreloadResult.stdout.lineStartingWith("alr handoff path rewrite count=")}" +
            "\nalr installed package preload stdout=${alrInstalledPackageSmokePreloadResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package preload stderr=${alrInstalledPackageSmokePreloadResult.stdout.alrHandoffStderrText()}" +
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
            resultBlock("native bionic fork benchmark", nativeBionicForkBenchmarkResult) +
            resultBlock("alr trampoline preflight", alrTrampolinePreflightResult) +
            resultBlock("alr trampoline entry probe", alrTrampolineEntryProbeResult) +
            resultBlock("alr trampoline loader help probe", alrTrampolineLoaderHelpProbeResult) +
            resultBlock("alr trampoline glibc hello probe", alrTrampolineGlibcHelloProbeResult) +
            resultBlock("alr trampoline cat os-release probe", alrTrampolineCatOsReleaseProbeResult) +
            resultBlock("alr trampoline static benchmark", alrTrampolineEntryBenchmarkResult) +
            resultBlock("alr trampoline glibc hello benchmark", alrTrampolineGlibcHelloBenchmarkResult) +
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
            resultBlock("proot glibc loop benchmark", prootGlibcLoopBenchmarkResult) +
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
            resultBlock("proot syscall stat benchmark", prootSyscallStatBenchmarkResult) +
            resultBlock("proot syscall openread benchmark", prootSyscallOpenReadBenchmarkResult) +
            resultBlock("proot syscall fsmeta benchmark", prootSyscallFsMetaBenchmarkResult) +
            resultBlock("proot syscall spawn benchmark", prootSyscallSpawnBenchmarkResult) +
            resultBlock("alr dpkg --version", alrDpkgVersionResult) +
            resultBlock("alr dpkg --print-architecture", alrDpkgArchResult) +
            resultBlock("alr dpkg --print-architecture preload", alrDpkgArchPreloadResult) +
            resultBlock("alr shell dpkg --print-architecture preload", alrShellDpkgArchPreloadResult) +
            resultBlock("alr dpkg-query --version", alrDpkgQueryVersionResult) +
            resultBlock("alr apt --version", alrAptVersionResult) +
            resultBlock("alr apt --version preload", alrAptPreloadVersionResult) +
            resultBlock("alr apt-get --version", alrAptGetVersionResult) +
            resultBlock("alr apt-get --version preload", alrAptGetPreloadVersionResult) +
            resultBlock("alr apt-cache --version", alrAptCacheVersionResult) +
            resultBlock("alr apt-cache --version preload", alrAptCachePreloadVersionResult) +
            resultBlock("alr apt-cache policy preload", alrAptCachePolicyPreloadResult) +
            resultBlock("alr apt-cache stats preload", alrAptCacheStatsPreloadResult) +
            resultBlock("alr apt-cache pkgnames preload", alrAptCachePkgNamesPreloadResult) +
            resultBlock("alr apt-config --version", alrAptConfigVersionResult) +
            resultBlock("alr apt-config --version preload", alrAptConfigPreloadVersionResult) +
            resultBlock("alr syscall stat benchmark", alrSyscallStatBenchmarkResult) +
            resultBlock("alr syscall openread benchmark", alrSyscallOpenReadBenchmarkResult) +
            resultBlock("alr syscall fsmeta benchmark", alrSyscallFsMetaBenchmarkResult) +
            resultBlock("alr syscall stat preload benchmark", alrSyscallStatPreloadBenchmarkResult) +
            resultBlock("alr syscall openread preload benchmark", alrSyscallOpenReadPreloadBenchmarkResult) +
            resultBlock("alr syscall fsmeta preload benchmark", alrSyscallFsMetaPreloadBenchmarkResult) +
            resultBlock("alr syscall spawn benchmark", alrSyscallSpawnBenchmarkResult) +
            resultBlock("alr dpkg -i local deb", alrDpkgInstallLocalResult) +
            resultBlock("alr dpkg -i local deb preload", alrDpkgInstallLocalPreloadResult) +
            resultBlock("alr installed package smoke preload", alrInstalledPackageSmokePreloadResult) +
            resultBlock("proot dpkg -i local deb", prootDpkgInstallLocalResult) +
            resultBlock("proot installed package smoke", prootInstalledPackageSmokeResult) +
            resultBlock("proot guest gpu client", prootGuestGpuClientResult) +
            resultBlock("alr guest gpu client", alrGuestGpuClientResult) +
            resultBlock("proot guest gpu ipc client", guestGpuIpcBridgeResult.clientResult) +
            resultBlock("alr guest gpu ipc client", alrGuestGpuIpcBridgeResult.clientResult) +
            resultBlock("proot guest gles shim smoke", prootGuestGlesShimSmokeResult) +
            resultBlock("alr guest gles shim smoke", alrGuestGlesShimSmokeResult) +
            resultBlock("proot guest gles abi smoke", prootGuestGlesAbiSmokeResult) +
            resultBlock("alr guest gles abi smoke", alrGuestGlesAbiSmokeResult) +
            resultBlock("proot guest gles demo gears", prootGuestGlesDemoGearsResult) +
            resultBlock("alr guest gles demo gears", alrGuestGlesDemoGearsResult) +
            resultBlock("proot guest gles procaddr demo", prootGuestGlesProcaddrDemoResult) +
            resultBlock("alr guest gles procaddr demo", alrGuestGlesProcaddrDemoResult) +
            resultBlock("proot guest wayland gui client", prootGuestWaylandGuiResult) +
            resultBlock("proot guest x11 gui client", prootGuestX11GuiResult) +
            resultBlock("alr guest wayland gui client", alrGuestWaylandGuiResult) +
            resultBlock("alr guest x11 gui client", alrGuestX11GuiResult) +
            resultBlock("proot guest wayland gui ipc client", guestWaylandGuiBridgeResult.clientResult) +
            resultBlock("proot guest x11 gui ipc client", guestX11GuiBridgeResult.clientResult) +
            resultBlock("alr guest wayland gui ipc client", alrGuestWaylandGuiBridgeResult.clientResult) +
            resultBlock("alr guest x11 gui ipc client", alrGuestX11GuiBridgeResult.clientResult) +
            optionalResultBlock("proot hello verbose on failure", prootHelloVerboseResult)

        val report = executionSummary + "\n\n--- verbose report ---\n" + verboseReport

        val view = TextView(this).apply {
            text = report
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
        }
        val surfaceStatusView = TextView(this).apply {
            text = "Linux guest GPU Surface renderer: waiting for Android Surface callback"
            textSize = 14f
            setPadding(32, 24, 32, 24)
            setTextIsSelectable(true)
        }
        val surfaceView = SurfaceView(this).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    val encodedFrames = encodeSurfaceFrames(surfaceGpuCommands)
                    val surfaceReport = nativeRenderGpuSurfaceFrames(holder.surface, encodedFrames)
                    val executionUpdate = surfaceExecutionUpdate(
                        surfaceReport,
                        guestGlesShimInitPassed,
                        guestGlesShimContextPassed,
                        guestGlesShimClearPassed,
                        guestGlesShimSwapPassed,
                        guestGlesShimDrawApiPassed,
                    )
                    surfaceStatusView.text = "Linux guest GPU Surface renderer callback complete\n$executionUpdate"
                    view.append("\n\n--- Linux guest Wayland/X11 GUI GPU surface renderer ---\n$executionUpdate\n$surfaceReport")
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        }
        val surfaceHeight = (180 * resources.displayMetrics.density).toInt().coerceAtLeast(180)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(surfaceStatusView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
        useAlr: Boolean = false,
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
        val clientResult = if (useAlr) {
            nativeCommandRunner.runAlrRuntimeTrampolineGuestGpuClientIpc(rootfsDir, port)
        } else {
            nativeCommandRunner.runProotRootfsGuestGpuClientIpc(rootfsDir, port)
        }
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
        useAlr: Boolean = false,
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
        val clientResult = if (useAlr) {
            nativeCommandRunner.runAlrRuntimeTrampolineGuestGuiClientIpc(rootfsDir, protocol, port)
        } else {
            nativeCommandRunner.runProotRootfsGuestGuiClientIpc(rootfsDir, protocol, port)
        }
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

    private fun parseGuestGlesShimCommands(stdout: String): List<GuestGpuCommand> =
        stdout.lineSequence()
            .filter {
                it.startsWith("ALR_GLES_SHIM_COMMAND ALR_GPU_CLEAR ") ||
                    it.startsWith("ALR_GLES_SHIM_COMMAND ALR_GPU_DRAW_TRIANGLE ")
            }
            .map { it.removePrefix("ALR_GLES_SHIM_COMMAND ") }
            .mapNotNull { parseGuestGpuCommandLine(it) }
            .mapIndexed { index, command -> command.copy(seq = index + 1) }
            .toList()

    private fun parseGuestGpuCommandLine(line: String): GuestGpuCommand? =
        when {
            line.startsWith("ALR_GPU_CLEAR ") -> parseGuestGpuClearLine(line)?.copy(protocol = "GLES")
            line.startsWith("ALR_GPU_DRAW_TRIANGLE ") -> parseGuestGpuTriangleLine(line)
            else -> null
        }

    private fun parseGuestGpuTriangleLine(line: String): GuestGpuCommand? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5 || parts[0] != "ALR_GPU_DRAW_TRIANGLE") return null
        val red = parts[1].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val green = parts[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val blue = parts[3].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        return GuestGpuCommand(red, green, blue, parts[4], protocol = "GLES_DRAW")
    }

    private fun parseGuestGpuClearLine(line: String): GuestGpuCommand? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5 || parts[0] != "ALR_GPU_CLEAR") return null
        val red = parts[1].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val green = parts[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val blue = parts[3].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        return GuestGpuCommand(red, green, blue, parts[4])
    }

    private fun buildNativeGlesBaselineCommands(frameCount: Int): List<GuestGpuCommand> =
        (1..frameCount.coerceIn(1, 120)).map { frame ->
            GuestGpuCommand(
                red = ((frame * 17) % 100) / 100f,
                green = ((frame * 29) % 100) / 100f,
                blue = ((frame * 43) % 100) / 100f,
                tag = "baseline-frame-%04d".format(frame),
                protocol = "NATIVE_GLES",
                seq = frame,
            )
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
            "\n$label elapsed ms=${result.elapsedMs}" +
            "\n$label stdout=${result.stdout}" +
            "\n$label stderr=${result.stderr}"

    private fun elapsedRatioPct(candidate: NativeCommandResult, baseline: NativeCommandResult): String =
        if (candidate.exitCode == 0 && baseline.exitCode == 0 && baseline.elapsedMs > 0) {
            ((candidate.elapsedMs * 100) / baseline.elapsedMs).toString()
        } else {
            "unavailable"
        }

    private fun averageElapsedMs(result: NativeCommandResult, repeatCount: Int): String =
        if (result.exitCode == 0 && repeatCount > 0) {
            (result.elapsedMs / repeatCount).toString()
        } else {
            "unavailable"
        }

    private fun alrRepeatAverageElapsedMs(result: NativeCommandResult): Long? =
        result.stdout.lineStartingWith("alr handoff repeat average elapsed ms=")
            .removePrefix("alr handoff repeat average elapsed ms=")
            .toLongOrNull()

    private fun nativeForkAverageElapsedUs(result: NativeCommandResult): Long? =
        result.stdout.lineStartingWith("native fork repeat average elapsed us=")
            .removePrefix("native fork repeat average elapsed us=")
            .toLongOrNull()

    private fun alrBenchmarkVsNativeForkRatioPct(
        alrBenchmark: NativeCommandResult,
        nativeForkBenchmark: NativeCommandResult,
    ): String {
        val alrAverageMs = alrRepeatAverageElapsedMs(alrBenchmark) ?: return "unavailable"
        val nativeAverageUs = nativeForkAverageElapsedUs(nativeForkBenchmark) ?: return "unavailable"
        if (nativeForkBenchmark.exitCode != 0 || nativeAverageUs <= 0) return "unavailable"
        return (((alrAverageMs * 1000) * 100) / nativeAverageUs).toString()
    }

    private fun alrBenchmarkVsProotLoopRatioPct(
        alrBenchmark: NativeCommandResult,
        prootLoopBenchmark: NativeCommandResult,
        repeatCount: Int,
    ): String {
        val alrAverageMs = alrRepeatAverageElapsedMs(alrBenchmark) ?: return "unavailable"
        if (prootLoopBenchmark.exitCode != 0 || repeatCount <= 0) return "unavailable"
        val prootAverageMs = prootLoopBenchmark.elapsedMs / repeatCount
        if (prootAverageMs <= 0) return "unavailable"
        return ((alrAverageMs * 100) / prootAverageMs).toString()
    }

    private fun alrBenchmarkFasterThanProotLoop(
        alrBenchmark: NativeCommandResult,
        prootLoopBenchmark: NativeCommandResult,
        repeatCount: Int,
    ): Boolean {
        val alrAverageMs = alrRepeatAverageElapsedMs(alrBenchmark) ?: return false
        if (prootLoopBenchmark.exitCode != 0 || repeatCount <= 0) return false
        val prootAverageMs = prootLoopBenchmark.elapsedMs / repeatCount
        return prootAverageMs > 0 && alrAverageMs < prootAverageMs
    }

    private fun loopHotPathFasterCount(
        staticAlrBenchmark: NativeCommandResult,
        staticProotLoopBenchmark: NativeCommandResult,
        dynamicAlrBenchmark: NativeCommandResult,
        dynamicProotLoopBenchmark: NativeCommandResult,
        repeatCount: Int,
    ): Int =
        listOf(
            alrBenchmarkFasterThanProotLoop(staticAlrBenchmark, staticProotLoopBenchmark, repeatCount),
            alrBenchmarkFasterThanProotLoop(dynamicAlrBenchmark, dynamicProotLoopBenchmark, repeatCount),
        ).count { it }

    private fun loopHotPathPerfEvidence(
        staticAlrBenchmark: NativeCommandResult,
        staticProotLoopBenchmark: NativeCommandResult,
        dynamicAlrBenchmark: NativeCommandResult,
        dynamicProotLoopBenchmark: NativeCommandResult,
        repeatCount: Int,
    ): String {
        if (
            staticAlrBenchmark.exitCode != 0 ||
            staticProotLoopBenchmark.exitCode != 0 ||
            dynamicAlrBenchmark.exitCode != 0 ||
            dynamicProotLoopBenchmark.exitCode != 0
        ) {
            return "INCOMPLETE"
        }
        return if (
            loopHotPathFasterCount(
                staticAlrBenchmark,
                staticProotLoopBenchmark,
                dynamicAlrBenchmark,
                dynamicProotLoopBenchmark,
                repeatCount,
            ) == 2
        ) {
            "PASS"
        } else {
            "NEEDS_WORK"
        }
    }

    private fun syscallBenchAverageUs(result: NativeCommandResult, alr: Boolean): String {
        if (!syscallBenchPassed(result, alr)) return "unavailable"
        val stdout = if (alr) result.stdout.alrHandoffStdoutText() else result.stdout
        return stdout.lineStartingWith("alr syscall bench average us=")
            .removePrefix("alr syscall bench average us=")
            .toLongOrNull()
            ?.toString()
            ?: "unavailable"
    }

    private fun syscallBenchAverageUsValue(result: NativeCommandResult, alr: Boolean): Long? {
        if (!syscallBenchPassed(result, alr)) return null
        val stdout = if (alr) result.stdout.alrHandoffStdoutText() else result.stdout
        return stdout.lineStartingWith("alr syscall bench average us=")
            .removePrefix("alr syscall bench average us=")
            .toLongOrNull()
    }

    private fun syscallBenchPassed(result: NativeCommandResult, alr: Boolean): Boolean =
        if (alr) {
            result.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                result.stdout.alrHandoffStdoutText().contains("ALR SYSCALL BENCH: PASS")
        } else {
            result.exitCode == 0 && result.stdout.contains("ALR SYSCALL BENCH: PASS")
        }

    private fun syscallBenchRatioPct(
        alrBenchmark: NativeCommandResult,
        prootBenchmark: NativeCommandResult,
    ): String {
        val alrAverageUs = syscallBenchAverageUsValue(alrBenchmark, alr = true) ?: return "unavailable"
        val prootAverageUs = syscallBenchAverageUsValue(prootBenchmark, alr = false) ?: return "unavailable"
        if (prootAverageUs <= 0) return "unavailable"
        return ((alrAverageUs * 100) / prootAverageUs).toString()
    }

    private fun syscallBenchFasterThanProot(
        alrBenchmark: NativeCommandResult,
        prootBenchmark: NativeCommandResult,
    ): Boolean {
        val alrAverageUs = syscallBenchAverageUsValue(alrBenchmark, alr = true) ?: return false
        val prootAverageUs = syscallBenchAverageUsValue(prootBenchmark, alr = false) ?: return false
        return prootAverageUs > 0 && alrAverageUs < prootAverageUs
    }

    private fun syscallHotPathFasterCount(
        statAlr: NativeCommandResult,
        statProot: NativeCommandResult,
        openReadAlr: NativeCommandResult,
        openReadProot: NativeCommandResult,
        spawnAlr: NativeCommandResult,
        spawnProot: NativeCommandResult,
    ): Int =
        listOf(
            syscallBenchFasterThanProot(statAlr, statProot),
            syscallBenchFasterThanProot(openReadAlr, openReadProot),
            syscallBenchFasterThanProot(spawnAlr, spawnProot),
        ).count { it }

    private fun syscallHotPathPerfEvidence(
        statAlr: NativeCommandResult,
        statProot: NativeCommandResult,
        openReadAlr: NativeCommandResult,
        openReadProot: NativeCommandResult,
        spawnAlr: NativeCommandResult,
        spawnProot: NativeCommandResult,
    ): String {
        if (
            !syscallBenchPassed(statAlr, alr = true) ||
            !syscallBenchPassed(statProot, alr = false) ||
            !syscallBenchPassed(openReadAlr, alr = true) ||
            !syscallBenchPassed(openReadProot, alr = false) ||
            !syscallBenchPassed(spawnAlr, alr = true) ||
            !syscallBenchPassed(spawnProot, alr = false)
        ) {
            return "INCOMPLETE"
        }
        return if (syscallHotPathFasterCount(statAlr, statProot, openReadAlr, openReadProot, spawnAlr, spawnProot) >= 2) {
            "PASS"
        } else {
            "NEEDS_WORK"
        }
    }

    private fun syscallStrictHotPathPerfEvidence(
        statAlr: NativeCommandResult,
        statProot: NativeCommandResult,
        openReadAlr: NativeCommandResult,
        openReadProot: NativeCommandResult,
        fsMetaAlr: NativeCommandResult,
        fsMetaProot: NativeCommandResult,
    ): String {
        if (
            !syscallBenchPassed(statAlr, alr = true) ||
            !syscallBenchPassed(statProot, alr = false) ||
            !syscallBenchPassed(openReadAlr, alr = true) ||
            !syscallBenchPassed(openReadProot, alr = false) ||
            !syscallBenchPassed(fsMetaAlr, alr = true) ||
            !syscallBenchPassed(fsMetaProot, alr = false)
        ) {
            return "INCOMPLETE"
        }
        return if (syscallHotPathFasterCount(statAlr, statProot, openReadAlr, openReadProot, fsMetaAlr, fsMetaProot) == 3) {
            "PASS"
        } else {
            "NEEDS_WORK"
        }
    }

    private fun syscallHotPathFasterCount(
        statAlr: NativeCommandResult,
        statProot: NativeCommandResult,
        openReadAlr: NativeCommandResult,
        openReadProot: NativeCommandResult,
    ): Int =
        listOf(
            syscallBenchFasterThanProot(statAlr, statProot),
            syscallBenchFasterThanProot(openReadAlr, openReadProot),
        ).count { it }

    private fun syscallHotPathPerfEvidence(
        statAlr: NativeCommandResult,
        statProot: NativeCommandResult,
        openReadAlr: NativeCommandResult,
        openReadProot: NativeCommandResult,
    ): String {
        if (
            !syscallBenchPassed(statAlr, alr = true) ||
            !syscallBenchPassed(statProot, alr = false) ||
            !syscallBenchPassed(openReadAlr, alr = true) ||
            !syscallBenchPassed(openReadProot, alr = false)
        ) {
            return "INCOMPLETE"
        }
        return if (syscallHotPathFasterCount(statAlr, statProot, openReadAlr, openReadProot) == 2) {
            "PASS"
        } else {
            "NEEDS_WORK"
        }
    }

    private fun isFaster(candidate: NativeCommandResult, baseline: NativeCommandResult): Boolean =
        candidate.exitCode == 0 && baseline.exitCode == 0 && candidate.elapsedMs < baseline.elapsedMs

    private fun hotPathFasterCount(
        staticAlr: NativeCommandResult,
        staticProot: NativeCommandResult,
        dynamicAlr: NativeCommandResult,
        dynamicProot: NativeCommandResult,
    ): Int =
        listOf(
            isFaster(staticAlr, staticProot),
            isFaster(dynamicAlr, dynamicProot),
        ).count { it }

    private fun hotPathPerfEvidence(
        staticAlr: NativeCommandResult,
        staticProot: NativeCommandResult,
        dynamicAlr: NativeCommandResult,
        dynamicProot: NativeCommandResult,
    ): String {
        if (staticAlr.exitCode != 0 || staticProot.exitCode != 0 || dynamicAlr.exitCode != 0 || dynamicProot.exitCode != 0) {
            return "INCOMPLETE"
        }
        return if (hotPathFasterCount(staticAlr, staticProot, dynamicAlr, dynamicProot) == 2) {
            "PASS"
        } else {
            "NEEDS_WORK"
        }
    }

    private fun optionalResultBlock(label: String, result: NativeCommandResult?): String =
        result?.let { resultBlock(label, it) } ?: "\n\n$label skipped=quiet rootfs execution passed"

    private fun surfaceExecutionUpdate(
        surfaceReport: String,
        glesShimInitPassed: Boolean,
        glesShimContextPassed: Boolean,
        glesShimClearPassed: Boolean,
        glesShimSwapPassed: Boolean,
        glesShimDrawPassed: Boolean,
    ): String {
        val framesRendered = surfaceReport.lineStartingWith("surface frames rendered=")
            .removePrefix("surface frames rendered=")
            .toIntOrNull()
            ?: 0
        val glesShimFramesRendered = surfaceReport.lineStartingWith("surface gles shim frames rendered=")
            .removePrefix("surface gles shim frames rendered=")
            .toIntOrNull()
            ?: 0
        val glesShimDrawFramesRendered = surfaceReport.lineStartingWith("surface gles shim draw frames rendered=")
            .removePrefix("surface gles shim draw frames rendered=")
            .toIntOrNull()
            ?: 0
        val hostSurfacePassed =
            surfaceReport.lineStartingWith("surface gpu hardware render=") == "surface gpu hardware render=true" &&
                framesRendered > 0
        val multiFrameSurfacePassed =
            surfaceReport.lineStartingWith("guest gpu bridge hardware render=") == "guest gpu bridge hardware render=true" &&
                surfaceReport.lineStartingWith("surface frame lossless=") == "surface frame lossless=true"
        val guiSurfacePassed =
            surfaceReport.lineStartingWith("guest wayland/x11 gui gpu surface hardware render=") ==
                "guest wayland/x11 gui gpu surface hardware render=true"
        val glesShimSurfacePassed =
            glesShimInitPassed &&
                glesShimContextPassed &&
                glesShimClearPassed &&
                glesShimSwapPassed &&
                surfaceReport.lineStartingWith("guest egl swap via android surface=") == "guest egl swap via android surface=true" &&
                surfaceReport.lineStartingWith("guest gles hardware render=") == "guest gles hardware render=true" &&
                glesShimFramesRendered > 0
        val glesShimDrawSurfacePassed =
            glesShimDrawPassed &&
                surfaceReport.lineStartingWith("guest gles draw via android surface=") == "guest gles draw via android surface=true" &&
                glesShimDrawFramesRendered > 0

        return "HOST GPU SURFACE EXECUTION UPDATE: ${if (hostSurfacePassed) "PASS" else "FAIL"}" +
            "\nGUEST GPU MULTI-FRAME SURFACE EXECUTION UPDATE: ${if (multiFrameSurfacePassed) "PASS" else "FAIL"}" +
            "\nGUEST GUI GPU SURFACE EXECUTION UPDATE: ${if (guiSurfacePassed) "PASS" else "FAIL"}" +
            "\nGUEST EGL INIT VIA SHIM UPDATE: ${if (glesShimInitPassed) "PASS" else "FAIL"}" +
            "\nGUEST EGL CONTEXT VIA SHIM UPDATE: ${if (glesShimContextPassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES CLEAR VIA SHIM UPDATE: ${if (glesShimSurfacePassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES DRAW VIA SHIM UPDATE: ${if (glesShimDrawSurfacePassed) "PASS" else "FAIL"}" +
            "\nGUEST EGL SWAP VIA ANDROID SURFACE UPDATE: ${if (glesShimSurfacePassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES HARDWARE RENDER UPDATE: ${if (glesShimSurfacePassed) "PASS" else "FAIL"}" +
            "\nsurface callback frames rendered=$framesRendered" +
            "\nsurface callback hardware render=${hostSurfacePassed && multiFrameSurfacePassed && guiSurfacePassed && glesShimSurfacePassed && glesShimDrawSurfacePassed}" +
            "\n${surfaceReport.lineStartingWith("surface gl renderer=")}" +
            "\n${surfaceReport.lineStartingWith("surface frames rendered=")}" +
            "\n${surfaceReport.lineStartingWith("surface frames dropped=")}" +
            "\n${surfaceReport.lineStartingWith("surface render elapsed us=")}" +
            "\n${surfaceReport.lineStartingWith("surface average frame render us=")}" +
            "\n${surfaceReport.lineStartingWith("surface gles shim render elapsed us=")}" +
            "\n${surfaceReport.lineStartingWith("surface gles shim average frame render us=")}" +
            "\n${surfaceReport.lineStartingWith("surface gles shim draw frames rendered=")}" +
            "\n${surfaceReport.lineStartingWith("surface gles shim draw render elapsed us=")}" +
            "\n${surfaceReport.lineStartingWith("surface gles shim draw average frame render us=")}" +
            "\n${surfaceReport.lineStartingWith("surface native gles frames rendered=")}" +
            "\n${surfaceReport.lineStartingWith("surface native gles render elapsed us=")}" +
            "\n${surfaceReport.lineStartingWith("surface native gles average frame render us=")}" +
            "\n${surfaceReport.lineStartingWith("surface gles shim vs native average ratio pct=")}" +
            "\n${surfaceReport.lineStartingWith("surface frame lossless=")}" +
            "\n${surfaceReport.lineStartingWith("surface gpu hardware render=")}" +
            "\n${surfaceReport.lineStartingWith("guest wayland/x11 gui gpu surface hardware render=")}" +
            "\n${surfaceReport.lineStartingWith("surface gles shim frames rendered=")}" +
            "\n${surfaceReport.lineStartingWith("guest egl swap via android surface=")}" +
            "\n${surfaceReport.lineStartingWith("guest gles hardware render=")}" +
            "\n${surfaceReport.lineStartingWith("guest gles draw via android surface=")}" +
            "\n${surfaceReport.lineStartingWith("surface wayland frames rendered=")}" +
            "\n${surfaceReport.lineStartingWith("surface x11 frames rendered=")}"
    }

    private fun String.lineStartingWith(prefix: String): String =
        lineSequence().firstOrNull { it.startsWith(prefix) } ?: "missing"

    private fun String.hasGlesApiSteps(vararg names: String): Boolean =
        names.all { name -> contains("ALR_GLES_API_STEP $name ok") }

    private fun String.alrHandoffStdoutText(): String =
        lineStartingWith("alr handoff stdout=")
            .removePrefix("alr handoff stdout=")
            .replace("\\n", "\n")
            .replace("\\r", "\r")

    private fun String.alrHandoffStderrText(): String =
        lineStartingWith("alr handoff stderr=")
            .removePrefix("alr handoff stderr=")
            .replace("\\n", "\n")
            .replace("\\r", "\r")

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
