package com.duq.android.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationCompat
import com.duq.android.BuildConfig
import com.duq.android.config.AppConfig
import com.duq.android.logging.FileLogger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Self-update (sideload channel — app is not on Play Store):
 *   check VPS version.json → if newer, download the signed APK → hand it to the
 *   system PackageInstaller, which shows the OS confirm dialog ("Update").
 *
 * This is the standard pattern for out-of-store Android apps: the app fully
 * automates discovery + download; the final install is confirmed by the user
 * (Android forbids silent install without root / device-owner / system signature).
 *
 * Install uses the modern PackageInstaller Session API (not the deprecated
 * ACTION_INSTALL_PACKAGE intent) so we get a real status callback and can raise
 * the confirm UI reliably from both foreground and background (WorkManager).
 */
class AppUpdater(private val context: Context, private val notificationsEnabled: Boolean = true) {

    companion object {
        private const val TAG = "AppUpdater"
        private val VERSION_URL = AppConfig.UPDATE_VERSION_URL
        private val APK_URL = AppConfig.UPDATE_APK_URL
        private const val APK_FILENAME = "duq-update.apk"
        const val CHANNEL_ID = "duq_update_channel"
        private const val NOTIFY_ID = 9001

        // Shared prefs so the UI can show the update banner immediately on detect.
        const val UPDATE_PREFS = "duq_update"
        const val KEY_AVAILABLE_VERSION = "available_version"

        /** Newer version available on the channel, or 0 if none / up to date. */
        fun availableVersion(context: Context): Int =
            context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_AVAILABLE_VERSION, 0)
                .takeIf { it > BuildConfig.VERSION_CODE } ?: 0
    }

    private val updatePrefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)

    // Force HTTP/1.1: OkHttp's HTTP/2 stream can stall indefinitely on large
    // bodies behind some nginx setups (flow-control window never opens), and the
    // per-read timeout never fires because no bytes arrive — the download hangs
    // forever. callTimeout is a hard ceiling on the whole request as a backstop.
    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    private val nm = context.getSystemService(NotificationManager::class.java)
    private val flog = FileLogger(context)

    /**
     * FAST detect (version.json only, ~1s, NO 33MB download). Records the available
     * version for the in-app banner and raises a notification immediately, so the
     * user sees "update available" instantly. Download happens later on tap.
     * Returns the newer version, or 0.
     */
    fun checkAvailable(): Int {
        return try {
            val remoteCode = fetchRemoteVersionCode() ?: run {
                flog.w(TAG, "version.json fetch returned null"); return 0
            }
            flog.i(TAG, "Remote versionCode=$remoteCode, local=${BuildConfig.VERSION_CODE}")
            if (remoteCode <= BuildConfig.VERSION_CODE) {
                updatePrefs.edit().remove(KEY_AVAILABLE_VERSION).apply()
                nm.cancel(NOTIFY_ID)
                return 0
            }
            updatePrefs.edit().putInt(KEY_AVAILABLE_VERSION, remoteCode).apply()
            if (notificationsEnabled) showAvailableNotification(remoteCode)
            flog.i(TAG, "Update available: $remoteCode (banner + notification shown)")
            remoteCode
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            flog.e(TAG, "checkAvailable failed: ${e.message}", e); 0
        }
    }

    /**
     * Download the APK and start install (in-app banner / notification tap).
     * Reuses a cached APK if present. Returns false if nothing to install.
     */
    fun downloadAndInstall(onProgress: (Float) -> Unit = {}): Boolean {
        return try {
            val v = updatePrefs.getInt(KEY_AVAILABLE_VERSION, 0)
            if (v <= BuildConfig.VERSION_CODE) return false
            if (notificationsEnabled) showDownloadingNotification(v)
            // Always fetch a FRESH APK. A reused cache file caused
            // INSTALL_PARSE_FAILED_NOT_APK (stale/partial download from earlier).
            val apkFile = downloadApk(onProgress) ?: run { nm.cancel(NOTIFY_ID); return false }
            nm.cancel(NOTIFY_ID)
            installApk(apkFile, v)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            nm.cancel(NOTIFY_ID); throw e
        } catch (e: Exception) {
            flog.e(TAG, "downloadAndInstall failed: ${e.message}", e)
            nm.cancel(NOTIFY_ID); false
        }
    }

    private fun fetchRemoteVersionCode(): Int? {
        // Cache-bust: GitHub's CDN serves releases/latest/download/* from edge
        // caches, so a fresh release can return a STALE version.json for a while.
        // no-cache + a unique query param force a fresh fetch.
        val request = Request.Builder()
            .url("$VERSION_URL?t=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }
        return JSONObject(body).optInt("versionCode", -1).takeIf { it > 0 }
    }

    private fun downloadApk(onProgress: (Float) -> Unit = {}): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(dir, APK_FILENAME)
        val tmp = File(dir, "$APK_FILENAME.tmp")
        // Download to .tmp then atomically rename, so an interrupted download never
        // leaves a partial file that fails install with INSTALL_PARSE_FAILED_NOT_APK.
        // Cache-bust so the CDN doesn't hand back a stale APK for latest/download.
        val request = Request.Builder()
            .url("$APK_URL?t=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) { tmp.delete(); return null }
            val body = resp.body ?: run { tmp.delete(); return null }
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var sum = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        sum += read
                        if (total > 0) onProgress((sum.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (tmp.length() <= 0) { tmp.delete(); return null }
        apkFile.delete()
        if (!tmp.renameTo(apkFile)) { tmp.delete(); return null }
        flog.i(TAG, "APK downloaded: ${apkFile.length() / 1024 / 1024}MB")
        return apkFile
    }

    /**
     * Streams the APK into a PackageInstaller session and commits it. The system
     * then sends STATUS_PENDING_USER_ACTION to [InstallResultReceiver], which
     * raises the OS confirm dialog. Works from foreground and background.
     */
    private fun installApk(apkFile: File, version: Int) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply { setAppPackageName(context.packageName) }

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("duq-update", 0, apkFile.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val statusIntent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = InstallResultReceiver.ACTION_INSTALL_STATUS
                    setPackage(context.packageName)
                    putExtra(InstallResultReceiver.EXTRA_VERSION, version)
                }
                val pi = android.app.PendingIntent.getBroadcast(
                    context, sessionId, statusIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutableFlag()
                )
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            // Otherwise the dangling session accumulates toward the OEM session cap.
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
        flog.i(TAG, "Install session committed for v$version (id=$sessionId)")
    }

    private fun pendingIntentMutableFlag(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            android.app.PendingIntent.FLAG_MUTABLE else 0

    private fun ensureChannel() {
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DUQ Updates", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "App update notifications" }
            )
        }
    }

    /** Immediate "update available" notification (shown on detect, before download). */
    private fun showAvailableNotification(version: Int) {
        ensureChannel()
        val open = Intent(context, com.duq.android.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = android.app.PendingIntent.getActivity(
            context, 1, open,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Доступно обновление DUQ v$version")
            .setContentText("Открой приложение и нажми «Установить»")
            .setContentIntent(pi)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(NOTIFY_ID, n)
        com.duq.android.data.NotificationInbox.record(
            context, "Доступно обновление v$version", "Открой приложение и нажми «Установить»",
            "update", System.currentTimeMillis()
        )
        flog.i(TAG, "Available notification shown for v$version")
    }

    private fun showDownloadingNotification(version: Int) {
        ensureChannel()
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("DUQ Update")
            .setContentText("Скачивание v$version...")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        nm.notify(NOTIFY_ID, n)
    }
}
