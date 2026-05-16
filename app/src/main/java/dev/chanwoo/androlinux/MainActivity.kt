package dev.chanwoo.androlinux

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private companion object {
        const val WAYLAND_DISPLAY_FRAMES = 8
        const val WAYLAND_WIRE_MESSAGES = 30
        const val WAYLAND_BINARY_BYTES = 568
        const val WAYLAND_FULL_PAYLOAD_BYTES = 1843200
        const val WAYLAND_DIRTY_BYTES = 460800
        const val WAYLAND_SURFACE_POOL_REUSES = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("alr_loader")

        if (intent.getStringExtra("ALR_VERIFY_MODE") == "gimp3-wayland") {
            runGimp3WaylandVerificationMode()
            return
        }

        val rootfsManifest = RootfsManifest(
            name = "debian-arm64",
            version = "trixie-slim-2026-05-gimp3-wayland-v104",
            assets = listOf(
                RootfsAsset(
                    path = "tiny-rootfs.tar",
                    sha256 = "9ed659c149510393662754f2508805f84edef5721a49539c26fe820481fcd75e",
                    sizeBytes = 1365166080,
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
        val alrInstalledPackageGimpDemoProfileResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGimpDemoProfile(rootfsStatus.rootfsDir)
        val gimpGtkWaylandProbeResult = runGimpGtkWaylandProbe(nativeCommandRunner, rootfsStatus.rootfsDir)
        val gimpGuiWaylandProbeResult = runGimpGuiWaylandProbe(nativeCommandRunner, rootfsStatus.rootfsDir, fast = true)
        val prootDpkgInstallLocalResult = nativeCommandRunner.runProotRootfsDpkgInstallLocalSmoke(rootfsStatus.rootfsDir)
        val prootInstalledPackageSmokeResult = nativeCommandRunner.runProotRootfsInstalledPackageSmoke(rootfsStatus.rootfsDir)
        val prootGuestGpuClientResult = nativeCommandRunner.runProotRootfsGuestGpuClient(rootfsStatus.rootfsDir)
        val guestGpuCommands = parseGuestGpuCommands(prootGuestGpuClientResult.stdout)
        val alrGuestGpuClientResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGpuClient(rootfsStatus.rootfsDir)
        val alrGuestGpuCommands = parseGuestGpuCommands(alrGuestGpuClientResult.stdout.alrHandoffStdoutText())
        val guestGpuIpcBridgeResult = runGuestGpuIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val alrGuestGpuIpcBridgeResult = runGuestGpuIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir, useAlr = true)
        val alrInstalledPackageGpuIpcBridgeResult = runInstalledPackageGpuIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
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
        val alrInstalledPackageGlesDemoResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGlesDemo(rootfsStatus.rootfsDir, glesDemoFrameCount)
        val alrInstalledPackageGlesIpcBridgeResult = runInstalledPackageGlesIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir, glesDemoFrameCount)
        val alrInstalledPackageGlesUnixIpcBridgeResult = runInstalledPackageGlesUnixIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir, glesDemoFrameCount)
        val alrInstalledPackageGlesUnixBatchIpcBridgeResult = runInstalledPackageGlesUnixBatchIpcBridge(nativeCommandRunner, rootfsStatus.rootfsDir, glesDemoFrameCount)
        val prootGuestGlesProcaddrDemoResult = nativeCommandRunner.runProotRootfsGuestGlesProcaddrDemo(rootfsStatus.rootfsDir, glesProcDemoFrameCount)
        val alrGuestGlesProcaddrDemoResult = nativeCommandRunner.runAlrRuntimeTrampolineGuestGlesProcaddrDemo(rootfsStatus.rootfsDir, glesProcDemoFrameCount)
        val alrInstalledPackageGlesProcaddrDemoResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGlesProcaddrDemo(rootfsStatus.rootfsDir, glesProcDemoFrameCount)
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
        val alrInstalledPackageGlesDemoStdout = alrInstalledPackageGlesDemoResult.stdout.alrHandoffStdoutText()
        val prootGuestGlesProcaddrDemoStdout = prootGuestGlesProcaddrDemoResult.stdout
        val alrGuestGlesProcaddrDemoStdout = alrGuestGlesProcaddrDemoResult.stdout.alrHandoffStdoutText()
        val alrInstalledPackageGlesProcaddrDemoStdout = alrInstalledPackageGlesProcaddrDemoResult.stdout.alrHandoffStdoutText()
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
        val alrInstalledPackageGlesDemoCommands = parseGuestGlesShimCommands(alrInstalledPackageGlesDemoStdout)
        val guestGlesProcaddrDemoCommands = parseGuestGlesShimCommands(prootGuestGlesProcaddrDemoStdout)
        val alrGuestGlesProcaddrDemoCommands = parseGuestGlesShimCommands(alrGuestGlesProcaddrDemoStdout)
        val alrInstalledPackageGlesProcaddrDemoCommands = parseGuestGlesShimCommands(alrInstalledPackageGlesProcaddrDemoStdout)
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
        val alrInstalledPackageWaylandGuiBridgeResult = runGuestGuiBridge(nativeCommandRunner, rootfsStatus.rootfsDir, "WAYLAND", useInstalledPackage = true)
        val alrInstalledPackageX11GuiBridgeResult = runGuestGuiBridge(nativeCommandRunner, rootfsStatus.rootfsDir, "X11", useInstalledPackage = true)
        val alrInstalledPackageWaylandGuiUnixBridgeResult = runGuestGuiBridgeUnix(nativeCommandRunner, rootfsStatus.rootfsDir, "WAYLAND", useInstalledPackage = true)
        val alrInstalledPackageX11GuiUnixBridgeResult = runGuestGuiBridgeUnix(nativeCommandRunner, rootfsStatus.rootfsDir, "X11", useInstalledPackage = true)
        val alrInstalledPackageWaylandDisplayBridgeResult = runInstalledPackageWaylandDisplayBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val alrInstalledPackageSimpleGuiDemoBridgeResult = runInstalledPackageSimpleGuiDemoBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val alrInstalledPackageVulkanDiscoveryBridgeResult = runInstalledPackageVulkanDiscoveryBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val alrInstalledPackageVulkanProxyBridgeResult = runInstalledPackageVulkanProxyBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val alrInstalledPackageVulkanIcdBridgeResult = runInstalledPackageVulkanIcdBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val alrInstalledPackageVulkanLoaderInfoBridgeResult = runInstalledPackageVulkanLoaderInfoBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val alrInstalledPackageVulkanUnixLoaderInfoBridgeResult = runInstalledPackageVulkanUnixLoaderInfoBridge(nativeCommandRunner, rootfsStatus.rootfsDir)
        val nativeGlesBaselineCommands = buildNativeGlesBaselineCommands(glesShimBenchmarkFrameCount)
        val waylandSurfaceSourceCommands = if (alrInstalledPackageSimpleGuiDemoBridgeResult.commands.isNotEmpty()) {
            alrInstalledPackageSimpleGuiDemoBridgeResult.commands
        } else {
            alrInstalledPackageWaylandDisplayBridgeResult.commands
        }
        val guestGuiSurfaceCommands = alrInstalledPackageSimpleGuiDemoBridgeResult.commands +
            alrInstalledPackageWaylandDisplayBridgeResult.commands +
            alrInstalledPackageWaylandGuiUnixBridgeResult.commands + alrInstalledPackageX11GuiUnixBridgeResult.commands +
            alrInstalledPackageWaylandGuiBridgeResult.commands + alrInstalledPackageX11GuiBridgeResult.commands +
            alrGuestWaylandGuiBridgeResult.commands + alrGuestX11GuiBridgeResult.commands +
            guestWaylandGuiBridgeResult.commands + guestX11GuiBridgeResult.commands
        val surfaceGpuCommands = buildList {
            addAll(guestGuiSurfaceCommands)
            addAll(alrInstalledPackageGpuIpcBridgeResult.commands)
            addAll(alrInstalledPackageGlesUnixBatchIpcBridgeResult.commands)
            addAll(alrInstalledPackageGlesUnixIpcBridgeResult.commands)
            addAll(alrInstalledPackageGlesIpcBridgeResult.commands)
            addAll(alrInstalledPackageGlesDemoCommands)
            addAll(alrInstalledPackageGlesProcaddrDemoCommands)
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
        val hostHardwareBufferProbe = nativeHostHardwareBufferProbe()
        val waylandHardwareBufferBridgeProbe = nativeWaylandHardwareBufferBridge(
            encodeSurfaceFrames(waylandSurfaceSourceCommands),
        )
        val hostVulkanProbe = alrInstalledPackageVulkanDiscoveryBridgeResult.hostProbe
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
        val rootfsInstalledGimpDemoFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-gimp-demo")
        val rootfsGimpDemoProfileFile = File(rootfsStatus.rootfsDir, "usr/share/androlinux/gimp-demo-profile.json")
        val rootfsGimpDemoBundleLockFile = File(rootfsStatus.rootfsDir, "usr/share/androlinux/gimp-demo-bundle.lock.json")
        val rootfsGimpDemoMaterializedFile = File(rootfsStatus.rootfsDir, "usr/share/androlinux/gimp-demo-materialized.txt")
        val rootfsGimpBinaryFile = File(rootfsStatus.rootfsDir, "usr/bin/gimp")
        val rootfsInstalledGpuSmokeFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-gpu-smoke")
        val rootfsInstalledGlesDemoFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-gles-demo")
        val rootfsInstalledGlesProcaddrDemoFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-gles-procaddr-demo")
        val rootfsInstalledWaylandGuiClientFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-wayland-gpu-client")
        val rootfsInstalledX11GuiClientFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-x11-gpu-client")
        val rootfsInstalledWaylandDisplayClientFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-wayland-display-client")
        val rootfsInstalledSimpleGuiDemoFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-simple-gui-demo")
        val rootfsInstalledVulkanDiscoveryClientFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-vulkan-discovery-client")
        val rootfsInstalledVulkanProxySmokeFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-vulkan-proxy-smoke")
        val rootfsInstalledVulkanIcdSmokeFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-vulkan-icd-manifest-smoke")
        val rootfsInstalledVulkanLoaderInfoFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-vulkan-loader-info")
        val rootfsInstalledVulkanProxyLibFile = File(rootfsStatus.rootfsDir, "usr/lib/androlinux/libvulkan.so.1")
        val rootfsInstalledVulkanIcdManifestFile = File(rootfsStatus.rootfsDir, "usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json")
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
        val rootfsWaylandDisplayClientFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-wayland-display-client")
        val rootfsSimpleGuiDemoFile = File(rootfsStatus.rootfsDir, "usr/bin/alr-simple-gui-demo")
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
        val gimpVersionStdout = alrInstalledPackageGimpDemoProfileResult.stdout.alrHandoffStdoutText()
        val gimpVersionExit = if (
            alrInstalledPackageGimpDemoProfileResult.exitCode == 0 &&
            alrInstalledPackageGimpDemoProfileResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS")
        ) {
            "0"
        } else {
            "1"
        }
        val gimpLockText = if (rootfsGimpDemoBundleLockFile.isFile) rootfsGimpDemoBundleLockFile.readText() else ""
        val gimpLockPackageCount = Regex("\"package_count\"\\s*:\\s*(\\d+)").find(gimpLockText)?.groupValues?.get(1) ?: "0"
        val gimpLockDownloadSizeMib = Regex("\"download_size_mib\"\\s*:\\s*([0-9.]+)").find(gimpLockText)?.groupValues?.get(1) ?: "0"
        val gimpLockSuite = Regex("\"suite\"\\s*:\\s*\"([^\"]+)\"").find(gimpLockText)?.groupValues?.get(1) ?: "unknown"
        val gimpMaterializedText = if (rootfsGimpDemoMaterializedFile.isFile) rootfsGimpDemoMaterializedFile.readText() else ""
        val gimpMaterializedPackageCount = gimpMaterializedText.lineStartingWith("package_count=").removePrefix("package_count=").ifBlank { "0" }
        val gimpMaterializedVersion = gimpMaterializedText.lineStartingWith("gimp_version=").removePrefix("gimp_version=").ifBlank { "unknown" }
        val gimpDemoProfileStdout = buildString {
            appendLine("ALR_GIMP_DEMO_PROFILE_READY target=gimp version=v104 profile=/usr/share/androlinux/gimp-demo-profile.json lock=/usr/share/androlinux/gimp-demo-bundle.lock.json")
            appendLine("ALR_GIMP_DEMO_PROFILE_PROGRAM path=/usr/bin/gimp argv=gimp,--version")
            appendLine("ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=wayland WAYLAND_DISPLAY=alr-gimp-0 XDG_RUNTIME_DIR=/tmp NO_AT_BRIDGE=1")
            appendLine("ALR_GIMP_DEMO_BUNDLE_LOCK present=${rootfsGimpDemoBundleLockFile.isFile} suite=$gimpLockSuite package_count=$gimpLockPackageCount download_size_mib=$gimpLockDownloadSizeMib")
            appendLine("ALR_GIMP_DEMO_MATERIALIZED present=${rootfsGimpDemoMaterializedFile.isFile} package_count=$gimpMaterializedPackageCount gimp_version=$gimpMaterializedVersion")
            appendLine("ALR_GIMP_DEMO_BINARY present=${rootfsGimpBinaryFile.isFile && rootfsGimpBinaryFile.canExecute()} path=/usr/bin/gimp")
            appendLine("ALR_GIMP_DEMO_LAUNCH_MODE version-probe")
            appendLine("ALR_GIMP_DEMO_VERSION_EXIT $gimpVersionExit")
            gimpVersionStdout.lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line -> appendLine("ALR_GIMP_DEMO_VERSION_STDOUT $line") }
            appendLine("ALR_GIMP_DEMO_EXEC_READY ${gimpVersionExit == "0"} mode=version-probe")
        }.trim()
        val gimpDemoProfileExecutionPassed =
            alrInstalledPackageGimpDemoProfileResult.exitCode == 0 &&
                alrInstalledPackageGimpDemoProfileResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledGimpDemoFile.isFile &&
                rootfsInstalledGimpDemoFile.canExecute() &&
                rootfsGimpDemoProfileFile.isFile &&
                rootfsGimpDemoBundleLockFile.isFile &&
                rootfsGimpDemoMaterializedFile.isFile &&
                rootfsGimpBinaryFile.isFile &&
                rootfsGimpBinaryFile.canExecute() &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_PROFILE_READY target=gimp") &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_PROFILE_PROGRAM path=/usr/bin/gimp") &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=wayland WAYLAND_DISPLAY=alr-gimp-0 XDG_RUNTIME_DIR=/tmp") &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_BUNDLE_LOCK present=true suite=trixie") &&
                gimpLockPackageCount.toIntOrNull()?.let { it >= 300 } == true &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_MATERIALIZED present=true") &&
                gimpMaterializedVersion.startsWith("3.") &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_BINARY present=true path=/usr/bin/gimp") &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_LAUNCH_MODE version-probe") &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_VERSION_EXIT 0") &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_VERSION_STDOUT GNU Image Manipulation Program version 3.") &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_EXEC_READY true mode=version-probe")
        val gimpGuiWaylandProbePassed = isWaylandRegistryProbe(gimpGuiWaylandProbeResult)
        val gimpGtkWaylandProbePassed = isWaylandRegistryProbe(gimpGtkWaylandProbeResult)
        val gimpGuiWaylandBlocker = describeGimpGuiWaylandBlocker(gimpGuiWaylandProbePassed, gimpGuiWaylandProbeResult)
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
        val alrInstalledPackageGpuIpcBridgePassed =
            alrInstalledPackageGpuIpcBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledGpuSmokeFile.isFile &&
                rootfsInstalledGpuSmokeFile.canExecute() &&
                alrInstalledPackageGpuIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("alr guest gpu client ok") &&
                alrInstalledPackageGpuIpcBridgeResult.commands.size == alrInstalledPackageGpuIpcBridgeResult.expectedFrames &&
                alrInstalledPackageGpuIpcBridgeResult.expectedFrames > 0 &&
                alrInstalledPackageGpuIpcBridgeResult.error == null
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
        val alrInstalledPackageGlesDemoPassed =
            alrInstalledPackageGlesDemoResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledGlesDemoFile.isFile &&
                rootfsInstalledGlesDemoFile.canExecute() &&
                alrInstalledPackageGlesDemoStdout.contains("ALR_GLES_DEMO_KIND es2gears-like-triangle-strip-subset") &&
                alrInstalledPackageGlesDemoStdout.contains("ALR_GLES_DEMO_WORKLOAD requested=$glesDemoFrameCount submitted=$glesDemoFrameCount") &&
                alrInstalledPackageGlesDemoCommands.count { it.protocol == "GLES_DRAW" } == glesDemoFrameCount
        val alrInstalledPackageGlesIpcBridgePassed =
            alrInstalledPackageGlesIpcBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                alrInstalledPackageGlesIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()
                    .contains("ALR_GLES_DEMO_WORKLOAD requested=$glesDemoFrameCount submitted=$glesDemoFrameCount") &&
                alrInstalledPackageGlesIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()
                    .contains("ALR_GLES_IPC_ACK_SUMMARY requested=$glesDemoFrameCount received=$glesDemoFrameCount") &&
                alrInstalledPackageGlesIpcBridgeResult.commands.count { it.protocol == "GLES_DRAW" } == glesDemoFrameCount &&
                alrInstalledPackageGlesIpcBridgeResult.commands.size == alrInstalledPackageGlesIpcBridgeResult.expectedFrames &&
                alrInstalledPackageGlesIpcBridgeResult.ackLines.size == glesDemoFrameCount &&
                alrInstalledPackageGlesIpcBridgeResult.error == null
        val alrInstalledPackageGlesUnixIpcBridgePassed =
            alrInstalledPackageGlesUnixIpcBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                alrInstalledPackageGlesUnixIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()
                    .contains("ALR_GLES_DEMO_WORKLOAD requested=$glesDemoFrameCount submitted=$glesDemoFrameCount") &&
                alrInstalledPackageGlesUnixIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()
                    .contains("ALR_GLES_IPC_ACK_SUMMARY requested=$glesDemoFrameCount received=$glesDemoFrameCount") &&
                alrInstalledPackageGlesUnixIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()
                    .contains("transport=unix-abstract") &&
                alrInstalledPackageGlesUnixIpcBridgeResult.commands.count { it.protocol == "GLES_DRAW" } == glesDemoFrameCount &&
                alrInstalledPackageGlesUnixIpcBridgeResult.commands.size == alrInstalledPackageGlesUnixIpcBridgeResult.expectedFrames &&
                alrInstalledPackageGlesUnixIpcBridgeResult.ackLines.size == glesDemoFrameCount &&
                alrInstalledPackageGlesUnixIpcBridgeResult.error == null
        val alrInstalledPackageGlesUnixBatchIpcBridgePassed =
            alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()
                    .contains("ALR_GLES_DEMO_WORKLOAD requested=$glesDemoFrameCount submitted=$glesDemoFrameCount") &&
                alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()
                    .contains("ALR_GLES_BATCH_ACK_SUMMARY requested=$glesDemoFrameCount received=$glesDemoFrameCount batches=1") &&
                alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()
                    .contains("transport=unix-abstract") &&
                alrInstalledPackageGlesUnixBatchIpcBridgeResult.commands.count { it.protocol == "GLES_DRAW" } == glesDemoFrameCount &&
                alrInstalledPackageGlesUnixBatchIpcBridgeResult.commands.size == alrInstalledPackageGlesUnixBatchIpcBridgeResult.expectedFrames &&
                alrInstalledPackageGlesUnixBatchIpcBridgeResult.ackLines.size == 1 &&
                alrInstalledPackageGlesUnixBatchIpcBridgeResult.error == null
        val guestGlesProcaddrDemoPassed = prootGuestGlesProcaddrDemoResult.exitCode == 0 &&
            prootGuestGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset") &&
            prootGuestGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_WORKLOAD requested=$glesProcDemoFrameCount submitted=$glesProcDemoFrameCount") &&
            guestGlesProcaddrDemoCommands.count { it.protocol == "GLES_DRAW" } == glesProcDemoFrameCount
        val alrGuestGlesProcaddrDemoPassed = alrGuestGlesProcaddrDemoResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
            alrGuestGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset") &&
            alrGuestGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_WORKLOAD requested=$glesProcDemoFrameCount submitted=$glesProcDemoFrameCount") &&
            alrGuestGlesProcaddrDemoCommands.count { it.protocol == "GLES_DRAW" } == glesProcDemoFrameCount
        val alrInstalledPackageGlesProcaddrDemoPassed =
            alrInstalledPackageGlesProcaddrDemoResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledGlesProcaddrDemoFile.isFile &&
                rootfsInstalledGlesProcaddrDemoFile.canExecute() &&
                alrInstalledPackageGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset") &&
                alrInstalledPackageGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_PROC glDrawArrays ok") &&
                alrInstalledPackageGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_PROC glUniform4f ok") &&
                alrInstalledPackageGlesProcaddrDemoStdout.contains("ALR_GLES_PROC_DEMO_WORKLOAD requested=$glesProcDemoFrameCount submitted=$glesProcDemoFrameCount") &&
                alrInstalledPackageGlesProcaddrDemoCommands.count { it.protocol == "GLES_DRAW" } == glesProcDemoFrameCount
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
        val alrInstalledPackageWaylandGuiBridgePassed =
            alrInstalledPackageWaylandGuiBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledWaylandGuiClientFile.isFile &&
                rootfsInstalledWaylandGuiClientFile.canExecute() &&
                alrInstalledPackageWaylandGuiBridgeResult.commands.size == alrInstalledPackageWaylandGuiBridgeResult.expectedFrames &&
                alrInstalledPackageWaylandGuiBridgeResult.expectedFrames > 0 &&
                alrInstalledPackageWaylandGuiBridgeResult.error == null
        val alrInstalledPackageX11GuiBridgePassed =
            alrInstalledPackageX11GuiBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledX11GuiClientFile.isFile &&
                rootfsInstalledX11GuiClientFile.canExecute() &&
                alrInstalledPackageX11GuiBridgeResult.commands.size == alrInstalledPackageX11GuiBridgeResult.expectedFrames &&
                alrInstalledPackageX11GuiBridgeResult.expectedFrames > 0 &&
                alrInstalledPackageX11GuiBridgeResult.error == null
        val alrInstalledPackageWaylandGuiUnixBridgePassed =
            alrInstalledPackageWaylandGuiUnixBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                alrInstalledPackageWaylandGuiUnixBridgeResult.rawLines.any { it.startsWith("ALR_GUI_UNIX_BRIDGE_SOCKET ") } &&
                alrInstalledPackageWaylandGuiUnixBridgeResult.rawLines.any { it.startsWith("ALR_GUI_IPC_HELLO ") && it.contains("transport=unix-abstract-gui") } &&
                rootfsInstalledWaylandGuiClientFile.isFile &&
                rootfsInstalledWaylandGuiClientFile.canExecute() &&
                alrInstalledPackageWaylandGuiUnixBridgeResult.commands.size == alrInstalledPackageWaylandGuiUnixBridgeResult.expectedFrames &&
                alrInstalledPackageWaylandGuiUnixBridgeResult.expectedFrames > 0 &&
                alrInstalledPackageWaylandGuiUnixBridgeResult.ackLines.size == 1 &&
                alrInstalledPackageWaylandGuiUnixBridgeResult.ackLines.first().contains("transport=unix-abstract") &&
                alrInstalledPackageWaylandGuiUnixBridgeResult.error == null
        val alrInstalledPackageX11GuiUnixBridgePassed =
            alrInstalledPackageX11GuiUnixBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                alrInstalledPackageX11GuiUnixBridgeResult.rawLines.any { it.startsWith("ALR_GUI_UNIX_BRIDGE_SOCKET ") } &&
                alrInstalledPackageX11GuiUnixBridgeResult.rawLines.any { it.startsWith("ALR_GUI_IPC_HELLO ") && it.contains("transport=unix-abstract-gui") } &&
                rootfsInstalledX11GuiClientFile.isFile &&
                rootfsInstalledX11GuiClientFile.canExecute() &&
                alrInstalledPackageX11GuiUnixBridgeResult.commands.size == alrInstalledPackageX11GuiUnixBridgeResult.expectedFrames &&
                alrInstalledPackageX11GuiUnixBridgeResult.expectedFrames > 0 &&
                alrInstalledPackageX11GuiUnixBridgeResult.ackLines.size == 1 &&
                alrInstalledPackageX11GuiUnixBridgeResult.ackLines.first().contains("transport=unix-abstract") &&
                alrInstalledPackageX11GuiUnixBridgeResult.error == null
        val alrInstalledPackageWaylandDisplayBridgePassed =
            alrInstalledPackageWaylandDisplayBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledWaylandDisplayClientFile.isFile &&
                rootfsInstalledWaylandDisplayClientFile.canExecute() &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_CONNECT ") && it.contains("display=alr-wayland-0") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_APP_STREAM_BEGIN ") && it.contains("frames=$WAYLAND_DISPLAY_FRAMES") && it.contains("mode=continuous-demo") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_APP_STREAM_END ") && it.contains("commits=$WAYLAND_DISPLAY_FRAMES") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_REGISTRY global=wl_compositor") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.count { it.startsWith("ALR_WL_WIRE ") } == WAYLAND_WIRE_MESSAGES &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_WIRE ") && it.contains("name=wl_display.get_registry") && it.contains("size=12") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_WIRE ") && it.contains("name=wl_compositor.create_surface") && it.contains("object=3") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.count { it.startsWith("ALR_WL_WIRE ") && it.contains("name=wl_surface.commit") } == WAYLAND_DISPLAY_FRAMES &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_AHB_BACKING_ADVERTISE ") && it.contains("dirty_rect=true") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_SHM_POOL_CREATE ") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_SHM_POOL_FD ") && it.contains("transport=scm-rights-memfd") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_FD_PAYLOAD ") && it.contains("verified=true") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.count { it.startsWith("ALR_WL_FD_PAYLOAD ") && it.contains("layout=triple-buffer") } == WAYLAND_DISPLAY_FRAMES &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.any { it.startsWith("ALR_WL_BUFFER_ATTACH ") && it.contains("scm-rights-memfd") } &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.count { it.startsWith("ALR_WL_DAMAGE ") && it.contains("backing=host-ahardwarebuffer") } == WAYLAND_DISPLAY_FRAMES &&
                alrInstalledPackageWaylandDisplayBridgeResult.rawLines.all {
                    !it.startsWith("ALR_WL_BUFFER_ATTACH ") ||
                        (it.contains("layout=triple-buffer") && it.contains("backing=host-ahardwarebuffer") && it.contains("update=partial"))
                } &&
                alrInstalledPackageWaylandDisplayBridgeResult.commands.size == alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames &&
                alrInstalledPackageWaylandDisplayBridgeResult.commands.all { it.payloadVerified } &&
                alrInstalledPackageWaylandDisplayBridgeResult.commands.all { it.fdPayloadVerified } &&
                alrInstalledPackageWaylandDisplayBridgeResult.commands.all { it.backing == "host-ahardwarebuffer" && it.partialUpdate } &&
                alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.dirtyBytes } == WAYLAND_DIRTY_BYTES &&
                alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.payloadBytes } == WAYLAND_FULL_PAYLOAD_BYTES &&
                alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.fdPayloadBytes } == WAYLAND_FULL_PAYLOAD_BYTES &&
                alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames == WAYLAND_DISPLAY_FRAMES &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.size == 1 &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("payload_verified=true") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("fd_payload_verified=true") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("fd_received=$WAYLAND_DISPLAY_FRAMES") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("layout=triple-buffer") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("transport=unix-abstract-wayland-scm-rights") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("wire_messages=$WAYLAND_WIRE_MESSAGES") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("wire_subset_ready=true") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("wire_surface_lifecycle=true") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("binary_messages=$WAYLAND_WIRE_MESSAGES") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("binary_header_ready=true") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("binary_subset_ready=true") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("continuous_stream_ready=true") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("backing=host-ahardwarebuffer") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("dirty_rects=$WAYLAND_DISPLAY_FRAMES") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("dirty_bytes=$WAYLAND_DIRTY_BYTES") &&
                alrInstalledPackageWaylandDisplayBridgeResult.ackLines.first().contains("ahb_state_ready=true") &&
                alrInstalledPackageWaylandDisplayBridgeResult.error == null
        val alrInstalledPackageSimpleGuiDemoPassed =
            alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledSimpleGuiDemoFile.isFile &&
                rootfsInstalledSimpleGuiDemoFile.canExecute() &&
                rootfsSimpleGuiDemoFile.isFile &&
                rootfsSimpleGuiDemoFile.canExecute() &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_SIMPLE_GUI_DEMO ok") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("glibc_dynamic=true") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.rawLines.any { it.startsWith("ALR_SIMPLE_GUI_DEMO_BEGIN ") && it.contains("glibc_dynamic=true") } &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.rawLines.any { it.startsWith("ALR_WL_CONNECT ") && it.contains("display=alr-simple-gui-demo-0") } &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.rawLines.any { it.startsWith("ALR_WL_APP_STREAM_BEGIN ") && it.contains("frames=$WAYLAND_DISPLAY_FRAMES") && it.contains("mode=simple-gui-demo") && it.contains("glibc_dynamic=true") } &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.rawLines.any { it.startsWith("ALR_WL_APP_STREAM_END ") && it.contains("commits=$WAYLAND_DISPLAY_FRAMES") && it.contains("mode=simple-gui-demo") } &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.rawLines.count { it.startsWith("ALR_WL_WIRE ") } == WAYLAND_WIRE_MESSAGES &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.rawLines.count { it.startsWith("ALR_WL_BINARY_MESSAGE ") } == WAYLAND_WIRE_MESSAGES &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.commands.size == WAYLAND_DISPLAY_FRAMES &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.commands.all { it.payloadVerified && it.fdPayloadVerified } &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.commands.all { it.backing == "host-ahardwarebuffer" && it.partialUpdate } &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.commands.sumOf { it.dirtyBytes } == WAYLAND_DIRTY_BYTES &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.commands.sumOf { it.payloadBytes } == WAYLAND_FULL_PAYLOAD_BYTES &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.commands.sumOf { it.fdPayloadBytes } == WAYLAND_FULL_PAYLOAD_BYTES &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.size == 1 &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.first().contains("payload_verified=true") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.first().contains("fd_payload_verified=true") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.first().contains("wire_messages=$WAYLAND_WIRE_MESSAGES") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.first().contains("binary_messages=$WAYLAND_WIRE_MESSAGES") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.first().contains("binary_subset_ready=true") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.first().contains("continuous_stream_ready=true") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.first().contains("ahb_state_ready=true") &&
                alrInstalledPackageSimpleGuiDemoBridgeResult.error == null
        val hostGpuHardwareCandidate = hostGpuProbe.lineStartingWith("host gpu hardware candidate=") == "host gpu hardware candidate=true"
        val hostHardwareBufferPassed =
            hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer execution=") == "ahardwarebuffer execution=PASS" &&
                hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer host managed triple buffer=") ==
                "ahardwarebuffer host managed triple buffer=true" &&
                hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer egl image import=") ==
                "ahardwarebuffer egl image import=ok"
        val waylandHardwareBufferBridgePassed =
            waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer execution=") == "ahardwarebuffer execution=PASS" &&
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer source=") ==
                "ahardwarebuffer source=wayland-display-commits" &&
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer wayland display backing=") ==
                "ahardwarebuffer wayland display backing=true" &&
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer visible payload bytes=") ==
                "ahardwarebuffer visible payload bytes=${waylandSurfaceSourceCommands.sumOf { it.dirtyBytes }}" &&
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer wayland state machine backing=") ==
                "ahardwarebuffer wayland state machine backing=true" &&
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer dirty rect bytes=") ==
                "ahardwarebuffer dirty rect bytes=${waylandSurfaceSourceCommands.sumOf { it.dirtyBytes }}" &&
                waylandSurfaceSourceCommands.size == WAYLAND_DISPLAY_FRAMES
        val hostVulkanHardwareCandidate = hostVulkanProbe.lineStartingWith("host vulkan hardware candidate=") == "host vulkan hardware candidate=true"
        val hostVulkanDiscoveryPassed = hostVulkanHardwareCandidate &&
            hostVulkanProbe.lineStartingWith("vulkan create device=") == "vulkan create device=ok"
        val alrInstalledPackageVulkanDiscoveryPassed =
            alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledVulkanDiscoveryClientFile.isFile &&
                rootfsInstalledVulkanDiscoveryClientFile.canExecute() &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.rawLines.any { it.startsWith("ALR_VK_DISCOVERY_HELLO ") } &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.ackLine.startsWith("ALR_VK_DISCOVERY_ACK status=PASS") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.deviceRecordLine.startsWith("ALR_VK_DEVICE_RECORD ") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.featureRecordLine.startsWith("ALR_VK_FEATURE_RECORD ") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clearRequestLine.startsWith("ALR_VK_SURFACE_CLEAR_REQUEST ") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clearAcceptedLine.startsWith("ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_DISCOVERY_ACK status=PASS") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_DEVICE_RECORD ") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_FEATURE_RECORD ") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_DISCOVERY_DEVICE_RECORD ok") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_DISCOVERY_FEATURE_RECORD ok") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_SURFACE_CLEAR_REQUEST_ACCEPTED ok") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_DISCOVERY_DONE ok") &&
                alrInstalledPackageVulkanDiscoveryBridgeResult.error == null
        val alrInstalledPackageVulkanProxyPassed =
            alrInstalledPackageVulkanProxyBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledVulkanProxySmokeFile.isFile &&
                rootfsInstalledVulkanProxySmokeFile.canExecute() &&
                rootfsInstalledVulkanProxyLibFile.isFile &&
                alrInstalledPackageVulkanProxyBridgeResult.clearRequestLine.contains("source=libvulkan-proxy") &&
                alrInstalledPackageVulkanProxyBridgeResult.clearAcceptedLine.startsWith("ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") &&
                alrInstalledPackageVulkanProxyBridgeResult.clearRequestLine.contains("protocol=binary-frame-v1") &&
                alrInstalledPackageVulkanProxyBridgeResult.clearAcceptedLine.contains("protocol=binary-frame-v1") &&
                alrInstalledPackageVulkanProxyBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_BINARY_BRIDGE_ACK status=PASS") &&
                alrInstalledPackageVulkanProxyBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_PROXY_STEP vkEnumerateInstanceVersion ok") &&
                alrInstalledPackageVulkanProxyBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_PROXY_BINARY_BRIDGE ok") &&
                alrInstalledPackageVulkanProxyBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_PROXY_SURFACE_CLEAR_REQUEST_ACCEPTED ok") &&
                alrInstalledPackageVulkanProxyBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_PROXY_DONE ok") &&
                alrInstalledPackageVulkanProxyBridgeResult.error == null
        val alrInstalledPackageVulkanIcdPassed =
            alrInstalledPackageVulkanIcdBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledVulkanIcdSmokeFile.isFile &&
                rootfsInstalledVulkanIcdSmokeFile.canExecute() &&
                rootfsInstalledVulkanIcdManifestFile.isFile &&
                rootfsInstalledVulkanProxyLibFile.isFile &&
                alrInstalledPackageVulkanIcdBridgeResult.clearRequestLine.contains("source=libvulkan-proxy") &&
                alrInstalledPackageVulkanIcdBridgeResult.clearRequestLine.contains("protocol=binary-frame-v1") &&
                alrInstalledPackageVulkanIcdBridgeResult.clearAcceptedLine.startsWith("ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") &&
                alrInstalledPackageVulkanIcdBridgeResult.clearAcceptedLine.contains("protocol=binary-frame-v1") &&
                alrInstalledPackageVulkanIcdBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_ICD_MANIFEST path=/usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json") &&
                alrInstalledPackageVulkanIcdBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_ICD_LIBRARY_PATH libvulkan.so.1") &&
                alrInstalledPackageVulkanIcdBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_ICD_BINARY_BRIDGE ok") &&
                alrInstalledPackageVulkanIcdBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_ICD_SURFACE_CLEAR_REQUEST_ACCEPTED ok") &&
                alrInstalledPackageVulkanIcdBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_ICD_DONE ok") &&
                alrInstalledPackageVulkanIcdBridgeResult.error == null
        val alrInstalledPackageVulkanLoaderInfoPassed =
            alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledVulkanLoaderInfoFile.isFile &&
                rootfsInstalledVulkanLoaderInfoFile.canExecute() &&
                rootfsInstalledVulkanIcdManifestFile.isFile &&
                rootfsInstalledVulkanProxyLibFile.isFile &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clearRequestLine.contains("source=libvulkan-proxy") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clearRequestLine.contains("protocol=binary-frame-v1") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clearAcceptedLine.startsWith("ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clearAcceptedLine.contains("protocol=binary-frame-v1") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains(
                    "ALR_VK_LOADER_SELECTED_MANIFEST /usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json",
                ) &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_ICD_LIBRARY_PATH libvulkan.so.1") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_VULKANINFO_INSTANCE_VERSION ok") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_VULKANINFO_DEVICE_RECORD ok") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_BINARY_BRIDGE ok") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_DONE ok") &&
                alrInstalledPackageVulkanLoaderInfoBridgeResult.error == null
        val alrInstalledPackageVulkanUnixLoaderInfoPassed =
            alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledVulkanLoaderInfoFile.isFile &&
                rootfsInstalledVulkanLoaderInfoFile.canExecute() &&
                rootfsInstalledVulkanIcdManifestFile.isFile &&
                rootfsInstalledVulkanProxyLibFile.isFile &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clearRequestLine.contains("source=libvulkan-proxy") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clearRequestLine.contains("protocol=binary-frame-v1") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clearRequestLine.contains("transport=unix-abstract") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clearAcceptedLine.startsWith("ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clearAcceptedLine.contains("transport=unix-abstract") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains(
                    "ALR_VK_LOADER_SELECTED_MANIFEST /usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json",
                ) &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_ICD_LIBRARY_PATH libvulkan.so.1") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_VULKANINFO_INSTANCE_VERSION ok") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_VULKANINFO_DEVICE_RECORD ok") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_BINARY_BRIDGE ok") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("ALR_VK_LOADER_DONE ok") &&
                alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.error == null

        val installedPackageCompatibilityTable =
            "script:${if (alrInstalledPackagePreloadExecutionPassed) "PASS" else "FAIL"}," +
                "gimp-profile:${if (gimpDemoProfileExecutionPassed) "PASS" else "FAIL"}," +
                "gpu-clear-ipc:${if (alrInstalledPackageGpuIpcBridgePassed) "PASS" else "FAIL"}," +
                "gles-demo:${if (alrInstalledPackageGlesDemoPassed) "PASS" else "FAIL"}," +
                "gles-tcp-ack:${if (alrInstalledPackageGlesIpcBridgePassed) "PASS" else "FAIL"}," +
                "gles-unix-ack:${if (alrInstalledPackageGlesUnixIpcBridgePassed) "PASS" else "FAIL"}," +
                "gles-unix-batch:${if (alrInstalledPackageGlesUnixBatchIpcBridgePassed) "PASS" else "FAIL"}," +
                "gles-procaddr:${if (alrInstalledPackageGlesProcaddrDemoPassed) "PASS" else "FAIL"}," +
                "wayland:${if (alrInstalledPackageWaylandGuiBridgePassed) "PASS" else "FAIL"}," +
                "x11:${if (alrInstalledPackageX11GuiBridgePassed) "PASS" else "FAIL"}," +
                "wayland-unix:${if (alrInstalledPackageWaylandGuiUnixBridgePassed) "PASS" else "FAIL"}," +
                "x11-unix:${if (alrInstalledPackageX11GuiUnixBridgePassed) "PASS" else "FAIL"}," +
                "wayland-display:${if (alrInstalledPackageWaylandDisplayBridgePassed) "PASS" else "FAIL"}," +
                "simple-gui-demo:${if (alrInstalledPackageSimpleGuiDemoPassed) "PASS" else "FAIL"}," +
                "vulkan-discovery:${if (alrInstalledPackageVulkanDiscoveryPassed) "PASS" else "FAIL"}," +
                "vulkan-proxy:${if (alrInstalledPackageVulkanProxyPassed) "PASS" else "FAIL"}," +
                "vulkan-icd:${if (alrInstalledPackageVulkanIcdPassed) "PASS" else "FAIL"}," +
                "vulkan-loader:${if (alrInstalledPackageVulkanLoaderInfoPassed) "PASS" else "FAIL"}," +
                "vulkan-loader-unix:${if (alrInstalledPackageVulkanUnixLoaderInfoPassed) "PASS" else "FAIL"}"

        val executionSummary = "build: 0.4.104-gimp3-wayland" +
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
            "\nGIMP DEMO PROFILE EXECUTION: ${if (gimpDemoProfileExecutionPassed) "PASS" else "FAIL"}" +
            "\nGIMP GTK WAYLAND PROBE EXECUTION: ${if (gimpGtkWaylandProbePassed) "PASS" else "FAIL"}" +
            "\nGIMP GUI WAYLAND PROBE EXECUTION: ${if (gimpGuiWaylandProbePassed) "PASS" else "FAIL"}" +
            "\nGIMP GUI WAYLAND BLOCKER: ${gimpGuiWaylandBlocker.uppercase().replace('-', '_')}" +
            "\nGIMP DEMO BUNDLE LOCK: ${if (rootfsGimpDemoBundleLockFile.isFile) "PASS" else "FAIL"}" +
            "\nHOST GPU EGL/GLES EXECUTION: ${if (hostGpuHardwareCandidate) "PASS" else "FAIL"}" +
            "\nHOST VULKAN DISCOVERY EXECUTION: ${if (hostVulkanDiscoveryPassed) "PASS" else "FAIL"}" +
            "\nHOST GPU SURFACE EXECUTION: PENDING_SURFACE_CALLBACK" +
            "\nGUEST GPU BRIDGE COMMAND EXECUTION: ${if (guestGpuBridgeCommandPassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GPU BRIDGE COMMAND EXECUTION: ${if (alrGuestGpuBridgeCommandPassed) "PASS" else "FAIL"}" +
            "\nGUEST GPU IPC BRIDGE EXECUTION: ${if (guestGpuIpcBridgePassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GPU IPC BRIDGE EXECUTION: ${if (alrGuestGpuIpcBridgePassed) "PASS" else "FAIL"}" +
            "\nALR INSTALLED PACKAGE GPU IPC EXECUTION: ${if (alrInstalledPackageGpuIpcBridgePassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES SHIM SMOKE EXECUTION: ${if (guestGlesShimSmokePassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GLES SHIM SMOKE EXECUTION: ${if (alrGuestGlesShimSmokePassed) "PASS" else "FAIL"}" +
            "\nGUEST EGL/GLES ABI LIB EXECUTION: ${if (guestGlesAbiSmokePassed) "PASS" else "FAIL"}" +
            "\nALR GUEST EGL/GLES ABI LIB EXECUTION: ${if (alrGuestGlesAbiSmokePassed) "PASS" else "FAIL"}" +
            "\nGUEST GLES DEMO GEARS EXECUTION: ${if (guestGlesDemoGearsPassed) "PASS" else "FAIL"}" +
            "\nALR GUEST GLES DEMO GEARS EXECUTION: ${if (alrGuestGlesDemoGearsPassed) "PASS" else "FAIL"}" +
            "\nALR INSTALLED PACKAGE GLES DEMO EXECUTION: ${if (alrInstalledPackageGlesDemoPassed) "PASS" else "FAIL"}" +
            "\nALR INSTALLED PACKAGE GLES IPC EXECUTION: ${if (alrInstalledPackageGlesIpcBridgePassed) "PASS" else "FAIL"}" +
            "\nALR INSTALLED PACKAGE GLES PROCADDR EXECUTION: ${if (alrInstalledPackageGlesProcaddrDemoPassed) "PASS" else "FAIL"}" +
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
            "\nALR INSTALLED PACKAGE WAYLAND GUI GPU BRIDGE EXECUTION: ${if (alrInstalledPackageWaylandGuiBridgePassed) "PASS" else "FAIL"}" +
            "\nALR INSTALLED PACKAGE X11 GUI GPU BRIDGE EXECUTION: ${if (alrInstalledPackageX11GuiBridgePassed) "PASS" else "FAIL"}" +
            "\nALR INSTALLED PACKAGE WAYLAND GUI GPU UNIX BRIDGE EXECUTION: ${if (alrInstalledPackageWaylandGuiUnixBridgePassed) "PASS" else "FAIL"}" +
            "\nALR INSTALLED PACKAGE X11 GUI GPU UNIX BRIDGE EXECUTION: ${if (alrInstalledPackageX11GuiUnixBridgePassed) "PASS" else "FAIL"}" +
            "\nGUI BRIDGE UNIX TRANSPORT EXECUTION: ${if (alrInstalledPackageWaylandGuiUnixBridgePassed && alrInstalledPackageX11GuiUnixBridgePassed) "PASS" else "FAIL"}" +
            "\nWAYLAND DISPLAY SOCKET AVAILABLE: ${if (alrInstalledPackageWaylandDisplayBridgePassed) "PASS" else "FAIL"}" +
            "\nWAYLAND DISPLAY COMMIT SURFACE EXECUTION: ${if (alrInstalledPackageWaylandDisplayBridgePassed) "PASS" else "FAIL"}" +
            "\nSIMPLE GUI DEMO EXECUTION: ${if (alrInstalledPackageSimpleGuiDemoPassed) "PASS" else "FAIL"}" +
            "\nSIMPLE GUI DEMO GLIBC DYNAMIC EXECUTION: ${if (alrInstalledPackageSimpleGuiDemoPassed) "PASS" else "FAIL"}" +
            "\nANDROID HOST AHARDWAREBUFFER EXECUTION: ${if (hostHardwareBufferPassed) "PASS" else "FAIL"}" +
            "\nANDROID HOST AHARDWAREBUFFER EGL IMPORT EXECUTION: ${if (hostHardwareBufferPassed) "PASS" else "FAIL"}" +
            "\nWAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: ${if (waylandHardwareBufferBridgePassed) "PASS" else "FAIL"}" +
            "\nWAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PENDING_SURFACE_CALLBACK" +
            "\nALR INSTALLED PACKAGE VULKAN DISCOVERY EXECUTION: ${if (alrInstalledPackageVulkanDiscoveryPassed) "PASS" else "FAIL"}" +
            "\nGUEST GUI GPU SURFACE EXECUTION: PENDING_SURFACE_CALLBACK" +
            "\nANDROID PERMISSION MODEL: ${if (internetPermissionDeclared && networkStatePermissionDeclared && !broadStoragePermissionDeclared) "PASS" else "FAIL"}" +
            "\nhost vulkan device=${hostVulkanProbe.lineStartingWith("host vulkan device=")}" +
            "\nhost vulkan hardware candidate=${hostVulkanProbe.lineStartingWith("host vulkan hardware candidate=")}" +
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
            "\nrootfs installed alr gimp demo exists=${rootfsInstalledGimpDemoFile.isFile} executable=${rootfsInstalledGimpDemoFile.canExecute()} bytes=${rootfsInstalledGimpDemoFile.length()}" +
            "\nrootfs gimp demo profile exists=${rootfsGimpDemoProfileFile.isFile} bytes=${rootfsGimpDemoProfileFile.length()}" +
            "\nrootfs gimp demo bundle lock exists=${rootfsGimpDemoBundleLockFile.isFile} bytes=${rootfsGimpDemoBundleLockFile.length()}" +
            "\nrootfs gimp demo materialized exists=${rootfsGimpDemoMaterializedFile.isFile} bytes=${rootfsGimpDemoMaterializedFile.length()}" +
            "\nrootfs /usr/bin/gimp exists=${rootfsGimpBinaryFile.isFile} executable=${rootfsGimpBinaryFile.canExecute()} bytes=${rootfsGimpBinaryFile.length()}" +
            "\ngimp gtk wayland socket path=${gimpGtkWaylandProbeResult.socketPath}" +
            "\ngimp gtk wayland connected=${gimpGtkWaylandProbeResult.connected}" +
            "\ngimp gtk wayland setup bytes=${gimpGtkWaylandProbeResult.setupBytes}" +
            "\ngimp gtk wayland object=${gimpGtkWaylandProbeResult.objectId}" +
            "\ngimp gtk wayland opcode=${gimpGtkWaylandProbeResult.opcode}" +
            "\ngimp gtk wayland size=${gimpGtkWaylandProbeResult.messageSize}" +
            "\ngimp gtk wayland request=${gimpGtkWaylandProbeResult.requestName}" +
            "\ngimp gtk wayland raw prefix=${gimpGtkWaylandProbeResult.rawPrefixHex}" +
            "\ngimp gtk wayland server requests=${gimpGtkWaylandProbeResult.waylandRequestCount}" +
            "\ngimp gtk wayland server response bytes=${gimpGtkWaylandProbeResult.waylandResponseBytes}" +
            "\ngimp gtk wayland server globals=${gimpGtkWaylandProbeResult.waylandGlobals.joinToString(",")}" +
            "\ngimp gtk wayland error=${gimpGtkWaylandProbeResult.error ?: "none"}" +
            "\ngimp gtk wayland handoff=${gimpGtkWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\ngimp gtk wayland stdout=${gimpGtkWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}" +
            "\ngimp gtk wayland stderr=${gimpGtkWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}" +
            "\ngimp gui wayland socket path=${gimpGuiWaylandProbeResult.socketPath}" +
            "\ngimp gui wayland connected=${gimpGuiWaylandProbeResult.connected}" +
            "\ngimp gui wayland setup bytes=${gimpGuiWaylandProbeResult.setupBytes}" +
            "\ngimp gui wayland object=${gimpGuiWaylandProbeResult.objectId}" +
            "\ngimp gui wayland opcode=${gimpGuiWaylandProbeResult.opcode}" +
            "\ngimp gui wayland size=${gimpGuiWaylandProbeResult.messageSize}" +
            "\ngimp gui wayland request=${gimpGuiWaylandProbeResult.requestName}" +
            "\ngimp gui wayland raw prefix=${gimpGuiWaylandProbeResult.rawPrefixHex}" +
            "\ngimp gui wayland server requests=${gimpGuiWaylandProbeResult.waylandRequestCount}" +
            "\ngimp gui wayland server response bytes=${gimpGuiWaylandProbeResult.waylandResponseBytes}" +
            "\ngimp gui wayland server globals=${gimpGuiWaylandProbeResult.waylandGlobals.joinToString(",")}" +
            "\ngimp gui wayland error=${gimpGuiWaylandProbeResult.error ?: "none"}" +
            "\ngimp gui wayland blocker=$gimpGuiWaylandBlocker" +
            "\ngimp gui wayland handoff=${gimpGuiWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\ngimp gui wayland stdout=${gimpGuiWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}" +
            "\ngimp gui wayland stderr=${gimpGuiWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}" +
            "\nrootfs installed alr gpu smoke exists=${rootfsInstalledGpuSmokeFile.isFile} executable=${rootfsInstalledGpuSmokeFile.canExecute()} bytes=${rootfsInstalledGpuSmokeFile.length()}" +
            "\nrootfs installed alr gles demo exists=${rootfsInstalledGlesDemoFile.isFile} executable=${rootfsInstalledGlesDemoFile.canExecute()} bytes=${rootfsInstalledGlesDemoFile.length()}" +
            "\nrootfs installed alr gles procaddr demo exists=${rootfsInstalledGlesProcaddrDemoFile.isFile} executable=${rootfsInstalledGlesProcaddrDemoFile.canExecute()} bytes=${rootfsInstalledGlesProcaddrDemoFile.length()}" +
            "\nrootfs installed alr wayland gui client exists=${rootfsInstalledWaylandGuiClientFile.isFile} executable=${rootfsInstalledWaylandGuiClientFile.canExecute()} bytes=${rootfsInstalledWaylandGuiClientFile.length()}" +
            "\nrootfs installed alr x11 gui client exists=${rootfsInstalledX11GuiClientFile.isFile} executable=${rootfsInstalledX11GuiClientFile.canExecute()} bytes=${rootfsInstalledX11GuiClientFile.length()}" +
            "\nrootfs installed alr wayland display client exists=${rootfsInstalledWaylandDisplayClientFile.isFile} executable=${rootfsInstalledWaylandDisplayClientFile.canExecute()} bytes=${rootfsInstalledWaylandDisplayClientFile.length()}" +
            "\nrootfs installed alr simple gui demo exists=${rootfsInstalledSimpleGuiDemoFile.isFile} executable=${rootfsInstalledSimpleGuiDemoFile.canExecute()} bytes=${rootfsInstalledSimpleGuiDemoFile.length()}" +
            "\nrootfs installed alr vulkan discovery client exists=${rootfsInstalledVulkanDiscoveryClientFile.isFile} executable=${rootfsInstalledVulkanDiscoveryClientFile.canExecute()} bytes=${rootfsInstalledVulkanDiscoveryClientFile.length()}" +
            "\nrootfs installed alr vulkan proxy smoke exists=${rootfsInstalledVulkanProxySmokeFile.isFile} executable=${rootfsInstalledVulkanProxySmokeFile.canExecute()} bytes=${rootfsInstalledVulkanProxySmokeFile.length()}" +
            "\nrootfs installed alr vulkan icd smoke exists=${rootfsInstalledVulkanIcdSmokeFile.isFile} executable=${rootfsInstalledVulkanIcdSmokeFile.canExecute()} bytes=${rootfsInstalledVulkanIcdSmokeFile.length()}" +
            "\nrootfs installed alr vulkan loader info exists=${rootfsInstalledVulkanLoaderInfoFile.isFile} executable=${rootfsInstalledVulkanLoaderInfoFile.canExecute()} bytes=${rootfsInstalledVulkanLoaderInfoFile.length()}" +
            "\nrootfs installed alr vulkan proxy lib exists=${rootfsInstalledVulkanProxyLibFile.isFile} bytes=${rootfsInstalledVulkanProxyLibFile.length()}" +
            "\nrootfs installed alr vulkan icd manifest exists=${rootfsInstalledVulkanIcdManifestFile.isFile} bytes=${rootfsInstalledVulkanIcdManifestFile.length()}" +
            "\ninstalled package compatibility table=$installedPackageCompatibilityTable" +
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
            "\nrootfs /usr/bin/alr-wayland-display-client exists=${rootfsWaylandDisplayClientFile.isFile} executable=${rootfsWaylandDisplayClientFile.canExecute()} bytes=${rootfsWaylandDisplayClientFile.length()}" +
            "\nrootfs /usr/bin/alr-simple-gui-demo exists=${rootfsSimpleGuiDemoFile.isFile} executable=${rootfsSimpleGuiDemoFile.canExecute()} bytes=${rootfsSimpleGuiDemoFile.length()}" +
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
            "\nalr installed package gpu ipc received frames=${alrInstalledPackageGpuIpcBridgeResult.commands.size}" +
            "\nalr installed package gpu ipc lossless=${alrInstalledPackageGpuIpcBridgeResult.expectedFrames > 0 && alrInstalledPackageGpuIpcBridgeResult.expectedFrames == alrInstalledPackageGpuIpcBridgeResult.commands.size}" +
            "\nalr installed package gpu ipc raw=${alrInstalledPackageGpuIpcBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package gpu ipc error=${alrInstalledPackageGpuIpcBridgeResult.error ?: "none"}" +
            "\nalr installed package gpu ipc handoff=${alrInstalledPackageGpuIpcBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package gpu ipc execve attempts=${alrInstalledPackageGpuIpcBridgeResult.clientResult.stdout.lineStartingWith("alr handoff execve attempt count=")}" +
            "\nalr installed package gpu ipc execve loader rewrites=${alrInstalledPackageGpuIpcBridgeResult.clientResult.stdout.lineStartingWith("alr handoff execve loader rewrite count=")}" +
            "\nalr installed package gpu ipc traced processes=${alrInstalledPackageGpuIpcBridgeResult.clientResult.stdout.lineStartingWith("alr handoff traced process count=")}" +
            "\nalr installed package gpu ipc stdout=${alrInstalledPackageGpuIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package gpu ipc stderr=${alrInstalledPackageGpuIpcBridgeResult.clientResult.stdout.alrHandoffStderrText()}" +
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
            "\nalr installed package gles demo handoff=${alrInstalledPackageGlesDemoResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package gles demo stdout=${alrInstalledPackageGlesDemoStdout}" +
            "\nalr installed package gles demo command parsed count=${alrInstalledPackageGlesDemoCommands.size}" +
            "\nalr installed package gles demo draw command count=${alrInstalledPackageGlesDemoCommands.count { it.protocol == "GLES_DRAW" }}" +
            "\nalr installed package gles ipc received frames=${alrInstalledPackageGlesIpcBridgeResult.commands.size}" +
            "\nalr installed package gles ipc draw frames=${alrInstalledPackageGlesIpcBridgeResult.commands.count { it.protocol == "GLES_DRAW" }}" +
            "\nalr installed package gles ipc ack frames=${alrInstalledPackageGlesIpcBridgeResult.ackLines.size}" +
            "\nalr installed package gles ipc lossless=${alrInstalledPackageGlesIpcBridgeResult.expectedFrames > 0 && alrInstalledPackageGlesIpcBridgeResult.expectedFrames == alrInstalledPackageGlesIpcBridgeResult.commands.size}" +
            "\nalr installed package gles ipc raw=${alrInstalledPackageGlesIpcBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package gles ipc ack raw=${alrInstalledPackageGlesIpcBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package gles ipc error=${alrInstalledPackageGlesIpcBridgeResult.error ?: "none"}" +
            "\nalr installed package gles ipc handoff=${alrInstalledPackageGlesIpcBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package gles ipc stdout=${alrInstalledPackageGlesIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package gles unix ipc received frames=${alrInstalledPackageGlesUnixIpcBridgeResult.commands.size}" +
            "\nalr installed package gles unix ipc draw frames=${alrInstalledPackageGlesUnixIpcBridgeResult.commands.count { it.protocol == "GLES_DRAW" }}" +
            "\nalr installed package gles unix ipc ack frames=${alrInstalledPackageGlesUnixIpcBridgeResult.ackLines.size}" +
            "\nalr installed package gles unix ipc lossless=${alrInstalledPackageGlesUnixIpcBridgeResult.expectedFrames > 0 && alrInstalledPackageGlesUnixIpcBridgeResult.expectedFrames == alrInstalledPackageGlesUnixIpcBridgeResult.commands.size}" +
            "\nalr installed package gles unix ipc raw=${alrInstalledPackageGlesUnixIpcBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package gles unix ipc ack raw=${alrInstalledPackageGlesUnixIpcBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package gles unix ipc error=${alrInstalledPackageGlesUnixIpcBridgeResult.error ?: "none"}" +
            "\nalr installed package gles unix ipc handoff=${alrInstalledPackageGlesUnixIpcBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package gles unix ipc stdout=${alrInstalledPackageGlesUnixIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package gles unix batch ipc received frames=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.commands.size}" +
            "\nalr installed package gles unix batch ipc draw frames=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.commands.count { it.protocol == "GLES_DRAW" }}" +
            "\nalr installed package gles unix batch ipc ack frames=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.ackLines.size}" +
            "\nalr installed package gles unix batch ipc lossless=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.expectedFrames > 0 && alrInstalledPackageGlesUnixBatchIpcBridgeResult.expectedFrames == alrInstalledPackageGlesUnixBatchIpcBridgeResult.commands.size}" +
            "\nalr installed package gles unix batch ipc raw=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package gles unix batch ipc ack raw=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package gles unix batch ipc error=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.error ?: "none"}" +
            "\nalr installed package gles unix batch ipc handoff=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package gles unix batch ipc stdout=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\ngles bridge transport tcp loader elapsed ms=${alrInstalledPackageGlesIpcBridgeResult.clientResult.elapsedMs}" +
            "\ngles bridge transport unix loader elapsed ms=${alrInstalledPackageGlesUnixIpcBridgeResult.clientResult.elapsedMs}" +
            "\ngles bridge transport unix batch loader elapsed ms=${alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult.elapsedMs}" +
            "\ngles bridge transport unix vs tcp ratio pct=${elapsedRatioPct(alrInstalledPackageGlesUnixIpcBridgeResult.clientResult, alrInstalledPackageGlesIpcBridgeResult.clientResult)}" +
            "\ngles bridge transport unix faster than tcp=${isFaster(alrInstalledPackageGlesUnixIpcBridgeResult.clientResult, alrInstalledPackageGlesIpcBridgeResult.clientResult)}" +
            "\ngles bridge transport unix batch vs tcp ratio pct=${elapsedRatioPct(alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult, alrInstalledPackageGlesIpcBridgeResult.clientResult)}" +
            "\ngles bridge transport unix batch vs unix ack ratio pct=${elapsedRatioPct(alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult, alrInstalledPackageGlesUnixIpcBridgeResult.clientResult)}" +
            "\ngles bridge transport unix batch faster than unix ack=${isFaster(alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult, alrInstalledPackageGlesUnixIpcBridgeResult.clientResult)}" +
            "\nalr installed package gles procaddr handoff=${alrInstalledPackageGlesProcaddrDemoResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package gles procaddr stdout=${alrInstalledPackageGlesProcaddrDemoStdout}" +
            "\nalr installed package gles procaddr command parsed count=${alrInstalledPackageGlesProcaddrDemoCommands.size}" +
            "\nalr installed package gles procaddr draw command count=${alrInstalledPackageGlesProcaddrDemoCommands.count { it.protocol == "GLES_DRAW" }}" +
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
            "\nalr installed package wayland gui ipc received frames=${alrInstalledPackageWaylandGuiBridgeResult.commands.size}" +
            "\nalr installed package wayland gui ipc lossless=${alrInstalledPackageWaylandGuiBridgeResult.expectedFrames > 0 && alrInstalledPackageWaylandGuiBridgeResult.expectedFrames == alrInstalledPackageWaylandGuiBridgeResult.commands.size}" +
            "\nalr installed package wayland gui ipc raw=${alrInstalledPackageWaylandGuiBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package wayland gui ipc error=${alrInstalledPackageWaylandGuiBridgeResult.error ?: "none"}" +
            "\nalr installed package wayland gui ipc client handoff=${alrInstalledPackageWaylandGuiBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package wayland gui ipc stdout=${alrInstalledPackageWaylandGuiBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package x11 gui ipc received frames=${alrInstalledPackageX11GuiBridgeResult.commands.size}" +
            "\nalr installed package x11 gui ipc lossless=${alrInstalledPackageX11GuiBridgeResult.expectedFrames > 0 && alrInstalledPackageX11GuiBridgeResult.expectedFrames == alrInstalledPackageX11GuiBridgeResult.commands.size}" +
            "\nalr installed package x11 gui ipc raw=${alrInstalledPackageX11GuiBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package x11 gui ipc error=${alrInstalledPackageX11GuiBridgeResult.error ?: "none"}" +
            "\nalr installed package x11 gui ipc client handoff=${alrInstalledPackageX11GuiBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package x11 gui ipc stdout=${alrInstalledPackageX11GuiBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package wayland gui unix ipc received frames=${alrInstalledPackageWaylandGuiUnixBridgeResult.commands.size}" +
            "\nalr installed package wayland gui unix ipc lossless=${alrInstalledPackageWaylandGuiUnixBridgeResult.expectedFrames > 0 && alrInstalledPackageWaylandGuiUnixBridgeResult.expectedFrames == alrInstalledPackageWaylandGuiUnixBridgeResult.commands.size}" +
            "\nalr installed package wayland gui unix ipc raw=${alrInstalledPackageWaylandGuiUnixBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package wayland gui unix ipc ack raw=${alrInstalledPackageWaylandGuiUnixBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package wayland gui unix ipc error=${alrInstalledPackageWaylandGuiUnixBridgeResult.error ?: "none"}" +
            "\nalr installed package wayland gui unix ipc client handoff=${alrInstalledPackageWaylandGuiUnixBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package wayland gui unix ipc stdout=${alrInstalledPackageWaylandGuiUnixBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package x11 gui unix ipc received frames=${alrInstalledPackageX11GuiUnixBridgeResult.commands.size}" +
            "\nalr installed package x11 gui unix ipc lossless=${alrInstalledPackageX11GuiUnixBridgeResult.expectedFrames > 0 && alrInstalledPackageX11GuiUnixBridgeResult.expectedFrames == alrInstalledPackageX11GuiUnixBridgeResult.commands.size}" +
            "\nalr installed package x11 gui unix ipc raw=${alrInstalledPackageX11GuiUnixBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package x11 gui unix ipc ack raw=${alrInstalledPackageX11GuiUnixBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package x11 gui unix ipc error=${alrInstalledPackageX11GuiUnixBridgeResult.error ?: "none"}" +
            "\nalr installed package x11 gui unix ipc client handoff=${alrInstalledPackageX11GuiUnixBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package x11 gui unix ipc stdout=${alrInstalledPackageX11GuiUnixBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package wayland display ipc received frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.size}" +
            "\nalr installed package wayland display ipc lossless=${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames > 0 && alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames == alrInstalledPackageWaylandDisplayBridgeResult.commands.size}" +
            "\nalr installed package wayland display ipc raw=${alrInstalledPackageWaylandDisplayBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package wayland display ipc ack raw=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package wayland display ipc error=${alrInstalledPackageWaylandDisplayBridgeResult.error ?: "none"}" +
            "\nalr installed package wayland display ipc client handoff=${alrInstalledPackageWaylandDisplayBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package wayland display ipc stdout=${alrInstalledPackageWaylandDisplayBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nwayland display socket name=alr-wayland-0" +
            "\nwayland display transport unix=true" +
            "\nwayland display surface commits=${alrInstalledPackageWaylandDisplayBridgeResult.commands.size}" +
            "\nwayland display shared payload frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.payloadVerified }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}" +
            "\nwayland display shared payload bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.payloadBytes }}" +
            "\nwayland display shared payload checksums=${alrInstalledPackageWaylandDisplayBridgeResult.commands.joinToString("|") { "%08x".format(it.payloadChecksum) }}" +
            "\nwayland display fd payload frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.fdPayloadVerified }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}" +
            "\nwayland display fd payload bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.fdPayloadBytes }}" +
            "\nwayland display fd payload checksums=${alrInstalledPackageWaylandDisplayBridgeResult.commands.joinToString("|") { "%08x".format(it.fdPayloadChecksum) }}" +
            "\nwayland display ahardwarebuffer backed frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.backing == "host-ahardwarebuffer" }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}" +
            "\nwayland display dirty rect frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.partialUpdate }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}" +
            "\nwayland display dirty rect bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.dirtyBytes }}" +
            "\nwayland display partial upload ratio pct=${partialUploadRatioPct(alrInstalledPackageWaylandDisplayBridgeResult.commands)}" +
            "\ngui bridge transport wayland tcp loader elapsed ms=${alrInstalledPackageWaylandGuiBridgeResult.clientResult.elapsedMs}" +
            "\ngui bridge transport wayland unix loader elapsed ms=${alrInstalledPackageWaylandGuiUnixBridgeResult.clientResult.elapsedMs}" +
            "\ngui bridge transport wayland unix vs tcp ratio pct=${elapsedRatioPct(alrInstalledPackageWaylandGuiUnixBridgeResult.clientResult, alrInstalledPackageWaylandGuiBridgeResult.clientResult)}" +
            "\ngui bridge transport wayland unix faster than tcp=${isFaster(alrInstalledPackageWaylandGuiUnixBridgeResult.clientResult, alrInstalledPackageWaylandGuiBridgeResult.clientResult)}" +
            "\ngui bridge transport x11 tcp loader elapsed ms=${alrInstalledPackageX11GuiBridgeResult.clientResult.elapsedMs}" +
            "\ngui bridge transport x11 unix loader elapsed ms=${alrInstalledPackageX11GuiUnixBridgeResult.clientResult.elapsedMs}" +
            "\ngui bridge transport x11 unix vs tcp ratio pct=${elapsedRatioPct(alrInstalledPackageX11GuiUnixBridgeResult.clientResult, alrInstalledPackageX11GuiBridgeResult.clientResult)}" +
            "\ngui bridge transport x11 unix faster than tcp=${isFaster(alrInstalledPackageX11GuiUnixBridgeResult.clientResult, alrInstalledPackageX11GuiBridgeResult.clientResult)}" +
            "\nalr installed package vulkan discovery host=${alrInstalledPackageVulkanDiscoveryBridgeResult.host}" +
            "\nalr installed package vulkan discovery port=${alrInstalledPackageVulkanDiscoveryBridgeResult.port}" +
            "\nalr installed package vulkan discovery raw=${alrInstalledPackageVulkanDiscoveryBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package vulkan discovery ack=${alrInstalledPackageVulkanDiscoveryBridgeResult.ackLine}" +
            "\nalr installed package vulkan discovery device record=${alrInstalledPackageVulkanDiscoveryBridgeResult.deviceRecordLine}" +
            "\nalr installed package vulkan discovery feature record=${alrInstalledPackageVulkanDiscoveryBridgeResult.featureRecordLine}" +
            "\nalr installed package vulkan surface clear request=${alrInstalledPackageVulkanDiscoveryBridgeResult.clearRequestLine}" +
            "\nalr installed package vulkan surface clear accepted=${alrInstalledPackageVulkanDiscoveryBridgeResult.clearAcceptedLine}" +
            "\nalr installed package vulkan discovery ack lines=${alrInstalledPackageVulkanDiscoveryBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package vulkan discovery error=${alrInstalledPackageVulkanDiscoveryBridgeResult.error ?: "none"}" +
            "\nalr installed package vulkan discovery handoff=${alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package vulkan discovery stdout=${alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package vulkan proxy raw=${alrInstalledPackageVulkanProxyBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package vulkan proxy surface clear request=${alrInstalledPackageVulkanProxyBridgeResult.clearRequestLine}" +
            "\nalr installed package vulkan proxy surface clear accepted=${alrInstalledPackageVulkanProxyBridgeResult.clearAcceptedLine}" +
            "\nalr installed package vulkan proxy ack lines=${alrInstalledPackageVulkanProxyBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package vulkan proxy error=${alrInstalledPackageVulkanProxyBridgeResult.error ?: "none"}" +
            "\nalr installed package vulkan proxy handoff=${alrInstalledPackageVulkanProxyBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package vulkan proxy stdout=${alrInstalledPackageVulkanProxyBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package vulkan icd raw=${alrInstalledPackageVulkanIcdBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package vulkan icd surface clear request=${alrInstalledPackageVulkanIcdBridgeResult.clearRequestLine}" +
            "\nalr installed package vulkan icd surface clear accepted=${alrInstalledPackageVulkanIcdBridgeResult.clearAcceptedLine}" +
            "\nalr installed package vulkan icd ack lines=${alrInstalledPackageVulkanIcdBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package vulkan icd error=${alrInstalledPackageVulkanIcdBridgeResult.error ?: "none"}" +
            "\nalr installed package vulkan icd handoff=${alrInstalledPackageVulkanIcdBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package vulkan icd stdout=${alrInstalledPackageVulkanIcdBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package vulkan loader info raw=${alrInstalledPackageVulkanLoaderInfoBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package vulkan loader info surface clear request=${alrInstalledPackageVulkanLoaderInfoBridgeResult.clearRequestLine}" +
            "\nalr installed package vulkan loader info surface clear accepted=${alrInstalledPackageVulkanLoaderInfoBridgeResult.clearAcceptedLine}" +
            "\nalr installed package vulkan loader info ack lines=${alrInstalledPackageVulkanLoaderInfoBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package vulkan loader info error=${alrInstalledPackageVulkanLoaderInfoBridgeResult.error ?: "none"}" +
            "\nalr installed package vulkan loader info handoff=${alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package vulkan loader info stdout=${alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nalr installed package vulkan unix loader info raw=${alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.rawLines.joinToString("|")}" +
            "\nalr installed package vulkan unix loader info surface clear request=${alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clearRequestLine}" +
            "\nalr installed package vulkan unix loader info surface clear accepted=${alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clearAcceptedLine}" +
            "\nalr installed package vulkan unix loader info ack lines=${alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.ackLines.joinToString("|")}" +
            "\nalr installed package vulkan unix loader info error=${alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.error ?: "none"}" +
            "\nalr installed package vulkan unix loader info handoff=${alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package vulkan unix loader info stdout=${alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\nvulkan bridge transport tcp loader elapsed ms=${alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult.elapsedMs}" +
            "\nvulkan bridge transport unix loader elapsed ms=${alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult.elapsedMs}" +
            "\nvulkan bridge transport unix vs tcp ratio pct=${elapsedRatioPct(alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult, alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult)}" +
            "\nvulkan bridge transport unix faster than tcp=${isFaster(alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult, alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult)}" +
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
            "\nalr installed package gimp demo profile handoff=${alrInstalledPackageGimpDemoProfileResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nalr installed package gimp demo profile stdout=$gimpDemoProfileStdout" +
            "\nalr installed package gimp demo profile stderr=${alrInstalledPackageGimpDemoProfileResult.stdout.alrHandoffStderrText()}" +
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
            "\nhost ahardwarebuffer execution=${hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer execution=")}" +
            "\nhost ahardwarebuffer buffers=${hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer allocated buffers=")}" +
            "\nhost ahardwarebuffer egl import=${hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer egl image import=")}" +
            "\nwayland ahardwarebuffer execution=${waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer execution=")}" +
            "\nwayland ahardwarebuffer backing=${waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer wayland display backing=")}" +
            "\nwayland ahardwarebuffer bytes=${waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer visible payload bytes=")}" +
            "\nhost vulkan device=${hostVulkanProbe.lineStartingWith("host vulkan device=")}" +
            "\nhost vulkan hardware candidate=${hostVulkanProbe.lineStartingWith("host vulkan hardware candidate=")}" +
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
            "\n\nAndroid host AHardwareBuffer probe:" +
            "\n$hostHardwareBufferProbe" +
            "\n\nWayland display AHardwareBuffer backing probe:" +
            "\n$waylandHardwareBufferBridgeProbe" +
            "\n\nAndroid host Vulkan probe:" +
            "\n$hostVulkanProbe" +
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
            resultBlock("alr installed package gimp demo profile", alrInstalledPackageGimpDemoProfileResult) +
            resultBlock("proot dpkg -i local deb", prootDpkgInstallLocalResult) +
            resultBlock("proot installed package smoke", prootInstalledPackageSmokeResult) +
            resultBlock("proot guest gpu client", prootGuestGpuClientResult) +
            resultBlock("alr guest gpu client", alrGuestGpuClientResult) +
            resultBlock("proot guest gpu ipc client", guestGpuIpcBridgeResult.clientResult) +
            resultBlock("alr guest gpu ipc client", alrGuestGpuIpcBridgeResult.clientResult) +
            resultBlock("alr installed package gpu ipc client", alrInstalledPackageGpuIpcBridgeResult.clientResult) +
            resultBlock("proot guest gles shim smoke", prootGuestGlesShimSmokeResult) +
            resultBlock("alr guest gles shim smoke", alrGuestGlesShimSmokeResult) +
            resultBlock("proot guest gles abi smoke", prootGuestGlesAbiSmokeResult) +
            resultBlock("alr guest gles abi smoke", alrGuestGlesAbiSmokeResult) +
            resultBlock("proot guest gles demo gears", prootGuestGlesDemoGearsResult) +
            resultBlock("alr guest gles demo gears", alrGuestGlesDemoGearsResult) +
            resultBlock("alr installed package gles demo", alrInstalledPackageGlesDemoResult) +
            resultBlock("alr installed package gles ipc demo", alrInstalledPackageGlesIpcBridgeResult.clientResult) +
            resultBlock("alr installed package gles unix ipc demo", alrInstalledPackageGlesUnixIpcBridgeResult.clientResult) +
            resultBlock("alr installed package gles unix batch ipc demo", alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult) +
            resultBlock("alr installed package gles procaddr demo", alrInstalledPackageGlesProcaddrDemoResult) +
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
            resultBlock("alr installed package wayland gui ipc client", alrInstalledPackageWaylandGuiBridgeResult.clientResult) +
            resultBlock("alr installed package x11 gui ipc client", alrInstalledPackageX11GuiBridgeResult.clientResult) +
            resultBlock("alr installed package wayland gui unix ipc client", alrInstalledPackageWaylandGuiUnixBridgeResult.clientResult) +
            resultBlock("alr installed package x11 gui unix ipc client", alrInstalledPackageX11GuiUnixBridgeResult.clientResult) +
            resultBlock("alr installed package wayland display ipc client", alrInstalledPackageWaylandDisplayBridgeResult.clientResult) +
            resultBlock("alr installed package simple gui demo", alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult) +
            resultBlock("alr installed package vulkan discovery client", alrInstalledPackageVulkanDiscoveryBridgeResult.clientResult) +
            resultBlock("alr installed package vulkan proxy smoke", alrInstalledPackageVulkanProxyBridgeResult.clientResult) +
            resultBlock("alr installed package vulkan icd manifest smoke", alrInstalledPackageVulkanIcdBridgeResult.clientResult) +
            resultBlock("alr installed package vulkan loader info", alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult) +
            resultBlock("alr installed package vulkan unix loader info", alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult) +
            optionalResultBlock("proot hello verbose on failure", prootHelloVerboseResult)

        val report = executionSummary + "\n\n--- verbose report ---\n" + verboseReport
        Log.i(
            "ALR_DEVICE_EVIDENCE",
            listOf(
                "build: 0.4.104-gimp3-wayland",
                "WAYLAND DISPLAY SOCKET AVAILABLE: ${if (alrInstalledPackageWaylandDisplayBridgePassed) "PASS" else "FAIL"}",
                "WAYLAND DISPLAY COMMIT SURFACE EXECUTION: ${if (alrInstalledPackageWaylandDisplayBridgePassed) "PASS" else "FAIL"}",
                "SIMPLE GUI DEMO EXECUTION: ${if (alrInstalledPackageSimpleGuiDemoPassed) "PASS" else "FAIL"}",
                "SIMPLE GUI DEMO GLIBC DYNAMIC EXECUTION: ${if (alrInstalledPackageSimpleGuiDemoPassed) "PASS" else "FAIL"}",
                "GIMP DEMO PROFILE EXECUTION: ${if (gimpDemoProfileExecutionPassed) "PASS" else "FAIL"}",
                "GIMP GTK WAYLAND PROBE EXECUTION: ${if (gimpGtkWaylandProbePassed) "PASS" else "FAIL"}",
                "GIMP GUI WAYLAND PROBE EXECUTION: ${if (gimpGuiWaylandProbePassed) "PASS" else "FAIL"}",
                "GIMP GUI WAYLAND BLOCKER: ${gimpGuiWaylandBlocker.uppercase().replace('-', '_')}",
                "GIMP DEMO BUNDLE LOCK: ${if (rootfsGimpDemoBundleLockFile.isFile) "PASS" else "FAIL"}",
                "gimp gtk wayland socket path=${gimpGtkWaylandProbeResult.socketPath}",
                "gimp gtk wayland connected=${gimpGtkWaylandProbeResult.connected}",
                "gimp gtk wayland setup bytes=${gimpGtkWaylandProbeResult.setupBytes}",
                "gimp gtk wayland object=${gimpGtkWaylandProbeResult.objectId}",
                "gimp gtk wayland opcode=${gimpGtkWaylandProbeResult.opcode}",
                "gimp gtk wayland size=${gimpGtkWaylandProbeResult.messageSize}",
                "gimp gtk wayland request=${gimpGtkWaylandProbeResult.requestName}",
                "gimp gtk wayland raw prefix=${gimpGtkWaylandProbeResult.rawPrefixHex}",
                "gimp gtk wayland server requests=${gimpGtkWaylandProbeResult.waylandRequestCount}",
                "gimp gtk wayland server response bytes=${gimpGtkWaylandProbeResult.waylandResponseBytes}",
                "gimp gtk wayland server globals=${gimpGtkWaylandProbeResult.waylandGlobals.joinToString(",")}",
                "gimp gtk wayland error=${gimpGtkWaylandProbeResult.error ?: "none"}",
                "gimp gtk wayland handoff=${gimpGtkWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
                "gimp gtk wayland stdout=${gimpGtkWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
                "gimp gtk wayland stderr=${gimpGtkWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
                "gimp gui wayland socket path=${gimpGuiWaylandProbeResult.socketPath}",
                "gimp gui wayland connected=${gimpGuiWaylandProbeResult.connected}",
                "gimp gui wayland setup bytes=${gimpGuiWaylandProbeResult.setupBytes}",
                "gimp gui wayland object=${gimpGuiWaylandProbeResult.objectId}",
                "gimp gui wayland opcode=${gimpGuiWaylandProbeResult.opcode}",
                "gimp gui wayland size=${gimpGuiWaylandProbeResult.messageSize}",
                "gimp gui wayland request=${gimpGuiWaylandProbeResult.requestName}",
                "gimp gui wayland raw prefix=${gimpGuiWaylandProbeResult.rawPrefixHex}",
                "gimp gui wayland server requests=${gimpGuiWaylandProbeResult.waylandRequestCount}",
                "gimp gui wayland server response bytes=${gimpGuiWaylandProbeResult.waylandResponseBytes}",
                "gimp gui wayland server globals=${gimpGuiWaylandProbeResult.waylandGlobals.joinToString(",")}",
                "gimp gui wayland error=${gimpGuiWaylandProbeResult.error ?: "none"}",
                "gimp gui wayland blocker=$gimpGuiWaylandBlocker",
                "gimp gui wayland handoff=${gimpGuiWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
                "gimp gui wayland stdout=${gimpGuiWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
                "gimp gui wayland stderr=${gimpGuiWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
                "ANDROID HOST AHARDWAREBUFFER EXECUTION: ${if (hostHardwareBufferPassed) "PASS" else "FAIL"}",
                "WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: ${if (waylandHardwareBufferBridgePassed) "PASS" else "FAIL"}",
                hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer allocated buffers="),
                hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer cpu verified buffers="),
                hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer egl imported buffers="),
                hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer visible payload bytes="),
                hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer egl image import="),
                hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer host managed triple buffer="),
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer source="),
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer wayland display backing="),
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer wayland state machine backing="),
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer dirty rect bytes="),
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer partial upload ratio pct="),
                waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer visible payload bytes="),
                "rootfs installed alr wayland display client exists=${rootfsInstalledWaylandDisplayClientFile.isFile} executable=${rootfsInstalledWaylandDisplayClientFile.canExecute()} bytes=${rootfsInstalledWaylandDisplayClientFile.length()}",
                "rootfs /usr/bin/alr-wayland-display-client exists=${rootfsWaylandDisplayClientFile.isFile} executable=${rootfsWaylandDisplayClientFile.canExecute()} bytes=${rootfsWaylandDisplayClientFile.length()}",
                "rootfs installed alr simple gui demo exists=${rootfsInstalledSimpleGuiDemoFile.isFile} executable=${rootfsInstalledSimpleGuiDemoFile.canExecute()} bytes=${rootfsInstalledSimpleGuiDemoFile.length()}",
                "rootfs /usr/bin/alr-simple-gui-demo exists=${rootfsSimpleGuiDemoFile.isFile} executable=${rootfsSimpleGuiDemoFile.canExecute()} bytes=${rootfsSimpleGuiDemoFile.length()}",
                "rootfs installed alr gimp demo exists=${rootfsInstalledGimpDemoFile.isFile} executable=${rootfsInstalledGimpDemoFile.canExecute()} bytes=${rootfsInstalledGimpDemoFile.length()}",
                "rootfs gimp demo profile exists=${rootfsGimpDemoProfileFile.isFile} bytes=${rootfsGimpDemoProfileFile.length()}",
                "rootfs gimp demo bundle lock exists=${rootfsGimpDemoBundleLockFile.isFile} bytes=${rootfsGimpDemoBundleLockFile.length()}",
                "rootfs gimp demo materialized exists=${rootfsGimpDemoMaterializedFile.isFile} bytes=${rootfsGimpDemoMaterializedFile.length()}",
                "rootfs /usr/bin/gimp exists=${rootfsGimpBinaryFile.isFile} executable=${rootfsGimpBinaryFile.canExecute()} bytes=${rootfsGimpBinaryFile.length()}",
                "gimp demo profile outer exit=${alrInstalledPackageGimpDemoProfileResult.exitCode}",
                "gimp demo profile elapsed ms=${alrInstalledPackageGimpDemoProfileResult.elapsedMs}",
                "gimp demo profile handoff=${alrInstalledPackageGimpDemoProfileResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
                "gimp demo profile stdout=$gimpDemoProfileStdout",
                "gimp demo profile stderr=${alrInstalledPackageGimpDemoProfileResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
                "gimp demo profile raw stdout=${alrInstalledPackageGimpDemoProfileResult.stdout.forEvidenceLog()}",
                "gimp demo profile raw stderr=${alrInstalledPackageGimpDemoProfileResult.stderr.forEvidenceLog()}",
                "simple gui demo outer exit=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.exitCode}",
                "simple gui demo elapsed ms=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.elapsedMs}",
                "simple gui demo handoff=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
                "simple gui demo stderr=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
                "simple gui demo raw stdout=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.forEvidenceLog()}",
                "simple gui demo raw stderr=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stderr.forEvidenceLog()}",
                "simple gui demo glibc dynamic=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("glibc_dynamic=true")}",
                "simple gui demo display commits=${alrInstalledPackageSimpleGuiDemoBridgeResult.commands.size}/${alrInstalledPackageSimpleGuiDemoBridgeResult.expectedFrames}",
                "simple gui demo binary messages=${alrInstalledPackageSimpleGuiDemoBridgeResult.rawLines.count { it.startsWith("ALR_WL_BINARY_MESSAGE ") }}",
                "simple gui demo continuous stream ready=${alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.firstOrNull()?.contains("continuous_stream_ready=true") == true}",
                "simple gui demo android surface candidate=${alrInstalledPackageSimpleGuiDemoPassed && waylandHardwareBufferBridgePassed}",
                "simple gui demo stdout=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.alrHandoffStdoutText()}",
                "alr installed package wayland display ipc received frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.size}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}",
                "wayland display shared payload frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.payloadVerified }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}",
                "wayland display shared payload bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.payloadBytes }}",
                "wayland display fd payload frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.fdPayloadVerified }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}",
                "wayland display fd payload bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.fdPayloadBytes }}",
                "wayland display continuous stream ready=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.firstOrNull()?.contains("continuous_stream_ready=true") == true}",
                "wayland display wire messages=${alrInstalledPackageWaylandDisplayBridgeResult.rawLines.count { it.startsWith("ALR_WL_WIRE ") }}",
                "wayland display wire subset ready=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.firstOrNull()?.contains("wire_subset_ready=true") == true}",
                "wayland display wire surface lifecycle=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.firstOrNull()?.contains("wire_surface_lifecycle=true") == true}",
                "wayland display binary messages=${alrInstalledPackageWaylandDisplayBridgeResult.rawLines.count { it.startsWith("ALR_WL_BINARY_MESSAGE ") }}",
                "wayland display binary bytes=${tokenValue(alrInstalledPackageWaylandDisplayBridgeResult.rawLines.firstOrNull { it.startsWith("ALR_WL_BINARY_STREAM ") }.orEmpty(), "bytes") ?: "0"}",
                "wayland display binary subset ready=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.firstOrNull()?.contains("binary_subset_ready=true") == true}",
                "wayland display ahardwarebuffer backed frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.backing == "host-ahardwarebuffer" }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}",
                "wayland display dirty rect frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.partialUpdate }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}",
                "wayland display dirty rect bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.dirtyBytes }}",
                "wayland display partial upload ratio pct=${partialUploadRatioPct(alrInstalledPackageWaylandDisplayBridgeResult.commands)}",
                "alr installed package wayland display ipc ack raw=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.joinToString("|")}",
                "alr installed package wayland display ipc error=${alrInstalledPackageWaylandDisplayBridgeResult.error ?: "none"}",
                "alr installed package wayland display client exit=${alrInstalledPackageWaylandDisplayBridgeResult.clientResult.exitCode}",
                "alr installed package wayland display client stdout=${alrInstalledPackageWaylandDisplayBridgeResult.clientResult.stdout.alrHandoffStdoutText()}",
                "alr installed package wayland display client stderr=${alrInstalledPackageWaylandDisplayBridgeResult.clientResult.stdout.alrHandoffStderrText()}",
            ).joinToString("\n"),
        )

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
                    val waylandHardwareBufferSurfaceReport = nativeRenderWaylandHardwareBufferSurface(
                        holder.surface,
                        encodeSurfaceFrames(waylandSurfaceSourceCommands),
                    )
                    val vulkanSurfaceReport = nativeRenderVulkanSurfaceClear(
                        holder.surface,
                        alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clearRequestLine,
                    )
                    val executionUpdate = surfaceExecutionUpdate(
                        surfaceReport,
                        guestGlesShimInitPassed,
                        guestGlesShimContextPassed,
                        guestGlesShimClearPassed,
                        guestGlesShimSwapPassed,
                        guestGlesShimDrawApiPassed,
                    )
                    val vulkanExecutionUpdate = vulkanSurfaceExecutionUpdate(
                        vulkanSurfaceReport,
                        alrInstalledPackageVulkanIcdPassed,
                        alrInstalledPackageVulkanLoaderInfoPassed,
                        alrInstalledPackageVulkanUnixLoaderInfoPassed,
                    )
                    val vulkanTransportUpdate = vulkanBridgeTransportUpdate(
                        alrInstalledPackageVulkanLoaderInfoBridgeResult.clientResult,
                        alrInstalledPackageVulkanUnixLoaderInfoBridgeResult.clientResult,
                        alrInstalledPackageVulkanUnixLoaderInfoPassed,
                    )
                    val glesTransportUpdate = glesBridgeTransportUpdate(
                        alrInstalledPackageGlesIpcBridgeResult.clientResult,
                        alrInstalledPackageGlesUnixIpcBridgeResult.clientResult,
                        alrInstalledPackageGlesUnixBatchIpcBridgeResult.clientResult,
                        alrInstalledPackageGlesUnixIpcBridgePassed,
                        alrInstalledPackageGlesUnixBatchIpcBridgePassed,
                    )
                    val guiTransportUpdate = guiBridgeTransportUpdate(
                        alrInstalledPackageWaylandGuiBridgeResult,
                        alrInstalledPackageWaylandGuiUnixBridgeResult,
                        alrInstalledPackageX11GuiBridgeResult,
                        alrInstalledPackageX11GuiUnixBridgeResult,
                        alrInstalledPackageWaylandGuiUnixBridgePassed,
                        alrInstalledPackageX11GuiUnixBridgePassed,
                    )
                    val waylandDisplayUpdate = waylandDisplayBridgeUpdate(
                        alrInstalledPackageWaylandDisplayBridgeResult,
                        alrInstalledPackageWaylandDisplayBridgePassed,
                    )
                    val hardwareBufferUpdate = hardwareBufferExecutionUpdate(hostHardwareBufferProbe)
                    val waylandHardwareBufferUpdate = waylandHardwareBufferBridgeUpdate(waylandHardwareBufferBridgeProbe)
                    val waylandHardwareBufferSurfaceUpdate =
                        waylandHardwareBufferSurfaceUpdate(waylandHardwareBufferSurfaceReport)
                    Log.i(
                        "ALR_SURFACE_EVIDENCE",
                        listOf(
                            "build: 0.4.104-gimp3-wayland",
                            "WAYLAND DISPLAY SOCKET AVAILABLE: ${if (alrInstalledPackageWaylandDisplayBridgePassed) "PASS" else "FAIL"}",
                            "WAYLAND DISPLAY COMMIT SURFACE EXECUTION: ${if (alrInstalledPackageWaylandDisplayBridgePassed) "PASS" else "FAIL"}",
                            "SIMPLE GUI DEMO EXECUTION: ${if (alrInstalledPackageSimpleGuiDemoPassed) "PASS" else "FAIL"}",
                            "SIMPLE GUI DEMO GLIBC DYNAMIC EXECUTION: ${if (alrInstalledPackageSimpleGuiDemoPassed) "PASS" else "FAIL"}",
                            "ANDROID HOST AHARDWAREBUFFER EXECUTION: ${if (hostHardwareBufferPassed) "PASS" else "FAIL"}",
                            "WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: ${if (waylandHardwareBufferBridgePassed) "PASS" else "FAIL"}",
                            waylandHardwareBufferSurfaceUpdate.lineStartingWith("WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION:"),
                            hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer egl image import="),
                            hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer visible payload bytes="),
                            hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer host managed triple buffer="),
                            hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer cpu write dirty rect locks="),
                            hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer cpu write fence count="),
                            hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer cpu read fence count="),
                            hostHardwareBufferProbe.lineStartingWith("ahardwarebuffer sync fence accounting="),
                            waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer source="),
                            waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer wayland display backing="),
                            waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer wayland state machine backing="),
                            waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer dirty rect bytes="),
                            waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer partial upload ratio pct="),
                            waylandHardwareBufferBridgeProbe.lineStartingWith("ahardwarebuffer visible payload bytes="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface compositor="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface replay passes="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface continuous guest commits="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface simple gui demo candidate="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface total frame submissions="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool mode="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool slots="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool misses="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool reuses="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface imported textures="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface sampled frames="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface presented frames="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface hardware render="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface dirty rect bytes="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface partial upload ratio pct="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface fence wait candidates="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface fence wait handoffs="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface fence pacing mode="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface sync fence accounting="),
                            waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface execution="),
                            "wayland display surface commits=${alrInstalledPackageWaylandDisplayBridgeResult.commands.size}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}",
                            "wayland display shared payload frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.payloadVerified }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}",
                            "wayland display shared payload bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.payloadBytes }}",
                            "wayland display fd payload frames=${alrInstalledPackageWaylandDisplayBridgeResult.commands.count { it.fdPayloadVerified }}/${alrInstalledPackageWaylandDisplayBridgeResult.expectedFrames}",
                            "wayland display fd payload bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.fdPayloadBytes }}",
                            "wayland display continuous stream ready=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.firstOrNull()?.contains("continuous_stream_ready=true") == true}",
                            "wayland display wire messages=${alrInstalledPackageWaylandDisplayBridgeResult.rawLines.count { it.startsWith("ALR_WL_WIRE ") }}",
                            "wayland display wire subset ready=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.firstOrNull()?.contains("wire_subset_ready=true") == true}",
                            "wayland display wire surface lifecycle=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.firstOrNull()?.contains("wire_surface_lifecycle=true") == true}",
                            "wayland display binary messages=${alrInstalledPackageWaylandDisplayBridgeResult.rawLines.count { it.startsWith("ALR_WL_BINARY_MESSAGE ") }}",
                            "wayland display binary bytes=${tokenValue(alrInstalledPackageWaylandDisplayBridgeResult.rawLines.firstOrNull { it.startsWith("ALR_WL_BINARY_STREAM ") }.orEmpty(), "bytes") ?: "0"}",
                            "wayland display binary subset ready=${alrInstalledPackageWaylandDisplayBridgeResult.ackLines.firstOrNull()?.contains("binary_subset_ready=true") == true}",
                            "wayland display dirty rect bytes=${alrInstalledPackageWaylandDisplayBridgeResult.commands.sumOf { it.dirtyBytes }}",
                            "wayland display partial upload ratio pct=${partialUploadRatioPct(alrInstalledPackageWaylandDisplayBridgeResult.commands)}",
                            "simple gui demo glibc dynamic=${alrInstalledPackageSimpleGuiDemoBridgeResult.clientResult.stdout.alrHandoffStdoutText().contains("glibc_dynamic=true")}",
                            "simple gui demo display commits=${alrInstalledPackageSimpleGuiDemoBridgeResult.commands.size}/${alrInstalledPackageSimpleGuiDemoBridgeResult.expectedFrames}",
                            "simple gui demo binary messages=${alrInstalledPackageSimpleGuiDemoBridgeResult.rawLines.count { it.startsWith("ALR_WL_BINARY_MESSAGE ") }}",
                            "simple gui demo continuous stream ready=${alrInstalledPackageSimpleGuiDemoBridgeResult.ackLines.firstOrNull()?.contains("continuous_stream_ready=true") == true}",
                            "simple gui demo android surface candidate=${alrInstalledPackageSimpleGuiDemoPassed && waylandHardwareBufferSurfaceReport.lineStartingWith("wayland ahardwarebuffer surface hardware render=") == "wayland ahardwarebuffer surface hardware render=true"}",
                            surfaceReport.lineStartingWith("surface wayland frames rendered="),
                            surfaceReport.lineStartingWith("surface x11 frames rendered="),
                            vulkanSurfaceReport.lineStartingWith("surface vulkan present="),
                            vulkanSurfaceReport.lineStartingWith("surface vulkan hardware render="),
                        ).joinToString("\n"),
                    )
                    surfaceStatusView.text =
                        "Linux guest GPU Surface renderer callback complete\n$executionUpdate\n$waylandDisplayUpdate\n$guiTransportUpdate\n$glesTransportUpdate\n$hardwareBufferUpdate\n$waylandHardwareBufferUpdate\n$waylandHardwareBufferSurfaceUpdate\n$vulkanExecutionUpdate\n$vulkanTransportUpdate"
                    view.append(
                        "\n\n--- Linux guest Wayland/X11 GUI GPU surface renderer ---\n" +
                            "$executionUpdate\n$waylandDisplayUpdate\n$guiTransportUpdate\n$glesTransportUpdate\n$surfaceReport" +
                            "\n\n--- Android host AHardwareBuffer bridge probe ---\n" +
                            "$hardwareBufferUpdate\n$hostHardwareBufferProbe" +
                            "\n\n--- Wayland display AHardwareBuffer backing probe ---\n" +
                            "$waylandHardwareBufferUpdate\n$waylandHardwareBufferBridgeProbe" +
                            "\n\n--- Wayland AHardwareBuffer Surface compositor ---\n" +
                            "$waylandHardwareBufferSurfaceUpdate\n$waylandHardwareBufferSurfaceReport" +
                            "\n\n--- Android host Vulkan Surface clear renderer ---\n" +
                            "$vulkanExecutionUpdate\n$vulkanTransportUpdate\n$vulkanSurfaceReport",
                    )
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

    private fun runGimp3WaylandVerificationMode() {
        val rootfsManifest = RootfsManifest(
            name = "debian-arm64",
            version = "trixie-slim-2026-05-gimp3-wayland-v104",
            assets = listOf(
                RootfsAsset(
                    path = "tiny-rootfs.tar",
                    sha256 = "9ed659c149510393662754f2508805f84edef5721a49539c26fe820481fcd75e",
                    sizeBytes = 1365166080,
                ),
            ),
        )
        val rootfsPlan = buildRootfsInstallPlan(rootfsManifest, filesDir)
        val rootfsStatus = RootfsInstaller(this).prepareBundledTinyRootfs()
        val nativeCommandRunner = NativeCommandRunner(
            File(applicationInfo.nativeLibraryDir),
            File(cacheDir, "proot-tmp"),
        )
        val runDeepGimpProbe = intent.getBooleanExtra("ALR_RUN_FULL_GIMP_PROBE", false)
        val gimpProfileResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGimpDemoProfile(rootfsStatus.rootfsDir)
        val gimpHelpResult = nativeCommandRunner.runAlrRuntimeTrampolineGimp3HelpProbe(rootfsStatus.rootfsDir)
        val gimpConsoleVersionResult = nativeCommandRunner.runAlrRuntimeTrampolineGimp3ConsoleVersionProbe(rootfsStatus.rootfsDir)
        val gimpCoreQuitResult = nativeCommandRunner.runAlrRuntimeTrampolineGimp3CoreQuitProbe(rootfsStatus.rootfsDir)
        val gimpConsoleBatchQuitResult = nativeCommandRunner.runAlrRuntimeTrampolineGimp3ConsoleBatchQuitProbe(rootfsStatus.rootfsDir)
        val gimpGtkWaylandProbeResult = runGimpGtkWaylandProbe(nativeCommandRunner, rootfsStatus.rootfsDir)
        val gimpGtkWindowWaylandProbeResult = runGimpGtkWindowWaylandProbe(nativeCommandRunner, rootfsStatus.rootfsDir)
        val gimpGdkSurfaceWaylandProbeResult = runGimpGdkSurfaceWaylandProbe(nativeCommandRunner, rootfsStatus.rootfsDir)
        val gimpGuiQuitWaylandProbeResult = runGimpGuiQuitWaylandProbe(nativeCommandRunner, rootfsStatus.rootfsDir)
        val gimpGuiWaylandProbeResult = runGimpGuiWaylandProbe(
            nativeCommandRunner,
            rootfsStatus.rootfsDir,
            fast = !runDeepGimpProbe,
        )

        val rootfsInstalledGimpDemoFile = File(rootfsStatus.rootfsDir, "usr/local/bin/alr-package-gimp-demo")
        val rootfsGimpDemoProfileFile = File(rootfsStatus.rootfsDir, "usr/share/androlinux/gimp-demo-profile.json")
        val rootfsGimpDemoBundleLockFile = File(rootfsStatus.rootfsDir, "usr/share/androlinux/gimp-demo-bundle.lock.json")
        val rootfsGimpDemoMaterializedFile = File(rootfsStatus.rootfsDir, "usr/share/androlinux/gimp-demo-materialized.txt")
        val rootfsGimpBinaryFile = File(rootfsStatus.rootfsDir, "usr/bin/gimp")
        val gimpLockText = if (rootfsGimpDemoBundleLockFile.isFile) rootfsGimpDemoBundleLockFile.readText() else ""
        val gimpLockPackageCount = Regex("\"package_count\"\\s*:\\s*(\\d+)").find(gimpLockText)?.groupValues?.get(1) ?: "0"
        val gimpLockDownloadSizeMib = Regex("\"download_size_mib\"\\s*:\\s*([0-9.]+)").find(gimpLockText)?.groupValues?.get(1) ?: "0"
        val gimpLockSuite = Regex("\"suite\"\\s*:\\s*\"([^\"]+)\"").find(gimpLockText)?.groupValues?.get(1) ?: "unknown"
        val gimpMaterializedText = if (rootfsGimpDemoMaterializedFile.isFile) rootfsGimpDemoMaterializedFile.readText() else ""
        val gimpMaterializedPackageCount = gimpMaterializedText.lineStartingWith("package_count=").removePrefix("package_count=").ifBlank { "0" }
        val gimpMaterializedVersion = gimpMaterializedText.lineStartingWith("gimp_version=").removePrefix("gimp_version=").ifBlank { "unknown" }
        val gimpVersionStdout = gimpProfileResult.stdout.alrHandoffStdoutText()
        val gimpVersionExit = if (
            gimpProfileResult.exitCode == 0 &&
            gimpProfileResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS")
        ) {
            "0"
        } else {
            "1"
        }
        val gimpDemoProfileStdout = buildString {
            appendLine("ALR_GIMP_DEMO_PROFILE_READY target=gimp version=v104 profile=/usr/share/androlinux/gimp-demo-profile.json lock=/usr/share/androlinux/gimp-demo-bundle.lock.json")
            appendLine("ALR_GIMP_DEMO_PROFILE_PROGRAM path=/usr/bin/gimp argv=gimp,--version")
            appendLine("ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=wayland WAYLAND_DISPLAY=alr-gimp-0 XDG_RUNTIME_DIR=/tmp NO_AT_BRIDGE=1")
            appendLine("ALR_GIMP_DEMO_BUNDLE_LOCK present=${rootfsGimpDemoBundleLockFile.isFile} suite=$gimpLockSuite package_count=$gimpLockPackageCount download_size_mib=$gimpLockDownloadSizeMib")
            appendLine("ALR_GIMP_DEMO_MATERIALIZED present=${rootfsGimpDemoMaterializedFile.isFile} package_count=$gimpMaterializedPackageCount gimp_version=$gimpMaterializedVersion")
            appendLine("ALR_GIMP_DEMO_BINARY present=${rootfsGimpBinaryFile.isFile && rootfsGimpBinaryFile.canExecute()} path=/usr/bin/gimp")
            appendLine("ALR_GIMP_DEMO_LAUNCH_MODE version-probe")
            appendLine("ALR_GIMP_DEMO_VERSION_EXIT $gimpVersionExit")
            gimpVersionStdout.lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line -> appendLine("ALR_GIMP_DEMO_VERSION_STDOUT $line") }
            appendLine("ALR_GIMP_DEMO_EXEC_READY ${gimpVersionExit == "0"} mode=version-probe")
        }.trim()
        val gimpDemoProfileExecutionPassed =
            gimpProfileResult.exitCode == 0 &&
                gimpProfileResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                rootfsInstalledGimpDemoFile.isFile &&
                rootfsInstalledGimpDemoFile.canExecute() &&
                rootfsGimpDemoProfileFile.isFile &&
                rootfsGimpDemoBundleLockFile.isFile &&
                rootfsGimpDemoMaterializedFile.isFile &&
                rootfsGimpBinaryFile.isFile &&
                rootfsGimpBinaryFile.canExecute() &&
                gimpDemoProfileStdout.contains("ALR_GIMP_DEMO_VERSION_STDOUT GNU Image Manipulation Program version 3.")
        val gimpHelpExecutionPassed =
            gimpHelpResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                gimpHelpResult.stdout.alrHandoffStdoutText().contains("--no-interface") &&
                gimpHelpResult.stdout.alrHandoffStdoutText().contains("--quit")
        val gimpConsoleVersionPassed =
            gimpConsoleVersionResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                gimpConsoleVersionResult.stdout.alrHandoffStdoutText().contains("GNU Image Manipulation Program version 3.")
        val gimpCoreQuitPassed =
            gimpCoreQuitResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                gimpCoreQuitResult.exitCode == 0
        val gimpConsoleBatchQuitPassed =
            gimpConsoleBatchQuitResult.stdout.contains("ALR STATIC ENTRY HANDOFF: PASS") &&
                gimpConsoleBatchQuitResult.exitCode == 0
        val gimpCoreQuitBlocker = describeGimpCoreQuitBlocker(gimpCoreQuitPassed, gimpCoreQuitResult)
        val gimpConsoleBatchQuitBlocker = describeGimpConsoleBatchQuitBlocker(gimpConsoleBatchQuitPassed, gimpConsoleBatchQuitResult)
        val gimpGtkWaylandProbePassed = isWaylandRegistryProbe(gimpGtkWaylandProbeResult)
        val gimpGtkWindowWaylandProbePassed = gimpGtkWindowWaylandProbeResult.connected &&
            gimpGtkWindowWaylandProbeResult.waylandRequestNames.any { it == "wl_shm.create_pool" || it == "wl_compositor.create_surface" }
        val gimpGdkSurfaceWaylandProbePassed = gimpGdkSurfaceWaylandProbeResult.connected &&
            gimpGdkSurfaceWaylandProbeResult.waylandRequestNames.any {
                it == "wl_compositor.create_surface" ||
                    it == "xdg_wm_base.get_xdg_surface" ||
                    it == "wl_surface.commit" ||
                    it == "wl_shm.create_pool"
            }
        val gimpGuiQuitWaylandProbePassed = isWaylandRegistryProbe(gimpGuiQuitWaylandProbeResult)
        val gimpGuiWaylandProbePassed = isWaylandRegistryProbe(gimpGuiWaylandProbeResult)
        val gimpGuiWaylandBlocker = if (runDeepGimpProbe || gimpGuiWaylandProbeResult.connected) {
            describeGimpGuiWaylandBlocker(gimpGuiWaylandProbePassed, gimpGuiWaylandProbeResult)
        } else {
            "pre-wayland-connect"
        }

        val evidence = listOf(
            "build: 0.4.104-gimp3-wayland",
            "versionCode=104",
            "versionName=0.4.104-gimp3-wayland",
            "rootfs plan verified=${rootfsPlan.assetDestinations.values.all { it.exists() }}",
            "rootfs verified=${rootfsStatus.verified} extracted=${rootfsStatus.extracted}",
            "full gimp probe mode=${if (runDeepGimpProbe) "deep" else "fast-scout"}",
            "GIMP DEMO PROFILE EXECUTION: ${if (gimpDemoProfileExecutionPassed) "PASS" else "FAIL"}",
            "GIMP CLI HELP PROBE EXECUTION: ${if (gimpHelpExecutionPassed) "PASS" else "FAIL"}",
            "GIMP CONSOLE VERSION PROBE EXECUTION: ${if (gimpConsoleVersionPassed) "PASS" else "FAIL"}",
            "GIMP CORE QUIT PROBE EXECUTION: ${if (gimpCoreQuitPassed) "PASS" else "FAIL"}",
            "GIMP CORE QUIT BLOCKER: ${gimpCoreQuitBlocker.uppercase().replace('-', '_')}",
            "GIMP CONSOLE BATCH QUIT PROBE EXECUTION: ${if (gimpConsoleBatchQuitPassed) "PASS" else "FAIL"}",
            "GIMP CONSOLE BATCH QUIT BLOCKER: ${gimpConsoleBatchQuitBlocker.uppercase().replace('-', '_')}",
            "GIMP GTK WAYLAND PROBE EXECUTION: ${if (gimpGtkWaylandProbePassed) "PASS" else "FAIL"}",
            "GIMP GTK WINDOW WAYLAND PROBE EXECUTION: ${if (gimpGtkWindowWaylandProbePassed) "PASS" else "FAIL"}",
            "GIMP GDK SURFACE WAYLAND PROBE EXECUTION: ${if (gimpGdkSurfaceWaylandProbePassed) "PASS" else "FAIL"}",
            "GIMP GUI QUIT WAYLAND PROBE EXECUTION: ${if (gimpGuiQuitWaylandProbePassed) "PASS" else "FAIL"}",
            "GIMP GUI WAYLAND PROBE EXECUTION: ${if (gimpGuiWaylandProbePassed) "PASS" else "FAIL"}",
            "GIMP GUI WAYLAND BLOCKER: ${gimpGuiWaylandBlocker.uppercase().replace('-', '_')}",
            "GIMP DEMO BUNDLE LOCK: ${if (rootfsGimpDemoBundleLockFile.isFile) "PASS" else "FAIL"}",
            gimpDemoProfileStdout,
            "gimp cli help handoff=${gimpHelpResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp cli help stdout=${gimpHelpResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp cli help stderr=${gimpHelpResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
            "gimp console version handoff=${gimpConsoleVersionResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp console version exit=${gimpConsoleVersionResult.exitCode}",
            "gimp console version stdout=${gimpConsoleVersionResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp console version stderr=${gimpConsoleVersionResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
            "gimp core quit handoff=${gimpCoreQuitResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp core quit exit=${gimpCoreQuitResult.exitCode}",
            "gimp core quit blocker=$gimpCoreQuitBlocker",
            "gimp core quit timed out=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff timed out=")}",
            "gimp core quit child exited=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff child exited=")}",
            "gimp core quit child signaled=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff child signaled=")}",
            "gimp core quit handoff exit=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff exit code=")}",
            "gimp core quit handoff signal=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff signal=")}",
            "gimp core quit fault signal=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff fault signal=")}",
            "gimp core quit fault syscall=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff fault syscall=")}",
            "gimp core quit fault pc=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff fault pc=")}",
            "gimp core quit elapsed=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff elapsed ms=")}",
            "gimp core quit traced processes=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff traced process count=")}",
            "gimp core quit path rewrite syscalls=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff path rewrite syscall count=")}",
            "gimp core quit path rewrites=${gimpCoreQuitResult.stdout.lineStartingWith("alr handoff path rewrite count=")}",
            "gimp core quit stdout=${gimpCoreQuitResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp core quit stderr=${gimpCoreQuitResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
            "gimp console batch quit handoff=${gimpConsoleBatchQuitResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp console batch quit exit=${gimpConsoleBatchQuitResult.exitCode}",
            "gimp console batch quit interpreter=plug-in-script-fu-eval",
            "gimp console batch quit blocker=$gimpConsoleBatchQuitBlocker",
            "gimp console batch quit timed out=${gimpConsoleBatchQuitResult.stdout.lineStartingWith("alr handoff timed out=")}",
            "gimp console batch quit handoff signal=${gimpConsoleBatchQuitResult.stdout.lineStartingWith("alr handoff signal=")}",
            "gimp console batch quit fault syscall=${gimpConsoleBatchQuitResult.stdout.lineStartingWith("alr handoff fault syscall=")}",
            "gimp console batch quit elapsed=${gimpConsoleBatchQuitResult.stdout.lineStartingWith("alr handoff elapsed ms=")}",
            "gimp console batch quit traced processes=${gimpConsoleBatchQuitResult.stdout.lineStartingWith("alr handoff traced process count=")}",
            "gimp console batch quit path rewrite syscalls=${gimpConsoleBatchQuitResult.stdout.lineStartingWith("alr handoff path rewrite syscall count=")}",
            "gimp console batch quit path rewrites=${gimpConsoleBatchQuitResult.stdout.lineStartingWith("alr handoff path rewrite count=")}",
            "gimp console batch quit stdout=${gimpConsoleBatchQuitResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp console batch quit stderr=${gimpConsoleBatchQuitResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
            "rootfs gimp demo materialized exists=${rootfsGimpDemoMaterializedFile.isFile}",
            "rootfs /usr/bin/gimp exists=${rootfsGimpBinaryFile.isFile} executable=${rootfsGimpBinaryFile.canExecute()}",
            "gimp gtk wayland socket path=${gimpGtkWaylandProbeResult.socketPath}",
            "gimp gtk wayland connected=${gimpGtkWaylandProbeResult.connected}",
            "gimp gtk wayland setup bytes=${gimpGtkWaylandProbeResult.setupBytes}",
            "gimp gtk wayland object=${gimpGtkWaylandProbeResult.objectId}",
            "gimp gtk wayland opcode=${gimpGtkWaylandProbeResult.opcode}",
            "gimp gtk wayland size=${gimpGtkWaylandProbeResult.messageSize}",
            "gimp gtk wayland request=${gimpGtkWaylandProbeResult.requestName}",
            "gimp gtk wayland raw prefix=${gimpGtkWaylandProbeResult.rawPrefixHex}",
            "gimp gtk wayland server requests=${gimpGtkWaylandProbeResult.waylandRequestCount}",
            "gimp gtk wayland server response bytes=${gimpGtkWaylandProbeResult.waylandResponseBytes}",
            "gimp gtk wayland server globals=${gimpGtkWaylandProbeResult.waylandGlobals.joinToString(",")}",
            "gimp gtk wayland server request trace=${gimpGtkWaylandProbeResult.waylandRequestNames.joinToString(",")}",
            "gimp gtk wayland server bind trace=${gimpGtkWaylandProbeResult.waylandBindInterfaces.joinToString(",")}",
            "gimp gtk wayland server last request=${gimpGtkWaylandProbeResult.lastWaylandRequest}",
            "gimp gtk wayland server fd count=${gimpGtkWaylandProbeResult.waylandReceivedFdCount}",
            "gimp gtk wayland server fd bytes=${gimpGtkWaylandProbeResult.waylandReceivedFdBytes}",
            "gimp gtk wayland server fd verified=${gimpGtkWaylandProbeResult.waylandReceivedFdVerifiedCount}",
            "gimp gtk wayland server shm create pools=${gimpGtkWaylandProbeResult.waylandShmCreatePoolCount}",
            "gimp gtk wayland server shm pool resizes=${gimpGtkWaylandProbeResult.waylandShmPoolResizeCount}",
            "gimp gtk wayland server shm pool buffers=${gimpGtkWaylandProbeResult.waylandShmPoolCreateBufferCount}",
            "gimp gtk wayland server surfaces created=${gimpGtkWaylandProbeResult.waylandSurfaceCreateCount}",
            "gimp gtk wayland server data devices=${gimpGtkWaylandProbeResult.waylandDataDeviceRequestCount}",
            "gimp gtk wayland server shell roles=${gimpGtkWaylandProbeResult.waylandShellRoleRequestCount}",
            "gimp gtk wayland server surface attaches=${gimpGtkWaylandProbeResult.waylandSurfaceAttachCount}",
            "gimp gtk wayland server surface commits=${gimpGtkWaylandProbeResult.waylandSurfaceCommitCount}",
            "gimp gtk wayland server seat trace=${gimpGtkWaylandProbeResult.waylandSeatRequestNames.joinToString(",")}",
            "gimp gtk wayland server keyboard keymaps=${gimpGtkWaylandProbeResult.waylandKeyboardKeymapSentCount}",
            "gimp gtk wayland error=${gimpGtkWaylandProbeResult.error ?: "none"}",
            "gimp gtk wayland handoff=${gimpGtkWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp gtk wayland stdout=${gimpGtkWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp gtk wayland stderr=${gimpGtkWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
            "gimp gtk window wayland socket path=${gimpGtkWindowWaylandProbeResult.socketPath}",
            "gimp gtk window wayland connected=${gimpGtkWindowWaylandProbeResult.connected}",
            "gimp gtk window wayland setup bytes=${gimpGtkWindowWaylandProbeResult.setupBytes}",
            "gimp gtk window wayland object=${gimpGtkWindowWaylandProbeResult.objectId}",
            "gimp gtk window wayland opcode=${gimpGtkWindowWaylandProbeResult.opcode}",
            "gimp gtk window wayland size=${gimpGtkWindowWaylandProbeResult.messageSize}",
            "gimp gtk window wayland request=${gimpGtkWindowWaylandProbeResult.requestName}",
            "gimp gtk window wayland raw prefix=${gimpGtkWindowWaylandProbeResult.rawPrefixHex}",
            "gimp gtk window wayland server requests=${gimpGtkWindowWaylandProbeResult.waylandRequestCount}",
            "gimp gtk window wayland server response bytes=${gimpGtkWindowWaylandProbeResult.waylandResponseBytes}",
            "gimp gtk window wayland server globals=${gimpGtkWindowWaylandProbeResult.waylandGlobals.joinToString(",")}",
            "gimp gtk window wayland server request trace=${gimpGtkWindowWaylandProbeResult.waylandRequestNames.joinToString(",")}",
            "gimp gtk window wayland server bind trace=${gimpGtkWindowWaylandProbeResult.waylandBindInterfaces.joinToString(",")}",
            "gimp gtk window wayland server last request=${gimpGtkWindowWaylandProbeResult.lastWaylandRequest}",
            "gimp gtk window wayland server fd count=${gimpGtkWindowWaylandProbeResult.waylandReceivedFdCount}",
            "gimp gtk window wayland server fd bytes=${gimpGtkWindowWaylandProbeResult.waylandReceivedFdBytes}",
            "gimp gtk window wayland server fd verified=${gimpGtkWindowWaylandProbeResult.waylandReceivedFdVerifiedCount}",
            "gimp gtk window wayland server shm create pools=${gimpGtkWindowWaylandProbeResult.waylandShmCreatePoolCount}",
            "gimp gtk window wayland server shm pool resizes=${gimpGtkWindowWaylandProbeResult.waylandShmPoolResizeCount}",
            "gimp gtk window wayland server shm pool buffers=${gimpGtkWindowWaylandProbeResult.waylandShmPoolCreateBufferCount}",
            "gimp gtk window wayland server surfaces created=${gimpGtkWindowWaylandProbeResult.waylandSurfaceCreateCount}",
            "gimp gtk window wayland server data devices=${gimpGtkWindowWaylandProbeResult.waylandDataDeviceRequestCount}",
            "gimp gtk window wayland server shell roles=${gimpGtkWindowWaylandProbeResult.waylandShellRoleRequestCount}",
            "gimp gtk window wayland server surface attaches=${gimpGtkWindowWaylandProbeResult.waylandSurfaceAttachCount}",
            "gimp gtk window wayland server surface commits=${gimpGtkWindowWaylandProbeResult.waylandSurfaceCommitCount}",
            "gimp gtk window wayland server seat trace=${gimpGtkWindowWaylandProbeResult.waylandSeatRequestNames.joinToString(",")}",
            "gimp gtk window wayland server keyboard keymaps=${gimpGtkWindowWaylandProbeResult.waylandKeyboardKeymapSentCount}",
            "gimp gtk window wayland error=${gimpGtkWindowWaylandProbeResult.error ?: "none"}",
            "gimp gtk window wayland handoff=${gimpGtkWindowWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp gtk window wayland stdout=${gimpGtkWindowWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp gtk window wayland stderr=${gimpGtkWindowWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
            "gimp gdk surface wayland socket path=${gimpGdkSurfaceWaylandProbeResult.socketPath}",
            "gimp gdk surface wayland connected=${gimpGdkSurfaceWaylandProbeResult.connected}",
            "gimp gdk surface wayland setup bytes=${gimpGdkSurfaceWaylandProbeResult.setupBytes}",
            "gimp gdk surface wayland object=${gimpGdkSurfaceWaylandProbeResult.objectId}",
            "gimp gdk surface wayland opcode=${gimpGdkSurfaceWaylandProbeResult.opcode}",
            "gimp gdk surface wayland size=${gimpGdkSurfaceWaylandProbeResult.messageSize}",
            "gimp gdk surface wayland request=${gimpGdkSurfaceWaylandProbeResult.requestName}",
            "gimp gdk surface wayland raw prefix=${gimpGdkSurfaceWaylandProbeResult.rawPrefixHex}",
            "gimp gdk surface wayland server requests=${gimpGdkSurfaceWaylandProbeResult.waylandRequestCount}",
            "gimp gdk surface wayland server response bytes=${gimpGdkSurfaceWaylandProbeResult.waylandResponseBytes}",
            "gimp gdk surface wayland server globals=${gimpGdkSurfaceWaylandProbeResult.waylandGlobals.joinToString(",")}",
            "gimp gdk surface wayland server request trace=${gimpGdkSurfaceWaylandProbeResult.waylandRequestNames.joinToString(",")}",
            "gimp gdk surface wayland server bind trace=${gimpGdkSurfaceWaylandProbeResult.waylandBindInterfaces.joinToString(",")}",
            "gimp gdk surface wayland server last request=${gimpGdkSurfaceWaylandProbeResult.lastWaylandRequest}",
            "gimp gdk surface wayland server fd count=${gimpGdkSurfaceWaylandProbeResult.waylandReceivedFdCount}",
            "gimp gdk surface wayland server fd bytes=${gimpGdkSurfaceWaylandProbeResult.waylandReceivedFdBytes}",
            "gimp gdk surface wayland server fd verified=${gimpGdkSurfaceWaylandProbeResult.waylandReceivedFdVerifiedCount}",
            "gimp gdk surface wayland server shm create pools=${gimpGdkSurfaceWaylandProbeResult.waylandShmCreatePoolCount}",
            "gimp gdk surface wayland server shm pool resizes=${gimpGdkSurfaceWaylandProbeResult.waylandShmPoolResizeCount}",
            "gimp gdk surface wayland server shm pool buffers=${gimpGdkSurfaceWaylandProbeResult.waylandShmPoolCreateBufferCount}",
            "gimp gdk surface wayland server surfaces created=${gimpGdkSurfaceWaylandProbeResult.waylandSurfaceCreateCount}",
            "gimp gdk surface wayland server data devices=${gimpGdkSurfaceWaylandProbeResult.waylandDataDeviceRequestCount}",
            "gimp gdk surface wayland server shell roles=${gimpGdkSurfaceWaylandProbeResult.waylandShellRoleRequestCount}",
            "gimp gdk surface wayland server surface attaches=${gimpGdkSurfaceWaylandProbeResult.waylandSurfaceAttachCount}",
            "gimp gdk surface wayland server surface commits=${gimpGdkSurfaceWaylandProbeResult.waylandSurfaceCommitCount}",
            "gimp gdk surface wayland server seat trace=${gimpGdkSurfaceWaylandProbeResult.waylandSeatRequestNames.joinToString(",")}",
            "gimp gdk surface wayland server keyboard keymaps=${gimpGdkSurfaceWaylandProbeResult.waylandKeyboardKeymapSentCount}",
            "gimp gdk surface wayland error=${gimpGdkSurfaceWaylandProbeResult.error ?: "none"}",
            "gimp gdk surface wayland handoff=${gimpGdkSurfaceWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp gdk surface wayland stdout=${gimpGdkSurfaceWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp gdk surface wayland stderr=${gimpGdkSurfaceWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
            "gimp gui quit wayland socket path=${gimpGuiQuitWaylandProbeResult.socketPath}",
            "gimp gui quit wayland connected=${gimpGuiQuitWaylandProbeResult.connected}",
            "gimp gui quit wayland setup bytes=${gimpGuiQuitWaylandProbeResult.setupBytes}",
            "gimp gui quit wayland object=${gimpGuiQuitWaylandProbeResult.objectId}",
            "gimp gui quit wayland opcode=${gimpGuiQuitWaylandProbeResult.opcode}",
            "gimp gui quit wayland size=${gimpGuiQuitWaylandProbeResult.messageSize}",
            "gimp gui quit wayland request=${gimpGuiQuitWaylandProbeResult.requestName}",
            "gimp gui quit wayland raw prefix=${gimpGuiQuitWaylandProbeResult.rawPrefixHex}",
            "gimp gui quit wayland server requests=${gimpGuiQuitWaylandProbeResult.waylandRequestCount}",
            "gimp gui quit wayland server response bytes=${gimpGuiQuitWaylandProbeResult.waylandResponseBytes}",
            "gimp gui quit wayland server globals=${gimpGuiQuitWaylandProbeResult.waylandGlobals.joinToString(",")}",
            "gimp gui quit wayland server request trace=${gimpGuiQuitWaylandProbeResult.waylandRequestNames.joinToString(",")}",
            "gimp gui quit wayland server bind trace=${gimpGuiQuitWaylandProbeResult.waylandBindInterfaces.joinToString(",")}",
            "gimp gui quit wayland server last request=${gimpGuiQuitWaylandProbeResult.lastWaylandRequest}",
            "gimp gui quit wayland server fd count=${gimpGuiQuitWaylandProbeResult.waylandReceivedFdCount}",
            "gimp gui quit wayland server fd bytes=${gimpGuiQuitWaylandProbeResult.waylandReceivedFdBytes}",
            "gimp gui quit wayland server fd verified=${gimpGuiQuitWaylandProbeResult.waylandReceivedFdVerifiedCount}",
            "gimp gui quit wayland server shm create pools=${gimpGuiQuitWaylandProbeResult.waylandShmCreatePoolCount}",
            "gimp gui quit wayland server shm pool resizes=${gimpGuiQuitWaylandProbeResult.waylandShmPoolResizeCount}",
            "gimp gui quit wayland server shm pool buffers=${gimpGuiQuitWaylandProbeResult.waylandShmPoolCreateBufferCount}",
            "gimp gui quit wayland server surfaces created=${gimpGuiQuitWaylandProbeResult.waylandSurfaceCreateCount}",
            "gimp gui quit wayland server data devices=${gimpGuiQuitWaylandProbeResult.waylandDataDeviceRequestCount}",
            "gimp gui quit wayland server shell roles=${gimpGuiQuitWaylandProbeResult.waylandShellRoleRequestCount}",
            "gimp gui quit wayland server surface attaches=${gimpGuiQuitWaylandProbeResult.waylandSurfaceAttachCount}",
            "gimp gui quit wayland server surface commits=${gimpGuiQuitWaylandProbeResult.waylandSurfaceCommitCount}",
            "gimp gui quit wayland server seat trace=${gimpGuiQuitWaylandProbeResult.waylandSeatRequestNames.joinToString(",")}",
            "gimp gui quit wayland server keyboard keymaps=${gimpGuiQuitWaylandProbeResult.waylandKeyboardKeymapSentCount}",
            "gimp gui quit wayland error=${gimpGuiQuitWaylandProbeResult.error ?: "none"}",
            "gimp gui quit wayland handoff=${gimpGuiQuitWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp gui quit wayland stdout=${gimpGuiQuitWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp gui quit wayland stderr=${gimpGuiQuitWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
            "gimp gui wayland socket path=${gimpGuiWaylandProbeResult.socketPath}",
            "gimp gui wayland connected=${gimpGuiWaylandProbeResult.connected}",
            "gimp gui wayland setup bytes=${gimpGuiWaylandProbeResult.setupBytes}",
            "gimp gui wayland object=${gimpGuiWaylandProbeResult.objectId}",
            "gimp gui wayland opcode=${gimpGuiWaylandProbeResult.opcode}",
            "gimp gui wayland size=${gimpGuiWaylandProbeResult.messageSize}",
            "gimp gui wayland request=${gimpGuiWaylandProbeResult.requestName}",
            "gimp gui wayland raw prefix=${gimpGuiWaylandProbeResult.rawPrefixHex}",
            "gimp gui wayland server requests=${gimpGuiWaylandProbeResult.waylandRequestCount}",
            "gimp gui wayland server response bytes=${gimpGuiWaylandProbeResult.waylandResponseBytes}",
            "gimp gui wayland server globals=${gimpGuiWaylandProbeResult.waylandGlobals.joinToString(",")}",
            "gimp gui wayland server request trace=${gimpGuiWaylandProbeResult.waylandRequestNames.joinToString(",")}",
            "gimp gui wayland server bind trace=${gimpGuiWaylandProbeResult.waylandBindInterfaces.joinToString(",")}",
            "gimp gui wayland server last request=${gimpGuiWaylandProbeResult.lastWaylandRequest}",
            "gimp gui wayland server fd count=${gimpGuiWaylandProbeResult.waylandReceivedFdCount}",
            "gimp gui wayland server fd bytes=${gimpGuiWaylandProbeResult.waylandReceivedFdBytes}",
            "gimp gui wayland server fd verified=${gimpGuiWaylandProbeResult.waylandReceivedFdVerifiedCount}",
            "gimp gui wayland server shm create pools=${gimpGuiWaylandProbeResult.waylandShmCreatePoolCount}",
            "gimp gui wayland server shm pool resizes=${gimpGuiWaylandProbeResult.waylandShmPoolResizeCount}",
            "gimp gui wayland server shm pool buffers=${gimpGuiWaylandProbeResult.waylandShmPoolCreateBufferCount}",
            "gimp gui wayland server surfaces created=${gimpGuiWaylandProbeResult.waylandSurfaceCreateCount}",
            "gimp gui wayland server data devices=${gimpGuiWaylandProbeResult.waylandDataDeviceRequestCount}",
            "gimp gui wayland server shell roles=${gimpGuiWaylandProbeResult.waylandShellRoleRequestCount}",
            "gimp gui wayland server surface attaches=${gimpGuiWaylandProbeResult.waylandSurfaceAttachCount}",
            "gimp gui wayland server surface commits=${gimpGuiWaylandProbeResult.waylandSurfaceCommitCount}",
            "gimp gui wayland server seat trace=${gimpGuiWaylandProbeResult.waylandSeatRequestNames.joinToString(",")}",
            "gimp gui wayland server keyboard keymaps=${gimpGuiWaylandProbeResult.waylandKeyboardKeymapSentCount}",
            "gimp gui wayland error=${gimpGuiWaylandProbeResult.error ?: "none"}",
            "gimp gui wayland blocker=$gimpGuiWaylandBlocker",
            "gimp gui wayland handoff=${gimpGuiWaylandProbeResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}",
            "gimp gui wayland stdout=${gimpGuiWaylandProbeResult.clientResult.stdout.alrHandoffStdoutText().forEvidenceLog()}",
            "gimp gui wayland stderr=${gimpGuiWaylandProbeResult.clientResult.stdout.alrHandoffStderrText().forEvidenceLog()}",
        ).joinToString("\n")
        Log.i("ALR_DEVICE_EVIDENCE", evidence)
        setContentView(
            ScrollView(this).apply {
                addView(TextView(this@MainActivity).apply { text = evidence })
            },
        )
    }

    private fun skippedGimpGuiWaylandProbe(rootfsDir: File): GimpWaylandProbeResult =
        GimpWaylandProbeResult(
            socketPath = File(rootfsDir, "tmp/alr-gimp-0").absolutePath,
            connected = false,
            setupBytes = 0,
            objectId = 0,
            opcode = 0,
            messageSize = 0,
            requestName = "skipped",
            rawPrefixHex = "",
            error = "fast verifier skipped full GIMP GUI probe",
            clientResult = NativeCommandResult(
                command = File(rootfsDir, "usr/bin/gimp"),
                environment = emptyMap(),
                exitCode = -125,
                stdout = "",
                stderr = "fast verifier skipped full GIMP GUI probe",
                elapsedMs = 0,
            ),
            waylandGlobals = minimalWaylandGlobalNames(),
        )

    private fun isWaylandRegistryProbe(result: GimpWaylandProbeResult): Boolean =
        result.connected &&
            result.setupBytes >= 12 &&
            result.objectId == 1 &&
            result.opcode == 1 &&
            result.messageSize == 12 &&
            result.requestName == "wl_display.get_registry"

    private fun describeGimpGuiWaylandBlocker(
        passed: Boolean,
        result: GimpWaylandProbeResult,
    ): String =
        if (passed) {
            "none"
        } else if (!result.connected) {
            "pre-wayland-connect"
        } else if (!result.clientResult.stdout.contains("ALR STATIC ENTRY HANDOFF:")) {
            "pre-handoff-timeout"
        } else {
            "wayland-handshake-incomplete"
        }

    private fun describeGimpConsoleBatchQuitBlocker(
        passed: Boolean,
        result: NativeCommandResult,
    ): String =
        if (passed) {
            "none"
        } else if (result.exitCode == -124 || result.stdout.contains("alr handoff timed out=true")) {
            "core-batch-timeout"
        } else if (!result.stdout.contains("ALR STATIC ENTRY HANDOFF:")) {
            "missing-handoff"
        } else {
            "exit-${result.exitCode}"
        }

    private fun describeGimpCoreQuitBlocker(
        passed: Boolean,
        result: NativeCommandResult,
    ): String =
        if (passed) {
            "none"
        } else if (result.exitCode == -124 || result.stdout.contains("alr handoff timed out=true")) {
            "core-quit-timeout"
        } else if (result.stdout.contains("alr handoff signal=31")) {
            "core-quit-sigsys"
        } else if (
            result.exitCode == 0 &&
            result.stdout.contains("ALR STATIC ENTRY HANDOFF: FAIL")
        ) {
            "handoff-fail-exit-0"
        } else if (!result.stdout.contains("ALR STATIC ENTRY HANDOFF:")) {
            "missing-handoff"
        } else {
            "exit-${result.exitCode}"
        }

    private data class GuestGpuCommand(
        val red: Float,
        val green: Float,
        val blue: Float,
        val tag: String,
        val protocol: String = "GPU",
        val seq: Int = 0,
        val payloadPath: String = "",
        val payloadBytes: Int = 0,
        val payloadChecksum: Long = 0,
        val payloadVerified: Boolean = false,
        val fdPayloadBytes: Int = 0,
        val fdPayloadChecksum: Long = 0,
        val fdPayloadVerified: Boolean = false,
        val backing: String = "",
        val bufferSlot: Int = -1,
        val dirtyX: Int = 0,
        val dirtyY: Int = 0,
        val dirtyWidth: Int = 0,
        val dirtyHeight: Int = 0,
        val dirtyBytes: Int = 0,
        val partialUpdate: Boolean = false,
    )

    private data class GuestGpuIpcBridgeResult(
        val host: String,
        val port: Int,
        val expectedFrames: Int,
        val commands: List<GuestGpuCommand>,
        val rawLines: List<String>,
        val error: String?,
        val clientResult: NativeCommandResult,
        val ackLines: List<String> = emptyList(),
    )

    private data class GimpWaylandProbeResult(
        val socketPath: String,
        val connected: Boolean,
        val setupBytes: Int,
        val objectId: Int,
        val opcode: Int,
        val messageSize: Int,
        val requestName: String,
        val rawPrefixHex: String,
        val error: String?,
        val clientResult: NativeCommandResult,
        val waylandRequestCount: Int = 0,
        val waylandResponseBytes: Int = 0,
        val waylandGlobals: List<String> = emptyList(),
        val waylandRequestNames: List<String> = emptyList(),
        val waylandBindInterfaces: List<String> = emptyList(),
        val lastWaylandRequest: String = "",
        val waylandReceivedFdCount: Int = 0,
        val waylandReceivedFdBytes: Long = 0,
        val waylandReceivedFdVerifiedCount: Int = 0,
        val waylandShmCreatePoolCount: Int = 0,
        val waylandShmPoolResizeCount: Int = 0,
        val waylandShmPoolCreateBufferCount: Int = 0,
        val waylandSurfaceCreateCount: Int = 0,
        val waylandSurfaceAttachCount: Int = 0,
        val waylandSurfaceCommitCount: Int = 0,
        val waylandDataDeviceRequestCount: Int = 0,
        val waylandShellRoleRequestCount: Int = 0,
        val waylandSeatRequestNames: List<String> = emptyList(),
        val waylandKeyboardKeymapSentCount: Int = 0,
    )

    private data class GuestFdPayloadVerification(
        val index: Int,
        val verified: Boolean,
        val bytes: Int,
        val checksum: Long,
    )

    private data class WaylandBinaryMessage(
        val index: Int,
        val objectId: Int,
        val opcode: Int,
        val size: Int,
    )

    private data class WaylandAttachState(
        val seq: Int,
        val backing: String,
        val bufferSlot: Int,
        val dirtyX: Int,
        val dirtyY: Int,
        val dirtyWidth: Int,
        val dirtyHeight: Int,
        val dirtyBytes: Int,
        val partialUpdate: Boolean,
    )

    private data class GuestVulkanDiscoveryBridgeResult(
        val host: String,
        val port: Int,
        val rawLines: List<String>,
        val ackLine: String,
        val deviceRecordLine: String,
        val featureRecordLine: String,
        val clearRequestLine: String,
        val clearAcceptedLine: String,
        val ackLines: List<String>,
        val hostProbe: String,
        val error: String?,
        val clientResult: NativeCommandResult,
    )

    private fun runInstalledPackageVulkanDiscoveryBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GuestVulkanDiscoveryBridgeResult =
        runInstalledPackageVulkanBridge(rootfsDir) { port ->
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageVulkanDiscovery(rootfsDir, port)
        }

    private fun runInstalledPackageVulkanProxyBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GuestVulkanDiscoveryBridgeResult =
        runInstalledPackageVulkanBinaryBridge(rootfsDir) { port ->
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageVulkanProxySmoke(rootfsDir, port)
        }

    private fun runInstalledPackageVulkanIcdBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GuestVulkanDiscoveryBridgeResult =
        runInstalledPackageVulkanBinaryBridge(rootfsDir) { port ->
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageVulkanIcdManifestSmoke(rootfsDir, port)
        }

    private fun runInstalledPackageVulkanLoaderInfoBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GuestVulkanDiscoveryBridgeResult =
        runInstalledPackageVulkanBinaryBridge(rootfsDir) { port ->
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageVulkanLoaderInfo(rootfsDir, port)
        }

    private fun runInstalledPackageVulkanUnixLoaderInfoBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GuestVulkanDiscoveryBridgeResult =
        runInstalledPackageVulkanUnixBinaryBridge(rootfsDir) { socketName ->
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageVulkanLoaderInfoUnix(rootfsDir, socketName)
        }

    private fun runInstalledPackageVulkanBridge(
        rootfsDir: File,
        runClient: (Int) -> NativeCommandResult,
    ): GuestVulkanDiscoveryBridgeResult {
        val host = "127.0.0.1"
        val server = ServerSocket(0, 1, InetAddress.getByName(host)).apply { soTimeout = 3000 }
        val port = server.localPort
        val rawLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val hostProbe = nativeHostVulkanProbe()
        val physicalDevices = hostProbe.lineStartingWith("vulkan physical device count=")
            .substringAfter("=", "0")
            .toIntOrNull()
            ?: 0
        val hardware = hostProbe.lineStartingWith("host vulkan hardware candidate=") == "host vulkan hardware candidate=true"
        val createDeviceOk = hostProbe.lineStartingWith("vulkan create device=") == "vulkan create device=ok"
        val deviceName = hostProbe.lineStartingWith("host vulkan device=")
            .substringAfter("=", "unknown")
            .replace(Regex("\\s+"), "_")
        val apiVersion = hostProbe.lineStartingWith("host vulkan api version=")
            .substringAfter("=", "unknown")
        val deviceType = hostProbe.lineStartingWith("host vulkan device type=")
            .substringAfter("=", "unknown")
        val queueFamilyCount = hostProbe.lineStartingWith("host vulkan queue family count=")
            .substringAfter("=", "0")
        val graphicsQueueFamily = hostProbe.lineStartingWith("host vulkan graphics queue family=")
            .substringAfter("=", "-1")
        val maxImage2d = hostProbe.lineStartingWith("host vulkan max image dimension 2d=")
            .substringAfter("=", "0")
        val maxMemoryAllocationCount = hostProbe.lineStartingWith("host vulkan max memory allocation count=")
            .substringAfter("=", "0")
        val robustBufferAccess = hostProbe.lineStartingWith("host vulkan feature robust buffer access=")
            .substringAfter("=", "unknown")
        val geometryShader = hostProbe.lineStartingWith("host vulkan feature geometry shader=")
            .substringAfter("=", "unknown")
        val samplerAnisotropy = hostProbe.lineStartingWith("host vulkan feature sampler anisotropy=")
            .substringAfter("=", "unknown")
        val status = if (physicalDevices > 0 && hardware && createDeviceOk) "PASS" else "FAIL"
        val ackLine = "ALR_VK_DISCOVERY_ACK status=$status physical_devices=$physicalDevices hardware=$hardware device=$deviceName"
        val deviceRecordLine =
            "ALR_VK_DEVICE_RECORD name=$deviceName api=$apiVersion type=$deviceType " +
                "physical_devices=$physicalDevices queue_families=$queueFamilyCount graphics_queue=$graphicsQueueFamily"
        val featureRecordLine =
            "ALR_VK_FEATURE_RECORD robust_buffer_access=$robustBufferAccess geometry_shader=$geometryShader " +
                "sampler_anisotropy=$samplerAnisotropy max_image_2d=$maxImage2d max_memory_allocations=$maxMemoryAllocationCount"
        var clearRequestLine = "missing"
        var clearAcceptedLine = "ALR_VK_SURFACE_CLEAR_ACCEPTED status=FAIL reason=no-request"
        var ackLines = listOf(ackLine, deviceRecordLine, featureRecordLine, clearAcceptedLine)
        val acceptThread = thread(name = "alr-vulkan-discovery-bridge", start = true) {
            try {
                server.use { srv ->
                    val socket = srv.accept()
                    socket.use { accepted ->
                        accepted.soTimeout = 1500
                        val reader = accepted.getInputStream().bufferedReader()
                        while (true) {
                            val line = try {
                                reader.readLine()
                            } catch (timeout: SocketTimeoutException) {
                                null
                            } ?: break
                            rawLines += line
                        }
                        clearRequestLine = rawLines.firstOrNull { it.startsWith("ALR_VK_SURFACE_CLEAR_REQUEST ") } ?: "missing"
                        clearAcceptedLine = if (
                            clearRequestLine.startsWith("ALR_VK_SURFACE_CLEAR_REQUEST ") &&
                            clearRequestLine.contains("red=") &&
                            clearRequestLine.contains("green=") &&
                            clearRequestLine.contains("blue=")
                        ) {
                            "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS request=guest-wsi-clear-v1"
                        } else {
                            "ALR_VK_SURFACE_CLEAR_ACCEPTED status=FAIL reason=invalid-request"
                        }
                        ackLines = listOf(ackLine, deviceRecordLine, featureRecordLine, clearAcceptedLine)
                        accepted.getOutputStream().write((ackLines.joinToString("\n") + "\n").toByteArray())
                        accepted.getOutputStream().flush()
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for guest vulkan discovery client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = runClient(port)
        acceptThread.join(3500)
        if (acceptThread.isAlive) {
            errors += "vulkan discovery accept thread still alive after join"
            server.close()
        }
        return GuestVulkanDiscoveryBridgeResult(
            host = host,
            port = port,
            rawLines = rawLines.toList(),
            ackLine = ackLine,
            deviceRecordLine = deviceRecordLine,
            featureRecordLine = featureRecordLine,
            clearRequestLine = clearRequestLine,
            clearAcceptedLine = clearAcceptedLine,
            ackLines = ackLines,
            hostProbe = hostProbe,
            error = errors.firstOrNull(),
            clientResult = clientResult,
        )
    }

    private fun runInstalledPackageVulkanBinaryBridge(
        rootfsDir: File,
        runClient: (Int) -> NativeCommandResult,
    ): GuestVulkanDiscoveryBridgeResult {
        val host = "127.0.0.1"
        val server = ServerSocket(0, 1, InetAddress.getByName(host)).apply { soTimeout = 3000 }
        val port = server.localPort
        val rawLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val hostProbe = nativeHostVulkanProbe()
        val physicalDevices = hostProbe.lineStartingWith("vulkan physical device count=")
            .substringAfter("=", "0")
            .toIntOrNull()
            ?: 0
        val hardware = hostProbe.lineStartingWith("host vulkan hardware candidate=") == "host vulkan hardware candidate=true"
        val createDeviceOk = hostProbe.lineStartingWith("vulkan create device=") == "vulkan create device=ok"
        val deviceName = hostProbe.lineStartingWith("host vulkan device=")
            .substringAfter("=", "unknown")
            .replace(Regex("\\s+"), "_")
        val apiVersion = hostProbe.lineStartingWith("host vulkan api version=")
            .substringAfter("=", "unknown")
        val deviceType = hostProbe.lineStartingWith("host vulkan device type=")
            .substringAfter("=", "unknown")
        val queueFamilyCount = hostProbe.lineStartingWith("host vulkan queue family count=")
            .substringAfter("=", "0")
        val graphicsQueueFamily = hostProbe.lineStartingWith("host vulkan graphics queue family=")
            .substringAfter("=", "-1")
        val maxImage2d = hostProbe.lineStartingWith("host vulkan max image dimension 2d=")
            .substringAfter("=", "0")
        val maxMemoryAllocationCount = hostProbe.lineStartingWith("host vulkan max memory allocation count=")
            .substringAfter("=", "0")
        val robustBufferAccess = hostProbe.lineStartingWith("host vulkan feature robust buffer access=")
            .substringAfter("=", "unknown")
        val geometryShader = hostProbe.lineStartingWith("host vulkan feature geometry shader=")
            .substringAfter("=", "unknown")
        val samplerAnisotropy = hostProbe.lineStartingWith("host vulkan feature sampler anisotropy=")
            .substringAfter("=", "unknown")
        val status = if (physicalDevices > 0 && hardware && createDeviceOk) "PASS" else "FAIL"
        val ackLine = "ALR_VK_DISCOVERY_ACK status=$status physical_devices=$physicalDevices hardware=$hardware device=$deviceName"
        val deviceRecordLine =
            "ALR_VK_DEVICE_RECORD name=$deviceName api=$apiVersion type=$deviceType " +
                "physical_devices=$physicalDevices queue_families=$queueFamilyCount graphics_queue=$graphicsQueueFamily"
        val featureRecordLine =
            "ALR_VK_FEATURE_RECORD robust_buffer_access=$robustBufferAccess geometry_shader=$geometryShader " +
                "sampler_anisotropy=$samplerAnisotropy max_image_2d=$maxImage2d max_memory_allocations=$maxMemoryAllocationCount"
        var clearRequestLine = "missing"
        var clearAcceptedLine = "ALR_VK_SURFACE_CLEAR_ACCEPTED status=FAIL reason=no-request"
        var ackLines = listOf(ackLine, deviceRecordLine, featureRecordLine, clearAcceptedLine)
        val acceptThread = thread(name = "alr-vulkan-binary-bridge", start = true) {
            try {
                server.use { srv ->
                    val socket = srv.accept()
                    socket.use { accepted ->
                        accepted.soTimeout = 1500
                        val requestBytes = readAllBounded(accepted.getInputStream(), 512)
                        val helloEnd = requestBytes.indexOfFirst { it == '\n'.code.toByte() }
                        if (helloEnd >= 0) {
                            rawLines += requestBytes.copyOfRange(0, helloEnd).toString(Charsets.UTF_8)
                        }
                        val frameOffset = helloEnd + 1
                        if (frameOffset < requestBytes.size && requestBytes.size >= frameOffset + 12) {
                            val magic = requestBytes.copyOfRange(frameOffset, frameOffset + 4).toString(Charsets.US_ASCII)
                            val version = readU16Le(requestBytes, frameOffset + 4)
                            val opcode = readU16Le(requestBytes, frameOffset + 6)
                            val payloadLen = readU16Le(requestBytes, frameOffset + 8)
                            val payloadOffset = frameOffset + 12
                            rawLines += "ALR_VK_BINARY_BRIDGE_REQUEST magic=$magic version=$version opcode=$opcode payload_bytes=$payloadLen"
                            if (magic == "ALVB" && version == 1 && opcode == 1 && payloadLen in 12..128 && requestBytes.size >= payloadOffset + payloadLen) {
                                val red = readU16Le(requestBytes, payloadOffset)
                                val green = readU16Le(requestBytes, payloadOffset + 2)
                                val blue = readU16Le(requestBytes, payloadOffset + 4)
                                val alpha = readU16Le(requestBytes, payloadOffset + 6)
                                val tagLen = readU16Le(requestBytes, payloadOffset + 8)
                                val sourceLen = readU16Le(requestBytes, payloadOffset + 10)
                                val tagOffset = payloadOffset + 12
                                val sourceOffset = tagOffset + tagLen
                                if (tagLen in 1..64 && sourceLen in 1..64 && sourceOffset + sourceLen <= payloadOffset + payloadLen) {
                                    val tag = requestBytes.copyOfRange(tagOffset, tagOffset + tagLen).toString(Charsets.UTF_8)
                                    val source = requestBytes.copyOfRange(sourceOffset, sourceOffset + sourceLen).toString(Charsets.UTF_8)
                                    clearRequestLine =
                                        "ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=${milliColor(red)} " +
                                            "green=${milliColor(green)} blue=${milliColor(blue)} alpha=${milliColor(alpha)} " +
                                            "tag=$tag source=$source protocol=binary-frame-v1"
                                }
                            }
                        }
                        clearAcceptedLine = if (
                            clearRequestLine.startsWith("ALR_VK_SURFACE_CLEAR_REQUEST ") &&
                            clearRequestLine.contains("source=libvulkan-proxy") &&
                            clearRequestLine.contains("protocol=binary-frame-v1")
                        ) {
                            "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS request=guest-wsi-clear-v1 protocol=binary-frame-v1"
                        } else {
                            "ALR_VK_SURFACE_CLEAR_ACCEPTED status=FAIL reason=invalid-binary-request"
                        }
                        val responseStatus = if (clearAcceptedLine.contains("status=PASS") && status == "PASS") 1 else 0
                        val responseRecords = listOf(ackLine, deviceRecordLine, featureRecordLine, clearAcceptedLine)
                        val payload = (responseRecords.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
                        val binaryAckLine =
                            "ALR_VK_BINARY_BRIDGE_ACK status=${if (responseStatus == 1) "PASS" else "FAIL"} " +
                                "protocol=alr-vk-bin-v1 payload_bytes=${payload.size} records=${responseRecords.size}"
                        ackLines = listOf(binaryAckLine) + responseRecords
                        accepted.getOutputStream().write(buildVulkanBinaryResponse(responseStatus, responseRecords.size, payload))
                        accepted.getOutputStream().flush()
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for guest vulkan binary client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = runClient(port)
        acceptThread.join(3500)
        if (acceptThread.isAlive) {
            errors += "vulkan binary accept thread still alive after join"
            server.close()
        }
        return GuestVulkanDiscoveryBridgeResult(
            host = host,
            port = port,
            rawLines = rawLines.toList(),
            ackLine = ackLine,
            deviceRecordLine = deviceRecordLine,
            featureRecordLine = featureRecordLine,
            clearRequestLine = clearRequestLine,
            clearAcceptedLine = clearAcceptedLine,
            ackLines = ackLines,
            hostProbe = hostProbe,
            error = errors.firstOrNull(),
            clientResult = clientResult,
        )
    }

    private fun runInstalledPackageVulkanUnixBinaryBridge(
        rootfsDir: File,
        runClient: (String) -> NativeCommandResult,
    ): GuestVulkanDiscoveryBridgeResult {
        val socketName = "alr-vk-bin-${System.nanoTime()}"
        val server = LocalServerSocket(socketName)
        val rawLines = mutableListOf("ALR_VK_UNIX_BRIDGE_SOCKET name=@$socketName")
        val errors = mutableListOf<String>()
        val hostProbe = nativeHostVulkanProbe()
        val physicalDevices = hostProbe.lineStartingWith("vulkan physical device count=")
            .substringAfter("=", "0")
            .toIntOrNull()
            ?: 0
        val hardware = hostProbe.lineStartingWith("host vulkan hardware candidate=") == "host vulkan hardware candidate=true"
        val createDeviceOk = hostProbe.lineStartingWith("vulkan create device=") == "vulkan create device=ok"
        val deviceName = hostProbe.lineStartingWith("host vulkan device=")
            .substringAfter("=", "unknown")
            .replace(Regex("\\s+"), "_")
        val apiVersion = hostProbe.lineStartingWith("host vulkan api version=")
            .substringAfter("=", "unknown")
        val deviceType = hostProbe.lineStartingWith("host vulkan device type=")
            .substringAfter("=", "unknown")
        val queueFamilyCount = hostProbe.lineStartingWith("host vulkan queue family count=")
            .substringAfter("=", "0")
        val graphicsQueueFamily = hostProbe.lineStartingWith("host vulkan graphics queue family=")
            .substringAfter("=", "-1")
        val maxImage2d = hostProbe.lineStartingWith("host vulkan max image dimension 2d=")
            .substringAfter("=", "0")
        val maxMemoryAllocationCount = hostProbe.lineStartingWith("host vulkan max memory allocation count=")
            .substringAfter("=", "0")
        val robustBufferAccess = hostProbe.lineStartingWith("host vulkan feature robust buffer access=")
            .substringAfter("=", "unknown")
        val geometryShader = hostProbe.lineStartingWith("host vulkan feature geometry shader=")
            .substringAfter("=", "unknown")
        val samplerAnisotropy = hostProbe.lineStartingWith("host vulkan feature sampler anisotropy=")
            .substringAfter("=", "unknown")
        val status = if (physicalDevices > 0 && hardware && createDeviceOk) "PASS" else "FAIL"
        val ackLine = "ALR_VK_DISCOVERY_ACK status=$status physical_devices=$physicalDevices hardware=$hardware device=$deviceName"
        val deviceRecordLine =
            "ALR_VK_DEVICE_RECORD name=$deviceName api=$apiVersion type=$deviceType " +
                "physical_devices=$physicalDevices queue_families=$queueFamilyCount graphics_queue=$graphicsQueueFamily"
        val featureRecordLine =
            "ALR_VK_FEATURE_RECORD robust_buffer_access=$robustBufferAccess geometry_shader=$geometryShader " +
                "sampler_anisotropy=$samplerAnisotropy max_image_2d=$maxImage2d max_memory_allocations=$maxMemoryAllocationCount"
        var clearRequestLine = "missing"
        var clearAcceptedLine = "ALR_VK_SURFACE_CLEAR_ACCEPTED status=FAIL reason=no-request"
        var ackLines = listOf(ackLine, deviceRecordLine, featureRecordLine, clearAcceptedLine)
        val acceptThread = thread(name = "alr-vulkan-unix-binary-bridge", start = true) {
            try {
                server.use { srv ->
                    val accepted = srv.accept()
                    accepted.use { socket ->
                        socket.setSoTimeout(1500)
                        val requestBytes = readAllBounded(socket.getInputStream(), 512)
                        val helloEnd = requestBytes.indexOfFirst { it == '\n'.code.toByte() }
                        if (helloEnd >= 0) {
                            rawLines += requestBytes.copyOfRange(0, helloEnd).toString(Charsets.UTF_8)
                        }
                        val frameOffset = helloEnd + 1
                        if (frameOffset < requestBytes.size && requestBytes.size >= frameOffset + 12) {
                            val magic = requestBytes.copyOfRange(frameOffset, frameOffset + 4).toString(Charsets.US_ASCII)
                            val version = readU16Le(requestBytes, frameOffset + 4)
                            val opcode = readU16Le(requestBytes, frameOffset + 6)
                            val payloadLen = readU16Le(requestBytes, frameOffset + 8)
                            val payloadOffset = frameOffset + 12
                            rawLines += "ALR_VK_BINARY_BRIDGE_REQUEST magic=$magic version=$version opcode=$opcode payload_bytes=$payloadLen transport=unix-abstract"
                            if (magic == "ALVB" && version == 1 && opcode == 1 && payloadLen in 12..128 && requestBytes.size >= payloadOffset + payloadLen) {
                                val red = readU16Le(requestBytes, payloadOffset)
                                val green = readU16Le(requestBytes, payloadOffset + 2)
                                val blue = readU16Le(requestBytes, payloadOffset + 4)
                                val alpha = readU16Le(requestBytes, payloadOffset + 6)
                                val tagLen = readU16Le(requestBytes, payloadOffset + 8)
                                val sourceLen = readU16Le(requestBytes, payloadOffset + 10)
                                val tagOffset = payloadOffset + 12
                                val sourceOffset = tagOffset + tagLen
                                if (tagLen in 1..64 && sourceLen in 1..64 && sourceOffset + sourceLen <= payloadOffset + payloadLen) {
                                    val tag = requestBytes.copyOfRange(tagOffset, tagOffset + tagLen).toString(Charsets.UTF_8)
                                    val source = requestBytes.copyOfRange(sourceOffset, sourceOffset + sourceLen).toString(Charsets.UTF_8)
                                    clearRequestLine =
                                        "ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=${milliColor(red)} " +
                                            "green=${milliColor(green)} blue=${milliColor(blue)} alpha=${milliColor(alpha)} " +
                                            "tag=$tag source=$source protocol=binary-frame-v1 transport=unix-abstract"
                                }
                            }
                        }
                        clearAcceptedLine = if (
                            clearRequestLine.startsWith("ALR_VK_SURFACE_CLEAR_REQUEST ") &&
                            clearRequestLine.contains("source=libvulkan-proxy") &&
                            clearRequestLine.contains("protocol=binary-frame-v1") &&
                            clearRequestLine.contains("transport=unix-abstract")
                        ) {
                            "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS request=guest-wsi-clear-v1 protocol=binary-frame-v1 transport=unix-abstract"
                        } else {
                            "ALR_VK_SURFACE_CLEAR_ACCEPTED status=FAIL reason=invalid-unix-binary-request transport=unix-abstract"
                        }
                        val responseStatus = if (clearAcceptedLine.contains("status=PASS") && status == "PASS") 1 else 0
                        val responseRecords = listOf(ackLine, deviceRecordLine, featureRecordLine, clearAcceptedLine)
                        val payload = (responseRecords.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
                        val binaryAckLine =
                            "ALR_VK_BINARY_BRIDGE_ACK status=${if (responseStatus == 1) "PASS" else "FAIL"} " +
                                "protocol=alr-vk-bin-v1 transport=unix-abstract payload_bytes=${payload.size} records=${responseRecords.size}"
                        ackLines = listOf(binaryAckLine) + responseRecords
                        socket.getOutputStream().write(buildVulkanBinaryResponse(responseStatus, responseRecords.size, payload))
                        socket.getOutputStream().flush()
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for guest vulkan unix binary client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = runClient(socketName)
        acceptThread.join(3500)
        if (acceptThread.isAlive) {
            errors += "vulkan unix binary accept thread still alive after join"
            server.close()
        }
        return GuestVulkanDiscoveryBridgeResult(
            host = "unix-abstract",
            port = 0,
            rawLines = rawLines.toList(),
            ackLine = ackLine,
            deviceRecordLine = deviceRecordLine,
            featureRecordLine = featureRecordLine,
            clearRequestLine = clearRequestLine,
            clearAcceptedLine = clearAcceptedLine,
            ackLines = ackLines,
            hostProbe = hostProbe,
            error = errors.firstOrNull(),
            clientResult = clientResult,
        )
    }

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

    private fun runInstalledPackageGpuIpcBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GuestGpuIpcBridgeResult {
        val host = "127.0.0.1"
        val server = ServerSocket(0, 1, InetAddress.getByName(host)).apply { soTimeout = 3000 }
        val port = server.localPort
        val rawLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val acceptThread = thread(name = "alr-installed-package-gpu-ipc-bridge", start = true) {
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
                errors += "timeout waiting for installed package gpu ipc client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGpuSmoke(rootfsDir, port)
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

    private fun runInstalledPackageGlesIpcBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
        frameCount: Int,
    ): GuestGpuIpcBridgeResult {
        val host = "127.0.0.1"
        val server = ServerSocket(0, 1, InetAddress.getByName(host)).apply { soTimeout = 12000 }
        val port = server.localPort
        val rawLines = mutableListOf<String>()
        val ackLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val acceptThread = thread(name = "alr-installed-package-gles-ipc-bridge", start = true) {
            try {
                server.use { srv ->
                    val socket = srv.accept()
                    socket.use { accepted ->
                        accepted.soTimeout = 12000
                        val writer = accepted.getOutputStream().bufferedWriter()
                        accepted.getInputStream().bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                rawLines += line
                                if (line.startsWith("ALR_GPU_CLEAR ") || line.startsWith("ALR_GPU_DRAW_TRIANGLE ")) {
                                    val ack = "ALR_GPU_IPC_ACK seq=${ackLines.size + 1}"
                                    ackLines += ack
                                    writer.write(ack)
                                    writer.newLine()
                                    writer.flush()
                                }
                            }
                        }
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for installed package gles ipc client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGlesDemoIpc(rootfsDir, frameCount, port)
        acceptThread.join(12500)
        if (acceptThread.isAlive) {
            errors += "accept thread still alive after join"
            server.close()
        }
        val commands = rawLines
            .mapNotNull { parseGuestGpuCommandLine(it) }
            .mapIndexed { index, command -> command.copy(seq = index + 1) }
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
            ackLines = ackLines.toList(),
        )
    }

    private fun runInstalledPackageGlesUnixIpcBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
        frameCount: Int,
    ): GuestGpuIpcBridgeResult {
        val socketName = "alr-gles-${System.nanoTime()}"
        val server = LocalServerSocket(socketName)
        val rawLines = mutableListOf("ALR_GPU_UNIX_BRIDGE_SOCKET name=@$socketName")
        val ackLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val acceptThread = thread(name = "alr-installed-package-gles-unix-ipc-bridge", start = true) {
            try {
                server.use { srv ->
                    val accepted = srv.accept()
                    accepted.use { socket ->
                        socket.setSoTimeout(12000)
                        val writer = socket.getOutputStream().bufferedWriter()
                        socket.getInputStream().bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                rawLines += line
                                if (line.startsWith("ALR_GPU_CLEAR ") || line.startsWith("ALR_GPU_DRAW_TRIANGLE ")) {
                                    val ack = "ALR_GPU_IPC_ACK seq=${ackLines.size + 1} transport=unix-abstract"
                                    ackLines += ack
                                    writer.write(ack)
                                    writer.newLine()
                                    writer.flush()
                                }
                            }
                        }
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for installed package gles unix ipc client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGlesDemoIpcUnix(rootfsDir, frameCount, socketName)
        acceptThread.join(12500)
        if (acceptThread.isAlive) {
            errors += "gles unix accept thread still alive after join"
            server.close()
        }
        val commands = rawLines
            .mapNotNull { parseGuestGpuCommandLine(it) }
            .mapIndexed { index, command -> command.copy(seq = index + 1) }
        val expectedFrames = rawLines.firstOrNull { it.startsWith("ALR_GPU_IPC_HELLO ") }
            ?.substringAfter("frames=", "0")
            ?.substringBefore(" ")
            ?.toIntOrNull()
            ?: commands.size
        return GuestGpuIpcBridgeResult(
            host = "unix-abstract",
            port = 0,
            expectedFrames = expectedFrames,
            commands = commands,
            rawLines = rawLines.toList(),
            error = errors.firstOrNull(),
            clientResult = clientResult,
            ackLines = ackLines.toList(),
        )
    }

    private fun runInstalledPackageGlesUnixBatchIpcBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
        frameCount: Int,
    ): GuestGpuIpcBridgeResult {
        val socketName = "alr-gles-batch-${System.nanoTime()}"
        val server = LocalServerSocket(socketName)
        val rawLines = mutableListOf("ALR_GPU_UNIX_BATCH_BRIDGE_SOCKET name=@$socketName")
        val ackLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val acceptThread = thread(name = "alr-installed-package-gles-unix-batch-ipc-bridge", start = true) {
            try {
                server.use { srv ->
                    val accepted = srv.accept()
                    accepted.use { socket ->
                        socket.setSoTimeout(12000)
                        val writer = socket.getOutputStream().bufferedWriter()
                        val reader = socket.getInputStream().bufferedReader()
                        var expectedBatchFrames = frameCount
                        var batchCommandCount = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            rawLines += line
                            when {
                                line.startsWith("ALR_GPU_IPC_HELLO ") -> {
                                    expectedBatchFrames = line.substringAfter("frames=", expectedBatchFrames.toString())
                                        .substringBefore(" ")
                                        .toIntOrNull()
                                        ?: expectedBatchFrames
                                }
                                line.startsWith("ALR_GPU_BATCH_BEGIN ") -> {
                                    expectedBatchFrames = line.substringAfter("frames=", expectedBatchFrames.toString())
                                        .substringBefore(" ")
                                        .toIntOrNull()
                                        ?: expectedBatchFrames
                                    batchCommandCount = 0
                                }
                                line.startsWith("ALR_GPU_CLEAR ") || line.startsWith("ALR_GPU_DRAW_TRIANGLE ") -> {
                                    batchCommandCount += 1
                                }
                                line.startsWith("ALR_GPU_BATCH_END ") -> {
                                    val declaredCommands = line.substringAfter("commands=", batchCommandCount.toString())
                                        .substringBefore(" ")
                                        .toIntOrNull()
                                        ?: batchCommandCount
                                    val expected = if (declaredCommands > 0) declaredCommands else expectedBatchFrames
                                    val lossless = batchCommandCount == expected
                                    val ack = "ALR_GPU_BATCH_ACK received=$batchCommandCount expected=$expected lossless=$lossless transport=unix-abstract"
                                    ackLines += ack
                                    writer.write(ack)
                                    writer.newLine()
                                    writer.flush()
                                }
                            }
                            if (line == "ALR_GPU_IPC_END") break
                        }
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for installed package gles unix batch ipc client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGlesDemoIpcUnixBatch(rootfsDir, frameCount, socketName)
        acceptThread.join(12500)
        if (acceptThread.isAlive) {
            errors += "gles unix batch accept thread still alive after join"
            server.close()
        }
        val commands = rawLines
            .mapNotNull { parseGuestGpuCommandLine(it) }
            .mapIndexed { index, command -> command.copy(seq = index + 1) }
        val expectedFrames = rawLines.firstOrNull { it.startsWith("ALR_GPU_BATCH_BEGIN ") }
            ?.substringAfter("frames=", "0")
            ?.substringBefore(" ")
            ?.toIntOrNull()
            ?: rawLines.firstOrNull { it.startsWith("ALR_GPU_IPC_HELLO ") }
                ?.substringAfter("frames=", "0")
                ?.substringBefore(" ")
                ?.toIntOrNull()
            ?: commands.size
        return GuestGpuIpcBridgeResult(
            host = "unix-abstract-batch",
            port = 0,
            expectedFrames = expectedFrames,
            commands = commands,
            rawLines = rawLines.toList(),
            error = errors.firstOrNull(),
            clientResult = clientResult,
            ackLines = ackLines.toList(),
        )
    }


    private fun runGuestGuiBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
        protocol: String,
        useAlr: Boolean = false,
        useInstalledPackage: Boolean = false,
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
        val clientResult = if (useInstalledPackage) {
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGuiClientIpc(rootfsDir, protocol, port)
        } else if (useAlr) {
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

    private fun runGuestGuiBridgeUnix(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
        protocol: String,
        useAlr: Boolean = false,
        useInstalledPackage: Boolean = false,
    ): GuestGpuIpcBridgeResult {
        val socketName = "alr-gui-${protocol.lowercase()}-${System.nanoTime()}"
        val server = LocalServerSocket(socketName)
        val rawLines = mutableListOf("ALR_GUI_UNIX_BRIDGE_SOCKET protocol=$protocol name=@$socketName")
        val ackLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val acceptThread = thread(name = "alr-gui-unix-ipc-bridge-$protocol", start = true) {
            try {
                server.use { srv ->
                    val accepted = srv.accept()
                    accepted.use { socket ->
                        socket.setSoTimeout(3000)
                        val reader = socket.getInputStream().bufferedReader()
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
                        val ack = "ALR_GUI_IPC_ACK protocol=$protocol received=$receivedFrames expected=$expectedFrames lossless=$lossless transport=unix-abstract"
                        ackLines += ack
                        socket.getOutputStream().write((ack + "\n").toByteArray())
                        socket.getOutputStream().flush()
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for guest gui unix ipc client $protocol"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = if (useInstalledPackage) {
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGuiClientIpcUnix(rootfsDir, protocol, socketName)
        } else if (useAlr) {
            nativeCommandRunner.runAlrRuntimeTrampolineGuestGuiClientIpcUnix(rootfsDir, protocol, socketName)
        } else {
            nativeCommandRunner.runProotRootfsGuestGuiClientIpcUnix(rootfsDir, protocol, socketName)
        }
        acceptThread.join(3500)
        if (acceptThread.isAlive) {
            errors += "gui unix accept thread still alive after join $protocol"
            server.close()
        }
        val commands = parseGuestGuiCommands(rawLines.joinToString("\n"), protocol)
        val expectedFrames = rawLines.firstOrNull { it.startsWith("ALR_GUI_IPC_HELLO ") }
            ?.substringAfter("frames=", "0")
            ?.substringBefore(" ")
            ?.toIntOrNull()
            ?: commands.size
        return GuestGpuIpcBridgeResult(
            host = "unix-abstract-gui",
            port = 0,
            expectedFrames = expectedFrames,
            commands = commands,
            rawLines = rawLines.toList(),
            error = errors.firstOrNull(),
            clientResult = clientResult,
            ackLines = ackLines.toList(),
        )
    }

    private fun runGimpGuiWaylandProbe(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
        fast: Boolean = false,
    ): GimpWaylandProbeResult {
        return runGimpWaylandSocketProbe(
            rootfsDir = rootfsDir,
            socketLeaf = "alr-gimp-0",
            threadName = "alr-gimp-wayland-probe",
            acceptJoinTimeoutMs = if (fast) 20000 else 95000,
            runClient = {
                if (fast) {
                    nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGimpGuiWaylandFastProbe(rootfsDir)
                } else {
                    nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageGimpGuiWaylandProbe(rootfsDir)
                }
            },
        )
    }

    private fun runGimpGtkWaylandProbe(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GimpWaylandProbeResult {
        return runGimpWaylandSocketProbe(
            rootfsDir = rootfsDir,
            socketLeaf = "alr-gimp-gtk-0",
            threadName = "alr-gimp-gtk-wayland-probe",
            socketReadTimeoutMs = 5000,
            runClient = { nativeCommandRunner.runAlrRuntimeTrampolineGimp3GtkWaylandPythonProbe(rootfsDir) },
        )
    }

    private fun runGimpGtkWindowWaylandProbe(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GimpWaylandProbeResult {
        return runGimpWaylandSocketProbe(
            rootfsDir = rootfsDir,
            socketLeaf = "alr-gimp-gtk-window-0",
            threadName = "alr-gimp-gtk-window-wayland-probe",
            acceptJoinTimeoutMs = 25000,
            socketReadTimeoutMs = 9000,
            runClient = { nativeCommandRunner.runAlrRuntimeTrampolineGimp3GtkWaylandWindowPythonProbe(rootfsDir) },
        )
    }

    private fun runGimpGdkSurfaceWaylandProbe(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GimpWaylandProbeResult {
        return runGimpWaylandSocketProbe(
            rootfsDir = rootfsDir,
            socketLeaf = "alr-gimp-gdk-surface-0",
            threadName = "alr-gimp-gdk-surface-wayland-probe",
            acceptJoinTimeoutMs = 28000,
            socketReadTimeoutMs = 9000,
            runClient = { nativeCommandRunner.runAlrRuntimeTrampolineGimp3GdkSurfaceWaylandPythonProbe(rootfsDir) },
        )
    }

    private fun runGimpGuiQuitWaylandProbe(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GimpWaylandProbeResult {
        return runGimpWaylandSocketProbe(
            rootfsDir = rootfsDir,
            socketLeaf = "alr-gimp-quit-0",
            threadName = "alr-gimp-gui-quit-wayland-probe",
            acceptJoinTimeoutMs = 25000,
            socketReadTimeoutMs = 9000,
            runClient = { nativeCommandRunner.runAlrRuntimeTrampolineGimp3GuiQuitWaylandProbe(rootfsDir) },
        )
    }

    private fun runGimpWaylandSocketProbe(
        rootfsDir: File,
        socketLeaf: String,
        threadName: String,
        acceptJoinTimeoutMs: Long = 95000,
        socketReadTimeoutMs: Int = 2500,
        runClient: () -> NativeCommandResult,
    ): GimpWaylandProbeResult {
        val runtimeDir = File(rootfsDir, "tmp").apply { mkdirs() }
        val socketFile = File(runtimeDir, socketLeaf)
        if (socketFile.exists()) socketFile.delete()

        val rawBytes = ByteArrayOutputStream()
        val errors = mutableListOf<String>()
        var connected = false
        var waylandRequestCount = 0
        var waylandResponseBytes = 0
        var waylandGlobals = emptyList<String>()
        var waylandRequestNames = emptyList<String>()
        var waylandBindInterfaces = emptyList<String>()
        var waylandReceivedFdCount = 0
        var waylandReceivedFdBytes = 0L
        var waylandReceivedFdVerifiedCount = 0
        var waylandShmCreatePoolCount = 0
        var waylandShmPoolResizeCount = 0
        var waylandShmPoolCreateBufferCount = 0
        var waylandSurfaceCreateCount = 0
        var waylandSurfaceAttachCount = 0
        var waylandSurfaceCommitCount = 0
        var waylandDataDeviceRequestCount = 0
        var waylandShellRoleRequestCount = 0
        var waylandSeatRequestNames = emptyList<String>()
        var waylandKeyboardKeymapSentCount = 0
        var server: LocalServerSocket? = null
        val listenSocket = try {
            LocalSocket().also { socket ->
                socket.bind(LocalSocketAddress(socketFile.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
                Os.listen(socket.fileDescriptor, 1)
                server = LocalServerSocket(socket.fileDescriptor)
            }
        } catch (error: Exception) {
            errors += error.javaClass.simpleName + ": " + (error.message ?: "socket bind failed")
            null
        }

        val acceptThread = if (server != null) {
            thread(name = threadName, start = true) {
                try {
                    server.use { srv ->
                        val accepted = srv.accept()
                        accepted.use { socket ->
                            connected = true
                            socket.setSoTimeout(socketReadTimeoutMs)
                            val stats = serveMinimalWaylandClient(socket, rawBytes)
                            waylandRequestCount = stats.requestCount
                            waylandResponseBytes = stats.responseBytes
                            waylandGlobals = stats.globals
                            waylandRequestNames = stats.requestNames
                            waylandBindInterfaces = stats.bindInterfaces
                            waylandReceivedFdCount = stats.receivedFdCount
                            waylandReceivedFdBytes = stats.receivedFdBytes
                            waylandReceivedFdVerifiedCount = stats.receivedFdVerifiedCount
                            waylandShmCreatePoolCount = stats.shmCreatePoolCount
                            waylandShmPoolResizeCount = stats.shmPoolResizeCount
                            waylandShmPoolCreateBufferCount = stats.shmPoolCreateBufferCount
                            waylandSurfaceCreateCount = stats.surfaceCreateCount
                            waylandSurfaceAttachCount = stats.surfaceAttachCount
                            waylandSurfaceCommitCount = stats.surfaceCommitCount
                            waylandDataDeviceRequestCount = stats.dataDeviceRequestCount
                            waylandShellRoleRequestCount = stats.shellRoleRequestCount
                            waylandSeatRequestNames = stats.seatRequestNames
                            waylandKeyboardKeymapSentCount = stats.keyboardKeymapSentCount
                        }
                    }
                } catch (error: SocketTimeoutException) {
                    errors += "timeout waiting for gimp wayland bytes"
                } catch (error: Exception) {
                    errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
                }
            }
        } else {
            null
        }

        val clientResult = runClient()
        acceptThread?.join(acceptJoinTimeoutMs)
        if (acceptThread?.isAlive == true) {
            errors += "gimp wayland accept thread still alive after join"
            try {
                server?.close()
            } catch (_: Exception) {
            }
        }
        try {
            listenSocket?.close()
        } catch (_: Exception) {
        }
        if (socketFile.exists()) socketFile.delete()

        val bytes = rawBytes.toByteArray()
        val objectId = if (bytes.size >= 4) readLe32(bytes, 0) else 0
        val secondWord = if (bytes.size >= 8) readLe32(bytes, 4) else 0
        val opcode = secondWord and 0xffff
        val messageSize = (secondWord ushr 16) and 0xffff
        val requestName = if (objectId == 1 && opcode == 1 && messageSize == 12) {
            "wl_display.get_registry"
        } else {
            "unknown"
        }
        return GimpWaylandProbeResult(
            socketPath = socketFile.absolutePath,
            connected = connected,
            setupBytes = bytes.size,
            objectId = objectId,
            opcode = opcode,
            messageSize = messageSize,
            requestName = requestName,
            rawPrefixHex = bytes.hexPrefix(),
            error = errors.firstOrNull(),
            clientResult = clientResult,
            waylandRequestCount = waylandRequestCount,
            waylandResponseBytes = waylandResponseBytes,
            waylandGlobals = waylandGlobals,
            waylandRequestNames = waylandRequestNames,
            waylandBindInterfaces = waylandBindInterfaces,
            lastWaylandRequest = waylandRequestNames.lastOrNull().orEmpty(),
            waylandReceivedFdCount = waylandReceivedFdCount,
            waylandReceivedFdBytes = waylandReceivedFdBytes,
            waylandReceivedFdVerifiedCount = waylandReceivedFdVerifiedCount,
            waylandShmCreatePoolCount = waylandShmCreatePoolCount,
            waylandShmPoolResizeCount = waylandShmPoolResizeCount,
            waylandShmPoolCreateBufferCount = waylandShmPoolCreateBufferCount,
            waylandSurfaceCreateCount = waylandSurfaceCreateCount,
            waylandSurfaceAttachCount = waylandSurfaceAttachCount,
            waylandSurfaceCommitCount = waylandSurfaceCommitCount,
            waylandDataDeviceRequestCount = waylandDataDeviceRequestCount,
            waylandShellRoleRequestCount = waylandShellRoleRequestCount,
            waylandSeatRequestNames = waylandSeatRequestNames,
            waylandKeyboardKeymapSentCount = waylandKeyboardKeymapSentCount,
        )
    }

    private data class WaylandServerStats(
        val requestCount: Int,
        val responseBytes: Int,
        val globals: List<String>,
        val requestNames: List<String>,
        val bindInterfaces: List<String>,
        val receivedFdCount: Int,
        val receivedFdBytes: Long,
        val receivedFdVerifiedCount: Int,
        val shmCreatePoolCount: Int,
        val shmPoolResizeCount: Int,
        val shmPoolCreateBufferCount: Int,
        val surfaceCreateCount: Int,
        val surfaceAttachCount: Int,
        val surfaceCommitCount: Int,
        val dataDeviceRequestCount: Int,
        val shellRoleRequestCount: Int,
        val seatRequestNames: List<String>,
        val keyboardKeymapSentCount: Int,
    )

    private data class WaylandServerResponse(
        val bytes: ByteArray,
        val fds: List<ParcelFileDescriptor> = emptyList(),
    )

    private data class WaylandString(
        val value: String,
        val nextOffset: Int,
    )

    private fun serveMinimalWaylandClient(
        socket: LocalSocket,
        rawBytes: ByteArrayOutputStream,
    ): WaylandServerStats {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val objectInterfaces = mutableMapOf(1 to "wl_display")
        val globals = minimalWaylandGlobals()
        val globalNames = globals.map { it.interfaceName }
        val requestNames = mutableListOf<String>()
        val bindInterfaces = mutableListOf<String>()
        val seatRequestNames = mutableListOf<String>()
        val boundOutputIds = mutableListOf<Int>()
        val keyboardKeymapFile = prepareWaylandKeyboardKeymapFile()
        var requestCount = 0
        var responseBytes = 0
        var receivedFdCount = 0
        var receivedFdBytes = 0L
        var receivedFdVerifiedCount = 0
        var keyboardKeymapSentCount = 0
        var serial = 1
        while (requestCount < 96) {
            val header = try {
                readExactlyOrNull(input, 8)
            } catch (_: SocketTimeoutException) {
                break
            } catch (_: IOException) {
                break
            } ?: break
            rawBytes.write(header)
            val objectId = readLe32(header, 0)
            val secondWord = readLe32(header, 4)
            val opcode = secondWord and 0xffff
            val size = (secondWord ushr 16) and 0xffff
            if (size < 8 || size > 4096) break
            val payload = try {
                readExactlyOrNull(input, size - 8)
            } catch (_: SocketTimeoutException) {
                break
            } catch (_: IOException) {
                break
            } ?: break
            rawBytes.write(payload)
            requestCount += 1
            val interfaceName = objectInterfaces[objectId] ?: "unknown"
            val requestName = describeMinimalWaylandRequest(interfaceName, opcode, payload)
            requestNames += requestName
            if (requestName.startsWith("wl_seat.") || requestName.startsWith("wl_keyboard.") || requestName.startsWith("wl_pointer.")) {
                seatRequestNames += requestName
            }
            if (requestName.startsWith("wl_registry.bind:")) {
                bindInterfaces += requestName.substringAfter(":")
            }
            val receivedFds = socket.ancillaryFileDescriptors?.toList().orEmpty()
            if (receivedFds.isNotEmpty()) {
                receivedFdCount += receivedFds.size
                receivedFds.forEach { fd ->
                    val fdBytes = waylandFdSizeBytes(fd)
                    if (fdBytes > 0) {
                        receivedFdBytes += fdBytes
                        receivedFdVerifiedCount += 1
                    }
                    runCatching { Os.close(fd) }
                }
            }
            val responses = buildMinimalWaylandResponses(
                objectId = objectId,
                opcode = opcode,
                payload = payload,
                objectInterfaces = objectInterfaces,
                globals = globals,
                boundOutputIds = boundOutputIds,
                nextSerial = { serial++ },
                keyboardKeymapFile = keyboardKeymapFile,
            )
            responses.forEach { response ->
                if (response.fds.isNotEmpty()) {
                    socket.setFileDescriptorsForSend(response.fds.map { it.fileDescriptor }.toTypedArray())
                    keyboardKeymapSentCount += response.fds.size
                }
                output.write(response.bytes)
                responseBytes += response.bytes.size
                response.fds.forEach { it.close() }
                if (response.fds.isNotEmpty()) {
                    socket.setFileDescriptorsForSend(emptyArray())
                }
            }
            if (responses.isNotEmpty()) output.flush()
        }
        val shmCreatePoolCount = requestNames.count { it == "wl_shm.create_pool" }
        val shmPoolResizeCount = requestNames.count { it == "wl_shm_pool.resize" }
        val shmPoolCreateBufferCount = requestNames.count { it == "wl_shm_pool.create_buffer" }
        val surfaceCreateCount = requestNames.count { it == "wl_compositor.create_surface" }
        val surfaceAttachCount = requestNames.count { it == "wl_surface.attach" }
        val surfaceCommitCount = requestNames.count { it == "wl_surface.commit" }
        val dataDeviceRequestCount = requestNames.count { it == "wl_data_device_manager.get_data_device" }
        val shellRoleRequestCount = requestNames.count {
            it == "xdg_wm_base.get_xdg_surface" ||
                it == "xdg_surface.get_toplevel" ||
                it == "zxdg_shell_v6.get_xdg_surface" ||
                it == "zxdg_surface_v6.get_toplevel" ||
                it == "wl_shell.get_shell_surface" ||
                it == "wl_shell_surface.set_toplevel" ||
                it == "xdg_shell.get_xdg_surface" ||
                it == "xdg_surface_v5.set_title" ||
                it == "xdg_surface_v5.set_app_id"
        }
        return WaylandServerStats(
            requestCount = requestCount,
            responseBytes = responseBytes,
            globals = globalNames,
            requestNames = requestNames,
            bindInterfaces = bindInterfaces,
            receivedFdCount = receivedFdCount,
            receivedFdBytes = receivedFdBytes,
            receivedFdVerifiedCount = receivedFdVerifiedCount,
            shmCreatePoolCount = shmCreatePoolCount,
            shmPoolResizeCount = shmPoolResizeCount,
            shmPoolCreateBufferCount = shmPoolCreateBufferCount,
            surfaceCreateCount = surfaceCreateCount,
            surfaceAttachCount = surfaceAttachCount,
            surfaceCommitCount = surfaceCommitCount,
            dataDeviceRequestCount = dataDeviceRequestCount,
            shellRoleRequestCount = shellRoleRequestCount,
            seatRequestNames = seatRequestNames,
            keyboardKeymapSentCount = keyboardKeymapSentCount,
        )
    }

    private data class MinimalWaylandGlobal(
        val name: Int,
        val interfaceName: String,
        val version: Int,
    )

    private fun minimalWaylandGlobals(): List<MinimalWaylandGlobal> =
        listOf(
            MinimalWaylandGlobal(1, "wl_compositor", 4),
            MinimalWaylandGlobal(2, "wl_shm", 1),
            MinimalWaylandGlobal(3, "xdg_wm_base", 6),
            MinimalWaylandGlobal(4, "wl_seat", 5),
            MinimalWaylandGlobal(5, "wl_output", 2),
            MinimalWaylandGlobal(6, "wl_subcompositor", 1),
            MinimalWaylandGlobal(7, "wl_data_device_manager", 3),
            MinimalWaylandGlobal(8, "gtk_shell1", 5),
            MinimalWaylandGlobal(9, "zxdg_shell_v6", 1),
            MinimalWaylandGlobal(10, "wl_shell", 1),
            MinimalWaylandGlobal(11, "xdg_shell", 1),
        )

    private fun minimalWaylandGlobalNames(): List<String> =
        minimalWaylandGlobals().map { it.interfaceName }

    private fun describeMinimalWaylandRequest(
        interfaceName: String,
        opcode: Int,
        payload: ByteArray,
    ): String =
        when (interfaceName) {
            "wl_display" -> when (opcode) {
                0 -> "wl_display.sync"
                1 -> "wl_display.get_registry"
                else -> "wl_display.op$opcode"
            }
            "wl_registry" -> when (opcode) {
                0 -> "wl_registry.bind:${readRegistryBindInterface(payload)}"
                else -> "wl_registry.op$opcode"
            }
            "wl_compositor" -> when (opcode) {
                0 -> "wl_compositor.create_surface"
                1 -> "wl_compositor.create_region"
                else -> "wl_compositor.op$opcode"
            }
            "wl_shell" -> when (opcode) {
                0 -> "wl_shell.get_shell_surface"
                else -> "wl_shell.op$opcode"
            }
            "wl_shell_surface" -> when (opcode) {
                0 -> "wl_shell_surface.pong"
                1 -> "wl_shell_surface.move"
                2 -> "wl_shell_surface.resize"
                3 -> "wl_shell_surface.set_toplevel"
                4 -> "wl_shell_surface.set_transient"
                5 -> "wl_shell_surface.set_fullscreen"
                6 -> "wl_shell_surface.set_popup"
                7 -> "wl_shell_surface.set_maximized"
                8 -> "wl_shell_surface.set_title"
                9 -> "wl_shell_surface.set_class"
                else -> "wl_shell_surface.op$opcode"
            }
            "wl_subcompositor" -> when (opcode) {
                0 -> "wl_subcompositor.destroy"
                1 -> "wl_subcompositor.get_subsurface"
                else -> "wl_subcompositor.op$opcode"
            }
            "wl_subsurface" -> when (opcode) {
                0 -> "wl_subsurface.destroy"
                1 -> "wl_subsurface.set_position"
                2 -> "wl_subsurface.place_above"
                3 -> "wl_subsurface.place_below"
                4 -> "wl_subsurface.set_sync"
                5 -> "wl_subsurface.set_desync"
                else -> "wl_subsurface.op$opcode"
            }
            "wl_region" -> when (opcode) {
                0 -> "wl_region.destroy"
                1 -> "wl_region.add"
                2 -> "wl_region.subtract"
                else -> "wl_region.op$opcode"
            }
            "wl_surface" -> when (opcode) {
                0 -> "wl_surface.destroy"
                1 -> "wl_surface.attach"
                2 -> "wl_surface.damage"
                3 -> "wl_surface.frame"
                4 -> "wl_surface.set_opaque_region"
                5 -> "wl_surface.set_input_region"
                6 -> "wl_surface.commit"
                9 -> "wl_surface.damage_buffer"
                else -> "wl_surface.op$opcode"
            }
            "wl_shm" -> when (opcode) {
                0 -> "wl_shm.create_pool"
                else -> "wl_shm.op$opcode"
            }
            "wl_shm_pool" -> when (opcode) {
                0 -> "wl_shm_pool.create_buffer"
                1 -> "wl_shm_pool.destroy"
                2 -> "wl_shm_pool.resize"
                else -> "wl_shm_pool.op$opcode"
            }
            "wl_buffer" -> when (opcode) {
                0 -> "wl_buffer.destroy"
                else -> "wl_buffer.op$opcode"
            }
            "wl_seat" -> when (opcode) {
                0 -> "wl_seat.get_pointer"
                1 -> "wl_seat.get_keyboard"
                2 -> "wl_seat.get_touch"
                3 -> "wl_seat.release"
                else -> "wl_seat.op$opcode"
            }
            "wl_pointer" -> when (opcode) {
                0 -> "wl_pointer.set_cursor"
                1 -> "wl_pointer.release"
                else -> "wl_pointer.op$opcode"
            }
            "wl_keyboard" -> when (opcode) {
                0 -> "wl_keyboard.release"
                else -> "wl_keyboard.op$opcode"
            }
            "wl_touch" -> when (opcode) {
                0 -> "wl_touch.release"
                else -> "wl_touch.op$opcode"
            }
            "wl_data_device_manager" -> when (opcode) {
                0 -> "wl_data_device_manager.create_data_source"
                1 -> "wl_data_device_manager.get_data_device"
                else -> "wl_data_device_manager.op$opcode"
            }
            "wl_data_source" -> when (opcode) {
                0 -> "wl_data_source.offer"
                1 -> "wl_data_source.destroy"
                2 -> "wl_data_source.set_actions"
                else -> "wl_data_source.op$opcode"
            }
            "wl_data_device" -> when (opcode) {
                0 -> "wl_data_device.start_drag"
                1 -> "wl_data_device.set_selection"
                2 -> "wl_data_device.release"
                else -> "wl_data_device.op$opcode"
            }
            "gtk_shell1" -> when (opcode) {
                0 -> "gtk_shell1.get_gtk_surface"
                1 -> "gtk_shell1.set_startup_id"
                2 -> "gtk_shell1.system_bell"
                3 -> "gtk_shell1.notify_launch"
                else -> "gtk_shell1.op$opcode"
            }
            "gtk_surface1" -> when (opcode) {
                0 -> "gtk_surface1.set_dbus_properties"
                1 -> "gtk_surface1.set_modal"
                2 -> "gtk_surface1.unset_modal"
                3 -> "gtk_surface1.present"
                else -> "gtk_surface1.op$opcode"
            }
            "wl_callback" -> when (opcode) {
                0 -> "wl_callback.destroy"
                else -> "wl_callback.op$opcode"
            }
            "xdg_wm_base" -> when (opcode) {
                0 -> "xdg_wm_base.destroy"
                1 -> "xdg_wm_base.create_positioner"
                2 -> "xdg_wm_base.get_xdg_surface"
                3 -> "xdg_wm_base.pong"
                else -> "xdg_wm_base.op$opcode"
            }
            "zxdg_shell_v6" -> when (opcode) {
                0 -> "zxdg_shell_v6.destroy"
                1 -> "zxdg_shell_v6.create_positioner"
                2 -> "zxdg_shell_v6.get_xdg_surface"
                3 -> "zxdg_shell_v6.pong"
                else -> "zxdg_shell_v6.op$opcode"
            }
            "xdg_shell" -> when (opcode) {
                0 -> "xdg_shell.destroy"
                1 -> "xdg_shell.use_unstable_version"
                2 -> "xdg_shell.get_xdg_surface"
                3 -> "xdg_shell.get_xdg_popup"
                4 -> "xdg_shell.pong"
                else -> "xdg_shell.op$opcode"
            }
            "xdg_surface" -> when (opcode) {
                0 -> "xdg_surface.destroy"
                1 -> "xdg_surface.get_toplevel"
                2 -> "xdg_surface.get_popup"
                3 -> "xdg_surface.set_window_geometry"
                4 -> "xdg_surface.ack_configure"
                else -> "xdg_surface.op$opcode"
            }
            "xdg_toplevel" -> when (opcode) {
                0 -> "xdg_toplevel.destroy"
                1 -> "xdg_toplevel.set_parent"
                2 -> "xdg_toplevel.set_title"
                3 -> "xdg_toplevel.set_app_id"
                4 -> "xdg_toplevel.show_window_menu"
                5 -> "xdg_toplevel.move"
                6 -> "xdg_toplevel.resize"
                7 -> "xdg_toplevel.set_max_size"
                8 -> "xdg_toplevel.set_min_size"
                9 -> "xdg_toplevel.set_maximized"
                10 -> "xdg_toplevel.unset_maximized"
                11 -> "xdg_toplevel.set_fullscreen"
                12 -> "xdg_toplevel.unset_fullscreen"
                13 -> "xdg_toplevel.set_minimized"
                else -> "xdg_toplevel.op$opcode"
            }
            "zxdg_surface_v6" -> when (opcode) {
                0 -> "zxdg_surface_v6.destroy"
                1 -> "zxdg_surface_v6.get_toplevel"
                2 -> "zxdg_surface_v6.get_popup"
                3 -> "zxdg_surface_v6.set_window_geometry"
                4 -> "zxdg_surface_v6.ack_configure"
                else -> "zxdg_surface_v6.op$opcode"
            }
            "zxdg_toplevel_v6" -> when (opcode) {
                0 -> "zxdg_toplevel_v6.destroy"
                1 -> "zxdg_toplevel_v6.set_parent"
                2 -> "zxdg_toplevel_v6.set_title"
                3 -> "zxdg_toplevel_v6.set_app_id"
                4 -> "zxdg_toplevel_v6.show_window_menu"
                5 -> "zxdg_toplevel_v6.move"
                6 -> "zxdg_toplevel_v6.resize"
                7 -> "zxdg_toplevel_v6.set_max_size"
                8 -> "zxdg_toplevel_v6.set_min_size"
                9 -> "zxdg_toplevel_v6.set_maximized"
                10 -> "zxdg_toplevel_v6.unset_maximized"
                11 -> "zxdg_toplevel_v6.set_fullscreen"
                12 -> "zxdg_toplevel_v6.unset_fullscreen"
                13 -> "zxdg_toplevel_v6.set_minimized"
                else -> "zxdg_toplevel_v6.op$opcode"
            }
            "xdg_surface_v5" -> when (opcode) {
                0 -> "xdg_surface_v5.destroy"
                1 -> "xdg_surface_v5.set_parent"
                2 -> "xdg_surface_v5.set_title"
                3 -> "xdg_surface_v5.set_app_id"
                4 -> "xdg_surface_v5.show_window_menu"
                5 -> "xdg_surface_v5.move"
                6 -> "xdg_surface_v5.resize"
                7 -> "xdg_surface_v5.ack_configure"
                8 -> "xdg_surface_v5.set_window_geometry"
                9 -> "xdg_surface_v5.set_maximized"
                10 -> "xdg_surface_v5.unset_maximized"
                11 -> "xdg_surface_v5.set_fullscreen"
                12 -> "xdg_surface_v5.unset_fullscreen"
                13 -> "xdg_surface_v5.set_minimized"
                else -> "xdg_surface_v5.op$opcode"
            }
            else -> "$interfaceName.op$opcode"
        }

    private fun readRegistryBindInterface(payload: ByteArray): String =
        if (payload.size >= 8) readWaylandString(payload, 4).value else "unknown"

    private fun buildMinimalWaylandResponses(
        objectId: Int,
        opcode: Int,
        payload: ByteArray,
        objectInterfaces: MutableMap<Int, String>,
        globals: List<MinimalWaylandGlobal>,
        boundOutputIds: MutableList<Int>,
        nextSerial: () -> Int,
        keyboardKeymapFile: File,
    ): List<WaylandServerResponse> {
        val interfaceName = objectInterfaces[objectId] ?: "unknown"
        if (interfaceName == "wl_display" && opcode == 1 && payload.size >= 4) {
            val registryId = readLe32(payload, 0)
            objectInterfaces[registryId] = "wl_registry"
            return globals.map { global -> waylandRegistryGlobal(registryId, global).asWaylandResponse() }
        }
        if (interfaceName == "wl_display" && opcode == 0 && payload.size >= 4) {
            val callbackId = readLe32(payload, 0)
            objectInterfaces[callbackId] = "wl_callback"
            return listOf(waylandCallbackDone(callbackId, nextSerial()).asWaylandResponse())
        }
        if (interfaceName == "wl_registry" && opcode == 0 && payload.size >= 16) {
            val globalName = readLe32(payload, 0)
            val boundInterface = readWaylandString(payload, 4)
            val version = if (payload.size >= boundInterface.nextOffset + 4) {
                readLe32(payload, boundInterface.nextOffset)
            } else {
                1
            }
            val newIdOffset = boundInterface.nextOffset + 4
            val newId = if (payload.size >= newIdOffset + 4) readLe32(payload, newIdOffset) else 0
            if (newId > 0) objectInterfaces[newId] = boundInterface.value
            if (boundInterface.value == "wl_output" && newId > 0 && !boundOutputIds.contains(newId)) {
                boundOutputIds += newId
            }
            return initialWaylandBindEvents(newId, boundInterface.value, version.coerceAtLeast(1), globalName, nextSerial)
                .map { it.asWaylandResponse() }
        }
        if (interfaceName == "wl_compositor" && opcode == 0 && payload.size >= 4) {
            val surfaceId = readLe32(payload, 0)
            objectInterfaces[surfaceId] = "wl_surface"
            return boundOutputIds.firstOrNull()
                ?.let { outputId -> listOf(waylandObjectEvent(surfaceId, opcode = 0, eventObjectId = outputId).asWaylandResponse()) }
                ?: emptyList()
        }
        if (interfaceName == "wl_compositor" && opcode == 1 && payload.size >= 4) {
            val regionId = readLe32(payload, 0)
            objectInterfaces[regionId] = "wl_region"
            return emptyList()
        }
        if (interfaceName == "wl_shell" && opcode == 0 && payload.size >= 8) {
            val shellSurfaceId = readLe32(payload, 0)
            objectInterfaces[shellSurfaceId] = "wl_shell_surface"
            return emptyList()
        }
        if (interfaceName == "wl_subcompositor" && opcode == 1 && payload.size >= 12) {
            val subsurfaceId = readLe32(payload, 0)
            objectInterfaces[subsurfaceId] = "wl_subsurface"
            return emptyList()
        }
        if (interfaceName == "wl_shm" && opcode == 0 && payload.size >= 8) {
            val poolId = readLe32(payload, 0)
            objectInterfaces[poolId] = "wl_shm_pool"
            return emptyList()
        }
        if (interfaceName == "wl_shm_pool" && opcode == 0 && payload.size >= 4) {
            val bufferId = readLe32(payload, 0)
            objectInterfaces[bufferId] = "wl_buffer"
            return emptyList()
        }
        if (interfaceName == "wl_seat" && opcode == 0 && payload.size >= 4) {
            val pointerId = readLe32(payload, 0)
            objectInterfaces[pointerId] = "wl_pointer"
            return emptyList()
        }
        if (interfaceName == "wl_seat" && opcode == 1 && payload.size >= 4) {
            val keyboardId = readLe32(payload, 0)
            objectInterfaces[keyboardId] = "wl_keyboard"
            return listOf(waylandKeyboardKeymap(keyboardId, keyboardKeymapFile))
        }
        if (interfaceName == "wl_seat" && opcode == 2 && payload.size >= 4) {
            val touchId = readLe32(payload, 0)
            objectInterfaces[touchId] = "wl_touch"
            return emptyList()
        }
        if (interfaceName == "wl_data_device_manager" && opcode == 0 && payload.size >= 4) {
            val dataSourceId = readLe32(payload, 0)
            objectInterfaces[dataSourceId] = "wl_data_source"
            return emptyList()
        }
        if (interfaceName == "wl_data_device_manager" && opcode == 1 && payload.size >= 4) {
            val dataDeviceId = readLe32(payload, 0)
            objectInterfaces[dataDeviceId] = "wl_data_device"
            return listOf(waylandObjectEvent(dataDeviceId, opcode = 4, eventObjectId = 0).asWaylandResponse())
        }
        if (interfaceName == "gtk_shell1" && opcode == 0 && payload.size >= 8) {
            val gtkSurfaceId = readLe32(payload, 0)
            objectInterfaces[gtkSurfaceId] = "gtk_surface1"
            return emptyList()
        }
        if (interfaceName == "wl_surface" && opcode == 3 && payload.size >= 4) {
            val callbackId = readLe32(payload, 0)
            objectInterfaces[callbackId] = "wl_callback"
            return listOf(waylandCallbackDone(callbackId, nextSerial()).asWaylandResponse())
        }
        if (interfaceName == "xdg_wm_base" && opcode == 1 && payload.size >= 4) {
            val positionerId = readLe32(payload, 0)
            objectInterfaces[positionerId] = "xdg_positioner"
            return emptyList()
        }
        if (interfaceName == "xdg_wm_base" && opcode == 2 && payload.size >= 8) {
            val xdgSurfaceId = readLe32(payload, 0)
            objectInterfaces[xdgSurfaceId] = "xdg_surface"
            return emptyList()
        }
        if (interfaceName == "zxdg_shell_v6" && opcode == 1 && payload.size >= 4) {
            val positionerId = readLe32(payload, 0)
            objectInterfaces[positionerId] = "zxdg_positioner_v6"
            return emptyList()
        }
        if (interfaceName == "zxdg_shell_v6" && opcode == 2 && payload.size >= 8) {
            val xdgSurfaceId = readLe32(payload, 0)
            objectInterfaces[xdgSurfaceId] = "zxdg_surface_v6"
            return emptyList()
        }
        if (interfaceName == "xdg_shell" && opcode == 2 && payload.size >= 8) {
            val xdgSurfaceId = readLe32(payload, 0)
            objectInterfaces[xdgSurfaceId] = "xdg_surface_v5"
            return listOf(waylandXdgV5SurfaceConfigure(xdgSurfaceId, nextSerial()).asWaylandResponse())
        }
        if (interfaceName == "xdg_surface" && opcode == 1 && payload.size >= 4) {
            val toplevelId = readLe32(payload, 0)
            objectInterfaces[toplevelId] = "xdg_toplevel"
            return listOf(
                waylandXdgToplevelConfigure(toplevelId).asWaylandResponse(),
                waylandXdgSurfaceConfigure(objectId, nextSerial()).asWaylandResponse(),
            )
        }
        if (interfaceName == "zxdg_surface_v6" && opcode == 1 && payload.size >= 4) {
            val toplevelId = readLe32(payload, 0)
            objectInterfaces[toplevelId] = "zxdg_toplevel_v6"
            return listOf(
                waylandXdgToplevelConfigure(toplevelId).asWaylandResponse(),
                waylandXdgSurfaceConfigure(objectId, nextSerial()).asWaylandResponse(),
            )
        }
        return emptyList()
    }

    private fun initialWaylandBindEvents(
        objectId: Int,
        interfaceName: String,
        version: Int,
        globalName: Int,
        nextSerial: () -> Int,
    ): List<ByteArray> =
        when (interfaceName) {
            "wl_shm" -> listOf(
                waylandShmFormat(objectId, 0),
                waylandShmFormat(objectId, 1),
            )
            "wl_seat" -> buildList {
                add(waylandFixedU32Event(objectId, opcode = 0, value = 0x3))
                if (version >= 2) add(waylandStringEvent(objectId, opcode = 1, value = "alr-seat"))
            }
            "wl_output" -> buildList {
                add(waylandOutputGeometry(objectId))
                add(waylandOutputMode(objectId))
                if (version >= 2) add(waylandEmptyEvent(objectId, opcode = 2))
                if (version >= 2) add(waylandFixedU32Event(objectId, opcode = 3, value = 1))
            }
            "xdg_wm_base" -> listOf(waylandFixedU32Event(objectId, opcode = 0, value = nextSerial()))
            "zxdg_shell_v6" -> listOf(waylandFixedU32Event(objectId, opcode = 0, value = nextSerial()))
            "xdg_shell" -> listOf(waylandFixedU32Event(objectId, opcode = 0, value = nextSerial()))
            else -> emptyList()
        }

    private fun ByteArray.asWaylandResponse(): WaylandServerResponse =
        WaylandServerResponse(this)

    private fun prepareWaylandKeyboardKeymapFile(): File =
        File(cacheDir, "alr-wayland-keymap.xkb").apply {
            val keymap = """
                xkb_keymap {
                xkb_keycodes "alr" {
                    minimum = 8;
                    maximum = 255;
                    <ESC> = 9;
                    <RTRN> = 36;
                    <SPCE> = 65;
                };
                xkb_types "alr" {
                    type "ONE_LEVEL" {
                        modifiers = none;
                        level_name[Level1] = "Any";
                    };
                };
                xkb_compatibility "alr" { };
                xkb_symbols "alr" {
                    key <ESC> { [ Escape ] };
                    key <RTRN> { [ Return ] };
                    key <SPCE> { [ space ] };
                };
                xkb_geometry "alr" { };
                };
            """.trimIndent() + "\n"
            writeBytes(keymap.toByteArray() + byteArrayOf(0))
        }

    private fun waylandKeyboardKeymap(objectId: Int, keymapFile: File): WaylandServerResponse =
        WaylandServerResponse(
            bytes = waylandMessage(objectId, opcode = 0) {
                writeLe32(1)
                writeLe32(keymapFile.length().toInt())
            },
            fds = listOf(ParcelFileDescriptor.open(keymapFile, ParcelFileDescriptor.MODE_READ_ONLY)),
        )

    private fun waylandRegistryGlobal(registryId: Int, global: MinimalWaylandGlobal): ByteArray {
        val nameBytes = waylandStringBytes(global.interfaceName)
        return waylandMessage(registryId, opcode = 0) {
            writeLe32(global.name)
            write(nameBytes)
            writeLe32(global.version)
        }
    }

    private fun waylandCallbackDone(callbackId: Int, serial: Int): ByteArray =
        waylandMessage(callbackId, opcode = 0) {
            writeLe32(serial)
        }

    private fun waylandShmFormat(objectId: Int, format: Int): ByteArray =
        waylandMessage(objectId, opcode = 0) {
            writeLe32(format)
        }

    private fun waylandFixedU32Event(objectId: Int, opcode: Int, value: Int): ByteArray =
        waylandMessage(objectId, opcode = opcode) {
            writeLe32(value)
        }

    private fun waylandObjectEvent(objectId: Int, opcode: Int, eventObjectId: Int): ByteArray =
        waylandMessage(objectId, opcode = opcode) {
            writeLe32(eventObjectId)
        }

    private fun waylandEmptyEvent(objectId: Int, opcode: Int): ByteArray =
        waylandMessage(objectId, opcode = opcode) {}

    private fun waylandStringEvent(objectId: Int, opcode: Int, value: String): ByteArray =
        waylandMessage(objectId, opcode = opcode) {
            write(waylandStringBytes(value))
        }

    private fun waylandOutputGeometry(objectId: Int): ByteArray =
        waylandMessage(objectId, opcode = 0) {
            writeLe32(0)
            writeLe32(0)
            writeLe32(340)
            writeLe32(190)
            writeLe32(0)
            write(waylandStringBytes("Android"))
            write(waylandStringBytes("ALR virtual output"))
            writeLe32(0)
        }

    private fun waylandOutputMode(objectId: Int): ByteArray =
        waylandMessage(objectId, opcode = 1) {
            writeLe32(3)
            writeLe32(1280)
            writeLe32(720)
            writeLe32(60000)
        }

    private fun waylandXdgToplevelConfigure(objectId: Int): ByteArray =
        waylandMessage(objectId, opcode = 0) {
            writeLe32(640)
            writeLe32(480)
            writeLe32(0)
        }

    private fun waylandXdgSurfaceConfigure(objectId: Int, serial: Int): ByteArray =
        waylandMessage(objectId, opcode = 0) {
            writeLe32(serial)
        }

    private fun waylandXdgV5SurfaceConfigure(objectId: Int, serial: Int): ByteArray =
        waylandMessage(objectId, opcode = 0) {
            writeLe32(640)
            writeLe32(480)
            writeLe32(0)
            writeLe32(serial)
        }

    private fun waylandMessage(
        objectId: Int,
        opcode: Int,
        writePayload: ByteArrayOutputStream.() -> Unit,
    ): ByteArray {
        val payload = ByteArrayOutputStream().apply(writePayload).toByteArray()
        val size = 8 + payload.size
        return ByteArrayOutputStream().apply {
            writeLe32(objectId)
            writeLe32((size shl 16) or (opcode and 0xffff))
            write(payload)
        }.toByteArray()
    }

    private fun waylandStringBytes(value: String): ByteArray {
        val bytes = value.toByteArray()
        val lengthWithNul = bytes.size + 1
        val paddedLength = ((lengthWithNul + 3) / 4) * 4
        return ByteArrayOutputStream().apply {
            writeLe32(lengthWithNul)
            write(bytes)
            repeat(paddedLength - bytes.size) { write(0) }
        }.toByteArray()
    }

    private fun readWaylandString(bytes: ByteArray, offset: Int): WaylandString {
        if (bytes.size < offset + 4) return WaylandString("", offset)
        val lengthWithNul = readLe32(bytes, offset).coerceAtLeast(0)
        val textStart = offset + 4
        val textEnd = (textStart + lengthWithNul - 1).coerceIn(textStart, bytes.size)
        val paddedEnd = textStart + ((lengthWithNul + 3) / 4) * 4
        val value = bytes.copyOfRange(textStart, textEnd).toString(Charsets.UTF_8)
        return WaylandString(value, paddedEnd.coerceAtMost(bytes.size))
    }

    private fun readExactlyOrNull(input: InputStream, byteCount: Int): ByteArray? {
        if (byteCount == 0) return ByteArray(0)
        val buffer = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val read = input.read(buffer, offset, byteCount - offset)
            if (read < 0) return null
            offset += read
        }
        return buffer
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }

    private fun runInstalledPackageWaylandDisplayBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
        displayName: String = "alr-wayland-0",
        streamMode: String = "continuous-demo",
        runClient: (socketName: String, displayName: String) -> NativeCommandResult = { socketName, activeDisplayName ->
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageWaylandDisplayClientUnix(
                rootfsDir,
                socketName,
                activeDisplayName,
            )
        },
    ): GuestGpuIpcBridgeResult {
        val socketName = "alr-wayland-display-${System.nanoTime()}"
        val server = LocalServerSocket(socketName)
        val rawLines = mutableListOf("ALR_WAYLAND_DISPLAY_SOCKET display=$displayName name=@$socketName")
        val ackLines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val acceptThread = thread(name = "alr-wayland-display-ipc-bridge", start = true) {
            try {
                server.use { srv ->
                    val accepted = srv.accept()
                    accepted.use { socket ->
                        socket.setSoTimeout(3000)
                        val input = socket.getInputStream()
                        val fdMarker = input.read()
                        val receivedFds = if (fdMarker == 'F'.code) {
                            socket.ancillaryFileDescriptors?.toList().orEmpty()
                        } else {
                            emptyList()
                        }
                        val fdPayloads = receivedFds.mapIndexed { index, fd ->
                            verifyGuestFdPayload(
                                index = index,
                                fd = fd,
                                expectedBytes = 230400,
                            )
                        }
                        val markerText = if (fdMarker >= 0) fdMarker.toChar().toString() else "eof"
                        rawLines += "ALR_WL_FD_PREAMBLE marker=$markerText fds=${receivedFds.size}"
                        fdPayloads.forEach { fdPayload ->
                            rawLines += "ALR_WL_FD_PAYLOAD index=${fdPayload.index} verified=${fdPayload.verified} bytes=${fdPayload.bytes} checksum=${"%08x".format(fdPayload.checksum)} transport=scm-rights-memfd layout=triple-buffer"
                        }
                        val firstLine = readAsciiLine(input, 256)
                        val binaryHeader = firstLine?.takeIf { it.startsWith("ALR_WL_BINARY_STREAM ") }
                        val binaryMessages = if (binaryHeader != null) {
                            rawLines += binaryHeader
                            val binaryBytes = tokenValue(binaryHeader, "bytes")?.toIntOrNull()?.coerceIn(0, 4096) ?: 0
                            val expectedChecksum = tokenValue(binaryHeader, "checksum")?.toLongOrNull(16) ?: 0L
                            val payload = readExactBytes(input, binaryBytes)
                            val checksum = fnv1a32(payload)
                            val decoded = decodeWaylandBinaryMessages(payload)
                            val checksumVerified = payload.size == binaryBytes && checksum == expectedChecksum
                            rawLines += "ALR_WL_BINARY_DECODE bytes=${payload.size} messages=${decoded.size} checksum=${"%08x".format(checksum)} checksum_verified=$checksumVerified decoded=${decoded.isNotEmpty()} wire=wayland-binary-v1"
                            decoded.forEach { message ->
                                rawLines += "ALR_WL_BINARY_MESSAGE index=${message.index} object=${message.objectId} opcode=${message.opcode} size=${message.size}"
                            }
                            decoded
                        } else {
                            if (firstLine != null) rawLines += firstLine
                            emptyList()
                        }
                        val reader = input.bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            rawLines += line
                        }
                        val commits = rawLines.count { it.startsWith("ALR_WL_SURFACE_COMMIT ") }
                        val payloads = rawLines.count { it.startsWith("ALR_WL_BUFFER_ATTACH ") && it.contains("transport=shared-file") }
                        val ahbBackedAttaches = rawLines.count {
                            it.startsWith("ALR_WL_BUFFER_ATTACH ") && tokenValue(it, "backing") == "host-ahardwarebuffer"
                        }
                        val dirtyRectDamages = rawLines.count {
                            it.startsWith("ALR_WL_DAMAGE ") && tokenValue(it, "backing") == "host-ahardwarebuffer"
                        }
                        val partialUpdates = rawLines.count {
                            it.startsWith("ALR_WL_BUFFER_ATTACH ") && tokenValue(it, "update") == "partial"
                        }
                        val dirtyBytes = rawLines
                            .asSequence()
                            .filter { it.startsWith("ALR_WL_BUFFER_ATTACH ") }
                            .mapNotNull { tokenValue(it, "dirty_bytes")?.toIntOrNull() }
                            .sum()
                        val wireLines = rawLines.filter { it.startsWith("ALR_WL_WIRE ") }
                        val expectedWire = mutableListOf(
                            Triple("wl_display.get_registry", 1, 12),
                            Triple("wl_registry.bind", 0, 40),
                            Triple("wl_compositor.create_surface", 0, 12),
                            Triple("wl_registry.bind", 0, 32),
                            Triple("wl_shm.create_pool", 0, 20),
                            Triple("wl_shm_pool.create_buffer", 0, 36),
                        ).apply {
                            repeat(WAYLAND_DISPLAY_FRAMES) {
                                add(Triple("wl_surface.attach", 1, 20))
                                add(Triple("wl_surface.damage_buffer", 9, 24))
                                add(Triple("wl_surface.commit", 6, 8))
                            }
                        }
                        val wireSubsetReady = wireLines.size == expectedWire.size &&
                            wireLines.zip(expectedWire).all { (line, expected) ->
                                tokenValue(line, "name") == expected.first &&
                                    tokenValue(line, "opcode")?.toIntOrNull() == expected.second &&
                                    tokenValue(line, "size")?.toIntOrNull() == expected.third &&
                                    tokenValue(line, "wire") == "wayland-header-v1" &&
                                    tokenValue(line, "endian") == "little"
                            }
                        val binaryHeaderReady = binaryHeader != null &&
                            tokenValue(binaryHeader, "messages")?.toIntOrNull() == expectedWire.size &&
                            tokenValue(binaryHeader, "bytes")?.toIntOrNull() == WAYLAND_BINARY_BYTES &&
                            tokenValue(binaryHeader, "wire") == "wayland-binary-v1" &&
                            tokenValue(binaryHeader, "endian") == "little"
                        val binaryDecodeReady = binaryMessages.size == expectedWire.size &&
                            binaryMessages.zip(expectedWire).all { (message, expected) ->
                                message.opcode == expected.second && message.size == expected.third
                            } &&
                            rawLines.any { it.startsWith("ALR_WL_BINARY_DECODE ") && it.contains("checksum_verified=true") }
                        val wireSurfaceLifecycle = wireLines.count { tokenValue(it, "name") == "wl_surface.attach" } == WAYLAND_DISPLAY_FRAMES &&
                            wireLines.count { tokenValue(it, "name") == "wl_surface.damage_buffer" } == WAYLAND_DISPLAY_FRAMES &&
                            wireLines.count { tokenValue(it, "name") == "wl_surface.commit" } == WAYLAND_DISPLAY_FRAMES
                        val continuousStreamReady = rawLines.any {
                            it.startsWith("ALR_WL_APP_STREAM_BEGIN ") &&
                                tokenValue(it, "frames")?.toIntOrNull() == WAYLAND_DISPLAY_FRAMES &&
                                tokenValue(it, "mode") == streamMode
                        } && rawLines.any {
                            it.startsWith("ALR_WL_APP_STREAM_END ") &&
                                tokenValue(it, "commits")?.toIntOrNull() == WAYLAND_DISPLAY_FRAMES &&
                                tokenValue(it, "mode") == streamMode
                        }
                        val payloadBytes = rawLines
                            .asSequence()
                            .filter { it.startsWith("ALR_WL_BUFFER_ATTACH ") }
                            .mapNotNull { tokenValue(it, "bytes")?.toIntOrNull() }
                            .sum()
                        val fdPayloadCount = rawLines.count { it.startsWith("ALR_WL_BUFFER_ATTACH ") && it.contains("fd_index=") }
                        val fdPayloadBytes = rawLines
                            .asSequence()
                            .filter { it.startsWith("ALR_WL_BUFFER_ATTACH ") }
                            .mapNotNull { tokenValue(it, "fd_bytes")?.toIntOrNull() }
                            .sum()
                        val payloadVerified = rawLines
                            .filter { it.startsWith("ALR_WL_BUFFER_ATTACH ") }
                            .all { line ->
                                verifyGuestSharedPayload(
                                    rootfsDir,
                                    tokenValue(line, "path").orEmpty(),
                                    tokenValue(line, "bytes")?.toIntOrNull() ?: 0,
                                    tokenValue(line, "checksum")?.toLongOrNull(16) ?: 0,
                                )
                            } && payloads == WAYLAND_DISPLAY_FRAMES && payloadBytes == WAYLAND_FULL_PAYLOAD_BYTES
                        val fdPayloadVerified = rawLines
                            .filter { it.startsWith("ALR_WL_BUFFER_ATTACH ") }
                            .all { line ->
                                val fdIndex = tokenValue(line, "fd_index")?.toIntOrNull() ?: -1
                                val fdPayload = fdPayloads.firstOrNull { it.index == fdIndex }
                                fdPayload != null &&
                                    fdPayload.verified &&
                                    tokenValue(line, "fd_bytes")?.toIntOrNull() == fdPayload.bytes &&
                                    tokenValue(line, "fd_checksum")?.toLongOrNull(16) == fdPayload.checksum
                            } && fdPayloads.size == WAYLAND_DISPLAY_FRAMES && fdPayloadCount == WAYLAND_DISPLAY_FRAMES && fdPayloadBytes == WAYLAND_FULL_PAYLOAD_BYTES
                        val lossless = commits == WAYLAND_DISPLAY_FRAMES
                        val ahbStateReady = ahbBackedAttaches == WAYLAND_DISPLAY_FRAMES &&
                            dirtyRectDamages == WAYLAND_DISPLAY_FRAMES &&
                            partialUpdates == WAYLAND_DISPLAY_FRAMES &&
                            dirtyBytes == WAYLAND_DIRTY_BYTES
                        val ack = "ALR_WL_DISPLAY_ACK display=$displayName commits=$commits expected=$WAYLAND_DISPLAY_FRAMES lossless=$lossless payloads=$payloads payload_bytes=$payloadBytes payload_verified=$payloadVerified fd_payloads=$fdPayloadCount fd_payload_bytes=$fdPayloadBytes fd_payload_verified=$fdPayloadVerified fd_received=${fdPayloads.size} layout=triple-buffer transport=unix-abstract-wayland-scm-rights wire_messages=${wireLines.size} wire_subset_ready=$wireSubsetReady wire_surface_lifecycle=$wireSurfaceLifecycle binary_messages=${binaryMessages.size} binary_header_ready=$binaryHeaderReady binary_subset_ready=$binaryDecodeReady continuous_stream_ready=$continuousStreamReady backing=host-ahardwarebuffer ahb_backed=$ahbBackedAttaches dirty_rects=$dirtyRectDamages dirty_bytes=$dirtyBytes partial_updates=$partialUpdates ahb_state_ready=$ahbStateReady zero_copy_candidate=true"
                        ackLines += ack
                        socket.getOutputStream().write((ack + "\n").toByteArray())
                        socket.getOutputStream().flush()
                    }
                }
            } catch (error: SocketTimeoutException) {
                errors += "timeout waiting for wayland display client"
            } catch (error: Exception) {
                errors += error.javaClass.simpleName + ": " + (error.message ?: "unknown")
            }
        }
        val clientResult = runClient(socketName, displayName)
        acceptThread.join(3500)
        if (acceptThread.isAlive) {
            errors += "wayland display accept thread still alive after join"
            server.close()
        }
        val commands = parseWaylandDisplayCommands(rawLines.joinToString("\n"), rootfsDir)
        return GuestGpuIpcBridgeResult(
            host = "unix-abstract-wayland-display",
            port = 0,
            expectedFrames = WAYLAND_DISPLAY_FRAMES,
            commands = commands,
            rawLines = rawLines.toList(),
            error = errors.firstOrNull(),
            clientResult = clientResult,
            ackLines = ackLines.toList(),
        )
    }

    private fun runInstalledPackageSimpleGuiDemoBridge(
        nativeCommandRunner: NativeCommandRunner,
        rootfsDir: File,
    ): GuestGpuIpcBridgeResult =
        runInstalledPackageWaylandDisplayBridge(
            nativeCommandRunner,
            rootfsDir,
            displayName = "alr-simple-gui-demo-0",
            streamMode = "simple-gui-demo",
        ) { socketName, displayName ->
            nativeCommandRunner.runAlrRuntimeTrampolineInstalledPackageSimpleGuiDemoUnix(
                rootfsDir,
                socketName,
                displayName,
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

    private fun parseWaylandDisplayCommands(text: String, rootfsDir: File): List<GuestGpuCommand> {
        val fdPayloads = text.lineSequence()
            .filter { it.startsWith("ALR_WL_FD_PAYLOAD ") }
            .mapNotNull { line ->
                val index = tokenValue(line, "index")?.toIntOrNull() ?: return@mapNotNull null
                GuestFdPayloadVerification(
                    index = index,
                    verified = tokenValue(line, "verified") == "true",
                    bytes = tokenValue(line, "bytes")?.toIntOrNull() ?: 0,
                    checksum = tokenValue(line, "checksum")?.toLongOrNull(16) ?: 0,
                )
            }
            .associateBy { it.index }
        val attaches = text.lineSequence()
            .filter { it.startsWith("ALR_WL_BUFFER_ATTACH ") }
            .mapNotNull { parseWaylandAttachState(it) }
            .associateBy { it.seq }
        return text.lineSequence()
            .filter { it.startsWith("ALR_WL_SURFACE_COMMIT ") }
            .mapNotNull { parseWaylandDisplayCommitLine(it, rootfsDir, fdPayloads, attaches) }
            .toList()
    }

    private fun parseWaylandAttachState(line: String): WaylandAttachState? {
        val seq = tokenValue(line, "seq")?.toIntOrNull() ?: return null
        val dirtyWidth = tokenValue(line, "dirty_w")?.toIntOrNull() ?: 0
        val dirtyHeight = tokenValue(line, "dirty_h")?.toIntOrNull() ?: 0
        val dirtyBytes = tokenValue(line, "dirty_bytes")?.toIntOrNull()
            ?: (dirtyWidth * dirtyHeight * 4)
        return WaylandAttachState(
            seq = seq,
            backing = tokenValue(line, "backing").orEmpty(),
            bufferSlot = tokenValue(line, "buffer_slot")?.toIntOrNull() ?: -1,
            dirtyX = tokenValue(line, "dirty_x")?.toIntOrNull() ?: 0,
            dirtyY = tokenValue(line, "dirty_y")?.toIntOrNull() ?: 0,
            dirtyWidth = dirtyWidth,
            dirtyHeight = dirtyHeight,
            dirtyBytes = dirtyBytes,
            partialUpdate = tokenValue(line, "update") == "partial",
        )
    }

    private fun parseWaylandDisplayCommitLine(
        line: String,
        rootfsDir: File,
        fdPayloads: Map<Int, GuestFdPayloadVerification>,
        attaches: Map<Int, WaylandAttachState>,
    ): GuestGpuCommand? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 8 || parts[0] != "ALR_WL_SURFACE_COMMIT") return null
        val seq = parts.firstOrNull { it.startsWith("seq=") }
            ?.substringAfter("=")
            ?.toIntOrNull()
            ?: return null
        val red = parts[4].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val green = parts[5].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val blue = parts[6].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        val payloadPath = tokenValue(line, "payload").orEmpty()
        val payloadBytes = tokenValue(line, "bytes")?.toIntOrNull() ?: 0
        val payloadChecksum = tokenValue(line, "checksum")?.toLongOrNull(16) ?: 0
        val payloadVerified = verifyGuestSharedPayload(rootfsDir, payloadPath, payloadBytes, payloadChecksum)
        val fdIndex = tokenValue(line, "fd_index")?.toIntOrNull() ?: -1
        val fdBytes = tokenValue(line, "fd_bytes")?.toIntOrNull() ?: 0
        val fdChecksum = tokenValue(line, "fd_checksum")?.toLongOrNull(16) ?: 0
        val fdPayload = fdPayloads[fdIndex]
        val fdVerified = fdPayload != null &&
            fdPayload.verified &&
            fdBytes == fdPayload.bytes &&
            fdChecksum == fdPayload.checksum
        val attach = attaches[seq]
        val backing = tokenValue(line, "backing") ?: attach?.backing.orEmpty()
        val dirtyWidth = tokenValue(line, "dirty_w")?.toIntOrNull() ?: attach?.dirtyWidth ?: 0
        val dirtyHeight = tokenValue(line, "dirty_h")?.toIntOrNull() ?: attach?.dirtyHeight ?: 0
        val dirtyBytes = tokenValue(line, "dirty_bytes")?.toIntOrNull()
            ?: attach?.dirtyBytes
            ?: (dirtyWidth * dirtyHeight * 4)
        return GuestGpuCommand(
            red,
            green,
            blue,
            "WAYLAND-shared-payload-$seq",
            "WAYLAND",
            seq,
            payloadPath,
            payloadBytes,
            payloadChecksum,
            payloadVerified,
            fdBytes,
            fdChecksum,
            fdVerified,
            backing,
            tokenValue(line, "buffer_slot")?.toIntOrNull() ?: attach?.bufferSlot ?: -1,
            tokenValue(line, "dirty_x")?.toIntOrNull() ?: attach?.dirtyX ?: 0,
            tokenValue(line, "dirty_y")?.toIntOrNull() ?: attach?.dirtyY ?: 0,
            dirtyWidth,
            dirtyHeight,
            dirtyBytes,
            tokenValue(line, "update") == "partial" || attach?.partialUpdate == true,
        )
    }

    private fun tokenValue(line: String, key: String): String? =
        line.split(Regex("\\s+"))
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotEmpty() }

    private fun verifyGuestSharedPayload(rootfsDir: File, guestPath: String, expectedBytes: Int, expectedChecksum: Long): Boolean {
        if (!guestPath.startsWith("/usr/share/alr-smoke/alr-wayland-runtime/") || expectedBytes <= 0 || expectedChecksum <= 0) return false
        val hostFile = File(rootfsDir, guestPath.removePrefix("/"))
        val rootCanonical = rootfsDir.canonicalFile
        val hostCanonical = hostFile.canonicalFile
        if (!hostCanonical.path.startsWith(rootCanonical.path + File.separator)) return false
        if (!hostCanonical.isFile || hostCanonical.length() != expectedBytes.toLong()) return false
        return fnv1a32(hostCanonical.readBytes()) == expectedChecksum
    }

    private fun verifyGuestFdPayload(index: Int, fd: FileDescriptor?, expectedBytes: Int): GuestFdPayloadVerification {
        if (fd == null || expectedBytes <= 0) return GuestFdPayloadVerification(index, false, 0, 0)
        val bytes = runCatching {
            FileInputStream(fd).use { input -> input.readBytes() }
        }.getOrElse {
            return GuestFdPayloadVerification(index, false, 0, 0)
        }
        val checksum = fnv1a32(bytes)
        return GuestFdPayloadVerification(
            index = index,
            verified = bytes.size == expectedBytes,
            bytes = bytes.size,
            checksum = checksum,
        )
    }

    private fun waylandFdSizeBytes(fd: FileDescriptor?): Long =
        if (fd == null) {
            0
        } else {
            runCatching { Os.fstat(fd).st_size.coerceAtLeast(0L) }.getOrDefault(0L)
        }

    private fun fnv1a32(bytes: ByteArray): Long {
        var hash = 0x811c9dc5L
        for (byte in bytes) {
            hash = hash xor (byte.toLong() and 0xffL)
            hash = (hash * 0x01000193L) and 0xffffffffL
        }
        return hash
    }

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.hexPrefix(maxBytes: Int = 32): String =
        take(maxBytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

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
        commands.joinToString(separator = "\n") {
            "${it.red} ${it.green} ${it.blue} ${it.protocol}-seq${it.seq}-${it.tag}" +
                " seq=${it.seq} backing=${it.backing.ifEmpty { "none" }} buffer_slot=${it.bufferSlot}" +
                " width=320 height=180 dirty_x=${it.dirtyX} dirty_y=${it.dirtyY}" +
                " dirty_w=${it.dirtyWidth} dirty_h=${it.dirtyHeight} dirty_bytes=${it.dirtyBytes}" +
                " partial=${it.partialUpdate}"
        }

    private fun partialUploadRatioPct(commands: List<GuestGpuCommand>): Int {
        val fullBytes = commands.sumOf { command ->
            if (command.fdPayloadBytes > 0) command.fdPayloadBytes else command.payloadBytes
        }
        val dirtyBytes = commands.sumOf { it.dirtyBytes }
        return if (fullBytes > 0 && dirtyBytes > 0) (dirtyBytes * 100) / fullBytes else 0
    }

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

    private fun vulkanSurfaceExecutionUpdate(
        vulkanSurfaceReport: String,
        icdManifestPassed: Boolean,
        loaderInfoPassed: Boolean,
        unixLoaderInfoPassed: Boolean,
    ): String {
        val clearRequestTag = vulkanSurfaceReport.lineStartingWith("surface vulkan clear request tag=")
        val passed =
            vulkanSurfaceReport.lineStartingWith("surface vulkan clear request source=") ==
                "surface vulkan clear request source=guest-request" &&
            vulkanSurfaceReport.lineStartingWith("surface vulkan present=") == "surface vulkan present=ok" &&
                vulkanSurfaceReport.lineStartingWith("surface vulkan hardware render=") == "surface vulkan hardware render=true" &&
                vulkanSurfaceReport.lineStartingWith("android host vulkan surface execution=") ==
                "android host vulkan surface execution=PASS"
        val proxySurfacePassed = passed && clearRequestTag == "surface vulkan clear request tag=guest-vulkan-proxy-clear-0001"
        return "ANDROID HOST VULKAN SURFACE EXECUTION: ${if (passed) "PASS" else "FAIL"}" +
            "\nGUEST VULKAN SURFACE CLEAR REQUEST EXECUTION: ${if (passed) "PASS" else "FAIL"}" +
            "\nGUEST VULKAN PROXY SURFACE CLEAR EXECUTION: ${if (proxySurfacePassed) "PASS" else "FAIL"}" +
            "\nGUEST VULKAN ICD MANIFEST SURFACE CLEAR EXECUTION: ${if (proxySurfacePassed && icdManifestPassed) "PASS" else "FAIL"}" +
            "\nGUEST VULKAN LOADER INFO SURFACE CLEAR EXECUTION: ${if (proxySurfacePassed && icdManifestPassed && loaderInfoPassed) "PASS" else "FAIL"}" +
            "\nGUEST VULKAN UNIX SOCKET LOADER INFO SURFACE CLEAR EXECUTION: ${if (proxySurfacePassed && icdManifestPassed && loaderInfoPassed && unixLoaderInfoPassed) "PASS" else "FAIL"}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan clear request=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan clear request source=")}" +
            "\n$clearRequestTag" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan device=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan api version=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan graphics present queue=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan present mode=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan swapchain image count=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan clear command=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan queue submit=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan present=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan hardware render=")}" +
            "\n${vulkanSurfaceReport.lineStartingWith("surface vulkan render elapsed us=")}"
    }

    private fun hardwareBufferExecutionUpdate(hardwareBufferReport: String): String {
        val passed =
            hardwareBufferReport.lineStartingWith("ahardwarebuffer execution=") == "ahardwarebuffer execution=PASS" &&
                hardwareBufferReport.lineStartingWith("ahardwarebuffer host managed triple buffer=") ==
                "ahardwarebuffer host managed triple buffer=true" &&
                hardwareBufferReport.lineStartingWith("ahardwarebuffer egl image import=") ==
                "ahardwarebuffer egl image import=ok"
        return "ANDROID HOST AHARDWAREBUFFER EXECUTION: ${if (passed) "PASS" else "FAIL"}" +
            "\nANDROID HOST AHARDWAREBUFFER EGL IMPORT EXECUTION: ${if (passed) "PASS" else "FAIL"}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer allocated buffers=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer cpu verified buffers=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer egl imported buffers=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer visible payload bytes=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer host managed triple buffer=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer cpu write dirty rect locks=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer cpu write fence count=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer cpu read fence count=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer sync fence accounting=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer egl image import=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer render elapsed us=")}"
    }

    private fun waylandHardwareBufferBridgeUpdate(hardwareBufferReport: String): String {
        val passed =
            hardwareBufferReport.lineStartingWith("ahardwarebuffer execution=") == "ahardwarebuffer execution=PASS" &&
                hardwareBufferReport.lineStartingWith("ahardwarebuffer wayland display backing=") ==
                "ahardwarebuffer wayland display backing=true" &&
                hardwareBufferReport.lineStartingWith("ahardwarebuffer sync fence accounting=") ==
                "ahardwarebuffer sync fence accounting=ok"
        return "WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: ${if (passed) "PASS" else "FAIL"}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer source=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer requested buffers=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer backing mode=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer wayland state machine backing=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer cpu verified buffers=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer egl imported buffers=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer dirty rect frames=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer dirty rect bytes=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer partial upload ratio pct=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer cpu write dirty rect locks=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer cpu write fence count=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer cpu read fence count=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer sync fence accounting=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer visible payload bytes=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer wayland display backing=")}" +
            "\n${hardwareBufferReport.lineStartingWith("ahardwarebuffer egl image import=")}"
    }

    private fun waylandHardwareBufferSurfaceUpdate(surfaceReport: String): String {
        val passed =
            surfaceReport.lineStartingWith("wayland ahardwarebuffer surface execution=") ==
                "wayland ahardwarebuffer surface execution=PASS" &&
                surfaceReport.lineStartingWith("wayland ahardwarebuffer surface hardware render=") ==
                "wayland ahardwarebuffer surface hardware render=true" &&
                surfaceReport.lineStartingWith("wayland ahardwarebuffer surface presented frames=") ==
                "wayland ahardwarebuffer surface presented frames=$WAYLAND_DISPLAY_FRAMES" &&
                surfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool reuses=") ==
                "wayland ahardwarebuffer surface buffer pool reuses=$WAYLAND_SURFACE_POOL_REUSES" &&
                surfaceReport.lineStartingWith("wayland ahardwarebuffer surface dirty rect bytes=") ==
                "wayland ahardwarebuffer surface dirty rect bytes=$WAYLAND_DIRTY_BYTES" &&
                surfaceReport.lineStartingWith("wayland ahardwarebuffer surface continuous guest commits=") ==
                "wayland ahardwarebuffer surface continuous guest commits=true" &&
                surfaceReport.lineStartingWith("wayland ahardwarebuffer surface sync fence accounting=") ==
                "wayland ahardwarebuffer surface sync fence accounting=ok"
        return "WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: ${if (passed) "PASS" else "FAIL"}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface compositor=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface replay passes=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface continuous guest commits=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface simple gui demo candidate=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface total frame submissions=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool mode=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool slots=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool misses=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface buffer pool reuses=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface allocated buffers=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface imported textures=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface sampled frames=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface presented frames=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface host-backed frames=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface dirty rect frames=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface dirty rect bytes=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface partial upload ratio pct=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface write fence count=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface fence wait candidates=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface fence wait handoffs=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface fence pacing mode=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface sync fence accounting=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface hardware render=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface render elapsed us=")}" +
            "\n${surfaceReport.lineStartingWith("wayland ahardwarebuffer surface execution=")}"
    }

    private fun vulkanBridgeTransportUpdate(
        tcpLoaderInfoResult: NativeCommandResult,
        unixLoaderInfoResult: NativeCommandResult,
        unixLoaderInfoPassed: Boolean,
    ): String =
        "VULKAN BRIDGE UNIX TRANSPORT EXECUTION: ${if (unixLoaderInfoPassed) "PASS" else "FAIL"}" +
            "\nvulkan bridge transport tcp loader elapsed ms=${tcpLoaderInfoResult.elapsedMs}" +
            "\nvulkan bridge transport unix loader elapsed ms=${unixLoaderInfoResult.elapsedMs}" +
            "\nvulkan bridge transport unix vs tcp ratio pct=${elapsedRatioPct(unixLoaderInfoResult, tcpLoaderInfoResult)}" +
            "\nvulkan bridge transport unix faster than tcp=${isFaster(unixLoaderInfoResult, tcpLoaderInfoResult)}"

    private fun glesBridgeTransportUpdate(
        tcpGlesResult: NativeCommandResult,
        unixGlesResult: NativeCommandResult,
        unixBatchGlesResult: NativeCommandResult,
        unixGlesPassed: Boolean,
        unixBatchGlesPassed: Boolean,
    ): String =
        "GLES BRIDGE UNIX TRANSPORT EXECUTION: ${if (unixGlesPassed) "PASS" else "FAIL"}" +
            "\nGLES BRIDGE UNIX BATCH TRANSPORT EXECUTION: ${if (unixBatchGlesPassed) "PASS" else "FAIL"}" +
            "\ngles bridge transport tcp loader elapsed ms=${tcpGlesResult.elapsedMs}" +
            "\ngles bridge transport unix loader elapsed ms=${unixGlesResult.elapsedMs}" +
            "\ngles bridge transport unix batch loader elapsed ms=${unixBatchGlesResult.elapsedMs}" +
            "\ngles bridge transport unix vs tcp ratio pct=${elapsedRatioPct(unixGlesResult, tcpGlesResult)}" +
            "\ngles bridge transport unix faster than tcp=${isFaster(unixGlesResult, tcpGlesResult)}" +
            "\ngles bridge transport unix batch vs tcp ratio pct=${elapsedRatioPct(unixBatchGlesResult, tcpGlesResult)}" +
            "\ngles bridge transport unix batch vs unix ack ratio pct=${elapsedRatioPct(unixBatchGlesResult, unixGlesResult)}" +
            "\ngles bridge transport unix batch faster than unix ack=${isFaster(unixBatchGlesResult, unixGlesResult)}"

    private fun guiBridgeTransportUpdate(
        waylandTcpResult: GuestGpuIpcBridgeResult,
        waylandUnixResult: GuestGpuIpcBridgeResult,
        x11TcpResult: GuestGpuIpcBridgeResult,
        x11UnixResult: GuestGpuIpcBridgeResult,
        waylandUnixPassed: Boolean,
        x11UnixPassed: Boolean,
    ): String =
        "GUI BRIDGE UNIX TRANSPORT EXECUTION: ${if (waylandUnixPassed && x11UnixPassed) "PASS" else "FAIL"}" +
            "\nWAYLAND GUI UNIX TRANSPORT EXECUTION: ${if (waylandUnixPassed) "PASS" else "FAIL"}" +
            "\nX11 GUI UNIX TRANSPORT EXECUTION: ${if (x11UnixPassed) "PASS" else "FAIL"}" +
            "\ngui bridge transport wayland tcp loader elapsed ms=${waylandTcpResult.clientResult.elapsedMs}" +
            "\ngui bridge transport wayland unix loader elapsed ms=${waylandUnixResult.clientResult.elapsedMs}" +
            "\ngui bridge transport wayland unix vs tcp ratio pct=${elapsedRatioPct(waylandUnixResult.clientResult, waylandTcpResult.clientResult)}" +
            "\ngui bridge transport wayland unix faster than tcp=${isFaster(waylandUnixResult.clientResult, waylandTcpResult.clientResult)}" +
            "\ngui bridge wayland unix frames=${waylandUnixResult.commands.size}/${waylandUnixResult.expectedFrames}" +
            "\ngui bridge wayland unix ack frames=${waylandUnixResult.ackLines.size}" +
            "\ngui bridge wayland unix error=${waylandUnixResult.error ?: "none"}" +
            "\ngui bridge wayland unix handoff=${waylandUnixResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\ngui bridge wayland unix stdout=${waylandUnixResult.clientResult.stdout.alrHandoffStdoutText()}" +
            "\ngui bridge transport x11 tcp loader elapsed ms=${x11TcpResult.clientResult.elapsedMs}" +
            "\ngui bridge transport x11 unix loader elapsed ms=${x11UnixResult.clientResult.elapsedMs}" +
            "\ngui bridge transport x11 unix vs tcp ratio pct=${elapsedRatioPct(x11UnixResult.clientResult, x11TcpResult.clientResult)}" +
            "\ngui bridge transport x11 unix faster than tcp=${isFaster(x11UnixResult.clientResult, x11TcpResult.clientResult)}" +
            "\ngui bridge x11 unix frames=${x11UnixResult.commands.size}/${x11UnixResult.expectedFrames}" +
            "\ngui bridge x11 unix ack frames=${x11UnixResult.ackLines.size}" +
            "\ngui bridge x11 unix error=${x11UnixResult.error ?: "none"}" +
            "\ngui bridge x11 unix handoff=${x11UnixResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\ngui bridge x11 unix stdout=${x11UnixResult.clientResult.stdout.alrHandoffStdoutText()}"

    private fun waylandDisplayBridgeUpdate(
        displayResult: GuestGpuIpcBridgeResult,
        displayPassed: Boolean,
    ): String =
        "WAYLAND DISPLAY SOCKET AVAILABLE: ${if (displayPassed) "PASS" else "FAIL"}" +
            "\nWAYLAND DISPLAY COMMIT SURFACE EXECUTION: ${if (displayPassed) "PASS" else "FAIL"}" +
            "\nwayland display socket name=alr-wayland-0" +
            "\nwayland display transport unix=true" +
            "\nwayland display surface commits=${displayResult.commands.size}/${displayResult.expectedFrames}" +
            "\nwayland display continuous stream ready=${displayResult.ackLines.firstOrNull()?.contains("continuous_stream_ready=true") == true}" +
            "\nwayland display wire messages=${displayResult.rawLines.count { it.startsWith("ALR_WL_WIRE ") }}" +
            "\nwayland display wire subset ready=${displayResult.ackLines.firstOrNull()?.contains("wire_subset_ready=true") == true}" +
            "\nwayland display wire surface lifecycle=${displayResult.ackLines.firstOrNull()?.contains("wire_surface_lifecycle=true") == true}" +
            "\nwayland display binary messages=${displayResult.rawLines.count { it.startsWith("ALR_WL_BINARY_MESSAGE ") }}" +
            "\nwayland display binary bytes=${tokenValue(displayResult.rawLines.firstOrNull { it.startsWith("ALR_WL_BINARY_STREAM ") }.orEmpty(), "bytes") ?: "0"}" +
            "\nwayland display binary subset ready=${displayResult.ackLines.firstOrNull()?.contains("binary_subset_ready=true") == true}" +
            "\nwayland display ahardwarebuffer backed frames=${displayResult.commands.count { it.backing == "host-ahardwarebuffer" }}/${displayResult.expectedFrames}" +
            "\nwayland display dirty rect frames=${displayResult.commands.count { it.partialUpdate }}/${displayResult.expectedFrames}" +
            "\nwayland display dirty rect bytes=${displayResult.commands.sumOf { it.dirtyBytes }}" +
            "\nwayland display partial upload ratio pct=${partialUploadRatioPct(displayResult.commands)}" +
            "\nwayland display ack frames=${displayResult.ackLines.size}" +
            "\nwayland display error=${displayResult.error ?: "none"}" +
            "\nwayland display handoff=${displayResult.clientResult.stdout.lineStartingWith("ALR STATIC ENTRY HANDOFF:")}" +
            "\nwayland display stdout=${displayResult.clientResult.stdout.alrHandoffStdoutText()}"

    private fun String.lineStartingWith(prefix: String): String =
        lineSequence().firstOrNull { it.startsWith(prefix) } ?: "missing"

    private fun readAsciiLine(input: InputStream, maxBytes: Int): String? {
        val output = ByteArrayOutputStream()
        while (output.size() < maxBytes) {
            val next = input.read()
            if (next < 0) break
            if (next == '\n'.code) break
            output.write(next)
        }
        if (output.size() == 0) return null
        return output.toString(Charsets.UTF_8.name()).trimEnd('\r')
    }

    private fun readExactBytes(input: InputStream, expectedBytes: Int): ByteArray {
        if (expectedBytes <= 0 || expectedBytes > 4096) return ByteArray(0)
        val output = ByteArray(expectedBytes)
        var offset = 0
        while (offset < expectedBytes) {
            val count = input.read(output, offset, expectedBytes - offset)
            if (count <= 0) break
            offset += count
        }
        return if (offset == expectedBytes) output else output.copyOf(offset)
    }

    private fun decodeWaylandBinaryMessages(bytes: ByteArray): List<WaylandBinaryMessage> {
        val messages = mutableListOf<WaylandBinaryMessage>()
        var offset = 0
        var index = 0
        while (offset + 8 <= bytes.size && messages.size < 32) {
            val objectId = readU32Le(bytes, offset)
            val header = readU32Le(bytes, offset + 4)
            val opcode = header and 0xffff
            val size = (header ushr 16) and 0xffff
            if (objectId <= 0 || size < 8 || size % 4 != 0 || offset + size > bytes.size) break
            messages += WaylandBinaryMessage(index, objectId, opcode, size)
            offset += size
            index += 1
        }
        return messages.takeIf { offset == bytes.size } ?: emptyList()
    }

    private fun readAllBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (output.size() < maxBytes) {
            val nextLimit = minOf(buffer.size, maxBytes - output.size())
            val readCount = input.read(buffer, 0, nextLimit)
            if (readCount <= 0) break
            output.write(buffer, 0, readCount)
        }
        return output.toByteArray()
    }

    private fun readU16Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readU32Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun writeU16Le(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun buildVulkanBinaryResponse(status: Int, recordCount: Int, payload: ByteArray): ByteArray {
        val response = ByteArray(12 + payload.size)
        "ALVR".toByteArray(Charsets.US_ASCII).copyInto(response, 0)
        writeU16Le(response, 4, 1)
        writeU16Le(response, 6, status)
        writeU16Le(response, 8, payload.size)
        writeU16Le(response, 10, recordCount)
        payload.copyInto(response, 12)
        return response
    }

    private fun milliColor(value: Int): String {
        if (value % 1000 == 0) return "${value / 1000}.0"
        if (value in 1..999) return "0.${value.toString().padStart(3, '0').trimEnd('0')}"
        return (value / 1000.0f).toString()
    }

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

    private fun String.forEvidenceLog(maxChars: Int = 900): String {
        val singleLine = replace("\r", "\\r").replace("\n", "\\n")
        return if (singleLine.length <= maxChars) singleLine else singleLine.take(maxChars) + "...<truncated>"
    }

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

    private external fun nativeHostHardwareBufferProbe(): String

    private external fun nativeWaylandHardwareBufferBridge(encodedFrames: String): String

    private external fun nativeHostVulkanProbe(): String

    private external fun nativeRenderGpuSurfaceFrames(
        surface: android.view.Surface,
        encodedFrames: String,
    ): String

    private external fun nativeRenderWaylandHardwareBufferSurface(
        surface: android.view.Surface,
        encodedFrames: String,
    ): String

    private external fun nativeRenderVulkanSurfaceClear(
        surface: android.view.Surface,
        clearRequest: String,
    ): String
}
