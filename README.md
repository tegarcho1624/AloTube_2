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

## Fase 4: audio en segundo plano
- `PlayerProvider`: el ÚNICO ExoPlayer de la app. `PlaybackService` (MediaSessionService) lo envuelve en una MediaSession -> notificación MediaStyle y controles en pantalla de bloqueo.
- `PlaybackQueue` + `QueueAwarePlayer`: Siguiente/Anterior = video siguiente/anterior de los resultados de búsqueda.
- Audio focus lo gestiona ExoPlayer; el ajuste "Reanudar tras interrupciones" está en Ajustes.
- Permisos: INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, POST_NOTIFICATIONS (se pide al primer inicio en Android 13+), WAKE_LOCK.

## Fase 5: cuenta de Google
- `LoginActivity` + `LoginScreen`: WebView con accounts.google.com; al llegar a youtube.com se leen las cookies (SAPISID y __Secure-3PAPISID obligatorias).
- `SessionStore`: cookies cifradas (EncryptedSharedPreferences). Nunca se guarda la contraseña.
- `NewPipeDownloader`: interceptor que añade la cookie SOLO a youtube.com y subdominios.
- Ajustes > Cuenta de Google: iniciar / cerrar sesión (borra cookies, limpia el WebView y reinicia el estado).

## Fase 7: Biblioteca con datos de la cuenta
- `data/library/InnerTubeClient`: peticiones autenticadas (cookies + SAPISIDHASH) a youtubei `browse`.
- `LibraryRepository`: listas (FEplaylist_aggregation), suscripciones (FEchannels), videos de lista (PlaylistExtractor y, si falla, la API con sesión; "Ver después" = WL, "Me gustan" = LL).
- `LibraryViewModel` + `LibraryScreen`: cada sección carga y falla por separado ("No disponible"), con spinner.

## Fase 8: cuadrícula y pantalla de reproducción
- `VideoGrid` (LazyVerticalGrid, 2 columnas en tablet) y tarjeta `VideoListItem` (Card, duración, título 2 líneas, canal, vistas · fecha).
- `PlayerScreen`: video arriba + `WatchInfo` (título, canal, vistas, fecha, likes) + `VideoActionBar` (Agregar, Fondo, Emergente, Compartir, Reproductor de música, Descargar).

## Fase 9A: descargas
- `DownloadDialog` (nombre, Video/Audio, formato y calidad, idioma de audio) con los formatos reales de StreamInfo.
- `DownloadService` (ForegroundService + OkHttp, descarga por partes con Range, notificación con progreso y botón Cancelar).
- Se guarda en Descargas con MediaStore (minSdk 29: sin permisos de almacenamiento).

## Fase 9B: más calidades y audio .opus
- Video: además de los progresivos (360p), MP4 H.264 hasta 1080p (video + audio m4a unidos con MediaMuxer, sin FFmpeg).
- Audio: Opus de WebM se reempaqueta como Ogg Opus (.opus). Nunca se ofrece .webm.

## Ajustes reestructurados
- `SettingsScreen` = NavHost (Navigation Compose): principal (perfil + Ajustes / Buscar actualizaciones / Descargas) y submenú (Video y audio, Apariencia, Historial y caché, Notificación, Descargar).
- Apariencia = color de acento; Video y audio = "Reanudar tras interrupciones" (misma lógica, otra pantalla).

## Enlaces de YouTube en el buscador
- `YoutubeLinks` detecta youtube.com/watch, youtu.be, m./music.youtube.com, shorts, live, embed (con o sin https, también dentro de un texto).
- Si es un enlace: spinner, StreamInfo y abre el reproductor directo; si falla: "Enlace no válido o video no disponible". Si no, búsqueda normal.

## Apariencia: borrar colores propios
- Mantener presionada una bolita de color propio abre un diálogo de confirmación; los 12 colores básicos no se pueden borrar.
- `ThemeUtils.removeCustom`: quita el color de `accent_custom`; si era el acento activo, vuelve al dorado.

## Búsqueda: canales y scroll infinito
- Pestañas Videos / Canales. Primero se cargan los videos; los canales se piden aparte (filtro CHANNELS del SearchExtractor).
- Paginación (getPage) para ambos tipos; `LoadMoreEffect` en las cuadrículas.
- Tocar un canal: Toast "Abriendo canal: [Nombre] (Próximamente)".

## Pantalla de canal
- `ChannelRepository`: ChannelExtractor (nombre, avatar, banner, suscriptores, descripción) + videos con ChannelTabExtractor (pestaña "videos") y paginación.
- `ChannelScreen` se muestra encima de la navegación principal (estado en `AloTubeRoot`), con pestañas Inicio / Videos / Shorts / Listas.

## Reproductor local (pestaña Video Player)
- Permisos: READ_MEDIA_VIDEO / READ_MEDIA_AUDIO (Android 13+) y READ_EXTERNAL_STORAGE (hasta 12), pedidos al abrir la pestaña.
- `LocalMediaRepository`: MediaStore (Video/Audio), por defecto solo `Download/`; interruptor para ver todo el dispositivo.
- Pestañas Videos / Audios con HorizontalPager; miniaturas con ContentResolver.loadThumbnail.
- Reproducción: el mismo `PlayerActivity`; la "url" es el content:// URI (PlayerViewModel lo reconoce y no usa NewPipeExtractor).
