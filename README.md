# Aurelis Symphony — Android (Kotlin / ARM64)

**Native Android beta source project**, based on Aurelis Symphony Web 2.7.0 by TerserahGw.
The web/server project remains unchanged: https://github.com/TerserahGw/aurelis-symphony

> Status: **v0.1.1 Beta**. GitHub Actions builds an ARM64 debug-signed APK and publishes the successful result as a prerelease. Build success does not replace testing on a physical device.

## Design

- Native **Jetpack Compose** screens: Home / Search / Library, floating mini-player, fullscreen player, and Island Capsule search/menu with spring weight-morph transitions.
- Frosted artwork blur on floating Island capsules and lyrics, dynamic artwork-derived Ambient Glass lighting, highlight gradients, and adjustable density. Artwork itself is GPU-blurred on Android 12+; this is **not** a capture of the live screen behind a widget.
- Scrollbars are not rendered. Uses native lazy scrolling; no WebView, no API Docs screen.
- Anchored mini popup menus (three dots morph to X), queue, local favorites, history, playlists, and settings (Audio mode, Ambient Glass, motion, romanization). The navigation highlight follows finger dragging before the selected tab changes.
- Synchronized lyrics with word-level glow and a soft progress trail where true word timestamps exist, an active-line soft glass highlight, and corrected Latin spacing when a lyric provider returns unspaced word tokens.
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
3. When the run **succeeds**, open the run's **Artifacts** and download `AurelisSymphony-Android-ARM64-v0.1.1-beta`, or open [Releases](https://github.com/TerserahGw/aurelis-symphony-android/releases) to download `AurelisSymphony-Android-arm64-v0.1.1-beta.apk` directly.
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

- CI compilation and actual playback on a phone should be verified for each change. YouTube and device-specific playback can still fail.
- Source URL availability from YouTube and Aurelis is external and cannot be guaranteed.
- The frosted layer blurs cached artwork instead of recording or blurring the live view underneath, to keep the UI responsive; a pixel-identical browser replica is not the goal.
- Offline music downloads, synchronized cloud Library, Android Auto, equalizer and advanced audio caching are not included in this beta.
- Local data is stored with Android SharedPreferences. It is **not migrated automatically** from the browser's localStorage.

## Copyright & license

Project code: GPL-3.0 (see LICENSE), consistent with the original Aurelis Symphony repository and NewPipeExtractor's licensing. Third-party libraries, album artwork, search metadata, audio and lyrics remain subject to their respective licenses/terms.

## v0.1.1 Beta — visual and player redesign

- Full-bleed Cover mode with softly blurred top/bottom artwork edges and readable title overlay.
- Lyrics directly on artwork-derived Ambient Glass background — no opaque, separate lyrics rectangle.
- Animated word-accurate karaoke reveal with soft glow; smooth 65 ms playback sampling and smoothed scrubber. Line-level lyrics are not given fabricated word timestamps.
- 3-way frosted, morphing bottom island for Cover / Lirik / Up Next, where queue is visible inside the player.
- Per-song options and lyric timing calibration popup, ±250 ms taps, persistent global offset capped to ±5 seconds.
- Toggle romanization and translations next to the lyric title; translations are visible only when data exists.
- Album-tinted frosted overflow popups near the three-dot trigger, with dots morphing to a close icon, and mini/full player spring transitions.

**YouTube playlist integration:** Public playlist URL import is technically feasible with NewPipeExtractor, but is **not yet implemented in this release**. Reading a user's private YouTube playlists requires a Google OAuth 2.0 sign-in and YouTube Data API credentials; those are not configured here. The app does not ask for Google login.

**Build and testing:** CI should produce a debug-signed APK. YouTube stream extraction can fail independently of UI. Check actual performance on device.


## v0.1.2 Beta — liquid-glass polish and secure updater

- Lyric karaoke now renders **once**, with a smoothly animated green/white shader and no duplicated glyph layer.
- The progress track is a fine Ambient Glass line with a bloom/glow around its filled portion. Play/pause and skip buttons are frosted.
- Player overflow uses an anchored top-right morphing glass capsule with animated open/close. Home/Search overflow adapts upwards or downwards based on available space.
- Cover mode prefers available YouTube max-resolution thumbnails, uses a soft blurred full-screen backdrop, and blends edges without hard horizontal seams. If no high-resolution thumbnail exists, source artwork quality is the limit.
- Settings are a frosted artwork-tinted sheet with motion, audio, ambient, romanization, lyric offset, and update controls.
- The app checks [this repo's GitHub Releases](https://github.com/TerserahGw/aurelis-symphony-android/releases) when opened, then approximately every six hours while running. An available newer version appears in a dismissible glass popup. Downloads stream to a private cache, show progress, verify package name/signature and GitHub SHA-256 when available, then open the **Android installer for explicit user consent**. There is no silent installation.

### IMPORTANT: consistent signing is necessary for direct upgrades

Prior beta APKs were debug builds. Each GitHub-hosted runner can create a different debug signing key. Android **cannot update** an existing installation with a different signing certificate, and increasing `versionCode` alone cannot fix that. Version `0.1.2-beta` uses `versionCode = 3`.

To stop forcing a reinstall on **future** updates, this repository now **publishes stable release APKs only when four encrypted GitHub Actions secrets are set**. Without them, GitHub Actions still uploads a **debug test APK** under Artifacts, but it does not publish that debug build as the updatable release.

One-time setup using Termux (no PC):

```bash
pkg install -y git gh openjdk-17 coreutils
gh auth login
mkdir -p ~/aurelis-private-signing
keytool -genkeypair -v \
  -keystore ~/aurelis-private-signing/aurelis-release.p12 \
  -alias aurelis -keyalg RSA -keysize 3072 -validity 10000 \
  -storetype PKCS12

# Upload the KEYSTORE ONLY as an encrypted GitHub Actions secret:
base64 -w0 ~/aurelis-private-signing/aurelis-release.p12 \
  | gh secret set AURELIS_KEYSTORE_BASE64 \
  -R TerserahGw/aurelis-symphony-android

printf '%s' aurelis | gh secret set AURELIS_KEY_ALIAS \
  -R TerserahGw/aurelis-symphony-android

# The secret entry is hidden; type the password used in keytool:
read -r -s -p 'Keystore password: ' AURELIS_PASS; echo
printf '%s' "$AURELIS_PASS" | gh secret set AURELIS_KEYSTORE_PASSWORD \
  -R TerserahGw/aurelis-symphony-android
printf '%s' "$AURELIS_PASS" | gh secret set AURELIS_KEY_PASSWORD \
  -R TerserahGw/aurelis-symphony-android
unset AURELIS_PASS

gh workflow run android-arm64.yml -R TerserahGw/aurelis-symphony-android
```

Keep the `.p12` and passwords backed up securely **outside the public GitHub repository**. Every future stable release must use exactly the same key and a higher `versionCode`. Do not lose this key: replacing it makes ordinary in-place updates impossible. Do not publish the keystore, password, or their base64 encoding.

**Migration note:** users who installed the older debug-signed APK may need **one final reinstall** when switching to this new permanent release key. Local app data may be lost on uninstall; no backup/migration of old beta installations is guaranteed. The in-app updater warns of incompatible signatures rather than offering a broken install.


## v0.1.3 Beta — playback, search and player polish

- Fixed search race conditions: the previous query is cancelled on the **first keystroke** of a replacement query, not after the search debounce. Cancellation is never shown as a search failure, and stale responses cannot overwrite a newer search.
- Listening history now keeps the **500 most recent play events** rather than deduplicating the last 50 tracks. Replays are visible and have unique lazy-list keys.
- Up Next now prefetches up to four tracks at the beginning of playback and replenishes as the queue drains. Candidate rankings use backend recommendations plus fallback tracks, taking favorite/recent history, related artist/title, diversity, and replay suppression into account. This is a **transparent heuristic re-ranker**, not a neural recommender. Removing a suggestion prevents immediate reselection for the current queue session.
- Cover mode fills the entire viewport via crop, with a continuous transparent vignette and no `ContentScale.Fit` inset image.
- The music seek bar is now a fine-line control without a circular thumb; subtle bloom applies only along the played part.
- Three-dot popups are positioned to fit the viewport and animate outward from the originating capsule. Their original trigger is hidden until the closing animation finishes, so there is no double overlay.
- `versionCode` is **4**, `versionName` is **0.1.3-beta**. Signing uses the existing persistent keystore and encrypted Actions secrets, enabling direct updates from **v0.1.2 Beta release-signed** installations. This does not solve incompatible older debug-signing keys.

Android direct-update limitations remain: matching signing certificate and increasing version code are required. The in-app updater still requests Android's install consent rather than silently installing APKs.
