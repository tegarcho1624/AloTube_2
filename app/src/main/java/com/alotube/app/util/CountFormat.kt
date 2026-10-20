package com.alotube.app.util

import java.util.Locale

private val ES = Locale("es", "MX")

/** 2,5 -> "2,5"; 12,0 -> "12"; 238,4 -> "238". */
private fun compact(v: Double): String =
    if (v >= 10 || v % 1.0 == 0.0) v.toLong().toString() else String.format(ES, "%.1f", v)

/** "238 visualizaciones", "122 mil visualizaciones", "2 millones de visualizaciones". null si se desconoce. */
fun formatViews(count: Long): String? = when {
    count < 0 -> null
    count == 1L -> "1 visualización"
    count < 1_000 -> "$count visualizaciones"
    count < 1_000_000 -> "${compact(count / 1_000.0)} mil visualizaciones"
    count < 1_000_000_000 -> {
        val m = count / 1_000_000.0
        if (m < 1.05) "1 millón de visualizaciones" else "${compact(m)} millones de visualizaciones"
    }
    else -> "${compact(count / 1_000_000_000.0)} mil millones de visualizaciones"
}

/** Cifra corta para likes: "5", "1,2 mil", "3 M". null si se desconoce. */
fun formatCount(count: Long): String? = when {
    count < 0 -> null
    count < 1_000 -> count.toString()
    count < 1_000_000 -> "${compact(count / 1_000.0)} mil"
    else -> "${compact(count / 1_000_000.0)} M"
}
