package com.alotube.app.data.local

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** El archivo no es un Ogg Opus válido ([notOpus] = ni siquiera empieza como Opus) o su estructura no es editable. */
class OpusFormatException(val notOpus: Boolean, message: String) : Exception(message)

/**
 * Escritor propio de etiquetas para archivos Ogg Opus (.opus). Jaudiotagger NO soporta Opus, así que esto
 * reescribe solo el 2.º paquete ("OpusTags", comentarios Vorbis) y deja el audio intacto:
 *
 *  1. Página 1 ("OpusHead"): se copia tal cual.
 *  2. Páginas de "OpusTags": se juntan, se cambian solo los campos pedidos (el resto, incluida la
 *     carátula METADATA_BLOCK_PICTURE y el "vendor", se conserva byte a byte) y se vuelven a paginar.
 *  3. Páginas de audio: se copian tal cual. Solo si cambió el número de páginas de etiquetas se
 *     renumera su secuencia y se recalcula el CRC de cada una (los datos de audio no se tocan).
 *
 * Trabaja en flujo (una página a la vez), sin cargar el archivo completo en memoria.
 * [verify] relee un archivo, comprueba el CRC de todas las páginas y devuelve sus etiquetas.
 */
object OpusTagWriter {

    private const val HEADER_FIXED = 27
    private const val MAX_TAG_PAGES = 4096

    private class Page(val header: ByteArray, val data: ByteArray) {
        val headerType: Int get() = header[5].toInt() and 0xFF
        val serial: Int get() = int32(header, 14)
        val seq: Int get() = int32(header, 18)
        val storedCrc: Int get() = int32(header, 22)
        val segments: Int get() = header[26].toInt() and 0xFF
        fun lacing(i: Int): Int = header[HEADER_FIXED + i].toInt() and 0xFF
    }

    private class Comments(val vendor: ByteArray, val entries: List<ByteArray>)

    // ------------------------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------------------------

    /**
     * Copia [input] en [output] cambiando las etiquetas de [edits] (CLAVE en mayúsculas -> valor; valor
     * vacío = borrar esa etiqueta). Lanza [OpusFormatException] si no es un Ogg Opus editable.
     */
    fun rewrite(input: InputStream, output: OutputStream, edits: Map<String, String>) {
        val editKeys = edits.keys.map { it.uppercase() }.toSet()

        val first = readPage(input) ?: throw OpusFormatException(true, "Archivo vacío")
        if ((first.headerType and 0x02) == 0 || !first.data.startsWithAscii("OpusHead")) {
            throw OpusFormatException(true, "No es un archivo Ogg Opus")
        }
        val serial = first.serial
        writePage(output, first)

        // Paquete "OpusTags" (puede ocupar varias páginas si lleva una carátula grande)
        val packetBytes = ByteArrayOutputStream()
        var oldPages = 0
        var complete = false
        while (!complete) {
            val page = readPage(input) ?: throw OpusFormatException(false, "Falta el paquete OpusTags")
            if (page.serial != serial) throw OpusFormatException(false, "Flujos mezclados")
            val continued = (page.headerType and 0x01) != 0
            // La primera página de etiquetas no es continuación; las siguientes sí
            if ((oldPages == 0) == continued) throw OpusFormatException(false, "Páginas de etiquetas desordenadas")
            packetBytes.write(page.data)
            oldPages++
            if (oldPages > MAX_TAG_PAGES) throw OpusFormatException(false, "Etiquetas demasiado grandes")
            for (i in 0 until page.segments) {
                if (page.lacing(i) < 255) {
                    // El audio debe empezar en una página nueva (así lo exige el formato Opus)
                    if (i != page.segments - 1) {
                        throw OpusFormatException(false, "Audio dentro de la página de etiquetas")
                    }
                    complete = true
                    break
                }
            }
        }

        val comments = parseTags(packetBytes.toByteArray())
        val kept = comments.entries.filter { keyOf(it) !in editKeys }
        val added = edits.filter { it.value.isNotEmpty() }
            .map { "${it.key.uppercase()}=${it.value}".toByteArray(Charsets.UTF_8) }
        val newPacket = buildTags(comments.vendor, kept + added)

        val newPages = paginate(newPacket, serial, first.seq + 1)
        for (page in newPages) writePage(output, page)
        val delta = newPages.size - oldPages

        // Audio: tal cual (renumerando solo si cambió la cantidad de páginas de etiquetas)
        while (true) {
            val page = readPage(input) ?: break
            if (delta != 0 && page.serial == serial) {
                putInt32(page.header, 18, page.seq + delta)
                putInt32(page.header, 22, crc(page.header, page.data))
            }
            writePage(output, page)
        }
        output.flush()
    }

    /**
     * Comprueba que el archivo es un Ogg Opus íntegro (CRC correcto en TODAS las páginas) y devuelve sus
     * etiquetas (CLAVE en mayúsculas -> primer valor). Lanza [OpusFormatException] si algo no cuadra.
     */
    fun verify(input: InputStream): Map<String, String> {
        var pages = 0
        var serial0 = 0
        var tagsDone = false
        val packet = ByteArrayOutputStream()
        while (true) {
            val page = readPage(input) ?: break
            if (page.storedCrc != crc(page.header, page.data)) {
                throw OpusFormatException(false, "CRC incorrecto en la página $pages")
            }
            if (pages == 0) {
                if ((page.headerType and 0x02) == 0 || !page.data.startsWithAscii("OpusHead")) {
                    throw OpusFormatException(true, "No es un archivo Ogg Opus")
                }
                serial0 = page.serial
            } else if (!tagsDone && page.serial == serial0) {
                packet.write(page.data)
                for (i in 0 until page.segments) {
                    if (page.lacing(i) < 255) {
                        tagsDone = true
                        break
                    }
                }
            }
            pages++
        }
        if (pages < 2 || !tagsDone) throw OpusFormatException(false, "Faltan las etiquetas")
        val result = LinkedHashMap<String, String>()
        for (entry in parseTags(packet.toByteArray()).entries) {
            val eq = entry.indexOf('='.code.toByte())
            if (eq <= 0) continue
            val key = String(entry, 0, eq, Charsets.UTF_8).uppercase()
            if (!result.containsKey(key)) {
                result[key] = String(entry, eq + 1, entry.size - eq - 1, Charsets.UTF_8)
            }
        }
        return result
    }

    // ------------------------------------------------------------------------------------------
    // Páginas Ogg
    // ------------------------------------------------------------------------------------------

    /** null = fin limpio del archivo. */
    private fun readPage(input: InputStream): Page? {
        val fixed = ByteArray(HEADER_FIXED)
        val n = readFully(input, fixed, 0, HEADER_FIXED)
        if (n == 0) return null
        if (n < HEADER_FIXED) throw OpusFormatException(false, "Archivo cortado")
        if (fixed[0] != 'O'.code.toByte() || fixed[1] != 'g'.code.toByte() ||
            fixed[2] != 'g'.code.toByte() || fixed[3] != 'S'.code.toByte()
        ) throw OpusFormatException(true, "No es un archivo Ogg")
        val segments = fixed[26].toInt() and 0xFF
        val header = fixed.copyOf(HEADER_FIXED + segments)
        if (readFully(input, header, HEADER_FIXED, segments) < segments) {
            throw OpusFormatException(false, "Archivo cortado")
        }
        var size = 0
        for (i in 0 until segments) size += header[HEADER_FIXED + i].toInt() and 0xFF
        val data = ByteArray(size)
        if (readFully(input, data, 0, size) < size) throw OpusFormatException(false, "Archivo cortado")
        return Page(header, data)
    }

    private fun writePage(output: OutputStream, page: Page) {
        output.write(page.header)
        output.write(page.data)
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val n = input.read(buffer, offset + total, length - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    /** Parte [packet] en páginas Ogg (máx. 255 segmentos cada una) con CRC correcto. */
    private fun paginate(packet: ByteArray, serial: Int, startSeq: Int): List<Page> {
        val totalSegments = packet.size / 255 + 1 // el último mide menos de 255 (puede ser 0)
        val pages = ArrayList<Page>()
        var segment = 0
        var offset = 0
        var seq = startSeq
        while (segment < totalSegments) {
            val count = minOf(255, totalSegments - segment)
            val last = segment + count >= totalSegments
            val dataLength = if (last) packet.size - offset else count * 255
            val header = ByteArray(HEADER_FIXED + count)
            header[0] = 'O'.code.toByte()
            header[1] = 'g'.code.toByte()
            header[2] = 'g'.code.toByte()
            header[3] = 'S'.code.toByte()
            header[4] = 0 // versión
            header[5] = (if (segment == 0) 0x00 else 0x01).toByte() // continuación si no es la primera
            // Granule: 0 en la página donde termina el paquete de cabecera; -1 si el paquete sigue
            val granule = if (last) 0L else -1L
            for (b in 0 until 8) header[6 + b] = ((granule ushr (8 * b)) and 0xFFL).toByte()
            putInt32(header, 14, serial)
            putInt32(header, 18, seq)
            header[26] = count.toByte()
            for (i in 0 until count) {
                val index = segment + i
                header[HEADER_FIXED + i] =
                    (if (index < totalSegments - 1) 255 else packet.size % 255).toByte()
            }
            val data = packet.copyOfRange(offset, offset + dataLength)
            putInt32(header, 22, crc(header, data))
            pages.add(Page(header, data))
            offset += dataLength
            segment += count
            seq++
        }
        return pages
    }

    // ------------------------------------------------------------------------------------------
    // OpusTags
    // ------------------------------------------------------------------------------------------

    private fun parseTags(packet: ByteArray): Comments {
        if (packet.size < 16 || !packet.startsWithAscii("OpusTags")) {
            throw OpusFormatException(false, "Paquete OpusTags no válido")
        }
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(8)
        val vendorLength = buf.int
        if (vendorLength < 0 || vendorLength > buf.remaining() - 4) {
            throw OpusFormatException(false, "Etiquetas dañadas")
        }
        val vendor = ByteArray(vendorLength)
        buf.get(vendor)
        val count = buf.int
        if (count < 0) throw OpusFormatException(false, "Etiquetas dañadas")
        val entries = ArrayList<ByteArray>()
        for (i in 0 until count) {
            if (buf.remaining() < 4) throw OpusFormatException(false, "Etiquetas dañadas")
            val length = buf.int
            if (length < 0 || length > buf.remaining()) throw OpusFormatException(false, "Etiquetas dañadas")
            val raw = ByteArray(length)
            buf.get(raw)
            entries.add(raw)
        }
        return Comments(vendor, entries)
    }

    private fun buildTags(vendor: ByteArray, entries: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("OpusTags".toByteArray(Charsets.US_ASCII))
        out.write(le32(vendor.size))
        out.write(vendor)
        out.write(le32(entries.size))
        for (entry in entries) {
            out.write(le32(entry.size))
            out.write(entry)
        }
        return out.toByteArray()
    }

    /** Clave (antes del "=") en mayúsculas; "" si la entrada no tiene "=". */
    private fun keyOf(entry: ByteArray): String {
        val eq = entry.indexOf('='.code.toByte())
        return if (eq <= 0) "" else String(entry, 0, eq, Charsets.UTF_8).uppercase()
    }

    // ------------------------------------------------------------------------------------------
    // Utilidades de bytes y CRC de Ogg (polinomio 0x04C11DB7, sin reflejar, valor inicial 0)
    // ------------------------------------------------------------------------------------------

    private val CRC_TABLE: IntArray = IntArray(256).also { table ->
        for (i in 0 until 256) {
            var r = i shl 24
            repeat(8) {
                r = if ((r and 0x80000000.toInt()) != 0) (r shl 1) xor 0x04C11DB7 else r shl 1
            }
            table[i] = r
        }
    }

    /** CRC de la página con el campo CRC (bytes 22..25) tomado como cero. */
    private fun crc(header: ByteArray, data: ByteArray): Int {
        var c = 0
        for (i in header.indices) {
            val b = if (i in 22..25) 0 else header[i].toInt() and 0xFF
            c = (c shl 8) xor CRC_TABLE[((c ushr 24) and 0xFF) xor b]
        }
        for (byte in data) {
            c = (c shl 8) xor CRC_TABLE[((c ushr 24) and 0xFF) xor (byte.toInt() and 0xFF)]
        }
        return c
    }

    private fun int32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun putInt32(bytes: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) bytes[offset + i] = ((value ushr (8 * i)) and 0xFF).toByte()
    }

    private fun le32(value: Int): ByteArray = ByteArray(4).also { putInt32(it, 0, value) }

    private fun ByteArray.startsWithAscii(text: String): Boolean {
        if (size < text.length) return false
        for (i in text.indices) if (this[i] != text[i].code.toByte()) return false
        return true
    }
}
