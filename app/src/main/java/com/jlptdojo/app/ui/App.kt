package com.jlptdojo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlptdojo.app.data.JlptData
import com.jlptdojo.app.data.Progress
import com.jlptdojo.app.data.Speaker
import com.jlptdojo.app.game.GameConfig
import com.jlptdojo.app.game.GameKind
import com.jlptdojo.app.game.GameResult

sealed interface Screen {
    data object Home : Screen
    data class Setup(val kind: GameKind) : Screen
    data class Quiz(val config: GameConfig) : Screen
    data class Result(val result: GameResult) : Screen
    data object KanjiList : Screen
    data class KanjiDetail(val chars: List<String>, val index: Int) : Screen
    data object WordList : Screen
}

@Composable
fun JlptApp(progress: Progress, speaker: Speaker) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<JlptData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            data = JlptData.load(context)
        } catch (e: Exception) {
            error = e.message ?: "Could not load the word lists"
        }
    }

    val loaded = data
    if (loaded == null) {
        Splash(error)
    } else {
        Navigator(loaded, progress, speaker)
    }
}

@Composable
private fun Splash(error: String?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("道場", fontFamily = KanjiFont, fontSize = 72.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            if (error == null) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            } else {
                Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Navigator(data: JlptData, progress: Progress, speaker: Speaker) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    // Keeps filters, choices and scroll positions when you come back to a screen.
    val saved = rememberSaveableStateHolder()

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop() {
        // removeAt (not removeLast) keeps this safe on Android versions before 15.
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replaceTop(s: Screen) {
        stack[stack.lastIndex] = s
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "nav",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { screen ->
        saved.SaveableStateProvider(screenKey(screen)) {
        when (screen) {
            Screen.Home -> HomeScreen(data, progress, onOpen = { push(it) })
            is Screen.Setup -> SetupScreen(
                kind = screen.kind,
                data = data,
                progress = progress,
                onBack = { pop() },
                onStart = { push(Screen.Quiz(it)) },
            )
            is Screen.Quiz -> QuizScreen(
                config = screen.config,
                data = data,
                progress = progress,
                speaker = speaker,
                onFinished = { replaceTop(Screen.Result(it)) },
                onAbandon = { pop() },
            )
            is Screen.Result -> ResultScreen(
                result = screen.result,
                speaker = speaker,
                onPlayAgain = { replaceTop(Screen.Quiz(screen.result.config.copy(seed = System.nanoTime()))) },
                onRetryMissed = {
                    val ids = screen.result.missed.map { q -> q.itemId }.distinct()
                    replaceTop(
                        Screen.Quiz(
                            screen.result.config.copy(
                                itemIds = ids,
                                deckSize = 0,
                                weakOnly = false,
                                seed = System.nanoTime(),
                            ),
                        ),
                    )
                },
                onHome = { home() },
            )
            Screen.KanjiList -> KanjiListScreen(
                data = data,
                progress = progress,
                onBack = { pop() },
                onOpen = { chars, index -> push(Screen.KanjiDetail(chars, index)) },
            )
            is Screen.KanjiDetail -> KanjiDetailScreen(
                data = data,
                progress = progress,
                speaker = speaker,
                chars = screen.chars,
                startIndex = screen.index,
                onBack = { pop() },
            )
            Screen.WordList -> WordListScreen(data, progress, speaker, onBack = { pop() })
        }
        }
    }
}

private fun screenKey(s: Screen): String = when (s) {
    Screen.Home -> "home"
    is Screen.Setup -> "setup-${s.kind.name}"
    is Screen.Quiz -> "quiz-${s.config.seed}"
    is Screen.Result -> "result-${s.result.config.seed}"
    Screen.KanjiList -> "kanji-list"
    is Screen.KanjiDetail -> "kanji-detail-${s.chars.firstOrNull()}-${s.chars.size}-${s.index}"
    Screen.WordList -> "word-list"
}
