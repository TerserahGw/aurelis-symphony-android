# Aurelis Symphony — Android (Kotlin / ARM64)

**Native Android beta source project**, based on Aurelis Symphony Web 2.7.0 by TerserahGw.
The web/server project remains unchanged: https://github.com/TerserahGw/aurelis-symphony

> Status: **0.1.0 beta — source prepared, not compiled or device-tested here**. This ZIP is not an APK. GitHub Actions builds a debug APK; a successful CI build and device testing are needed before calling it stable.

## Design

- Native **Jetpack Compose** screens: Home / Search / Library, floating mini-player, fullscreen player, and Island Capsule search/menu with spring weight-morph transitions.
- Restrained semi-transparent ambient glass with artwork-derived tint, layered edges, configurable glass density, and GPU-friendly gradients. *The glass is simulated with tinted translucency, not genuine background/backdrop blur.*
- Scrollbars are not rendered. Uses native lazy scrolling; no WebView, no API Docs screen.
- Contextual song menus, queue, local favorites, history, playlists, and settings (Audio mode, Ambient Glass, motion, romanization).
- Synchronized lyrics; word/character progress shown only for lyrics with real word timing metadata, avoiding fabricated karaoke for ordinary line-level lyrics.
- Media3 + MediaSessionService for native playback and Android media controls (behavior depends on OS and permissions).

## Audio / NewPipeExtractor

- Kotlin application integrates **NewPipeExtractor v0.27.1** (Java 17 library) through JitPack.
- **Auto** mode: NewPipe extracts a public YouTube audio URL; on extraction failure, it tries the Aurelis playback endpoint. On ExoPlayer failure, it retries once through Aurelis Server. The server can fail too.
- **NewPipe** mode: use direct extraction only. **Aurelis Server** mode: use existing `/api/v2/playback/:id` resolver only.
- Direct NewPipe extraction **does not guarantee all videos can be played**. YouTube can restrict access, require authentication, change endpoints, or use formats/manifest types not supported by direct URL playback. No DRM, login, CAPTCHA, or geo-bypass is included.
- The Aurelis web service remains responsible for remote home recommendations, online search and lyrics; the native app is **not fully offline**.

## Technical requirements

- Kotlin 2.2.21, Android Gradle Plugin 8.13.2, JDK 17, Gradle 8.13, Android SDK 36.
- Android API 28+; release target architecture **arm64-v8a**. Most UI/player code is architecture-neutral; no ARM-specific native `.so` is bundled by this project.
- Requires network and live Aurelis server at `https://aurelis-symphony.go.altare.global` (configured in `data/AurelisApi.kt`).
- Requires Google Maven, Maven Central and JitPack during dependency download.
- No API docs or web-based developer panel in the app.

## Build the APK with GitHub Actions (no PC needed)

1. Upload **the contents of this ZIP** to a GitHub repository (not the ZIP as a single file), keeping `.github/workflows/android-arm64.yml` intact. Do not overwrite your existing web/server repository unless you want both projects combined.
2. In GitHub, open **Actions → Android ARM64 APK → Run workflow**. A push affecting the app also triggers the workflow.
3. When the run **succeeds**, open the run's **Artifacts** section and download `AurelisSymphony-Android-ARM64-debug`. Unzip the artifact and install its `.apk` on your ARM64 Android phone.
4. A debug APK is signed with the CI runner's *debug* key. For future updates intended to install without uninstalling or losing local data, create a **release signing key** and configure a release signing workflow. Do **not** put a private signing key into a public repo.

Build with Android Studio or a configured terminal with SDK 36 and Gradle 8.13:

```sh
gradle :app:assembleDebug
```

The resulting debug APK is under `app/build/outputs/apk/debug/`.

## Service endpoints used

- `GET /api/v2/home`
- `GET /api/v1/catalog`
- `GET /api/v1/search?q=...`
- `GET /api/v1/recommendations?track=...&artist=...`
- `GET /api/v1/lyrics?track=...&artist=...&duration=...`
- `POST /api/v2/romanize`
- `GET /api/v2/playback/:id`

No developer API documentation is exposed in the UI.

## Open issues / beta limitations

- CI compilation and actual playback on a phone have **not yet been verified** in this preparation environment. Build fixes may be needed.
- Source URL availability from YouTube and Aurelis is external and cannot be guaranteed.
- Native animations and surfaces approximate the web aesthetic rather than implementing a pixel-identical clone; heavyweight backdrop blur is intentionally avoided for smoother low-overhead rendering.
- Offline music downloads, synchronized cloud Library, Android Auto, equalizer and advanced audio caching are not included in this beta.
- Local data is stored with Android SharedPreferences. It is **not migrated automatically** from the browser's localStorage.

## Copyright & license

Project code: GPL-3.0 (see LICENSE), consistent with the original Aurelis Symphony repository and NewPipeExtractor's licensing. Third-party libraries, album artwork, search metadata, audio and lyrics remain subject to their respective licenses/terms.
