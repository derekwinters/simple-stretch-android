package com.derekwinters.stretch.store

import java.io.File

/** One shot's caption (STORE-SHOT-007, STORE-SHOT-012). */
data class Caption(val id: String, val headline: String, val supporting: String)

/**
 * STORE-SHOT-007: reads the captions from docs/store/captions.md, the only place their text
 * lives. Each entry is a `## STORE-SHOT-nnn` heading followed by a `Headline:` line and a
 * `Supporting:` line; anything before the first entry is ignored.
 */
object Captions {
    private const val RELATIVE_PATH = "docs/store/captions.md"
    private val heading = Regex("""^##\s+(STORE-SHOT-\d{3})\s*$""")

    /** The captions in file order. */
    fun load(): List<Caption> = parse(file().readText(Charsets.UTF_8))

    /** The caption for [shot]; fails if the file has none. */
    fun of(shot: StoreShot): Caption =
        load().firstOrNull { it.id == shot.id } ?: error("${shot.id} has no caption in $RELATIVE_PATH")

    fun parse(markdown: String): List<Caption> {
        val captions = mutableListOf<Caption>()
        var id: String? = null
        var headline: String? = null
        var supporting: String? = null

        fun finish() {
            val current = id ?: return
            check(!headline.isNullOrBlank()) { "$current has no Headline: line" }
            check(!supporting.isNullOrBlank()) { "$current has no Supporting: line" }
            check(captions.none { it.id == current }) { "$current is listed twice" }
            captions += Caption(current, headline!!, supporting!!)
        }

        for (raw in markdown.lines()) {
            val line = raw.trim()
            val match = heading.matchEntire(line)
            when {
                match != null -> {
                    finish()
                    id = match.groupValues[1]
                    headline = null
                    supporting = null
                }
                id == null -> Unit
                line.startsWith("Headline:") -> {
                    check(headline == null) { "$id has two Headline: lines" }
                    headline = line.removePrefix("Headline:").trim()
                }
                line.startsWith("Supporting:") -> {
                    check(supporting == null) { "$id has two Supporting: lines" }
                    supporting = line.removePrefix("Supporting:").trim()
                }
            }
        }
        finish()
        return captions
    }

    /**
     * Unit tests run with the module directory (app/) as the working directory; the directories
     * above it are tried too, so the file is found from the repository root as well.
     */
    private fun file(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, RELATIVE_PATH)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("$RELATIVE_PATH not found above ${File("").absolutePath}")
    }
}

/** Rendered line counts of a caption's two parts (STORE-SHOT-012). */
data class CaptionLines(val headline: Int, val supporting: Int)

/** STORE-SHOT-012 / STORE-SHOT-014: the caption's line limits. */
object CaptionFit {
    const val MAX_HEADLINE_LINES = 3
    const val MAX_SUPPORTING_LINES = 2

    /**
     * STORE-SHOT-014 invariant: a caption over either limit fails the test. Nothing shrinks the
     * text or moves the frame to make it fit; the caption must be shortened.
     */
    fun require(caption: Caption, lines: CaptionLines) {
        if (lines.headline > MAX_HEADLINE_LINES) {
            throw AssertionError(
                "${caption.id} headline is ${lines.headline} lines as rendered, at most " +
                    "$MAX_HEADLINE_LINES fit (STORE-SHOT-012); shorten it in docs/store/captions.md: " +
                    "\"${caption.headline}\"",
            )
        }
        if (lines.supporting > MAX_SUPPORTING_LINES) {
            throw AssertionError(
                "${caption.id} supporting line is ${lines.supporting} lines as rendered, at most " +
                    "$MAX_SUPPORTING_LINES fit (STORE-SHOT-012); shorten it in docs/store/captions.md: " +
                    "\"${caption.supporting}\"",
            )
        }
    }
}
