package com.jlptdojo.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.jlptdojo.app.game.GameResult
import org.json.JSONObject
import java.time.LocalDate

data class Rank(val title: String, val jp: String, val minXp: Int, val nextXp: Int?)

private val RANKS = listOf(
    Triple(0, "Beginner", "初心者"),
    Triple(300, "Apprentice", "見習い"),
    Triple(1_000, "Student", "学生"),
    Triple(2_500, "Adept", "中級者"),
    Triple(5_000, "Expert", "上級者"),
    Triple(10_000, "Master", "達人"),
    Triple(20_000, "Sensei", "先生"),
    Triple(40_000, "Grandmaster", "名人"),
)

fun rankFor(xp: Int): Rank {
    val i = RANKS.indexOfLast { xp >= it.first }.coerceAtLeast(0)
    val r = RANKS[i]
    return Rank(r.second, r.third, r.first, RANKS.getOrNull(i + 1)?.first)
}

/** Everything the app remembers between sessions, stored in SharedPreferences. */
class Progress(context: Context) {
    private val sp = context.getSharedPreferences("jlpt_dojo_progress", Context.MODE_PRIVATE)

    var xp by mutableIntStateOf(sp.getInt("xp", 0))
        private set
    var gamesPlayed by mutableIntStateOf(sp.getInt("games", 0))
        private set
    var totalAnswered by mutableIntStateOf(sp.getInt("answered", 0))
        private set
    var totalCorrect by mutableIntStateOf(sp.getInt("correct", 0))
        private set
    private var streakRaw by mutableIntStateOf(sp.getInt("streak", 0))
    private var lastDay by mutableLongStateOf(sp.getLong("lastDay", -10L))
    var showReadingHint by mutableStateOf(sp.getBoolean("hint", true))
        private set
    var haptics by mutableStateOf(sp.getBoolean("haptics", true))
        private set

    val selectedLevels = mutableStateListOf<Int>()
    val learned = mutableStateMapOf<String, Boolean>()
    val mistakes = mutableStateMapOf<String, Int>()

    init {
        val lv = (sp.getString("levels", "5") ?: "5").split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..5 }
        selectedLevels.addAll(if (lv.isEmpty()) listOf(5) else lv)
        (sp.getStringSet("learned", emptySet()) ?: emptySet()).forEach { learned[it] = true }
        try {
            val o = JSONObject(sp.getString("mistakes", "{}") ?: "{}")
            o.keys().forEach { k -> mistakes[k] = o.getInt(k) }
        } catch (_: Exception) {
        }
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    /** Days in a row with at least one finished game (0 if the chain broke). */
    val streak: Int get() = if (lastDay >= today() - 1) streakRaw else 0
    val playedToday: Boolean get() = lastDay == today()

    val rank: Rank get() = rankFor(xp)
    val accuracy: Float get() = if (totalAnswered == 0) 0f else totalCorrect.toFloat() / totalAnswered

    fun toggleLevel(level: Int) {
        if (level in selectedLevels) {
            if (selectedLevels.size > 1) selectedLevels.remove(level)
        } else {
            selectedLevels.add(level)
        }
        sp.edit { putString("levels", selectedLevels.sorted().joinToString(",")) }
    }

    fun updateHint(v: Boolean) {
        showReadingHint = v
        sp.edit { putBoolean("hint", v) }
    }

    fun updateHaptics(v: Boolean) {
        haptics = v
        sp.edit { putBoolean("haptics", v) }
    }

    fun isLearned(kanji: String): Boolean = learned[kanji] == true

    fun setLearned(kanji: String, value: Boolean) {
        if (value) learned[kanji] = true else learned.remove(kanji)
        sp.edit { putStringSet("learned", HashSet(learned.keys)) }
    }

    fun weakCount(ids: Collection<String>): Int = ids.count { (mistakes[it] ?: 0) > 0 }

    fun recordAnswer(itemId: String, correct: Boolean) {
        val now = mistakes[itemId] ?: 0
        if (correct) {
            if (now > 1) mistakes[itemId] = now - 1 else if (now == 1) mistakes.remove(itemId)
        } else {
            mistakes[itemId] = (now + 2).coerceAtMost(9)
        }
        saveMistakes()
    }

    fun clearMistakes() {
        mistakes.clear()
        saveMistakes()
    }

    private fun saveMistakes() {
        val o = JSONObject()
        mistakes.forEach { (k, v) -> o.put(k, v) }
        sp.edit { putString("mistakes", o.toString()) }
    }

    fun bestFor(key: String): Int = sp.getInt(key, 0)

    /** Saves a finished game. Returns true when it beats the previous best for this setup. */
    fun recordGame(r: GameResult): Boolean {
        xp += r.xp
        gamesPlayed += 1
        totalAnswered += r.answered
        totalCorrect += r.correct
        val t = today()
        if (lastDay != t) {
            streakRaw = if (lastDay == t - 1) streakRaw + 1 else 1
            lastDay = t
        }
        val key = r.config.bestKey
        val best = sp.getInt(key, 0)
        val newBest = r.correct > best && r.config.itemIds == null
        sp.edit {
            putInt("xp", xp)
            putInt("games", gamesPlayed)
            putInt("answered", totalAnswered)
            putInt("correct", totalCorrect)
            putInt("streak", streakRaw)
            putLong("lastDay", lastDay)
            if (newBest) putInt(key, r.correct)
        }
        return newBest
    }
}
