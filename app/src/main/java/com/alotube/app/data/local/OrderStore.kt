package com.alotube.app.data.local

import android.content.Context

/** Qué pestaña local: Música (audios) o Video Player (videos). Se usa como prefijo de las claves guardadas. */
enum class FolderKind(val key: String) {
    MUSIC("music"),
    VIDEO("video")
}

/**
 * Orden visual elegido por el usuario (arrastrar y soltar) en Música y Video Player.
 * SharedPreferences "local_order", valores = ids separados por comas:
 *  - `folders_music` / `folders_video`        : ids de carpeta (BUCKET_ID) en el orden elegido
 *  - `files_music_<bucketId>` / `files_video_<bucketId>` : ids de archivo (MediaStore _ID) dentro de esa carpeta
 *
 * Solo guarda el ORDEN: nunca mueve ni borra archivos del dispositivo. Los ids que ya no existen se
 * ignoran y los nuevos (sin posición guardada) quedan al final.
 */
object OrderStore {
    private const val PREFS = "local_order"
    private const val FOLDERS = "folders_"
    private const val FILES = "files_"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Clave de una carpeta dentro de `fileOrders` (ej. "music_123"). */
    fun fileKey(kind: FolderKind, bucketId: Long): String = "${kind.key}_$bucketId"

    fun folders(ctx: Context, kind: FolderKind): List<Long> = try {
        parse(prefs(ctx).getString(FOLDERS + kind.key, null))
    } catch (_: Exception) {
        emptyList()
    }

    fun saveFolders(ctx: Context, kind: FolderKind, ids: List<Long>) {
        try {
            prefs(ctx).edit().putString(FOLDERS + kind.key, ids.joinToString(",")).apply()
        } catch (_: Exception) {
            // Sin poder guardar, el orden vale hasta cerrar la app
        }
    }

    /** Todos los órdenes de archivos guardados, por [fileKey]. */
    fun fileOrders(ctx: Context): Map<String, List<Long>> = try {
        val result = HashMap<String, List<Long>>()
        for ((key, value) in prefs(ctx).all) {
            if (key.startsWith(FILES) && value is String) {
                result[key.removePrefix(FILES)] = parse(value)
            }
        }
        result
    } catch (_: Exception) {
        emptyMap()
    }

    fun saveFiles(ctx: Context, kind: FolderKind, bucketId: Long, ids: List<Long>) {
        try {
            prefs(ctx).edit()
                .putString(FILES + fileKey(kind, bucketId), ids.joinToString(","))
                .apply()
        } catch (_: Exception) {
            // Igual que arriba
        }
    }

    private fun parse(csv: String?): List<Long> =
        csv.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
}

/**
 * Ordena según [saved] (lista de ids). Los elementos sin posición guardada van al final, en su orden original.
 * Si [saved] está vacía devuelve la lista tal cual.
 */
fun <T> List<T>.applyOrder(saved: List<Long>, idOf: (T) -> Long): List<T> {
    if (saved.isEmpty() || size < 2) return this
    val position = HashMap<Long, Int>(saved.size * 2)
    saved.forEachIndexed { i, id -> position.putIfAbsent(id, i) }
    val (known, unknown) = partition { position.containsKey(idOf(it)) }
    return known.sortedBy { position.getValue(idOf(it)) } + unknown
}
