package com.jlptdojo.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlptdojo.app.data.JlptData
import com.jlptdojo.app.data.Progress
import com.jlptdojo.app.game.GameConfig
import com.jlptdojo.app.game.GameKind
import com.jlptdojo.app.game.PlayStyle
import com.jlptdojo.app.game.QuizEngine
import com.jlptdojo.app.game.QuizMode
import kotlin.math.min

private val DECK_SIZES = listOf(10, 20, 30, 50, 100, 0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    kind: GameKind,
    data: JlptData,
    progress: Progress,
    onBack: () -> Unit,
    onStart: (GameConfig) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val modes = remember(kind) { QuizMode.entries.filter { it.kind == kind } }
    var mode by rememberSaveable { mutableStateOf(modes.first()) }
    var deckSize by rememberSaveable { mutableStateOf(20) }
    var style by rememberSaveable { mutableStateOf(PlayStyle.CLASSIC) }
    var weakOnly by rememberSaveable { mutableStateOf(false) }

    val levels = progress.selectedLevels.toList().sorted()
    val eligible = remember(mode, levels) { QuizEngine.eligible(data, mode, levels) }
    val weakCount = eligible.count { (progress.mistakes[it.id] ?: 0) > 0 }
    val useWeak = weakOnly && weakCount > 0
    val available = if (useWeak) weakCount else eligible.size
    val inGame = if (deckSize == 0) available else min(deckSize, available)
    val unit = if (kind == GameKind.VOCAB) "words" else "kanji"
    val best = progress.bestFor(
        GameConfig(mode, levels, deckSize, style, useWeak).bestKey,
    )

    Scaffold(
        containerColor = cs.background,
        topBar = { BackTopBar(if (kind == GameKind.VOCAB) "Vocabulary Battle" else "Kanji Battle", onBack) },
        bottomBar = {
            Surface(color = cs.background) {
                Button(
                    onClick = { onStart(GameConfig(mode, levels, deckSize, style, useWeak)) },
                    enabled = inGame >= 1,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.secondary, contentColor = cs.onSecondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .height(60.dp),
                ) {
                    Text(
                        if (inGame >= 1) "Start  ·  $inGame $unit" else "No $unit available",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Levels")
            LevelPicker(progress)

            Spacer(Modifier.height(4.dp))
            SectionLabel("Question type")
            modes.forEach { m ->
                ChoiceCard(
                    selected = m == mode,
                    badge = m.badge,
                    title = m.title,
                    subtitle = m.subtitle,
                    onClick = { mode = m },
                )
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel("${unit.replaceFirstChar { it.uppercase() }} in this game")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DECK_SIZES.forEach { size ->
                    FilterChip(
                        selected = deckSize == size,
                        onClick = { deckSize = size },
                        label = {
                            Text(
                                if (size == 0) "All ($available)" else "$size",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        enabled = size == 0 || size <= available || size == DECK_SIZES.first(),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel("Game mode")
            PlayStyle.entries.forEach { s ->
                ChoiceCard(
                    selected = s == style,
                    badge = when (s) {
                        PlayStyle.CLASSIC -> "🎯"
                        PlayStyle.SURVIVAL -> "❤"
                        PlayStyle.TIME_ATTACK -> "⏱"
                    },
                    title = s.title,
                    subtitle = s.desc,
                    onClick = { style = s },
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Review mistakes only", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (weakCount > 0) "$weakCount $unit you got wrong before" else "Nothing to review yet. Nice!",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
                Switch(checked = useWeak, onCheckedChange = { weakOnly = it }, enabled = weakCount > 0)
            }
            if (best > 0) {
                Text("🏆 Best for this setup: $best correct", color = Dojo.Gold, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChoiceCard(
    selected: Boolean,
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) cs.primaryContainer else cs.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) cs.primary else cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge,
                    fontFamily = KanjiFont,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) cs.onPrimaryContainer else cs.onSurface)
                Text(subtitle, fontSize = 13.sp, color = if (selected) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
