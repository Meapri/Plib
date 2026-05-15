package dev.chanwoo.androlinux

import java.io.File

data class NativeCommandResult(
    val command: File,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class NativeCommandRunner(private val nativeLibraryDir: File) {
    fun runSmokeTest(): NativeCommandResult = runPackagedCommand("libalr_test_command.so", "smoke")

    fun runProotCandidateSmokeTest(): NativeCommandResult =
        runPackagedCommand("libalr_proot.so", "--version")

    private fun runPackagedCommand(fileName: String, argument: String): NativeCommandResult {
        val command = File(nativeLibraryDir, fileName)
        val process = ProcessBuilder(command.absolutePath, argument)
            .redirectErrorStream(false)
            .start()
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
