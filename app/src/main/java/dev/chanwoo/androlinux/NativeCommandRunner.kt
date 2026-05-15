package dev.chanwoo.androlinux

import java.io.File
import java.util.concurrent.TimeUnit

private const val COMMAND_TIMEOUT_SECONDS = 3L

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
        runPackagedCommand(
            "libalr_proot.so",
            listOf("-R", rootfsDir.absolutePath, "-w", "/", program),
            prootEnvironment(verbose = "9"),
        )

    fun runProotRootfsProgramQuiet(rootfsDir: File, program: String): NativeCommandResult =
        runPackagedCommand(
            "libalr_proot.so",
            listOf("-R", rootfsDir.absolutePath, "-w", "/", program),
            prootEnvironment(verbose = "-1"),
        )

    private fun prootEnvironment(verbose: String = "9"): Map<String, String> {
        prootTmpDir.mkdirs()
        return mapOf(
            "PROOT_LOADER" to File(nativeLibraryDir, "libproot-loader.so").absolutePath,
            "PROOT_TMP_DIR" to prootTmpDir.absolutePath,
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_VERBOSE" to verbose,
            "LD_LIBRARY_PATH" to nativeLibraryDir.absolutePath,
        )
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
        processBuilder.environment().remove("LD_PRELOAD")
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
