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
        val prootHelloResult = nativeCommandRunner.runProotRootfsProgram(rootfsStatus.rootfsDir, "/bin/hello")

        val executionSummary = "build: 0.3.1-termux-proot-pair" +
            "\nexecution summary" +
            "\nrootfs verified=${rootfsStatus.verified} extracted=${rootfsStatus.extracted}" +
            "\nnative smoke exit=${nativeCommandResult.exitCode}" +
            "\nnative smoke stdout=${nativeCommandResult.stdout}" +
            "\nproot loader=${prootCandidateResult.environment["PROOT_LOADER"]}" +
            "\nproot tmp=${prootCandidateResult.environment["PROOT_TMP_DIR"]}" +
            "\nproot verbose=${prootCandidateResult.environment["PROOT_VERBOSE"]}" +
            "\nproot --version exit=${prootCandidateResult.exitCode}" +
            "\nproot --version stdout=${prootCandidateResult.stdout}" +
            "\nproot --version stderr=${prootCandidateResult.stderr}" +
            "\nproot hello exit=${prootHelloResult.exitCode}" +
            "\nproot hello stdout=${prootHelloResult.stdout}" +
            "\nproot hello stderr=${prootHelloResult.stderr}"

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
            "\n\nproot backend candidate: packaged native executable" +
            "\nproot command: ${applicationInfo.nativeLibraryDir}/libalr_proot.so" +
            "\nproot actual env:" +
            prootCandidateResult.environment.entries.joinToString(separator = "") { "\n  ${it.key}=${it.value}" } +
            "\nproot candidate exit=${prootCandidateResult.exitCode}" +
            "\nproot candidate stdout=${prootCandidateResult.stdout}" +
            "\nproot candidate stderr=${prootCandidateResult.stderr}" +
            "\n\nproot hello exit=${prootHelloResult.exitCode}" +
            "\nproot hello stdout=${prootHelloResult.stdout}" +
            "\nproot hello stderr=${prootHelloResult.stderr}"

        val report = executionSummary + "\n\n--- verbose report ---\n" + verboseReport

        val view = TextView(this).apply {
            text = report
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
        }
        setContentView(ScrollView(this).apply { addView(view) })
    }

    private external fun nativeRuntimeReport(
        packageName: String,
        nativeLibraryDir: String,
        appFilesDir: String,
        appCacheDir: String,
        rootfsName: String,
        program: String,
    ): String
}
