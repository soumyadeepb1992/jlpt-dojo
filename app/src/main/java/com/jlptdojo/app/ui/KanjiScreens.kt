package com.jlptdojo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlptdojo.app.data.Example
import com.jlptdojo.app.data.JlptData
import com.jlptdojo.app.data.Kanji
import com.jlptdojo.app.data.Progress
import com.jlptdojo.app.data.Speaker
import com.jlptdojo.app.data.formatKun

private fun hiragana(s: String): String = buildString {
    s.forEach { c -> append(if (c in 'ァ'..'ヶ') (c - 0x60) else c) }
}

private fun matchesQuery(k: Kanji, q: String): Boolean {
    if (q.isBlank()) return true
    val query = q.trim().lowercase()
    if (k.char == query) return true
    if (k.meanings.any { it.lowercase().contains(query) }) return true
    val h = hiragana(query)
    return k.on.any { hiragana(it).contains(h) } || k.kun.any { it.replace(".", "").contains(h) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanjiListScreen(
    data: JlptData,
    progress: Progress,
    onBack: () -> Unit,
    onOpen: (List<String>, Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var level by rememberSaveable { mutableStateOf(progress.selectedLevels.maxOrNull() ?: 5) }
    var query by rememberSaveable { mutableStateOf("") }
    val list = remember(level, query) {
        data.kanji.filter { (level == 0 || it.level == level) && matchesQuery(it, query) }
    }
    val learned = list.count { progress.isLearned(it.char) }

    Scaffold(
        containerColor = cs.background,
        topBar = { BackTopBar("Kanji Library", onBack) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 16.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(5, 4, 3, 2, 1, 0)).forEach { l ->
                    FilterChip(
                        selected = level == l,
                        onClick = { level = l },
                        label = { Text(if (l == 0) "All" else "N$l", fontWeight = FontWeight.Bold) },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search kanji, meaning or reading") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${list.size} kanji", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("✓ $learned learned", color = cs.tertiary, fontWeight = FontWeight.SemiBold)
            }
            val fraction = if (list.isEmpty()) 0f else learned.toFloat() / list.size
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = cs.tertiary,
                trackColor = cs.surfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 70.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.char }) { k ->
                    KanjiTile(k, progress.isLearned(k.char)) {
                        onOpen(list.map { it.char }, list.indexOf(k))
                    }
                }
            }
        }
    }
}

@Composable
private fun KanjiTile(k: Kanji, learned: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (learned) cs.tertiaryContainer else cs.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            k.char,
            fontFamily = KanjiFont,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = if (learned) cs.onTertiaryContainer else cs.onSurface,
        )
        Text(
            k.meanings.firstOrNull().orEmpty(),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KanjiDetailScreen(
    data: JlptData,
    progress: Progress,
    speaker: Speaker,
    chars: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (chars.size - 1).coerceAtLeast(0))) }
    val k = chars.getOrNull(index)?.let { data.kanjiByChar[it] }
    if (k == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val learned = progress.isLearned(k.char)

    Scaffold(
        containerColor = cs.background,
        topBar = {
            BackTopBar("${index + 1} / ${chars.size}", onBack) {
                IconButton(onClick = { index-- }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                }
                IconButton(onClick = { index++ }, enabled = index < chars.size - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        },
        bottomBar = {
            Surface(color = cs.background) {
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            progress.setLearned(k.char, !learned)
                            if (!learned && index < chars.size - 1) index++
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (learned) cs.surfaceVariant else cs.tertiary,
                            contentColor = if (learned) cs.onSurfaceVariant else cs.onTertiary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                    ) {
                        if (learned) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Learned · tap to undo", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Mark learned & next", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero-${k.char}") { Hero(k, learned) }
            item(key = "on-${k.char}") {
                ReadingSection(
                    title = "On'yomi",
                    jp = "音読み",
                    note = "Chinese-derived reading, used mostly in compound words",
                    readings = k.on,
                    examples = k.onExamples,
                    kanji = k.char,
                    accent = cs.primary,
                    speaker = speaker,
                    emptyText = "This kanji has no on'yomi.",
                )
            }
            item(key = "kun-${k.char}") {
                ReadingSection(
                    title = "Kun'yomi",
                    jp = "訓読み",
                    note = "Native Japanese reading, often with okurigana (shown in brackets)",
                    readings = k.kun.map { formatKun(it) },
                    examples = k.kunExamples,
                    kanji = k.char,
                    accent = cs.secondary,
                    speaker = speaker,
                    emptyText = "This kanji has no kun'yomi.",
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun Hero(k: Kanji, learned: Boolean) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(k.char, fontFamily = KanjiFont, fontSize = 96.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(k.level)
                    Spacer(Modifier.width(8.dp))
                    Text("${k.strokes} strokes", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(k.meaningText, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                if (learned) {
                    Spacer(Modifier.height(6.dp))
                    Text("✓ Learned", color = cs.tertiary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingSection(
    title: String,
    jp: String,
    note: String,
    readings: List<String>,
    examples: List<Example>,
    kanji: String,
    accent: Color,
    speaker: Speaker,
    emptyText: String,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(jp, fontFamily = KanjiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(note, fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (readings.isEmpty()) {
                Text(emptyText, color = cs.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    readings.forEach { r ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.12f))
                                .clickable { speaker.speak(r.replace("(", "").replace(")", "").replace("-", "")) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(r, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Examples", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
                if (examples.isEmpty()) {
                    Text(
                        "No common JLPT word uses this reading yet. Learn the reading above.",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    examples.forEach { e ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = cs.outlineVariant)
                        ExampleRow(e, kanji, accent, speaker)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(e: Example, kanji: String, accent: Color, speaker: Speaker) {
    val cs = MaterialTheme.colorScheme
    val highlighted = buildAnnotatedString {
        e.word.forEach { c ->
            if (c.toString() == kanji) {
                withStyle(SpanStyle(color = accent)) { append(c) }
            } else {
                append(c)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(highlighted, fontFamily = KanjiFont, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(e.reading, fontSize = 14.sp, color = cs.onSurfaceVariant)
            }
            Text(e.meaning, fontSize = 13.sp, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        LevelBadge(e.level)
        SpeakButton(speaker, e.reading)
    }
}
