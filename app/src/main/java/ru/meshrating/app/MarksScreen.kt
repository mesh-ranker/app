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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * «Оценки» объединяют три режима:
 *  - по дате — все оценки за последние 30 дней, сгруппированные по дням;
 *  - по предмету — список предметов из `/subject_marks`, по нажатию открывается
 *    список оценок конкретного предмета;
 *  - итоговые — таблица четвертей и годовой по предметам с выбором учебного года.
 *
 * Графики (распределение оценок и динамика по неделям) показываются в режимах
 * «по дате» и в карточке предмета.
 */
@Composable
fun MarksScreen(
    store: SessionStore,
    client: MeshClient,
    studentId: String?,
    personId: String? = null,
    subjectId: String? = null,
    onOpenSubject: (String, String) -> Unit = { _, _ -> }
) {
    var mode by remember { mutableStateOf("date") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var marks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var subjects by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var retryKey by remember { mutableStateOf(0) }

    // Итоговые оценки и учебные годы.
    var academicYears by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedYearId by remember { mutableStateOf<String?>(null) }
    var finalMarks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var finalLoading by remember { mutableStateOf(false) }
    var finalError by remember { mutableStateOf<String?>(null) }

    val from = MeshCore.addDays(MeshCore.today(), -30)
    val to = MeshCore.today()
    val effectivePersonId = personId?.takeIf { it.isNotBlank() } ?: store.personId

    LaunchedEffect(studentId, mode, subjectId, retryKey) {
        if (studentId.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        // «Итоговые» грузятся своим эффектом (итоги + выбор года).
        if (subjectId == null && mode == "final") return@LaunchedEffect
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

    // Учебные годы грузим один раз; по умолчанию — текущий.
    LaunchedEffect(studentId) {
        if (studentId.isNullOrBlank()) return@LaunchedEffect
        try {
            val years = withContext(Dispatchers.IO) { client.getAcademicYears().toObjectList() }
            academicYears = years
            if (selectedYearId == null) {
                val current = years.firstOrNull { it.optBoolean("currentYear") }
                    ?: years.maxByOrNull { it.optString("beginDate") }
                selectedYearId = current?.optString("id")?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            finalError = MeshCore.describeError(e)
        }
    }

    // Итоговые оценки перезагружаем при смене года.
    LaunchedEffect(studentId, effectivePersonId, selectedYearId) {
        val yearId = selectedYearId
        if (studentId.isNullOrBlank() || effectivePersonId.isNullOrBlank() || yearId.isNullOrBlank()) {
            return@LaunchedEffect
        }
        finalLoading = true
        finalError = null
        try {
            val selected = academicYears.firstOrNull { it.optString("id") == yearId }
            val yearOnly = selected != null && !selected.optBoolean("currentYear")
            finalMarks = withContext(Dispatchers.IO) {
                client.getFinalMarks(
                    personId = effectivePersonId,
                    studentId = studentId,
                    academicYearId = yearId,
                    yearOnly = yearOnly
                ).toObjectList()
            }
        } catch (e: Exception) {
            finalError = MeshCore.describeError(e)
        } finally {
            finalLoading = false
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
                FilterChip(
                    selected = mode == "final",
                    onClick = { mode = "final" },
                    label = { Text("Итоговые") }
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
            mode == "final" -> FinalMarksSection(
                years = academicYears,
                selectedYearId = selectedYearId,
                onYearChange = { selectedYearId = it },
                marks = finalMarks,
                loading = finalLoading,
                error = finalError
            )
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
            else -> DateMarksList(marks, from, to, Modifier.weight(1f))
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
            Spacer(Modifier.height(6.dp))
        }
        if (marks.isNotEmpty()) {
            item {
                MarksCharts(marks)
                Spacer(Modifier.height(6.dp))
            }
        }
        if (marks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Оценок за период нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ------------------------------------------------------------------ графики --

@Composable
private fun MarksCharts(marks: List<JSONObject>) {
    val summary = remember(marks) { MeshCore.summarizeMarks(marks) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GradeDistributionChart(summary)
        WeeklyTrendChart(marks)
    }
}

@Composable
private fun GradeDistributionChart(summary: JSONObject) {
    val buckets = summary.optJSONArray("byValue")?.toObjectList().orEmpty()
    val maxCount = buckets.maxOfOrNull { it.optInt("count") } ?: 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Распределение оценок",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Число оценок каждой отметки",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            Text(
                                grade.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
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
            Text(
                "Динамика по неделям",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Средний балл за неделю · шкала от 2 до 5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (points.isEmpty()) {
                ChartEmpty("За выбранный период нет оценок для графика")
            } else {
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                val lineColor = MaterialTheme.colorScheme.primary
                val labelStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val textMeasurer = rememberTextMeasurer()
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    val left = 16.dp.toPx()
                    val right = size.width - 12.dp.toPx()
                    val top = 22.dp.toPx()
                    val bottom = size.height - 10.dp.toPx()
                    (2..5).forEach { grade ->
                        val y = bottom - (grade - 2) / 3f * (bottom - top)
                        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                    }
                    fun xAt(index: Int): Float = if (points.size == 1) {
                        (left + right) / 2f
                    } else {
                        left + index * (right - left) / (points.size - 1)
                    }
                    fun yAt(value: Double): Float =
                        bottom - ((value.coerceIn(2.0, 5.0) - 2.0) / 3.0).toFloat() * (bottom - top)
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val offset = Offset(xAt(index), yAt(point.average))
                        if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
                    points.forEachIndexed { index, point ->
                        drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(xAt(index), yAt(point.average)))
                    }
                    // Балл — прямо внутри графика, над каждой точкой.
                    points.forEachIndexed { index, point ->
                        val text = formatMark(point.average)
                        val layout = textMeasurer.measure(text, labelStyle)
                        val x = (xAt(index) - layout.size.width / 2f)
                            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                        var y = yAt(point.average) - layout.size.height - 6.dp.toPx()
                        if (y < 0f) y = yAt(point.average) + 6.dp.toPx()
                        drawText(textMeasurer, text, topLeft = Offset(x, y), style = labelStyle)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val visible = if (points.size <= 6) {
                        points
                    } else {
                        listOf(points.first(), points[points.size / 2], points.last())
                    }
                    visible.forEach { point ->
                        Text(
                            shortDate(point.week),
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
private fun ChartEmpty(text: String) {
    Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

// ----------------------------------------------------------- итоговые оценки --

/** Ширина колонки с отметкой в таблице итоговых оценок. */
private val MARK_COLUMN_WIDTH = 40.dp

@Composable
private fun FinalMarksSection(
    years: List<JSONObject>,
    selectedYearId: String?,
    onYearChange: (String) -> Unit,
    marks: List<JSONObject>,
    loading: Boolean,
    error: String?
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedYear = years.firstOrNull { it.optString("id") == selectedYearId }
    // В выпадающем списке сначала текущий год, затем остальные от новых к старым,
    // чтобы текущий год не прятался в конце длинного списка.
    val orderedYears = remember(years) {
        years.sortedWith(
            compareByDescending<JSONObject> { it.optBoolean("currentYear") }
                .thenByDescending { it.optString("beginDate") }
        )
    }

    // Колонки — только те периоды, по которым реально пришли отметки (в порядке API).
    val periodColumns = remember(marks) {
        val order = LinkedHashMap<String, String>()
        marks.forEach { subject ->
            subject.periods().forEach { period ->
                if (period.nullableDouble("value") == null) return@forEach
                val key = period.optString("ordinal").ifBlank { period.optString("name") }
                if (key.isNotBlank()) order.getOrPut(key) { periodColumnLabel(period) }
            }
        }
        order
    }
    val hasYear = remember(marks) { marks.any { it.nullableDouble("yearMark") != null } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Итоговые оценки",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Четверти и годовая по предметам",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                OutlinedButton(onClick = { menuOpen = true }, enabled = years.isNotEmpty()) {
                    Text(
                        selectedYear?.optString("name")?.takeIf { it.isNotBlank() } ?: "Учебный год",
                        maxLines = 1
                    )
                    Text(" ▾")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    orderedYears.forEach { year ->
                        val name = year.optString("name").ifBlank { year.optString("id") }
                        DropdownMenuItem(
                            text = { Text(if (year.optBoolean("currentYear")) "$name · текущий" else name) },
                            onClick = {
                                menuOpen = false
                                onYearChange(year.optString("id"))
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            marks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Итоговых оценок за этот год нет",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            periodColumns.isEmpty() && !hasYear -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "За этот год итоговые оценки ещё не выставлены",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> FinalMarksTable(marks, periodColumns, hasYear)
        }
    }
}

/** Таблица: предметы по вертикали, периоды и годовая — по горизонтали. */
@Composable
private fun FinalMarksTable(
    marks: List<JSONObject>,
    periodColumns: Map<String, String>,
    hasYear: Boolean
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Предмет",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                periodColumns.values.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.width(MARK_COLUMN_WIDTH),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                if (hasYear) {
                    Text(
                        "Год",
                        modifier = Modifier.width(MARK_COLUMN_WIDTH),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            HorizontalDivider()
        }
        items(marks) { subject ->
            val byKey = subject.periods().associateBy {
                it.optString("ordinal").ifBlank { it.optString("name") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    subject.optString("subjectName").ifBlank { "Предмет" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                periodColumns.keys.forEach { key ->
                    FinalMarkCell(byKey[key]?.nullableDouble("value"))
                }
                if (hasYear) FinalMarkCell(subject.nullableDouble("yearMark"))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun FinalMarkCell(value: Double?) {
    Text(
        formatMark(value),
        modifier = Modifier.width(MARK_COLUMN_WIDTH),
        textAlign = TextAlign.Center,
        color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else markColor(value),
        fontWeight = if (value == null) FontWeight.Normal else FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
}

/** Короткая подпись периода: «I», «II»… или сокращение названия. */
private fun periodColumnLabel(period: JSONObject): String {
    val ordinal = period.optString("ordinal").trim()
    if (ordinal.isNotBlank()) return ordinal
    val name = period.optString("name").trim()
    return when {
        name.startsWith("Первая", true) -> "I"
        name.startsWith("Вторая", true) -> "II"
        name.startsWith("Третья", true) -> "III"
        name.startsWith("Четв", true) -> "IV"
        else -> name.take(3)
    }
}

private fun JSONObject.periods(): List<JSONObject> =
    optJSONArray("periods")?.toObjectList().orEmpty()

// ------------------------------------------------------------------ предметы --

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
                item {
                    MarksCharts(subjectMarks)
                    Spacer(Modifier.height(6.dp))
                }
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
    val subjectYear = subject.nullableDouble("yearMark")
    val count = subject.optJSONArray("marks")?.length() ?: 0
    val dynamic = subject.optString("dynamic")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                SummaryStat("Средний", formatMark(average))
                if (subjectYear != null) SummaryStat("Год", formatMark(subjectYear))
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
