package com.alotube.app.data.local

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Cambios pedidos por el usuario. null = ese campo no cambió (no se toca); "" = borrar la etiqueta.
 */
data class TagEdits(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val composer: String? = null,
    val albumArtist: String? = null,
    val track: String? = null,
    val year: String? = null,
    val genre: String? = null
) {
    /** Clave de comentario Vorbis/Opus (en mayúsculas) -> valor. Solo los campos que cambiaron. */
    fun toVorbis(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        title?.let { map["TITLE"] = it }
        artist?.let { map["ARTIST"] = it }
        album?.let { map["ALBUM"] = it }
        composer?.let { map["COMPOSER"] = it }
        albumArtist?.let { map["ALBUMARTIST"] = it }
        track?.let { map["TRACKNUMBER"] = it }
        year?.let { map["DATE"] = it }
        genre?.let { map["GENRE"] = it }
        return map
    }
}

enum class SaveError { NOT_OPUS, PERMISSION, READ, WRITE, VERIFY, STRUCTURE, UNKNOWN }

sealed interface SaveResult {
    /** Etiquetas guardadas y verificadas en el archivo original. */
    data object Saved : SaveResult

    /** No había nada que guardar. */
    data object NoChanges : SaveResult

    /** Android necesita que el usuario autorice modificar este archivo: lanzar [intentSender] y reintentar. */
    class NeedsPermission(val intentSender: IntentSender) : SaveResult

    /**
     * No se guardó. [touched] = se llegó a escribir en el archivo original (aunque se restauró el
     * respaldo): quien reproduce debe recargarlo.
     */
    class Failed(val error: SaveError, val detail: String? = null, val touched: Boolean = false) : SaveResult
}

/**
 * Guarda etiquetas en el archivo de audio ORIGINAL (solo Ogg Opus por ahora; ver [OpusTagWriter]).
 *
 * Flujo, siempre fuera del hilo principal:
 *  1. Comprueba que se puede escribir (si no, pide el permiso del sistema: ver [SaveResult.NeedsPermission]).
 *  2. Copia el original a la caché de la app: es el RESPALDO.
 *  3. Reescribe las etiquetas en otra copia temporal y la verifica (CRC de todas las páginas + etiquetas).
 *  4. Sobrescribe el original con esa copia, lo relee y verifica de nuevo.
 *  5. Si algo falla en 4, restaura el respaldo sobre el original.
 * Se usa el URI de MediaStore (no una ruta): así funciona con el almacenamiento por alcance de Android 10+.
 */
object TagSaver {

    suspend fun save(ctx: Context, uri: Uri, edits: TagEdits): SaveResult = withContext(Dispatchers.IO) {
        try {
            saveBlocking(ctx.applicationContext, uri, edits)
        } catch (e: Exception) {
            SaveResult.Failed(SaveError.UNKNOWN, e.message)
        }
    }

    private fun saveBlocking(app: Context, uri: Uri, edits: TagEdits): SaveResult {
        val vorbis = edits.toVorbis()
        if (vorbis.isEmpty()) return SaveResult.NoChanges
        val resolver = app.contentResolver

        // 1. ¿Se puede escribir? ("rw" no trunca ni cambia nada: solo comprueba el acceso)
        try {
            val fd = resolver.openFileDescriptor(uri, "rw")
                ?: return SaveResult.Failed(SaveError.WRITE, "El sistema no abrió el archivo")
            fd.close()
        } catch (e: SecurityException) {
            return permissionOrFail(app, uri, e)
        } catch (e: FileNotFoundException) {
            // Puede ser "no existe" o "no tienes permiso": si se puede leer, es lo segundo
            val canRead = try {
                resolver.openInputStream(uri)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
            return if (canRead) permissionOrFail(app, uri, e) else SaveResult.Failed(SaveError.READ, e.message)
        }

        val dir = File(app.cacheDir, "tag_edit").apply { mkdirs() }
        val backup = File(dir, "backup.opus")
        val output = File(dir, "new.opus")
        var keepBackup = false
        try {
            // 2. Respaldo del original
            val copied = resolver.openInputStream(uri)?.use { input ->
                backup.outputStream().use { out -> input.copyTo(out) }
                true
            } ?: false
            if (!copied) return SaveResult.Failed(SaveError.READ, "No se pudo leer el archivo")

            // 3. Reescribir etiquetas en una copia y verificarla
            try {
                backup.inputStream().buffered().use { input ->
                    output.outputStream().buffered().use { out -> OpusTagWriter.rewrite(input, out, vorbis) }
                }
                val check = output.inputStream().buffered().use { OpusTagWriter.verify(it) }
                if (!applied(check, vorbis)) return SaveResult.Failed(SaveError.VERIFY, "La copia no quedó bien")
            } catch (e: OpusFormatException) {
                return SaveResult.Failed(if (e.notOpus) SaveError.NOT_OPUS else SaveError.STRUCTURE, e.message)
            }

            // 4. Sobrescribir el original y volver a verificarlo
            try {
                val out = resolver.openOutputStream(uri, "wt")
                    ?: throw IOException("El sistema no abrió el archivo para escribir")
                out.use { stream ->
                    output.inputStream().use { it.copyTo(stream) }
                    stream.flush()
                }
                val after = resolver.openInputStream(uri)?.buffered()?.use { OpusTagWriter.verify(it) }
                    ?: throw IOException("No se pudo releer el archivo guardado")
                if (!applied(after, vorbis)) throw IOException("Las etiquetas no coinciden tras guardar")
            } catch (e: Exception) {
                // 5. Algo falló con el original ya tocado: se restaura el respaldo
                val restored = restore(resolver, uri, backup)
                if (!restored) keepBackup = true
                val kind = if (e is OpusFormatException) SaveError.VERIFY else SaveError.WRITE
                val note = if (restored) "" else " (no se pudo restaurar; respaldo en ${backup.absolutePath})"
                return SaveResult.Failed(kind, (e.message ?: e.javaClass.simpleName) + note, touched = true)
            }
            return SaveResult.Saved
        } finally {
            try {
                output.delete()
                if (!keepBackup) backup.delete()
            } catch (_: Exception) {
            }
        }
    }

    /** Las etiquetas pedidas están en el archivo (y las que se borraron ya no). */
    private fun applied(actual: Map<String, String>, wanted: Map<String, String>): Boolean =
        wanted.all { (key, value) ->
            val found = actual[key.uppercase()]
            if (value.isEmpty()) found == null else found == value
        }

    /** Devuelve true si el original quedó como estaba antes. */
    private fun restore(resolver: android.content.ContentResolver, uri: Uri, backup: File): Boolean = try {
        val out = resolver.openOutputStream(uri, "wt") ?: throw IOException()
        out.use { stream ->
            backup.inputStream().use { it.copyTo(stream) }
            stream.flush()
        }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Android 11+: diálogo del sistema "¿Permitir modificar este archivo?" (MediaStore.createWriteRequest).
     * Android 10: el permiso que trae RecoverableSecurityException. Si no hay forma, error de permiso.
     */
    private fun permissionOrFail(app: Context, uri: Uri, e: Exception): SaveResult {
        val sender: IntentSender? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createWriteRequest(app.contentResolver, listOf(uri)).intentSender
            } else if (e is RecoverableSecurityException) {
                e.userAction.actionIntent.intentSender
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
        return if (sender != null) SaveResult.NeedsPermission(sender)
        else SaveResult.Failed(SaveError.PERMISSION, e.message)
    }
}
