package dev.chanwoo.androlinux

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("alr_loader")

        val rootfsManifest = RootfsManifest(
            name = "debian-arm64",
            version = "bookworm-slim-2026-05",
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
        val prootHelloVerboseResult = if (prootHelloResult.exitCode == 0) {
            null
        } else {
            nativeCommandRunner.runProotRootfsProgramVerbose(rootfsStatus.rootfsDir, "/bin/hello")
        }
        val nativeProbe = nativeLibraryProbe(applicationInfo.nativeLibraryDir)
        val rootfsHelloFile = File(rootfsStatus.rootfsDir, "bin/hello")
        val rootfsShellFile = File(rootfsStatus.rootfsDir, "bin/sh")
        val rootfsExecutionPassed = prootHelloResult.exitCode == 0 &&
            prootHelloResult.stdout.contains("hello from static arm64 rootfs")

        val executionSummary = "build: 0.3.5-rootfs-exec-success" +
            "\nexecution summary" +
            "\nROOTFS EXECUTION: ${if (rootfsExecutionPassed) "PASS" else "FAIL"}" +
            "\nrootfs verified=${rootfsStatus.verified} extracted=${rootfsStatus.extracted}" +
            "\nrootfs /bin/hello exists=${rootfsHelloFile.isFile} executable=${rootfsHelloFile.canExecute()} bytes=${rootfsHelloFile.length()}" +
            "\nrootfs /bin/sh exists=${rootfsShellFile.exists()} static-hello-needs-shell=false" +
            "\nnative smoke exit=${nativeCommandResult.exitCode}" +
            "\nnative smoke stdout=${nativeCommandResult.stdout}" +
            "\nproot --version exit=${prootCandidateResult.exitCode}" +
            "\nlinker64 proot --version exit=${prootViaLinkerResult.exitCode}" +
            "\nproot hello quiet exit=${prootHelloResult.exitCode}" +
            "\nproot hello quiet stdout=${prootHelloResult.stdout}" +
            "\nproot hello quiet stderr=${prootHelloResult.stderr}" +
            "\nprobe dlopen talloc=${nativeProbe.lineStartingWith("dlopen libtalloc.so")}" +
            "\nprobe dlopen proot=${nativeProbe.lineStartingWith("dlopen libalr_proot.so")}" +
            "\nproot loader=${prootCandidateResult.environment["PROOT_LOADER"]}" +
            "\nproot tmp=${prootCandidateResult.environment["PROOT_TMP_DIR"]}" +
            "\nproot verbose=${prootCandidateResult.environment["PROOT_VERBOSE"]}"

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
            optionalResultBlock("proot hello verbose on failure", prootHelloVerboseResult)

        val report = executionSummary + "\n\n--- verbose report ---\n" + verboseReport

        val view = TextView(this).apply {
            text = report
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
        }
        setContentView(ScrollView(this).apply { addView(view) })
    }

    private fun resultBlock(label: String, result: NativeCommandResult): String =
        "\n\n$label command=${result.command.absolutePath}" +
            "\n$label exit=${result.exitCode}" +
            "\n$label stdout=${result.stdout}" +
            "\n$label stderr=${result.stderr}"

    private fun optionalResultBlock(label: String, result: NativeCommandResult?): String =
        result?.let { resultBlock(label, it) } ?: "\n\n$label skipped=quiet rootfs execution passed"

    private fun String.lineStartingWith(prefix: String): String =
        lineSequence().firstOrNull { it.startsWith(prefix) } ?: "missing"

    private external fun nativeRuntimeReport(
        packageName: String,
        nativeLibraryDir: String,
        appFilesDir: String,
        appCacheDir: String,
        rootfsName: String,
        program: String,
    ): String

    private external fun nativeLibraryProbe(nativeLibraryDir: String): String
}
