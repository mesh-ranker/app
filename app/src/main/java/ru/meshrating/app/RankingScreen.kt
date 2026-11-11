package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun RankingScreen(store: SessionStore, client: MeshClient, personId: String?, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var subjects by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedSubject by remember { mutableStateOf("all") }
    var board by remember { mutableStateOf<JSONObject?>(null) }
    var date by remember { mutableStateOf(MeshCore.today()) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                val subs = withContext(Dispatchers.IO) {
                    val arr = client.getSubjects(personId, date)
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                }
                subjects = subs
                val b = withContext(Dispatchers.IO) {
                    client.getRankBoard(personId, date, if (selectedSubject == "all") null else selectedSubject)
                }
                board = b
            } catch (e: Exception) {
                error = e.message ?: "Ошибка"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(personId, date, selectedSubject) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Рейтинг", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        // KPI
        board?.optJSONObject("summary")?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard("Моё место", "#${s.optInt("myPlace", 0)} из ${s.optInt("total", 0)}")
                KpiCard("Мой балл", s.optDouble("myAverage", 0.0).toString())
                KpiCard("Класс", s.optDouble("classAverage", 0.0).toString())
            }
            Spacer(Modifier.height(8.dp))
        }

        // Subject chips: horizontal scroll, no wrap
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                FilterChip(selected = selectedSubject == "all", onClick = { selectedSubject = "all" }, label = { Text("Общий", maxLines = 1) })
            }
            items(subjects) { s ->
                FilterChip(selected = selectedSubject == s.optString("id"), onClick = { selectedSubject = s.optString("id") }, label = { Text(s.optString("name"), maxLines = 1) })
            }
        }
        Spacer(Modifier.height(8.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else {
            val rows = board?.optJSONArray("rows")
            LazyColumn {
                if (rows != null) {
                    items((0 until rows.length()).map { rows.getJSONObject(it) }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#${row.optInt("place")} ${row.optString("name")}")
                            Text(row.optDouble("average", 0.0).toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogout) { Text("Выйти") }
    }
}

@Composable
fun KpiCard(label: String, value: String) {
    Card(modifier = Modifier.width(110.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}