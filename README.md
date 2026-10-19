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

## Fase 2.1 (SABR)
- NewPipeExtractor v0.26.5 (incluye el workaround SABR de v0.26.3).
- `StreamRepository`: 3 intentos (StreamInfo -> StreamExtractor directo -> StreamInfo tras pausa), fallback a HLS/DASH, y error con el detalle de cada intento.

## Fase 3: identidad visual (estilo Alouncher)
- `ui/theme/`: `Colors.kt` (neutros fijos), `ThemeUtils.kt` (acento dinámico en SharedPreferences "settings", claves `accent` y `accent_custom`; dorado `#C9A24B` por defecto), `FontLoader.kt` + `Type.kt` (fuente de `assets/fonts`), `Theme.kt`.
- Navegación inferior (Inicio / Música / Biblioteca / Ajustes) con íconos de trazo propios. Ajustes permite cambiar el acento (recrea la Activity).
- Controles de Media3 tematizados en `player/PlayerControlsStyle.kt`.
- Pon la fuente del launcher (.ttf/.otf) en `app/src/main/assets/fonts/`.
