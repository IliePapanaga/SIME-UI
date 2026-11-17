package md.sime.agenda.notifications

import android.content.Context
import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import md.sime.agenda.model.AppUpdateInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

object AppUpdateChecker {

    const val APK_LATEST_URL = "https://github.com/IliePapanaga/SIME-UI/releases/latest/download/Agenda.apk"
    private const val VERSION_JSON_URL = "https://github.com/IliePapanaga/SIME-UI/releases/latest/download/version.json"
    private const val GITHUB_RELEASE_API = "https://api.github.com/repos/IliePapanaga/SIME-UI/releases/latest"

    private const val PREFS_NAME = "sime_update_prefs"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version_code"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(context: Context): PackageInfo? {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }

    fun getInstalledVersion(context: Context): Pair<Int, String> {
        val pkg = getPackageInfo(context) ?: return 1 to "1.0.0"
        val code = PackageInfoCompat.getLongVersionCode(pkg).toInt()
        val name = pkg.versionName ?: "1.0.0"
        return code to name
    }

    /**
     * Checks GitHub Releases once via version.json (and falls back to GitHub Releases API
     * asset timestamps only if version.json is not present).
     */
    suspend fun checkForUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val pkg = getPackageInfo(context) ?: return@withContext null
        val installedCode = PackageInfoCompat.getLongVersionCode(pkg).toInt()
        val installedUpdateTime = pkg.lastUpdateTime

        val versionCheckResult: Pair<Boolean, AppUpdateInfo?> = runCatching {
            val req = Request.Builder()
                .url("$VERSION_JSON_URL?t=${System.currentTimeMillis()}")
                .header("User-Agent", "Agenda-Android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false to null
                val body = resp.body?.string()?.takeIf { it.isNotBlank() }
                    ?: return@runCatching false to null
                val json = JSONObject(body)
                val remoteCode = json.optInt("versionCode", -1)
                if (remoteCode <= 0) return@runCatching false to null
                val remoteName = json.optString("versionName", "").ifBlank { "nouă" }
                val downloadUrl = json.optString("downloadUrl", APK_LATEST_URL).ifBlank { APK_LATEST_URL }
                val update = if (remoteCode > installedCode) {
                    AppUpdateInfo(remoteCode, remoteName, downloadUrl)
                } else {
                    null
                }
                true to update
            }
        }.getOrElse { false to null }

        if (versionCheckResult.first) {
            return@withContext versionCheckResult.second
        }

        // Fallback: GitHub Releases API asset timestamp comparison
        runCatching {
            val req = Request.Builder()
                .url(GITHUB_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Agenda-Android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                val json = JSONObject(body)
                val assets = json.optJSONArray("assets") ?: return@use null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name") == "Agenda.apk") {
                        val updatedAt = asset.optString("updated_at")
                        if (updatedAt.isNotBlank()) {
                            val remoteMillis = Instant.parse(updatedAt).toEpochMilli()
                            if (remoteMillis > installedUpdateTime + 120_000L) {
                                return@use AppUpdateInfo(
                                    versionCode = (remoteMillis / 1000L).toInt(),
                                    versionName = "nouă",
                                    downloadUrl = asset.optString("browser_download_url", APK_LATEST_URL)
                                        .ifBlank { APK_LATEST_URL }
                                )
                            }
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Checks for an available GitHub release and posts a system notification once per versionCode.
     */
    suspend fun checkAndNotifyIfAvailable(context: Context): AppUpdateInfo? {
        val update = checkForUpdate(context)
        if (update != null && shouldNotify(context, update)) {
            NotificationHelper.showAppUpdateNotification(context, update)
            markNotified(context, update)
        }
        return update
    }

    fun shouldNotify(context: Context, update: AppUpdateInfo): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LAST_NOTIFIED_VERSION, -1) < update.versionCode
    }

    fun markNotified(context: Context, update: AppUpdateInfo) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_NOTIFIED_VERSION, update.versionCode)
            .apply()
    }
}
