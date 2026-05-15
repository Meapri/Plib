package dev.chanwoo.androlinux

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class RootfsInstallStatus(
    val manifestName: String,
    val assetPath: String,
    val verified: Boolean,
    val stagedArchive: File,
)

class RootfsInstaller(private val context: Context) {
    fun prepareBundledTinyRootfs(): RootfsInstallStatus {
        val manifestText = context.assets
            .open("rootfs/manifests/debian-arm64-bookworm-slim.json")
            .bufferedReader()
            .use { it.readText() }
        val manifestJson = JSONObject(manifestText)
        val assetJson = manifestJson.getJSONArray("assets").getJSONObject(0)
        val manifest = RootfsManifest(
            name = manifestJson.getString("name"),
            version = manifestJson.getString("version"),
            assets = listOf(
                RootfsAsset(
                    path = assetJson.getString("path"),
                    sha256 = assetJson.getString("sha256"),
                    sizeBytes = assetJson.getLong("size_bytes"),
                ),
            ),
        )
        val plan = buildRootfsInstallPlan(manifest, context.filesDir)
        val asset = manifest.assets.first()
        val stagedArchive = plan.assetDestinations.getValue(asset.path)
        stagedArchive.parentFile?.mkdirs()
        context.assets.open("rootfs/payloads/${asset.path}").use { input ->
            stagedArchive.outputStream().use { output -> input.copyTo(output) }
        }
        return RootfsInstallStatus(
            manifestName = manifest.name,
            assetPath = asset.path,
            verified = verifyAsset(stagedArchive, asset),
            stagedArchive = stagedArchive,
        )
    }

    fun verifyAsset(file: File, asset: RootfsAsset): Boolean {
        if (!file.isFile || file.length() != asset.sizeBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) } == asset.sha256.lowercase()
    }
}
