package com.alotube.app.data.model

import org.schabi.newpipe.extractor.Page

/** Canal devuelto por la búsqueda. */
data class ChannelItem(
    val url: String,
    val name: String,
    val avatarUrl: String?,
    /** -1 si se desconoce. */
    val subscriberCount: Long,
    val description: String? = null
)

/** Una página de resultados y, si hay más, la referencia para pedir la siguiente. */
data class SearchPage<T>(val items: List<T>, val nextPage: Page?)
