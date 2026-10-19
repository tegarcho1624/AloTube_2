package com.alotube.app

import android.app.Application
import com.alotube.app.data.network.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

/** Inicializa NewPipeExtractor una sola vez al arrancar la app. */
class AloTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader, Localization("es", "MX"))
    }
}
