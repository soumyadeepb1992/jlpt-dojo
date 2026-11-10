package com.jlptdojo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlptdojo.app.data.JlptData
import com.jlptdojo.app.data.Progress
import com.jlptdojo.app.game.GameKind
import kotlin.math.roundToInt

@Composable
fun HomeScreen(data: JlptData, progress: Progress, onOpen: (Screen) -> Unit) {
    val levels = progress.selectedLevels.toList().sorted()
    val words = remember(levels) { data.wordsFor(levels) }
    val kanji = remember(levels) { data.kanjiFor(levels) }
    val learnedCount = kanji.count { progress.isLearned(it.char) }
    val weakWords = words.count { (progress.mistakes[it.id] ?: 0) > 0 }
    val weakKanji = kanji.count { (progress.mistakes[it.id] ?: 0) > 0 }
    val cs = MaterialTheme.colorScheme

    Scaffold(containerColor = cs.background) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(progress) }
            item { RankCard(progress, learnedCount, kanji.size) }
            item {
                Column {
                    SectionLabel("Your JLPT levels")
                    Spacer(Modifier.height(6.dp))
                    LevelPicker(progress)
                }
            }
            item { SectionLabel("Train") }
            item {
                ModeCard(
                    glyph = "語",
                    accent = cs.primary,
                    title = "Vocabulary Battle",
                    subtitle = "${words.size} words · 3 choices · combos & hearts",
                    badge = if (weakWords > 0) "$weakWords to review" else null,
                    onClick = { onOpen(Screen.Setup(GameKind.VOCAB)) },
                )
            }
            item {
                ModeCard(
                    glyph = "試",
                    accent = cs.secondary,
                    title = "Kanji Battle",
                    subtitle = "${kanji.size} kanji · meaning, on'yomi, kun'yomi",
                    badge = if (weakKanji > 0) "$weakKanji to review" else null,
                    onClick = { onOpen(Screen.Setup(GameKind.KANJI)) },
                )
            }
            item { SectionLabel("Study") }
            item {
                ModeCard(
                    glyph = "漢",
                    accent = cs.tertiary,
                    title = "Kanji Library",
                    subtitle = "Readings with on'yomi & kun'yomi example words",
                    badge = null,
                    onClick = { onOpen(Screen.KanjiList) },
                )
            }
            item {
                ModeCard(
                    glyph = "辞",
                    accent = Dojo.levelColor(3),
                    title = "Word List",
                    subtitle = "Browse and search ${data.words.size} JLPT words",
                    badge = null,
                    onClick = { onOpen(Screen.WordList) },
                )
            }
            item { SettingsCard(progress) }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun Header(progress: Progress) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("JLPT Dojo", style = MaterialTheme.typography.headlineLarge, color = cs.onBackground)
            Text("日本語道場 · N5 → N1", color = cs.onSurfaceVariant, fontSize = 14.sp)
        }
        val streak = progress.streak
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Pill("🔥 $streak", if (progress.playedToday) Dojo.Flame else cs.onSurfaceVariant)
            Text(
                if (progress.playedToday) "day streak" else "play today!",
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RankCard(progress: Progress, learned: Int, kanjiTotal: Int) {
    val cs = MaterialTheme.colorScheme
    val rank = progress.rank
    val next = rank.nextXp
    val frac = if (next == null) 1f else (progress.xp - rank.minXp).toFloat() / (next - rank.minXp)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.primary, contentColor = cs.onPrimary),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rank.jp, fontFamily = KanjiFont, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(rank.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${progress.xp} XP", fontSize = 13.sp, color = cs.onPrimary.copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { frac.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Dojo.Gold,
                trackColor = cs.onPrimary.copy(alpha = 0.18f),
            )
            Text(
                if (next == null) "Highest rank reached" else "${next - progress.xp} XP to next rank",
                fontSize = 12.sp,
                color = cs.onPrimary.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("${progress.gamesPlayed}", "games", Modifier.weight(1f))
                Stat("${(progress.accuracy * 100).roundToInt()}%", "accuracy", Modifier.weight(1f))
                Stat("$learned/$kanjiTotal", "kanji learned", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.75f))
    }
}

@Composable
private fun ModeCard(
    glyph: String,
    accent: Color,
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(glyph, fontFamily = KanjiFont, fontSize = 32.sp, color = accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (badge != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("● $badge", fontSize = 12.sp, color = cs.secondary, fontWeight = FontWeight.SemiBold)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsCard(progress: Progress) {
    val cs = MaterialTheme.colorScheme
    var confirmReset by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingRow("Show reading hints", "Furigana under words, meaning under kanji", progress.showReadingHint) {
                progress.updateHint(it)
            }
            SettingRow("Vibration", "Haptic feedback on answers", progress.haptics) { progress.updateHaptics(it) }
            if (progress.mistakes.isNotEmpty()) {
                TextButton(onClick = { confirmReset = true }) {
                    Text("Clear review list (${progress.mistakes.size})", color = cs.secondary)
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Clear review list?") },
            text = { Text("Words and kanji you got wrong will no longer be prioritised.") },
            confirmButton = {
                TextButton(onClick = { progress.clearMistakes(); confirmReset = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
