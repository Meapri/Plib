package dev.chanwoo.androlinux

import java.io.File
import java.util.concurrent.TimeUnit

private const val COMMAND_TIMEOUT_SECONDS = 15L

data class NativeCommandResult(
    val command: File,
    val environment: Map<String, String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val elapsedMs: Long,
)

class NativeCommandRunner(
    private val nativeLibraryDir: File,
    private val prootTmpDir: File,
) {
    fun runSmokeTest(): NativeCommandResult = runPackagedCommand("libalr_test_command.so", listOf("smoke"))

    fun runNativeBionicForkBenchmark(repeatCount: Int = 10): NativeCommandResult =
        runPackagedCommand(
            "libalr_test_command.so",
            listOf("fork-benchmark", repeatCount.coerceIn(1, 200).toString()),
        )

    fun runAlrRuntimeTrampolinePreflight(rootfsDir: File, program: String): NativeCommandResult {
        return runAlrRuntimeTrampoline(rootfsDir, program, executeEntry = false)
    }

    fun runAlrRuntimeTrampolineEntryProbe(rootfsDir: File, program: String): NativeCommandResult {
        return runAlrRuntimeTrampoline(rootfsDir, program, executeEntry = true)
    }

    fun runAlrRuntimeTrampolineEntryBenchmark(rootfsDir: File, repeatCount: Int = 10): NativeCommandResult {
        return runAlrRuntimeTrampoline(
            rootfsDir,
            "/bin/hello",
            executeEntry = true,
            repeatCount = repeatCount,
        )
    }

    fun runAlrRuntimeTrampolineLoaderHelpProbe(rootfsDir: File): NativeCommandResult {
        return runAlrRuntimeTrampoline(
            rootfsDir,
            "/lib/ld-linux-aarch64.so.1",
            executeEntry = true,
            extraArgs = listOf("--help"),
            timeoutMs = 1500,
        )
    }

    fun runAlrRuntimeTrampolineGlibcHelloProbe(rootfsDir: File): NativeCommandResult {
        val libraryPath = glibcLibraryPath(rootfsDir)
        return runAlrRuntimeTrampoline(
            rootfsDir,
            "/lib/ld-linux-aarch64.so.1",
            executeEntry = true,
            extraArgs = listOf(
                "--argv0",
                "/bin/glibc-hello",
                "--library-path",
                libraryPath,
                translateGuestPath(rootfsDir, "/bin/glibc-hello"),
            ),
            timeoutMs = 1500,
        )
    }

    fun runAlrRuntimeTrampolineGlibcHelloBenchmark(rootfsDir: File, repeatCount: Int = 10): NativeCommandResult {
        val libraryPath = glibcLibraryPath(rootfsDir)
        return runAlrRuntimeTrampoline(
            rootfsDir,
            "/lib/ld-linux-aarch64.so.1",
            executeEntry = true,
            extraArgs = listOf(
                "--argv0",
                "/bin/glibc-hello",
                "--library-path",
                libraryPath,
                translateGuestPath(rootfsDir, "/bin/glibc-hello"),
            ),
            timeoutMs = 1500,
            repeatCount = repeatCount,
        )
    }

    fun runAlrRuntimeTrampolineGuestGlesShimSmoke(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(rootfsDir)

    fun runAlrRuntimeTrampolineGuestGlesAbiSmoke(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(rootfsDir, binaryPath = "/usr/bin/alr-gles-abi-smoke")

    fun runAlrRuntimeTrampolineGuestGlesDemoGears(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(
            rootfsDir,
            mapOf("ALR_GLES_DEMO_FRAME_COUNT" to frameCount.coerceIn(1, 240).toString()),
            timeoutMs = 4000,
            binaryPath = "/usr/bin/alr-gles-demo-gears",
        )

    fun runAlrRuntimeTrampolineGuestGlesProcaddrDemo(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(
            rootfsDir,
            mapOf("ALR_GLES_PROC_DEMO_FRAME_COUNT" to frameCount.coerceIn(1, 240).toString()),
            timeoutMs = 4000,
            binaryPath = "/usr/bin/alr-gles-procaddr-demo",
        )

    fun runAlrRuntimeTrampolineGuestGlesShimBenchmark(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(
            rootfsDir,
            mapOf(
                "ALR_GLES_SHIM_FRAME_COUNT" to frameCount.coerceIn(1, 120).toString(),
                "ALR_GLES_COMPAT_SUBMIT" to "0",
            ),
            timeoutMs = 3000,
        )

    fun runAlrRuntimeTrampolineGuestGlesShimDrawBenchmark(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(
            rootfsDir,
            mapOf(
                "ALR_GLES_SHIM_FRAME_COUNT" to "1",
                "ALR_GLES_DRAW_FRAME_COUNT" to frameCount.coerceIn(1, 120).toString(),
                "ALR_GLES_COMPAT_SUBMIT" to "0",
            ),
            timeoutMs = 3000,
        )

    private fun runAlrRuntimeTrampolineGuestGlesShim(
        rootfsDir: File,
        extraGuestEnvironment: Map<String, String> = emptyMap(),
        timeoutMs: Int = 1500,
        binaryPath: String = "/usr/bin/alr-gles-shim-smoke",
    ): NativeCommandResult {
        val libraryPath = glibcLibraryPath(rootfsDir) + ":" + File(rootfsDir, "usr/lib/androlinux").absolutePath
        return runAlrRuntimeTrampoline(
            rootfsDir,
            "/lib/ld-linux-aarch64.so.1",
            executeEntry = true,
            extraArgs = listOf(
                "--argv0",
                binaryPath,
                "--library-path",
                libraryPath,
                translateGuestPath(rootfsDir, binaryPath),
            ),
            timeoutMs = timeoutMs,
            extraGuestEnvironment = extraGuestEnvironment,
            pathRewrite = true,
            pathRewriteLimit = 16,
            pathRewriteIdleSyscallLimit = 16,
        )
    }

    fun runAlrRuntimeTrampolineCatOsReleaseProbe(rootfsDir: File): NativeCommandResult {
        return runAlrRuntimeTrampoline(
            rootfsDir,
            "/bin/cat",
            executeEntry = true,
            extraArgs = listOf(
                translateGuestPath(rootfsDir, "/etc/os-release"),
            ),
            timeoutMs = 1500,
        )
    }

    fun runAlrRuntimeTrampolineGuestGpuClient(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampoline(rootfsDir, "/usr/bin/alr-gpu-client", executeEntry = true, timeoutMs = 1500)

    fun runAlrRuntimeTrampolineGuestGpuClientIpc(rootfsDir: File, port: Int): NativeCommandResult =
        runAlrRuntimeTrampoline(
            rootfsDir,
            "/usr/bin/alr-gpu-client",
            executeEntry = true,
            timeoutMs = 1500,
            extraGuestEnvironment = mapOf(
                "ALR_GPU_BRIDGE_HOST" to "127.0.0.1",
                "ALR_GPU_BRIDGE_PORT" to port.toString(),
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback",
            ),
        )

    fun runAlrRuntimeTrampolineGuestGuiClient(rootfsDir: File, protocol: String): NativeCommandResult =
        runAlrRuntimeTrampoline(
            rootfsDir,
            if (protocol == "X11") "/usr/bin/alr-x11-gpu-client" else "/usr/bin/alr-wayland-gpu-client",
            executeEntry = true,
            timeoutMs = 1500,
        )

    fun runAlrRuntimeTrampolineGuestGuiClientIpc(rootfsDir: File, protocol: String, port: Int): NativeCommandResult =
        runAlrRuntimeTrampoline(
            rootfsDir,
            if (protocol == "X11") "/usr/bin/alr-x11-gpu-client" else "/usr/bin/alr-wayland-gpu-client",
            executeEntry = true,
            timeoutMs = 1500,
            extraGuestEnvironment = mapOf(
                "ALR_GUI_BRIDGE_HOST" to "127.0.0.1",
                "ALR_GUI_BRIDGE_PORT" to port.toString(),
                "ALR_GUI_BRIDGE_PROTOCOL" to protocol,
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback-gui",
            ),
        )

    fun runAlrRuntimeTrampolineInstalledPackageGuiClientIpc(rootfsDir: File, protocol: String, port: Int): NativeCommandResult =
        runAlrRuntimeTrampoline(
            rootfsDir,
            if (protocol == "X11") "/usr/local/bin/alr-package-x11-gpu-client" else "/usr/local/bin/alr-package-wayland-gpu-client",
            executeEntry = true,
            timeoutMs = 1500,
            extraGuestEnvironment = mapOf(
                "ALR_GUI_BRIDGE_HOST" to "127.0.0.1",
                "ALR_GUI_BRIDGE_PORT" to port.toString(),
                "ALR_GUI_BRIDGE_PROTOCOL" to protocol,
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback-gui",
            ),
        )

    fun runAlrRuntimeTrampolineInstalledPackageVulkanDiscovery(rootfsDir: File, port: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/local/bin/alr-package-vulkan-discovery-client",
            emptyList(),
            timeoutMs = 3000,
            extraGuestEnvironment = mapOf(
                "ALR_VK_BRIDGE_HOST" to "127.0.0.1",
                "ALR_VK_BRIDGE_PORT" to port.toString(),
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback-vulkan",
            ),
        )

    fun runAlrRuntimeTrampolineInstalledPackageVulkanProxySmoke(rootfsDir: File, port: Int): NativeCommandResult {
        val program = "/usr/local/bin/alr-package-vulkan-proxy-smoke"
        val libraryPath = glibcLibraryPath(rootfsDir) + ":" + File(rootfsDir, "usr/lib/androlinux").absolutePath
        return runAlrRuntimeTrampoline(
            rootfsDir,
            "/lib/ld-linux-aarch64.so.1",
            executeEntry = true,
            extraArgs = listOf(
                "--argv0",
                program,
                "--library-path",
                libraryPath,
                translateGuestPath(rootfsDir, program),
            ),
            timeoutMs = 3000,
            extraGuestEnvironment = mapOf(
                "ALR_VK_BRIDGE_HOST" to "127.0.0.1",
                "ALR_VK_BRIDGE_PORT" to port.toString(),
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback-vulkan-proxy",
            ),
            pathRewrite = true,
            pathRewriteLimit = 16,
            pathRewriteIdleSyscallLimit = 16,
        )
    }

    fun runAlrRuntimeTrampolineDpkgVersion(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(rootfsDir, "/usr/bin/dpkg", listOf("--version"))

    fun runAlrRuntimeTrampolineDpkgPrintArchitecture(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(rootfsDir, "/usr/bin/dpkg", listOf("--print-architecture"))

    fun runAlrRuntimeTrampolineDpkgPrintArchitecturePreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/dpkg",
            listOf("--print-architecture"),
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineShellDpkgPrintArchitecturePreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/bin/dash",
            listOf("-c", "dpkg --print-architecture"),
            timeoutMs = 8000,
            pathRewrite = true,
            pathRewriteLimit = 2048,
            pathRewriteIdleSyscallLimit = 256,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineDpkgQueryVersion(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(rootfsDir, "/usr/bin/dpkg-query", listOf("--version"))

    fun runAlrRuntimeTrampolineAptVersion(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(rootfsDir, "/usr/bin/apt", listOf("--version"), timeoutMs = 5000)

    fun runAlrRuntimeTrampolineAptVersionPreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/apt",
            listOf("--version"),
            timeoutMs = 5000,
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineAptGetVersion(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(rootfsDir, "/usr/bin/apt-get", listOf("--version"), timeoutMs = 5000)

    fun runAlrRuntimeTrampolineAptGetVersionPreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/apt-get",
            listOf("--version"),
            timeoutMs = 5000,
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineAptCacheVersion(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(rootfsDir, "/usr/bin/apt-cache", listOf("--version"), timeoutMs = 5000)

    fun runAlrRuntimeTrampolineAptCacheVersionPreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/apt-cache",
            listOf("--version"),
            timeoutMs = 5000,
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineAptCachePolicyPreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/apt-cache",
            listOf("policy"),
            timeoutMs = 5000,
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineAptCacheStatsPreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/apt-cache",
            listOf("stats"),
            timeoutMs = 5000,
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineAptCachePkgNamesPreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/apt-cache",
            listOf("pkgnames"),
            timeoutMs = 5000,
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineAptConfigVersion(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(rootfsDir, "/usr/bin/apt-config", listOf("--version"), timeoutMs = 5000)

    fun runAlrRuntimeTrampolineAptConfigVersionPreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/apt-config",
            listOf("--version"),
            timeoutMs = 5000,
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineSyscallBench(rootfsDir: File, mode: String, count: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/alr-syscall-bench",
            listOf(mode, count.coerceIn(1, 10000).toString()),
            timeoutMs = 8000,
            pathRewriteLimit = 12000,
            pathRewriteIdleSyscallLimit = 1024,
        )

    fun runAlrRuntimeTrampolineSyscallBenchPreload(rootfsDir: File, mode: String, count: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/alr-syscall-bench",
            listOf(mode, count.coerceIn(1, 10000).toString()),
            timeoutMs = 8000,
            pathRewrite = false,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineDpkgInstallLocalSmoke(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/dpkg",
            listOf("-i", "/var/cache/apt/archives/alr-smoke_1.0_arm64.deb"),
            timeoutMs = 8000,
            virtualRootIdentity = true,
            pathRewriteLimit = 1024,
            pathRewriteIdleSyscallLimit = 256,
        )

    fun runAlrRuntimeTrampolineDpkgInstallLocalSmokePreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/usr/bin/dpkg",
            listOf("-i", "/var/cache/apt/archives/alr-smoke_1.0_arm64.deb"),
            timeoutMs = 10000,
            virtualRootIdentity = true,
            pathRewrite = true,
            pathRewriteLimit = 4096,
            pathRewriteIdleSyscallLimit = 512,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir) + mapOf(
                "ALR_PRELOAD_FAKE_ROOT" to "1",
            ),
        )

    fun runAlrRuntimeTrampolineInstalledPackageSmokePreload(rootfsDir: File): NativeCommandResult =
        runAlrRuntimeTrampolineGlibcRootfsProgram(
            rootfsDir,
            "/bin/dash",
            listOf("/usr/local/bin/alr-package-smoke"),
            timeoutMs = 8000,
            pathRewrite = true,
            pathRewriteLimit = 2048,
            pathRewriteIdleSyscallLimit = 256,
            extraGuestEnvironment = preloadPathFastPathEnvironment(rootfsDir),
        )

    fun runAlrRuntimeTrampolineInstalledPackageGpuSmoke(rootfsDir: File, port: Int): NativeCommandResult =
        runAlrRuntimeTrampoline(
            rootfsDir,
            "/usr/local/bin/alr-package-gpu-smoke",
            executeEntry = true,
            timeoutMs = 1500,
            extraGuestEnvironment = mapOf(
                "ALR_GPU_BRIDGE_HOST" to "127.0.0.1",
                "ALR_GPU_BRIDGE_PORT" to port.toString(),
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback",
            ),
        )

    fun runAlrRuntimeTrampolineInstalledPackageGlesDemo(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(
            rootfsDir,
            mapOf("ALR_GLES_DEMO_FRAME_COUNT" to frameCount.coerceIn(1, 240).toString()),
            timeoutMs = 4000,
            binaryPath = "/usr/local/bin/alr-package-gles-demo",
        )

    fun runAlrRuntimeTrampolineInstalledPackageGlesProcaddrDemo(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(
            rootfsDir,
            mapOf("ALR_GLES_PROC_DEMO_FRAME_COUNT" to frameCount.coerceIn(1, 240).toString()),
            timeoutMs = 4000,
            binaryPath = "/usr/local/bin/alr-package-gles-procaddr-demo",
        )

    fun runAlrRuntimeTrampolineInstalledPackageGlesDemoIpc(rootfsDir: File, frameCount: Int, port: Int): NativeCommandResult =
        runAlrRuntimeTrampolineGuestGlesShim(
            rootfsDir,
            mapOf(
                "ALR_GLES_DEMO_FRAME_COUNT" to frameCount.coerceIn(1, 240).toString(),
                "ALR_GPU_BRIDGE_HOST" to "127.0.0.1",
                "ALR_GPU_BRIDGE_PORT" to port.toString(),
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback-gles-shim",
                "ALR_GPU_BRIDGE_ACK" to "1",
            ),
            timeoutMs = 12000,
            binaryPath = "/usr/local/bin/alr-package-gles-demo",
        )

    private fun glibcLibraryPath(rootfsDir: File): String =
        listOf(
            File(rootfsDir, "lib/aarch64-linux-gnu").absolutePath,
            File(rootfsDir, "usr/lib/aarch64-linux-gnu").absolutePath,
            File(rootfsDir, "lib").absolutePath,
            File(rootfsDir, "usr/lib").absolutePath,
        ).joinToString(":")

    private fun translateGuestPath(rootfsDir: File, guestPath: String): String {
        require(guestPath.startsWith("/")) { "ALR guest path must be absolute" }
        require(!guestPath.split('/').any { it == ".." }) { "ALR guest path must not contain .." }
        return File(rootfsDir, guestPath.removePrefix("/")).absolutePath
    }

    private fun preloadPathFastPathEnvironment(rootfsDir: File): Map<String, String> =
        mapOf(
            "ALR_ROOTFS" to rootfsDir.absolutePath,
            "LD_PRELOAD" to translateGuestPath(rootfsDir, "/usr/lib/androlinux/libalr_path_preload.so"),
        )

    private fun runAlrRuntimeTrampolineGlibcRootfsProgram(
        rootfsDir: File,
        program: String,
        arguments: List<String>,
        timeoutMs: Int = 5000,
        virtualRootIdentity: Boolean = false,
        pathRewrite: Boolean = true,
        pathRewriteLimit: Int = 128,
        pathRewriteIdleSyscallLimit: Int = 32,
        extraGuestEnvironment: Map<String, String> = emptyMap(),
    ): NativeCommandResult {
        val libraryPath = glibcLibraryPath(rootfsDir)
        return runAlrRuntimeTrampoline(
            rootfsDir,
            "/lib/ld-linux-aarch64.so.1",
            executeEntry = true,
            extraArgs = listOf(
                "--argv0",
                program,
                "--library-path",
                libraryPath,
                translateGuestPath(rootfsDir, program),
            ) + arguments,
            timeoutMs = timeoutMs,
            extraGuestEnvironment = extraGuestEnvironment,
            pathRewrite = pathRewrite,
            pathRewriteLimit = pathRewriteLimit,
            pathRewriteIdleSyscallLimit = pathRewriteIdleSyscallLimit,
            virtualRootIdentity = virtualRootIdentity,
        )
    }

    private fun runAlrRuntimeTrampoline(
        rootfsDir: File,
        program: String,
        executeEntry: Boolean,
        extraArgs: List<String> = emptyList(),
        extraGuestEnvironment: Map<String, String> = emptyMap(),
        timeoutMs: Int = 1000,
        repeatCount: Int = 1,
        pathRewrite: Boolean = false,
        pathRewriteLimit: Int = 0,
        pathRewriteIdleSyscallLimit: Int = 0,
        virtualRootIdentity: Boolean = false,
    ): NativeCommandResult {
        require(program.startsWith("/")) { "ALR trampoline program must be an absolute guest path" }
        require(!program.split('/').any { it == ".." }) { "ALR trampoline program must not contain .." }
        val targetHost = File(rootfsDir, program.removePrefix("/"))
        val mode = if (executeEntry) "entry-probe" else "preflight"
        val extraArgEnv = buildMap {
            put("ALR_TRAMPOLINE_EXTRA_ARG_COUNT", extraArgs.size.coerceAtMost(16).toString())
            extraArgs.take(16).forEachIndexed { index, value ->
                put("ALR_TRAMPOLINE_EXTRA_ARG_$index", value)
            }
            val guestEnv = extraGuestEnvironment.entries
                .filter { (key, _) -> key.isNotBlank() && !key.contains("=") }
                .take(16)
                .map { (key, value) -> "$key=$value" }
            put("ALR_TRAMPOLINE_EXTRA_ENV_COUNT", guestEnv.size.toString())
            guestEnv.forEachIndexed { index, value ->
                put("ALR_TRAMPOLINE_EXTRA_ENV_$index", value)
            }
        }
        return runPackagedCommand(
            "libalr_runtime_trampoline.so",
            listOf("--preflight"),
            mapOf(
                "ALR_ROOTFS" to rootfsDir.absolutePath,
                "ALR_PROGRAM" to program,
                "ALR_TRAMPOLINE_TARGET_GUEST_PATH" to program,
                "ALR_TRAMPOLINE_TARGET_HOST_PATH" to targetHost.absolutePath,
                "ALR_TRAMPOLINE_MODE" to mode,
                "ALR_TRAMPOLINE_EXECUTE_ENTRY" to if (executeEntry) "1" else "0",
                "ALR_TRAMPOLINE_HANDOFF_TIMEOUT_MS" to timeoutMs.toString(),
                "ALR_TRAMPOLINE_REPEAT_COUNT" to repeatCount.coerceIn(1, 50).toString(),
                "ALR_TRAMPOLINE_PATH_REWRITE" to if (pathRewrite) "1" else "0",
                "ALR_TRAMPOLINE_PATH_REWRITE_LIMIT" to pathRewriteLimit.coerceAtLeast(0).toString(),
                "ALR_TRAMPOLINE_PATH_REWRITE_IDLE_SYSCALL_LIMIT" to pathRewriteIdleSyscallLimit.coerceAtLeast(0).toString(),
                "ALR_TRAMPOLINE_VIRTUAL_ROOT_IDENTITY" to if (virtualRootIdentity) "1" else "0",
                "ALR_TRAMPOLINE_EXEC_LOADER_PATH" to File(nativeLibraryDir, "libalr_glibc_loader.so").absolutePath,
                "LD_LIBRARY_PATH" to nativeLibraryDir.absolutePath,
                "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            ) + extraArgEnv,
        )
    }

    fun runProotCandidateSmokeTest(): NativeCommandResult =
        runPackagedCommand("libalr_proot.so", listOf("--version"), prootEnvironment())

    fun runProotHelpProbe(): NativeCommandResult =
        runPackagedCommand("libalr_proot.so", listOf("--help"), prootEnvironment())

    fun runProotShortVersionProbe(): NativeCommandResult =
        runPackagedCommand("libalr_proot.so", listOf("-V"), prootEnvironment())

    fun runProotNoEnvVersionProbe(): NativeCommandResult =
        runPackagedCommand("libalr_proot.so", listOf("--version"))

    fun runProotViaLinkerVersionProbe(): NativeCommandResult =
        runAbsoluteCommand(
            File("/system/bin/linker64"),
            listOf(File(nativeLibraryDir, "libalr_proot.so").absolutePath, "--version"),
            prootEnvironment(),
        )

    fun runProotLoaderDirectProbe(): NativeCommandResult =
        runPackagedCommand("libproot-loader.so", emptyList(), prootEnvironment())

    fun runTallocViaLinkerProbe(): NativeCommandResult =
        runAbsoluteCommand(
            File("/system/bin/linker64"),
            listOf(File(nativeLibraryDir, "libtalloc.so").absolutePath),
            mapOf("LD_LIBRARY_PATH" to nativeLibraryDir.absolutePath),
        )

    fun runProotRootfsProgram(rootfsDir: File, program: String): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, program)

    fun runProotRootfsHelloLoopBenchmark(rootfsDir: File, repeatCount: Int = 10): NativeCommandResult {
        val clampedRepeatCount = repeatCount.coerceIn(1, 50)
        val loopItems = (1..clampedRepeatCount).joinToString(" ")
        return runProotRootfsShell(
            rootfsDir,
            "for i in $loopItems; do /bin/hello >/dev/null || exit 1; done; echo proot-loop-ok count=$clampedRepeatCount",
        )
    }

    fun runProotRootfsGlibcHelloLoopBenchmark(rootfsDir: File, repeatCount: Int = 10): NativeCommandResult {
        val clampedRepeatCount = repeatCount.coerceIn(1, 50)
        val loopItems = (1..clampedRepeatCount).joinToString(" ")
        return runProotRootfsShell(
            rootfsDir,
            "for i in $loopItems; do /bin/glibc-hello >/dev/null || exit 1; done; echo proot-glibc-loop-ok count=$clampedRepeatCount",
        )
    }

    fun runProotRootfsProgramAsRoot(rootfsDir: File, program: String): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, program, rootId = true)

    fun runProotRootfsIdAsRoot(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/id", rootId = true, rawRootfs = true)

    fun runProotRootfsDpkgVersion(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/dpkg", listOf("--version"), rootId = true, rawRootfs = true)

    fun runProotRootfsDpkgPrintArchitecture(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/dpkg", listOf("--print-architecture"), rootId = true, rawRootfs = true)

    fun runProotRootfsDpkgQueryVersion(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/dpkg-query", listOf("--version"), rootId = true, rawRootfs = true)

    fun runProotRootfsDpkgSplitVersion(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/dpkg-split", listOf("--version"), rootId = true, rawRootfs = true)

    fun runProotRootfsAptVersion(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/apt", listOf("--version"), rootId = true, rawRootfs = true)

    fun runProotRootfsAptGetVersion(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/apt-get", listOf("--version"), rootId = true, rawRootfs = true)

    fun runProotRootfsAptCacheVersion(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/apt-cache", listOf("--version"), rootId = true, rawRootfs = true)

    fun runProotRootfsAptConfigVersion(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/apt-config", listOf("--version"), rootId = true, rawRootfs = true)

    fun runProotRootfsSyscallBench(rootfsDir: File, mode: String, count: Int): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            "/usr/bin/alr-syscall-bench",
            listOf(mode, count.coerceIn(1, 10000).toString()),
            rootId = true,
            rawRootfs = true,
        )

    fun runProotRootfsDpkgInstallLocalSmoke(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            "/usr/bin/dpkg",
            listOf("-i", "/var/cache/apt/archives/alr-smoke_1.0_arm64.deb"),
            rootId = true,
            rawRootfs = true,
            binds = minimalPackageManagerBinds(),
        )

    fun runProotRootfsInstalledPackageSmoke(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            "/usr/local/bin/alr-package-smoke",
            rootId = true,
            rawRootfs = true,
            binds = minimalPackageManagerBinds(),
        )

    fun runProotRootfsGuestGpuClient(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/alr-gpu-client", rootId = true, rawRootfs = true)

    fun runProotRootfsGuestGpuClientIpc(rootfsDir: File, port: Int): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            "/usr/bin/alr-gpu-client",
            rootId = true,
            rawRootfs = true,
            extraEnvironment = mapOf(
                "ALR_GPU_BRIDGE_HOST" to "127.0.0.1",
                "ALR_GPU_BRIDGE_PORT" to port.toString(),
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback",
            ),
        )

    fun runProotRootfsGuestGlesShimSmoke(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/alr-gles-shim-smoke", rootId = true, rawRootfs = true)

    fun runProotRootfsGuestGlesAbiSmoke(rootfsDir: File): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/usr/bin/alr-gles-abi-smoke", rootId = true, rawRootfs = true)

    fun runProotRootfsGuestGlesDemoGears(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            "/usr/bin/alr-gles-demo-gears",
            rootId = true,
            rawRootfs = true,
            extraEnvironment = mapOf("ALR_GLES_DEMO_FRAME_COUNT" to frameCount.coerceIn(1, 240).toString()),
        )

    fun runProotRootfsGuestGlesProcaddrDemo(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            "/usr/bin/alr-gles-procaddr-demo",
            rootId = true,
            rawRootfs = true,
            extraEnvironment = mapOf("ALR_GLES_PROC_DEMO_FRAME_COUNT" to frameCount.coerceIn(1, 240).toString()),
        )

    fun runProotRootfsGuestGlesShimBenchmark(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            "/usr/bin/alr-gles-shim-smoke",
            rootId = true,
            rawRootfs = true,
            extraEnvironment = mapOf(
                "ALR_GLES_SHIM_FRAME_COUNT" to frameCount.coerceIn(1, 120).toString(),
                "ALR_GLES_COMPAT_SUBMIT" to "0",
            ),
        )

    fun runProotRootfsGuestGlesShimDrawBenchmark(rootfsDir: File, frameCount: Int): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            "/usr/bin/alr-gles-shim-smoke",
            rootId = true,
            rawRootfs = true,
            extraEnvironment = mapOf(
                "ALR_GLES_SHIM_FRAME_COUNT" to "1",
                "ALR_GLES_DRAW_FRAME_COUNT" to frameCount.coerceIn(1, 120).toString(),
                "ALR_GLES_COMPAT_SUBMIT" to "0",
            ),
        )

    fun runProotRootfsGuestGuiClient(rootfsDir: File, protocol: String): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            if (protocol == "X11") "/usr/bin/alr-x11-gpu-client" else "/usr/bin/alr-wayland-gpu-client",
            rootId = true,
            rawRootfs = true,
        )

    fun runProotRootfsGuestGuiClientIpc(rootfsDir: File, protocol: String, port: Int): NativeCommandResult =
        runProotRootfsCommand(
            rootfsDir,
            if (protocol == "X11") "/usr/bin/alr-x11-gpu-client" else "/usr/bin/alr-wayland-gpu-client",
            rootId = true,
            rawRootfs = true,
            extraEnvironment = mapOf(
                "ALR_GUI_BRIDGE_HOST" to "127.0.0.1",
                "ALR_GUI_BRIDGE_PORT" to port.toString(),
                "ALR_GUI_BRIDGE_PROTOCOL" to protocol,
                "ALR_GPU_BRIDGE_TRANSPORT" to "tcp-loopback-gui",
            ),
        )

    fun runProotRootfsProgramVerbose(rootfsDir: File, program: String): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, program, verbose = "9")

    fun runProotRootfsShell(rootfsDir: File, command: String): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/bin/sh", listOf("-c", command))

    fun runProotRootfsDash(rootfsDir: File, command: String): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, "/bin/dash", listOf("-c", command))

    private fun runProotRootfsCommand(
        rootfsDir: File,
        program: String,
        arguments: List<String> = emptyList(),
        verbose: String = "-1",
        rootId: Boolean = false,
        rawRootfs: Boolean = false,
        binds: List<String> = emptyList(),
        extraEnvironment: Map<String, String> = emptyMap(),
    ): NativeCommandResult =
        runPackagedCommand(
            "libalr_proot.so",
            listOf(if (rawRootfs) "-r" else "-R", rootfsDir.absolutePath) +
                binds.flatMap { listOf("-b", it) } +
                (if (rootId) listOf("-0") else emptyList()) +
                listOf("-w", "/", program) + arguments,
            prootEnvironment(verbose = verbose, rootfsDir = rootfsDir, program = program) + extraEnvironment,
        )

    private fun minimalPackageManagerBinds(): List<String> = listOf(
        "/dev/null:/dev/null",
        "/dev/zero:/dev/zero",
        "/dev/urandom:/dev/urandom",
    )

    private fun prootEnvironment(
        verbose: String = "-1",
        rootfsDir: File? = null,
        program: String? = null,
    ): Map<String, String> {
        prootTmpDir.mkdirs()
        val environment = mutableMapOf(
            "PROOT_LOADER" to File(nativeLibraryDir, "libproot-loader.so").absolutePath,
            "PROOT_TMP_DIR" to prootTmpDir.absolutePath,
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_VERBOSE" to verbose,
            "LD_LIBRARY_PATH" to nativeLibraryDir.absolutePath,
            "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
            "HOME" to "/root",
            "TMPDIR" to "/tmp",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        )
        if (rootfsDir != null) {
            environment["ALR_ROOTFS"] = rootfsDir.absolutePath
        }
        if (program != null) {
            environment["ALR_PROGRAM"] = program
        }
        return environment
    }

    private fun runPackagedCommand(
        fileName: String,
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): NativeCommandResult = runAbsoluteCommand(File(nativeLibraryDir, fileName), arguments, environment)

    private fun runAbsoluteCommand(
        command: File,
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): NativeCommandResult {
        val processBuilder = ProcessBuilder(listOf(command.absolutePath) + arguments)
            .redirectErrorStream(false)
        processBuilder.environment().clear()
        processBuilder.environment().putAll(environment)
        val startedNs = System.nanoTime()
        val process = processBuilder.start()
        val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val exitCode = if (completed) {
            process.exitValue()
        } else {
            process.destroyForcibly()
            process.waitFor()
            -124
        }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
        val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
        return NativeCommandResult(
            command = command,
            environment = environment.toSortedMap(),
            exitCode = exitCode,
            stdout = stdout,
            stderr = if (completed) stderr else listOf(stderr, "timeout after ${COMMAND_TIMEOUT_SECONDS}s").filter { it.isNotBlank() }.joinToString("\n"),
            elapsedMs = elapsedMs,
        )
    }
}
