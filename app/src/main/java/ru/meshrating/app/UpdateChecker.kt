package ru.meshrating.app

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Найденное обновление на GitHub Releases. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseUrl: String
)

/**
 * Проверка обновлений через GitHub Releases.
 *
 * Версия в теге имеет вид `v1.0.<epochSeconds>`, а versionCode релизной сборки
 * равен тому же числу. Поэтому сравнение versionCode корректно и не зависит от
 * строкового versionName.
 */
object UpdateChecker {

    const val CHECK_INTERVAL_MS = 60L * 60L * 1000L

    private const val LATEST_RELEASE_API = "https://api.github.com/repos/mesh-ranker/app/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/mesh-ranker/app/releases"

    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    /** Возвращает обновление, если на GitHub лежит релиз новее установленного. */
    fun check(context: Context): UpdateInfo? {
        return try {
            val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MESH-Ranker-Android")
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").trim()
                val remoteCode = tag.substringAfterLast('.').toLongOrNull() ?: return null
                if (remoteCode <= installedVersionCode(context)) return null

                val releaseUrl = json.optString("html_url")
                    .takeIf { it.isNotBlank() }
                    ?: RELEASES_PAGE
                val apkUrl = firstApkUrl(json) ?: releaseUrl
                UpdateInfo(
                    versionName = tag.removePrefix("v").ifBlank { "новая версия" },
                    versionCode = remoteCode,
                    downloadUrl = apkUrl,
                    releaseUrl = releaseUrl
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun firstApkUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank()) return url
            }
        }
        return null
    }
}
