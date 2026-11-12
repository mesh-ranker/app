package ru.meshrating.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun HomeworksScreen(store: SessionStore, client: MeshClient, studentId: String?) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var loading by remember { mutableStateOf(studentId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var homeworks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedDate by remember { mutableStateOf(MeshCore.today()) }
    // due — домашнее задание к уроку выбранного дня (как в дневнике);
    // assigned — по дате, когда его задали.
    var mode by remember { mutableStateOf("due") }
    val from = MeshCore.addDays(selectedDate, -45)
    val to = MeshCore.addDays(selectedDate, 45)

    fun load() {
        if (studentId.isNullOrBlank()) {
            loading = false
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                homeworks = withContext(Dispatchers.IO) { client.getHomeworks(studentId, from, to) }
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(studentId, selectedDate) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Домашние задания", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        DaySelector(
            date = selectedDate,
            onPrev = { selectedDate = MeshCore.addDays(selectedDate, -1) },
            onNext = { selectedDate = MeshCore.addDays(selectedDate, 1) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = mode == "due",
                onClick = { mode = "due" },
                label = { Text("На день") }
            )
            FilterChip(
                selected = mode == "assigned",
                onClick = { mode = "assigned" },
                label = { Text("Задано") }
            )
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
            else -> {
                val filtered = homeworks
                    .filter { h ->
                        val day = if (mode == "assigned") h.optString("assignedOn") else h.optString("dueDate")
                        day == selectedDate
                    }
                    .sortedWith(compareBy({ it.optString("subjectName") }, { it.optString("dueDate") }))
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (mode == "due") "На этот день домашнего задания нет" else "В этот день ничего не задавали")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered) { h ->
                            HomeworkCard(h, uriHandler)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkCard(h: JSONObject, uriHandler: androidx.compose.ui.platform.UriHandler) {
    val description = h.optString("description").trim().let { if (it == ".") "" else it }
    val assigned = h.optString("assignedOn").takeIf { it.isNotBlank() && it != "null" }
    val due = h.optString("dueDate").takeIf { it.isNotBlank() && it != "null" }
    val materials = h.optJSONArray("materials")
    val attachments = h.optJSONArray("attachments")

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.optString("subjectName").ifBlank { "Задание" },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (due != null) {
                    Text(
                        "до ${MeshCore.dateShort(due)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            val dates = buildString {
                if (assigned != null) append("Задано: ${MeshCore.dateShort(assigned)}")
                if (due != null) {
                    if (isNotEmpty()) append(" · ")
                    append("Сдать до: ${MeshCore.dateShort(due)}")
                }
            }
            if (dates.isNotBlank()) {
                Text(
                    dates,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (materials != null && materials.length() > 0) {
                val visible = (0 until materials.length())
                    .mapNotNull { materials.optJSONObject(it) }
                    .filter { it.optString("type") != "attachments" && it.optString("title").isNotBlank() }
                if (visible.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Материалы и ЦДЗ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    visible.forEach { material ->
                        val title = material.optString("title")
                        val url = material.optString("url")
                        val kind = listOf(material.optString("typeName"), material.optString("actionName"))
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        Text(
                            "• $title",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (url.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = if (url.isNotBlank()) {
                                Modifier.clickable { uriHandler.openUri(url) }
                            } else Modifier
                        )
                        if (kind.isNotBlank()) {
                            Text(
                                kind,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            }

            if (attachments != null && attachments.length() > 0) {
                Spacer(Modifier.height(8.dp))
                Text("Файлы", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                for (i in 0 until attachments.length()) {
                    val attachment = attachments.optJSONObject(i) ?: continue
                    val name = attachment.optString("name").ifBlank { "Файл" }
                    val url = attachment.optString("url")
                    Text(
                        "📎 $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (url.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (url.isNotBlank()) {
                            Modifier.clickable { uriHandler.openUri(url) }
                        } else Modifier
                    )
                }
            }
        }
    }
}
