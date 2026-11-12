package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SubjectsScreen(store: SessionStore, client: MeshClient, personId: String?) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(personId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var subjects by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    fun load() {
        if (personId.isNullOrBlank()) {
            loading = false
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val arr = withContext(Dispatchers.IO) { client.getSubjects(personId, MeshCore.today()) }
                subjects = (0 until arr.length()).map { arr.getJSONObject(it) }
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(personId) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Предметы", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        when {
            personId.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Загружаем профиль ученика…")
                    }
                }
            }
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { load() }) { Text("Повторить") }
                }
            }
            subjects.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Предметы не найдены")
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(subjects) { s ->
                    val average = if (s.isNull("average")) null else s.optDouble("average")
                    val place = if (s.isNull("place")) null else s.optInt("place")
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    s.optString("name").ifBlank { "Предмет" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                                val sub = buildString {
                                    if (place != null) append("место #$place")
                                    if (s.optBoolean("isExam")) {
                                        if (isNotEmpty()) append(" · ")
                                        append("экзамен")
                                    }
                                }
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            GradeBadge(formatMark(average), average)
                        }
                    }
                }
            }
        }
    }
}
