package com.alotube.app.data.repository

import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
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
