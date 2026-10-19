@file:OptIn(UnstableApi::class)

package com.alotube.app.player

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.alotube.app.data.model.PlaybackOption

/** Convierte una [PlaybackOption] en un MediaSource de Media3. */
object PlayerMediaSources {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val dataSourceFactory by lazy {
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
    }

    fun create(option: PlaybackOption): MediaSource {
        if (option.isHls) {
            return HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(option.videoUrl))
        }
        if (option.isDash) {
            return DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(option.videoUrl))
        }
        val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)
        val video = progressive.createMediaSource(MediaItem.fromUri(option.videoUrl))
        val audioUrl = option.audioUrl ?: return video
        val audio = progressive.createMediaSource(MediaItem.fromUri(audioUrl))
        return MergingMediaSource(video, audio)
    }
}
