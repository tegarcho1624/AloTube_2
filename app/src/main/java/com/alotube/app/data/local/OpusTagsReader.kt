package com.alotube.app.data.local

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Etiquetas de un archivo Ogg Opus (.opus). [picture] = bytes de la imagen incrustada (JPEG/PNG). */
class OpusTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val picture: ByteArray?
)

/**
 * Respaldo de MediaMetadataRetriever para archivos .opus: en algunos dispositivos no lee sus etiquetas
 * (Vorbis comments dentro de la cabecera "OpusTags"). Esto lee SOLO las primeras páginas Ogg del archivo.
 * Cualquier cosa rara (no es Ogg Opus, archivo cortado...) devuelve null: nunca lanza.
 */
object OpusTagsReader {

    private const val MAX_TAGS_BYTES = 16 * 1024 * 1024
    private const val MAX_PAGES = 512

    fun read(input: InputStream): OpusTags? = try {
        parse(DataInputStream(input))
    } catch (_: EOFException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun parse(input: DataInputStream): OpusTags? {
        // Página 1: debe empezar con "OpusHead"
        val first = readPage(input) ?: return null
        if (!first.payload.startsWith("OpusHead")) return null

        // Siguientes páginas: el 2.º paquete ("OpusTags") puede ocupar varias páginas (carátula grande)
        val packet = java.io.ByteArrayOutputStream()
        var pages = 0
        var complete = false
        while (!complete && pages < MAX_PAGES) {
            val page = readPage(input) ?: return null
            pages++
            packet.write(page.payload)
            if (packet.size() > MAX_TAGS_BYTES) return null
            complete = page.endsPacket
        }
        if (!complete) return null
        val bytes = packet.toByteArray()
        if (!bytes.startsWith("OpusTags")) return null
        return parseTags(bytes)
    }

    private class Page(val payload: ByteArray, val endsPacket: Boolean)

    /** Lee una página Ogg. [Page.endsPacket] = el último segmento mide menos de 255 (el paquete termina). */
    private fun readPage(input: DataInputStream): Page? {
        val header = ByteArray(27)
        input.readFully(header)
        if (header[0] != 'O'.code.toByte() || header[1] != 'g'.code.toByte() ||
            header[2] != 'g'.code.toByte() || header[3] != 'S'.code.toByte()
        ) return null
        val segmentCount = header[26].toInt() and 0xFF
        val table = ByteArray(segmentCount)
        input.readFully(table)
        var size = 0
        for (b in table) size += b.toInt() and 0xFF
        val payload = ByteArray(size)
        input.readFully(payload)
        val last = if (segmentCount == 0) 0 else table[segmentCount - 1].toInt() and 0xFF
        return Page(payload, endsPacket = segmentCount > 0 && last < 255)
    }

    private fun parseTags(data: ByteArray): OpusTags? {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(8) // "OpusTags"
        val vendorLength = buf.int
        if (vendorLength < 0 || vendorLength > buf.remaining()) return null
        buf.position(buf.position() + vendorLength)
        val count = buf.int
        if (count < 0) return null

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var picture: ByteArray? = null

        for (i in 0 until count) {
            if (buf.remaining() < 4) break
            val length = buf.int
            if (length < 0 || length > buf.remaining()) break
            val raw = ByteArray(length)
            buf.get(raw)
            val text = String(raw, Charsets.UTF_8)
            val eq = text.indexOf('=')
            if (eq <= 0) continue
            val key = text.substring(0, eq).uppercase()
            val value = text.substring(eq + 1)
            when (key) {
                "TITLE" -> if (title == null) title = value
                "ARTIST" -> if (artist == null) artist = value
                "ALBUMARTIST" -> if (albumArtist == null) albumArtist = value
                "ALBUM" -> if (album == null) album = value
                "METADATA_BLOCK_PICTURE" -> if (picture == null) picture = decodePicture(value)
            }
        }
        return OpusTags(title, artist ?: albumArtist, album, picture)
    }

    /** METADATA_BLOCK_PICTURE = bloque "picture" de FLAC en base64. Devuelve solo los bytes de la imagen. */
    private fun decodePicture(base64: String): ByteArray? = try {
        val block = ByteBuffer.wrap(Base64.getMimeDecoder().decode(base64)).order(ByteOrder.BIG_ENDIAN)
        block.int // tipo de imagen
        val mimeLength = block.int
        block.position(block.position() + mimeLength)
        val descriptionLength = block.int
        block.position(block.position() + descriptionLength)
        block.position(block.position() + 16) // ancho, alto, profundidad, colores
        val dataLength = block.int
        if (dataLength <= 0 || dataLength > block.remaining()) {
            null
        } else {
            ByteArray(dataLength).also { block.get(it) }
        }
    } catch (_: Exception) {
        null
    }

    private fun ByteArray.startsWith(text: String): Boolean {
        if (size < text.length) return false
        for (i in text.indices) if (this[i] != text[i].code.toByte()) return false
        return true
    }
}
