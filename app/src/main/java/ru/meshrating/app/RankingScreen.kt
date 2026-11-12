package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import kotlin.math.roundToInt

@Composable
fun RankingScreen(
    store: SessionStore,
    client: MeshClient,
    personId: String?,
    studentId: String?,
    onLogout: () -> Unit,
    selectedSubjectId: String? = null,
    onSubjectChange: (String?) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(personId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var subjects by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var marksSummary by remember { mutableStateOf<JSONObject?>(null) }
    var marksSummaryLoading by remember { mutableStateOf(studentId != null) }
    val selectedSubject = selectedSubjectId ?: "all"
    var period by remember { mutableStateOf("year") }
    var board by remember { mutableStateOf<JSONObject?>(null) }
    val date = MeshCore.today()
    // Месячный рейтинг МЭШ отдаёт только для общего рейтинга класса.
    // У конкретного предмета 30-дневных полей нет, поэтому переключатель скрыт.
    val isGeneral = selectedSubject == "all"
    val monthlyMode = isGeneral && period == "month"

    fun load(monthlyMode: Boolean) {
        if (personId.isNullOrBlank()) {
            loading = false
            marksSummaryLoading = false
            return
        }
        loading = true
        marksSummaryLoading = !studentId.isNullOrBlank()
        marksSummary = null
        error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val arr = client.getSubjects(personId, date)
                    val subs = (0 until arr.length()).map { arr.getJSONObject(it) }
                    val selectedName = subs.firstOrNull { it.optString("id") == selectedSubject }?.optString("name").orEmpty()
                    val b = client.getRankBoard(
                        personId = personId,
                        date = date,
                        subjectId = if (selectedSubject == "all") null else selectedSubject,
                        subjectName = selectedName.takeIf { it.isNotBlank() },
                        monthly = monthlyMode
                    )
                    val marksStats = if (studentId.isNullOrBlank()) null else try {
                        val from = MeshCore.addDays(date, -29)
                        val loadedMarks = client.getMarks(studentId, from, date)
                        val filteredMarks = if (selectedSubject == "all") loadedMarks else loadedMarks.filter { mark ->
                            mark.optString("subjectId") == selectedSubject ||
                                (selectedName.isNotBlank() && mark.optString("subjectName") == selectedName)
                        }
                        MeshCore.summarizeMarks(filteredMarks)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    Triple(subs, b, marksStats)
                }
                subjects = result.first
                board = result.second
                marksSummary = result.third
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
                marksSummaryLoading = false
            }
        }
    }

    LaunchedEffect(personId, studentId, date, selectedSubject, period) { load(monthlyMode) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (isGeneral) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = period == "year",
                    onClick = { period = "year" },
                    label = { Text("Год") }
                )
                FilterChip(
                    selected = period == "month",
                    onClick = { period = "month" },
                    label = { Text("Месяц") }
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        board?.optJSONObject("summary")?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard(
                    label = "Моё место",
                    value = if (s.optInt("myPlace", 0) > 0) "#${s.optInt("myPlace")}" else "—",
                    sub = if (s.optInt("total", 0) > 0) "из ${s.optInt("total")}" else "",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    label = "Мой балл",
                    value = formatMark(if (s.isNull("myAverage")) null else s.optDouble("myAverage")),
                    sub = "",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    label = "Класс",
                    value = formatMark(if (s.isNull("classAverage")) null else s.optDouble("classAverage")),
                    sub = "медиана ${formatMark(if (s.isNull("classMedian")) null else s.optDouble("classMedian"))}",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedSubject == "all",
                    onClick = { onSubjectChange(null) },
                    label = { Text("Общий", maxLines = 1) }
                )
            }
            items(subjects) { s ->
                val subjectAverage = when {
                    monthlyMode && !s.isNull("average30") -> s.optDouble("average30")
                    !s.isNull("average") -> s.optDouble("average")
                    else -> null
                }
                FilterChip(
                    selected = selectedSubject == s.optString("id"),
                    onClick = { onSubjectChange(s.optString("id").takeIf { it.isNotBlank() }) },
                    label = {
                        Text(
                            s.optString("name") + " · " + formatMark(subjectAverage),
                            maxLines = 1
                        )
                    }
                )
            }
        }
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
                    Button(onClick = { load(monthlyMode) }) { Text("Повторить") }
                }
            }
            else -> {
                val rows = board?.optJSONArray("rows")
                val selectedName = if (selectedSubject == "all") null else {
                    subjects.firstOrNull { it.optString("id") == selectedSubject }?.optString("name")
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        RatingMarksStatistics(
                            summary = marksSummary,
                            loading = marksSummaryLoading,
                            from = MeshCore.addDays(date, -29),
                            to = date,
                            subjectName = selectedName
                        )
                    }
                    if (rows == null || rows.length() == 0) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                                Text("Нет данных за эту дату")
                            }
                        }
                    } else {
                        items((0 until rows.length()).map { rows.getJSONObject(it) }) { row ->
                            val average = if (row.isNull("average")) null else row.optDouble("average")
                            val isMe = row.optBoolean("isMe")
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "#${row.optInt("place")}",
                                        modifier = Modifier.width(42.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            row.optString("name").ifBlank { "Без имени" },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
                                        )
                                        val status = row.optString("status")
                                        val delta = if (row.isNull("averageDelta")) null else row.optDouble("averageDelta")
                                        val sub = buildString {
                                            if (isMe) append("это я")
                                            if (status.isNotBlank()) {
                                                if (isNotEmpty()) append(" · ")
                                                append(
                                                    when (status) {
                                                        "TOP" -> "сильный"
                                                        "MIDDLE" -> "средний"
                                                        "up" -> "рост"
                                                        "down" -> "снижение"
                                                        "stable" -> "стабильно"
                                                        "NO_DATA" -> "нет данных"
                                                        "BOTTOM" -> "ниже среднего"
                                                        else -> "—"
                                                    }
                                                )
                                            }
                                            if (delta != null) {
                                                if (isNotEmpty()) append(" · ")
                                                append(if (delta >= 0) "▲ " else "▼ ")
                                                append(formatMark(Math.abs(delta)))
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
    }
}

private data class RatingMarkMetric(val label: String, val value: String, val note: String)

@Composable
private fun RatingMarksStatistics(
    summary: JSONObject?,
    loading: Boolean,
    from: String,
    to: String,
    subjectName: String?
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Моя статистика оценок", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Последние 30 дней · ${MeshCore.dateRangeText(from, to)} · ${subjectName?.takeIf { it.isNotBlank() }?.let { "предмет: $it" } ?: "все предметы"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                loading -> Text("Загружаем оценки…", style = MaterialTheme.typography.bodySmall)
                summary == null -> Text(
                    "Статистика оценок сейчас недоступна.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                summary.optInt("withValue") == 0 -> Text(
                    "За выбранный период оценок нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    val count = summary.optInt("withValue")
                    fun number(key: String): Double? = if (summary.isNull(key)) null else summary.optDouble(key)
                    val share = number("positiveShare")
                    val shareText = share?.let { "${(it * 100).roundToInt()}%" } ?: "—"
                    val metrics = listOf(
                        RatingMarkMetric("Средний балл", formatMark(number("average")), "Среднее арифметическое"),
                        RatingMarkMetric("Взвешенное среднее", formatMark(number("weightedAverage")), "С учётом веса работы"),
                        RatingMarkMetric("Медиана", formatMark(number("median")), "Центральное значение"),
                        RatingMarkMetric("Доля положительных", shareText, "${summary.optInt("positiveCount")} из $count оценок"),
                        RatingMarkMetric("Размах", formatMark(number("range")), "Максимум − минимум"),
                        RatingMarkMetric("Стандартное отклонение", formatMark(number("standardDeviation")), "Разброс относительно среднего")
                    )
                    metrics.chunked(2).forEach { rowMetrics ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                            rowMetrics.forEach { metric ->
                                Card(modifier = Modifier.weight(1f)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(9.dp)) {
                                        Text(metric.label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                        Text(metric.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            metric.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KpiCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
