package com.alotube.app.data.repository

import com.alotube.app.data.download.AudioTrackOption
import com.alotube.app.data.download.DownloadChoice
import com.alotube.app.data.download.DownloadOptions
import com.alotube.app.data.download.PostProcess
import com.alotube.app.data.model.PlayableVideo
import com.alotube.app.data.model.PlaybackOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.util.Locale

/**
 * Fallo al obtener streams tras todos los intentos.
 * [details] lista cada intento y su resultado, para poder diagnosticar en pantalla.
 */
class StreamFetchException(val details: String, cause: Throwable?) : Exception(details, cause)

/** Streams "crudos", vengan de StreamInfo o directamente del StreamExtractor. */
private data class RawStreams(
    val title: String,
    val author: String,
    val isLive: Boolean,
    val video: List<VideoStream>,
    val videoOnly: List<VideoStream>,
    val audio: List<AudioStream>,
    val hls: String?,
    val dash: String?,
    val viewCount: Long = -1L,
    val likeCount: Long = -1L,
    val uploadDate: String? = null
)

/**
 * Obtiene los enlaces de reproducción.
 *
 * Nota sobre el "cliente" de YouTube: NewPipeExtractor NO expone una API pública para elegir el
 * cliente de InnerTube (WEB, ANDROID, VISIONOS...). Desde v0.26.3 el propio extractor usa un cliente
 * alternativo para esquivar SABR. Por eso aquí los reintentos usan caminos distintos de la API pública:
 *   1) StreamInfo.getInfo        (camino normal)
 *   2) StreamExtractor directo   (tolera campos que fallen al parsear, que hacen fallar a StreamInfo)
 *   3) StreamInfo.getInfo otra vez tras una pausa (por si el fallo fue pasajero)
 */
class StreamRepository {

    suspend fun getPlayable(url: String): Result<PlayableVideo> = withContext(Dispatchers.IO) {
        val log = mutableListOf<String>()
        var lastError: Throwable? = null

        val attempts: List<Pair<String, () -> RawStreams>> = listOf(
            "StreamInfo" to { fetchViaStreamInfo(url) },
            "StreamExtractor directo" to { fetchViaExtractor(url) },
            "StreamInfo (reintento)" to { fetchViaStreamInfo(url) }
        )

        for ((index, attempt) in attempts.withIndex()) {
            val (name, fetch) = attempt
            val n = index + 1
            try {
                if (index > 0) delay(RETRY_DELAY_MS)
                val raw = fetch()
                val playable = raw.toPlayable()
                if (playable.options.isNotEmpty() || playable.fallback != null) {
                    return@withContext Result.success(playable)
                }
                log += "$n) $name → sin URLs válidas (${raw.summary()})"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                log += "$n) $name → ${e.javaClass.simpleName}: ${e.message?.take(160)}"
                // Sin red / captcha: reintentar enseguida no sirve de nada
                if (e is IOException) break
            }
        }

        val header = "NewPipeExtractor $EXTRACTOR_VERSION · cliente de YouTube: interno (lo elige el extractor)"
        Result.failure(StreamFetchException(header + "\n" + log.joinToString("\n"), lastError))
    }

    /**
     * Formatos descargables del video: progresivos (video+audio juntos) y pistas de audio.
     * Usa los mismos caminos que la reproducción (StreamInfo y, si falla, StreamExtractor).
     */
    suspend fun getDownloadOptions(url: String): Result<DownloadOptions> = withContext(Dispatchers.IO) {
        val sources: List<() -> RawStreams> = listOf(
            { fetchViaStreamInfo(url) },
            { fetchViaExtractor(url) }
        )
        var last: Throwable? = null
        for (fetch in sources) {
            try {
                val options = fetch().toDownloadOptions()
                if (options.videoChoices.isNotEmpty() || options.audioTracks.isNotEmpty()) {
                    return@withContext Result.success(options)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        Result.failure(last ?: IOException("No hay formatos descargables"))
    }

    // ---------- Fuentes de datos ----------

    private fun fetchViaStreamInfo(url: String): RawStreams {
        val info = StreamInfo.getInfo(url)
        return RawStreams(
            title = info.name.orEmpty(),
            author = info.uploaderName.orEmpty(),
            isLive = info.streamType == StreamType.LIVE_STREAM,
            video = info.videoStreams.orEmpty(),
            videoOnly = info.videoOnlyStreams.orEmpty(),
            audio = info.audioStreams.orEmpty(),
            hls = info.hlsUrl.nullIfEmpty(),
            dash = info.dashMpdUrl.nullIfEmpty(),
            viewCount = info.viewCount,
            likeCount = info.likeCount,
            uploadDate = info.textualUploadDate.nullIfEmpty()
        )
    }

    private fun fetchViaExtractor(url: String): RawStreams {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()
        // Cada campo por separado: si uno falla al parsear, los demás siguen valiendo
        fun <T> safe(default: T, block: () -> T): T = try {
            block()
        } catch (e: Exception) {
            default
        }
        return RawStreams(
            title = safe("") { extractor.name.orEmpty() },
            author = safe("") { extractor.uploaderName.orEmpty() },
            isLive = safe(false) { extractor.streamType == StreamType.LIVE_STREAM },
            video = safe(emptyList<VideoStream>()) { extractor.videoStreams.orEmpty() },
            videoOnly = safe(emptyList<VideoStream>()) { extractor.videoOnlyStreams.orEmpty() },
            audio = safe(emptyList<AudioStream>()) { extractor.audioStreams.orEmpty() },
            hls = safe<String?>(null) { extractor.hlsUrl }.nullIfEmpty(),
            dash = safe<String?>(null) { extractor.dashMpdUrl }.nullIfEmpty(),
            viewCount = safe(-1L) { extractor.viewCount },
            likeCount = safe(-1L) { extractor.likeCount },
            uploadDate = safe<String?>(null) { extractor.textualUploadDate }.nullIfEmpty()
        )
    }

    // ---------- Selección de calidades ----------

    private fun RawStreams.toPlayable(): PlayableVideo {
        val hlsOption = hls?.let {
            PlaybackOption(label = "En vivo", height = 0, videoUrl = it, isHls = true)
        }
        val dashOption = dash?.let {
            PlaybackOption(label = "DASH", height = 0, videoUrl = it, isDash = true)
        }
        val manifest = hlsOption ?: dashOption

        // Directos: solo manifiesto
        if (isLive && manifest != null) {
            return PlayableVideo(title, author, listOf(manifest), null, viewCount, likeCount, uploadDate)
        }

        val bestAudio = pickAudio(audio)
        val adaptive = if (bestAudio != null) pickAdaptive(videoOnly, bestAudio.content) else emptyList()

        val muxedStream = video.filter { it.usable() && it.height > 0 }.maxByOrNull { it.height }
        val muxed = muxedStream?.let {
            PlaybackOption(label = "${it.height}p", height = it.height, videoUrl = it.content)
        }

        // Si no hay URLs directas de video pero sí manifiesto HLS/DASH, se usa el manifiesto
        val options = when {
            adaptive.isNotEmpty() -> adaptive
            muxed != null -> listOf(muxed)
            manifest != null -> listOf(manifest)
            else -> emptyList()
        }
        return PlayableVideo(title, author, options, muxed ?: manifest, viewCount, likeCount, uploadDate)
    }

    private fun RawStreams.toDownloadOptions(): DownloadOptions {
        // ---- Audio por pistas (idioma) ----
        val tracks = audio
            .filter { it.usable() }
            .groupBy { it.audioTrackId ?: it.audioLocale?.toLanguageTag() ?: "default" }
            .map { (_, group) ->
                val first = group.first()
                AudioTrackOption(
                    label = trackLabel(first),
                    streams = group.sortedByDescending { it.averageBitrate }.map {
                        val suffix = it.format?.suffix
                        val kbps = if (it.averageBitrate > 0) "${it.averageBitrate}kbps" else ""
                        if (suffix == "webm") {
                            // Opus dentro de WebM: se entrega como .opus (Ogg Opus), nunca como .webm
                            DownloadChoice(
                                label = "opus $kbps".trim(),
                                url = it.content,
                                extension = "opus",
                                mimeType = "audio/ogg",
                                postProcess = PostProcess.OPUS_REMUX
                            )
                        } else {
                            DownloadChoice(
                                label = "${suffix ?: "m4a"} $kbps".trim(),
                                url = it.content,
                                extension = suffix ?: "m4a",
                                mimeType = it.format?.mimeType ?: "audio/mp4"
                            )
                        }
                    }
                ) to (first.audioTrackType == AudioTrackType.ORIGINAL || first.audioTrackType == null)
            }
            // La pista original primero
            .sortedByDescending { it.second }
            .map { it.first }

        // ---- Video ----
        // 1) Progresivos: ya traen audio (normalmente solo 360p)
        val progressive = video
            .filter { it.usable() && it.height > 0 }
            .map {
                val fmt = it.format
                Triple(
                    it.height, it.fps,
                    DownloadChoice(
                        label = "${fmt?.name ?: "Video"} ${it.resolution ?: "${it.height}p"}",
                        url = it.content,
                        extension = fmt?.suffix ?: "mp4",
                        mimeType = fmt?.mimeType ?: "video/mp4"
                    )
                )
            }

        // 2) H.264 (MP4) sin audio, hasta 1080p: se une con el audio AAC (m4a) en un MP4.
        //    Solo si hay audio m4a disponible.
        val hasAac = tracks.any { track -> track.streams.any { it.extension == "m4a" } }
        val adaptive = if (!hasAac) emptyList() else videoOnly
            .filter {
                it.usable() && it.height > 0 &&
                    it.format == MediaFormat.MPEG_4 && it.codec?.startsWith("avc1") == true
            }
            .groupBy { it.height to (it.fps > 30) }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.bitrate } }
            // Si ya hay uno progresivo de la misma resolución, se deja ese (no necesita unir)
            .filter { a -> progressive.none { it.first == a.height && (it.second > 30) == (a.fps > 30) } }
            .map {
                val res = if (it.fps > 30) "${it.height}p${it.fps}" else "${it.height}p"
                Triple(
                    it.height, it.fps,
                    DownloadChoice(
                        label = "MPEG-4 $res",
                        url = it.content,
                        extension = "mp4",
                        mimeType = "video/mp4",
                        postProcess = PostProcess.MERGE_MP4
                    )
                )
            }

        val videos = (progressive + adaptive)
            .sortedWith(
                compareByDescending<Triple<Int, Int, DownloadChoice>> { it.first }
                    .thenByDescending { it.second }
            )
            .map { it.third }

        return DownloadOptions(videos, tracks)
    }

    private fun trackLabel(stream: AudioStream): String {
        val language = stream.audioLocale?.getDisplayLanguage(Locale("es"))?.takeIf { it.isNotBlank() }
            ?: stream.audioTrackName?.takeIf { it.isNotBlank() }
            ?: "Predeterminado"
        val kind = when (stream.audioTrackType) {
            AudioTrackType.ORIGINAL -> " (original)"
            AudioTrackType.DUBBED -> " (doblado)"
            AudioTrackType.DESCRIPTIVE -> " (audiodescripción)"
            else -> ""
        }
        return language + kind
    }

    private fun RawStreams.summary(): String =
        "video=${video.count { it.usable() }}, videoSolo=${videoOnly.count { it.usable() }}, " +
            "audio=${audio.count { it.usable() }}, hls=${if (hls != null) "sí" else "no"}, " +
            "dash=${if (dash != null) "sí" else "no"}"

    /** Solo enlaces directos (HTTP progresivo) y no vacíos. */
    private fun Stream.usable(): Boolean =
        deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && isUrl && !content.isNullOrEmpty()

    /** Audio: pista original > M4A > mayor bitrate. */
    private fun pickAudio(streams: List<AudioStream>): AudioStream? =
        streams.filter { it.usable() }.maxWithOrNull(
            compareBy<AudioStream>(
                { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL },
                { it.format == MediaFormat.M4A },
                { it.averageBitrate }
            )
        )

    /** Un video-only por resolución (prefiere H.264 por compatibilidad), de mayor a menor. */
    private fun pickAdaptive(streams: List<VideoStream>, audioUrl: String): List<PlaybackOption> =
        streams
            .filter { it.usable() && it.height > 0 }
            .groupBy { it.height to (it.fps > 30) }
            .mapNotNull { (_, group) ->
                group.maxWithOrNull(
                    compareBy<VideoStream>({ codecRank(it.codec) }, { it.bitrate })
                )
            }
            .sortedWith(compareByDescending<VideoStream> { it.height }.thenByDescending { it.fps })
            .map {
                val label = if (it.fps > 30) "${it.height}p${it.fps}" else "${it.height}p"
                PlaybackOption(label, it.height, it.content, audioUrl)
            }

    private fun codecRank(codec: String?): Int = when {
        codec == null -> 0
        codec.startsWith("avc1") -> 2
        codec.startsWith("vp9") || codec.startsWith("vp09") -> 1
        else -> 0
    }

    private fun String?.nullIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }

    private companion object {
        /** Debe coincidir con la versión de app/build.gradle.kts (solo para el diagnóstico en pantalla). */
        const val EXTRACTOR_VERSION = "v0.26.5"
        const val RETRY_DELAY_MS = 1200L
    }
}
