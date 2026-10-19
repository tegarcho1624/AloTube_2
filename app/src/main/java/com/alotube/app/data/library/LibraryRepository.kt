package com.alotube.app.data.library

import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException

private inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Datos de la cuenta. Ninguna función lanza: devuelven Result y la UI decide qué mostrar
 * ("No disponible" si una sección falla, sin afectar al resto).
 */
class LibraryRepository {

    /** Listas de reproducción de la cuenta (sin "Ver después" ni "Me gustan", que van aparte). */
    suspend fun playlists(): Result<LibraryResult<LibraryPlaylist>> = withContext(Dispatchers.IO) {
        safeCall {
            val root = InnerTubeClient.browse("FEplaylist_aggregation")
            val list = LibraryParsers.playlists(root).filter { it.id != WATCH_LATER && it.id != LIKED }
            LibraryResult(list, if (list.isEmpty()) LibraryParsers.describe(root) else null)
        }
    }

    /**
     * Suscripciones. NewPipeExtractor no puede obtenerlas de la cuenta logueada (su
     * SubscriptionExtractor solo importa desde archivos de Google Takeout), así que se piden
     * directamente a YouTube con la sesión.
     */
    suspend fun subscriptions(): Result<LibraryResult<LibraryChannel>> = withContext(Dispatchers.IO) {
        safeCall {
            val root = InnerTubeClient.browse("FEchannels")
            val list = LibraryParsers.channels(root)
            LibraryResult(list, if (list.isEmpty()) LibraryParsers.describe(root) else null)
        }
    }

    /**
     * Videos de una lista. Las públicas se piden primero con PlaylistExtractor (NewPipe);
     * si falla o viene vacía (privada), y siempre para "Ver después"/"Me gustan", se usa la API con sesión.
     */
    suspend fun playlistVideos(id: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        safeCall {
            val isSystem = id == WATCH_LATER || id == LIKED
            if (!isSystem) {
                try {
                    val viaNewPipe = fromNewPipe(id)
                    if (viaNewPipe.isNotEmpty()) return@safeCall viaNewPipe
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Probablemente privada: se intenta con la sesión
                }
            }
            val root = InnerTubeClient.browse("VL$id")
            val videos = LibraryParsers.playlistVideos(root)
            if (videos.isEmpty() && !isSystem) {
                throw IOException("Lista vacía o no disponible (${LibraryParsers.describe(root)})")
            }
            videos
        }
    }

    private fun fromNewPipe(id: String): List<VideoItem> {
        val info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=$id")
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map {
                VideoItem(
                    url = it.url,
                    title = it.name ?: "",
                    author = it.uploaderName ?: "",
                    durationSeconds = it.duration,
                    thumbnailUrl = it.thumbnails.lastOrNull()?.url,
                    isLive = it.streamType == StreamType.LIVE_STREAM
                )
            }
    }

    companion object {
        const val WATCH_LATER = "WL"
        const val LIKED = "LL"
    }
}
