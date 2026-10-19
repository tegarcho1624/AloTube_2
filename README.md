# AloTube 2.0 — Fase 2: reproductor + PiP (incluye Fase 1: búsqueda)

App nativa Android (Kotlin + Jetpack Compose + Material 3) para buscar videos de YouTube
sin anuncios ni telemetría. Único permiso: INTERNET.

## Estructura
- `data/network`    → Downloader (OkHttp) que pide NewPipeExtractor
- `data/repository` → VideoRepository: única clase que usa NewPipeExtractor
- `data/model`      → VideoItem
- `viewmodel`       → SearchViewModel (estado, errores)
- `ui/screens`, `ui/components`, `ui/theme` → Compose

## Compilar
GitHub Actions (`.github/workflows/build.yml`) genera el APK debug:
pestaña Actions → "Build APK" → Artifacts → AloTube-debug-apk.

## Fase 2
- `PlayerActivity`: pantalla completa, Media3, PiP (`supportsPictureInPicture`, `onUserLeaveHint`, `onPictureInPictureModeChanged`).
- `StreamRepository`: StreamInfo -> mejor video (video-only + audio, unidos con MergingMediaSource) y flujo compatible de respaldo.
- `PlayerViewModel`: estado, calidad elegida, reintento y respaldo automático.
- Errores: mensaje en pantalla con botones Volver / Reintentar.
