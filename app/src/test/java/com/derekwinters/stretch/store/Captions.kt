package com.derekwinters.stretch.store

import java.io.File

/**
 * One shot's caption (STORE-SHOT-007, STORE-SHOT-012), or the feature graphic's text
 * (STORE-GRAPHIC-003). [headlineCites] and [supportingCites] are the requirement IDs a line's
 * trailing `<!-- ID, ID -->` tag names; the tag is not part of the text.
 */
data class Caption(
    val id: String,
    val headline: String,
    val supporting: String,
    val headlineCites: List<String> = emptyList(),
    val supportingCites: List<String> = emptyList(),
)

/**
 * STORE-SHOT-007 / STORE-GRAPHIC-003: reads the captions from docs/store/captions.md, the only
 * place their text lives. Each entry is a `## STORE-SHOT-nnn` (or `## STORE-GRAPHIC-nnn`) heading
 * followed by a `Headline:` line and a `Supporting:` line, each of which may end with one
 * `<!-- ID, ID -->` tag; anything before the first entry is ignored.
 */
object Captions {
    private const val RELATIVE_PATH = "docs/store/captions.md"
    private val heading = Regex("""^##\s+(STORE-(?:SHOT|GRAPHIC)-\d{3})\s*$""")
    private val tagAtEnd = Regex("""^(.*?)\s*<!--(.*?)-->\s*$""")

    /** The captions in file order. */
    fun load(): List<Caption> = parse(repoFile(RELATIVE_PATH).readText(Charsets.UTF_8))

    /** The caption for [shot]; fails if the file has none. */
    fun of(shot: StoreShot): Caption =
        load().firstOrNull { it.id == shot.id } ?: error("${shot.id} has no caption in $RELATIVE_PATH")

    /** STORE-GRAPHIC-003: the feature graphic's headline and supporting line. */
    fun featureGraphic(): Caption =
        load().firstOrNull { it.id == FeatureGraphicSpec.TEXT_ID }
            ?: error("${FeatureGraphicSpec.TEXT_ID} has no entry in $RELATIVE_PATH")

    /** A line's text and the IDs its trailing tag cites (none without a tag). */
    private fun splitTag(id: String, value: String): Pair<String, List<String>> {
        val match = tagAtEnd.matchEntire(value) ?: run {
            check("<!--" !in value && "-->" !in value) { "$id: a tag must be one `<!-- ID, ID -->` at the end of the line: $value" }
            return value to emptyList()
        }
        val text = match.groupValues[1].trim()
        val ids = match.groupValues[2].split(',').map { it.trim() }
        check("<!--" !in text && "-->" !in text && ids.all { SpecIds.ID.matches(it) }) {
            "$id: a tag must be one `<!-- ID, ID -->` at the end of the line: $value"
        }
        return text to ids
    }

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
            val (headlineText, headlineCites) = splitTag(current, headline!!)
            val (supportingText, supportingCites) = splitTag(current, supporting!!)
            check(headlineText.isNotBlank()) { "$current has no Headline: text" }
            check(supportingText.isNotBlank()) { "$current has no Supporting: text" }
            captions += Caption(current, headlineText, supportingText, headlineCites, supportingCites)
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

}

/**
 * A file or folder of the repository, by its path from the root. Unit tests run with the module
 * directory (app/) as the working directory; the directories above it are tried too, so the
 * path is found from the repository root as well.
 */
fun repoFile(relativePath: String): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        val candidate = File(dir, relativePath)
        if (candidate.exists()) return candidate
        dir = dir.parentFile
    }
    error("$relativePath not found above ${File("").absolutePath}")
}

/**
 * STORE-GRAPHIC-003: the requirement IDs the spec pages define, by the rule the listing text
 * follows (STORE-LIST-003, `.github/scripts/store_listing.py`): a top-level list item opening
 * with the bold ID defines it, and a definition whose text opens with `_Retired._` retires it.
 */
object SpecIds {
    val ID = Regex("""[A-Z]+(?:-[A-Z]+)*-[0-9]{3}""")
    private val definition = Regex("""^- \*\*(${ID.pattern})\*\*(.*)$""")

    /** Every defined ID, mapped to whether it is retired. */
    fun load(): Map<String, Boolean> = repoFile("docs/spec").listFiles { f -> f.name.endsWith(".md") && f.name != "README.md" }!!
        .sortedBy { it.name }
        .flatMap { page -> page.readLines(Charsets.UTF_8).mapNotNull { definition.matchEntire(it) } }
        .associate { it.groupValues[1] to it.groupValues[2].trim().startsWith("_Retired._") }

    /** Fails unless every one of [ids] is defined and not retired. */
    fun requireCitable(owner: String, ids: List<String>, defined: Map<String, Boolean> = load()) {
        for (id in ids) {
            when (defined[id]) {
                null -> throw AssertionError("$owner cites $id, which no spec page defines (STORE-GRAPHIC-003)")
                true -> throw AssertionError("$owner cites $id, which the spec has retired (STORE-GRAPHIC-003)")
                false -> Unit
            }
        }
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
