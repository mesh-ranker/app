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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * «Оценки» объединяют два режима:
 *  - по дате — все оценки за последние 30 дней, сгруппированные по дням;
 *  - по предмету — список предметов из `/subject_marks`, по нажатию открывается
 *    список оценок конкретного предмета.
 */
@Composable
fun MarksScreen(
    store: SessionStore,
    client: MeshClient,
    studentId: String?,
    subjectId: String? = null,
    onOpenSubject: (String, String) -> Unit = { _, _ -> }
) {
    var mode by remember { mutableStateOf("date") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var marks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var subjects by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var retryKey by remember { mutableStateOf(0) }
    val from = MeshCore.addDays(MeshCore.today(), -30)
    val to = MeshCore.today()

    LaunchedEffect(studentId, mode, subjectId, retryKey) {
        if (studentId.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            if (subjectId != null || mode == "subject") {
                subjects = withContext(Dispatchers.IO) {
                    client.getSubjectMarksDetailed(studentId).toObjectList()
                }
            } else {
                marks = withContext(Dispatchers.IO) {
                    client.getMarks(studentId, from, to)
                }
            }
        } catch (e: Exception) {
            error = MeshCore.describeError(e)
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (subjectId == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = mode == "date",
                    onClick = { mode = "date" },
                    label = { Text("По дате") }
                )
                FilterChip(
                    selected = mode == "subject",
                    onClick = { mode = "subject" },
                    label = { Text("По предмету") }
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        when {
            studentId.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Загружаем профиль ученика...")
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
                    Button(onClick = { retryKey++ }) { Text("Повторить") }
                }
            }
            subjectId != null -> {
                val subject = subjects.firstOrNull { it.optString("subjectId") == subjectId }
                SubjectMarksDetail(subject)
            }
            mode == "subject" -> {
                if (subjects.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Предметов с оценками нет")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(subjects) { subject ->
                            SubjectMarksCard(subject) {
                                val id = subject.optString("subjectId")
                                val name = subject.optString("subjectName")
                                if (id.isNotBlank()) onOpenSubject(id, name)
                            }
                        }
                    }
                }
            }
            else -> {
                DateMarksList(marks, from, to, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DateMarksList(
    marks: List<JSONObject>,
    from: String,
    to: String,
    modifier: Modifier = Modifier
) {
    if (marks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Оценок за период нет")
        }
        return
    }
    val grouped = remember(marks) {
        marks.groupBy { it.optString("date") }.entries.sortedByDescending { it.key }
    }
    LazyColumn(modifier = modifier) {
        item {
            Text(
                MeshCore.dateRangeText(from, to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }
        grouped.forEach { (date, dayMarks) ->
            item(key = "date-$date") {
                Text(
                    MeshCore.dateShort(date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
            items(dayMarks) { mark ->
                MarkCard(mark, showSubject = true, showDate = false)
            }
        }
    }
}

@Composable
private fun SubjectMarksDetail(subject: JSONObject?) {
    if (subject == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Предмет не найден")
        }
        return
    }
    val subjectMarks = remember(subject) {
        subject.optJSONArray("marks")?.toObjectList().orEmpty()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        SubjectSummaryCard(subject)
        Spacer(Modifier.height(6.dp))
        if (subjectMarks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Оценок по этому предмету нет")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(subjectMarks) { mark ->
                    MarkCard(mark, showSubject = false, showDate = true)
                }
            }
        }
    }
}

@Composable
private fun SubjectMarksCard(subject: JSONObject, onClick: () -> Unit) {
    val name = subject.optString("subjectName").ifBlank { "Предмет" }
    val average = subject.nullableDouble("average")
    val yearMark = subject.nullableDouble("yearMark")
    val count = subject.optJSONArray("marks")?.length() ?: 0
    val trend = when (subject.optString("dynamic")) {
        "UP" -> " ↑"
        "DOWN" -> " ↓"
        else -> ""
    }
    val details = buildList {
        average?.let { add("Средний: ${formatMark(it)}") }
        if (count > 0) add("Оценок: $count")
        yearMark?.let { add("Год: ${formatMark(it)}") }
    }.joinToString(" · ") + trend

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubjectSummaryCard(subject: JSONObject) {
    val name = subject.optString("subjectName").ifBlank { "Предмет" }
    val average = subject.nullableDouble("average")
    val yearMark = subject.nullableDouble("yearMark")
    val count = subject.optJSONArray("marks")?.length() ?: 0
    val dynamic = subject.optString("dynamic")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                SummaryStat("Средний", formatMark(average))
                if (yearMark != null) SummaryStat("Год", formatMark(yearMark))
                SummaryStat("Оценок", count.toString())
            }
            if (dynamic == "UP" || dynamic == "DOWN") {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (dynamic == "UP") "Динамика: рост" else "Динамика: снижение",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dynamic == "UP") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MarkCard(mark: JSONObject, showSubject: Boolean, showDate: Boolean) {
    val value = mark.nullableDouble("value")
    val markText = mark.optString("markText").takeIf { it.isNotBlank() } ?: formatMark(value)
    val title = if (showSubject) {
        mark.optString("subjectName").ifBlank { "Предмет" }
    } else {
        mark.optString("controlForm").ifBlank { "Оценка" }
    }
    val details = buildList {
        if (showSubject) {
            val controlForm = mark.optString("controlForm")
            if (controlForm.isNotBlank() && controlForm != "—") add(controlForm)
        }
        if (showDate) {
            val date = mark.optString("date")
            if (date.isNotBlank()) add(MeshCore.dateShort(date))
        }
    }.joinToString(" · ")

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                if (details.isNotBlank()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val comment = mark.optString("comment")
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

private fun JSONObject.nullableDouble(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() || it.isInfinite() }

private fun JSONArray.toObjectList(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }
