package com.alotube.app.data.model

/** Datos del encabezado de un canal (ChannelExtractor). */
data class ChannelDetails(
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    /** -1 si se desconoce. */
    val subscriberCount: Long,
    val description: String?
)
