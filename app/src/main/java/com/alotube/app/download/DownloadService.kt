package com.alotube.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.alotube.app.MainActivity
import com.alotube.app.R
import com.alotube.app.data.download.DownloadRequest
import com.alotube.app.data.download.PostProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Descargas en segundo plano (ForegroundService + OkHttp).
 *  - Una a la vez, en cola; notificación de progreso con botón Cancelar.
 *  - Se pide el archivo por partes (Range, 10 MB) porque YouTube limita las descargas de un solo golpe.
 *  - Según el tipo: se guarda tal cual, o se descarga a caché, se reempaqueta (WebM Opus -> .opus,
 *    video H.264 + audio AAC -> MP4) y se guarda.
 *  - Se guarda con MediaStore en la carpeta pública Descargas: no necesita permisos de almacenamiento
 *    y aparece en el gestor de archivos.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val pending = ArrayDeque<DownloadRequest>()
    private var running = false
    private var doneId = DONE_ID_BASE

    @Volatile
    private var cancelled = false

    @Volatile
    private var currentCall: Call? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.dl_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled = true
            currentCall?.cancel()
            synchronized(lock) { if (!running) stopSelf(startId) }
            return START_NOT_STICKY
        }

        val request = DownloadRequest.fromIntent(intent)
        synchronized(lock) {
            if (request != null) pending.add(request)
            if (!running) {
                running = true
                // Obligatorio tras startForegroundService: la notificación sale ya
                ServiceCompat.startForeground(
                    this, PROGRESS_ID,
                    progressNotification(request?.displayName ?: getString(R.string.dl_channel), -1),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                scope.launch { processQueue() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        currentCall?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Cola ----------

    private fun processQueue() {
        while (true) {
            val next = synchronized(lock) {
                val item = pending.removeFirstOrNull()
                if (item == null) running = false
                item
            } ?: break
            download(next)
        }
        // Se detiene en el hilo principal; si entró otra petición mientras tanto, running vuelve a ser true
        mainHandler.post {
            synchronized(lock) {
                if (!running) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    // ---------- Una descarga ----------

    private fun download(request: DownloadRequest) {
        cancelled = false
        val displayName = request.displayName
        val temps = mutableListOf<File>()
        try {
            notificationManager.notify(PROGRESS_ID, progressNotification(displayName, -1))

            // Se usa el tipo MIME que Android asocia a la extensión: así MediaStore no renombra el archivo
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(request.extension)
                ?: request.mimeType

            val uri: Uri = when (request.postProcess) {
                PostProcess.NONE ->
                    saveToStore(displayName, mime) { out ->
                        copyInChunks(request.url, out, displayName, 0, 100)
                    }

                PostProcess.OPUS_REMUX -> {
                    val source = tempFile("src").also { temps += it }
                    FileOutputStream(source).use { copyInChunks(request.url, it, displayName, 0, 85) }

                    showProcessing(displayName)
                    val converted = tempFile("opus").also { temps += it }
                    // Si el reempaquetado falla en este dispositivo, se guarda el audio tal cual con extensión .opus
                    val finalFile = try {
                        MediaRemuxer.webmOpusToOgg(source, converted)
                        converted
                    } catch (e: Exception) {
                        if (cancelled) throw e
                        source
                    }
                    saveToStore(displayName, mime) { out -> copyFile(finalFile, out) }
                }

                PostProcess.MERGE_MP4 -> {
                    val audioUrl = request.audioUrl ?: throw IOException(getString(R.string.dl_err_audio))
                    val videoPart = tempFile("v").also { temps += it }
                    val audioPart = tempFile("a").also { temps += it }
                    FileOutputStream(videoPart).use { copyInChunks(request.url, it, displayName, 0, 75) }
                    FileOutputStream(audioPart).use { copyInChunks(audioUrl, it, displayName, 75, 15) }

                    showProcessing(displayName)
                    val merged = tempFile("mp4").also { temps += it }
                    MediaRemuxer.mergeToMp4(videoPart, audioPart, merged)
                    // Se libera espacio antes de copiar al almacenamiento público
                    videoPart.delete()
                    audioPart.delete()
                    saveToStore(displayName, mime) { out -> copyFile(merged, out) }
                }
            }

            notifyDone(displayName, uri, mime)
            toast(getString(R.string.dl_done_toast, displayName))
        } catch (e: Exception) {
            if (cancelled) {
                toast(getString(R.string.dl_cancelled))
            } else {
                val reason = e.message?.take(120) ?: e.javaClass.simpleName
                notifyFailed(displayName, reason)
                toast(getString(R.string.dl_failed_toast, reason))
            }
        } finally {
            temps.forEach { it.delete() }
            currentCall = null
        }
    }

    /** Crea el archivo en Descargas (MediaStore), lo rellena con [write] y lo hace visible. */
    private fun saveToStore(displayName: String, mime: String, write: (OutputStream) -> Unit): Uri {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1) // oculto hasta terminar
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException(getString(R.string.dl_err_create))
        try {
            val stream = resolver.openOutputStream(target)
                ?: throw IOException(getString(R.string.dl_err_create))
            stream.use(write)
            resolver.update(
                target,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null, null
            )
            return target
        } catch (e: Exception) {
            // Archivo a medias: se borra
            try {
                resolver.delete(target, null, null)
            } catch (_: Exception) {
            }
            throw e
        }
    }

    /**
     * Descarga por partes con Range y progreso. El avance va de [base] a [base]+[span] (0–100).
     * Lanza IOException si algo falla.
     */
    private fun copyInChunks(url: String, out: OutputStream, displayName: String, base: Int, span: Int) {
        val buffer = ByteArray(64 * 1024)
        var offset = 0L
        var total = -1L
        var lastNotify = 0L
        var lastPercent = -2

        while (true) {
            if (cancelled) throw IOException("cancelled")

            val httpRequest = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=$offset-${offset + CHUNK_SIZE - 1}")
                .build()
            val call = client.newCall(httpRequest)
            currentCall = call

            var finished = false
            call.execute().use { response ->
                if (response.code == 416) { // más allá del final: ya está completo
                    finished = true
                    return@use
                }
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException(getString(R.string.dl_err_empty))

                val partial = response.code == 206
                if (!partial && offset > 0) throw IOException(getString(R.string.dl_err_range))
                if (partial) {
                    parseTotal(response.header("Content-Range"))?.let { total = it }
                } else {
                    total = body.contentLength() // el servidor ignoró Range: llega entero
                }

                var written = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        written += n
                        offset += n

                        val now = System.currentTimeMillis()
                        val percent = if (total > 0) {
                            (base + offset.toDouble() / total * span).toInt().coerceIn(0, 100)
                        } else -1
                        if (percent != lastPercent && now - lastNotify > 500) {
                            lastNotify = now
                            lastPercent = percent
                            notificationManager.notify(PROGRESS_ID, progressNotification(displayName, percent))
                        }
                        if (cancelled) throw IOException("cancelled")
                    }
                }

                finished = when {
                    !partial -> true
                    written == 0L -> true
                    total > 0 -> offset >= total
                    else -> written < CHUNK_SIZE
                }
            }
            if (finished) break
        }
    }

    private fun copyFile(file: File, out: OutputStream) {
        file.inputStream().use { it.copyTo(out, 64 * 1024) }
    }

    private fun tempFile(tag: String): File = File(cacheDir, "dl_${System.nanoTime()}_$tag")

    private fun parseTotal(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/')?.toLongOrNull()

    // ---------- Notificaciones ----------

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun showProcessing(title: String) {
        notificationManager.notify(
            PROGRESS_ID,
            progressNotification(title, -1, getString(R.string.dl_processing))
        )
    }

    /** [percent] = -1 -> indeterminado. [text] reemplaza el texto por defecto. */
    private fun progressNotification(title: String, percent: Int, text: String? = null): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_act_download)
            .setContentTitle(title)
            .setContentText(
                text ?: if (percent >= 0) getString(R.string.dl_progress_pct, percent)
                else getString(R.string.dl_progress)
            )
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.dl_cancel), cancelIntent)
            .build()
    }

    private fun notifyDone(displayName: String, uri: Uri, mime: String) {
        val view = PendingIntent.getActivity(
            this, 0,
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_act_download)
            .setContentTitle(displayName)
            .setContentText(getString(R.string.dl_done))
            .setAutoCancel(true)
            .setContentIntent(view)
            .build()
        notificationManager.notify(doneId++, notification)
    }

    private fun notifyFailed(displayName: String, reason: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_act_download)
            .setContentTitle(getString(R.string.dl_failed))
            .setContentText("$displayName — $reason")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager.notify(doneId++, notification)
    }

    private fun toast(text: String) {
        mainHandler.post { Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        private const val CHANNEL_ID = "alotube_downloads"
        private const val PROGRESS_ID = 2001
        private const val DONE_ID_BASE = 3000
        private const val ACTION_CANCEL = "com.alotube.app.action.CANCEL_DOWNLOAD"
        private const val CHUNK_SIZE = 10L * 1024 * 1024
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        /** Encola una descarga (arranca el servicio en primer plano). */
        fun enqueue(ctx: Context, request: DownloadRequest) {
            ContextCompat.startForegroundService(
                ctx, request.toIntent(ctx, DownloadService::class.java)
            )
        }
    }
}
