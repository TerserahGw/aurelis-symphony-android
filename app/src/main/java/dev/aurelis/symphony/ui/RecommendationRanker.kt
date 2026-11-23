package dev.aurelis.symphony.ui

import dev.aurelis.symphony.data.Song
import java.util.Locale
import kotlin.math.min

/**
 * Transparent local re-ranking of the server's recommendation pool.
 * No hidden user profiling or artificial claims of a machine-learning model.
 */
internal object RecommendationRanker {
    private val variantWords = setOf(
        "official", "audio", "video", "music", "lyric", "lyrics", "visualizer",
        "live", "cover", "slowed", "reverb", "sped", "up", "remix", "version",
        "extended", "edit", "remaster", "remastered", "nightcore", "hour", "hd"
    )
    private val stopWords = variantWords + setOf(
        "the", "a", "an", "and", "of", "to", "ft", "feat", "featuring", "with"
    )
    private fun words(value: String): List<String> =
        value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .split(' ').filter { it.isNotBlank() && it !in stopWords }

    private fun artist(song: Song) = song.artist.lowercase(Locale.ROOT).trim()
    private fun signature(song: Song) =
        artist(song) + "|" + words(song.title).joinToString(" ")


    /**
     * Musical scene/style estimate from explicit metadata and well-known
     * artist catalogs. We do NOT have audio features, BPM or embeddings.
     * Unknown labels stay UNKNOWN rather than inventing a mood.
     */
    private enum class Scene { JAPANESE, KOREAN, INDIAN, INDONESIAN, WESTERN, UNKNOWN }
    private enum class Style { POP, ROCK, ELECTRONIC, RAP, BALLAD, INSTRUMENTAL, UNKNOWN }
    private data class Vibe(val scene: Scene, val style: Style)
    private val jpPop = setOf("yoasobi", "ado", "aimer", "eve", "yorushika",
        "zutomayo", "yama", "vaundy", "kenshi yonezu", "米津玄師", "king gnu",
        "ayase", "ikura", "fujii kaze", "藤井風", "milet", "reol", "natori", "tuki.")
    private val jpRock = setOf("radwimps", "one ok rock", "spyair",
        "bump of chicken", "asian kung-fu generation", "my first story")
    private val krPop = setOf("bts", "blackpink", "newjeans", "twice", "aespa",
        "ive", "le sserafim", "stray kids", "seventeen", "iu", "itzy", "nct")
    private val indian = setOf("arijit singh", "shreya ghoshal", "pritam",
        "atif aslam", "diljit dosanjh", "a.r. rahman", "armaan malik", "neha kakkar")
    private val indo = setOf("tulus", "raisa", "pamungkas", "hindia", "noah",
        "juicy luicy", "mahalini", "tiara andini", "nadin amizah", "lyodra")
    private val westPop = setOf("adele", "taylor swift", "bruno mars",
        "katy perry", "demi lovato", "olivia rodrigo", "ed sheeran", "niki",
        "ruth b.", "dua lipa", "ariana grande", "billie eilish", "the weeknd")
    private val westRock = setOf("linkin park", "metallica", "green day",
        "nirvana", "queen", "my chemical romance", "bring me the horizon")
    private val edm = setOf("alan walker", "avicii", "zedd", "marshmello",
        "martin garrix", "kygo", "calvin harris", "the chainsmokers")
    private val kana = Regex("[\\u3040-\\u30ff]")
    private val hangul = Regex("[\\uac00-\\ud7af]")
    private val devanagari = Regex("[\\u0900-\\u097f]")
    private fun leadArtist(song: Song) = artist(song)
        .substringBefore(" feat").substringBefore(" ft.")
        .substringBefore(" & ").substringBefore(" x ")

    private fun vibe(song: Song): Vibe {
        val lead = leadArtist(song)
        val label = listOf(song.title, song.artist, song.album)
            .joinToString(" ").lowercase(Locale.ROOT)
        val scene = when {
            lead in jpPop || lead in jpRock || kana.containsMatchIn(label) ->
                Scene.JAPANESE
            lead in krPop || hangul.containsMatchIn(label) -> Scene.KOREAN
            lead in indian || devanagari.containsMatchIn(label) ||
                Regex("\\b(bollywood|punjabi|hindi)\\b").containsMatchIn(label) ->
                Scene.INDIAN
            lead in indo -> Scene.INDONESIAN
            lead in westPop || lead in westRock || lead in edm -> Scene.WESTERN
            else -> Scene.UNKNOWN
        }
        val style = when {
            lead in edm || Regex("\\b(edm|techno|trance|house|dubstep)\\b")
                .containsMatchIn(label) -> Style.ELECTRONIC
            lead in jpRock || lead in westRock ||
                Regex("\\b(rock|metal|punk|hardcore|grunge)\\b").containsMatchIn(label) ->
                Style.ROCK
            Regex("\\b(rap|hip hop|hiphop|trap)\\b").containsMatchIn(label) ->
                Style.RAP
            Regex("\\b(acoustic|instrumental|unplugged|piano solo|lofi|lo-fi)\\b")
                .containsMatchIn(label) -> Style.INSTRUMENTAL
            Regex("\\b(ballad|slow jazz)\\b").containsMatchIn(label) ->
                Style.BALLAD
            lead in jpPop || lead in krPop || lead in indo || lead in westPop ||
                Regex("\\b(jpop|j-pop|kpop|k-pop|pop)\\b").containsMatchIn(label) ->
                Style.POP
            else -> Style.UNKNOWN
        }
        return Vibe(scene, style)
    }

    private fun contrasting(a: Style, b: Style): Boolean {
        if (a == Style.UNKNOWN || b == Style.UNKNOWN || a == b) return false
        if ((a == Style.POP && b in setOf(Style.BALLAD, Style.ELECTRONIC)) ||
            (b == Style.POP && a in setOf(Style.BALLAD, Style.ELECTRONIC))) return false
        return true
    }

    fun clashesWith(seed: Song, candidate: Song): Boolean {
        if (leadArtist(seed) == leadArtist(candidate)) return false
        val a = vibe(seed)
        val b = vibe(candidate)
        return (a.scene != Scene.UNKNOWN && b.scene != Scene.UNKNOWN &&
            a.scene != b.scene) || contrasting(a.style, b.style)
    }

    private fun relatedEnough(
        seed: Song, candidate: Song,
        directRadio: Boolean, seedServer: Boolean, accountTaste: Boolean
    ): Boolean {
        if (!candidate.isPlayableTrack() || seed.key == candidate.key) return false
        if (leadArtist(seed) == leadArtist(candidate)) return true
        if (clashesWith(seed, candidate)) return false
        val a = vibe(seed)
        val b = vibe(candidate)
        if (seed.album.isNotBlank() &&
            seed.album.equals(candidate.album, ignoreCase = true)) return true
        // Specific radio endpoints are stronger relatedness evidence than
        // generic Home playlists/favorites, whose genres may be very broad.
        if (directRadio) return true
        if (seedServer && a.scene != Scene.UNKNOWN &&
            b.scene != Scene.UNKNOWN && a.scene == b.scene) return true
        if (seedServer && a.style != Style.UNKNOWN && a.style == b.style &&
            accountTaste) return true
        // Global "recommended for you" must not inject other genres just
        // because the listener has played both genres on different days.
        return accountTaste && a.scene != Scene.UNKNOWN && b.scene == a.scene &&
            !contrasting(a.style, b.style)
    }

    fun pick(
        seed: Song,
        incoming: List<Song>,
        favorites: List<Song>,
        history: List<Song>,
        youtubeHistory: List<Song> = emptyList(),
        youtubeRecommendations: List<Song> = emptyList(),
        seedRadio: List<Song> = emptyList(),
        seedServer: List<Song> = emptyList(),
        excluded: Set<String>,
        maxCount: Int
    ): List<Song> {
        if (maxCount <= 0) return emptyList()
        val seedTokens = words(seed.title).toSet()
        val recent = history.take(35)
        val counts = recent.groupingBy { it.key }.eachCount()
        val favored = favorites.map { it.key }.toSet()
        val historyPos = recent.mapIndexed { i, item -> item.key to i }.toMap()
        // The signed-in user's own YouTube Music feed and listening history
        // are signals; no generic title-matching-only pseudo-personalization.
        val ytPositions = youtubeHistory.take(90).mapIndexed { idx, song ->
            song.key to idx
        }.toMap()
        val ytArtists = youtubeHistory.take(90).groupingBy(::leadArtist).eachCount()
        val ytSeen = youtubeRecommendations.take(140).map { it.key }.toSet()
        val hasAccountTaste = ytSeen.isNotEmpty() || youtubeHistory.isNotEmpty()
        val ytShelfOrder = youtubeRecommendations.take(140).mapIndexed { idx, song ->
            song.key to idx
        }.toMap()
        val radioPosition = seedRadio.take(70).mapIndexed { index, song ->
            song.key to index
        }.toMap()
        val serverKeys = seedServer.take(100).map { it.key }.toSet()
        val vibeSeed = vibe(seed)
        val seenVariants = mutableSetOf(signature(seed))
        val candidates = incoming.asSequence()
            .filter { it.key.isNotBlank() && it.title.isNotBlank() }
            .filter { it.key != seed.key && it.key !in excluded }
            .filter { seenVariants.add(signature(it)) }
            .filter { song -> relatedEnough(seed, song,
                directRadio = song.key in radioPosition,
                seedServer = song.key in serverKeys,
                accountTaste = song.key in ytSeen ||
                    (ytArtists[leadArtist(song)] ?: 0) > 0 ||
                    song.key in favored
            ) }
            .take(200)
            .toMutableList()
        val output = mutableListOf<Song>()

        fun base(song: Song): Double {
            val tokens = words(song.title).toSet()
            val titleMatch = if (seedTokens.isEmpty() || tokens.isEmpty()) 0.0 else
                seedTokens.intersect(tokens).size.toDouble() /
                    min(seedTokens.size, tokens.size).coerceAtLeast(1)
            val fromSameArtist = leadArtist(song) == leadArtist(seed)
            val itemVibe = vibe(song)
            val sameScene = vibeSeed.scene != Scene.UNKNOWN &&
                vibeSeed.scene == itemVibe.scene
            val sameStyle = vibeSeed.style != Style.UNKNOWN &&
                vibeSeed.style == itemVibe.style
            val radioBoost = radioPosition[song.key]?.let { position ->
                12.0 - position.coerceAtMost(60) * .14
            } ?: 0.0
            val recentlyPlayed = historyPos[song.key]?.let { (35 - it) / 35.0 } ?: 0.0
            val repeat = counts[song.key] ?: 0
            // Prioritize a related song, mix favorites, penalize loops and variants.
            // Personalized YouTube Music gets a meaningful boost:
            //  - tracks ranked in account Home shelves
            //  - artists listened to by the account
            //  - account history used for taste, but immediate exact repeats penalized
            val ytArtistPlays = (ytArtists[leadArtist(song)] ?: 0).coerceAtMost(5)
            val ytHome = ytShelfOrder[song.key]?.let { (140 - it) / 140.0 } ?: 0.0
            val ytRecentExact = ytPositions[song.key]?.let { (90 - it) / 90.0 } ?: 0.0
            return (if (fromSameArtist) 8.0 else 0.0) +
                radioBoost +
                (if (song.key in serverKeys) 2.6 else 0.0) +
                (if (sameScene) 4.8 else 0.0) +
                (if (sameStyle) 3.2 else 0.0) +
                titleMatch * .7 +
                (if (song.key in favored) 1.2 else 0.0) +
                // The account Home shelves/history are weighted well above
                // anonymous search/catalog matches, so recommendations feel
                // like the actual signed-in YouTube Music taste.
                (if (song.key in ytSeen) 2.4 + ytHome * 1.3 else 0.0) +
                ytArtistPlays * .7 +
                (if (hasAccountTaste && song.isYouTube) .4 else 0.0) -
                ytRecentExact * 2.1 -
                recentlyPlayed * 3.2 -
                repeat * 0.7 -
                (if (song.duration in 1L..35L) 1.5 else 0.0)
        }

        while (output.size < maxCount && candidates.isNotEmpty()) {
            val chosen = candidates.maxByOrNull { item ->
                val sameArtistInQueue = output.count { leadArtist(it) == leadArtist(item) }
                val previousSameArtist = output.lastOrNull()?.let { leadArtist(it) == leadArtist(item) } == true
                val abruptJump = output.lastOrNull()?.let {
                    clashesWith(it, item)
                } ?: false
                base(item) - sameArtistInQueue * 2.5 -
                    (if (previousSameArtist) 2.8 else 0.0) -
                    (if (abruptJump) 40.0 else 0.0) +
                    ((item.key.hashCode().toLong() and 0xFFL) / 10000.0)
            } ?: break
            // Do not force a genre jump to fill all five slots.
            if (output.lastOrNull()?.let { clashesWith(it, chosen) } == true) {
                candidates.remove(chosen)
                continue
            }
            output.add(chosen)
            candidates.remove(chosen)
        }
        return output
    }
}
