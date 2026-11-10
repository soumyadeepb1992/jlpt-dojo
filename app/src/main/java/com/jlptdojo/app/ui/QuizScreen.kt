package com.jlptdojo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlptdojo.app.data.JlptData
import com.jlptdojo.app.data.Progress
import com.jlptdojo.app.data.Speaker
import com.jlptdojo.app.game.GameConfig
import com.jlptdojo.app.game.GameResult
import com.jlptdojo.app.game.GameSession
import com.jlptdojo.app.game.Option
import com.jlptdojo.app.game.PlayStyle
import com.jlptdojo.app.game.Question
import com.jlptdojo.app.game.QuizEngine
import com.jlptdojo.app.game.TIME_ATTACK_TENTHS
import kotlinx.coroutines.delay

private enum class OptState { Idle, Correct, Wrong, Dimmed }

@Composable
fun QuizScreen(
    config: GameConfig,
    data: JlptData,
    progress: Progress,
    speaker: Speaker,
    onFinished: (GameResult) -> Unit,
    onAbandon: () -> Unit,
) {
    val session = remember(config) {
        GameSession(config, QuizEngine(data, config, progress.mistakes.toMap(), progress.showReadingHint))
    }
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    var confirmQuit by remember { mutableStateOf(false) }

    BackHandler { confirmQuit = true }

    // Correct answers move on by themselves; wrong ones wait so you can study the answer.
    LaunchedEffect(session.index, session.selected) {
        if (session.wasCorrect == true) {
            delay(750)
            session.next()
        }
    }

    if (config.style == PlayStyle.TIME_ATTACK) {
        LaunchedEffect(session) {
            while (!session.finished) {
                delay(100)
                if (session.selected == null) session.tick()
            }
        }
    }

    LaunchedEffect(session.finished) {
        if (session.finished && !session.recorded) {
            session.recorded = true
            val draft = session.result(newBest = false)
            val newBest = progress.recordGame(draft)
            onFinished(draft.copy(newBest = newBest))
        }
    }

    fun pick(i: Int) {
        if (session.selected != null) return
        val q = session.question
        val ok = session.answer(i)
        progress.recordAnswer(q.itemId, ok)
        if (progress.haptics) {
            haptic.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        }
    }

    Scaffold(containerColor = cs.background) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 18.dp),
        ) {
            TopStrip(session, onClose = { confirmQuit = true })
            Spacer(Modifier.height(10.dp))

            AnimatedContent(
                targetState = session.question,
                transitionSpec = {
                    (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(160)))
                },
                label = "question",
                modifier = Modifier.weight(1f),
            ) { q ->
                val current = q === session.question
                QuestionBody(
                    q = q,
                    // The outgoing card keeps showing its answer while it slides away.
                    selected = if (current) session.selected else q.correctIndex,
                    session = session,
                    speaker = speaker,
                    onPick = { if (current) pick(it) },
                )
            }
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("End this game?") },
            text = {
                Text(
                    if (session.answered > 0) "You'll see your results for the ${session.answered} answered so far."
                    else "Nothing answered yet. You'll go back to the menu.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmQuit = false
                    if (session.answered > 0) session.finish() else onAbandon()
                }) { Text("End game") }
            },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep playing") } },
        )
    }
}

@Composable
private fun TopStrip(session: GameSession, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val style = session.config.style
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "End game") }
            Text(
                "${session.index + 1} / ${session.total}",
                fontWeight = FontWeight.Bold,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            when (style) {
                PlayStyle.SURVIVAL -> Row {
                    repeat(3) { i ->
                        val full = i < session.lives
                        val scale by animateFloatAsState(if (full) 1f else 0.8f, spring(dampingRatio = 0.4f), label = "heart")
                        Icon(
                            if (full) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (full) Dojo.Heart else cs.outline,
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale },
                        )
                    }
                }
                PlayStyle.TIME_ATTACK -> {
                    val secs = (session.timeLeftTenths + 9) / 10
                    Pill("⏱ ${secs}s", if (secs <= 10) Dojo.Wrong else cs.primary)
                }
                PlayStyle.CLASSIC -> {}
            }
            Spacer(Modifier.width(8.dp))
            Pill("${session.xp} XP", Dojo.Gold)
        }
        Spacer(Modifier.height(6.dp))
        val frac = if (style == PlayStyle.TIME_ATTACK) {
            session.timeLeftTenths.toFloat() / TIME_ATTACK_TENTHS
        } else {
            (session.index + if (session.selected != null) 1 else 0).toFloat() / session.total.coerceAtLeast(1)
        }
        val animated by animateFloatAsState(frac, tween(300), label = "progress")
        LinearProgressIndicator(
            progress = { animated.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = if (style == PlayStyle.TIME_ATTACK && frac < 0.15f) Dojo.Wrong else cs.secondary,
            trackColor = cs.surfaceVariant,
        )
        val showCombo = session.combo >= 3
        val comboScale by animateFloatAsState(
            if (showCombo) 1f else 0.6f,
            spring(dampingRatio = 0.4f),
            label = "comboScale",
        )
        val comboAlpha by animateFloatAsState(if (showCombo) 1f else 0f, tween(200), label = "comboAlpha")
        Text(
            "🔥 ${session.combo} combo!",
            color = Dojo.Flame,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(top = 4.dp)
                .graphicsLayer {
                    alpha = comboAlpha
                    scaleX = comboScale
                    scaleY = comboScale
                },
        )
    }
}

@Composable
private fun QuestionBody(
    q: Question,
    selected: Int?,
    session: GameSession,
    speaker: Speaker,
    onPick: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PromptCard(q, speaker)
        Spacer(Modifier.height(18.dp))
        q.options.forEachIndexed { i, option ->
            val state = when {
                selected == null -> OptState.Idle
                i == q.correctIndex -> OptState.Correct
                i == selected -> OptState.Wrong
                else -> OptState.Dimmed
            }
            OptionButton(i, option, state, q.optionsJapanese, q.optionsLarge) { onPick(i) }
            Spacer(Modifier.height(12.dp))
        }

        if (selected != null && selected == q.correctIndex) {
            Text(
                "正解! +${session.lastGain} XP",
                color = Dojo.Correct,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedVisibility(
            visible = selected != null && selected != q.correctIndex,
            enter = slideInVertically { it / 2 } + fadeIn(),
        ) {
            Column {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Answer", fontSize = 12.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text(q.answerTitle, fontFamily = KanjiFont, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            if (q.answerReading.isNotBlank()) {
                                Text(q.answerReading, fontSize = 16.sp, color = cs.primary)
                            }
                            Text(q.answerMeaning, fontSize = 15.sp, color = cs.onSurface)
                        }
                        SpeakButton(speaker, q.speakText)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val outOfLives = session.config.style == PlayStyle.SURVIVAL && session.lives == 0
                Button(
                    onClick = { session.next() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        when {
                            outOfLives -> "See results"
                            session.index + 1 >= session.total -> "Finish"
                            else -> "Continue"
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PromptCard(q: Question, speaker: Speaker) {
    val cs = MaterialTheme.colorScheme
    val size = when {
        !q.promptJapanese -> if (q.prompt.length > 28) 22.sp else 28.sp
        q.prompt.length <= 2 -> 88.sp
        q.prompt.length <= 4 -> 56.sp
        q.prompt.length <= 7 -> 42.sp
        else -> 32.sp
    }
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(q.instruction, color = cs.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    q.prompt,
                    fontSize = size,
                    lineHeight = size * 1.15f,
                    fontFamily = if (q.promptJapanese) KanjiFont else FontFamily.Default,
                    fontWeight = if (q.promptJapanese) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = cs.onSurface,
                )
                if (q.promptSub != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(q.promptSub, color = cs.primary, fontSize = 17.sp, textAlign = TextAlign.Center)
                }
            }
            LevelBadge(q.level, Modifier.align(Alignment.TopStart).padding(12.dp))
            if (q.promptJapanese) {
                SpeakButton(speaker, q.speakText, Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

@Composable
private fun OptionButton(
    index: Int,
    option: Option,
    state: OptState,
    japanese: Boolean,
    large: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when (state) {
            OptState.Idle, OptState.Dimmed -> cs.surface
            OptState.Correct -> Dojo.Correct
            OptState.Wrong -> Dojo.Wrong
        },
        label = "optionBg",
    )
    val fg = when (state) {
        OptState.Correct, OptState.Wrong -> Color.White
        OptState.Dimmed -> cs.onSurface.copy(alpha = 0.4f)
        OptState.Idle -> cs.onSurface
    }
    val scale by animateFloatAsState(
        if (state == OptState.Correct) 1.03f else 1f,
        spring(dampingRatio = 0.45f),
        label = "optionScale",
    )
    Surface(
        onClick = onClick,
        enabled = state == OptState.Idle,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = if (state == OptState.Idle || state == OptState.Dimmed) BorderStroke(1.5.dp, cs.outlineVariant) else null,
        shadowElevation = if (state == OptState.Idle) 1.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (large) 88.dp else 72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(fg.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("ABC"[index].toString(), color = fg, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    option.main,
                    color = fg,
                    fontSize = when {
                        large -> 44.sp
                        japanese -> 23.sp
                        else -> 17.sp
                    },
                    fontFamily = if (japanese) KanjiFont else FontFamily.Default,
                    fontWeight = if (japanese) FontWeight.Bold else FontWeight.Medium,
                )
                if (option.sub != null) {
                    Text(option.sub, color = fg.copy(alpha = 0.75f), fontSize = 13.sp)
                }
            }
            when (state) {
                OptState.Correct -> Icon(Icons.Filled.Check, contentDescription = "Correct", tint = Color.White)
                OptState.Wrong -> Icon(Icons.Filled.Close, contentDescription = "Wrong", tint = Color.White)
                else -> {}
            }
        }
    }
}
