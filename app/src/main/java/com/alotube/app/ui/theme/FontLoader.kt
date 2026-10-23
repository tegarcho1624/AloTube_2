package com.alotube.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Una fuente de la lista: [id] = valor guardado en `font` ("sys:serif", "file:Nombre.ttf"), [label] sin extensión. */
class FontEntry(val id: String, val label: String, val family: FontFamily)

/**
 * Carga la fuente de la app, con el mismo criterio que FontManager de Alouncher.
 *
 * SharedPreferences "settings", clave `font`:
 *  - `sys:serif` / `sys:sans-serif` / `sys:monospace` / `sys:cursive` -> fuente del sistema
 *  - `file:Nombre.ttf` -> archivo en assets/fonts/ (o en files/fonts/ si se importó)
 *  - sin valor -> la primera .ttf/.otf (orden alfabético) de assets/fonts/
 *
 * Si no hay fuente, el archivo falta o es inválido: cae a la fuente predeterminada, sin errores.
 */
object FontLoader {

    private const val PREFS = "settings"
    private const val KEY_FONT = "font"
    private const val DIR = "fonts"

    private val _version = MutableStateFlow(0)

    /** Sube cuando se elige o importa una fuente: el tema lo observa y reconstruye la tipografía. */
    val version: StateFlow<Int> = _version

    private val SYSTEM = listOf(
        "sys:default" to "Predeterminada",
        "sys:sans-serif" to "Sans Serif",
        "sys:serif" to "Serif",
        "sys:monospace" to "Monospace",
        "sys:cursive" to "Cursive"
    )

    private fun fontsDir(ctx: Context) = File(ctx.filesDir, DIR)

    /** Valor realmente en uso: lo guardado o, sin guardar, la primera fuente de assets (o la del sistema). */
    fun currentId(ctx: Context): String {
        val saved = try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FONT, null)
        } catch (_: Exception) {
            null
        }
        if (saved != null && (saved.startsWith("sys:") || (saved.startsWith("file:") && fromName(ctx, saved.removePrefix("file:")) != null))) {
            return saved
        }
        val first = bundledNames(ctx).firstOrNull()?.takeIf { fromName(ctx, it) != null }
        return if (first != null) "file:$first" else "sys:default"
    }

    private fun bundledNames(ctx: Context): List<String> = try {
        ctx.assets.list(DIR)?.filter { isFontFile(it) }?.sorted() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun importedNames(ctx: Context): List<String> = try {
        fontsDir(ctx).listFiles()?.filter { it.isFile && isFontFile(it.name) }?.map { it.name }?.sorted()
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** Todas las fuentes disponibles (assets + importadas + del sistema), cada una con su familia para la vista previa. */
    fun list(ctx: Context): List<FontEntry> {
        val out = ArrayList<FontEntry>()
        val seen = HashSet<String>()
        for (name in bundledNames(ctx) + importedNames(ctx)) {
            if (!seen.add(name)) continue
            val family = fromName(ctx, name) ?: continue // archivo dañado: no se lista
            out.add(FontEntry("file:$name", name.substringBeforeLast('.'), family))
        }
        for ((id, label) in SYSTEM) out.add(FontEntry(id, label, systemFamily(id.removePrefix("sys:"))))
        return out
    }

    /** Guarda la elección y avisa al tema para que cambie la tipografía de toda la app al instante. */
    fun select(ctx: Context, id: String) {
        try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_FONT, id).apply()
        } catch (_: Exception) {
        }
        _version.value = _version.value + 1
    }

    /**
     * Copia una .ttf/.otf elegida por el usuario a files/fonts/. Devuelve el nombre del archivo copiado,
     * o null si no es una fuente válida (extensión o contenido incorrecto).
     */
    suspend fun import(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        var tmp: File? = null
        try {
            val app = ctx.applicationContext
            val shown = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: return@withContext null
            if (!isFontFile(shown)) return@withContext null

            val dir = fontsDir(app).apply { mkdirs() }
            val safe = shown.replace(Regex("[^A-Za-z0-9._ -]"), "_")
            val base = safe.substringBeforeLast('.')
            val ext = safe.substringAfterLast('.').lowercase()
            val file = File(dir, "import.tmp")
            tmp = file
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { out -> input.copyTo(out) }
            } ?: return@withContext null

            // Cabecera de un archivo de fuente: TrueType, OpenType (OTTO), true, ttcf
            val head = ByteArray(4)
            val read = file.inputStream().use { it.read(head) }
            val tag = String(head, Charsets.ISO_8859_1)
            val magicOk = read == 4 && (tag == "OTTO" || tag == "true" || tag == "ttcf" ||
                (head[0].toInt() == 0 && head[1].toInt() == 1 && head[2].toInt() == 0 && head[3].toInt() == 0))
            if (!magicOk || Typeface.Builder(file).build() == null) return@withContext null

            // Nombre libre: no pisa una fuente de assets ni otra importada
            val taken = (bundledNames(app) + importedNames(app)).toSet()
            var name = "$base.$ext"
            var n = 1
            while (name in taken) name = "${base}_${n++}.$ext"
            val target = File(dir, name)
            if (!file.renameTo(target)) return@withContext null
            tmp = null
            _version.value = _version.value + 1
            name
        } catch (_: Exception) {
            null
        } finally {
            try {
                tmp?.delete()
            } catch (_: Exception) {
            }
        }
    }

    fun load(ctx: Context): FontFamily {
        val choice = try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FONT, null)
        } catch (_: Exception) {
            null
        }

        return when {
            choice != null && choice.startsWith("sys:") -> systemFamily(choice.removePrefix("sys:"))
            choice != null && choice.startsWith("file:") ->
                fromName(ctx, choice.removePrefix("file:")) ?: firstBundled(ctx) ?: FontFamily.Default
            else -> firstBundled(ctx) ?: FontFamily.Default
        }
    }

    private fun systemFamily(name: String): FontFamily = when (name) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "sans-serif" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }

    private fun isFontFile(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".ttf") || n.endsWith(".otf")
    }

    private fun firstBundled(ctx: Context): FontFamily? = try {
        ctx.assets.list(DIR)
            ?.filter { isFontFile(it) }
            ?.minOrNull()
            ?.let { fromName(ctx, it) }
    } catch (_: Exception) {
        null
    }

    private fun fromName(ctx: Context, name: String): FontFamily? {
        if (!isFontFile(name)) return null
        // 1) assets/fonts/
        try {
            val path = "$DIR/$name"
            Typeface.createFromAsset(ctx.assets, path) // lanza si no existe
            return FontFamily(Font(path, ctx.assets, FontWeight.Normal, FontStyle.Normal))
        } catch (_: Exception) {
            // no está en assets: se prueba files/fonts/
        }
        // 2) files/fonts/ (fuentes importadas)
        return try {
            val file = File(File(ctx.filesDir, DIR), name)
            if (file.isFile) {
                Typeface.createFromFile(file)
                FontFamily(Font(file, FontWeight.Normal, FontStyle.Normal))
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
