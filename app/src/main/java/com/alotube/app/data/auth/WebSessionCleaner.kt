package com.alotube.app.data.auth

import android.webkit.CookieManager
import android.webkit.WebStorage

/** Borra cookies y datos web del WebView (así no queda una copia sin cifrar de la sesión). */
object WebSessionCleaner {
    fun wipe() {
        try {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies { cm.flush() }
            cm.flush()
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {
            // Sin WebView disponible no hay nada que limpiar
        }
    }
}
