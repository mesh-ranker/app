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
fun MarksScreen(store: SessionStore, client: MeshClient, studentId: String?) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(studentId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var marks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val from = MeshCore.addDays(MeshCore.today(), -30)
    val to = MeshCore.today()

    fun load() {
        if (studentId.isNullOrBlank()) {
            loading = false
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                marks = withContext(Dispatchers.IO) { client.getMarks(studentId, from, to) }
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(studentId) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Оценки", style = MaterialTheme.typography.headlineMedium)
        Text(
            MeshCore.dateRangeText(from, to),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        when {
            studentId.isNullOrBlank() -> {
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
            marks.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Оценок за период нет")
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(marks) { m ->
                    val value = if (m.isNull("value")) null else m.optDouble("value")
                    val markText = m.optString("markText").takeIf { it.isNotBlank() } ?: formatMark(value)
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    m.optString("subjectName").ifBlank { "Предмет" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                                val details = listOf(
                                    m.optString("controlForm"),
                                    MeshCore.dateShort(if (m.isNull("date")) null else m.optString("date"))
                                ).filter { it.isNotBlank() && it != "—" }.joinToString(" · ")
                                if (details.isNotBlank()) {
                                    Text(
                                        details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val comment = m.optString("comment")
                                if (comment.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        comment,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            GradeBadge(markText, value)
                        }
                    }
                }
            }
        }
    }
}
