package com.jlptdojo.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Anything that can appear as a quiz card. */
sealed interface StudyItem {
    val id: String
    val level: Int
}

data class Word(
    val word: String,
    val reading: String,
    val meaning: String,
    override val level: Int,
) : StudyItem {
    override val id: String = "v:$word|$reading"
    val hasKanji: Boolean = word.any { isKanji(it) }
    val shortMeaning: String = shortenMeaning(meaning)
    val isVerbLike: Boolean = meaning.startsWith("to ")
}

data class Example(
    val word: String,
    val reading: String,
    val meaning: String,
    val level: Int,
)

data class Kanji(
    val char: String,
    override val level: Int,
    val strokes: Int,
    val meanings: List<String>,
    val on: List<String>,
    val kun: List<String>,
    val onExamples: List<Example>,
    val kunExamples: List<Example>,
) : StudyItem {
    override val id: String = "k:$char"
    val meaningText: String = meanings.take(3).joinToString(", ")
    val shortMeaning: String = meanings.take(2).joinToString(", ")
    /** Quiz-friendly readings: main forms first, dictionary dashes removed. */
    val onText: String = quizReadings(on).joinToString("・")
    val kunText: String = quizReadings(kun).joinToString("・") { formatKun(it) }
}

private fun quizReadings(list: List<String>): List<String> {
    val main = list.filter { !it.startsWith("-") && !it.endsWith("-") }
    val pick = (if (main.isNotEmpty()) main else list).map { it.trim('-') }.filter { it.isNotEmpty() }
    return pick.distinct().take(2)
}

fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c == '々'

/** "た.べる" -> "た(べる)" : stem plus okurigana in brackets. */
fun formatKun(r: String): String {
    val i = r.indexOf('.')
    return if (i < 0) r else r.substring(0, i) + "(" + r.substring(i + 1) + ")"
}

fun shortenMeaning(m: String, max: Int = 44): String {
    val parts = m.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return m
    var out = parts[0]
    for (p in parts.drop(1)) {
        if (out.length + 2 + p.length > max) break
        out += ", $p"
    }
    return if (out.length > max + 12) out.take(max).trimEnd() + "…" else out
}

class JlptData(val words: List<Word>, val kanji: List<Kanji>) {
    val kanjiByChar: Map<String, Kanji> = kanji.associateBy { it.char }

    fun wordsFor(levels: Collection<Int>): List<Word> = words.filter { it.level in levels }
    fun kanjiFor(levels: Collection<Int>): List<Kanji> = kanji.filter { it.level in levels }

    companion object {
        suspend fun load(context: Context): JlptData = withContext(Dispatchers.IO) {
            val words = parseWords(readAsset(context, "vocab.json"))
            val kanji = parseKanji(readAsset(context, "kanji.json"))
            JlptData(words, kanji)
        }

        private fun readAsset(context: Context, name: String): String =
            context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

        private fun parseWords(json: String): List<Word> {
            val arr = JSONArray(json)
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Word(o.getString("w"), o.getString("r"), o.getString("m"), o.getInt("l"))
            }
        }

        private fun parseKanji(json: String): List<Kanji> {
            val arr = JSONArray(json)
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Kanji(
                    char = o.getString("k"),
                    level = o.getInt("l"),
                    strokes = o.optInt("s", 0),
                    meanings = o.optJSONArray("m").strings(),
                    on = o.optJSONArray("on").strings(),
                    kun = o.optJSONArray("kun").strings(),
                    onExamples = o.optJSONArray("eo").examples(),
                    kunExamples = o.optJSONArray("ek").examples(),
                )
            }
        }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else List(length()) { getString(it) }

        private fun JSONArray?.examples(): List<Example> =
            if (this == null) emptyList() else List(length()) {
                val o = getJSONObject(it)
                Example(o.getString("w"), o.getString("r"), o.getString("m"), o.getInt("l"))
            }
    }
}
