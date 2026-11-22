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
        excluded: Set<String>,
        maxCount: Int
    ): List<Song> {
        if (maxCount <= 0) return emptyList()
        val seedTokens = words(seed.title).toSet()
        val recent = history.take(35)
        val counts = recent.groupingBy { it.key }.eachCount()
        val favored = favorites.map { it.key }.toSet()
        val historyPos = recent.mapIndexed { i, item -> item.key to i }.toMap()
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
            return (if (fromSameArtist) 3.1 else 0.0) +
                titleMatch * 2.2 +
                (if (song.key in favored) 1.15 else 0.0) -
                recentlyPlayed * 3.2 -
                repeat * 0.7 -
                (if (song.duration in 1..35) 1.5 else 0.0)
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
