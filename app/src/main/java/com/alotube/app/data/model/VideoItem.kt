package com.alotube.app.data.model

/** Resultado de búsqueda ya limpio, independiente de NewPipeExtractor. */
data class VideoItem(
    val url: String,
    val title: String,
    val author: String,
    /** Duración en segundos; <= 0 si es desconocida o es un directo. */
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val isLive: Boolean,
    /** Vistas; -1 si se desconocen. */
    val viewCount: Long = -1L,
    /** Fecha relativa tal como la da YouTube ("hace 4 días"). */
    val uploadDateText: String? = null
)
