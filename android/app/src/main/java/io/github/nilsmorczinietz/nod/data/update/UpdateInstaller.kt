package io.github.nilsmorczinietz.nod.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import io.github.nilsmorczinietz.nod.NodApp
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Progress of an in-app update, shown in Settings › About and the inbox banner. */
sealed interface InstallState {
    data object Idle : InstallState

    /** Android needs "Install unknown apps" for Nod first; we continue on return. */
    data class NeedsPermission(val info: UpdateInfo) : InstallState

    /** [progress] in 0..1, or null when the size is unknown. */
    data class Downloading(val version: String, val progress: Float?) : InstallState

    data class Verifying(val version: String) : InstallState

    /** Handed to the system installer; the user confirms its dialog. */
    data class WaitingForUser(val version: String) : InstallState

    data class Failed(val reason: String) : InstallState
}

/**
 * Downloads the release APK into the cache, checks it against the release's
 * SHA256SUMS and hands it to the system installer (PackageInstaller session).
 * Android itself refuses an APK that isn't signed with the installed app's
 * key, and the user confirms the install. No storage permission is needed.
 */
class UpdateInstaller(
    context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val context = context.applicationContext
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()
    private var job: Job? = null

    private val updatesDir get() = File(context.cacheDir, "updates")

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** "Install unknown apps" for Nod. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(info: UpdateInfo) {
        if (job?.isActive == true || !info.canInstallInApp) return
        if (!canInstall()) {
            _state.value = InstallState.NeedsPermission(info)
            return
        }
        job = scope.launch {
            try {
                run(info)
            } catch (e: Exception) {
                Log.w(TAG, "Update ${info.version} failed", e)
                cleanUp()
                _state.value = InstallState.Failed(e.message ?: "unknown error")
            }
        }
    }

    /** Call on ON_RESUME: continues after the user granted "Install unknown apps". */
    fun onResume() {
        val current = _state.value
        if (current is InstallState.NeedsPermission && canInstall()) install(current.info)
    }

    fun reset() {
        if (job?.isActive != true) _state.value = InstallState.Idle
    }

    private suspend fun run(info: UpdateInfo) = withContext(Dispatchers.IO) {
        val name = requireNotNull(info.apkName)
        cleanUp()
        updatesDir.mkdirs()
        val apk = File(updatesDir, name)

        _state.value = InstallState.Downloading(info.version, 0f)
        download(requireNotNull(info.apkUrl), apk) { progress -> _state.value = InstallState.Downloading(info.version, progress) }

        _state.value = InstallState.Verifying(info.version)
        val expected = expectedChecksum(requireNotNull(info.checksumsUrl), name)
            ?: error("$name is not listed in SHA256SUMS")
        val actual = sha256(apk)
        if (!actual.equals(expected, ignoreCase = true)) {
            error("Checksum mismatch, the download was not installed")
        }

        commit(apk)
        // The session holds its own copy; the cached file is no longer needed.
        cleanUp()
        _state.value = InstallState.WaitingForUser(info.version)
    }

    private fun download(url: String, target: File, onProgress: (Float?) -> Unit) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (HTTP ${response.code})")
            val total = response.body.contentLength().takeIf { it > 0 }
            var done = 0L
            var reported = -1
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = total?.let { (done * 100 / it).toInt() } ?: -1
                        if (percent != reported) {
                            reported = percent
                            onProgress(total?.let { done.toFloat() / it })
                        }
                    }
                }
            }
        }
    }

    /** `<sha256>  <file name>` per line, as written by `sha256sum`. */
    private fun expectedChecksum(url: String, name: String): String? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("SHA256SUMS not available (HTTP ${response.code})")
            response.body.string().lineSequence()
                .map { it.trim().split(Regex("\\s+"), limit = 2) }
                .firstOrNull { it.size == 2 && it[1].removePrefix("*") == name }
                ?.get(0)
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java),
                // The installer fills in the status extras.
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(callback.intentSender)
        }
    }

    internal fun onStatus(status: Int, message: String?, confirm: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> confirm?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            // On success Android replaces (and restarts) the app; nothing left to do.
            PackageInstaller.STATUS_SUCCESS -> _state.value = InstallState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = InstallState.Idle
            else -> _state.value = InstallState.Failed(message ?: "Android rejected the update (status $status)")
        }
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) cleanUp()
    }

    private fun cleanUp() {
        updatesDir.deleteRecursively()
    }

    private companion object {
        const val TAG = "NodUpdate"
    }
}

/** Receives PackageInstaller session results (manifest-registered, not exported). */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        (context.applicationContext as NodApp).container.updateInstaller.onStatus(status, message, confirm)
    }
}
