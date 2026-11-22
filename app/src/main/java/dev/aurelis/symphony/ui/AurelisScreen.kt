package dev.aurelis.symphony.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

private val bg = Color(0xFF090D11)
private val white = Color(0xFFF3F8F5)
private val mist = Color(0xFF93A6A0)
private val lime = Color(0xFFBCEBD0)
private val groove = Color(0xFF243632)
private val motion = spring<Float>(dampingRatio = .86f, stiffness = Spring.StiffnessLow)
private val LocalGlassDensity = compositionLocalOf { .68f }
private val LocalReducedMotion = compositionLocalOf { false }

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
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalGlassDensity.current
    val strength = (opacity * (.46f + density * .69f)).coerceIn(.16f, .88f)
    val edge = Brush.linearGradient(
        listOf(white.copy(alpha = .32f), white.copy(alpha = .06f), lime.copy(alpha = .15f))
    )
    val fill = Brush.linearGradient(
        listOf(
            tint.mix(Color(0xFF0D1C20), .55f).copy(alpha = strength * .67f),
            Color(0xFF101B1E).copy(alpha = strength * .77f),
            tint.copy(alpha = strength * .48f)
        )
    )
    Box(modifier.clip(shape)) {
        // Real GPU blur is limited to the artwork layer, never the text/icons above it.
        if (backdrop?.startsWith("https://") == true) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize().blur(24.dp).graphicsLayer { alpha = .47f },
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
    LaunchedEffect(state.settings.checkUpdates) {
        while (state.settings.checkUpdates) {
            updater.check()
            delay(6L * 60L * 60L * 1000L)
        }
    }
    var tab by rememberSaveable { mutableStateOf("Home") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
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

    CompositionLocalProvider(LocalGlassDensity provides state.settings.glassOpacity, LocalReducedMotion provides !state.settings.motion) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = lime, secondary = Color(0xFF98CDB9), background = bg,
        surface = Color(0xFF15221F), onSurface = white, onBackground = white
    )) {
        Box(Modifier.fillMaxSize()) {
            Ambient(state)
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(tab, onSettings = { showSettings = true }, onLibrary = { tab = "Library" })
                Box(Modifier.weight(1f)) {
                    AnimatedContent(targetState = tab, label = "page", transitionSpec = {
                        (
                            fadeIn(tween(if (state.settings.motion) 270 else 0)) +
                                slideInVertically(tween(if (state.settings.motion) 320 else 0)) { it / 20 }
                        ) togetherWith fadeOut(tween(if (state.settings.motion) 190 else 0))
                    }) { page ->
                        when (page) {
                            "Home" -> HomePage(state, onPlay = { state.play(it) }, onMenu = { menuSong = it })
                            "Search" -> SearchPage(state, searchQuery, onPlay = { state.play(it) }, onMenu = { menuSong = it })
                            else -> LibraryPage(state, onPlay = { state.play(it) }, onMenu = { menuSong = it }, onCreate = { creatingPlaylist = true })
                        }
                    }
                }
                Spacer(Modifier.height(if (state.current == null) 91.dp else 164.dp))
            }
            Column(Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(horizontal = 15.dp).padding(bottom = 10.dp)) {
                state.current?.let {
                    AnimatedVisibility(visible = !expanded,
                        enter = fadeIn(tween(220)) + slideInVertically { it / 2 },
                        exit = fadeOut(tween(160)) + slideOutVertically { it / 2 }) {
                        MiniPlayer(state, onExpand = { expanded = true })
                    }
                }
                Spacer(Modifier.height(10.dp))
                IslandDock(tab, searchExpanded, searchQuery,
                    onTab = { tab = it; searchExpanded = false },
                    onSearchExpand = { tab = "Search"; searchExpanded = !searchExpanded },
                    onQuery = { searchQuery = it }, artwork = state.current?.artwork.orEmpty())
            }
            AnimatedVisibility(visible = expanded && state.current != null,
                enter = fadeIn(tween(if (state.settings.motion) 370 else 0)) +
                    scaleIn(initialScale = .935f, animationSpec = spring(dampingRatio = .88f, stiffness = Spring.StiffnessLow)) +
                    slideInVertically(animationSpec = spring(dampingRatio = .92f, stiffness = Spring.StiffnessLow)) { it / 8 },
                exit = fadeOut(tween(if (state.settings.motion) 240 else 0)) +
                    scaleOut(targetScale = .95f, animationSpec = tween(300)) +
                    slideOutVertically(tween(300)) { it / 7 }
            ) {
                FullPlayer(state, onClose = { expanded = false }, onQueue = { showQueue = true }, onMenu = { state.current?.let { menuSong = it } })
            }
            UpdateAnnouncement(updater, Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 19.dp, vertical = 96.dp))
        }
        if (showSettings) SettingsSheet(state, updater, onClose = { showSettings = false })
        if (showQueue) QueueSheet(state, onClose = { showQueue = false })
        menuSong?.let { song -> SongMenu(state, song, onClose = { menuSong = null }) }
        if (creatingPlaylist) AlertDialog(
            onDismissRequest = { creatingPlaylist = false },
            title = { Text("Playlist baru", fontWeight = FontWeight.SemiBold) },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Nama playlist") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { state.addPlaylist(playlistName); playlistName = ""; creatingPlaylist = false }) { Text("Buat") } },
            dismissButton = { TextButton(onClick = { creatingPlaylist = false }) { Text("Batal") } }
        )
        state.error?.let { e -> AlertDialog(onDismissRequest = state::dismissError,
            title = { Text("Aurelis") }, text = { Text(e) },
            confirmButton = { TextButton(onClick = state::dismissError) { Text("Tutup") } }) }
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
                            Text("Versi baru tersedia", color = white,
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
                    Text("Unduh dari GitHub dan pasang dengan izin Android.",
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
                                Text("Nanti", Modifier.align(Alignment.Center),
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
            Text("Aurelis", color = white, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = -.6.sp)
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
private fun HomePage(state: AurelisState, onPlay: (Song) -> Unit, onMenu: (Song) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 22.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Text("CURATED FOR YOUR MOMENT", fontSize = 10.sp, letterSpacing = 2.sp, color = lime.copy(alpha = .83f), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text("Find your\nfrequency.", fontSize = 42.sp, fontWeight = FontWeight.SemiBold,
                lineHeight = 43.sp, letterSpacing = -1.8.sp, color = white)
            Spacer(Modifier.height(8.dp))
            Text("Musik yang mengikuti suasanamu.", color = mist, fontSize = 13.sp)
        }
        if (state.loadingHome) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 20.dp), color = lime) }
        state.home.firstOrNull()?.let { song ->
            item { FeaturedSong(song, state, onPlay = { onPlay(song) }) }
        }
        if (state.home.isNotEmpty()) {
            item { SectionHeading("Dibuat untukmu", "Pilihan berdasarkan suasana dan kebiasaan") }
            items(state.home.drop(1).take(18), key = { "home-${it.key}" }) { song -> SongRow(song, state, onPlay = { onPlay(song) }, onMenu = { onMenu(song) }) }
        }
        if (state.originals.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)); SectionHeading("Aurelis Originals", "Musik orisinal dari perpustakaan") }
            items(state.originals, key = { "original-${it.key}" }) { song -> SongRow(song, state, onPlay = { onPlay(song) }, onMenu = { onMenu(song) }) }
        }
        if (!state.loadingHome && state.home.isEmpty() && state.originals.isEmpty()) item { EmptyPage("Belum ada musik", "Periksa koneksi atau server Aurelis lalu buka kembali Home.") }
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
                Text("ON THE RADAR", color = lime, fontSize = 10.sp, letterSpacing = 1.7.sp)
                Spacer(Modifier.height(8.dp))
                Text(song.title, color = white, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 27.sp, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = mist, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = lime, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(5.dp)); Text("Putar sekarang", fontSize = 12.sp, color = white, fontWeight = FontWeight.Medium)
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
private fun SearchPage(state: AurelisState, query: String, onPlay: (Song) -> Unit, onMenu: (Song) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { SectionHeading("Search", if (query.isBlank()) "Mulai cari dari Island Search di bawah" else "Hasil untuk “${query.take(60)}”") }
        if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = lime) }
        if (query.isBlank()) item { EmptyPage("Temukan sesuatu yang baru", "Cari judul, artis, atau musik untuk didengarkan.") }
        else if (!state.searching && state.results.isEmpty()) item { EmptyPage("Belum ada hasil", "Coba kata kunci yang berbeda.") }
        items(state.results, key = { "result-${it.key}" }) { song -> SongRow(song, state, onPlay = { onPlay(song) }, onMenu = { onMenu(song) }) }
    }
}

@Composable
private fun LibraryPage(state: AurelisState, onPlay: (Song) -> Unit, onMenu: (Song) -> Unit, onCreate: () -> Unit) {
    var segment by rememberSaveable { mutableStateOf("Disukai") }
    val libraryStamp = state.libraryStamp
    val list = remember(segment, libraryStamp) {
        when (segment) { "Disukai" -> state.library.favorites; "Riwayat" -> state.library.history; else -> emptyList() }
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 21.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { SectionHeading("Your Library",
            if (segment == "Riwayat") "${list.size} pemutaran terakhir · termasuk lagu yang diputar ulang"
            else "Satu ruang untuk semua musikmu") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Disukai", "Riwayat", "Playlist").forEach { item ->
                    Tactile(action = { segment = item }) {
                        Glass(Modifier, RoundedCornerShape(22.dp), tint = if (item == segment) Color(0xFF476C5C) else Color(0xFF1F302D), opacity = if (item == segment) .9f else .54f) {
                            Text(item, Modifier.padding(horizontal = 17.dp, vertical = 11.dp), color = if (item == segment) white else mist, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (segment == "Playlist") {
            item { Tactile(action = onCreate) { Glass(Modifier.fillMaxWidth()) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, tint = lime); Spacer(Modifier.width(12.dp)); Text("Buat playlist", color = white)
            } } } }
            state.library.playlists.forEach { (name, tracks) ->
                item(key = "playlist-$name") {
                    var opened by remember { mutableStateOf(false) }
                    Column {
                        Tactile(Modifier.fillMaxWidth(), { opened = !opened }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LibraryMusic, null, tint = lime); Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) { Text(name, color = white, fontWeight = FontWeight.Medium); Text("${tracks.size} lagu", color = mist, fontSize = 11.sp) }
                                Icon(if (opened) Icons.Default.ExpandLess else Icons.Default.ChevronRight, null, tint = mist)
                            }
                        }
                        if (opened) tracks.forEach { song -> SongRow(song, state, onPlay = { onPlay(song) }, onMenu = { onMenu(song) }) }
                    }
                }
            }
        } else if (list.isEmpty()) item { EmptyPage("Belum ada lagu", if (segment == "Disukai") "Ketuk ikon hati untuk menyimpan lagu." else "Lagu yang kamu dengarkan akan muncul di sini.") }
        else items(list.size, key = { index -> "${segment}-${index}-${list[index].key}" }) { index ->
            val song = list[index]
            SongRow(song, state, onPlay = { onPlay(song) }, onMenu = { onMenu(song) })
        }
    }
}

@Composable
private fun SongRow(song: Song, state: AurelisState, onPlay: () -> Unit, onMenu: () -> Unit) {
    var menuOpen by remember(song.key) { mutableStateOf(false) }
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
            IconButton(onClick = { menuOpen = !menuOpen }, modifier = Modifier.size(43.dp)) {
                AnimatedContent(targetState = menuOpen, label = "moreX", transitionSpec = {
                    (fadeIn(tween(145)) + scaleIn(initialScale = .70f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = .75f))
                }) { open ->
                    Icon(if (open) Icons.Rounded.Close else Icons.Rounded.MoreVert,
                        if (open) "Tutup opsi" else "Opsi lagu",
                        tint = if (open) lime else mist, modifier = Modifier.size(21.dp))
                }
            }
            InlineGlassSongMenu(state, song, menuOpen, { menuOpen = false })
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
    private val inset: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val above = anchorBounds.top - inset
        val below = windowSize.height - anchorBounds.bottom - inset
        val putBelow = preferDown || below >= height || below >= above
        // Top-right stays fixed at the 3-dot capsule. Near screen bottom, morph upward.
        val y = if (putBelow) anchorBounds.top - inset
            else anchorBounds.bottom - height + inset
        return IntOffset(
            (anchorBounds.right - width + inset).coerceIn(inset,
                max(inset, windowSize.width - width - inset)),
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
    lyricOffset: Boolean = false
) {
    val visibility = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return
    val margin = with(LocalDensity.current) { 9.dp.roundToPx() }
    val provider = remember(lyricOffset, margin) {
        AdaptiveGlassMenuPosition(preferDown = lyricOffset, inset = margin)
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = expandIn(expandFrom = Alignment.TopEnd,
                animationSpec = spring(dampingRatio = .82f,
                    stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(185)),
            exit = shrinkOut(shrinkTowards = Alignment.TopEnd,
                animationSpec = tween(275)) + fadeOut(tween(185))
        ) {
        Glass(
            Modifier.width(252.dp).shadow(20.dp, RoundedCornerShape(23.dp)),
            RoundedCornerShape(23.dp), tint = state.ambientColor, opacity = .89f,
            backdrop = song.artwork
        ) {
            Column(Modifier.fillMaxWidth().padding(7.dp)) {
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
                state.library.playlists.keys.take(4).forEach { name ->
                    GlassPopupItem(Icons.Rounded.PlaylistAdd, "Playlist · $name") {
                        state.addToPlaylist(name, song); onDismiss()
                    }
                }
                if (lyricOffset) {
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = white.copy(alpha = .15f))
                    Text("OFFSET LIRIK", Modifier.padding(start = 13.dp, bottom = 3.dp),
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
                    Text("Tekan waktu untuk reset", Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            tint = state.ambientColor.mix(Color(0xFF284037), .45f), opacity = .70f, backdrop = song.artwork) {
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
                Box(Modifier.fillMaxWidth().height(2.dp).background(white.copy(alpha = .12f))) {
                    Box(Modifier.fillMaxWidth(if (state.duration > 0) (state.progress.toFloat() / state.duration).coerceIn(0f, 1f) else 0f)
                        .fillMaxHeight().background(Brush.horizontalGradient(listOf(lime.copy(alpha = .63f), lime))))
                }
            }
        }
    }
}

@Composable
private fun IslandDock(tab: String, expanded: Boolean, query: String,
    onTab: (String) -> Unit, onSearchExpand: () -> Unit, onQuery: (String) -> Unit,
    artwork: String = "") {
    val focus = LocalFocusManager.current
    val reduce = LocalReducedMotion.current
    val tabs = listOf("Home", "Search", "Library")
    var hovered by remember { mutableIntStateOf(-1) }
    val navWeight by animateFloatAsState(
        targetValue = if (expanded) .16f else .79f,
        animationSpec = if (reduce) tween(0) else spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow),
        label = "islandMorph"
    )
    Row(Modifier.fillMaxWidth().height(59.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Glass(
            Modifier.weight(navWeight).fillMaxHeight(), RoundedCornerShape(32.dp),
            opacity = .53f, backdrop = artwork
        ) {
            if (expanded) {
                Tactile(Modifier.fillMaxSize(), { focus.clearFocus(); onSearchExpand() }) {
                    Icon(Icons.Default.Menu, "Tampilkan navigasi", tint = white,
                        modifier = Modifier.size(22.dp).align(Alignment.Center))
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp).pointerInput(tab) {
                    detectDragGestures(
                        onDragStart = { p -> hovered = ((p.x / size.width) * 3f).toInt().coerceIn(0, 2) },
                        onDrag = { change, _ ->
                            hovered = ((change.position.x / size.width) * 3f).toInt().coerceIn(0, 2)
                            change.consume()
                        },
                        onDragEnd = { val selected = hovered; hovered = -1; if (selected in 0..2) onTab(tabs[selected]) },
                        onDragCancel = { hovered = -1 }
                    )
                }) {
                    val segment = maxWidth / 3
                    val selection = if (hovered in 0..2) hovered else tabs.indexOf(tab).coerceAtLeast(0)
                    val indicator by animateDpAsState(
                        targetValue = segment * selection,
                        animationSpec = if (reduce) tween(0) else spring(dampingRatio = .80f, stiffness = Spring.StiffnessMediumLow),
                        label = "islandSelection"
                    )
                    Box(
                        Modifier.offset(x = indicator).width(segment).fillMaxHeight()
                            .clip(RoundedCornerShape(25.dp))
                            .background(Brush.verticalGradient(listOf(
                                lime.copy(alpha = .34f), Color(0xFF547C69).copy(alpha = .31f)
                            )))
                            .border(1.dp, lime.copy(alpha = .22f), RoundedCornerShape(25.dp))
                    )
                    Row(Modifier.fillMaxSize()) {
                        tabs.forEachIndexed { index, name ->
                            val icon = when (name) {
                                "Home" -> Icons.Rounded.Home
                                "Search" -> Icons.Rounded.Search
                                else -> Icons.Rounded.LibraryMusic
                            }
                            Tactile(Modifier.weight(1f).fillMaxHeight(), { hovered = -1; onTab(name) }) {
                                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center) {
                                    Icon(icon, name, Modifier.size(20.dp),
                                        tint = if (selection == index) white else mist)
                                    Spacer(Modifier.height(2.dp))
                                    Text(name, color = if (selection == index) white else mist, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Glass(
            Modifier.weight(1f - navWeight).fillMaxHeight(), RoundedCornerShape(32.dp),
            opacity = .55f, backdrop = artwork
        ) {
            if (expanded) {
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = lime, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(value = query, onValueChange = onQuery,
                        modifier = Modifier.weight(1f), cursorBrush = SolidColor(lime), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = white, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        decorationBox = { inner -> Box {
                            if (query.isEmpty()) Text("Lagu, artis, album…", color = mist, fontSize = 13.sp)
                            inner()
                        } })
                    IconButton(onClick = {
                        if (query.isNotEmpty()) onQuery("") else { focus.clearFocus(); onSearchExpand() }
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, "Hapus atau tutup", tint = mist, modifier = Modifier.size(19.dp))
                    }
                }
            } else {
                Tactile(Modifier.fillMaxSize(), onSearchExpand) {
                    Icon(Icons.Rounded.Search, "Cari lagu", tint = white,
                        modifier = Modifier.size(22.dp).align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun FullPlayer(state: AurelisState, onClose: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit) {
    val song = state.current ?: return
    val stamp = state.libraryStamp
    var pane by rememberSaveable { mutableStateOf("Lirik") }
    var optionsOpen by remember { mutableStateOf(false) }
    var showTranslation by rememberSaveable { mutableStateOf(false) }
    var showRoman by rememberSaveable { mutableStateOf(state.settings.romanize) }
    val artwork = song.artwork.takeIf { it.startsWith("https://") }
    val video = song.videoId.ifBlank { song.id.removePrefix("yt-") }
    val hdEligible = video.matches(Regex("[A-Za-z0-9_-]{11}"))
    var useMaxres by remember(song.key) { mutableStateOf(hdEligible) }
    val coverUrl = if (useMaxres && hdEligible)
        "https://i.ytimg.com/vi/$video/maxresdefault.jpg" else artwork
    val animateMs = if (state.settings.motion) 360 else 0

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

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleAction(Icons.Rounded.KeyboardArrowDown, "Tutup pemutar", onClose)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", color = white.copy(alpha = .54f),
                        fontSize = 9.sp, letterSpacing = 2.2.sp)
                    Text("Aurelis Symphony", color = white, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    OverflowMorphButton(optionsOpen) { optionsOpen = !optionsOpen }
                    InlineGlassSongMenu(state, song, optionsOpen,
                        onDismiss = { optionsOpen = false }, lyricOffset = true)
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
                                IconButton(onClick = { showTranslation = !showTranslation },
                                    modifier = Modifier.size(40.dp)) {
                                    Glass(Modifier.fillMaxSize(), RoundedCornerShape(20.dp),
                                        opacity = if (showTranslation) .78f else .37f,
                                        tint = if (showTranslation) state.ambientColor else groove,
                                        backdrop = artwork) {
                                        Icon(Icons.Rounded.Translate, "Terjemahan",
                                            Modifier.size(18.dp).align(Alignment.Center),
                                            tint = if (showTranslation) lime else mist)
                                    }
                                }
                            }
                            if (showTranslation && state.lyrics.lines.none { it.translation.isNotBlank() }) {
                                Text("Terjemahan belum tersedia dari sumber lirik.",
                                    Modifier.padding(horizontal = 28.dp, vertical = 2.dp),
                                    color = mist, fontSize = 11.sp)
                            }
                            // There is deliberately NO separate glass lyric panel.
                            LyricContent(state, Modifier.fillMaxWidth().weight(1f),
                                showRoman = showRoman, showTranslation = showTranslation)
                        }
                    }
                    else -> {
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                            Spacer(Modifier.height(13.dp))
                            Text("Up Next", color = white, fontSize = 27.sp,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Antrean musik selanjutnya", color = mist, fontSize = 12.sp)
                            Spacer(Modifier.height(17.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(song, 49.dp, 12.dp)
                                Spacer(Modifier.width(11.dp))
                                Column {
                                    Text("Sedang diputar", color = lime, fontSize = 10.sp)
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
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.queue, key = { "player-queue-${it.key}" }) { queued ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.weight(1f)) {
                                                SongRow(queued, state,
                                                    onPlay = { state.play(queued) },
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

@Composable
private fun PlayerModeIsland(selected: String, onSelect: (String) -> Unit,
    artwork: String, enabledMotion: Boolean) {
    val modes = listOf("Cover", "Lirik", "Up Next")
    val icons = listOf(Icons.Rounded.Album, Icons.Rounded.Mic, Icons.Rounded.QueueMusic)
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
            Box(Modifier.offset(x = x).width(cellWidth).fillMaxHeight()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(
                    lime.copy(alpha = .30f), Color(0xFF467A67).copy(alpha = .25f)
                )))
                .border(1.dp, lime.copy(alpha = .20f), RoundedCornerShape(26.dp)))
            Row(Modifier.fillMaxSize()) {
                modes.forEachIndexed { i, label ->
                    Tactile(Modifier.weight(1f).fillMaxHeight(), { dragging = -1; onSelect(label) }) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(icons[i], label, Modifier.size(18.dp),
                                tint = if (i == highlight) white else mist)
                            Spacer(Modifier.width(5.dp))
                            Text(label, fontSize = 11.sp,
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
    showRoman: Boolean = true, showTranslation: Boolean = false
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
                Text("Lirik belum tersedia", color = white,
                    fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Lirik akan mengikuti musik jika sumber menyediakan waktu sinkronisasi.",
                    color = mist, fontSize = 12.sp, textAlign = TextAlign.Center,
                    lineHeight = 18.sp)
            }
        }
        return
    }

    LazyColumn(
        state = scroll, modifier = modifier,
        contentPadding = PaddingValues(horizontal = 23.dp, vertical = 39.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
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
                        fontSize = 23.sp, lineHeight = 34.sp,
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
                if (showTranslation && line.translation.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(line.translation, color = if (focused) white.copy(alpha = .77f) else mist,
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
private fun PlayerControls(state: AurelisState) {
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(38.dp)
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
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth().height(70.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            Tactile(action = state::previous) {
                Glass(Modifier.size(49.dp), RoundedCornerShape(25.dp),
                    tint = state.ambientColor, opacity = .54f,
                    backdrop = song?.artwork) {
                    Icon(Icons.Rounded.SkipPrevious, "Sebelumnya",
                        Modifier.size(24.dp).align(Alignment.Center), tint = white)
                }
            }
            Tactile(action = state::togglePlay) {
                Glass(Modifier.size(69.dp), RoundedCornerShape(35.dp),
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
                                "Putar atau jeda", Modifier.size(33.dp), tint = lime)
                        }
                    }
                }
            }
            Tactile(action = state::next) {
                Glass(Modifier.size(49.dp), RoundedCornerShape(25.dp),
                    tint = state.ambientColor, opacity = .54f,
                    backdrop = song?.artwork) {
                    Icon(Icons.Rounded.SkipNext, "Berikutnya",
                        Modifier.size(24.dp).align(Alignment.Center), tint = white)
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
private fun SettingsSheet(state: AurelisState, updater: AurelisUpdater, onClose: () -> Unit) {
    val song = state.current
    val s = state.settings
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = white,
        tonalElevation = 0.dp,
        scrimColor = bg.copy(alpha = .73f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    ) {
        Glass(
            Modifier.fillMaxWidth().fillMaxHeight(.88f),
            RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            tint = state.ambientColor,
            opacity = .83f,
            backdrop = song?.artwork
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(
                androidx.compose.foundation.rememberScrollState()
            ).padding(start = 23.dp, end = 23.dp, top = 12.dp, bottom = 26.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(34.dp).height(4.dp)
                    .clip(CircleShape).background(white.copy(alpha = .28f)))
                Spacer(Modifier.height(21.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PREFERENSI", fontSize = 9.sp, letterSpacing = 2.sp,
                            color = lime.copy(alpha = .80f))
                        Spacer(Modifier.height(6.dp))
                        Text("Pengaturan", color = white,
                            fontSize = 29.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-.8).sp)
                    }
                    Tactile(action = onClose) {
                        Glass(Modifier.size(42.dp), RoundedCornerShape(21.dp), opacity = .60f) {
                            Icon(Icons.Rounded.Close, "Tutup pengaturan",
                                Modifier.align(Alignment.Center).size(20.dp), tint = white)
                        }
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text("Ruang untuk musik, cahaya, dan gerakan.", color = mist, fontSize = 12.sp)
                Spacer(Modifier.height(22.dp))

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
                            Text("Glass Ambient", color = white,
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text("Warna dan cahaya mengikuti artwork",
                                color = mist, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsCategory("SUASANA", "Tampilan")
                SettingsSwitch("Ambient dari artwork",
                    "Cahaya di belakang konten mengikuti musik",
                    s.ambient, { state.update(s.copy(ambient = it)) })
                SettingsSlider("Kekuatan cahaya", "Sesuaikan bloom dan atmosfer",
                    s.ambientIntensity, enabled = s.ambient,
                    onChange = { state.update(s.copy(ambientIntensity = it)) })
                SettingsSlider("Transparansi glass", "Keseimbangan visual dan keterbacaan",
                    s.glassOpacity, onChange = { state.update(s.copy(glassOpacity = it)) })

                DividerSoft()
                SettingsCategory("INTERAKSI", "Animasi")
                SettingsSwitch("Motion & morph",
                    "Transisi spring pada popup, island, dan pemutar",
                    s.motion, { state.update(s.copy(motion = it)) })

                DividerSoft()
                SettingsCategory("PEMUTAR", "Sumber musik")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(AudioMode.AUTO to "Auto", AudioMode.NEWPIPE to "NewPipe",
                        AudioMode.AURELIS to "Server").forEach { (mode, label) ->
                        Tactile(action = { state.update(s.copy(audioMode = mode)) }) {
                            Glass(tint = if (s.audioMode == mode) state.ambientColor
                                else groove, opacity = if (s.audioMode == mode) .88f else .45f,
                                shape = RoundedCornerShape(19.dp)) {
                                Text(label, Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                    color = if (s.audioMode == mode) white else mist,
                                    fontSize = 12.sp,
                                    fontWeight = if (s.audioMode == mode)
                                        FontWeight.SemiBold else FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Auto mencoba NewPipe, lalu server jika sumber gagal.",
                    color = mist, fontSize = 11.sp, lineHeight = 17.sp)

                DividerSoft()
                SettingsCategory("BAHASA", "Lirik")
                SettingsSwitch("Romanisasi otomatis",
                    "Jepang, Korea, dan Mandarin bila tersedia",
                    s.romanize, { state.update(s.copy(romanize = it)) })
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Offset lirik", color = white, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        Text("Kalibrasi sinkronisasi karaoke",
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
                SettingsCategory("AURELIS", "Pembaruan")
                SettingsSwitch("Periksa versi baru",
                    "Cek GitHub Releases saat aplikasi dibuka",
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
                updater.message?.let {
                    Spacer(Modifier.height(11.dp))
                    Text(it, color = lime, fontSize = 11.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(17.dp))
                Text("AURELIS SYMPHONY  ·  ANDROID 0.1.2 BETA",
                    color = mist.copy(alpha = .8f), fontSize = 10.sp,
                    letterSpacing = 1.sp)
                Text("APK dipasang melalui konfirmasi sistem Android.",
                    color = mist.copy(alpha = .58f), fontSize = 10.sp)
                Spacer(Modifier.height(25.dp))
            }
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
            Text(title, color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            Text("Up Next", color = white, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("Antrean berikutnya", color = mist, fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
            if (state.queue.isEmpty()) EmptyPage("Antrean kosong", "Pemutar akan mencari rekomendasi setelah lagu selesai.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.queue, key = { "queued-${it.key}" }) { song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SongRow(song, state, onPlay = { state.play(song); onClose() }, onMenu = { state.removeQueue(song) }) }
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
                Text("SIMPAN KE PLAYLIST", color = mist, fontSize = 10.sp, letterSpacing = 1.7.sp)
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
