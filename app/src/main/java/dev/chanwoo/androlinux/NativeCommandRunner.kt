package dev.chanwoo.androlinux

import java.io.File
import java.util.concurrent.TimeUnit

private const val COMMAND_TIMEOUT_SECONDS = 5L

data class NativeCommandResult(
    val command: File,
    val environment: Map<String, String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class NativeCommandRunner(
    private val nativeLibraryDir: File,
    private val prootTmpDir: File,
) {
    fun runSmokeTest(): NativeCommandResult = runPackagedCommand("libalr_test_command.so", listOf("smoke"))

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

    fun runProotRootfsProgramAsRoot(rootfsDir: File, program: String): NativeCommandResult =
        runProotRootfsCommand(rootfsDir, program, rootId = true)

    fun runProotRootfsIdAsRoot(rootfsDir: File): NativeCommandResult =
        runProotRootfsProgramAsRoot(rootfsDir, "/usr/bin/id")

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
    ): NativeCommandResult =
        runPackagedCommand(
            "libalr_proot.so",
            listOf("-R", rootfsDir.absolutePath) +
                (if (rootId) listOf("-0") else emptyList()) +
                listOf("-w", "/", program) + arguments,
            prootEnvironment(verbose = verbose, rootfsDir = rootfsDir, program = program),
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
            "PATH" to "/bin:/usr/bin:/usr/local/bin",
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
        val process = processBuilder.start()
        val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val exitCode = if (completed) {
            process.exitValue()
        } else {
            process.destroyForcibly()
            process.waitFor()
            -124
        }
        val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
        return NativeCommandResult(
            command = command,
            environment = environment.toSortedMap(),
            exitCode = exitCode,
            stdout = stdout,
            stderr = if (completed) stderr else listOf(stderr, "timeout after ${COMMAND_TIMEOUT_SECONDS}s").filter { it.isNotBlank() }.joinToString("\n"),
        )
    }
}
