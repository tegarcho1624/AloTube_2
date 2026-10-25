package com.alotube.app.data.local

import android.content.Context

/**
 * Carpetas archivadas de Música y Video Player. SharedPreferences "folder_archive": un conjunto de claves
 * `<tipo>_<bucketId>` (ej. "music_123"). Solo es una marca visual de la app: nunca mueve ni borra archivos.
 * Una carpeta archivada no se ve en su pestaña y aparece en Ajustes > Archivados.
 */
object ArchiveStore {
    private const val PREFS = "folder_archive"
    private const val KEY = "archived"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Clave de una carpeta (ej. "music_123"); igual que [OrderStore.fileKey]. */
    fun key(kind: FolderKind, folderId: Long): String = "${kind.key}_$folderId"

    fun all(ctx: Context): Set<String> = try {
        prefs(ctx).getStringSet(KEY, emptySet())?.toSet().orEmpty()
    } catch (_: Exception) {
        emptySet()
    }

    /** Guarda el conjunto completo de carpetas archivadas. */
    fun save(ctx: Context, keys: Set<String>) {
        try {
            prefs(ctx).edit().putStringSet(KEY, HashSet(keys)).apply()
        } catch (_: Exception) {
            // Sin poder guardar, vale hasta cerrar la app
        }
    }
}
