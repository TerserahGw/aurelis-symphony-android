## v1.2.17 — sharper widgets, smooth title slides and five Up Next songs

- **Sharper cover art:** prefer larger source images on Google-hosted artwork and YouTube (with quality fallback); cache album art at 768×768 JPEG quality 94, preserve centre crop instead of stretching, and pre-render sharper 320×320 / 336×320 hero layers. Only small final bitmaps reach Android's launcher to keep RemoteViews memory bounded. If the source itself has no extra detail, true resolution cannot be invented.
- **2×1 / 4×1:** increase cover from 40/44dp to **50/54dp**, keeping left margin and playback-button layout as before.
- **4×2:** feather and subtly blur the *right edge of the Now Playing album-cover panel* into the continuous Up Next background; the existing bottom blur and control positions remain.
- **Long titles:** use a lightweight, system-supported **ViewFlipper** with gentle alternating slide/fade segments on overflow titles in all four widgets and the queue rows. Short text stays motionless, and Android fading edges soften clipping. The animation depends on launcher support and may be throttled by HyperOS.
- **Up Next:** keep the heading **UP NEXT** without a 3/5 counter, show up to **five real queued songs** (hiding missing slots), enlarge covers to **36dp**, and tighten vertical spacing for 4×2's two-row grid footprint.
- Preserve existing playback, Glass Ambient/Ambient styles, gesture and Music Player animations. Signed release v1.2.17, versionCode 26.

## v1.2.16 — one-second scroll hold, full-cover widgets, and stability

- Landscape scrolling: Search + mini player stay merged for **1 second after the last vertical scroll**. Starting another scroll cancels the waiting reset immediately. This applies to both left and right controls; search text editing remains unaffected.
- **2×2 home widget:** now uses a single large album cover with the lower portion softly preblurred and darkened progressively for the song title, artist and the same unboxed transport controls.
- **4×2 Up Next home widget** (the wide widget shown on HyperOS): only the **Now Playing half** gets the large artwork with blurred bottom, title and playback controls. The three real Up Next tracks and their individual thumbnails remain in the other half. The 2×1 and 4×1 variants stay unchanged.
- Stability/performance work is conservative: only re-render backgrounds and preblurred hero artwork when song/theme changes; decode artwork with bounded sample sizes, cache queue thumbnails per update, skip identical widget status broadcasts, and isolate launcher update failures per widget. This reduces memory churn while keeping all visual effects, animations and playback behavior intact.

## v1.2.15 — scrolling Search/mini morph, HD landscape artwork and smooth carousels

- While **vertically scrolling** Home, Search, or Library in landscape, the mini player springs into the Search capsule: the Search glyph morphs into the currently playing cover, the extra mini capsule smoothly reduces to zero width, and the gap closes. When scrolling stops, the Search icon and full mini player return with the reverse spring. Works whether the main navigation rail is on the left or right. When Search is actively expanded for text input, it remains usable and does not collapse into the cover. Tap the merged cover to open the full player.
- Landscape Music Player album artwork requests a **higher-resolution original source** where the upstream service offers it (up to 1080px for Google/YouTube thumbnails), with fallback to the normal artwork URL if the high-res request fails. The cover's alignment, crop and rounded corners remain unchanged; this cannot add genuine detail if the provider only serves low-res imagery.
- Home's **horizontal music card carousels**, mood selector and playlist carousel have gradual left/right GPU alpha fades **only where additional items can scroll off-screen**, in portrait and landscape. No hard-cut last cards or background-colored overlays.
- Retains previous spring player opening/closing, themed Glass Ambient widgets, mirrored landscape rail, settings, and negative-padding crash fix.

## v1.2.14 — mirrored landscape Search + mini player

- In landscape **Kiri**, the upper-left Search capsule and adjacent mini-player retain their existing order. Expanding Search grows rightward while the mini-player shrinks into a centered album-cover capsule.
- In landscape **Kanan**, both upper capsules move with the navigation rail: **Mini Player on the left, Search on the far right**, vertically aligned above Home/Search/Library. Expanding Search grows **to the left**, while the mini-player compresses to its cover without disappearing.
- Search, mini-player and navigation rail follow the same saved left/right selection or three-finger horizontal swipe. The upper dock slides with a spring, and the two capsules crossfade/slide into mirrored order. Dock coordinates are clamped without negative padding.
- v1.2.13 negative-padding crash fix, spring Player transitions, three-dot popup side anchoring, and widget updates are preserved.

## v1.2.13 — Landscape rail crash hotfix

Fixed the Android Compose `IllegalArgumentException: Padding must be non-negative` seen when toggling the landscape menu/player rail from left to right (or vice versa). Spring animations can briefly overshoot below zero; both content insets and the rail offset are now bounded to valid geometry on every frame. No change to the visual spring, three-finger gesture, widgets, or full-player zoom animation. Signed versionCode 22.

## v1.2.12 — satisfying Player spring, seamless edges & global landscape controls

- Restores the **original spring zoom and diagonal/vertical slide** when opening and closing the Music Player in portrait and landscape. An unscaled artwork-reactive **blurred underlay** fills the exposed perimeter while a temporary GPU alpha feather softens the transitioning player's sharp edges; at rest it is fully sharp.
- The **same saved Kiri/Kanan setting** now moves the vertical navigation rail on the **main landscape screen** (Home, Search, Library, Settings) and the control rail inside the landscape Music Player (Close, Lirik/Up Next, three-dot).
- **Three-finger horizontal swipe** works from both the main menu and the player in landscape. Swipe left for left controls or right for right. One- and two-finger interactions are not intercepted.
- The three-dot popup is **anchored and animated to the physical side**: bottom-left opens toward the right/up, bottom-right toward the left/up. Spring scales around the correct lower corner with matching exit animation.
- Existing v1.2.10 widget artwork, seamless Glass Ambient surfaces, and 3-item real Up Next remain unchanged.

# Aurelis Symphony — Android (Kotlin / ARM64)

**Native Android beta source project**, based on Aurelis Symphony Web 2.7.0 by TerserahGw.
The web/server project remains unchanged: https://github.com/TerserahGw/aurelis-symphony

> Status: **v1.2.10 Stable source**. GitHub Actions builds an ARM64 test APK and, when persistent signing secrets are configured, publishes a stable-signed release APK. Device testing is still recommended.

## v1.2.11 — seamless Player morph + landscape control rail

- Enter/exit transitions no longer scale or slide the **full-screen player background**. The background stays full-bleed and fades continuously; the foreground controls/content gently scale during the morph. This avoids exposed/cropped edges.
- In **Settings → Interaksi → Landscape**, select **Kiri** or **Kanan** for the full-player vertical control rail (close, Lirik/Up Next navigation, overflow).
- While the full player is open in landscape, swipe **three fingers horizontally**: left to move the rail left, right to move it right. Single-finger gestures remain dedicated to lyrics, queue and progress. The switch animates and respects **Motion & morph**.
- The previous v1.2.10 widget fixes (single seamless background, classic icon-only buttons, three real Up Next covers) are preserved.

## Home Screen widgets — v1.2.10

Four separately selectable **fixed-size** native widget variants follow the app's **Glass Ambient** or **Ambient** theme. Android declares `resizeMode="none"` for each widget (some third-party launchers may override this setting).

| Picker size | Layout | Playback controls | Up Next |
|---|---|---|---|
| **2×1** | Cover, song title, artist | None — tap to open app | No |
| **2×2** | Square music card | Previous, Play/Pause, Next | No |
| **4×1** | Horizontal mini player | Previous, Play/Pause, Next | No |
| **4×2** | Split Now Playing / Up Next | Previous, Play/Pause, Next | First two real upcoming songs |

v1.2.10 keeps the 4×2 RemoteViews and album-art fixes but removes the hard-edged artwork rectangle that appeared on some HyperOS launchers. The wallpaper-facing background now uses only continuous full-surface gradients and soft radial glow. Icon-only playback controls are restored for a cleaner appearance; the 4×2 Up Next pane shows **up to three actual queue tracks with cached artwork**, hides absent tracks instead of filling the area with fake placeholders, and indicates when more remain in the queue.

These are independent Android widget providers rather than one resizable layout. The fixed `19dp` corner outline clips the artwork-reactive background, so corners do not stretch with width/height. Glass Ambient uses a softer transparent acrylic surface, subtle artwork highlights and a fine outline; Ambient stays opaque and dark. The launcher wallpaper cannot be blurred by normal `RemoteViews` widgets.

If an **older widget** remains resized to a custom footprint, remove it from the Home Screen and add the new **2×1, 2×2, 4×1, or 4×2** entry to get the intended fixed grid size. Up Next reads AurelisState's live queue and is updated when it changes. When Android has killed the UI state, Previous/Next safely open the app rather than guessing a different music queue.

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


## 1.0.0 Stable — the first permanent Android release

The Android app is now version **1.0.0** (`versionCode = 5`). It retains the persistent release-signing keystore configured for v0.1.2/v0.1.3; installing the **signed** release APK on top of those signed betas should preserve local settings, favorites, history and playlists.

### Home and music player

- An album-tinted frosted mini-player and translucent navigation/search islands now allow moving Home content to be visible behind their surfaces. Lists have extra bottom padding so their last item remains reachable.
- While the user **scrolls Home** and a song is playing, the dock transitions into a compact layout with three islands: a hamburger on the left, **mini-player in the center**, and Search on the right. The compact mini-player shows artwork, title/artist, and play/pause; tapping its body opens the full player. After **three seconds without scrolling**, the mini-player expands back above the dock and the hamburger smoothly unfolds into the Home/Search/Library navigation. The scroll-aware mode is limited to Home.
- Overflow song menus morph from the top-right anchor using GPU scale/alpha animation rather than changing the popup window dimensions every animation frame. This avoids the previous window-position jitter.
- Mini-player, navigation, and search surfaces use lower-density artwork-tinted glass.

### Translation

- The Translate button now sends an **explicit on-demand** request to the existing Aurelis server's `POST /api/v2/translate` endpoint, with batches of 16 lyric lines and an Indonesian (`id`) destination.
- The server delegates translation to MyMemory. Translation is **best-effort**, subject to third-party service quotas and connectivity; unavailable lines stay in the original language and an error/partial-results message appears in the lyrics view.
- No lyrics are sent to the translation provider until translation is requested by the user. Returned lines are aligned with their original lyric timestamps.

### Shipping and verification

GitHub Actions compiles a test APK, then builds and verifies the release-signed ARM64 APK using the repository's private signing secrets. Only the signed APK is attached to the public Stable Release. Android's package installer still obtains user consent during app updates. Real playback, UI animation, network translation, and device-specific behavior require testing on the target Android device.


## 1.0.1 Stable Hotfix

- **Glass intensity:** Navigation, Search, full mini-player and compact mini-player use stronger album-art frost (38 dp radius, adjusted translucency), still retaining content readability. Blur uses album artwork, rather than dynamically capturing and blurring live list items behind the dock.
- **Unified scroll behavior:** Compact dock now responds to scrolling on Home, Search and Library. Each tab keeps its own LazyListState and the mini-player moves into the center island between the hamburger and Search. The full player/navigation layout returns after three seconds of scroll inactivity. Search-input expansion is preserved when its tab is in use.
- **Smoothness / reliability:** Stable glass gradients are memoized; high-frequency progress is isolated into its own Composable and interpolated; slider settings are persisted after a short debounce with a final flush on close. Rapid song changes preserve coroutine cancellation rather than showing errors from stale playback requests.
- **Settings fast-scroll fix:** Modal sheet's drag gestures are disabled while the dedicated content scroll remains enabled, the sheet uses an opaque background behind its artwork-tinted glass, and the content fills the full sheet including its bottom. This prevents the underlying Home screen from showing through the lower gap visible in the provided recording.
- **Version:** `1.0.1`, `versionCode = 6`, **Stable**, using the same release-signing secrets as v1.0.0 for in-place Android updates.

Release APKs are built and signature-verified by GitHub Actions, but animation smoothness and interaction on each physical Android device still require device testing.


## v1.1.0 Beta — YouTube Music integration and new Aurelis Home

This is an **experimental beta**, not a replacement for the previous v1.0.1 Stable release. It uses the same Android package and persistent signing key, with `versionCode = 7` for direct upgrade testing.

### What was learned from BitChord

The open-source [BitChord](https://github.com/kushagrasinghx/BitChord) Android music client uses the YouTube Music WEB_REMIX / InnerTube browse and next endpoints for personalized shelves and radio recommendations. Its account login opens Google's real pages in a WebView, captures an explicitly confirmed signed-in session, and uses Google session-bound authentication for history and personal playlists. Its lyric translator batches bounded text requests with careful original-line mapping and caching. We inspected BitChord's `YtMusicRepository.kt`, `Innertube.kt`, `PlaybackTracker.kt`, login flow, and `docs/LYRICS_TRANSLATION.md` as technical references; Aurelis's new implementation is separate and substantially smaller.

### Implemented in Aurelis

- **Optional YouTube Music account linking.** The login page is Google's own WebView page. Aurelis does not ask for or inspect your Google password. Only after you explicitly tap **Hubungkan akun** does it capture the YouTube Music session; the session is AES-256-GCM encrypted under Android Keystore and stored under the app's no-backup directory. Disconnect deletes the stored session.
- **YouTube Music browse.** Experimental InnerTube WEB_REMIX requests retrieve personalized Home shelves, account history, and playlist metadata. The app refreshes while open (roughly one minute between refresh attempts), with a manual refresh button. These are *periodic pull refreshes*, not true realtime push; server restrictions or Google changes may cause empty or unavailable data.
- **YouTube Music Library tab.** View remote playlists, load their first page of tracks, and inspect remote listening history. Local Aurelis playlists, favorites, and 500-entry history remain separate and intact. *Remote playlist editing, full pagination, remote history writeback and account playback tracking are not implemented in this beta.*
- **Up Next radio:** for YouTube tracks, the client tries YouTube Music's own `next`/radio candidates in parallel with the existing Aurelis backend; the local recommendation ranker still reduces repeats and uses local listening behavior. Nothing bypasses DRM or authentication.
- **Faster lyric translation:** limited parallel requests to Google's lightweight web translation service with strict line-index markers and in-memory caching. It falls back to the existing Aurelis/MyMemory endpoint when responses are invalid or unavailable. Translation is on demand; translated lines are never substituted at incorrect lyric timestamps.
- **Refreshed Home:** music-first visual discovery with horizontal artwork shelves, recently played, mood filters, personalized YouTube Music shelves, playlists, and Aurelis Originals, while keeping the palette and capsule motion design.
- **Display choice:** Settings includes **Glass Ambient** and **Ambient**. Ambient keeps album-art-derived background lighting, but renders panels without frosted blur effects. Navigation icons sit slightly lower for optical alignment.
- **Settings handle:** swipe **down from the top drag strip** to dismiss the sheet with animation. Regular fast scrolling inside Settings remains independent.

### Security and caveats

This uses an **unofficial Google protocol** rather than an officially supported YouTube Music integration. Google may reject embedded browser sign-in, change request formats, rate-limit requests, or invalidate sessions. YouTube cookies must remain on the user's phone: do **not** paste them into a support chat, commit them to Git, or send them to the Aurelis server. Logging out in the app deletes the encrypted session file. The live Google account history feed is read-only in this beta; listening to audio in Aurelis does not register a play against the remote Google history.

Google's lightweight web translation endpoint is also not a versioned public translation API. Results and uptime may vary; requests are made only after choosing Translate.

The user-facing client remains GPL-3.0. BitChord is [GPLv3](https://github.com/kushagrasinghx/BitChord/blob/main/LICENSE); the implementation here was written independently after reviewing its architecture and includes attribution in documentation.

**Testing:** CI compilation and APK signature verification are necessary but do not establish live Google login compatibility, remote playback, animation smoothness, or real-device UX. These are areas for hands-on beta testing.


## v1.1.1 Beta — YouTube Music account linking hotfix

v1.1.0 Beta could show **"Caller-provided IV not permitted"** even after the embedded YouTube Music page visibly signed in. The issue was not an account rejection: Aurelis attempted to pass a random IV to an Android Keystore AES-GCM key whose randomized encryption requirement rejects caller-supplied IVs.

Fixed by invoking `Cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)` without a GCM parameter, then persisting the **Keystore-generated IV** with the ciphertext. Decryption still uses the saved IV in `GCMParameterSpec`, and the authenticated session remains encrypted at rest under an Android Keystore-protected AES-256-GCM key. The login dialog now loads `music.youtube.com` first so previously signed-in WebView sessions may be reused, similar to BitChord.

**Install v1.1.1-beta above v1.1.0-beta without uninstalling** if both are release-signed with the existing keystore; `versionCode = 8`. Open Settings → YouTube Music → Hubungkan akun; confirm after the YouTube Music account loads. User login remains required for account personalization, and the InnerTube endpoint can still change or refuse account access. Cloud history writeback and true realtime/push synchronization are still out of scope for this hotfix.


## v1.2.0 Stable — YouTube taste, full playlists, five app languages

- **Signed in-place update:** `versionCode 9`, same package and permanent signing key as the prior stable/beta releases. Existing locally stored history, likes, playlists, and preferences are not intentionally cleared.
- **Personalized radio:** while signed in, Up Next combines YouTube Music `next`/radio, personal Home shelves and artists from remote listening history with existing Aurelis recommendations. It is an **approximation based on available account data**, not YouTube's private ranking model, and depends on unofficial InnerTube remaining accessible.
- **Playlist playback context:** playing a local or YouTube playlist starts from the chosen item and adds all remaining tracks to Up Next in their existing order, including duplicates. Next/Previous stay inside the playlist, and playlist end stops rather than injecting a radio. The Up Next view shows the source playlist name.
- **Unified Library playlists:** local playlists and connected account YouTube Music playlists appear together under **Library → Playlist**, with a *YouTube* label. Tap a YouTube collection to load its tracks, or import a publicly accessible playlist with a YouTube/YouTube Music URL. The reader paginates with a bounded maximum and displays errors if a track list cannot be retrieved. Playlist changes are **not** written back to Google.
- **UI language:** Settings supports Indonesian (default), English, Japanese, Spanish and Korean. Major UI labels change immediately without restarting. Some system dialogs, dynamic server messages, names, and rarely used fallback strings may remain in their original language.
- **Lyric language:** Translate targets the currently selected app language. A language change discards stale translations and requests fresh results only after the user explicitly enables Translate. Google Translate lightweight batching is tried first and Aurelis/MyMemory is fallback; provider limits and lyric-source availability still apply.
- **Ambient without glass:** Ambient uses flat opaque capsules, removing frosted artwork layers and glossy borders from shared Glass surfaces; tab highlights and overflow shadows also switch to a non-glass presentation. Glass Ambient keeps the artwork frost effects.
- **Login encryption:** Android Keystore AES-GCM generates its own IV, preserving the v1.1.1 login fix.

**Known limitations:** YouTube Music access relies on unofficial InnerTube / WebView authentication. Not every YouTube playlist is public or resolvable, embedded login may be blocked by Google, and some video streams may be unavailable to NewPipe/Aurelis. Personal YouTube Music listening history is read-only and periodically refreshed; Aurelis playback does not write history or playlist edits to the Google account. Build success is separate from physical-device testing.


## v1.2.1 Stable Hotfix — update storage

The in-app updater saves downloaded installation packages under its own private
`cacheDir/updates` directory. In older builds those APKs were never removed,
so successive ~56 MB downloads could accumulate to hundreds of megabytes.

This release introduces narrowly scoped cleanup:

- On app startup, delete APKs of **already-installed** versions and any expired
  update package from the updater's cache directory.
- Keep a single not-yet-installed candidate; other APKs are removed when a new
  download completes. Downloaded APKs expire after **24 hours**.
- Schedule a one-time Android JobScheduler cleanup for approximately 24–36 hours
  later even if the music app has been closed (actual execution depends on
  Android power management).
- Delete failed/interrupted temporary downloads (immediately on failure, and
  after a short grace period if the process was interrupted).
- If the user declines a downloaded update before opening the installer,
  remove that unused APK. **Do not delete immediately after launching Android's
  package installer**, because it needs the FileProvider URI to stay valid.
- Settings → Pembaruan displays the number of MB in update packages and offers
  **Bersihkan file update sekarang**.

**Scope:** Only Aurelis updater-owned APKs and partial downloads inside
`cacheDir/updates` are touched. Music playback, local playlists, history,
translation caches, settings, account data, and other artwork caches are never
cleared by this feature. If Android Settings reports a much larger storage
total, that total can also include other application caches/data.

The app is `1.2.1` (`versionCode = 10`), signed with the existing release
keystore for in-place updates from v1.2.0 Stable.


## v1.2.2 Stable — Playlists & launcher identity

- **Contextual playlist actions:** when you open a track's three-dot menu inside one of your *local Aurelis playlists*, that playlist's existing "add" entry is replaced by **Hapus dari playlist ini**. Other local playlists remain valid add targets. Removing a song updates the currently playing local playlist's Up Next without deleting the song from Library or other playlists.
- **Tap to play:** tapping a local playlist, an authenticated YouTube playlist card, a Home YouTube playlist card, or opening a YouTube playlist URL loads its first available song into the normal playlist-ordered queue and opens the full player with its established animation.
- **Separate expand control:** the arrow next to an Aurelis playlist expands/collapses its song rows without starting playback. YouTube playlists now have their own animated down/up expand arrow. Expanded remote songs are inserted lazily into the parent list, with loading progress and a clear empty message.
- **Launcher icon:** Aurelis Orbit is a crisp, restrained geometric vector mark in deep evergreen/mint. Includes adaptive round and Android 13+ monochrome themed-icon resources; no raster-heavy artwork or fake 3D effects.
- **Update continuity:** signed Stable version **1.2.2** uses `versionCode 11` and the same release certificate as v1.2.1. No new permissions, account credentials, or installation steps are needed.

**YouTube limitation:** this release reads and plays YouTube playlist contents but does not edit Google-hosted playlists. Removing a track is intentionally available only in *local* Aurelis playlists. Access to remote playlists depends on Google login/session and unofficial InnerTube availability.

As with prior releases, GitHub Actions validates compilation and APK signing. Physical-device animation and remote YouTube behavior need testing on the user's Android device.


## v1.2.3 Stable — Landscape navigation and rotation hotfix

- **Portrait ⇄ Landscape playback continuity:** `AurelisState` is now an Android lifecycle `ViewModel`. The media controller, active track, queue, and mini player survive configuration-based Activity recreation. The controller is released only when its ViewModel is actually cleared, rather than during every rotation.
- **Landscape navigation rail:** Home, Search, and Library are vertically stacked in a left-side frosted capsule with an animated selected-state highlight. Each list gains reserved horizontal space so its content cannot sit underneath the rail. Portrait retains the original bottom navigation.
- **Landscape top capsules:** Search sits above the left navigation island. Tapping Search expands its input rightward with spring motion. The adjacent mini-player is still visible, compressing from cover + title/controls to an artwork-only capsule while Search is expanded. A small Settings capsule remains accessible on the right.
- **Landscape music player:** split layout, with permanent cover artwork and timeline/duration/previous/play/next controls on the left, and synchronized lyrics (including romanization and translation) or Up Next on the right. Two vertical mode capsules under the close control switch the right panel with a smooth transition. The right-panel content uses compact spacing, while Portrait keeps the existing three-mode Cover/Lirik/Up Next island.
- **Compact landscape playback controls:** smaller seek spacing and playback capsules avoid overflows on narrow landscape displays.
- **Stable signing:** `versionCode 12`, `versionName 1.2.3`, same application ID and persistent release certificate for an in-place update from v1.2.2. No new account access or permissions.

**Testing limitation:** GitHub Actions compilation, release signing, and APK signature verification check package integrity, but device-specific screen insets, rotation behavior, and animated visual balance still require physical Android testing.



## v1.2.4 Stable — Landscape capsule alignment & atmospheric transitions

This hotfix refines landscape behavior after reviewing a 30-second recording of the Android build:

- **Accurate Search alignment:** the collapsed search capsule is exactly 62dp square, horizontally aligned with the left vertical Home/Search/Library navigation capsule; expanding search retains its rightward spring animation, and shrinks the mini player to cover-only.
- **No abrupt scrolling cutoff:** the Home, Search and Library lists now start safely below the upper dock but can scroll behind it, using a top atmospheric fade instead of the previous hard clip.
- **Settings relocated:** the Settings button is a matching 62dp left-bottom capsule rather than an isolated top-right icon. The navigation rail scales down where needed to leave enough space for Settings on landscape displays.
- **Landscape settings:** a broad rounded, centered, scrollable dialog with a compact title and reduced top spacing replaces the cramped portrait-style bottom sheet; portrait keeps the original bottom sheet and pull-to-dismiss gesture.
- **Landscape full player navigation:** Lyrics and Up Next share one tall vertical capsule with a spring-animated moving highlight. The Close player button uses a separate matching 62dp capsule above it.
- **All prior audio/playlists:** the Android ViewModel rotation continuity, playback queue, in-app updater cleanup, YouTube Music hub and local playlist context remain in place.

`versionName=1.2.4`, `versionCode=13`, ARM64 signed with the same persistent release keystore. GitHub Actions verifies build and signing, but real-world landscape insets and scrolling should still be checked on device.



## v1.2.5 Stable — Precise mini-player alignment, player popup, soft scroll edges

- **Mini player:** compact landscape capsule cover now stays geometrically centered as Search expands, using the animated capsule width rather than a row's fixed left padding.
- **Landscape player overflow:** the three-dot player options moved from the upper-right Lyrics/Up Next panel into an equally sized 62dp bottom-left capsule under the combined vertical mode selector. The menu opens as an inline frosted Glass popup matching Portrait, positioned to the right of the rail rather than showing a disconnected modal bottom sheet.
- **Responsive popup:** landscape options are wider (up to 380dp) and constrained to the available screen height with internal scrolling, including playlist and lyrics-offset actions. The flat Ambient mode continues to use the corresponding opaque capsule style.
- **Smooth scroll boundaries:** Home, Search, Library, synchronized Lyrics, and Up Next apply GPU offscreen alpha feathering at both viewport edges. This removes hard horizontal clipping in Portrait and Landscape while preserving the underlying ambient artwork colors and scroll touch handling.
- **Update continuity:** `versionName 1.2.5`, `versionCode 14`, signed with the same release keystore and installed as an upgrade over v1.2.4 without a data reset.

The gradient feathering is a lightweight GPU opacity transition, not an expensive per-row Gaussian blur; this makes it suitable for scrolling and karaoke word-by-word animations on Android. Device-specific visual checks remain useful after the automated build and APK signing verification.



## v1.2.6 Stable — System-bar continuity & correctly anchored overflow morph

- **Status-bar seam:** synchronizes Android window/theme base with Compose background (`#090D11`), requests genuinely transparent edge-to-edge status/navigation bars, dark-on-light system icons disabled, and disables extra bar-contrast enforcement where the OS supports it. This addresses the noticeably different color band at the top of the landscape display.
- **Upper-left landscape seam:** moves the top atmospheric scrim from the *right-hand page box* to one full-width root overlay (beneath the interactive search and navigation capsules), so the left rail and the adjacent Home/Search/Library content share the same gradient rather than showing a hard transition at the 79dp boundary.
- **Landscape player popup origin:** the bottom-left three-dot capsule opens a frosted Glass popup rightward and upward, now scaling from its bottom-left corner (`TransformOrigin(0f, 1f)`) with a slight upward glide. Closing reverses toward the same anchor. Portrait and regular song-row popup morphs remain top-right anchored as before.
- **Versioning:** `versionCode 15`, `versionName 1.2.6`, and the same stable signing key for in-place upgrades.

Build validation checks compilation and signing; final system-bar appearance can vary with HyperOS and Android's window contrast behavior, so confirm on-device after installing the signed APK.



## v1.2.7 Stable — Fluid player morph & Home Screen music widget

**Refined music-player transitions.** Portrait and landscape now use separate mini-player-to-full-player transformations: an upward spring from the bottom capsule in Portrait, and a smaller diagonal unfolding from the upper capsule in Landscape. Closing reverses the movement with reduced fade and matched easing. The `Motion & morph` switch still disables transitions for reduced motion.

**Native Android widget.** Long-press an empty part of your Android Home Screen, open **Widgets**, find **Aurelis Symphony**, and add the responsive player card. Resize it for either a full Now Playing card or compact card. The widget reads the existing visual setting, so **Glass Ambient** uses a translucent, softly illuminated album-art-derived background with a rounded edge, while **Ambient** uses a clean, opaque evergreen treatment. It displays album art, title, artist and playback status and has Play/Pause, Previous and Next actions. Artwork uses a small bounded cache and the widget updates from the app and the media session without periodic background polling.

**Android widget limitation:** third-party RemoteViews widgets cannot sample or blur the launcher wallpaper in real time. Glass Ambient is a deliberately rendered frosted-look gradient rather than the app's real-time backdrop blur. Play/Pause uses the active Media3 session. Playlist-aware Previous/Next work while Aurelis's playback ViewModel remains alive; when Android has reclaimed that state, these buttons open the app instead of skipping unpredictably.

`versionCode 16`, `versionName 1.2.7`; same stable signing certificate, installable over v1.2.6. Automated CI builds and signs the APK; animations and widget rendering still need live-device testing across launchers.

