package com.alotube.app.data.library

import com.alotube.app.data.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * La respuesta de youtubei es un árbol enorme y cambiante. En vez de seguir una ruta fija, se recorre
 * entero buscando los "renderers" conocidos (formato clásico y el nuevo lockupViewModel).
 * Si algo no se reconoce simplemente no aparece: nunca lanza excepciones por el formato.
 */
internal object LibraryParsers {

    // ---------- Recorrido ----------

    private fun walk(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                visit(node)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    // ---------- Utilidades ----------

    /** simpleText, runs[] o content (formato nuevo). */
    private fun JSONObject?.text(): String? {
        if (this == null) return null
        optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        optJSONArray("runs")?.let { runs ->
            val joined = (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            if (joined.isNotEmpty()) return joined
        }
        return optString("content").takeIf { it.isNotEmpty() }
    }

    private fun fixUrl(url: String?): String? = when {
        url.isNullOrEmpty() -> null
        url.startsWith("//") -> "https:$url"
        else -> url
    }

    /** Última (normalmente la más grande) de una lista de miniaturas {url}. */
    private fun bestThumb(array: JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        return fixUrl(array.optJSONObject(array.length() - 1)?.optString("url"))
    }

    private fun JSONObject?.obj(vararg path: String): JSONObject? {
        var cur: JSONObject? = this
        for (key in path) cur = cur?.optJSONObject(key)
        return cur
    }

    // ---------- Listas de reproducción ----------

    fun playlists(root: JSONObject): List<LibraryPlaylist> {
        val found = LinkedHashMap<String, LibraryPlaylist>()
        walk(root) { o ->
            (o.optJSONObject("gridPlaylistRenderer") ?: o.optJSONObject("playlistRenderer"))?.let { r ->
                val id = r.optString("playlistId")
                val title = r.optJSONObject("title").text()
                if (id.isNotEmpty() && !title.isNullOrBlank()) {
                    val thumb = bestThumb(r.obj("thumbnail")?.optJSONArray("thumbnails"))
                        ?: bestThumb(r.obj("thumbnailRenderer", "playlistVideoThumbnailRenderer", "thumbnail")?.optJSONArray("thumbnails"))
                    val count = r.optJSONObject("videoCountText").text()
                        ?: r.optJSONObject("videoCountShortText").text()
                    found.putIfAbsent(id, LibraryPlaylist(id, title, count, thumb))
                }
            }
            o.optJSONObject("lockupViewModel")?.let { lv ->
                if (lv.optString("contentType").contains("PLAYLIST")) {
                    val id = lv.optString("contentId")
                    val title = lv.obj("metadata", "lockupMetadataViewModel", "title").text()
                    if (id.isNotEmpty() && !title.isNullOrBlank()) {
                        val thumb = bestThumb(
                            lv.obj("contentImage", "collectionThumbnailViewModel", "primaryThumbnail", "thumbnailViewModel", "image")
                                ?.optJSONArray("sources")
                        )
                        var count: String? = null
                        walk(lv.optJSONObject("contentImage")) { c ->
                            c.optJSONObject("thumbnailBadgeViewModel")?.optString("text")
                                ?.takeIf { it.isNotEmpty() }?.let { count = it }
                        }
                        found.putIfAbsent(id, LibraryPlaylist(id, title, count, thumb))
                    }
                }
            }
        }
        return found.values.toList()
    }

    // ---------- Canales ----------

    fun channels(root: JSONObject): List<LibraryChannel> {
        val found = LinkedHashMap<String, LibraryChannel>()
        walk(root) { o ->
            (o.optJSONObject("channelRenderer") ?: o.optJSONObject("gridChannelRenderer"))?.let { r ->
                val id = r.optString("channelId")
                val name = r.optJSONObject("title").text()
                if (id.isNotEmpty() && !name.isNullOrBlank()) {
                    val avatar = bestThumb(r.obj("thumbnail")?.optJSONArray("thumbnails"))
                    val sub = r.optJSONObject("subscriberCountText").text()
                        ?: r.optJSONObject("videoCountText").text()
                    found.putIfAbsent(id, LibraryChannel(id, name, sub, avatar))
                }
            }
            o.optJSONObject("lockupViewModel")?.let { lv ->
                if (lv.optString("contentType").contains("CHANNEL")) {
                    val id = lv.optString("contentId")
                    val name = lv.obj("metadata", "lockupMetadataViewModel", "title").text()
                    if (id.isNotEmpty() && !name.isNullOrBlank()) {
                        val avatar = bestThumb(
                            lv.obj("contentImage", "decoratedAvatarViewModel", "avatar", "avatarViewModel", "image")
                                ?.optJSONArray("sources")
                        )
                        found.putIfAbsent(id, LibraryChannel(id, name, null, avatar))
                    }
                }
            }
        }
        return found.values.toList()
    }

    // ---------- Videos de una lista ----------

    fun playlistVideos(root: JSONObject): List<VideoItem> {
        val found = LinkedHashMap<String, VideoItem>()
        walk(root) { o ->
            o.optJSONObject("playlistVideoRenderer")?.let { r ->
                val id = r.optString("videoId")
                val title = r.optJSONObject("title").text()
                if (id.isNotEmpty() && !title.isNullOrBlank()) {
                    val author = r.optJSONObject("shortBylineText").text() ?: ""
                    val seconds = r.optString("lengthSeconds").toLongOrNull() ?: 0L
                    val thumb = bestThumb(r.obj("thumbnail")?.optJSONArray("thumbnails"))
                    found.putIfAbsent(id, VideoItem(watchUrl(id), title, author, seconds, thumb, false))
                }
            }
            o.optJSONObject("lockupViewModel")?.let { lv ->
                if (lv.optString("contentType").contains("VIDEO")) {
                    val id = lv.optString("contentId")
                    val title = lv.obj("metadata", "lockupMetadataViewModel", "title").text()
                    if (id.isNotEmpty() && !title.isNullOrBlank()) {
                        val thumb = bestThumb(lv.obj("contentImage", "thumbnailViewModel", "image")?.optJSONArray("sources"))
                        val author = lv.obj("metadata", "lockupMetadataViewModel", "metadata", "contentMetadataViewModel")
                            ?.optJSONArray("metadataRows")?.optJSONObject(0)
                            ?.optJSONArray("metadataParts")?.optJSONObject(0)
                            ?.optJSONObject("text").text() ?: ""
                        found.putIfAbsent(id, VideoItem(watchUrl(id), title, author, 0L, thumb, false))
                    }
                }
            }
        }
        return found.values.toList()
    }

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** Claves de primer nivel de la respuesta: pista de diagnóstico cuando no se reconoce nada. */
    fun describe(root: JSONObject): String {
        val keys = root.keys().asSequence().take(8).toList()
        return "claves: " + keys.joinToString(", ")
    }
}
