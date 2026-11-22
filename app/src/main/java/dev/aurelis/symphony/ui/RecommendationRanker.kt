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

    fun pick(
        seed: Song,
        incoming: List<Song>,
        favorites: List<Song>,
        history: List<Song>,
        youtubeHistory: List<Song> = emptyList(),
        youtubeRecommendations: List<Song> = emptyList(),
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
        val ytArtists = youtubeHistory.take(90).groupingBy { artist(it) }.eachCount()
        val ytSeen = youtubeRecommendations.take(140).map { it.key }.toSet()
        val ytShelfOrder = youtubeRecommendations.take(140).mapIndexed { idx, song ->
            song.key to idx
        }.toMap()
        val seenVariants = mutableSetOf(signature(seed))
        val candidates = incoming.asSequence()
            .filter { it.key.isNotBlank() && it.title.isNotBlank() }
            .filter { it.key != seed.key && it.key !in excluded }
            .filter { seenVariants.add(signature(it)) }
            .take(125)
            .toMutableList()
        val output = mutableListOf<Song>()

        fun base(song: Song): Double {
            val tokens = words(song.title).toSet()
            val titleMatch = if (seedTokens.isEmpty() || tokens.isEmpty()) 0.0 else
                seedTokens.intersect(tokens).size.toDouble() /
                    min(seedTokens.size, tokens.size).coerceAtLeast(1)
            val fromSameArtist = artist(song) == artist(seed)
            val recentlyPlayed = historyPos[song.key]?.let { (35 - it) / 35.0 } ?: 0.0
            val repeat = counts[song.key] ?: 0
            // Prioritize a related song, mix favorites, penalize loops and variants.
            // Personalized YouTube Music gets a meaningful boost:
            //  - tracks ranked in account Home shelves
            //  - artists listened to by the account
            //  - account history used for taste, but immediate exact repeats penalized
            val ytArtistPlays = (ytArtists[artist(song)] ?: 0).coerceAtMost(5)
            val ytHome = ytShelfOrder[song.key]?.let { (140 - it) / 140.0 } ?: 0.0
            val ytRecentExact = ytPositions[song.key]?.let { (90 - it) / 90.0 } ?: 0.0
            return (if (fromSameArtist) 2.5 else 0.0) +
                titleMatch * 1.6 +
                (if (song.key in favored) 1.2 else 0.0) +
                (if (song.key in ytSeen) 2.5 + ytHome * 2.1 else 0.0) +
                ytArtistPlays * .74 -
                ytRecentExact * 1.4 -
                recentlyPlayed * 3.2 -
                repeat * 0.7 -
                (if (song.duration in 1L..35L) 1.5 else 0.0)
        }

        while (output.size < maxCount && candidates.isNotEmpty()) {
            val chosen = candidates.maxByOrNull { item ->
                val sameArtistInQueue = output.count { artist(it) == artist(item) }
                val previousSameArtist = output.lastOrNull()?.let { artist(it) == artist(item) } == true
                base(item) - sameArtistInQueue * 2.5 -
                    (if (previousSameArtist) 2.8 else 0.0) +
                    ((item.key.hashCode().toLong() and 0xFFL) / 10000.0)
            } ?: break
            output.add(chosen)
            candidates.remove(chosen)
        }
        return output
    }
}
