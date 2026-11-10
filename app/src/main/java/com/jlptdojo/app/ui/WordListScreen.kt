package com.jlptdojo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlptdojo.app.data.JlptData
import com.jlptdojo.app.data.Progress
import com.jlptdojo.app.data.Speaker
import com.jlptdojo.app.data.Word

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordListScreen(data: JlptData, progress: Progress, speaker: Speaker, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var level by rememberSaveable { mutableStateOf(progress.selectedLevels.maxOrNull() ?: 5) }
    var query by rememberSaveable { mutableStateOf("") }
    var weakOnly by rememberSaveable { mutableStateOf(false) }
    val list = remember(level, query, weakOnly) {
        val q = query.trim().lowercase()
        data.words.filter { w ->
            (level == 0 || w.level == level) &&
                (!weakOnly || (progress.mistakes[w.id] ?: 0) > 0) &&
                (q.isEmpty() || w.word.contains(q) || w.reading.contains(q) || w.meaning.lowercase().contains(q))
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = { BackTopBar("Word List", onBack) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 16.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 4, 3, 2, 1, 0).forEach { l ->
                    FilterChip(
                        selected = level == l,
                        onClick = { level = l },
                        label = { Text(if (l == 0) "All" else "N$l", fontWeight = FontWeight.Bold) },
                    )
                }
                FilterChip(
                    selected = weakOnly,
                    onClick = { weakOnly = !weakOnly },
                    label = { Text("Missed", fontWeight = FontWeight.Bold) },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search word, kana or English") },
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
            Text(
                "${list.size} words",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { w -> WordRow(w, (progress.mistakes[w.id] ?: 0) > 0, speaker) }
            }
        }
    }
}

@Composable
private fun WordRow(w: Word, weak: Boolean, speaker: Speaker) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.word, fontFamily = KanjiFont, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    if (w.reading != w.word) {
                        Spacer(Modifier.width(8.dp))
                        Text(w.reading, fontSize = 14.sp, color = cs.primary)
                    }
                    if (weak) {
                        Spacer(Modifier.width(6.dp))
                        Text("●", color = cs.secondary, fontSize = 10.sp)
                    }
                }
                Text(w.meaning, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant)
            }
            LevelBadge(w.level)
            SpeakButton(speaker, w.reading)
        }
    }
}
