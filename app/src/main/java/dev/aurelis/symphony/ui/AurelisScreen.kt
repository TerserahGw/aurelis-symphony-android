package dev.aurelis.symphony.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import dev.aurelis.symphony.update.AurelisUpdater
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import dev.aurelis.symphony.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val bg = Color(0xFF090D11)
private val white = Color(0xFFF3F8F5)
private val mist = Color(0xFF93A6A0)
private val lime = Color(0xFFBCEBD0)
private val groove = Color(0xFF243632)
private val motion = spring<Float>(dampingRatio = .86f, stiffness = Spring.StiffnessLow)
private val LocalGlassDensity = compositionLocalOf { .68f }
private val LocalVisualStyle = compositionLocalOf { VisualStyle.GLASS }
private val LocalAppLanguage = compositionLocalOf { AppLanguage.INDONESIAN }

@Composable
private fun uiText(key: String): String =
    AurelisI18n.text(LocalAppLanguage.current, key)
private val LocalReducedMotion = compositionLocalOf { false }

/**
 * Only during the entry/exit zoom, soften the four edges of the moving
 * player over the static, blurred underlay. At rest the mask is completely
 * disabled, keeping typography and UI sharp.
 */
private fun Modifier.featherPlayerPerimeter(amount: Float): Modifier =
    this.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val strength = amount.coerceIn(0f, 1f)
        if (strength > .005f && size.width > 0f && size.height > 0f) {
            val x = (25.dp.toPx() / size.width).coerceIn(.01f, .20f)
            val y = (25.dp.toPx() / size.height).coerceIn(.01f, .20f)
            val edge = Color.White.copy(alpha = 1f - .90f * strength)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to edge, x to Color.White,
                        (1f - x) to Color.White, 1f to edge
                    )
                ),
                blendMode = BlendMode.DstIn
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to edge, y to Color.White,
                        (1f - y) to Color.White, 1f to edge
                    )
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * GPU offscreen alpha mask: content dissolves smoothly at both viewport
 * edges instead of being cut by the LazyColumn's hard clip. Unlike an opaque
 * overlay it respects artwork and ambient backgrounds.
 */
private fun Modifier.featherScrollEdges(top: Dp, bottom: Dp): Modifier =
    this.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        if (size.height <= 0f) return@drawWithContent
        val upper = (top.toPx() / size.height).coerceIn(0f, .40f)
        val lower = (bottom.toPx() / size.height).coerceIn(0f, .40f)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to (if (upper > 0f) Color.Transparent else Color.Black),
                    upper to Color.Black,
                    (1f - lower) to Color.Black,
                    1f to (if (lower > 0f) Color.Transparent else Color.Black)
                )
            ),
            blendMode = BlendMode.DstIn
        )
    }

private fun Color.mix(other: Color, amount: Float) = Color(
    red = red * (1 - amount) + other.red * amount,
    green = green * (1 - amount) + other.green * amount,
    blue = blue * (1 - amount) + other.blue * amount,
    alpha = 1f
)

@Composable
private fun Glass(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    tint: Color = Color(0xFF213A32),
    opacity: Float = .68f,
    backdrop: String? = null,
    backdropBlur: Dp = 24.dp,
    backdropOpacity: Float = .47f,
    content: @Composable BoxScope.() -> Unit
) {
    val visualStyle = LocalVisualStyle.current
    if (visualStyle == VisualStyle.AMBIENT) {
        // Ambient deliberately draws a flat opaque capsule: no frosted
        // artwork, glass-border highlight or translucent blur.
        val cleanTint = tint.mix(bg, .86f).copy(alpha = 1f)
        Box(modifier.clip(shape).background(cleanTint, shape)) {
            content()
        }
        return
    }
    val density = LocalGlassDensity.current
    val strength = (opacity * (.46f + density * .69f)).coerceIn(.16f, .88f)
    // Brushes are stable between playback ticks, scroll frames, and morph frames.
    // This avoids constructing gradient lists on each unrelated recomposition.
    val edge = remember {
        Brush.linearGradient(
            listOf(white.copy(alpha = .32f), white.copy(alpha = .06f),
                lime.copy(alpha = .15f))
        )
    }
    val fill = remember(tint, strength) {
        Brush.linearGradient(
            listOf(
                tint.mix(Color(0xFF0D1C20), .55f).copy(alpha = strength * .67f),
                Color(0xFF101B1E).copy(alpha = strength * .77f),
                tint.copy(alpha = strength * .48f)
            )
        )
    }
    Box(modifier.clip(shape)) {
        // Real GPU blur is limited to the artwork layer, never the text/icons above it.
        if (backdrop?.startsWith("https://") == true) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize().blur(backdropBlur)
                    .graphicsLayer { alpha = backdropOpacity },
                contentScale = ContentScale.Crop
            )
        }
        Box(Modifier.matchParentSize().background(fill, shape))
        Box(Modifier.matchParentSize().border(BorderStroke(1.dp, edge), shape))
        content()
    }
}

@Composable
private fun Tactile(modifier: Modifier = Modifier, action: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .965f else 1f, animationSpec = tween(if (reducedMotion) 0 else 160), label = "press")
    Box(modifier.scale(scale).clickable(interactionSource = interaction, indication = null, onClick = action), content = content)
}

@Composable
private fun Ambient(state: AurelisState) {
    val settings = state.settings
    val c by animateColorAsState(
        state.ambientColor, animationSpec = tween(if (settings.motion) 1200 else 0),
        label = "ambientPalette"
    )
    val strength = if (settings.ambient) settings.ambientIntensity else 0f
    Box(Modifier.fillMaxSize().background(bg)) {
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(.57f).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(
                    c.mix(Color(0xFF4C8A79), .20f).copy(alpha = .67f * strength),
                    c.copy(alpha = .24f * strength), Color.Transparent
                )))
        )
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(.61f).align(Alignment.BottomEnd)
                .background(Brush.radialGradient(listOf(
                    c.mix(Color(0xFF477B78), .27f).copy(alpha = .50f * strength),
                    c.copy(alpha = .13f * strength), Color.Transparent
                )))
        )
        Box(
            Modifier.fillMaxWidth(.81f).fillMaxHeight(.40f).align(Alignment.CenterStart)
                .background(Brush.radialGradient(listOf(
                    Color(0xFF3A7775).copy(alpha = .22f * strength), Color.Transparent
                )))
        )
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = .18f), bg.copy(alpha = .48f)))
        ))
    }
}

@Composable
fun AurelisScreen(state: AurelisState) {
    val context = LocalContext.current
    val updater = remember(context) { AurelisUpdater(context) }
    val pageScope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(state.settings.checkUpdates) {
        while (state.settings.checkUpdates) {
            updater.check()
            delay(6L * 60L * 60L * 1000L)
        }
    }
    var tab by rememberSaveable { mutableStateOf("Home") }
    var librarySegment by rememberSaveable { mutableStateOf("Disukai") }
    val homeScrollState = rememberLazyListState()
    val searchScrollState = rememberLazyListState()
    val libraryScrollState = rememberLazyListState()
    var compactDock by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showYouTubeLogin by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var creatingPlaylist by remember { mutableStateOf(false) }
    LaunchedEffect(searchQuery) {
        // Cancel the previous network request immediately on each keystroke.
        state.cancelSearch()
        if (searchQuery.isNotBlank()) {
            delay(300)
            state.search(searchQuery)
        }
    }
    LaunchedEffect(state.current) { if (state.current == null) expanded = false }
    LaunchedEffect(state.youtube.isConnected) {
        // While app is composed, refresh the authenticated YouTube Music
        // library at most once per minute. This is polling, not push events.
        while (true) {
            state.youtube.refresh()
            delay(60_000L)
        }
    }
    LaunchedEffect(tab, state.current?.key) {
        compactDock = false
        if (state.current == null) return@LaunchedEffect
        val activeScroll = when (tab) {
            "Search" -> searchScrollState
            "Library" -> libraryScrollState
            else -> homeScrollState
        }
        snapshotFlow { activeScroll.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (scrolling) compactDock = true
                else {
                    // Repeated scrolls cancel and restart the full idle interval.
                    delay(3000L)
                    compactDock = false
                }
            }
    }

    // One page hierarchy is reused in both orientations. LazyListStates are
    // retained across rotations, avoiding list jumps and duplicate network loads.
    val pageContent: @Composable () -> Unit = {
                    AnimatedContent(targetState = tab, label = "page", transitionSpec = {
                        (
                            fadeIn(tween(if (state.settings.motion) 270 else 0)) +
                                slideInVertically(tween(if (state.settings.motion) 320 else 0)) { it / 20 }
                        ) togetherWith fadeOut(tween(if (state.settings.motion) 190 else 0))
                    }) { page ->
                        when (page) {
                            "Home" -> HomePage(state, homeScrollState,
                                onPlay = { state.play(it) },
                                onMenu = { menuSong = it },
                                onConnect = { showYouTubeLogin = true },
                                onPlaylist = { playlist ->
                                    tab = "Library"
                                    librarySegment = "Playlist"
                                    pageScope.launch {
                                        val tracks = state.youtube.playlistTracks(playlist)
                                        if (tracks.isNotEmpty()) {
                                            state.playPlaylist(playlist.title, tracks)
                                            expanded = true
                                        }
                                    }
                                })
                            "Search" -> SearchPage(state, searchQuery, searchScrollState,
                                onPlay = { state.play(it) }, onMenu = { menuSong = it })
                            else -> LibraryPage(state, libraryScrollState,
                                segment = librarySegment,
                                onSegment = { librarySegment = it },
                                onPlay = { state.play(it) },
                                onMenu = { menuSong = it },
                                onCreate = { creatingPlaylist = true },
                                onConnectYouTube = { showYouTubeLogin = true },
                                onOpenPlayer = { expanded = true })
                        }
                    }
    }

    // Shared three-finger rail gesture: the same action works in the main
    // landscape menu AND inside full-screen Music Player. The latest value is
    // always read without restarting the detector on every recomposition.
    val gestureSideChange by rememberUpdatedState<(Boolean) -> Unit>({ moveRight ->
        if (state.settings.landscapeRailRight != moveRight) {
            state.update(state.settings.copy(landscapeRailRight = moveRight))
        }
    })

    CompositionLocalProvider(
        LocalGlassDensity provides state.settings.glassOpacity,
        LocalReducedMotion provides !state.settings.motion,
        LocalVisualStyle provides state.settings.visualStyle,
        LocalAppLanguage provides state.settings.language
    ) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = lime, secondary = Color(0xFF98CDB9), background = bg,
        surface = Color(0xFF15221F), onSurface = white, onBackground = white
    )) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(isLandscape) {
                    if (isLandscape) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var start: Offset? = null
                            var switched = false
                            var heldThree = false
                            do {
                                val gesture = awaitPointerEvent(PointerEventPass.Initial)
                                val touches = gesture.changes.filter { it.pressed }
                                if (touches.size == 3) {
                                    val centre = Offset(
                                        touches.map { it.position.x }.average().toFloat(),
                                        touches.map { it.position.y }.average().toFloat()
                                    )
                                    if (!heldThree) {
                                        start = centre
                                        heldThree = true
                                    }
                                    val origin = start ?: centre
                                    val dx = centre.x - origin.x
                                    val dy = centre.y - origin.y
                                    if (!switched && abs(dx) > 76.dp.toPx() &&
                                        abs(dx) > abs(dy) * 1.55f) {
                                        switched = true
                                        gestureSideChange(dx > 0)
                                    }
                                } else if (!switched) {
                                    heldThree = false
                                    start = null
                                }
                                // Leave ordinary single-finger seek/scroll and
                                // two-finger gestures completely untouched.
                                if (switched) gesture.changes.forEach { it.consume() }
                            } while (gesture.changes.any { it.pressed })
                        }
                    }
                }
        ) {
            Ambient(state)
            if (!isLandscape) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(tab, onSettings = { showSettings = true }, onLibrary = { tab = "Library" })
                Box(Modifier.weight(1f)) {
                    pageContent()
                }
                // Bottom capsules float over scrollable content. Each LazyColumn
                // supplies its own bottom padding to keep the final row reachable.
            }
            Column(Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(horizontal = 15.dp).padding(bottom = 10.dp)) {
                state.current?.let {
                    AnimatedVisibility(
                        visible = !expanded && !compactDock,
                        // Mini capsule gracefully merges into the full player
                        // rather than disappearing abruptly before the cover arrives.
                        enter = fadeIn(tween(if (state.settings.motion) 230 else 0,
                                delayMillis = if (state.settings.motion) 85 else 0)) +
                            scaleIn(initialScale = .91f,
                                transformOrigin = TransformOrigin(.5f, 1f),
                                animationSpec = spring(
                                    dampingRatio = .84f,
                                    stiffness = Spring.StiffnessMediumLow)) +
                            slideInVertically(tween(if (state.settings.motion) 325 else 0,
                                easing = LinearOutSlowInEasing)) { it / 4 },
                        exit = fadeOut(tween(if (state.settings.motion) 160 else 0)) +
                            scaleOut(targetScale = .88f,
                                transformOrigin = TransformOrigin(.5f, 1f),
                                animationSpec = tween(if (state.settings.motion) 240 else 0,
                                    easing = FastOutSlowInEasing)) +
                            slideOutVertically(tween(if (state.settings.motion) 250 else 0)) { it / 4 }
                    ) {
                        MiniPlayer(state, onExpand = { expanded = true })
                    }
                }
                val miniGap by animateDpAsState(
                    targetValue = if (state.current != null && !expanded && !compactDock) 10.dp else 0.dp,
                    animationSpec = if (!state.settings.motion) tween(0)
                        else spring(dampingRatio = .9f, stiffness = Spring.StiffnessMediumLow),
                    label = "miniPlayerDockGap"
                )
                Spacer(Modifier.height(miniGap))
                IslandDock(
                    tab = tab, expanded = searchExpanded, query = searchQuery,
                    onTab = { tab = it; searchExpanded = false; compactDock = false },
                    onSearchExpand = {
                        val wasCompact = compactDock
                        tab = "Search"
                        searchExpanded = if (wasCompact) true else !searchExpanded
                        compactDock = false
                    },
                    onQuery = { searchQuery = it },
                    artwork = state.current?.artwork.orEmpty(),
                    state = state,
                    compact = compactDock && !expanded,
                    onOpenNav = { compactDock = false; searchExpanded = false },
                    onExpandPlayer = { expanded = true }
                )
            }
            } else {
                // The Home/Search/Library/Settings rail follows the SAME persisted
                // side as the full-player rail. Move the rail itself with a
                // spring, while the page gently reflows to the opposite side.
                BoxWithConstraints(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                ) {
                    val rightRail = state.settings.landscapeRailRight
                    val railWidth = 79.dp
                    val railTravel by animateDpAsState(
                        targetValue = if (rightRail) (maxWidth - railWidth).coerceAtLeast(0.dp) else 0.dp,
                        animationSpec = if (!state.settings.motion) tween(0)
                            else spring(dampingRatio = .86f,
                                stiffness = Spring.StiffnessMediumLow),
                        label = "mainLandscapeRailSlide"
                    )
                    val leftInset by animateDpAsState(
                        targetValue = if (rightRail) 0.dp else railWidth,
                        animationSpec = if (!state.settings.motion) tween(0)
                            else spring(dampingRatio = .90f,
                                stiffness = Spring.StiffnessMediumLow),
                        label = "mainLandscapeLeftInset"
                    )
                    val rightInset by animateDpAsState(
                        targetValue = if (rightRail) railWidth else 0.dp,
                        animationSpec = if (!state.settings.motion) tween(0)
                            else spring(dampingRatio = .90f,
                                stiffness = Spring.StiffnessMediumLow),
                        label = "mainLandscapeRightInset"
                    )
                    // A spring can temporarily overshoot *below zero* when
                    // moving either inset toward 0.dp. Compose PaddingKt
                    // rejects negative values and crashed HyperOS with
                    // "Padding must be non-negative". Bound only the
                    // geometry; keep the rail's spring motion untouched.
                    val safeLeftInset = leftInset.coerceIn(0.dp, railWidth)
                    val safeRightInset = rightInset.coerceIn(0.dp, railWidth)
                    val safeRailTravel = railTravel.coerceIn(
                        0.dp, (maxWidth - railWidth).coerceAtLeast(0.dp)
                    )
                    Box(Modifier.fillMaxSize().padding(
                        start = safeLeftInset, end = safeRightInset
                    )) {
                        pageContent()
                    }
                    BoxWithConstraints(
                        Modifier.offset(x = safeRailTravel)
                            .width(railWidth).fillMaxHeight()
                            .padding(
                                start = if (rightRail) 7.dp else 10.dp,
                                end = if (rightRail) 10.dp else 0.dp,
                                top = 72.dp, bottom = 8.dp
                            )
                    ) {
                        val navigationHeight = (maxHeight - 71.dp)
                            .coerceIn(116.dp, 202.dp)
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LandscapeNavigationRail(
                                tab = tab,
                                artwork = state.current?.artwork.orEmpty(),
                                state = state,
                                railHeight = navigationHeight,
                                onTab = { next ->
                                    tab = next
                                    searchExpanded = false
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            Tactile(action = { showSettings = true }) {
                                Glass(Modifier.size(62.dp), RoundedCornerShape(31.dp),
                                    tint = state.ambientColor, opacity = .43f,
                                    backdrop = state.current?.artwork) {
                                    Icon(Icons.Rounded.Settings, "Pengaturan",
                                        Modifier.size(23.dp).align(Alignment.Center),
                                        tint = white)
                                }
                            }
                        }
                    }
                }
                // Keep the scrim in the ROOT coordinate system so that the
                // system-bar inset, left navigation area and right content
                // have one uninterrupted atmospheric color field. The old
                // page-only 94dp scrim started at x=79dp and created a seam.
                // This layer has no clickable/pointer handlers; capsules are
                // drawn above it and remain fully interactive.
                Box(Modifier.fillMaxWidth().height(144.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to bg.copy(alpha = .88f),
                            .24f to bg.copy(alpha = .78f),
                            .58f to bg.copy(alpha = .34f),
                            1f to Color.Transparent
                        )
                    )))
                LandscapeTopDock(
                    modifier = Modifier.align(Alignment.TopStart)
                        .statusBarsPadding().padding(start = 14.dp, end = 13.dp, top = 8.dp),
                    state = state,
                    query = searchQuery,
                    searchExpanded = searchExpanded,
                    onSearchClick = {
                        tab = "Search"
                        searchExpanded = !searchExpanded
                        compactDock = false
                    },
                    onQuery = { searchQuery = it },
                    onExpandPlayer = { expanded = true }
                )
            }
            // Restore the original satisfying spring + mini-player zoom
            // choreography. An UN-SCALED blurred ambient underlay fills the
            // exposed screen edges during the scale/slide frames; a brief soft
            // perimeter feather prevents a rectangular seam. The whole player
            // still zooms, unlike v1.2.11's simplified fade-only transition.
            val playerMotion = state.settings.motion
            val playerOrigin = if (isLandscape)
                TransformOrigin(.23f, .18f) else TransformOrigin(.50f, .92f)
            AnimatedVisibility(
                visible = expanded && state.current != null,
                enter = fadeIn(tween(if (playerMotion) 260 else 0)) ,
                exit = fadeOut(tween(if (playerMotion) 295 else 0))
            ) {
                Box(Modifier.fillMaxSize().background(bg)) {
                    Ambient(state)
                    state.current?.artwork?.takeIf { it.startsWith("https://") }?.let { url ->
                        AsyncImage(
                            model = url, contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                                .blur(88.dp).graphicsLayer { alpha = .36f },
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(
                                bg.copy(alpha = .62f),
                                bg.copy(alpha = .34f),
                                bg.copy(alpha = .64f)
                            ))
                        )
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded && state.current != null,
                enter = fadeIn(tween(if (playerMotion) 300 else 0,
                    easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = if (isLandscape) .89f else .87f,
                        transformOrigin = playerOrigin,
                        animationSpec = if (playerMotion) spring(
                            dampingRatio = .80f,
                            stiffness = Spring.StiffnessMediumLow
                        ) else tween(0)) +
                    slideInVertically(
                        animationSpec = tween(if (playerMotion) 420 else 0,
                            easing = FastOutSlowInEasing)
                    ) { height -> if (isLandscape) -height / 13 else height / 6 },
                exit = fadeOut(tween(if (playerMotion) 255 else 0,
                    easing = FastOutSlowInEasing)) +
                    scaleOut(targetScale = if (isLandscape) .90f else .88f,
                        transformOrigin = playerOrigin,
                        animationSpec = tween(if (playerMotion) 320 else 0,
                            easing = FastOutSlowInEasing)) +
                    slideOutVertically(
                        animationSpec = tween(if (playerMotion) 305 else 0,
                            easing = FastOutSlowInEasing)
                    ) { height -> if (isLandscape) -height / 14 else height / 7 }
            ) {
                val fullSize by transition.animateFloat(
                    transitionSpec = { tween(if (playerMotion) 300 else 0) },
                    label = "playerEdgeFeather"
                ) { state ->
                    if (state == EnterExitState.Visible) 1f else 0f
                }
                Box(Modifier.fillMaxSize().featherPlayerPerimeter(1f - fullSize)) {
                    FullPlayer(
                        state, onClose = { expanded = false },
                        onQueue = { showQueue = true },
                        onMenu = { state.current?.let { menuSong = it } },
                        presentationScale = 1f
                    )
                }
            }
            UpdateAnnouncement(updater, Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 19.dp, vertical = 96.dp))
        }
        if (showSettings) SettingsSheet(
            state, updater,
            onClose = { showSettings = false },
            onConnectYouTube = {
                showSettings = false
                showYouTubeLogin = true
            }
        )
        if (showYouTubeLogin) YouTubeMusicLogin(state.youtube) {
            showYouTubeLogin = false
        }
        if (showQueue) QueueSheet(state, onClose = { showQueue = false })
        menuSong?.let { song -> SongMenu(state, song, onClose = { menuSong = null }) }
        if (creatingPlaylist) AlertDialog(
            onDismissRequest = { creatingPlaylist = false },
            title = { Text(uiText("Playlist baru"), fontWeight = FontWeight.SemiBold) },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text(uiText("Nama playlist")) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { state.addPlaylist(playlistName); playlistName = ""; creatingPlaylist = false }) { Text(uiText("Buat")) } },
            dismissButton = { TextButton(onClick = { creatingPlaylist = false }) { Text(uiText("Batal")) } }
        )
        state.error?.let { e -> AlertDialog(onDismissRequest = state::dismissError,
            title = { Text(uiText("Aurelis")) }, text = { Text(e) },
            confirmButton = { TextButton(onClick = state::dismissError) { Text(uiText("Tutup")) } }) }
    }
    }
}

@Composable
private fun UpdateAnnouncement(updater: AurelisUpdater, modifier: Modifier = Modifier) {
    val release = updater.available
    AnimatedVisibility(
        visible = release != null,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(
            spring(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow)
        ) { it / 2 } + scaleIn(initialScale = .90f),
        exit = fadeOut(tween(160)) +
            slideOutVertically(tween(210)) { it / 3 } + scaleOut(targetScale = .94f)
    ) {
        if (release != null) {
            Glass(
                Modifier.fillMaxWidth().shadow(22.dp, RoundedCornerShape(27.dp)),
                RoundedCornerShape(27.dp), tint = Color(0xFF2B6553),
                opacity = .88f
            ) {
                Column(Modifier.fillMaxWidth().padding(19.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glass(Modifier.size(44.dp), RoundedCornerShape(15.dp),
                            tint = Color(0xFF4D8674), opacity = .77f) {
                            Icon(Icons.Rounded.SystemUpdate, null,
                                Modifier.size(23.dp).align(Alignment.Center), tint = lime)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(uiText("Versi baru tersedia"), color = white,
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(release.tag, color = lime, fontSize = 12.sp)
                        }
                        IconButton(onClick = { updater.dismiss() }) {
                            Icon(Icons.Rounded.Close, "Tutup pembaruan",
                                Modifier.size(19.dp), tint = mist)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(uiText("Unduh dari GitHub dan pasang dengan izin Android."),
                        color = mist, fontSize = 12.sp, lineHeight = 18.sp)
                    if (updater.downloading || updater.downloaded) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { updater.progress },
                            modifier = Modifier.fillMaxWidth().height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = lime, trackColor = white.copy(alpha = .13f)
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(if (updater.downloaded) "APK selesai diunduh"
                            else "${(updater.progress * 100).roundToInt()}% terunduh",
                            color = lime, fontSize = 11.sp)
                    }
                    updater.message?.let { msg ->
                        Spacer(Modifier.height(9.dp))
                        Text(msg, color = white.copy(alpha = .88f),
                            fontSize = 11.sp, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.height(17.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tactile(Modifier.weight(1f), { updater.dismiss() }) {
                            Glass(Modifier.fillMaxWidth().height(44.dp),
                                RoundedCornerShape(19.dp), opacity = .48f) {
                                Text(uiText("Nanti"), Modifier.align(Alignment.Center),
                                    color = mist, fontSize = 13.sp)
                            }
                        }
                        Tactile(Modifier.weight(1.35f), {
                            if (updater.downloaded) updater.install() else updater.download()
                        }) {
                            Glass(Modifier.fillMaxWidth().height(44.dp),
                                RoundedCornerShape(19.dp), tint = Color(0xFF659F88),
                                opacity = if (updater.downloading) .38f else .87f) {
                                Row(Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    if (updater.downloading) {
                                        CircularProgressIndicator(Modifier.size(17.dp),
                                            strokeWidth = 2.dp, color = lime)
                                    } else {
                                        Icon(if (updater.downloaded) Icons.Rounded.InstallMobile
                                            else Icons.Rounded.Download,
                                            null, Modifier.size(18.dp), tint = white)
                                    }
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (updater.downloading) "Mengunduh"
                                        else if (updater.downloaded) "Instal"
                                        else "Unduh APK",
                                        color = white, fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(tab: String, onSettings: () -> Unit, onLibrary: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 23.dp, end = 20.dp, top = 16.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", fontSize = 27.sp, color = lime)
        Spacer(Modifier.width(11.dp))
        Column {
            Text(uiText("Aurelis"), color = white, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = -.6.sp)
            Text("SYMPHONY", color = mist, letterSpacing = 2.3.sp, fontSize = 9.sp)
        }
        Spacer(Modifier.weight(1f))
        CircleAction(Icons.Default.Settings, "Pengaturan", onSettings)
        Spacer(Modifier.width(9.dp))
        Tactile(action = onLibrary) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF36574C)), contentAlignment = Alignment.Center) {
                Text("A", fontWeight = FontWeight.Bold, color = Color(0xFFE9FFEE))
            }
        }
    }
}

@Composable
private fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit, size: Dp = 40.dp) {
    Tactile(action = action) {
        Glass(Modifier.size(size), RoundedCornerShape(size / 2), opacity = .60f) {
            Icon(icon, label, Modifier.size(19.dp).align(Alignment.Center), tint = white)
        }
    }
}

@Composable
private fun HomePage(
    state: AurelisState,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (Song) -> Unit,
    onMenu: (Song) -> Unit,
    onConnect: () -> Unit,
    onPlaylist: (YouTubePlaylist) -> Unit
) {
    var mood by rememberSaveable { mutableStateOf("Semua") }
    val libraryStamp = state.libraryStamp
    val recentlyPlayed = remember(libraryStamp, state.youtube.history) {
        (state.library.history + state.youtube.history).distinctBy { it.key }.take(20)
    }
    val selectedSongs = remember(mood, state.home, state.youtube.shelves, libraryStamp) {
        val pool = (state.youtube.shelves.flatMap { it.tracks } + state.home)
            .distinctBy { it.key }
        val filtered = when (mood) {
            "Santai" -> pool.filter {
                val label = (it.title + " " + it.artist).lowercase()
                listOf("chill", "slowed", "lofi", "acoustic", "piano", "sleep")
                    .any(label::contains)
            }
            "Energi" -> pool.filter {
                val label = (it.title + " " + it.artist).lowercase()
                listOf("remix", "rock", "dance", "hardstyle", "phonk", "sped")
                    .any(label::contains)
            }
            "Disukai" -> state.library.favorites
            "Baru" -> pool.asReversed()
            else -> pool
        }
        filtered.ifEmpty { if (mood == "Disukai") emptyList() else pool }.take(40)
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize().featherScrollEdges(
            top = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 32.dp else 30.dp,
            bottom = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 40.dp else 100.dp
        ),
        contentPadding = PaddingValues(
            start = 21.dp, end = 21.dp,
            top = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 95.dp else 17.dp,
            bottom = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 32.dp else 184.dp
        ),
        verticalArrangement = Arrangement.spacedBy(19.dp)
    ) {
        item(key = "welcome") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("AURELIS SYMPHONY"), color = lime.copy(alpha = .81f),
                            fontSize = 10.sp, letterSpacing = 1.9.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text(uiText("Musik untuk harimu."), color = white, fontSize = 30.sp,
                            letterSpacing = (-.9).sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(uiText("Temukan lagu, suasana, dan rekomendasi favorit."),
                            color = mist, fontSize = 12.sp)
                    }
                    if (state.youtube.isConnected) {
                        Glass(Modifier.size(42.dp), RoundedCornerShape(20.dp),
                            tint = state.ambientColor, opacity = .52f) {
                            Icon(Icons.Rounded.CheckCircle, "YouTube Music terhubung",
                                tint = lime, modifier = Modifier.size(21.dp)
                                    .align(Alignment.Center))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Semua", "Santai", "Energi", "Disukai", "Baru")) { filter ->
                        Tactile(action = { mood = filter }) {
                            Glass(
                                shape = RoundedCornerShape(23.dp),
                                tint = if (filter == mood) state.ambientColor else groove,
                                opacity = if (filter == mood) .74f else .38f
                            ) {
                                Text(uiText(filter),
                                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    fontSize = 12.sp,
                                    color = if (filter == mood) lime else mist,
                                    fontWeight = if (filter == mood)
                                        FontWeight.SemiBold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        if (state.loadingHome) item(key = "home-loading") {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                color = lime
            )
        }

        if (!state.youtube.isConnected) item(key = "youtube-connect") {
            Tactile(Modifier.fillMaxWidth(), onConnect) {
                Glass(Modifier.fillMaxWidth().height(71.dp),
                    RoundedCornerShape(23.dp), tint = state.ambientColor,
                    opacity = .48f) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LibraryMusic, null,
                            Modifier.size(24.dp), tint = lime)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(uiText("Hubungkan YouTube Music"), color = white,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(uiText("Rekomendasi, history, dan playlist dari akunmu"),
                                color = mist, fontSize = 10.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, null,
                            Modifier.size(21.dp), tint = mist)
                    }
                }
            }
        }

        val feature = selectedSongs.firstOrNull() ?: state.home.firstOrNull()
        feature?.let { song ->
            item(key = "feature") {
                FeaturedSong(song, state, onPlay = { onPlay(song) })
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item(key = "listen-again") {
                HomeSongCarousel(
                    "Dengarkan lagi",
                    "Kembali ke lagu yang kamu putar",
                    recentlyPlayed,
                    onPlay
                )
            }
        }

        if (state.youtube.shelves.isNotEmpty()) {
            items(state.youtube.shelves.take(6), key = { "yt-shelf-${it.title}" }) { shelf ->
                HomeSongCarousel(
                    shelf.title,
                    "Dari YouTube Music · diperbarui saat terhubung",
                    shelf.tracks,
                    onPlay
                )
            }
        }

        if (selectedSongs.isNotEmpty()) {
            item(key = "made-for-you") {
                HomeSongCarousel(
                    if (mood == "Semua") "Mix untukmu" else "Pilihan $mood",
                    "Musik pilihan dengan rekomendasi Aurelis",
                    selectedSongs,
                    onPlay
                )
            }
            item(key = "tracks-heading") {
                SectionHeading(uiText("Lagu yang mungkin kamu suka"), uiText("Jelajahi dan masukkan ke antrean"))
            }
            items(selectedSongs.take(12), key = { "home-song-${it.key}" }) { song ->
                SongRow(song, state, onPlay = { onPlay(song) },
                    onMenu = { onMenu(song) })
            }
        }

        if (state.youtube.playlists.isNotEmpty()) {
            item(key = "yt-playlists") {
                Column {
                    SectionHeading(uiText("Playlist YouTube Music"), "Koleksi tersinkron dari akun yang terhubung")
                    Spacer(Modifier.height(11.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.youtube.playlists, key = { it.id }) { playlist ->
                            Tactile(Modifier.width(149.dp), { onPlaylist(playlist) }) {
                                Column {
                                    Glass(Modifier.size(149.dp), RoundedCornerShape(20.dp),
                                        tint = state.ambientColor, opacity = .44f,
                                        backdrop = playlist.artwork) {
                                        AsyncImage(
                                            model = playlist.artwork.takeIf { it.isNotBlank() },
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(playlist.title, color = white, fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.originals.isNotEmpty()) item(key = "originals") {
            HomeSongCarousel(
                "Aurelis Originals",
                "Dengarkan musik orisinal dari perpustakaan",
                state.originals,
                onPlay
            )
        }

        if (!state.loadingHome && state.home.isEmpty() &&
            state.youtube.shelves.isEmpty() && state.originals.isEmpty())
            item(key = "empty-home") {
                EmptyPage(uiText("Belum ada musik"), "Periksa internet atau hubungkan akun YouTube Music.")
            }
    }
}

@Composable
private fun HomeSongCarousel(
    title: String, subtitle: String,
    songs: List<Song>,
    onPlay: (Song) -> Unit
) {
    Column {
        SectionHeading(title, subtitle)
        Spacer(Modifier.height(11.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            items(songs.take(20), key = { it.key }) { song ->
                Tactile(Modifier.width(153.dp), { onPlay(song) }) {
                    Column {
                        Glass(
                            Modifier.size(153.dp),
                            RoundedCornerShape(23.dp),
                            opacity = .37f,
                            backdrop = song.artwork
                        ) {
                            AsyncImage(
                                model = song.artwork.takeIf { it.isNotBlank() },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(
                                    Color.Transparent,
                                    bg.copy(alpha = .23f)
                                ))
                            ))
                            Icon(
                                Icons.Rounded.PlayArrow, "Putar",
                                Modifier.align(Alignment.BottomEnd)
                                    .padding(10.dp).size(25.dp),
                                tint = white
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(song.title, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = white, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(song.artist, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = mist, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedSong(song: Song, state: AurelisState, onPlay: () -> Unit) {
    Tactile(Modifier.fillMaxWidth().height(214.dp), onPlay) {
        Glass(Modifier.fillMaxSize(), RoundedCornerShape(30.dp), tint = state.ambientColor.mix(Color(0xFF29443B), .42f), opacity = .87f) {
            AsyncImage(model = song.artwork.takeIf { it.isNotBlank() }, contentDescription = null,
                modifier = Modifier.size(190.dp).align(Alignment.CenterEnd).padding(10.dp).clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0F1A1B).copy(alpha = .94f), Color(0xFF0F1A1B).copy(alpha = .35f), Color.Transparent))))
            Column(Modifier.align(Alignment.BottomStart).padding(23.dp).fillMaxWidth(.75f)) {
                Text(uiText("ON THE RADAR"), color = lime, fontSize = 10.sp, letterSpacing = 1.7.sp)
                Spacer(Modifier.height(8.dp))
                Text(song.title, color = white, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 27.sp, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = mist, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = lime, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(5.dp)); Text(uiText("Putar sekarang"), fontSize = 12.sp, color = white, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Text(title, color = white, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = -.6.sp)
        Spacer(Modifier.height(4.dp)); Text(subtitle, color = mist, fontSize = 12.sp)
    }
}

@Composable
private fun SearchPage(state: AurelisState, query: String,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (Song) -> Unit, onMenu: (Song) -> Unit) {
    LazyColumn(state = scrollState,
        modifier = Modifier.fillMaxSize().featherScrollEdges(
            top = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 32.dp else 30.dp,
            bottom = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 40.dp else 100.dp
        ),
        contentPadding = PaddingValues(start = 21.dp, end = 21.dp,
            top = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 95.dp else 18.dp,
            bottom = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 32.dp else 180.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { SectionHeading(uiText("Search"), if (query.isBlank()) "Mulai cari dari Island Search di bawah" else "Hasil untuk “${query.take(60)}”") }
        if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = lime) }
        if (query.isBlank()) item { EmptyPage("Temukan sesuatu yang baru", "Cari judul, artis, atau musik untuk didengarkan.") }
        else if (!state.searching && state.results.isEmpty()) item { EmptyPage("Belum ada hasil", "Coba kata kunci yang berbeda.") }
        items(state.results, key = { "result-${it.key}" }) { song -> SongRow(song, state, onPlay = { onPlay(song) }, onMenu = { onMenu(song) }) }
    }
}

@Composable
private fun LibraryPage(
    state: AurelisState,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    segment: String,
    onSegment: (String) -> Unit,
    onPlay: (Song) -> Unit,
    onMenu: (Song) -> Unit,
    onCreate: () -> Unit,
    onConnectYouTube: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var expandedYouTubeId by rememberSaveable { mutableStateOf<String?>(null) }
    var importUrl by rememberSaveable { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val libraryStamp = state.libraryStamp
    val list = remember(segment, libraryStamp) {
        when (segment) {
            "Disukai" -> state.library.favorites
            "Riwayat" -> state.library.history
            else -> emptyList()
        }
    }
    val youtube = state.youtube
    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize().featherScrollEdges(
            top = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 32.dp else 30.dp,
            bottom = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 40.dp else 100.dp
        ),
        contentPadding = PaddingValues(
            start = 21.dp, end = 21.dp,
            top = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 95.dp else 18.dp,
            bottom = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 32.dp else 180.dp
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item(key = "library-head") {
            SectionHeading(uiText("Your Library"),
                if (segment == "Riwayat")
                    "${list.size} pemutaran terakhir"
                else "Playlist, lagu disukai, dan riwayat musikmu")
        }
        item(key = "library-tabs") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(listOf("Disukai", "Riwayat", "Playlist", "YouTube")) { item ->
                    Tactile(action = { onSegment(item) }) {
                        Glass(shape = RoundedCornerShape(22.dp),
                            tint = if (item == segment) Color(0xFF476C5C)
                                else Color(0xFF1F302D),
                            opacity = if (item == segment) .8f else .44f) {
                            Text(item,
                                Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
                                color = if (item == segment) white else mist,
                                fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (segment == "Playlist") {
            item(key = "playlist-create") {
                Tactile(Modifier.fillMaxWidth(), onCreate) {
                    Glass(Modifier.fillMaxWidth(),
                        RoundedCornerShape(19.dp), opacity = .46f) {
                        Row(Modifier.padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, null, tint = lime)
                            Spacer(Modifier.width(12.dp))
                            Text(uiText("Buat playlist"), color = white)
                        }
                    }
                }
            }

            state.library.playlists.forEach { (name, tracks) ->
                item(key = "local-playlist-$name") {
                    var opened by remember(name) { mutableStateOf(false) }
                    Column {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Tactile(Modifier.weight(1f), {
                                if (tracks.isNotEmpty()) {
                                    state.playPlaylist(name, tracks)
                                    onOpenPlayer()
                                } else opened = !opened
                            }) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.LibraryMusic, null,
                                        tint = lime, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(name, color = white, fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold)
                                        Text("${tracks.size} lagu · Aurelis",
                                            color = mist, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, "Putar playlist",
                                        tint = if (tracks.isNotEmpty()) lime else mist,
                                        modifier = Modifier.size(23.dp))
                                }
                            }
                            IconButton(onClick = { opened = !opened }) {
                                AnimatedContent(opened, label = "expandLocalPlaylist",
                                    transitionSpec = {
                                        fadeIn(tween(170)) togetherWith fadeOut(tween(130))
                                    }) { isOpen ->
                                    Icon(if (isOpen) Icons.Rounded.ExpandLess
                                        else Icons.Rounded.ExpandMore,
                                        if (isOpen) "Sembunyikan lagu" else "Lihat lagu",
                                        tint = mist)
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = opened,
                            enter = fadeIn(tween(180)) +
                                expandVertically(tween(260)),
                            exit = fadeOut(tween(150)) +
                                shrinkVertically(tween(220))
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tracks.forEachIndexed { index, song ->
                                    SongRow(song, state,
                                        onPlay = {
                                            state.playPlaylist(name, tracks, index)
                                            onOpenPlayer()
                                        },
                                        onMenu = { onMenu(song) },
                                        localPlaylist = name)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "yt-playlists-heading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("Playlist YouTube Music"), Modifier.weight(1f),
                        color = white, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    if (youtube.isConnected) IconButton(onClick = {
                        scope.launch { youtube.refresh(force = true) }
                    }) {
                        Icon(Icons.Rounded.Refresh, "Segarkan playlist", tint = lime)
                    }
                }
            }
            if (!youtube.isConnected) {
                item(key = "yt-connect") {
                    TextButton(onClick = onConnectYouTube) {
                        Text(uiText("Hubungkan akun untuk melihat playlist pribadi"),
                            color = lime, fontSize = 12.sp)
                    }
                }
            }
            if (youtube.playlists.isNotEmpty()) {
                // Parent LazyColumn owns scrolling. Expanding a remote playlist
                // inserts individually lazy song rows directly beneath its card.
                youtube.playlists.forEach { playlist ->
                    item(key = "remote-${playlist.id}") {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Tactile(Modifier.weight(1f), {
                                scope.launch {
                                    val tracks = youtube.playlistTracks(playlist)
                                    if (tracks.isNotEmpty()) {
                                        state.playPlaylist(playlist.title, tracks)
                                        onOpenPlayer()
                                    }
                                }
                            }) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Glass(Modifier.size(55.dp),
                                        RoundedCornerShape(14.dp),
                                        tint = state.ambientColor, opacity = .45f) {
                                        AsyncImage(
                                            model = playlist.artwork.takeIf { it.isNotBlank() },
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(playlist.title, color = white,
                                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(4.dp))
                                        Text(uiText("YouTube"), color = lime, fontSize = 10.sp)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, "Putar playlist",
                                        tint = lime, modifier = Modifier.size(22.dp))
                                }
                            }
                            IconButton(onClick = {
                                if (expandedYouTubeId == playlist.id) {
                                    expandedYouTubeId = null
                                } else {
                                    expandedYouTubeId = playlist.id
                                    if (youtube.selectedPlaylist?.id != playlist.id) {
                                        scope.launch { youtube.playlistTracks(playlist) }
                                    }
                                }
                            }) {
                                AnimatedContent(
                                    targetState = expandedYouTubeId == playlist.id,
                                    label = "youtubePlaylistExpand",
                                    transitionSpec = {
                                        fadeIn(tween(185)) togetherWith fadeOut(tween(135))
                                    }
                                ) { isExpanded ->
                                    Icon(if (isExpanded) Icons.Rounded.ExpandLess
                                        else Icons.Rounded.ExpandMore,
                                        if (isExpanded) "Tutup daftar lagu" else "Lihat lagu YouTube",
                                        tint = lime, modifier = Modifier.size(23.dp))
                                }
                            }
                        }
                    }
                    if (expandedYouTubeId == playlist.id) {
                        if (youtube.playlistLoading &&
                            youtube.selectedPlaylist?.id == playlist.id) {
                            item(key = "yt-spinner-${playlist.id}") {
                                LinearProgressIndicator(Modifier.fillMaxWidth(),
                                    color = lime, trackColor = white.copy(alpha = .10f))
                            }
                        }
                        if (youtube.selectedPlaylist?.id == playlist.id &&
                            !youtube.playlistLoading) {
                            if (youtube.playlistTracks.isEmpty()) {
                                item(key = "yt-empty-${playlist.id}") {
                                    Text("Playlist kosong atau belum dapat dibaca.",
                                        color = mist, fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 10.dp))
                                }
                            }
                            items(youtube.playlistTracks.size, key = { i ->
                                "yt-entry-${playlist.id}-$i-${youtube.playlistTracks[i].key}"
                            }) { i ->
                                val track = youtube.playlistTracks[i]
                                SongRow(track, state,
                                    onPlay = {
                                        state.playPlaylist(playlist.title,
                                            youtube.playlistTracks, i)
                                        onOpenPlayer()
                                    },
                                    onMenu = { onMenu(track) })
                            }
                        }
                    }
                }
            }

            item(key = "import-yt-playlist") {
                Column {
                    Text(uiText("Buka playlist dari tautan YouTube"),
                        color = white, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = importUrl,
                            onValueChange = { importUrl = it; importError = null },
                            singleLine = true,
                            placeholder = {
                                Text("music.youtube.com/playlist?list=…",
                                    fontSize = 11.sp)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        IconButton(onClick = {
                            runCatching {
                                youtube.playlistFromUrl(importUrl)
                            }.onSuccess { playlist ->
                                scope.launch {
                                    val tracks = youtube.playlistTracks(playlist)
                                    if (tracks.isNotEmpty()) {
                                        state.playPlaylist(playlist.title, tracks)
                                        onOpenPlayer()
                                    }
                                }
                            }.onFailure {
                                importError = it.message
                            }
                        }) {
                            Icon(Icons.Rounded.ArrowForward, "Buka playlist",
                                tint = lime, modifier = Modifier.size(23.dp))
                        }
                    }
                    importError?.let {
                        Text(it, color = Color(0xFFFFBABD), fontSize = 11.sp)
                    }
                }
            }
            // Imported playlists have no card above; keep their detailed
            // section below. Account playlists expand in-place under the card.
            youtube.selectedPlaylist?.takeIf { selected ->
                youtube.playlists.none { it.id == selected.id }
            }?.let { playlist ->
                item(key = "yt-current-playlist") {
                    Column {
                        DividerSoft()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(playlist.title, color = white, fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("YouTube · ${youtube.playlistTracks.size} lagu",
                                    color = lime, fontSize = 11.sp)
                            }
                            IconButton(onClick = {
                                state.playPlaylist(playlist.title, youtube.playlistTracks)
                                onOpenPlayer()
                            }, enabled = !youtube.playlistLoading &&
                                youtube.playlistTracks.isNotEmpty()) {
                                Icon(Icons.Rounded.PlayCircle, "Putar semua",
                                    tint = lime, modifier = Modifier.size(32.dp))
                            }
                            IconButton(onClick = { youtube.closePlaylist() }) {
                                Icon(Icons.Rounded.Close, "Tutup playlist", tint = mist)
                            }
                        }
                    }
                }
                if (youtube.playlistLoading) item(key = "yt-playlist-loading") {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = lime)
                }
                items(youtube.playlistTracks.size,
                    key = { i -> "import-track-$i-${youtube.playlistTracks[i].key}" }) { i ->
                    val song = youtube.playlistTracks[i]
                    SongRow(song, state,
                        onPlay = {
                            state.playPlaylist(playlist.title, youtube.playlistTracks, i)
                            onOpenPlayer()
                        },
                        onMenu = { onMenu(song) })
                }
            }
        } else if (segment == "YouTube") {
            if (!youtube.isConnected) item(key = "yt-login") {
                TextButton(onClick = onConnectYouTube) {
                    Text(uiText("Hubungkan YouTube Music"), color = lime)
                }
            } else {
                item(key = "yt-sync") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeading(uiText("Riwayat YouTube Music"), uiText("Lagu terbaru yang diputar lewat akun YouTube"))
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            scope.launch { youtube.refresh(force = true) }
                        }, enabled = !youtube.refreshing) {
                            Icon(Icons.Rounded.Refresh, "Segarkan", tint = lime)
                        }
                    }
                }
                if (youtube.history.isEmpty()) item {
                    EmptyPage("Riwayat akun belum tersedia",
                        "Coba segarkan. Riwayat YouTube bergantung pada akses akun.")
                }
                items(youtube.history.size, key = { i ->
                    "yt-history-${i}-${youtube.history[i].key}"
                }) { i ->
                    val song = youtube.history[i]
                    SongRow(song, state,
                        onPlay = { onPlay(song) },
                        onMenu = { onMenu(song) })
                }
            }
            youtube.status?.let { message ->
                item(key = "yt-status") {
                    Text(message, color = Color(0xFFFFC7C7), fontSize = 11.sp)
                }
            }
        } else if (list.isEmpty()) {
            item(key = "library-empty") {
                EmptyPage(uiText("Belum ada lagu"),
                    if (segment == "Disukai") "Ketuk ikon hati untuk menyimpan lagu."
                    else "Lagu yang kamu dengarkan akan muncul di sini.")
            }
        } else {
            items(list.size, key = { i -> "${segment}-$i-${list[i].key}" }) { i ->
                val song = list[i]
                SongRow(song, state,
                    onPlay = { onPlay(song) },
                    onMenu = { onMenu(song) })
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song, state: AurelisState,
    onPlay: () -> Unit, onMenu: () -> Unit,
    localPlaylist: String? = null
) {
    var menuOpen by remember(song.key) { mutableStateOf(false) }
    val popupTransition = remember(song.key) {
        androidx.compose.animation.core.MutableTransitionState(false)
    }
    popupTransition.targetState = menuOpen
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Tactile(Modifier.weight(1f), onPlay) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song, 59.dp, 15.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (state.current?.key == song.key) lime else white,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = mist, fontSize = 12.sp)
                }
            }
        }
        Box {
            // The same location becomes the menu rather than showing a second button underneath it.
            if (!popupTransition.currentState && !popupTransition.targetState) {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(43.dp)) {
                    Icon(Icons.Rounded.MoreVert, "Opsi lagu", tint = mist, modifier = Modifier.size(21.dp))
                }
            } else Spacer(Modifier.size(43.dp))
            InlineGlassSongMenu(state, song, menuOpen, { menuOpen = false },
                transitionState = popupTransition, localPlaylist = localPlaylist)
        }
    }
}

@Composable
private fun GlassPopupItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    action: () -> Unit) {
    Tactile(Modifier.fillMaxWidth(), action) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(19.dp), tint = lime)
            Spacer(Modifier.width(13.dp))
            Text(label, color = white, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        }
    }
}

private class AdaptiveGlassMenuPosition(
    private val preferDown: Boolean,
    private val inset: Int,
    private val preferRight: Boolean = false,
    private val railOnRight: Boolean = false
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val above = anchorBounds.top - inset
        val below = windowSize.height - anchorBounds.bottom - inset
        val putBelow = when {
            below >= height -> true
            above >= height -> false
            else -> below >= above
        }
        // The landscape rail's three-dot trigger sits at the BOTTOM corner:
        // left rail -> menu opens rightward; right rail -> menu opens leftward.
        // Other row menus preserve their original anchored placement.
        val y = if (preferRight || railOnRight) anchorBounds.bottom - height
            else if (putBelow) anchorBounds.top else anchorBounds.bottom - height
        val x = when {
            railOnRight -> anchorBounds.left - width - inset
            preferRight -> anchorBounds.right + inset
            else -> anchorBounds.right - width
        }
        return IntOffset(
            x.coerceIn(inset, max(inset, windowSize.width - width - inset)),
            y.coerceIn(inset, max(inset, windowSize.height - height - inset))
        )
    }
}

@Composable
private fun OverflowMorphButton(open: Boolean, toggle: () -> Unit) {
    Tactile(action = toggle) {
        Glass(Modifier.size(40.dp), RoundedCornerShape(20.dp),
            opacity = .61f) {
            AnimatedContent(open, label = "playerOverflowThreeDotsMorph",
                modifier = Modifier.align(Alignment.Center), transitionSpec = {
                    (fadeIn(tween(145)) + scaleIn(initialScale = .60f)) togetherWith
                        (fadeOut(tween(125)) + scaleOut(targetScale = .65f))
                }) { isOpen ->
                Icon(if (isOpen) Icons.Rounded.Close else Icons.Rounded.MoreHoriz,
                    if (isOpen) "Tutup menu" else "Menu pemutar",
                    Modifier.size(19.dp), tint = if (isOpen) lime else white)
            }
        }
    }
}

@Composable
private fun InlineGlassSongMenu(
    state: AurelisState, song: Song, expanded: Boolean, onDismiss: () -> Unit,
    lyricOffset: Boolean = false,
    transitionState: androidx.compose.animation.core.MutableTransitionState<Boolean>? = null,
    localPlaylist: String? = null,
    preferRight: Boolean = false,
    railOnRight: Boolean = false
) {
    val isGlass = LocalVisualStyle.current == VisualStyle.GLASS
    val visibility = transitionState ?: remember {
        androidx.compose.animation.core.MutableTransitionState(false)
    }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return
    val density = LocalDensity.current
    val margin = with(density) { 9.dp.roundToPx() }
    val configuration = LocalConfiguration.current
    val isLandscapeMenu = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val popupWidth = if (isLandscapeMenu)
        (configuration.screenWidthDp.dp * .45f).coerceIn(260.dp, 380.dp)
    else 252.dp
    val popupHeight = (configuration.screenHeightDp.dp - 32.dp)
        .coerceAtLeast(160.dp)
    val provider = remember(lyricOffset, margin, preferRight, railOnRight) {
        AdaptiveGlassMenuPosition(
            preferDown = lyricOffset, inset = margin,
            preferRight = preferRight, railOnRight = railOnRight
        )
    }
    val railPopup = preferRight || railOnRight
    val origin = when {
        railOnRight -> TransformOrigin(1f, 1f)
        preferRight -> TransformOrigin(0f, 1f)
        else -> TransformOrigin(1f, 0f)
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            // Keep popup window bounds stable during its morph.
            // Follow the physical button: bottom-left -> grow right/up,
            // bottom-right -> grow left/up. Classic row menus are unchanged.
            enter = scaleIn(
                initialScale = if (railPopup) .40f else .22f,
                transformOrigin = origin,
                animationSpec = spring(dampingRatio = .84f,
                    stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(205)) +
                (if (railPopup) slideInHorizontally(tween(260,
                    easing = FastOutSlowInEasing)) {
                        if (railOnRight) it / 8 else -it / 8
                    } else slideInHorizontally(tween(0)) { 0 }) +
                (if (railPopup) slideInVertically(tween(245)) { it / 12 }
                 else slideInVertically(tween(0)) { 0 }),
            exit = scaleOut(
                targetScale = if (railPopup) .46f else .22f,
                transformOrigin = origin,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(195)) +
                (if (railPopup) slideOutHorizontally(tween(235)) {
                    if (railOnRight) it / 9 else -it / 9
                } else slideOutHorizontally(tween(0)) { 0 }) +
                (if (railPopup) slideOutVertically(tween(225)) { it / 14 }
                 else slideOutVertically(tween(0)) { 0 })
        ) {
        Glass(
            Modifier.width(popupWidth).shadow(
                if (isGlass) 15.dp else 0.dp, RoundedCornerShape(23.dp)
            ),
            RoundedCornerShape(topStart = 25.dp, topEnd = 20.dp,
                bottomStart = 25.dp, bottomEnd = 25.dp),
            tint = state.ambientColor, opacity = .83f,
            backdrop = song.artwork
        ) {
            Column(Modifier.fillMaxWidth()
                .heightIn(max = popupHeight)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(7.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(song.title, Modifier.weight(1f), color = white, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(35.dp)) {
                        Icon(Icons.Rounded.Close, "Tutup popup", Modifier.size(17.dp), tint = lime)
                    }
                }
                GlassPopupItem(if (state.isLiked(song)) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    if (state.isLiked(song)) "Hapus dari Disukai" else "Sukai lagu") {
                    state.like(song); onDismiss()
                }
                GlassPopupItem(Icons.Rounded.QueueMusic, "Tambahkan ke antrean") {
                    state.enqueue(song); onDismiss()
                }
                // Inside an Aurelis playlist, the matching playlist gets
                // a remove action instead of an ineffective "add again".
                if (localPlaylist != null &&
                    state.library.playlists[localPlaylist]?.any { it.key == song.key } == true) {
                    GlassPopupItem(Icons.Rounded.RemoveCircleOutline,
                        "Hapus dari playlist ini") {
                        state.removeFromPlaylist(localPlaylist, song)
                        onDismiss()
                    }
                }
                state.library.playlists.keys
                    .filterNot { it == localPlaylist }.take(4).forEach { name ->
                        GlassPopupItem(Icons.Rounded.PlaylistAdd, "Playlist · $name") {
                            state.addToPlaylist(name, song); onDismiss()
                        }
                    }
                if (lyricOffset) {
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = white.copy(alpha = .15f))
                    Text(uiText("OFFSET LIRIK"), Modifier.padding(start = 13.dp, bottom = 3.dp),
                        color = mist, fontSize = 10.sp, letterSpacing = 1.3.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = {
                            state.update(state.settings.copy(
                                lyricOffsetMs = (state.settings.lyricOffsetMs - 250).coerceAtLeast(-5000)))
                        }) {
                            Icon(Icons.Rounded.Remove, "Mundurkan lirik", tint = white)
                        }
                        Tactile(action = { state.update(state.settings.copy(lyricOffsetMs = 0)) }) {
                            Text("%+.2f s".format(java.util.Locale.US,
                                state.settings.lyricOffsetMs / 1000.0),
                                color = lime, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = {
                            state.update(state.settings.copy(
                                lyricOffsetMs = (state.settings.lyricOffsetMs + 250).coerceAtMost(5000)))
                        }) {
                            Icon(Icons.Rounded.Add, "Majukan lirik", tint = white)
                        }
                    }
                    Text(uiText("Tekan waktu untuk reset"), Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        color = mist.copy(alpha = .7f), fontSize = 10.sp,
                        textAlign = TextAlign.Center)
                }
            }
        }
        }
    }
}

@Composable
private fun Artwork(song: Song, size: Dp, radius: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(groove), contentAlignment = Alignment.Center) {
        if (song.artwork.startsWith("https://")) AsyncImage(model = song.artwork, contentDescription = "Sampul ${song.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("✦", color = lime.copy(alpha = .8f), fontSize = 28.sp)
    }
}

@Composable
private fun EmptyPage(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✦", color = lime.copy(alpha = .55f), fontSize = 32.sp)
        Spacer(Modifier.height(12.dp)); Text(title, color = white, fontWeight = FontWeight.Medium, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp)); Text(description, color = mist, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
    }
}

@Composable
private fun MiniPlayer(state: AurelisState, onExpand: () -> Unit) {
    val song = state.current ?: return
    Tactile(Modifier.fillMaxWidth(), onExpand) {
        Glass(Modifier.fillMaxWidth().height(72.dp), RoundedCornerShape(25.dp),
            tint = state.ambientColor.mix(Color(0xFF284037), .45f),
            opacity = .39f, backdrop = song.artwork,
            backdropBlur = 38.dp, backdropOpacity = .67f) {
            Column {
                Row(Modifier.weight(1f).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(song, 55.dp, 17.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(if (state.preparing) "Menyiapkan audio…" else song.artist, color = mist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                    }
                    IconButton(onClick = { state.togglePlay() }, enabled = !state.preparing) {
                        Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Putar atau jeda", tint = white, modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = state::next) { Icon(Icons.Default.SkipNext, "Lagu berikutnya", tint = white, modifier = Modifier.size(22.dp)) }
                }
                MiniPlayerProgress(state)
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(state: AurelisState) {
    val reduced = LocalReducedMotion.current
    val progressFraction by remember(state) {
        derivedStateOf {
            if (state.duration > 0L)
                (state.progress.toFloat() / state.duration).coerceIn(0f, 1f)
            else 0f
        }
    }
    val fluidFraction by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(if (reduced) 0 else 95, easing = LinearEasing),
        label = "miniPlayerFluidProgress"
    )
    Box(Modifier.fillMaxWidth().height(2.dp)
        .background(white.copy(alpha = .11f))) {
        Box(Modifier.fillMaxWidth(fluidFraction.coerceIn(0f, 1f))
            .fillMaxHeight().background(
                Brush.horizontalGradient(
                    listOf(lime.copy(alpha = .63f), lime)
                )
            ))
    }
}

/**
 * Landscape rail is deliberately separate from the search bar. The selected
 * capsule highlight follows Home/Search/Library vertically, while the main
 * page starts beyond its 79dp reserved left inset.
 */
@Composable
private fun LandscapeNavigationRail(
    tab: String,
    artwork: String,
    state: AurelisState,
    railHeight: Dp,
    onTab: (String) -> Unit
) {
    val tabs = listOf("Home", "Search", "Library")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search,
        Icons.Rounded.LibraryMusic)
    val reduced = LocalReducedMotion.current
    val isGlass = LocalVisualStyle.current == VisualStyle.GLASS
    var dragging by remember { mutableIntStateOf(-1) }
    Glass(
        Modifier.width(62.dp).height(railHeight),
        RoundedCornerShape(32.dp),
        tint = state.ambientColor, opacity = .43f, backdrop = artwork,
        backdropBlur = 35.dp, backdropOpacity = .58f
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(5.dp).pointerInput(tab) {
                detectDragGestures(
                    onDragStart = { p ->
                        dragging = ((p.y / size.height) * 3).toInt().coerceIn(0, 2)
                    },
                    onDrag = { change, _ ->
                        dragging = ((change.position.y / size.height) * 3)
                            .toInt().coerceIn(0, 2)
                        change.consume()
                    },
                    onDragEnd = {
                        val selected = dragging
                        dragging = -1
                        if (selected in 0..2) onTab(tabs[selected])
                    },
                    onDragCancel = { dragging = -1 }
                )
            }
        ) {
            val segment = maxHeight / 3
            val selected = if (dragging in 0..2) dragging
                else tabs.indexOf(tab).coerceAtLeast(0)
            val offset by animateDpAsState(
                targetValue = segment * selected,
                animationSpec = if (reduced) tween(0)
                else spring(dampingRatio = .83f,
                    stiffness = Spring.StiffnessMediumLow),
                label = "landscapeNavHighlight"
            )
            val highlight = if (isGlass) Modifier.background(
                Brush.verticalGradient(listOf(
                    lime.copy(alpha = .28f), Color(0xFF547C69).copy(alpha = .20f)
                ))
            ).border(1.dp, lime.copy(alpha = .19f),
                RoundedCornerShape(27.dp))
            else Modifier.background(Color(0xFF365D4D))
            Box(Modifier.offset(y = offset).fillMaxWidth()
                .height(segment).clip(RoundedCornerShape(27.dp))
                .then(highlight))
            Column(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, name ->
                    Tactile(Modifier.fillMaxWidth().weight(1f), {
                        dragging = -1
                        onTab(name)
                    }) {
                        Column(Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Icon(icons[index], name, Modifier.size(21.dp),
                                tint = if (selected == index) white else mist)
                            Spacer(Modifier.height(3.dp))
                            Text(uiText(name), fontSize = 8.sp, maxLines = 1,
                                color = if (selected == index) white else mist)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mirrored landscape search/mini-player dock.
 *
 * LEFT rail:  [Search][Mini Player] anchored at the top-left.
 * RIGHT rail: [Mini Player][Search] anchored at the top-right, with Search
 * centered immediately ABOVE the right navigation rail.
 *
 * Expanding Search grows toward the content on the opposite side:
 * to the right for the left rail, or to the left for the right rail. The mini
 * player smoothly shrinks to its album artwork in either direction.
 *
 * Side progress is animated independently from capsule widths, then the
 * horizontal anchor is derived from the CURRENT widths every frame. This
 * keeps the right edge pinned to the rail while Search morphs open and shut.
 */
@Composable
private fun LandscapeTopDock(
    modifier: Modifier = Modifier,
    state: AurelisState,
    query: String,
    searchExpanded: Boolean,
    onSearchClick: () -> Unit,
    onQuery: (String) -> Unit,
    onExpandPlayer: () -> Unit
) {
    val focus = LocalFocusManager.current
    val reduced = LocalReducedMotion.current
    val railRight = state.settings.landscapeRailRight
    BoxWithConstraints(modifier.fillMaxWidth().height(62.dp)) {
        val hasMini = state.current != null
        val searchTarget = if (searchExpanded)
            (maxWidth * .44f).coerceAtMost(292.dp) else 62.dp
        val miniTarget = if (searchExpanded) 62.dp
            else (maxWidth * .35f).coerceAtMost(250.dp)
        val capsuleAnimation = if (reduced) tween<Dp>(0)
            else spring<Dp>(dampingRatio = .91f,
                stiffness = Spring.StiffnessMediumLow)
        val searchWidth by animateDpAsState(searchTarget, capsuleAnimation,
            label = "landscapeSearchWidth")
        val miniWidth by animateDpAsState(miniTarget, capsuleAnimation,
            label = "landscapeMiniPlayerWidth")

        // Animate one normalized side, not a fixed pixel X position. The
        // changing capsule widths automatically shift the group so Search
        // remains right-aligned while it expands leftward.
        val sideProgress by animateFloatAsState(
            targetValue = if (railRight) 1f else 0f,
            animationSpec = if (reduced) tween(0)
                else spring(dampingRatio = .86f,
                    stiffness = Spring.StiffnessMediumLow),
            label = "landscapeTopDockSide"
        )
        val gap = if (hasMini) 9.dp else 0.dp
        val groupWidth = (searchWidth + if (hasMini) miniWidth + gap else 0.dp)
            .coerceIn(62.dp, maxWidth.coerceAtLeast(62.dp))
        val travel = (maxWidth - groupWidth).coerceAtLeast(0.dp)
        val dockX = travel * sideProgress.coerceIn(0f, 1f)

        val searchCapsule: @Composable () -> Unit = {
            Glass(Modifier.width(searchWidth).fillMaxHeight(),
                RoundedCornerShape(28.dp), tint = state.ambientColor,
                opacity = .39f, backdrop = state.current?.artwork,
                backdropBlur = 36.dp, backdropOpacity = .61f) {
                if (searchExpanded) {
                    Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = lime)
                        Spacer(Modifier.width(7.dp))
                        BasicTextField(value = query, onValueChange = onQuery,
                            modifier = Modifier.weight(1f), singleLine = true,
                            cursorBrush = SolidColor(lime),
                            textStyle = LocalTextStyle.current.copy(
                                color = white, fontSize = 13.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focus.clearFocus() }),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isBlank())
                                        Text(uiText("Lagu, artis, album…"),
                                            color = mist, fontSize = 12.sp)
                                    inner()
                                }
                            })
                        IconButton(onClick = {
                            if (query.isNotEmpty()) onQuery("")
                            else {
                                focus.clearFocus()
                                onSearchClick()
                            }
                        }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Close, "Tutup pencarian",
                                Modifier.size(18.dp), tint = mist)
                        }
                    }
                } else {
                    Tactile(Modifier.fillMaxSize(), onSearchClick) {
                        Icon(Icons.Rounded.Search, "Buka pencarian",
                            Modifier.size(23.dp).align(Alignment.Center),
                            tint = white)
                    }
                }
            }
        }

        val miniCapsule: @Composable () -> Unit = {
            state.current?.let { track ->
                Tactile(Modifier.width(miniWidth).fillMaxHeight(), onExpandPlayer) {
                    Glass(Modifier.fillMaxSize(), RoundedCornerShape(28.dp),
                        tint = state.ambientColor, opacity = .42f,
                        backdrop = track.artwork,
                        backdropBlur = 37.dp, backdropOpacity = .61f) {
                        Box(Modifier.fillMaxSize()) {
                            val coverX = if (searchExpanded)
                                ((miniWidth - 41.dp) / 2f).coerceAtLeast(0.dp)
                            else 6.dp
                            Box(Modifier.align(Alignment.CenterStart)
                                .offset(x = coverX)) {
                                Artwork(track, 41.dp, 14.dp)
                            }
                            if (!searchExpanded && miniWidth > 110.dp) {
                                Row(
                                    Modifier.align(Alignment.CenterStart)
                                        .fillMaxWidth()
                                        .padding(start = 56.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, color = white, fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = mist, fontSize = 9.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (miniWidth >= 185.dp) {
                                        IconButton(onClick = state::togglePlay,
                                            enabled = !state.preparing,
                                            modifier = Modifier.size(35.dp)) {
                                            Icon(if (state.isPlaying) Icons.Rounded.Pause
                                                else Icons.Rounded.PlayArrow,
                                                "Putar atau jeda", Modifier.size(21.dp),
                                                tint = lime)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = railRight,
            modifier = Modifier.offset(x = dockX)
                .width(groupWidth).fillMaxHeight(),
            label = "landscapeTopDockMirror",
            transitionSpec = {
                (fadeIn(tween(if (reduced) 0 else 260)) +
                    slideInHorizontally(tween(if (reduced) 0 else 320,
                        easing = FastOutSlowInEasing)) {
                        if (targetState) it / 9 else -it / 9
                    }) togetherWith
                (fadeOut(tween(if (reduced) 0 else 180)) +
                    slideOutHorizontally(tween(if (reduced) 0 else 220)) {
                        if (targetState) -it / 12 else it / 12
                    })
            }
        ) { onRight ->
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                if (onRight) {
                    miniCapsule()
                    searchCapsule()
                } else {
                    searchCapsule()
                    miniCapsule()
                }
            }
        }
    }
}

@Composable
private fun IslandDock(
    tab: String,
    expanded: Boolean,
    query: String,
    onTab: (String) -> Unit,
    onSearchExpand: () -> Unit,
    onQuery: (String) -> Unit,
    artwork: String = "",
    state: AurelisState,
    compact: Boolean,
    onOpenNav: () -> Unit,
    onExpandPlayer: () -> Unit
) {
    val focus = LocalFocusManager.current
    val reduced = LocalReducedMotion.current
    val isGlass = LocalVisualStyle.current == VisualStyle.GLASS
    val tabs = listOf("Home", "Search", "Library")
    val track = state.current
    val compactNow = compact && track != null
    var hovered by remember { mutableIntStateOf(-1) }
    val smooth = if (reduced) tween<Dp>(0)
        else spring<Dp>(dampingRatio = .89f, stiffness = Spring.StiffnessMediumLow)

    BoxWithConstraints(Modifier.fillMaxWidth().height(59.dp)) {
        val width = maxWidth
        val navTarget = if (expanded || compactNow) 56.dp else (width - 8.dp) * .79f
        val searchTarget = when {
            compactNow -> 56.dp
            expanded -> width - 64.dp
            else -> width - navTarget - 8.dp
        }
        val centerTarget = if (compactNow) width - 128.dp else 0.dp
        val navWidth by animateDpAsState(navTarget, smooth, label = "dockNavWidth")
        val searchWidth by animateDpAsState(searchTarget, smooth, label = "dockSearchWidth")
        val centerWidth by animateDpAsState(centerTarget, smooth, label = "dockMiniWidth")
        val miniAlpha by animateFloatAsState(
            targetValue = if (compactNow) 1f else 0f,
            animationSpec = tween(if (reduced) 0 else 230),
            label = "dockMiniOpacity"
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Glass(
                Modifier.width(navWidth).fillMaxHeight(),
                RoundedCornerShape(32.dp),
                tint = state.ambientColor,
                opacity = .38f, backdrop = artwork,
                backdropBlur = 38.dp, backdropOpacity = .67f
            ) {
                if (expanded || compactNow || navWidth < 165.dp) {
                    Tactile(Modifier.fillMaxSize(), {
                        focus.clearFocus()
                        if (compactNow) onOpenNav()
                        else if (expanded) onSearchExpand()
                        else onOpenNav()
                    }) {
                        Icon(Icons.Rounded.Menu, "Buka menu navigasi",
                            Modifier.size(22.dp).align(Alignment.Center)
                                .offset(y = 2.dp), tint = white)
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp).pointerInput(tab) {
                        detectDragGestures(
                            onDragStart = { point ->
                                hovered = ((point.x / size.width) * 3f)
                                    .toInt().coerceIn(0, 2)
                            },
                            onDrag = { change, _ ->
                                hovered = ((change.position.x / size.width) * 3f)
                                    .toInt().coerceIn(0, 2)
                                change.consume()
                            },
                            onDragEnd = {
                                val chosen = hovered
                                hovered = -1
                                if (chosen in 0..2) onTab(tabs[chosen])
                            },
                            onDragCancel = { hovered = -1 }
                        )
                    }) {
                        val segment = maxWidth / 3
                        val selected = if (hovered in 0..2) hovered
                            else tabs.indexOf(tab).coerceAtLeast(0)
                        val x by animateDpAsState(
                            segment * selected,
                            animationSpec = if (reduced) tween(0)
                                else spring(dampingRatio = .84f,
                                    stiffness = Spring.StiffnessMediumLow),
                            label = "dockSelection"
                        )
                        val highlight = if (isGlass) Modifier
                            .background(Brush.verticalGradient(listOf(
                                lime.copy(alpha = .28f),
                                Color(0xFF547C69).copy(alpha = .19f)
                            )))
                            .border(1.dp, lime.copy(alpha = .18f),
                                RoundedCornerShape(26.dp))
                        else Modifier.background(Color(0xFF365D4D))
                        Box(Modifier.offset(x = x).width(segment).fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp)).then(highlight))
                        Row(Modifier.fillMaxSize()) {
                            tabs.forEachIndexed { index, name ->
                                val icon = when (name) {
                                    "Home" -> Icons.Rounded.Home
                                    "Search" -> Icons.Rounded.Search
                                    else -> Icons.Rounded.LibraryMusic
                                }
                                Tactile(Modifier.weight(1f).fillMaxHeight(), {
                                    hovered = -1
                                    onTab(name)
                                }) {
                                    Column(Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center) {
                                        Icon(icon, name, Modifier.size(19.dp).offset(y = 2.dp),
                                            tint = if (selected == index) white else mist)
                                        Spacer(Modifier.height(3.dp))
                                        Text(uiText(name), fontSize = 10.sp,
                                            color = if (selected == index) white else mist)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (track != null) {
                Box(Modifier.width(centerWidth).fillMaxHeight()
                    .graphicsLayer { alpha = miniAlpha }) {
                    if (centerWidth > 22.dp) {
                        Glass(
                            Modifier.fillMaxSize(),
                            RoundedCornerShape(31.dp),
                            tint = state.ambientColor,
                            opacity = .39f,
                            backdrop = artwork,
                            backdropBlur = 38.dp,
                            backdropOpacity = .67f
                        ) {
                            Tactile(Modifier.fillMaxSize(), onExpandPlayer) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Artwork(track, 37.dp, 12.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = white, fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold)
                                        Text(track.artist, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = mist, fontSize = 9.sp)
                                    }
                                    IconButton(
                                        onClick = state::togglePlay,
                                        enabled = !state.preparing,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(if (state.isPlaying) Icons.Rounded.Pause
                                            else Icons.Rounded.PlayArrow, "Putar atau jeda",
                                            Modifier.size(21.dp), tint = lime)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Glass(
                Modifier.width(searchWidth).fillMaxHeight(),
                RoundedCornerShape(32.dp),
                tint = state.ambientColor,
                opacity = .38f,
                backdrop = artwork,
                backdropBlur = 38.dp,
                backdropOpacity = .67f
            ) {
                if (expanded && !compactNow) {
                    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = lime,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = query, onValueChange = onQuery,
                            modifier = Modifier.weight(1f),
                            cursorBrush = SolidColor(lime),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = white, fontSize = 14.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focus.clearFocus() }),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty())
                                        Text(uiText("Lagu, artis, album…"),
                                            color = mist, fontSize = 13.sp)
                                    inner()
                                }
                            }
                        )
                        IconButton(onClick = {
                            if (query.isNotEmpty()) onQuery("")
                            else { focus.clearFocus(); onSearchExpand() }
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, "Hapus atau tutup",
                                tint = mist, modifier = Modifier.size(19.dp))
                        }
                    }
                } else {
                    Tactile(Modifier.fillMaxSize(), onSearchExpand) {
                        Icon(Icons.Rounded.Search, "Cari lagu",
                            Modifier.size(22.dp).align(Alignment.Center),
                            tint = white)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullPlayer(state: AurelisState, onClose: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit, presentationScale: Float = 1f) {
    val song = state.current ?: return
    val stamp = state.libraryStamp
    var pane by rememberSaveable { mutableStateOf("Lirik") }
    var optionsOpen by remember { mutableStateOf(false) }
    val overflowTransition = remember {
        androidx.compose.animation.core.MutableTransitionState(false)
    }
    overflowTransition.targetState = optionsOpen
    var showTranslation by rememberSaveable { mutableStateOf(false) }
    var showRoman by rememberSaveable { mutableStateOf(state.settings.romanize) }
    LaunchedEffect(showTranslation, state.lyrics.lines.size,
        song.key, state.settings.language) {
        if (showTranslation && state.lyrics.lines.isNotEmpty()) state.loadTranslations()
    }
    val artwork = song.artwork.takeIf { it.startsWith("https://") }
    val video = song.videoId.ifBlank { song.id.removePrefix("yt-") }
    val hdEligible = video.matches(Regex("[A-Za-z0-9_-]{11}"))
    var useMaxres by remember(song.key) { mutableStateOf(hdEligible) }
    val coverUrl = if (useMaxres && hdEligible)
        "https://i.ytimg.com/vi/$video/maxresdefault.jpg" else artwork
    val animateMs = if (state.settings.motion) 360 else 0

    val isLandscapePlayer = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    if (isLandscapePlayer) {
        LandscapeFullPlayer(
            state = state, song = song, onClose = onClose,
            selected = if (pane == "Up Next") "Up Next" else "Lirik",
            onSelect = { pane = it },
            showRoman = showRoman,
            onRomanToggle = {
                showRoman = !showRoman
                if (showRoman && !state.settings.romanize)
                    state.update(state.settings.copy(romanize = true))
            },
            showTranslation = showTranslation,
            onTranslationToggle = {
                showTranslation = !showTranslation
                if (showTranslation) state.loadTranslations()
            },
            onQueue = onQueue,
            presentationScale = presentationScale
        )
        return
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        Ambient(state)
        // Full-bleed cover: clear center, soft/vignetted upper and lower edges.
        if (artwork != null) {
            if (pane == "Cover") {
                // A SINGLE full-bleed cover, with no fit-size rectangle or seams.
                // Keep texture at the top and fade only where text/controls need contrast.
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    onError = { if (useMaxres) useMaxres = false }
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colorStops = arrayOf(
                        0f to bg.copy(alpha = .59f),
                        .11f to bg.copy(alpha = .28f),
                        .30f to bg.copy(alpha = .03f),
                        .52f to bg.copy(alpha = .02f),
                        .72f to bg.copy(alpha = .34f),
                        .87f to bg.copy(alpha = .75f),
                        1f to bg.copy(alpha = .94f)
                    ))
                ))
            } else {
                AsyncImage(model = artwork, contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(78.dp).graphicsLayer { alpha = .38f },
                    contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(
                        bg.copy(alpha = .49f), bg.copy(alpha = .52f),
                        bg.copy(alpha = .69f), bg.copy(alpha = .96f)
                    ))
                ))
            }
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .graphicsLayer {
                    scaleX = presentationScale
                    scaleY = presentationScale
                    transformOrigin = TransformOrigin(.5f, .5f)
                }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleAction(Icons.Rounded.KeyboardArrowDown, "Tutup pemutar", onClose)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiText("NOW PLAYING"), color = white.copy(alpha = .54f),
                        fontSize = 9.sp, letterSpacing = 2.2.sp)
                    Text("Aurelis Symphony", color = white, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    if (!overflowTransition.currentState && !overflowTransition.targetState)
                        OverflowMorphButton(false) { optionsOpen = true }
                    else Spacer(Modifier.size(40.dp))
                    InlineGlassSongMenu(state, song, optionsOpen,
                        onDismiss = { optionsOpen = false },
                        lyricOffset = true, transitionState = overflowTransition)
                }
            }

            AnimatedContent(
                targetState = pane,
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = "fullscreenPlayerMode",
                transitionSpec = {
                    (fadeIn(tween(animateMs)) +
                        slideInHorizontally(tween(animateMs)) { it / 7 }) togetherWith
                        (fadeOut(tween(animateMs * 2 / 3)) +
                            slideOutHorizontally(tween(animateMs * 2 / 3)) { -it / 9 })
                }
            ) { mode ->
                when (mode) {
                    "Cover" -> {
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(start = 27.dp, end = 27.dp, bottom = 34.dp)
                            ) {
                                Text("AURELIS  ·  NOW PLAYING", color = lime.copy(alpha = .82f),
                                    fontSize = 10.sp, letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(10.dp))
                                Text(song.title, color = white, fontSize = 29.sp,
                                    lineHeight = 33.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(7.dp))
                                Text(song.artist, color = white.copy(alpha = .74f), fontSize = 15.sp)
                                Spacer(Modifier.height(10.dp))
                                IconButton(onClick = { state.like(song) }) {
                                    Icon(if (state.isLiked(song)) Icons.Rounded.Favorite
                                        else Icons.Rounded.FavoriteBorder,
                                        "Sukai lagu", Modifier.size(23.dp),
                                        tint = if (state.isLiked(song)) lime else white)
                                }
                            }
                        }
                    }
                    "Lirik" -> {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Artwork(song, 49.dp, 13.dp)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, color = white, fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(3.dp))
                                    Text(song.artist, color = mist, fontSize = 11.sp, maxLines = 1)
                                }
                                IconButton(onClick = {
                                    showRoman = !showRoman
                                    if (showRoman && !state.settings.romanize)
                                        state.update(state.settings.copy(romanize = true))
                                }, modifier = Modifier.size(40.dp)) {
                                    Glass(Modifier.fillMaxSize(), RoundedCornerShape(20.dp),
                                        opacity = if (showRoman) .78f else .37f,
                                        tint = if (showRoman) state.ambientColor else groove,
                                        backdrop = artwork) {
                                        Icon(Icons.Rounded.Language, "Romanisasi",
                                            Modifier.size(18.dp).align(Alignment.Center),
                                            tint = if (showRoman) lime else mist)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                IconButton(onClick = {
                                    showTranslation = !showTranslation
                                    if (showTranslation) state.loadTranslations()
                                }, modifier = Modifier.size(40.dp)) {
                                    Glass(Modifier.fillMaxSize(), RoundedCornerShape(20.dp),
                                        opacity = if (showTranslation) .78f else .37f,
                                        tint = if (showTranslation) state.ambientColor else groove,
                                        backdrop = artwork) {
                                        if (showTranslation && state.translating)
                                            CircularProgressIndicator(
                                                Modifier.size(18.dp).align(Alignment.Center),
                                                strokeWidth = 1.8.dp, color = lime)
                                        else Icon(Icons.Rounded.Translate, "Terjemahan",
                                            Modifier.size(18.dp).align(Alignment.Center),
                                            tint = if (showTranslation) lime else mist)
                                    }
                                }
                            }
                            if (showTranslation && (state.translating || state.translationError != null)) {
                                Text(
                                    when {
                                        state.translating -> "Menerjemahkan lirik ke Bahasa Indonesia…"
                                        else -> state.translationError.orEmpty()
                                    },
                                    Modifier.padding(horizontal = 28.dp, vertical = 2.dp),
                                    color = if (state.translationError != null) Color(0xFFFFC5C5) else mist,
                                    fontSize = 11.sp
                                )
                            }
                            // There is deliberately NO separate glass lyric panel.
                            LyricContent(state, Modifier.fillMaxWidth().weight(1f),
                                showRoman = showRoman, showTranslation = showTranslation)
                        }
                    }
                    else -> {
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                            Spacer(Modifier.height(13.dp))
                            Text(uiText("Up Next"), color = white, fontSize = 27.sp,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.activePlaylist?.let { "Playlist · $it" }
                                    ?: "Antrean rekomendasi musik selanjutnya",
                                color = mist, fontSize = 12.sp
                            )
                            Spacer(Modifier.height(17.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(song, 49.dp, 12.dp)
                                Spacer(Modifier.width(11.dp))
                                Column {
                                    Text(uiText("Sedang diputar"), color = lime, fontSize = 10.sp)
                                    Text(song.title, color = white, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.height(19.dp))
                            if (state.queue.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Belum ada lagu dalam antrean.\nTambahkan lagu melalui menu titik tiga.",
                                        color = mist, fontSize = 13.sp,
                                        lineHeight = 20.sp, textAlign = TextAlign.Center)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                        .featherScrollEdges(top = 19.dp, bottom = 35.dp),
                                    contentPadding = PaddingValues(top = 12.dp, bottom = 22.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.queue.size,
                                        key = { i -> "player-queue-$i-${state.queue[i].key}" }
                                    ) { i ->
                                        val queued = state.queue[i]
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.weight(1f)) {
                                                SongRow(queued, state,
                                                    onPlay = { state.playFromQueue(queued, i) },
                                                    onMenu = { })
                                            }
                                            IconButton(onClick = { state.removeQueue(queued) }) {
                                                Icon(Icons.Rounded.Close, "Hapus dari antrean",
                                                    tint = mist, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PlayerControls(state)
            Spacer(Modifier.height(11.dp))
            PlayerModeIsland(pane, { pane = it }, artwork.orEmpty(), state.settings.motion)
            Spacer(Modifier.height(15.dp))
        }
    }
}

/**
 * The landscape player always shows artwork + playback on the left.
 * The right panel is independently navigable between lyrics and Up Next.
 * There is intentionally NO bottom Cover/Lirik/Up Next horizontal island.
 */
@Composable
private fun LandscapeFullPlayer(
    state: AurelisState,
    song: Song,
    onClose: () -> Unit,
    selected: String,
    onSelect: (String) -> Unit,
    showRoman: Boolean,
    onRomanToggle: () -> Unit,
    showTranslation: Boolean,
    onTranslationToggle: () -> Unit,
    onQueue: () -> Unit,
    presentationScale: Float = 1f
) {
    val reduced = LocalReducedMotion.current
    val artwork = song.artwork.takeIf { it.startsWith("https://") }
    var landscapeMenuExpanded by remember { mutableStateOf(false) }
    val menuTransition = remember {
        androidx.compose.animation.core.MutableTransitionState(false)
    }
    menuTransition.targetState = landscapeMenuExpanded
    LaunchedEffect(state.settings.landscapeRailRight) {
        landscapeMenuExpanded = false
    }

    // Keep every rail action in one composable so moving it between the
    // two edges never duplicates controls, menus, or navigation state.
    val playerRail: @Composable () -> Unit = {
            Column(
                Modifier.width(62.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Tactile(action = onClose) {
                    Glass(
                        Modifier.size(62.dp),
                        RoundedCornerShape(31.dp),
                        tint = state.ambientColor, opacity = .45f,
                        backdrop = artwork
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Tutup pemutar",
                            Modifier.size(24.dp).align(Alignment.Center),
                            tint = white)
                    }
                }
                Spacer(Modifier.height(10.dp))
                LandscapePlayerModeRail(
                    selected = selected,
                    onSelect = onSelect,
                    artwork = artwork,
                    state = state
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(62.dp)) {
                    Tactile(Modifier.fillMaxSize(), {
                        landscapeMenuExpanded = !landscapeMenuExpanded
                    }) {
                        Glass(Modifier.fillMaxSize(), RoundedCornerShape(31.dp),
                            tint = state.ambientColor, opacity = .43f,
                            backdrop = artwork) {
                            Icon(
                                if (landscapeMenuExpanded) Icons.Rounded.Close
                                    else Icons.Rounded.MoreHoriz,
                                "Opsi lagu", Modifier.size(24.dp)
                                    .align(Alignment.Center), tint = white
                            )
                        }
                    }
                    InlineGlassSongMenu(
                        state = state, song = song,
                        expanded = landscapeMenuExpanded,
                        onDismiss = { landscapeMenuExpanded = false },
                        lyricOffset = true,
                        transitionState = menuTransition,
                        preferRight = !state.settings.landscapeRailRight,
                        railOnRight = state.settings.landscapeRailRight
                    )
                }
            }

    }
    Box(Modifier.fillMaxSize().background(bg)) {
        Ambient(state)
        AnimatedContent(
            targetState = state.settings.landscapeRailRight,
            label = "landscapeControlRailSwap",
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val toRight = targetState
                (fadeIn(tween(if (reduced) 0 else 260)) +
                    slideInHorizontally(
                        tween(if (reduced) 0 else 340,
                            easing = FastOutSlowInEasing)
                    ) { width -> if (toRight) width / 12 else -width / 12 }
                ) togetherWith
                (fadeOut(tween(if (reduced) 0 else 200)) +
                    slideOutHorizontally(
                        tween(if (reduced) 0 else 255,
                            easing = FastOutSlowInEasing)
                    ) { width -> if (toRight) -width / 15 else width / 15 })
            }
        ) { railRight ->
        Row(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(start = 12.dp, end = 15.dp, top = 8.dp, bottom = 10.dp)
                .graphicsLayer {
                    scaleX = presentationScale
                    scaleY = presentationScale
                    transformOrigin = TransformOrigin(.5f, .5f)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!railRight) {
                playerRail()
                Spacer(Modifier.width(13.dp))
            }

            // Cover column. Controls remain here while the right panel changes.
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    if (artwork != null) {
                        AsyncImage(
                            model = artwork, contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(state.ambientColor))
                    }
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to bg.copy(alpha = .04f),
                                .32f to bg.copy(alpha = .06f),
                                .67f to bg.copy(alpha = .45f),
                                1f to bg.copy(alpha = .89f)
                            )
                        )
                    ))
                    Column(
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(song.title, color = white, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 2,
                            lineHeight = 19.sp, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(song.artist, color = white.copy(alpha = .74f),
                            fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Seek/duration, previous, play and next sit below the artwork.
                PlayerControls(state, compact = true)
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1.12f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(uiText(selected), color = white,
                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    if (selected == "Lirik") {
                        IconButton(onClick = onRomanToggle,
                            modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Language, "Romanisasi",
                                Modifier.size(19.dp),
                                tint = if (showRoman) lime else mist)
                        }
                        IconButton(onClick = onTranslationToggle,
                            modifier = Modifier.size(36.dp)) {
                            if (showTranslation && state.translating) {
                                CircularProgressIndicator(
                                    Modifier.size(17.dp),
                                    strokeWidth = 2.dp, color = lime)
                            } else {
                                Icon(Icons.Rounded.Translate, "Terjemahan",
                                    Modifier.size(19.dp),
                                    tint = if (showTranslation) lime else mist)
                            }
                        }
                    }
                }
                AnimatedContent(
                    targetState = selected,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = "landscapePlayerRightPanel",
                    transitionSpec = {
                        (fadeIn(tween(if (reduced) 0 else 260)) +
                            slideInHorizontally(tween(if (reduced) 0 else 320)) { it / 9 }
                        ) togetherWith
                        (fadeOut(tween(if (reduced) 0 else 170)) +
                            slideOutHorizontally(tween(if (reduced) 0 else 210)) { -it / 10 })
                    }
                ) { mode ->
                    if (mode == "Lirik") {
                        Column(Modifier.fillMaxSize()) {
                            if (showTranslation &&
                                (state.translating || state.translationError != null)) {
                                Text(
                                    if (state.translating)
                                        "Menerjemahkan ke ${state.settings.language.displayName}…"
                                    else state.translationError.orEmpty(),
                                    Modifier.fillMaxWidth().padding(
                                        horizontal = 11.dp, vertical = 3.dp),
                                    color = if (state.translationError != null)
                                        Color(0xFFFFC5C5) else mist,
                                    fontSize = 10.sp
                                )
                            }
                            LyricContent(
                                state, Modifier.fillMaxWidth().weight(1f),
                                showRoman = showRoman,
                                showTranslation = showTranslation,
                                compact = true
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                state.activePlaylist?.let { "Playlist · $it" }
                                    ?: "Rekomendasi musik selanjutnya",
                                color = mist, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            if (state.queue.isEmpty()) {
                                Box(Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center) {
                                    Text("Belum ada lagu dalam antrean.",
                                        color = mist, fontSize = 12.sp,
                                        textAlign = TextAlign.Center)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                        .featherScrollEdges(top = 19.dp, bottom = 28.dp),
                                    contentPadding = PaddingValues(top = 12.dp, bottom = 21.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    items(state.queue.size, key = { i ->
                                        "landscape-queue-$i-${state.queue[i].key}"
                                    }) { index ->
                                        val queued = state.queue[index]
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Tactile(Modifier.weight(1f), {
                                                state.playFromQueue(queued, index)
                                            }) {
                                                Row(
                                                    Modifier.fillMaxWidth()
                                                        .padding(vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Artwork(queued, 44.dp, 12.dp)
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(queued.title, color = white,
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis)
                                                        Text(queued.artist, color = mist,
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = { state.removeQueue(queued) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.Close,
                                                    "Hapus dari antrean",
                                                    Modifier.size(17.dp), tint = mist)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (railRight) {
                Spacer(Modifier.width(13.dp))
                playerRail()
            }
        }
        }
    }
}

/**
 * One continuous two-cell capsule instead of independent circular buttons.
 * The accent follows the selected lyrics/queue tab with a vertical spring.
 */
@Composable
private fun LandscapePlayerModeRail(
    selected: String,
    onSelect: (String) -> Unit,
    artwork: String?,
    state: AurelisState
) {
    val reduced = LocalReducedMotion.current
    val isGlass = LocalVisualStyle.current == VisualStyle.GLASS
    val choices = listOf("Lirik", "Up Next")
    val icons = listOf(Icons.Rounded.Mic, Icons.Rounded.QueueMusic)
    var hovering by remember { mutableIntStateOf(-1) }
    Glass(
        Modifier.width(62.dp).height(132.dp),
        RoundedCornerShape(31.dp),
        tint = state.ambientColor, opacity = .43f,
        backdrop = artwork, backdropBlur = 35.dp,
        backdropOpacity = .59f
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(5.dp).pointerInput(selected) {
                detectDragGestures(
                    onDragStart = { point ->
                        hovering = ((point.y / size.height) * 2f)
                            .toInt().coerceIn(0, 1)
                    },
                    onDrag = { change, _ ->
                        hovering = ((change.position.y / size.height) * 2f)
                            .toInt().coerceIn(0, 1)
                        change.consume()
                    },
                    onDragEnd = {
                        if (hovering in 0..1) onSelect(choices[hovering])
                        hovering = -1
                    },
                    onDragCancel = { hovering = -1 }
                )
            }
        ) {
            val segment = maxHeight / 2
            val active = if (hovering in 0..1) hovering
                else choices.indexOf(selected).coerceIn(0, 1)
            val highlightY by animateDpAsState(
                segment * active,
                animationSpec = if (reduced) tween(0)
                    else spring(dampingRatio = .83f,
                        stiffness = Spring.StiffnessMediumLow),
                label = "landscapePlayerModeHighlight"
            )
            val highlightStyle = if (isGlass) Modifier
                .background(Brush.verticalGradient(listOf(
                    lime.copy(alpha = .32f),
                    Color(0xFF477A69).copy(alpha = .23f)
                )))
                .border(1.dp, lime.copy(alpha = .19f),
                    RoundedCornerShape(27.dp))
            else Modifier.background(Color(0xFF365D4D))
            Box(
                Modifier.offset(y = highlightY).fillMaxWidth()
                    .height(segment).clip(RoundedCornerShape(27.dp))
                    .then(highlightStyle)
            )
            Column(Modifier.fillMaxSize()) {
                choices.forEachIndexed { index, mode ->
                    Tactile(
                        Modifier.fillMaxWidth().weight(1f),
                        { hovering = -1; onSelect(mode) }
                    ) {
                        Icon(
                            icons[index], mode,
                            Modifier.size(23.dp).align(Alignment.Center),
                            tint = if (active == index) white else mist
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerModeIsland(selected: String, onSelect: (String) -> Unit,
    artwork: String, enabledMotion: Boolean) {
    val modes = listOf("Cover", "Lirik", "Up Next")
    val icons = listOf(Icons.Rounded.Album, Icons.Rounded.Mic, Icons.Rounded.QueueMusic)
    val isGlass = LocalVisualStyle.current == VisualStyle.GLASS
    var dragging by remember { mutableIntStateOf(-1) }
    Glass(
        Modifier.fillMaxWidth().padding(horizontal = 31.dp).height(57.dp),
        RoundedCornerShape(30.dp), opacity = .62f, backdrop = artwork
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp).pointerInput(selected) {
            detectDragGestures(
                onDragStart = { p -> dragging = ((p.x / size.width) * 3f).toInt().coerceIn(0, 2) },
                onDrag = { change, _ ->
                    dragging = ((change.position.x / size.width) * 3f).toInt().coerceIn(0, 2)
                    change.consume()
                },
                onDragEnd = {
                    val target = dragging
                    dragging = -1
                    if (target in 0..2) onSelect(modes[target])
                },
                onDragCancel = { dragging = -1 }
            )
        }) {
            val cellWidth = maxWidth / 3
            val highlight = if (dragging >= 0) dragging else modes.indexOf(selected).coerceAtLeast(0)
            val x by animateDpAsState(
                targetValue = cellWidth * highlight,
                animationSpec = if (!enabledMotion) tween(0) else
                    spring(dampingRatio = .78f, stiffness = Spring.StiffnessMediumLow),
                label = "playerTabHighlight"
            )
            val highlightStyle = if (isGlass) Modifier
                .background(Brush.verticalGradient(listOf(
                    lime.copy(alpha = .30f), Color(0xFF467A67).copy(alpha = .25f)
                )))
                .border(1.dp, lime.copy(alpha = .20f), RoundedCornerShape(26.dp))
            else Modifier.background(Color(0xFF365D4D))
            Box(Modifier.offset(x = x).width(cellWidth).fillMaxHeight()
                .clip(RoundedCornerShape(26.dp)).then(highlightStyle))
            Row(Modifier.fillMaxSize()) {
                modes.forEachIndexed { i, label ->
                    Tactile(Modifier.weight(1f).fillMaxHeight(), { dragging = -1; onSelect(label) }) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(icons[i], label, Modifier.size(18.dp),
                                tint = if (i == highlight) white else mist)
                            Spacer(Modifier.width(5.dp))
                            Text(uiText(label), fontSize = 11.sp,
                                fontWeight = if (i == highlight) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (i == highlight) white else mist)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricContent(
    state: AurelisState, modifier: Modifier = Modifier,
    showRoman: Boolean = true, showTranslation: Boolean = false,
    compact: Boolean = false
) {
    val currentSec = (state.progress + state.settings.lyricOffsetMs) / 1000.0
    val lines = state.lyrics.lines
    val active = lines.indexOfLast { it.time != null && it.time <= currentSec }
    val scroll = rememberLazyListState()

    LaunchedEffect(active, state.current?.key) {
        if (active >= 0 && lines.isNotEmpty()) {
            val target = (active - 1).coerceAtLeast(0)
            if (state.settings.motion) scroll.animateScrollToItem(target)
            else scroll.scrollToItem(target)
        }
    }

    if (lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp)) {
                Icon(Icons.Rounded.GraphicEq, null, tint = lime.copy(alpha = .7f),
                    modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(13.dp))
                Text(uiText("Lirik belum tersedia"), color = white,
                    fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(uiText("Lirik akan mengikuti musik jika sumber menyediakan waktu sinkronisasi."),
                    color = mist, fontSize = 12.sp, textAlign = TextAlign.Center,
                    lineHeight = 18.sp)
            }
        }
        return
    }

    LazyColumn(
        state = scroll,
        modifier = modifier.featherScrollEdges(
            top = if (compact) 24.dp else 39.dp,
            bottom = if (compact) 28.dp else 43.dp
        ),
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 23.dp,
            vertical = if (compact) 13.dp else 39.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 11.dp else 22.dp)
    ) {
        items(lines.size, key = { "lyric-$it" }) { i ->
            val line = lines[i]
            val focused = active < 0 || i == active
            val nearby = active < 0 || i in (active - 1)..(active + 1)
            val targetAlpha = if (focused) 1f else if (nearby) .46f else .24f
            val opacity by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(if (state.settings.motion) 360 else 0),
                label = "lyricFocus"
            )
            Column(
                Modifier.fillMaxWidth()
                    .graphicsLayer { alpha = opacity }
                    .clickable(enabled = line.time != null) {
                        line.time?.let {
                            val seekTime = (it * 1000 - state.settings.lyricOffsetMs).coerceAtLeast(0.0)
                            state.seek((seekTime / state.duration.coerceAtLeast(1)).toFloat())
                        }
                    }
                    .padding(start = 4.dp, end = 9.dp, top = 5.dp, bottom = 5.dp)
            ) {
                if (focused && active >= 0) {
                    Box(Modifier.width(27.dp).height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(lime, lime.copy(alpha = 0f)))))
                    Spacer(Modifier.height(9.dp))
                }

                if (focused && line.words.isNotEmpty() && active >= 0) {
                    // Continuous fractional highlight per *real* word timestamp.
                    // Plain LRC lines are never given fabricated karaoke timing.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        line.words.forEachIndexed { idx, word ->
                            val previous = line.words.getOrNull(idx - 1)?.text.orEmpty()
                            val last = previous.lastOrNull()
                            val first = word.text.firstOrNull()
                            val separated = last != null && first != null &&
                                last.isLetterOrDigit() && first.isLetterOrDigit() &&
                                last.code < 128 && first.code < 128 &&
                                !previous.endsWith(" ") && !word.text.startsWith(" ")
                            val rendered = (if (separated) " " else "") + word.text
                            val fraction = if (word.end > word.start)
                                ((currentSec - word.start) / (word.end - word.start))
                                    .coerceIn(0.0, 1.0).toFloat()
                            else if (currentSec >= word.start) 1f else 0f
                            KaraokeToken(rendered, fraction)
                        }
                    }
                } else {
                    Text(
                        text = line.text, color = if (focused) white else mist,
                        fontSize = if (compact) 18.sp else 23.sp,
                        lineHeight = if (compact) 25.sp else 34.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = if (focused) Shadow(lime.copy(alpha = .21f),
                                Offset.Zero, 8f) else null
                        )
                    )
                }
                if (showRoman) {
                    state.romanized[i]?.takeIf { it != line.text }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = if (focused) lime.copy(alpha = .80f) else mist,
                            fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                val translatedLine = state.translated[i].orEmpty().ifBlank {
                    if (state.settings.language == AppLanguage.INDONESIAN)
                        line.translation else ""
                }
                if (showTranslation && translatedLine.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(translatedLine, color = if (focused) white.copy(alpha = .77f) else mist,
                        fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun KaraokeToken(word: String, target: Float) {
    val reduced = LocalReducedMotion.current
    val progress by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = tween(if (reduced) 0 else 110, easing = LinearEasing),
        label = "singleLayerKaraokeFill"
    )
    // One glyph raster / one Text. No doubled overlay, misplaced baselines, or ghost letters.
    val edge = (progress + .045f).coerceIn(0f, 1f)
    val stops = when {
        progress <= .001f -> arrayOf(0f to mist.copy(alpha = .7f), 1f to mist.copy(alpha = .7f))
        progress >= .999f -> arrayOf(0f to lime, 1f to lime)
        else -> arrayOf(
            0f to lime, progress to lime,
            edge to white.copy(alpha = .91f),
            (edge + .001f).coerceAtMost(1f) to mist.copy(alpha = .71f),
            1f to mist.copy(alpha = .71f)
        )
    }
    Text(
        text = word,
        fontSize = 23.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        style = androidx.compose.ui.text.TextStyle(
            brush = Brush.horizontalGradient(colorStops = stops),
            shadow = if (progress > .01f) Shadow(
                lime.copy(alpha = .12f + .24f * progress), Offset.Zero, 9f
            ) else null
        )
    )
}

@Composable
private fun PlayerControls(state: AurelisState, compact: Boolean = false) {
    val fraction = if (state.duration > 0)
        (state.progress.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
    var scrubbing by remember { mutableFloatStateOf(-1f) }
    val intended = if (scrubbing >= 0f) scrubbing else fraction
    val smoothed by animateFloatAsState(
        targetValue = intended,
        animationSpec = tween(if (state.settings.motion) 135 else 0, easing = LinearEasing),
        label = "glowLineProgress"
    )
    val song = state.current
    Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) 7.dp else 24.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(if (compact) 27.dp else 38.dp)
                .pointerInput(state.duration) {
                    detectTapGestures { location ->
                        if (size.width > 0) state.seek(
                            (location.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(state.duration) {
                    detectDragGestures(
                        onDragStart = { p ->
                            if (size.width > 0) scrubbing =
                                (p.x / size.width).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            if (size.width > 0) scrubbing =
                                (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd = {
                            if (scrubbing >= 0) state.seek(scrubbing)
                            scrubbing = -1f
                        },
                        onDragCancel = { scrubbing = -1f }
                    )
                }
        ) {
            val y = size.height * .5f
            val filled = size.width * smoothed
            drawLine(
                color = white.copy(alpha = .24f),
                start = Offset(0f, y), end = Offset(size.width, y),
                strokeWidth = 1.3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            if (filled > 0f) {
                val start = Offset(0f, y)
                val end = Offset(filled, y)
                val bloom = Brush.horizontalGradient(
                    listOf(lime.copy(alpha = .61f), white.copy(alpha = .94f)),
                    endX = max(filled, 1f)
                )
                // Subtle emissive progress line: intentionally NO knob/circles.
                drawLine(brush = bloom, start = start, end = end,
                    strokeWidth = 5.5.dp.toPx(), alpha = .09f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(brush = bloom, start = start, end = end,
                    strokeWidth = 1.9.dp.toPx(), alpha = .91f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(state.progress), color = white.copy(alpha = .84f), fontSize = 11.sp)
            Text(formatMs(state.duration), color = mist, fontSize = 11.sp)
        }
        Spacer(Modifier.height(if (compact) 1.dp else 13.dp))
        Row(Modifier.fillMaxWidth().height(if (compact) 49.dp else 70.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            Tactile(action = state::previous) {
                Glass(Modifier.size(if (compact) 38.dp else 49.dp), RoundedCornerShape(25.dp),
                    tint = state.ambientColor, opacity = .54f,
                    backdrop = song?.artwork) {
                    Icon(Icons.Rounded.SkipPrevious, "Sebelumnya",
                        Modifier.size(if (compact) 20.dp else 24.dp).align(Alignment.Center), tint = white)
                }
            }
            Tactile(action = state::togglePlay) {
                Glass(Modifier.size(if (compact) 50.dp else 69.dp), RoundedCornerShape(35.dp),
                    tint = state.ambientColor, opacity = .85f,
                    backdrop = song?.artwork) {
                    if (state.preparing) {
                        CircularProgressIndicator(Modifier.size(26.dp).align(Alignment.Center),
                            color = lime, strokeWidth = 2.dp)
                    } else {
                        AnimatedContent(state.isPlaying, label = "playPauseMorph",
                            modifier = Modifier.align(Alignment.Center), transitionSpec = {
                                (fadeIn(tween(160)) + scaleIn(initialScale = .66f)) togetherWith
                                    (fadeOut(tween(120)) + scaleOut(targetScale = .7f))
                            }) { playing ->
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                "Putar atau jeda", Modifier.size(if (compact) 26.dp else 33.dp), tint = lime)
                        }
                    }
                }
            }
            Tactile(action = state::next) {
                Glass(Modifier.size(if (compact) 38.dp else 49.dp), RoundedCornerShape(25.dp),
                    tint = state.ambientColor, opacity = .54f,
                    backdrop = song?.artwork) {
                    Icon(Icons.Rounded.SkipNext, "Berikutnya",
                        Modifier.size(if (compact) 20.dp else 24.dp).align(Alignment.Center), tint = white)
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1000).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    state: AurelisState,
    updater: AurelisUpdater,
    onClose: () -> Unit,
    onConnectYouTube: () -> Unit
) {
    val song = state.current
    val s = state.settings
    val landscapeSettings = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    val closeSheet: () -> Unit = {
        if (landscapeSettings) onClose()
        else sheetScope.launch {
            sheetState.hide()
            onClose()
        }
        Unit
    }
    // Shared settings controls; only their container differs per orientation.
    val sheetContent: @Composable () -> Unit = {
        Glass(
            Modifier.fillMaxSize(),
            if (landscapeSettings) RoundedCornerShape(27.dp)
                else RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            tint = state.ambientColor,
            opacity = .83f,
            backdrop = song?.artwork
        ) {
            val settingsScroll = androidx.compose.foundation.rememberScrollState()
            Column(Modifier.fillMaxSize()
                .padding(top = if (landscapeSettings) 4.dp else 38.dp)
                .verticalScroll(settingsScroll)
                .padding(start = if (landscapeSettings) 27.dp else 23.dp,
                    end = if (landscapeSettings) 27.dp else 23.dp,
                    bottom = if (landscapeSettings) 16.dp else 42.dp)) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("PREFERENSI"), fontSize = 9.sp, letterSpacing = 2.sp,
                            color = lime.copy(alpha = .80f))
                        Spacer(Modifier.height(6.dp))
                        Text(uiText("Pengaturan"), color = white,
                            fontSize = if (landscapeSettings) 23.sp else 29.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-.8).sp)
                    }
                    Tactile(action = closeSheet) {
                        Glass(Modifier.size(42.dp), RoundedCornerShape(21.dp), opacity = .60f) {
                            Icon(Icons.Rounded.Close, "Tutup pengaturan",
                                Modifier.align(Alignment.Center).size(20.dp), tint = white)
                        }
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text(uiText("Ruang untuk musik, cahaya, dan gerakan."), color = mist, fontSize = 12.sp)
                Spacer(Modifier.height(22.dp))

                if (!landscapeSettings) {
                Glass(Modifier.fillMaxWidth().height(94.dp), RoundedCornerShape(23.dp),
                    opacity = .46f, backdrop = song?.artwork, tint = state.ambientColor) {
                    Row(Modifier.fillMaxSize().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(55.dp).clip(RoundedCornerShape(17.dp))
                            .background(Brush.radialGradient(listOf(
                                state.ambientColor, Color(0xFF162B2A)
                            ))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null,
                                Modifier.size(23.dp), tint = lime)
                        }
                        Spacer(Modifier.width(15.dp))
                        Column {
                            Text(uiText("Glass Ambient"), color = white,
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text(uiText("Warna dan cahaya mengikuti artwork"),
                                color = mist, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }

                }
                Spacer(Modifier.height(if (landscapeSettings) 9.dp else 24.dp))
                SettingsCategory(uiText("SUASANA"), uiText("Tampilan"))
                Text(uiText("Gaya visual"), color = white, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        VisualStyle.GLASS to "Glass Ambient",
                        VisualStyle.AMBIENT to "Ambient"
                    ).forEach { (style, title) ->
                        Tactile(Modifier.weight(1f), {
                            state.update(s.copy(visualStyle = style))
                        }) {
                            Glass(
                                Modifier.fillMaxWidth().height(48.dp),
                                RoundedCornerShape(21.dp),
                                tint = if (s.visualStyle == style) state.ambientColor else groove,
                                opacity = if (s.visualStyle == style) .8f else .42f,
                                backdrop = if (style == VisualStyle.GLASS) song?.artwork else null
                            ) {
                                Text(title, Modifier.align(Alignment.Center),
                                    fontSize = 12.sp,
                                    color = if (s.visualStyle == style) lime else mist,
                                    fontWeight = if (s.visualStyle == style)
                                        FontWeight.SemiBold else FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (s.visualStyle == VisualStyle.AMBIENT)
                    "Ambient mempertahankan cahaya artwork tanpa blur Glass."
                    else "Glass Ambient menggunakan lapisan frosted artwork.",
                    color = mist, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                SettingsSwitch(uiText("Ambient dari artwork"), uiText("Cahaya di belakang konten mengikuti musik"),
                    s.ambient, { state.update(s.copy(ambient = it)) })
                SettingsSlider(uiText("Kekuatan cahaya"), uiText("Sesuaikan bloom dan atmosfer"),
                    s.ambientIntensity, enabled = s.ambient,
                    onChange = { state.update(s.copy(ambientIntensity = it)) })
                SettingsSlider(uiText("Transparansi glass"), uiText("Keseimbangan visual dan keterbacaan"),
                    s.glassOpacity,
                    enabled = s.visualStyle == VisualStyle.GLASS,
                    onChange = { state.update(s.copy(glassOpacity = it)) })

                DividerSoft()
                SettingsCategory(uiText("INTERAKSI"), uiText("Animasi"))
                SettingsSwitch(uiText("Motion & morph"), uiText("Transisi spring pada popup, island, dan pemutar"),
                    s.motion, { state.update(s.copy(motion = it)) })

                Spacer(Modifier.height(17.dp))
                SettingsCategory("LANDSCAPE", "Posisi kontrol landscape")
                Text("Atur posisi rail Home/Search/Library/Settings dan tombol Music Player sekaligus.",
                    color = mist, fontSize = 11.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(false to "Kiri", true to "Kanan").forEach { (right, title) ->
                        Tactile(Modifier.weight(1f), {
                            state.update(s.copy(landscapeRailRight = right))
                        }) {
                            Glass(
                                Modifier.fillMaxWidth().height(46.dp),
                                RoundedCornerShape(18.dp),
                                tint = if (s.landscapeRailRight == right)
                                    state.ambientColor else groove,
                                opacity = if (s.landscapeRailRight == right) .80f else .42f
                            ) {
                                Row(Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (right) Icons.Rounded.KeyboardArrowRight
                                        else Icons.Rounded.KeyboardArrowLeft,
                                        null, Modifier.size(18.dp),
                                        tint = if (s.landscapeRailRight == right) lime else mist
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(title, color = if (s.landscapeRailRight == right) lime else mist,
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Gesture: geser tiga jari ke kiri atau kanan di menu utama maupun Music Player landscape. Animasi perpindahan mengikuti Motion & morph.",
                    color = mist, fontSize = 11.sp, lineHeight = 17.sp)


                DividerSoft()
                SettingsCategory(uiText("PEMUTAR"), uiText("Sumber musik"))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(AudioMode.AUTO to "Auto", AudioMode.NEWPIPE to "NewPipe",
                        AudioMode.AURELIS to "Server").forEach { (mode, label) ->
                        Tactile(action = { state.update(s.copy(audioMode = mode)) }) {
                            Glass(tint = if (s.audioMode == mode) state.ambientColor
                                else groove, opacity = if (s.audioMode == mode) .88f else .45f,
                                shape = RoundedCornerShape(19.dp)) {
                                Text(uiText(label), Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                    color = if (s.audioMode == mode) white else mist,
                                    fontSize = 12.sp,
                                    fontWeight = if (s.audioMode == mode)
                                        FontWeight.SemiBold else FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(uiText("Auto mencoba NewPipe, lalu server jika sumber gagal."),
                    color = mist, fontSize = 11.sp, lineHeight = 17.sp)

                DividerSoft()
                SettingsCategory(uiText("AKUN"), "YouTube Music")
                Text(if (state.youtube.isConnected)
                    "Terhubung. Rekomendasi, riwayat, dan playlist disegarkan saat aplikasi aktif."
                    else "Hubungkan akun agar Aurelis dapat membaca rekomendasi, riwayat, dan playlist YouTube Music.",
                    color = mist, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Tactile(Modifier.fillMaxWidth(), {
                    if (state.youtube.isConnected) state.youtube.disconnect()
                    else onConnectYouTube()
                }) {
                    Glass(Modifier.fillMaxWidth().height(52.dp),
                        RoundedCornerShape(20.dp),
                        tint = state.ambientColor, opacity = .54f,
                        backdrop = song?.artwork) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LibraryMusic, null, tint = lime,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(if (state.youtube.isConnected) "Putuskan akun YouTube Music"
                                else "Hubungkan YouTube Music", color = white,
                                fontSize = 13.sp)
                        }
                    }
                }
                if (state.youtube.isConnected) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        sheetScope.launch { state.youtube.refresh(force = true) }
                    }, enabled = !state.youtube.refreshing) {
                        Icon(Icons.Rounded.Refresh, null, tint = lime,
                            modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(uiText("Segarkan dari YouTube Music"), color = lime, fontSize = 12.sp)
                    }
                }
                state.youtube.status?.let {
                    Text(it, color = Color(0xFFFFC8C1), fontSize = 11.sp)
                }

                DividerSoft()
                SettingsCategory(uiText("BAHASA"), uiText("Lirik"))
                Text(uiText("Bahasa aplikasi"), color = white,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(9.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(AppLanguage.entries) { language ->
                        Tactile(action = {
                            state.update(s.copy(language = language))
                        }) {
                            Glass(
                                shape = RoundedCornerShape(20.dp),
                                tint = if (s.language == language)
                                    state.ambientColor else groove,
                                opacity = if (s.language == language) .78f else .46f
                            ) {
                                Text(language.displayName,
                                    Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    color = if (s.language == language) lime else mist,
                                    fontSize = 12.sp,
                                    fontWeight = if (s.language == language)
                                        FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(uiText("Bahasa terjemahan lirik mengikuti bahasa aplikasi."),
                    color = mist, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(17.dp))
                SettingsSwitch(uiText("Romanisasi otomatis"),
                    "Jepang, Korea, dan Mandarin bila tersedia",
                    s.romanize, { state.update(s.copy(romanize = it)) })
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("Offset lirik"), color = white, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        Text(uiText("Kalibrasi sinkronisasi karaoke"),
                            color = mist, fontSize = 11.sp)
                    }
                    IconButton(onClick = {
                        state.update(s.copy(lyricOffsetMs =
                            (s.lyricOffsetMs - 250).coerceAtLeast(-5000)))
                    }) { Icon(Icons.Rounded.RemoveCircleOutline, null, tint = mist) }
                    Text("%+.2fs".format(java.util.Locale.US, s.lyricOffsetMs / 1000.0),
                        color = lime, fontSize = 12.sp)
                    IconButton(onClick = {
                        state.update(s.copy(lyricOffsetMs =
                            (s.lyricOffsetMs + 250).coerceAtMost(5000)))
                    }) { Icon(Icons.Rounded.AddCircleOutline, null, tint = mist) }
                }

                DividerSoft()
                SettingsCategory("AURELIS", uiText("Pembaruan"))
                SettingsSwitch(uiText("Periksa versi baru"), uiText("Cek GitHub Releases saat aplikasi dibuka"),
                    s.checkUpdates, { state.update(s.copy(checkUpdates = it)) })
                Tactile(Modifier.fillMaxWidth(), { updater.check(manual = true) }) {
                    Glass(Modifier.fillMaxWidth().height(56.dp),
                        RoundedCornerShape(20.dp), opacity = .56f,
                        tint = state.ambientColor, backdrop = song?.artwork) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 17.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SystemUpdate, null, tint = lime,
                                modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(13.dp))
                            Text(if (updater.checking) "Memeriksa…" else "Periksa pembaruan",
                                Modifier.weight(1f), color = white, fontSize = 13.sp)
                            if (updater.checking)
                                CircularProgressIndicator(Modifier.size(18.dp),
                                    strokeWidth = 2.dp, color = lime)
                            else Icon(Icons.Rounded.ChevronRight, null,
                                Modifier.size(18.dp), tint = mist)
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                Glass(Modifier.fillMaxWidth(), RoundedCornerShape(21.dp),
                    tint = state.ambientColor, opacity = .37f,
                    backdrop = song?.artwork) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CleaningServices, null,
                                Modifier.size(20.dp), tint = lime)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Penyimpanan file pembaruan",
                                    fontSize = 13.sp, color = white,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("%.1f MB tersimpan · otomatis dibersihkan setelah 24 jam".format(
                                    java.util.Locale.US, updater.cachedApkBytes / 1048576.0),
                                    fontSize = 11.sp, color = mist)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Tactile(Modifier.fillMaxWidth(), {
                            updater.clearDownloadedUpdates()
                        }) {
                            Box(Modifier.fillMaxWidth().height(38.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(white.copy(alpha = .075f)),
                                contentAlignment = Alignment.Center) {
                                Text("Bersihkan file update sekarang",
                                    fontSize = 12.sp, color = lime,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                updater.message?.let {
                    Spacer(Modifier.height(11.dp))
                    Text(it, color = lime, fontSize = 11.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(17.dp))
                Text("AURELIS SYMPHONY  ·  ANDROID 1.2.7 STABLE",
                    color = mist.copy(alpha = .8f), fontSize = 10.sp,
                    letterSpacing = 1.sp)
                Text("APK dipasang melalui konfirmasi sistem Android.",
                    color = mist.copy(alpha = .58f), fontSize = 10.sp)
                Spacer(Modifier.height(25.dp))
            }
            // Dedicated large handle hit-zone. Fast swipes inside content
            // remain scroll gestures, while a downward pull from the strip
            // closes the sheet with its built-in dismissal animation.
            if (!landscapeSettings) Box(
                Modifier.fillMaxWidth().height(40.dp).align(Alignment.TopCenter)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onVerticalDrag = { change, dy ->
                                if (dy > 0) total += dy
                                else total = (total + dy).coerceAtLeast(0f)
                                change.consume()
                            },
                            onDragCancel = { total = 0f },
                            onDragEnd = {
                                if (total >= 78.dp.toPx()) closeSheet()
                                total = 0f
                            }
                        )
                    }, contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(43.dp).height(5.dp)
                    .clip(CircleShape).background(white.copy(alpha = .42f)))
            }
        }
    }
    if (landscapeSettings) {
        Dialog(
            onDismissRequest = closeSheet,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth(.87f).fillMaxHeight(.92f)
                        .clip(RoundedCornerShape(27.dp))
                        .background(Color(0xFF0D191E))
                ) {
                    sheetContent()
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = closeSheet,
            sheetState = sheetState,
            sheetGesturesEnabled = false,
            containerColor = Color(0xFF0D191E),
            contentColor = white,
            tonalElevation = 0.dp,
            scrimColor = bg.copy(alpha = .73f),
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
        ) {
            sheetContent()
        }
    }
}

@Composable
private fun DividerSoft() { HorizontalDivider(Modifier.padding(vertical = 24.dp), thickness = 1.dp, color = white.copy(alpha = .095f)) }

@Composable
private fun SettingsCategory(overline: String, title: String) {
    Text(overline, color = lime, fontSize = 10.sp, letterSpacing = 1.9.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Text(title, color = white, fontSize = 19.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun SettingsSwitch(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(uiText(title), color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp)); Text(desc, color = mist, fontSize = 11.sp, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF193329), checkedTrackColor = lime,
                uncheckedThumbColor = mist, uncheckedTrackColor = Color(0xFF263B36)))
    }
}

@Composable
private fun SettingsSlider(title: String, desc: String, value: Float, enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (enabled) white else mist, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", color = lime, fontSize = 12.sp)
        }
        Spacer(Modifier.height(3.dp)); Text(desc, color = mist, fontSize = 11.sp)
        Slider(value = value, onValueChange = onChange, valueRange = .15f..1f, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = lime, activeTrackColor = lime, inactiveTrackColor = white.copy(alpha = .12f)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: AurelisState, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF111C1B), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.68f).padding(horizontal = 21.dp)) {
            Text(uiText("Up Next"), color = white, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(state.activePlaylist?.let { "Playlist · $it" }
                ?: "Antrean berikutnya", color = mist, fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
            if (state.queue.isEmpty()) EmptyPage(uiText("Antrean kosong"), "Pemutar akan mencari rekomendasi setelah lagu selesai.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.queue.size, key = { i -> "queued-$i-${state.queue[i].key}" }) { i ->
                    val song = state.queue[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SongRow(song, state,
                                onPlay = { state.playFromQueue(song, i); onClose() },
                                onMenu = { state.removeQueue(song) })
                        }
                        IconButton(onClick = { state.removeQueue(song) }) { Icon(Icons.Default.Close, "Hapus antrean", tint = mist) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(state: AurelisState, song: Song, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF111C1B), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 23.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(song, 60.dp, 15.dp); Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = white, maxLines = 1, fontWeight = FontWeight.Medium)
                    Text(song.artist, color = mist, maxLines = 1, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            MenuLine(if (state.isLiked(song)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (state.isLiked(song)) "Hapus dari Disukai" else "Tambahkan ke Disukai") { state.like(song); onClose() }
            MenuLine(Icons.Default.QueueMusic, "Tambahkan ke antrean") { state.enqueue(song); onClose() }
            if (state.library.playlists.isNotEmpty()) {
                DividerSoft()
                Text(uiText("SIMPAN KE PLAYLIST"), color = mist, fontSize = 10.sp, letterSpacing = 1.7.sp)
                Spacer(Modifier.height(6.dp))
                state.library.playlists.keys.forEach { name ->
                    MenuLine(Icons.Default.PlaylistAdd, name) { state.addToPlaylist(name, song); onClose() }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun MenuLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, action: () -> Unit) {
    Tactile(Modifier.fillMaxWidth(), action) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = lime, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(title, color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
