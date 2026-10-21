package com.alotube.app.data.repository

import com.alotube.app.data.model.ChannelItem
import com.alotube.app.data.model.SearchPage
import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

private inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

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

    // ---------- Videos con paginación ----------

    /** Primera página de videos (misma búsqueda de siempre) + referencia a la siguiente. */
    suspend fun searchVideos(query: String): Result<SearchPage<VideoItem>> = withContext(Dispatchers.IO) {
        safeCall {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()
            collectVideos(extractor.initialPage) { extractor.getPage(it) }
        }
    }

    /** Páginas siguientes (scroll infinito). */
    suspend fun moreVideos(query: String, page: Page): Result<SearchPage<VideoItem>> = withContext(Dispatchers.IO) {
        safeCall {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            collectVideos(extractor.getPage(page)) { extractor.getPage(it) }
        }
    }

    /**
     * Se queda con los videos de la página. Si una página trae solo canales/listas (sin videos) y hay
     * más, se pide la siguiente (máx. 3 veces) para que el scroll infinito no se quede "vacío".
     */
    private fun collectVideos(
        first: InfoItemsPage<InfoItem>,
        next: (Page) -> InfoItemsPage<InfoItem>
    ): SearchPage<VideoItem> {
        var page = first
        val videos = mutableListOf<VideoItem>()
        var tries = 0
        while (true) {
            videos += page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
            val nextPage = if (page.hasNextPage()) page.nextPage else null
            if (videos.isNotEmpty() || nextPage == null || ++tries >= 3) return SearchPage(videos, nextPage)
            page = next(nextPage)
        }
    }

    // ---------- Canales con paginación ----------

    private fun channelExtractor(query: String) = ServiceList.YouTube.getSearchExtractor(
        query,
        listOf(YoutubeSearchQueryHandlerFactory.CHANNELS),
        ""
    )

    suspend fun searchChannels(query: String): Result<SearchPage<ChannelItem>> = withContext(Dispatchers.IO) {
        safeCall {
            val extractor = channelExtractor(query)
            extractor.fetchPage()
            toChannelPage(extractor.initialPage)
        }
    }

    suspend fun moreChannels(query: String, page: Page): Result<SearchPage<ChannelItem>> = withContext(Dispatchers.IO) {
        safeCall { toChannelPage(channelExtractor(query).getPage(page)) }
    }

    private fun toChannelPage(page: InfoItemsPage<InfoItem>): SearchPage<ChannelItem> =
        SearchPage(
            items = page.items.filterIsInstance<ChannelInfoItem>().map {
                ChannelItem(
                    url = it.url,
                    name = it.name ?: "",
                    avatarUrl = it.thumbnails.lastOrNull()?.url?.let { u ->
                        if (u.startsWith("//")) "https:$u" else u
                    },
                    subscriberCount = it.subscriberCount,
                    description = it.description?.takeIf { d -> d.isNotBlank() }
                )
            },
            nextPage = if (page.hasNextPage()) page.nextPage else null
        )

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
