package com.alotube.app.data.repository

import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Única clase que conoce NewPipeExtractor. El resto de la app usa [VideoItem]. */
class VideoRepository {

    suspend fun search(query: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()
            val videos = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>() // descarta canales y listas
                .map { it.toVideoItem() }
            Result.success(videos)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene los datos del video de un enlace con StreamInfo (NewPipeExtractor). Si hay sesión de
     * Google, el Downloader ya envía las cookies a youtube.com, así que videos privados o con
     * restricción de edad se intentan con la cuenta.
     */
    suspend fun resolveLink(url: String): Result<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(url)
            Result.success(
                VideoItem(
                    url = url,
                    title = info.name.orEmpty().ifBlank { url },
                    author = info.uploaderName.orEmpty(),
                    durationSeconds = info.duration,
                    thumbnailUrl = info.thumbnails.lastOrNull()?.url,
                    isLive = info.streamType == StreamType.LIVE_STREAM,
                    viewCount = info.viewCount,
                    uploadDateText = info.textualUploadDate?.takeIf { it.isNotBlank() }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (first: Exception) {
            // Segundo intento: extractor directo (tolera campos que fallen al parsear)
            try {
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                extractor.fetchPage()
                fun <T> safe(default: T, block: () -> T): T = try {
                    block()
                } catch (_: Exception) {
                    default
                }
                Result.success(
                    VideoItem(
                        url = url,
                        title = safe("") { extractor.name.orEmpty() }.ifBlank { url },
                        author = safe("") { extractor.uploaderName.orEmpty() },
                        durationSeconds = safe(0L) { extractor.length },
                        thumbnailUrl = safe<String?>(null) { extractor.thumbnails.lastOrNull()?.url },
                        isLive = safe(false) { extractor.streamType == StreamType.LIVE_STREAM },
                        viewCount = safe(-1L) { extractor.viewCount },
                        uploadDateText = safe<String?>(null) { extractor.textualUploadDate }
                            ?.takeIf { it.isNotBlank() }
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Result.failure(first)
            }
        }
    }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name ?: "",
        author = uploaderName ?: "",
        durationSeconds = duration,
        thumbnailUrl = thumbnails.lastOrNull()?.url,
        isLive = streamType == StreamType.LIVE_STREAM,
        viewCount = viewCount,
        uploadDateText = textualUploadDate?.takeIf { it.isNotBlank() }
    )
}
