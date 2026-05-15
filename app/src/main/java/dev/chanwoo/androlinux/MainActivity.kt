package dev.chanwoo.androlinux

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

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

        val report = nativeRuntimeReport(
            packageName,
            applicationInfo.nativeLibraryDir,
            filesDir.absolutePath,
            rootfsManifest.name,
            "/bin/bash",
        ) + "\n\nrootfs install dir: ${rootfsPlan.rootfsDir.absolutePath}" +
            "\nrootfs marker: ${rootfsPlan.markerPath.absolutePath}" +
            "\nrootfs status: ${rootfsStatus.manifestName}/${rootfsStatus.assetPath} verified=${rootfsStatus.verified}" +
            "\nrootfs staged archive: ${rootfsStatus.stagedArchive.absolutePath}"

        val view = TextView(this).apply {
            text = report
            textSize = 14f
            setPadding(32, 32, 32, 32)
        }
        setContentView(view)
    }

    private external fun nativeRuntimeReport(
        packageName: String,
        nativeLibraryDir: String,
        appFilesDir: String,
        rootfsName: String,
        program: String,
    ): String
}
