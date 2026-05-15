package dev.chanwoo.androlinux

import java.io.File

data class NativeCommandResult(
    val command: File,
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

    fun runProotRootfsProgram(rootfsDir: File, program: String): NativeCommandResult =
        runPackagedCommand(
            "libalr_proot.so",
            listOf("-R", rootfsDir.absolutePath, "-w", "/root", program),
            prootEnvironment(),
        )

    private fun prootEnvironment(): Map<String, String> {
        prootTmpDir.mkdirs()
        return mapOf(
            "PROOT_LOADER" to File(nativeLibraryDir, "libproot-loader.so").absolutePath,
            "PROOT_TMP_DIR" to prootTmpDir.absolutePath,
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_VERBOSE" to "9",
        )
    }

    private fun runPackagedCommand(
        fileName: String,
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): NativeCommandResult {
        val command = File(nativeLibraryDir, fileName)
        val processBuilder = ProcessBuilder(listOf(command.absolutePath) + arguments)
            .redirectErrorStream(false)
        processBuilder.environment().putAll(environment)
        val process = processBuilder.start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        return NativeCommandResult(
            command = command,
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
        )
    }
}
