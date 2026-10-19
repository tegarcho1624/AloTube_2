# AloTube 2.0 — Fase 1: interfaz y búsqueda

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

## Fase 2 (pendiente)
Reproductor Media3 (ya incluido como dependencia) + obtención de streams con StreamInfo.
