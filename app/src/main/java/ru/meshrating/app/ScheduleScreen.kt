package ru.meshrating.app

import androidx.compose.foundation.clickable
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
fun ScheduleScreen(
    store: SessionStore,
    client: MeshClient,
    studentId: String?,
    onHomeworkClick: (HomeworkTarget) -> Unit = {},
    onFoodClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(studentId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lessons by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedDate by remember { mutableStateOf(MeshCore.today()) }

    fun load() {
        if (studentId.isNullOrBlank()) {
            loading = false
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val arr = withContext(Dispatchers.IO) { client.getSchedule(studentId, selectedDate) }
                lessons = (0 until arr.length()).map { arr.getJSONObject(it) }
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(studentId, selectedDate) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        DaySelector(
            date = selectedDate,
            onPrev = { selectedDate = MeshCore.addDays(selectedDate, -1) },
            onNext = { selectedDate = MeshCore.addDays(selectedDate, 1) }
        )
        OutlinedButton(onClick = onFoodClick, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Еда и баланс", "Food & balance"))
        }
        Spacer(Modifier.height(4.dp))

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
            lessons.none { it.optString("subject").let { s -> s.isNotBlank() && s != "—" } } ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет уроков")
                }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(lessons.filter { it.optString("subject").let { s -> s.isNotBlank() && s != "—" } }) { lesson ->
                    val begin = lesson.optString("begin").takeIf { it.isNotBlank() && it != "null" }
                    val end = lesson.optString("end").takeIf { it.isNotBlank() && it != "null" }
                    val number = if (lesson.isNull("number")) null else lesson.optInt("number")
                    val mark = if (lesson.isNull("mark")) null else lesson.optDouble("mark")
                    val teacher = lesson.optString("teacher")
                    val room = lesson.optString("room")
                    val homework = lesson.optString("homework")

                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.width(70.dp)) {
                                Text(begin ?: "—", fontWeight = FontWeight.Bold)
                                if (end != null) {
                                    Text(
                                        end,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (number != null) {
                                    Text(
                                        "$number урок",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    lesson.optString("subject").ifBlank { "Урок" },
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val details = listOfNotNull(
                                    room.takeIf { it.isNotBlank() },
                                    teacher.takeIf { it.isNotBlank() }
                                ).joinToString(" · ")
                                if (details.isNotBlank()) {
                                    Text(
                                        details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (homework.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "ДЗ: $homework",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable {
                                            onHomeworkClick(
                                                HomeworkTarget(
                                                    date = lesson.optString("date").ifBlank { selectedDate },
                                                    subjectId = lesson.optString("subjectId"),
                                                    subjectName = lesson.optString("subject"),
                                                    text = homework
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                            if (mark != null) {
                                Spacer(Modifier.width(8.dp))
                                GradeBadge(formatMark(mark), mark)
                            }
                        }
                    }
                }
            }
        }
    }
}
