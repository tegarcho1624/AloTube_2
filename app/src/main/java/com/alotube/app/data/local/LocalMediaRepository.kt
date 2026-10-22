package com.alotube.app.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Comprueba que el archivo siga existiendo y se pueda abrir (los registros de MediaStore pueden quedar viejos). */
fun mediaExists(ctx: Context, uri: Uri): Boolean = try {
    ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
} catch (_: Exception) {
    false
}

/**
 * Lee videos y audios con MediaStore.
 * Por defecto solo los de la carpeta Download/ (donde guarda AloTube); con [includeAll] todo el dispositivo.
 */
class LocalMediaRepository {

    suspend fun load(ctx: Context, includeAll: Boolean): Result<LocalLibrary> = withContext(Dispatchers.IO) {
        try {
            Result.success(
                LocalLibrary(
                    videos = query(ctx, video = true, includeAll = includeAll),
                    audios = query(ctx, video = false, includeAll = includeAll)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun query(ctx: Context, video: Boolean, includeAll: Boolean): List<LocalMedia> {
        val collection = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE
        )
        val selection = if (includeAll) null else "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = if (includeAll) null else arrayOf("Download/%")
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC" // lo más reciente primero

        val result = mutableListOf<LocalMedia>()
        ctx.contentResolver.query(collection, projection, selection, args, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += LocalMedia(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = cursor.getString(nameCol) ?: "",
                    durationMs = cursor.getLong(durCol),
                    size = cursor.getLong(sizeCol),
                    isVideo = video
                )
            }
        }
        return result
    }
}
