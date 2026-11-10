package com.jlptdojo.app.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlptdojo.app.data.JlptData
import com.jlptdojo.app.data.Kanji
import com.jlptdojo.app.data.StudyItem
import com.jlptdojo.app.data.Word
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class GameKind { VOCAB, KANJI }

enum class QuizMode(val kind: GameKind, val title: String, val subtitle: String, val badge: String) {
    WORD_TO_MEANING(GameKind.VOCAB, "Word → Meaning", "See the Japanese word, pick its meaning", "意"),
    MEANING_TO_WORD(GameKind.VOCAB, "Meaning → Word", "See the English, pick the Japanese word", "語"),
    WORD_TO_READING(GameKind.VOCAB, "Word → Reading", "Pick the correct kana reading", "読"),
    KANJI_TO_MEANING(GameKind.KANJI, "Kanji → Meaning", "What does this character mean?", "意"),
    KANJI_TO_ON(GameKind.KANJI, "Kanji → On'yomi", "Chinese-derived reading (カタカナ)", "音"),
    KANJI_TO_KUN(GameKind.KANJI, "Kanji → Kun'yomi", "Native Japanese reading (ひらがな)", "訓"),
    MEANING_TO_KANJI(GameKind.KANJI, "Meaning → Kanji", "Spot the right character", "字"),
}

enum class PlayStyle(val title: String, val desc: String) {
    CLASSIC("Classic", "Answer every card at your own pace"),
    SURVIVAL("Survival", "3 hearts. Three mistakes and you're out"),
    TIME_ATTACK("Time Attack", "90 seconds on the clock. Go fast!"),
}

const val TIME_ATTACK_TENTHS = 900

data class GameConfig(
    val mode: QuizMode,
    val levels: List<Int>,
    /** Number of words/kanji in this game; 0 means every eligible item. */
    val deckSize: Int,
    val style: PlayStyle,
    val weakOnly: Boolean = false,
    /** When set, the game uses exactly these items (e.g. "retry missed"). */
    val itemIds: List<String>? = null,
    val seed: Long = System.nanoTime(),
) {
    val bestKey: String
        get() = "best_${mode.name}_${style.name}_${levels.sorted().joinToString("")}_$deckSize"
}

data class Option(val main: String, val sub: String? = null)

data class Question(
    val itemId: String,
    val level: Int,
    val instruction: String,
    val prompt: String,
    val promptSub: String?,
    val promptJapanese: Boolean,
    val options: List<Option>,
    val correctIndex: Int,
    val optionsJapanese: Boolean,
    val optionsLarge: Boolean,
    val answerTitle: String,
    val answerReading: String,
    val answerMeaning: String,
    /** Kana to read aloud with text-to-speech, if any. */
    val speakText: String?,
)

class QuizEngine(
    data: JlptData,
    private val config: GameConfig,
    mistakes: Map<String, Int>,
    private val showReadingHint: Boolean,
) {
    private val rnd = Random(config.seed)

    /** Items used to make wrong answers. Widened to every level when the selection is tiny. */
    private val distractorWords: List<Word>
    private val distractorKanji: List<Kanji>

    val deck: List<StudyItem>

    init {
        val eligible = eligible(data, config.mode, config.levels)
        val everywhere = eligible(data, config.mode, listOf(1, 2, 3, 4, 5))
        val distractorBase = if (eligible.size >= 40) eligible else everywhere
        distractorWords = distractorBase.filterIsInstance<Word>()
        distractorKanji = distractorBase.filterIsInstance<Kanji>()

        val base: List<StudyItem> = when {
            config.itemIds != null -> {
                val wanted = config.itemIds.toSet()
                everywhere.filter { it.id in wanted }
            }
            config.weakOnly -> eligible.filter { (mistakes[it.id] ?: 0) > 0 }
            else -> eligible
        }
        // Shuffle, but let items you got wrong before surface earlier.
        val ordered = base
            .map { item -> item to rnd.nextDouble() * (if ((mistakes[item.id] ?: 0) > 0) 0.45 else 1.0) }
            .sortedBy { it.second }
            .map { it.first }
        deck = if (config.deckSize > 0) ordered.take(config.deckSize) else ordered
    }

    fun question(index: Int): Question = build(deck[index])

    private fun build(item: StudyItem): Question = when (config.mode) {
        QuizMode.WORD_TO_MEANING -> {
            val w = item as Word
            val pool = distractorWords.filter { it.shortMeaning != w.shortMeaning && it.word != w.word }
            val wrong = pickTwo(w.shortMeaning, pool, { it.shortMeaning }) { meaningScore(w, it) }
            assemble(
                item = w,
                instruction = "What does this mean?",
                prompt = w.word,
                promptSub = if (showReadingHint && w.reading != w.word) w.reading else null,
                promptJapanese = true,
                correct = Option(w.shortMeaning),
                wrong = wrong.map { Option(it.shortMeaning) },
                optionsJapanese = false,
                optionsLarge = false,
            )
        }
        QuizMode.MEANING_TO_WORD -> {
            val w = item as Word
            val pool = distractorWords.filter { it.shortMeaning != w.shortMeaning && it.word != w.word }
            val wrong = pickTwo(w.word, pool, { it.word }) { meaningScore(w, it) }
            assemble(
                item = w,
                instruction = "Which word means…",
                prompt = w.shortMeaning,
                promptSub = null,
                promptJapanese = false,
                correct = wordOption(w),
                wrong = wrong.map { wordOption(it) },
                optionsJapanese = true,
                optionsLarge = false,
            )
        }
        QuizMode.WORD_TO_READING -> {
            val w = item as Word
            val pool = distractorWords.filter { it.reading != w.reading }
            val wrong = pickTwo(w.reading, pool, { it.reading }) { readingScore(w.reading, it.reading) }
            assemble(
                item = w,
                instruction = "How do you read this?",
                prompt = w.word,
                promptSub = null,
                promptJapanese = true,
                correct = Option(w.reading),
                wrong = wrong.map { Option(it.reading) },
                optionsJapanese = true,
                optionsLarge = false,
            )
        }
        QuizMode.KANJI_TO_MEANING -> {
            val k = item as Kanji
            val first = k.meanings.firstOrNull().orEmpty()
            val pool = distractorKanji.filter { c ->
                c.char != k.char && c.shortMeaning != k.shortMeaning && c.meanings.none { it.equals(first, true) }
            }
            val wrong = pickTwo(k.shortMeaning, pool, { it.shortMeaning }) { 1.0 - abs(it.strokes - k.strokes) / 10.0 }
            assemble(
                item = k,
                instruction = "What does this kanji mean?",
                prompt = k.char,
                promptSub = null,
                promptJapanese = true,
                correct = Option(k.shortMeaning),
                wrong = wrong.map { Option(it.shortMeaning) },
                optionsJapanese = false,
                optionsLarge = false,
            )
        }
        QuizMode.KANJI_TO_ON -> {
            val k = item as Kanji
            val pool = distractorKanji.filter { c -> c.char != k.char && c.on.isNotEmpty() && c.on.none { it in k.on } }
            val wrong = pickTwo(k.onText, pool, { it.onText }) { readingScore(k.onText, it.onText) }
            assemble(
                item = k,
                instruction = "Pick the on'yomi (音読み)",
                prompt = k.char,
                promptSub = if (showReadingHint) k.shortMeaning else null,
                promptJapanese = true,
                correct = Option(k.onText),
                wrong = wrong.map { Option(it.onText) },
                optionsJapanese = true,
                optionsLarge = false,
            )
        }
        QuizMode.KANJI_TO_KUN -> {
            val k = item as Kanji
            val pool = distractorKanji.filter { c -> c.char != k.char && c.kun.isNotEmpty() && c.kun.none { it in k.kun } }
            val wrong = pickTwo(k.kunText, pool, { it.kunText }) { readingScore(k.kunText, it.kunText) }
            assemble(
                item = k,
                instruction = "Pick the kun'yomi (訓読み)",
                prompt = k.char,
                promptSub = if (showReadingHint) k.shortMeaning else null,
                promptJapanese = true,
                correct = Option(k.kunText),
                wrong = wrong.map { Option(it.kunText) },
                optionsJapanese = true,
                optionsLarge = false,
            )
        }
        QuizMode.MEANING_TO_KANJI -> {
            val k = item as Kanji
            val first = k.meanings.firstOrNull().orEmpty()
            val pool = distractorKanji.filter { c ->
                c.char != k.char && c.shortMeaning != k.shortMeaning && c.meanings.none { it.equals(first, true) }
            }
            // Similar stroke counts make visually confusable choices.
            val wrong = pickTwo(k.char, pool, { it.char }) { 1.0 - abs(it.strokes - k.strokes) / 3.0 }
            assemble(
                item = k,
                instruction = "Which kanji means…",
                prompt = k.meaningText,
                promptSub = null,
                promptJapanese = false,
                correct = Option(k.char),
                wrong = wrong.map { Option(it.char) },
                optionsJapanese = true,
                optionsLarge = true,
            )
        }
    }

    private fun wordOption(w: Word) = Option(w.word, if (w.reading != w.word) w.reading else null)

    private fun meaningScore(answer: Word, cand: Word): Double {
        var s = 0.0
        if (cand.isVerbLike == answer.isVerbLike) s += 3.0
        if (cand.level == answer.level) s += 1.0
        s -= abs(cand.shortMeaning.length - answer.shortMeaning.length) / 12.0
        return s
    }

    private fun readingScore(answer: String, cand: String): Double {
        var s = 0.0
        if (cand.length == answer.length) s += 2.0
        if (cand.isNotEmpty() && answer.isNotEmpty() && cand[0] == answer[0]) s += 1.5
        if (cand.isNotEmpty() && answer.isNotEmpty() && cand.last() == answer.last()) s += 0.5
        s -= abs(cand.length - answer.length) * 0.5
        return s
    }

    /** Sample candidates at random, then keep the two most plausible ones with distinct text. */
    private fun <T> pickTwo(answerKey: String, pool: List<T>, key: (T) -> String, score: (T) -> Double): List<T> {
        if (pool.isEmpty()) return emptyList()
        val sample = List(min(60, pool.size * 2)) { pool[rnd.nextInt(pool.size)] }
            .map { it to score(it) + rnd.nextDouble() * 1.5 }
            .sortedByDescending { it.second }
            .map { it.first }
        val used = mutableSetOf(answerKey)
        val out = mutableListOf<T>()
        for (c in sample) {
            val k = key(c)
            if (k.isNotBlank() && used.add(k)) out += c
            if (out.size == 2) return out
        }
        for (c in pool.shuffled(rnd)) {
            val k = key(c)
            if (k.isNotBlank() && used.add(k)) out += c
            if (out.size == 2) break
        }
        return out
    }

    private fun assemble(
        item: StudyItem,
        instruction: String,
        prompt: String,
        promptSub: String?,
        promptJapanese: Boolean,
        correct: Option,
        wrong: List<Option>,
        optionsJapanese: Boolean,
        optionsLarge: Boolean,
    ): Question {
        val all = (listOf(correct to true) + wrong.map { it to false }).shuffled(rnd)
        val (title, reading, meaning, speak) = when (item) {
            is Word -> Reveal(item.word, if (item.reading != item.word) item.reading else "", item.meaning, item.reading)
            is Kanji -> Reveal(
                item.char,
                listOfNotNull(
                    item.on.takeIf { it.isNotEmpty() }?.let { "音 " + it.take(3).joinToString("・") },
                    item.kun.takeIf { it.isNotEmpty() }?.let { "訓 " + item.kun.take(3).joinToString("・") { r -> com.jlptdojo.app.data.formatKun(r) } },
                ).joinToString("   "),
                item.meaningText,
                item.kun.firstOrNull()?.replace(".", "")?.replace("-", "") ?: item.on.firstOrNull(),
            )
        }
        return Question(
            itemId = item.id,
            level = item.level,
            instruction = instruction,
            prompt = prompt,
            promptSub = promptSub,
            promptJapanese = promptJapanese,
            options = all.map { it.first },
            correctIndex = all.indexOfFirst { it.second },
            optionsJapanese = optionsJapanese,
            optionsLarge = optionsLarge,
            answerTitle = title,
            answerReading = reading,
            answerMeaning = meaning,
            speakText = speak,
        )
    }

    private data class Reveal(val title: String, val reading: String, val meaning: String, val speak: String?)

    companion object {
        fun eligible(data: JlptData, mode: QuizMode, levels: List<Int>): List<StudyItem> = when (mode) {
            QuizMode.WORD_TO_MEANING, QuizMode.MEANING_TO_WORD -> data.wordsFor(levels)
            QuizMode.WORD_TO_READING -> data.wordsFor(levels).filter { it.hasKanji && it.reading != it.word }
            QuizMode.KANJI_TO_MEANING, QuizMode.MEANING_TO_KANJI -> data.kanjiFor(levels)
            QuizMode.KANJI_TO_ON -> data.kanjiFor(levels).filter { it.on.isNotEmpty() }
            QuizMode.KANJI_TO_KUN -> data.kanjiFor(levels).filter { it.kun.isNotEmpty() }
        }
    }
}

/** Live state of one game, observed by the quiz screen. */
class GameSession(val config: GameConfig, private val engine: QuizEngine) {
    val total: Int = engine.deck.size

    var index by mutableIntStateOf(0)
        private set
    var question by mutableStateOf(engine.question(0))
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    var correctCount by mutableIntStateOf(0)
        private set
    var answered by mutableIntStateOf(0)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var bestCombo by mutableIntStateOf(0)
        private set
    var xp by mutableIntStateOf(0)
        private set
    var lastGain by mutableIntStateOf(0)
        private set
    var lives by mutableIntStateOf(if (config.style == PlayStyle.SURVIVAL) 3 else -1)
        private set
    var timeLeftTenths by mutableIntStateOf(if (config.style == PlayStyle.TIME_ATTACK) TIME_ATTACK_TENTHS else 0)
        private set
    var finished by mutableStateOf(false)
        private set

    var recorded = false
    val missed = mutableStateListOf<Question>()

    val wasCorrect: Boolean? get() = selected?.let { it == question.correctIndex }

    /** Returns true if the pick was correct. */
    fun answer(i: Int): Boolean {
        if (selected != null || finished) return false
        selected = i
        answered++
        val ok = i == question.correctIndex
        if (ok) {
            correctCount++
            combo++
            bestCombo = max(bestCombo, combo)
            lastGain = 10 + min(combo, 10) * 2 + (if (config.style == PlayStyle.TIME_ATTACK) 3 else 0)
            xp += lastGain
        } else {
            combo = 0
            lastGain = 0
            missed += question
            if (lives > 0) lives--
        }
        return ok
    }

    fun next() {
        if (finished) return
        if (lives == 0 || index + 1 >= total) {
            finish()
            return
        }
        index++
        question = engine.question(index)
        selected = null
    }

    fun tick() {
        if (finished || config.style != PlayStyle.TIME_ATTACK) return
        if (timeLeftTenths > 0) timeLeftTenths--
        if (timeLeftTenths == 0) finish()
    }

    fun finish() {
        if (finished) return
        // Bonuses for finishing strong.
        if (answered == total && total >= 5 && correctCount == total) xp += 50
        else if (config.style == PlayStyle.SURVIVAL && answered == total && lives > 0) xp += 30
        finished = true
    }

    fun result(newBest: Boolean): GameResult = GameResult(
        config = config,
        total = total,
        answered = answered,
        correct = correctCount,
        bestCombo = bestCombo,
        xp = xp,
        missed = missed.toList(),
        livesLeft = lives,
        newBest = newBest,
    )
}

data class GameResult(
    val config: GameConfig,
    val total: Int,
    val answered: Int,
    val correct: Int,
    val bestCombo: Int,
    val xp: Int,
    val missed: List<Question>,
    val livesLeft: Int,
    val newBest: Boolean,
) {
    val accuracy: Float get() = if (answered == 0) 0f else correct.toFloat() / answered

    val stars: Int
        get() {
            if (answered == 0) return 0
            val completed = answered == total || config.style == PlayStyle.TIME_ATTACK
            return when {
                accuracy >= 0.999f && completed && answered >= min(total, 5) -> 3
                accuracy >= 0.85f && completed -> 2
                accuracy >= 0.6f -> 1
                else -> 0
            }
        }

    val headline: String
        get() = when {
            config.style == PlayStyle.SURVIVAL && livesLeft == 0 -> "Out of hearts!"
            config.style == PlayStyle.TIME_ATTACK && answered < total -> "Time's up!"
            stars == 3 -> "Perfect! 完璧!"
            stars == 2 -> "Great job! すごい!"
            stars == 1 -> "Nice work! いいね!"
            else -> "Keep practising! 頑張って!"
        }
}
