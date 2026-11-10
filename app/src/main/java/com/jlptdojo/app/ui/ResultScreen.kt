package com.jlptdojo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlptdojo.app.data.Speaker
import com.jlptdojo.app.game.GameResult
import com.jlptdojo.app.game.Question
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ResultScreen(
    result: GameResult,
    speaker: Speaker,
    onPlayAgain: () -> Unit,
    onRetryMissed: () -> Unit,
    onHome: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        bottomBar = {
            Surface(color = cs.background) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (result.missed.isNotEmpty()) {
                        Button(
                            onClick = onRetryMissed,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cs.secondary, contentColor = cs.onSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            Text("Retry missed (${result.missed.map { it.itemId }.distinct().size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onHome,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                        ) { Text("Home", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = onPlayAgain,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                        ) { Text("Play again", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { Summary(result) }
                if (result.missed.isNotEmpty()) {
                    item { SectionLabel("Review these (${result.missed.size})") }
                    items(result.missed) { q -> MissedRow(q, speaker) }
                } else if (result.answered > 0) {
                    item {
                        Text(
                            "No mistakes. 素晴らしい!",
                            color = Dojo.Correct,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (result.stars >= 2) Confetti(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Summary(result: GameResult) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i -> AnimatedStar(earned = i < result.stars, delayMs = 250L + i * 280L) }
        }
        Spacer(Modifier.height(10.dp))
        Text(result.headline, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (result.newBest) {
            Spacer(Modifier.height(6.dp))
            Pill("🏆 New personal best!", Dojo.Gold)
        }
        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(vertical = 16.dp)) {
                BigStat("${result.correct}/${result.answered}", "correct", Modifier.weight(1f))
                BigStat("${(result.accuracy * 100).roundToInt()}%", "accuracy", Modifier.weight(1f))
                BigStat("${result.bestCombo}", "best combo", Modifier.weight(1f))
                BigStat("+${result.xp}", "XP", Modifier.weight(1f), Dojo.Gold)
            }
        }
        Text(
            "${result.config.mode.title} · ${result.config.style.title} · " +
                result.config.levels.sortedDescending().joinToString(" ") { "N$it" },
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AnimatedStar(earned: Boolean, delayMs: Long) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
    }
    Icon(
        Icons.Filled.Star,
        contentDescription = null,
        tint = if (earned) Dojo.Gold else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(if (earned) 64.dp else 52.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    )
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier, color: Color = Color.Unspecified) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp, color = color)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MissedRow(q: Question, speaker: Speaker) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                q.answerTitle,
                fontFamily = KanjiFont,
                fontSize = if (q.answerTitle.length <= 2) 34.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 52.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (q.answerReading.isNotBlank()) Text(q.answerReading, color = cs.primary, fontSize = 14.sp)
                Text(q.answerMeaning, fontSize = 14.sp, maxLines = 2)
            }
            LevelBadge(q.level)
            SpeakButton(speaker, q.speakText)
        }
    }
}

private class Particle(val x: Float, val speed: Float, val drift: Float, val delay: Float, val size: Float, val color: Color)

@Composable
private fun Confetti(modifier: Modifier) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    val colors = listOf(Dojo.Gold, Dojo.Flame, Dojo.Correct, Dojo.levelColor(3), Dojo.levelColor(2), Dojo.Heart)
    val particles = remember {
        val r = Random(7)
        List(80) {
            Particle(
                x = r.nextFloat(),
                speed = 0.7f + r.nextFloat() * 0.8f,
                drift = (r.nextFloat() - 0.5f) * 0.25f,
                delay = r.nextFloat() * 0.35f,
                size = 8f + r.nextFloat() * 12f,
                color = colors[r.nextInt(colors.size)],
            )
        }
    }
    Canvas(modifier) {
        val time = t.value
        if (time >= 1f) return@Canvas
        particles.forEach { p ->
            val local = ((time - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -40f + local * p.speed * size.height
            val x = (p.x + p.drift * local) * size.width + sin(local * 12f + p.x * 6f) * 18f
            val w = p.size * (0.4f + 0.6f * kotlin.math.abs(sin(local * 9f + p.delay * 20f)))
            drawRect(
                color = p.color,
                topLeft = Offset(x, y),
                size = Size(w, p.size * 0.55f),
                alpha = (1f - local).coerceIn(0f, 1f),
            )
        }
    }
}
