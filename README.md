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

## Reproductor local: Música y carpetas de video
- **Música** (pestaña 2, `MusicScreen`): audios locales con carátula, nombre y duración. Sin pestañas.
- **Video Player** (`LocalPlayerScreen`): gestor de carpetas. Nivel 1 = cuadrícula de carpetas con videos (`LocalFolderCard`); nivel 2 = videos de la carpeta, con flecha atrás y "Inicio > Carpeta". El botón Atrás del sistema también vuelve a las carpetas.
- `LocalMediaRepository` lee `BUCKET_ID` y `BUCKET_DISPLAY_NAME`; `LocalMediaViewModel` guarda `currentFolderId` (null = carpetas) y comparte la lectura de MediaStore entre ambas pestañas.
- `LocalScreenParts.kt`: permisos, encabezado y reproducción segura compartidos por las dos pantallas.

## Interfaz de audio (pestaña Música)
- `data/local/AudioMetadata.kt`: `AudioMetadataRepository` lee título, artista, álbum y carátula con `MediaMetadataRetriever` en segundo plano (máx. 3 lecturas a la vez, caché en memoria). Sin etiquetas: el nombre del archivo es el título y el artista es "Artista Desconocido". Sin carátula incrustada se pide la del sistema (`loadThumbnail`).
- `MusicScreen`: carpetas de audio (carátula del primer audio que la tenga, o nota musical con el acento) y, dentro, filas con carátula, título en negrita, artista y duración. La canción que suena se ilumina y muestra un ecualizador animado (`AudioComponents.kt`). Estado: `currentAudioFolderId` en `LocalMediaViewModel`.
- `PlayerScreen`: si el archivo es un audio local muestra título / artista / álbum y la carátula grande en el centro.
- Mini-reproductor (`MiniPlayer.kt`, en `AloTubeRoot` sobre la barra de navegación): carátula, título, artista, Play/Pausa y Siguiente; al tocarlo abre el reproductor completo. `player/NowPlaying.kt` solo LEE el estado del ExoPlayer (cada 300 ms) y `LocalAudioController` hace Pausa/Siguiente cuando la pantalla completa está cerrada.
- Cambio mínimo en `PlayerActivity`: al cerrar la pantalla completa con MÚSICA LOCAL el audio sigue sonando (con mini-reproductor y notificación); con video o YouTube se detiene como siempre. Si ya suena el mismo archivo al abrir el reproductor, no se reinicia.

## Reproductor local (pestaña Video Player)
- Permisos: READ_MEDIA_VIDEO / READ_MEDIA_AUDIO (Android 13+) y READ_EXTERNAL_STORAGE (hasta 12), pedidos al abrir la pestaña.
- `LocalMediaRepository`: MediaStore (Video/Audio); por defecto lee todo el dispositivo (sin selector en la interfaz), agrupado por carpeta.
- Pestañas Videos / Audios con HorizontalPager; miniaturas con ContentResolver.loadThumbnail.
- Reproducción: el mismo `PlayerActivity`; la "url" es el content:// URI (PlayerViewModel lo reconoce y no usa NewPipeExtractor).

### Pantalla de reproducción de música
- `AudioPlayerScreen.kt` (`AudioPlayerContent`): fila de 7 botones (⋮, −10 s, anterior, play, siguiente, +10 s, cola; ⋮ y cola aún son avisos "Próximamente"); carátula grande, título / artista / álbum, barra de progreso con tiempo actual y total, controles (anterior, −10 s, play/pausa, +10 s, siguiente) en el acento dinámico; Emergente y Compartir quedan más abajo. Se desplaza si no cabe. Solo usa los métodos normales del `Player`.
- `OpusTagsReader.kt`: respaldo que lee las etiquetas y la carátula de archivos `.opus` cuando `MediaMetadataRetriever` no las entrega.

## Fondos personalizados
- Ajustes > Apariencia > **Fondo >** abre **Fondos** (Youtube [Inicio y Biblioteca], Música, Video Player, Ajustes). Tocar una abre el Photo Picker; mantener presionado quita el fondo.
- `BackgroundStore.kt`: copia la imagen (≤1920 px, JPEG) a `filesDir/backgrounds/` y guarda la ruta en SharedPreferences `backgrounds` (clave por pantalla: youtube, music, video, settings).
- `BackgroundLayer.kt`: imagen + capa negra al 60 %; sin imagen queda el fondo oscuro de siempre. Se usa en `AloTubeRoot` (por pestaña) y en `PlayerScreen` (música local → Música, video local → Video Player).

### Fondos: modos de escalado
- Mantener presionada una opción de Fondos abre un menú: **Rellenar** (`Crop`), **Estirar** (`FillBounds`), **Centrar** (`Fit`) y **Eliminar fondo** (rojo).
- Se guarda en `backgrounds` junto a la ruta: `<pantalla>_scale` = `Crop` / `FillBounds` / `Fit`. Imagen nueva = `Crop`. Eliminar borra ruta, modo y el archivo.
- ✓ junto al nombre en Fondos cuando esa pantalla ya tiene imagen.

## Estilo de Escritura (fuentes)
- Apariencia: Fondo > / **Estilo de Escritura (Estilos >)** / Color de acento. La pantalla de fuentes lista las de `assets/fonts/`, las importadas (`files/fonts/`) y 5 del sistema, cada nombre (sin extensión) en su propia tipografía; ✓ en la elegida; "+" importa .ttf/.otf.
- Se guarda en `settings`, clave `font` (`sys:*` o `file:Nombre.ttf`), igual que Alouncher. `FontLoader.version` hace que `AloTubeTheme` reconstruya la tipografía de toda la app al instante.
- Sin elección: primera fuente de `assets/fonts/` (como antes) o la del sistema. Archivo borrado/dañado: vuelve a ese valor por defecto sin errores. Archivo no válido al importar: Toast.

### Fondos: propagación a sub-pantallas
- `AppBackground(scope) { ... }` (en `BackgroundLayer.kt`): envoltorio reutilizable; la pantalla de dentro debe ir transparente. Se usa en el Canal; el detalle del video de YouTube usa el fondo "Youtube"; todo el submenú de Ajustes (menú, Apariencia, Video y audio, Fondos, Estilo de Escritura, etc.) usa el fondo "Ajustes".

### Controles de audio (v2)
- Mini-reproductor: [Play/Pausa] [Anterior] [Siguiente]. Anterior reinicia la canción si llevas >3 s (o es la primera) y si no pasa a la anterior de la cola (`LocalAudioController.previous`).
- Pantalla completa: `⋮  Shuffle  −10s  ◀  PLAY  ▶  +10s  Repeat`. Se eliminó el botón de cola. ⋮, Shuffle y Repeat solo muestran un Toast ("Próximamente").

### Controles de audio: fila de 8 y mini-reproductor
- Pantalla completa: `⋮  Barajear  −10 s  ◀  PLAY  ▶  +10 s  Repetir`. Barajear y Repetir solo muestran el aviso "Próximamente" (no tocan el ExoPlayer). Se quitó el botón de Cola. En pantallas < 420 dp los botones se reducen un poco para que quepan.
- Mini-reproductor: `Play/Pausa  Anterior  Siguiente`. Anterior usa `LocalAudioController.previous` (más de 3 s o primera canción: reinicia; si no, pasa a la anterior de la cola).

### Autoplay de música y orden del mini-reproductor
- Mini-reproductor: `Anterior  Play/Pausa  Siguiente`.
- `PlayerProvider`: `onPlaybackStateChanged` con `STATE_ENDED` en música local avanza a la siguiente de la cola (`LocalAudioController.next`); si era la última, se detiene. La cola es `PlaybackQueue` (el ExoPlayer carga una canción a la vez), así que no se precarga toda la lista de `MediaItem`.

### Barajear y Repetir (pantalla de música local)
- Los botones **Barajear** y **Repetir** de `AudioPlayerScreen` ya funcionan (antes solo mostraban "Próximamente").
- El estado vive en `PlaybackQueue` (`QueueState.shuffle`, `QueueState.repeat`, más el orden barajeado `order` / `orderPos`), porque el ExoPlayer carga una canción a la vez y no tiene lista propia. Al ser un objeto de la app, se conserva al cambiar de canción y al cerrar/abrir la pantalla completa mientras la app siga viva. Solo actúa con **música local**; con YouTube la cola funciona como siempre.
- **Barajear**: al activarlo, la canción actual queda primera y el resto se ordena al azar; Siguiente/Anterior siguen ese orden (sin repetir canciones hasta agotarlo). Al desactivarlo vuelve el orden de la carpeta. Icono gris = apagado, acento dinámico = encendido.
- **Repetir** (un botón, 3 estados): Inactivo (gris) → Repetir lista (acento) → Repetir canción (acento, ícono con "1", `ic_audio_repeat_one.xml`) → Inactivo. Cada cambio muestra un Toast.
  - Repetir lista: tras la última canción (o el final del orden barajeado) vuelve a empezar; Siguiente también queda habilitado en la última.
  - Repetir canción: al terminar, `PlayerProvider` reinicia la misma (Siguiente/Anterior manuales siguen avanzando).
- Sin cambios: Play/Pausa, ±10 s, Siguiente y Anterior (los botones llaman a los mismos métodos del `Player`; solo `PlaybackQueue.next/previous` tienen en cuenta los modos).
- Textos nuevos: `mp_repeat_one`, `mp_repeat_all_on`, `mp_repeat_one_on`, `mp_repeat_off`. `versionCode` 18.

## Reordenar y personalizar carpetas (Música y Video Player)
- **Toque largo + arrastre** reordena: carpetas (cuadrícula de Música y de Video Player) y archivos dentro de una carpeta (canciones y videos). Un archivo solo cambia de posición dentro de SU carpeta; no se puede pasar a otra.
- **Toque largo sin mover** sobre una carpeta abre el menú: **Establecer icono** (Photo Picker) y **Quitar fondo** (solo aparece si la carpeta ya tiene imagen). En archivos, el toque largo quieto no hace nada.
- Mientras se arrastra, el elemento se levanta (sombra), crece un poco y se ve algo transparente. Cerca del borde superior/inferior la lista se desplaza sola.
- `ui/components/ReorderDrag.kt`: gestos y estado del arrastre (sin librerías nuevas; funciona con `LazyVerticalGrid` y `LazyColumn`). Los eventos se consumen mientras dura el toque largo, así que no se desplaza la lista ni se abre el elemento al soltar. Rápido de reutilizar: `rememberReorderList` + `rememberGridReorderState` / `rememberListReorderState` + `Modifier.reorderGestures` + `Modifier.dragVisual`.
- `ui/components/FolderIconMenu.kt`: menú del ícono y `rememberFolderIcon`. `AudioFolderCard` y `LocalFolderCard` reciben `icon` opcional: con imagen se ve solo la imagen (esquinas redondeadas, `FilterQuality.High`); sin imagen, lo de siempre (carátula / nota musical, o miniatura + ícono de carpeta).
- **Orden guardado** (`data/local/OrderStore.kt`): SharedPreferences `local_order`, valores = ids separados por comas. `folders_music` / `folders_video` (BUCKET_ID) y `files_music_<bucketId>` / `files_video_<bucketId>` (MediaStore _ID). Los ids que ya no existen se ignoran y lo nuevo queda al final. `LocalMediaUiState` aplica el orden (`applyOrder`) y `LocalMediaViewModel` lo carga al iniciar (`setFolderOrder`, `setFileOrder`).
- **Íconos guardados** (`data/local/FolderIconStore.kt`): la imagen se copia (máx. 768 px, JPEG 92 o PNG si tiene transparencia, respeta la rotación EXIF) a `filesDir/folder_icons/`; ruta en SharedPreferences `folder_icons`, clave `<music|video>_<bucketId>`. Archivo faltante o dañado = ícono por defecto.
- Textos nuevos: `fi_set`, `fi_remove` (los avisos reutilizan `bg_applied`, `bg_removed`, `bg_error`). `versionCode` 19.
- **No cambia:** reproducción de audio/video y YouTube (`PlayerProvider`, `PlaybackQueue`, `PlayerActivity`…), lectura de MediaStore, permisos. Solo se guarda orden e imágenes dentro de la app; nunca se mueven ni borran archivos del dispositivo. Al tocar una canción/video, la cola de reproducción sigue el orden mostrado.

### Íconos de carpeta: llenar todo el recuadro
- `AudioFolderCard` y `LocalFolderCard`: la imagen personalizada usa `ContentScale.FillBounds` con `fillMaxSize()` dentro del recuadro (se estira hasta los bordes, como en Alouncher). Antes era `Crop`. Solo cambia el dibujo; el arrastre y el menú de toque largo no se tocan. `versionCode` 20.

## Opciones de canción (⋮ en la pantalla de música local)
- El botón **⋮** de `AudioPlayerScreen` abre una hoja inferior (`ModalBottomSheet`, `ui/components/SongOptions.kt`) con **Etiquetas**, **Carátula del álbum** y **Detalles** (íconos de trazo `ic_song_tag`, `ic_song_image`, `ic_song_info`; acento dinámico). Antes mostraba el aviso "Próximamente" (`mp_more_soon` ya no se usa).
- **Etiquetas** (`SongTagsScreen`, solo visual): pantalla completa con barra (atrás, título, ✓, ⋮), recuadro "Sin carátula del álbum" con botones editar y buscar, campos de solo lectura (Nombre, Artista, Álbum, Compositor, Artista del álbum, Pista #, Año, Género) y el checkbox "Editar archivo de audio también". Los campos muestran los datos reales del archivo pero NO se pueden editar; ✓, ⋮, editar y buscar solo avisan "Próximamente" y no guardan nada.
- **Carátula del álbum**: solo Toast "Próximamente".
- **Detalles** (`SongDetailsDialog`, datos reales): `data/local/AudioDetails.kt` (`AudioDetailsReader`, fuera del hilo principal) lee con `MediaMetadataRetriever` (título, álbum, artista, compositor, artista del álbum, género, año, pista, duración, bitrate), el lector de `.opus` como respaldo de etiquetas, `MediaExtractor` para frecuencia de muestreo y canales, y `File` para ruta y tamaño (`File.length()`, en MB; si no hay ruta, el tamaño de MediaStore). Lo que falte se muestra como "Desconocido"; ningún fallo tumba la app. Solo LEE el archivo.
- La canción se toma de `PlaybackQueue.state.current` (content://). Textos nuevos con prefijo `so_`. `versionCode` 21.
- **No cambia:** la lógica de reproducción (botones, cola, `PlayerProvider`, `PlayerActivity`) ni nada de YouTube.

## Etiquetas funcionales (guardar en el archivo)
- **Etiquetas** (`SongTagsScreen`) ya es editable: se quitó el checkbox "Editar archivo de audio también" (siempre se edita el archivo físico) y todos los campos se escriben (Pista # y Año solo dígitos). El diseño no cambia. El lápiz de la carátula, la lupa y ⋮ siguen avisando "Próximamente".
- **✓ guarda**: `data/local/TagSaver.kt` (en `Dispatchers.IO`) solo escribe los campos que el usuario cambió. Flujo: comprobar permiso → respaldo del original en `cacheDir/tag_edit` → reescribir etiquetas en una copia y verificarla → sobrescribir el original (`openOutputStream(uri, "wt")`) → releerlo y verificar → si algo falla, se restaura el respaldo. Toast "Etiquetas guardadas" y la pantalla se cierra; si falla, Toast con el motivo (`so_err_*`).
- **Formato**: se escribió un escritor propio de etiquetas Ogg Opus (`data/local/OpusTagWriter.kt`, sin dependencias nuevas) porque **Jaudiotagger no soporta `.opus`**. Reescribe solo las páginas de `OpusTags` (recalcula CRC y numeración si cambia el número de páginas) y no toca el audio ni la carátula incrustada. MP3/M4A muestran "Formato no soportado todavía".
- **Permisos**: Android 11+ `MediaStore.createWriteRequest` (diálogo del sistema una sola vez por archivo ajeno a la app); Android 10 `RecoverableSecurityException`. No hace falta `WRITE_EXTERNAL_STORAGE` (minSdk 29).
- **Reproductor**: `beforeSave` detiene el audio (para soltar el archivo) y `afterSave` lo recarga con las etiquetas nuevas en la misma posición y con el mismo estado play/pausa. `AudioMetadataRepository.invalidate(uri)` + `version` hacen que pantalla de reproducción, mini-reproductor y listas se actualicen sin reiniciar la app.
- `versionCode` 22. **No cambia:** la lógica de reproducción ni YouTube.
