package ru.meshrating.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * «Оценки» объединяют журнал за период и итоги по предметам.
 * В журнале доступны сводная статистика, графики и подпись учебного периода.
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
    var showCharts by remember { mutableStateOf(true) }
    var retryKey by remember { mutableStateOf(0) }
    val from = MeshCore.addDays(MeshCore.today(), -29)
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
                val periodSubjects = runCatching {
                    withContext(Dispatchers.IO) { client.getSubjectMarksDetailed(studentId).toObjectList() }
                }.getOrDefault(emptyList())
                val loadedMarks = withContext(Dispatchers.IO) {
                    client.getMarks(studentId, from, to)
                }
                subjects = periodSubjects
                marks = MeshCore.attachMarkPeriods(loadedMarks, JSONArray(periodSubjects))
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
                if (mode == "date") {
                    FilterChip(
                        selected = showCharts,
                        onClick = { showCharts = !showCharts },
                        label = { Text(if (showCharts) "Графики показаны" else "Показать графики") }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

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
                DateMarksList(marks, from, to, showCharts, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DateMarksList(
    marks: List<JSONObject>,
    from: String,
    to: String,
    showCharts: Boolean,
    modifier: Modifier = Modifier
) {
    val grouped = remember(marks) {
        marks.groupBy { it.optString("date") }.entries.sortedByDescending { it.key }
    }
    val summary = remember(marks) { MeshCore.summarizeMarks(marks) }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Text(
                MeshCore.dateRangeText(from, to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            MarksStatistics(summary)
            Spacer(Modifier.height(8.dp))
        }
        if (showCharts) {
            item {
                MarksCharts(summary, marks)
                Spacer(Modifier.height(8.dp))
            }
        }
        if (marks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                    Text("Оценок за выбранный период нет")
                }
            }
        } else {
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
}

private data class StatisticItem(val label: String, val value: String, val note: String)

@Composable
private fun MarksStatistics(summary: JSONObject) {
    val count = summary.optInt("withValue")
    val noValueCount = summary.optInt("withoutValue")
    val positiveShare = summary.nullableDouble("positiveShare")
        ?.let { "${(it * 100).roundToInt()}%" } ?: "—"
    val items = listOf(
        StatisticItem("С оценкой", count.toString(), if (noValueCount > 0) "Без оценки: $noValueCount" else "За выбранный период"),
        StatisticItem("Средний балл", formatMark(summary.nullableDouble("average")), "Взвешенный: ${formatMark(summary.nullableDouble("weightedAverage"))}"),
        StatisticItem("Медиана", formatMark(summary.nullableDouble("median")), "Середина всех оценок"),
        StatisticItem("Положительных", "${summary.optInt("positiveCount")} · $positiveShare", "Оценки от 3 и выше"),
        StatisticItem("Пятёрок", summary.optInt("fivesCount").toString(), "Из $count оценок"),
        StatisticItem(
            "Максимум / минимум",
            "${formatMark(summary.nullableDouble("best"))} / ${formatMark(summary.nullableDouble("worst"))}",
            "Размах: ${formatMark(summary.nullableDouble("range"))} · стандартное отклонение: ${formatMark(summary.nullableDouble("standardDeviation"))}"
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Статистика за период", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Card(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(item.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                item.value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = item.valueColor()
                            )
                            Text(item.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatisticItem.valueColor(): androidx.compose.ui.graphics.Color = when (label) {
    "Средний балл" -> markColor(value.toDoubleOrNull())
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun MarksCharts(summary: JSONObject, marks: List<JSONObject>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Графики оценок", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        GradeDistributionChart(summary)
        WeeklyTrendChart(marks)
        SubjectAverageChart(summary)
    }
}

@Composable
private fun GradeDistributionChart(summary: JSONObject) {
    val buckets = summary.optJSONArray("byValue")?.toObjectList().orEmpty()
    val maxCount = buckets.maxOfOrNull { it.optInt("count") } ?: 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Распределение оценок", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text("Число оценок каждой отметки", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (maxCount == 0) {
                ChartEmpty("Недостаточно данных для построения графика")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(136.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    buckets.forEach { bucket ->
                        val grade = bucket.optInt("value")
                        val count = bucket.optInt("count")
                        val height = if (count == 0) 2.dp else (82f * count / maxCount).dp.coerceAtLeast(5.dp)
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.width(30.dp).height(height)
                                    .background(markColor(grade.toDouble()), RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(grade.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private data class TrendPoint(val week: String, val average: Double)

@Composable
private fun WeeklyTrendChart(marks: List<JSONObject>) {
    val points = remember(marks) { weeklyAverages(marks) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Динамика по неделям", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text("Средний балл за неделю · шкала от 2 до 5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            if (points.isEmpty()) {
                ChartEmpty("За выбранный период нет оценок для графика")
            } else {
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                val lineColor = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(132.dp)) {
                    val left = 16.dp.toPx()
                    val right = size.width - 12.dp.toPx()
                    val top = 10.dp.toPx()
                    val bottom = size.height - 10.dp.toPx()
                    (2..5).forEach { grade ->
                        val y = bottom - (grade - 2) / 3f * (bottom - top)
                        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                    }
                    fun xAt(index: Int): Float = if (points.size == 1) (left + right) / 2f
                    else left + index * (right - left) / (points.size - 1)
                    fun yAt(value: Double): Float = bottom - ((value.coerceIn(2.0, 5.0) - 2.0) / 3.0).toFloat() * (bottom - top)
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val offset = Offset(xAt(index), yAt(point.average))
                        if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
                    points.forEachIndexed { index, point ->
                        drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(xAt(index), yAt(point.average)))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val visible = if (points.size <= 6) points else listOf(points.first(), points[points.size / 2], points.last())
                    visible.forEach { point ->
                        Text(
                            "${shortDate(point.week)} · ${formatMark(point.average)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun weeklyAverages(marks: List<JSONObject>): List<TrendPoint> {
    val grouped = marks.mapNotNull { mark ->
        val date = mark.optString("date")
        val value = mark.nullableDouble("value") ?: return@mapNotNull null
        if (date.isBlank()) return@mapNotNull null
        val calendar = MeshCore.parseIso(date)
        val weekday = calendar.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = (weekday + 5) % 7
        calendar.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        val week = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        week to value
    }.groupBy({ it.first }, { it.second })
    return grouped.entries.sortedBy { it.key }.takeLast(10).map { (week, values) ->
        TrendPoint(week, values.average())
    }
}

private fun shortDate(iso: String): String =
    if (iso.length >= 10) iso.substring(8, 10) + "." + iso.substring(5, 7) else iso

@Composable
private fun SubjectAverageChart(summary: JSONObject) {
    val groups = summary.optJSONArray("bySubject")?.toObjectList().orEmpty()
        .filter { it.nullableDouble("average") != null }
        .take(8)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Средний балл по предметам", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            if (groups.isEmpty()) {
                ChartEmpty("Недостаточно данных по предметам")
            } else {
                groups.forEach { group ->
                    val average = group.nullableDouble("average") ?: 0.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.optString("subjectName").ifBlank { "Предмет" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(formatMark(average), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 7.dp)
                            .height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    ) {
                        Box(
                            Modifier.fillMaxWidth((average / 5.0).toFloat().coerceIn(0f, 1f))
                                .fillMaxHeight().background(markColor(average), RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartEmpty(text: String) {
    Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        val raw = subject.optJSONArray("marks")?.toObjectList().orEmpty()
        MeshCore.attachMarkPeriods(raw, JSONArray().put(subject))
    }
    val periods = subject.optJSONArray("periods")?.toObjectList().orEmpty()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SubjectSummaryCard(subject, subjectMarks)
            Spacer(Modifier.height(6.dp))
        }
        if (periods.isNotEmpty()) {
            item {
                SubjectPeriodChart(periods)
                Spacer(Modifier.height(6.dp))
            }
        }
        if (subjectMarks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Оценок по этому предмету нет")
                }
            }
        } else {
            items(subjectMarks) { mark ->
                MarkCard(mark, showSubject = false, showDate = true)
            }
        }
    }
}

@Composable
private fun SubjectPeriodChart(periods: List<JSONObject>) {
    val values = periods.mapNotNull { period ->
        period.nullableDouble("value")?.let { period to it }
    }
    if (values.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Итоги по учебным периодам", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(122.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val max = values.maxOf { it.second }.coerceAtLeast(1.0)
                values.take(6).forEach { (period, value) ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(formatMark(value), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier.width(30.dp).height((72f * value / max).dp.coerceAtLeast(5.dp))
                                .background(markColor(value), RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            period.optString("title").ifBlank { "Период" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    val periods = subject.optJSONArray("periods")?.length() ?: 0
    val marks = subject.optJSONArray("marks")?.toObjectList().orEmpty()
    val summary = remember(marks) { MeshCore.summarizeMarks(marks) }
    val count = summary.optInt("withValue")
    val details = buildList {
        average?.let { add("Средний: ${formatMark(it)}") }
        summary.nullableDouble("median")?.let { add("медиана: ${formatMark(it)}") }
        add("Оценок: $count")
        yearMark?.let { add("Годовая: ${formatMark(it)}") }
        if (periods > 0) add("Периодов: $periods")
    }.joinToString(" · ")

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
                Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubjectSummaryCard(subject: JSONObject, subjectMarks: List<JSONObject>) {
    val name = subject.optString("subjectName").ifBlank { "Предмет" }
    val average = subject.nullableDouble("average")
    val yearMark = subject.nullableDouble("yearMark")
    val summary = remember(subjectMarks) { MeshCore.summarizeMarks(subjectMarks) }
    val count = summary.optInt("withValue")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryStat("Средний балл", formatMark(average), Modifier.weight(1f))
                SummaryStat("Медиана", formatMark(summary.nullableDouble("median")), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryStat("Годовая оценка", formatMark(yearMark), Modifier.weight(1f))
                SummaryStat("Оценок", count.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            val distribution = summary.optJSONArray("byValue")?.toObjectList().orEmpty()
            val distributionText = distribution.joinToString(" · ") { "${it.optInt("value")}: ${it.optInt("count")}" }
            if (distributionText.isNotBlank()) {
                Text("Распределение: $distributionText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (subject.optString("dynamic")) {
                "UP" -> Text("Динамика: рост", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                "DOWN" -> Text("Динамика: снижение", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val periodName = mark.optString("periodName").trim()
        if (periodName.isNotBlank()) add("Учебный период: $periodName")
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
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (details.isNotBlank()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val comment = mark.optString("comment")
                if (comment.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
