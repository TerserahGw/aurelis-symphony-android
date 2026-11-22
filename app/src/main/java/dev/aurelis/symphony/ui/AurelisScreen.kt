package dev.aurelis.symphony.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import dev.aurelis.symphony.data.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalGlassDensity.current
    val actualOpacity = (opacity * (.45f + density * .82f)).coerceIn(.18f, .94f)
    val edge = Brush.linearGradient(listOf(white.copy(alpha = .23f), white.copy(alpha = .055f), white.copy(alpha = .12f)))
    val fill = Brush.linearGradient(listOf(tint.mix(Color(0xFF1A2429), .40f).copy(alpha = actualOpacity),
        Color(0xFF142224).copy(alpha = (actualOpacity + .08f).coerceAtMost(.97f))))
    Box(modifier.clip(shape).background(fill, shape).border(BorderStroke(1.dp, edge), shape), content = content)
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
    val c by animateColorAsState(state.ambientColor, animationSpec = tween(if (settings.motion) 920 else 0), label = "ambientTint")
    val a = if (settings.ambient) settings.ambientIntensity else 0f
    Box(Modifier.fillMaxSize().background(bg)) {
        Box(Modifier.fillMaxWidth().height(390.dp).align(Alignment.TopCenter).background(
            Brush.verticalGradient(listOf(c.copy(alpha = .31f * a), c.copy(alpha = .07f * a), Color.Transparent))
        ))
        Box(Modifier.fillMaxWidth(.9f).height(400.dp).align(Alignment.BottomEnd).background(
            Brush.radialGradient(listOf(c.mix(Color(0xFF32665D), .28f).copy(alpha = .18f * a), Color.Transparent))
        ))
    }
}

@Composable
fun AurelisScreen(state: AurelisState) {
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
        if (searchQuery.isBlank()) state.search("")
        else { delay(420); state.search(searchQuery) }
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
                        fadeIn(tween(if (state.settings.motion) 230 else 0)) togetherWith fadeOut(tween(if (state.settings.motion) 170 else 0))
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
                state.current?.let { MiniPlayer(state, onExpand = { expanded = true }) }
                Spacer(Modifier.height(10.dp))
                IslandDock(tab, searchExpanded, searchQuery,
                    onTab = { tab = it; searchExpanded = false },
                    onSearchExpand = { tab = "Search"; searchExpanded = !searchExpanded },
                    onQuery = { searchQuery = it })
            }
            AnimatedVisibility(visible = expanded && state.current != null,
                enter = fadeIn(tween(240)) + slideInVertically(tween(420)) { it / 6 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(310)) { it / 8 }
            ) {
                FullPlayer(state, onClose = { expanded = false }, onQueue = { showQueue = true }, onMenu = { state.current?.let { menuSong = it } })
            }
        }
        if (showSettings) SettingsSheet(state, onClose = { showSettings = false })
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
        item { SectionHeading("Your Library", "Satu ruang untuk semua musikmu") }
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
        else items(list, key = { "${segment}-${it.key}" }) { song -> SongRow(song, state, onPlay = { onPlay(song) }, onMenu = { onMenu(song) }) }
    }
}

@Composable
private fun SongRow(song: Song, state: AurelisState, onPlay: () -> Unit, onMenu: () -> Unit) {
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
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = mist, fontSize = 12.sp)
                }
            }
        }
        IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, "Opsi lagu", tint = mist) }
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
            tint = state.ambientColor.mix(Color(0xFF284037), .45f), opacity = .87f) {
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
                        .fillMaxHeight().background(lime))
                }
            }
        }
    }
}

@Composable
private fun IslandDock(tab: String, expanded: Boolean, query: String,
    onTab: (String) -> Unit, onSearchExpand: () -> Unit, onQuery: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val reduce = LocalReducedMotion.current
    val navWeight by animateFloatAsState(
        targetValue = if (expanded) .16f else .79f,
        animationSpec = if (reduce) tween(0) else spring(dampingRatio = .87f, stiffness = Spring.StiffnessLow),
        label = "islandMorph"
    )
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Glass(Modifier.weight(navWeight).fillMaxHeight(),
            RoundedCornerShape(32.dp), opacity = .84f) {
            if (expanded) {
                Tactile(Modifier.fillMaxSize(), { focus.clearFocus(); onSearchExpand() }) {
                    Icon(Icons.Default.Menu, "Tampilkan menu", tint = white, modifier = Modifier.align(Alignment.Center))
                }
            } else {
                Row(Modifier.fillMaxSize().padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(Triple("Home", Icons.Default.Home, "Beranda"), Triple("Search", Icons.Default.Search, "Cari"), Triple("Library", Icons.Default.LibraryMusic, "Koleksi")).forEach { (name, icon, label) ->
                        Tactile(Modifier.weight(1f).fillMaxHeight(), { onTab(name) }) {
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                                .background(if (tab == name) Color(0xFF527665).copy(alpha = .56f) else Color.Transparent),
                                contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(icon, label, Modifier.size(19.dp), tint = if (tab == name) white else mist)
                                    Spacer(Modifier.height(2.dp)); Text(name, color = if (tab == name) white else mist, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Glass(Modifier.weight(1f - navWeight).fillMaxHeight(),
            RoundedCornerShape(32.dp), opacity = .85f) {
            if (expanded) {
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = lime, modifier = Modifier.size(20.dp))
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
                    IconButton(onClick = { if (query.isNotEmpty()) onQuery("") else { focus.clearFocus(); onSearchExpand() } }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "Hapus atau tutup", tint = mist, modifier = Modifier.size(19.dp))
                    }
                }
            } else {
                Tactile(Modifier.fillMaxSize(), onSearchExpand) {
                    Icon(Icons.Default.Search, "Cari lagu", tint = white, modifier = Modifier.size(23.dp).align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun FullPlayer(state: AurelisState, onClose: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit) {
    val song = state.current ?: return
    val stamp = state.libraryStamp // observe local like/playlist changes
    var pane by rememberSaveable { mutableStateOf("Lirik") }
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(Brush.horizontalGradient(listOf(state.ambientColor, Color(0xFF182827)))))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleAction(Icons.Default.KeyboardArrowDown, "Tutup pemutar", onClose)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", color = mist, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("Aurelis Symphony", color = white, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            CircleAction(Icons.Default.MoreHoriz, "Opsi lagu", onMenu)
        }
        AnimatedContent(targetState = pane, modifier = Modifier.weight(1f), label = "playerPane", transitionSpec = {
            fadeIn(tween(if (state.settings.motion) 250 else 0)) togetherWith fadeOut(tween(if (state.settings.motion) 140 else 0))
        }) { page ->
            if (page == "Lirik") {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 27.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(song, 61.dp, 17.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = white, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp)); Text(song.artist, color = mist, fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = { state.like(song) }) {
                            Icon(if (state.isLiked(song)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Sukai lagu", tint = if (state.isLiked(song)) lime else mist)
                        }
                    }
                    LyricContent(state, Modifier.fillMaxWidth().weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 27.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(.22f))
                    Artwork(song, 285.dp, 28.dp)
                    Spacer(Modifier.height(25.dp))
                    Text(song.title, color = white, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(5.dp)); Text(song.artist, color = mist, fontSize = 14.sp)
                    Spacer(Modifier.weight(.28f))
                }
            }
        }
        PlayerControls(state)
        Row(Modifier.fillMaxWidth().padding(start = 27.dp, end = 27.dp, top = 5.dp, bottom = 17.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { pane = if (pane == "Lirik") "Sampul" else "Lirik" }) {
                Icon(if (pane == "Lirik") Icons.Default.Album else Icons.Default.Mic, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp))
                Text(if (pane == "Lirik") "Sampul" else "Lirik", fontSize = 12.sp)
            }
            TextButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Up Next", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun LyricContent(state: AurelisState, modifier: Modifier = Modifier) {
    val currentSec = state.progress / 1000.0
    val lines = state.lyrics.lines
    val active = lines.indexOfLast { it.time != null && it.time <= currentSec }
    val scroll = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0 && lines.isNotEmpty()) {
            if (state.settings.motion) scroll.animateScrollToItem((active - 2).coerceAtLeast(0))
            else scroll.scrollToItem((active - 2).coerceAtLeast(0))
        }
    }
    if (lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✦", color = lime.copy(alpha = .45f), fontSize = 34.sp)
                Spacer(Modifier.height(12.dp)); Text("Lirik belum tersedia", color = white, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(7.dp)); Text("Lirik disinkronkan jika sumber menyediakannya.", color = mist, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }
    LazyColumn(state = scroll, modifier = modifier, contentPadding = PaddingValues(horizontal = 27.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp)) {
        items(lines.size, key = { "lyric-$it" }) { i ->
            val line = lines[i]
            val selected = if (active >= 0) i == active else true
            Column(Modifier.fillMaxWidth().clickable(enabled = line.time != null) { line.time?.let { state.seek((it * 1000 / state.duration.coerceAtLeast(1)).toFloat()) } }) {
                val color by animateColorAsState(if (selected) white else mist.copy(alpha = .54f), label = "lyricColor")
                val formatted = remember(line, currentSec, selected) {
                    if (line.words.isEmpty()) null else buildAnnotatedString {
                        line.words.forEach { w ->
                            // Animate only genuine word timestamps; never invent karaoke on plain LRC.
                            val ratio = if (w.end > w.start) ((currentSec - w.start) / (w.end - w.start)).coerceIn(0.0, 1.0) else if (currentSec >= w.start) 1.0 else 0.0
                            val sung = (w.text.length * ratio).roundToInt().coerceIn(0, w.text.length)
                            withStyle(SpanStyle(color = lime, fontWeight = FontWeight.SemiBold)) {
                                append(w.text.take(sung))
                            }
                            withStyle(SpanStyle(color = mist.copy(alpha = if (selected) .88f else .50f), fontWeight = FontWeight.Medium)) {
                                append(w.text.drop(sung))
                            }
                        }
                    }
                }
                if (formatted != null) Text(formatted, fontSize = 24.sp, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
                else Text(line.text, color = color, fontSize = 24.sp, lineHeight = 33.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                state.romanized[i]?.takeIf { state.settings.romanize && it != line.text }?.let {
                    Spacer(Modifier.height(6.dp)); Text(it, color = if (selected) lime.copy(alpha = .86f) else mist.copy(alpha = .45f), fontSize = 13.sp, lineHeight = 19.sp)
                }
                if (line.translation.isNotBlank()) {
                    Spacer(Modifier.height(5.dp)); Text(line.translation, color = mist.copy(alpha = if (selected) .85f else .5f), fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(state: AurelisState) {
    val fraction = if (state.duration > 0) (state.progress.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
    var scrub by remember { mutableFloatStateOf(-1f) }
    Column(Modifier.padding(horizontal = 26.dp)) {
        Slider(value = if (scrub >= 0) scrub else fraction, onValueChange = { scrub = it },
            onValueChangeFinished = { if (scrub >= 0) state.seek(scrub); scrub = -1f },
            colors = SliderDefaults.colors(thumbColor = lime, activeTrackColor = lime, inactiveTrackColor = white.copy(alpha = .14f)),
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(state.progress), color = mist, fontSize = 11.sp)
            Text(formatMs(state.duration), color = mist, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(69.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = state::previous) { Icon(Icons.Default.SkipPrevious, "Sebelumnya", Modifier.size(30.dp), tint = white) }
            Tactile(action = state::togglePlay) {
                Box(Modifier.size(67.dp).clip(CircleShape).background(lime), contentAlignment = Alignment.Center) {
                    if (state.preparing) CircularProgressIndicator(Modifier.size(28.dp), color = bg, strokeWidth = 2.dp)
                    else Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Putar atau jeda", Modifier.size(36.dp), tint = bg)
                }
            }
            IconButton(onClick = state::next) { Icon(Icons.Default.SkipNext, "Berikutnya", Modifier.size(30.dp), tint = white) }
        }
    }
}

private fun formatMs(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1000).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: AurelisState, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF111C1B), contentColor = white,
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 15.dp).size(34.dp, 4.dp).clip(CircleShape).background(white.copy(alpha = .25f))) },
        shape = RoundedCornerShape(topStart = 31.dp, topEnd = 31.dp)) {
        val s = state.settings
        Column(Modifier.fillMaxWidth().fillMaxHeight(.79f).verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 25.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("Pengaturan", fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = -.7.sp)
            Text("Sesuaikan pengalaman, bukan sekadar tampilan.", fontSize = 12.sp, color = mist)
            Spacer(Modifier.height(29.dp))
            SettingsCategory("TAMPILAN", "Glass Ambient")
            SettingsSwitch("Ambient Artwork", "Warna latar mengikuti artwork lagu", s.ambient, { state.update(s.copy(ambient = it)) })
            Spacer(Modifier.height(13.dp))
            SettingsSlider("Intensitas ambient", "Pencahayaan yang halus di belakang konten", s.ambientIntensity,
                enabled = s.ambient, onChange = { state.update(s.copy(ambientIntensity = it)) })
            SettingsSlider("Kepadatan glass", "Menjaga teks tetap terbaca saat album berwarna cerah", s.glassOpacity,
                onChange = { state.update(s.copy(glassOpacity = it)) })
            DividerSoft()
            SettingsCategory("GERAKAN", "Motion")
            SettingsSwitch("Animasi halus", "Transisi capsule, lembar, dan lirik", s.motion, { state.update(s.copy(motion = it)) })
            DividerSoft()
            SettingsCategory("MUSIK", "Sumber Audio")
            Text("Mode pemutaran", color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(AudioMode.AUTO to "Auto", AudioMode.NEWPIPE to "NewPipe", AudioMode.AURELIS to "Server").forEach { (mode, label) ->
                    Tactile(action = { state.update(s.copy(audioMode = mode)) }) {
                        Glass(tint = if (s.audioMode == mode) Color(0xFF527462) else Color(0xFF25332F), opacity = .85f) {
                            Text(label, Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                                color = if (s.audioMode == mode) white else mist, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Auto mencoba NewPipe terlebih dahulu, lalu Aurelis jika diperlukan. Sumber eksternal dapat berubah atau tidak tersedia.",
                color = mist, fontSize = 11.sp, lineHeight = 17.sp)
            DividerSoft()
            SettingsCategory("LIRIK", "Bahasa")
            SettingsSwitch("Romanisasi otomatis", "Untuk Jepang, Korea, dan Mandarin jika server mendukung", s.romanize, { state.update(s.copy(romanize = it)) })
            Spacer(Modifier.height(28.dp))
            Text("AURELIS SYMPHONY  ·  ANDROID 0.1.0 BETA", color = mist.copy(alpha = .65f), fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(31.dp))
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
