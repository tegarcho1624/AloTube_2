package com.alotube.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Reempaquetado con las clases nativas de Android (sin FFmpeg y sin recodificar: solo se copian
 * las muestras de un contenedor a otro, así que es rápido y no pierde calidad).
 */
object MediaRemuxer {

    private const val MIN_BUFFER = 2 * 1024 * 1024

    /** Audio WebM (Opus) -> Ogg Opus (.opus). Requiere Android 10+ (MUXER_OUTPUT_OGG). */
    fun webmOpusToOgg(input: File, output: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val track = findTrack(extractor, "audio/")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            val outTrack = muxer.addTrack(format)
            muxer.start()

            val buffer = ByteBuffer.allocate(bufferSize(format))
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, flagsOf(extractor))
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    /** Video H.264 (mp4, sin audio) + audio AAC (m4a) -> un MP4. Las muestras se intercalan por tiempo. */
    fun mergeToMp4(videoFile: File, audioFile: File, output: File) {
        val videoEx = MediaExtractor()
        val audioEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            videoEx.setDataSource(videoFile.absolutePath)
            audioEx.setDataSource(audioFile.absolutePath)

            val vTrack = findTrack(videoEx, "video/")
            val aTrack = findTrack(audioEx, "audio/")
            videoEx.selectTrack(vTrack)
            audioEx.selectTrack(aTrack)
            val vFormat = videoEx.getTrackFormat(vTrack)
            val aFormat = audioEx.getTrackFormat(aTrack)

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vOut = muxer.addTrack(vFormat)
            val aOut = muxer.addTrack(aFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxOf(bufferSize(vFormat), bufferSize(aFormat)))
            val info = MediaCodec.BufferInfo()

            var vTime = videoEx.sampleTime // -1 cuando ya no quedan muestras
            var aTime = audioEx.sampleTime
            while (vTime >= 0 || aTime >= 0) {
                val useVideo = vTime >= 0 && (aTime < 0 || vTime <= aTime)
                val extractor = if (useVideo) videoEx else audioEx
                val outTrack = if (useVideo) vOut else aOut

                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    // Pista terminada antes de lo previsto
                    if (useVideo) vTime = -1 else aTime = -1
                    continue
                }
                info.set(0, size, extractor.sampleTime, flagsOf(extractor))
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()

                if (useVideo) vTime = videoEx.sampleTime else aTime = audioEx.sampleTime
            }
            muxer.stop()
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            videoEx.release()
            audioEx.release()
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith(mimePrefix)) return i
        }
        throw IOException("No se encontró una pista de $mimePrefix en el archivo")
    }

    private fun bufferSize(format: MediaFormat): Int {
        val declared = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else 0
        return maxOf(declared, MIN_BUFFER)
    }

    private fun flagsOf(extractor: MediaExtractor): Int =
        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else 0
}
