package com.alotube.app.data.library

data class LibraryPlaylist(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null
)

data class LibraryChannel(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null
)

/** Resultado de una sección. [note] solo se rellena si vino vacía (ayuda a diagnosticar). */
data class LibraryResult<T>(val items: List<T>, val note: String? = null)
