package com.alotube.app.data.repository

import com.alotube.app.data.model.ChannelDetails
import com.alotube.app.data.model.SearchPage
import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

private inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Cada campo por separado: si uno falla al parsear, el resto de la pantalla sigue funcionando. */
private fun <T> safe(default: T, block: () -> T): T = try {
    block()
} catch (_: Exception) {
    default
}

/**
 * Información de un canal con ChannelExtractor. Desde NewPipeExtractor 0.24 los videos ya no vienen
 * en el canal: se piden con el ChannelTabExtractor de la pestaña "videos".
 */
class ChannelRepository {

    data class Loaded(
        val details: ChannelDetails,
        val videos: SearchPage<VideoItem>?,
        /** Pestaña de videos, necesaria para pedir más páginas. */
        val videosTab: ListLinkHandler?,
        /** Si falló solo la lista de videos (el encabezado sí cargó). */
        val videosError: String?
    )

    suspend fun load(url: String): Result<Loaded> = withContext(Dispatchers.IO) {
        safeCall {
            val extractor = ServiceList.YouTube.getChannelExtractor(url)
            extractor.fetchPage() // si esto falla, el canal no carga y se muestra el error

            val name = safe("") { extractor.name.orEmpty() }
            val details = ChannelDetails(
                name = name,
                avatarUrl = safe<String?>(null) { bestImage(extractor.avatars) },
                bannerUrl = safe<String?>(null) { bestImage(extractor.banners) },
                subscriberCount = safe(-1L) { extractor.subscriberCount },
                description = safe<String?>(null) { extractor.description }?.takeIf { it.isNotBlank() }
            )

            var tab: ListLinkHandler? = null
            var videos: SearchPage<VideoItem>? = null
            var videosError: String? = null
            try {
                tab = extractor.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                if (tab == null) {
                    videosError = "El canal no tiene pestaña de videos"
                } else {
                    val tabExtractor = ServiceList.YouTube.getChannelTabExtractor(tab)
                    tabExtractor.fetchPage()
                    videos = toVideoPage(tabExtractor.initialPage, name)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                videosError = e.message?.take(160) ?: e.javaClass.simpleName
            }
            Loaded(details, videos, tab, videosError)
        }
    }

    suspend fun moreVideos(
        tab: ListLinkHandler,
        page: Page,
        channelName: String
    ): Result<SearchPage<VideoItem>> = withContext(Dispatchers.IO) {
        safeCall {
            val tabExtractor = ServiceList.YouTube.getChannelTabExtractor(tab)
            toVideoPage(tabExtractor.getPage(page), channelName)
        }
    }

    private fun toVideoPage(page: InfoItemsPage<InfoItem>, channelName: String): SearchPage<VideoItem> =
        SearchPage(
            items = page.items.filterIsInstance<StreamInfoItem>().map {
                VideoItem(
                    url = it.url,
                    title = it.name ?: "",
                    // En la lista de un canal YouTube no repite el autor: se usa el nombre del canal
                    author = it.uploaderName?.takeIf { a -> a.isNotBlank() } ?: channelName,
                    durationSeconds = it.duration,
                    thumbnailUrl = it.thumbnails.lastOrNull()?.url,
                    isLive = it.streamType == StreamType.LIVE_STREAM,
                    viewCount = it.viewCount,
                    uploadDateText = it.textualUploadDate?.takeIf { d -> d.isNotBlank() }
                )
            },
            nextPage = if (page.hasNextPage()) page.nextPage else null
        )

    /** La imagen más grande disponible (avatares y banners vienen en varios tamaños). */
    private fun bestImage(images: List<Image>): String? {
        val best = images.maxByOrNull { it.height } ?: images.lastOrNull()
        return best?.url?.let { if (it.startsWith("//")) "https:$it" else it }
    }
}
