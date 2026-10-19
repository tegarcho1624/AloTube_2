package com.alotube.app.data.repository

import com.alotube.app.data.model.PlayableVideo
import com.alotube.app.data.model.PlaybackOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** Obtiene los enlaces de reproducción con StreamInfo (NewPipeExtractor). */
class StreamRepository {

    suspend fun getPlayable(url: String): Result<PlayableVideo> = withContext(Dispatchers.IO) {
        try {
            Result.success(StreamInfo.getInfo(url).toPlayable())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun StreamInfo.toPlayable(): PlayableVideo {
        val hls = hlsUrl?.takeIf { it.isNotEmpty() }
        val hlsOption = hls?.let {
            PlaybackOption(label = "En vivo", height = 0, videoUrl = it, isHls = true)
        }

        // Directos: solo HLS
        if (streamType == StreamType.LIVE_STREAM && hlsOption != null) {
            return PlayableVideo(name ?: "", uploaderName ?: "", listOf(hlsOption), null)
        }

        val audio = pickAudio(audioStreams)
        val adaptive = if (audio != null) pickAdaptive(videoOnlyStreams, audio.content) else emptyList()

        val muxedStream = videoStreams
            .filter { it.usable() && it.height > 0 }
            .maxByOrNull { it.height }
        val muxed = muxedStream?.let {
            PlaybackOption(label = "${it.height}p", height = it.height, videoUrl = it.content)
        }

        val options = when {
            adaptive.isNotEmpty() -> adaptive
            muxed != null -> listOf(muxed)
            hlsOption != null -> listOf(hlsOption)
            else -> emptyList()
        }
        return PlayableVideo(name ?: "", uploaderName ?: "", options, muxed)
    }

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
}
