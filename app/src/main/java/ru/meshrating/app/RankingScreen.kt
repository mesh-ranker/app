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
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun RankingScreen(
    store: SessionStore,
    client: MeshClient,
    personId: String?,
    onLogout: () -> Unit,
    selectedSubjectId: String? = null,
    onSubjectChange: (String?) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(personId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var subjects by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val selectedSubject = selectedSubjectId ?: "all"
    var period by remember { mutableStateOf("year") }
    var board by remember { mutableStateOf<JSONObject?>(null) }
    val date = MeshCore.today()
    // Месячный рейтинг МЭШ отдаёт только для общего рейтинга класса.
    // У конкретного предмета 30-дневных полей нет, поэтому переключатель скрыт.
    val isGeneral = selectedSubject == "all"
    val monthlyMode = isGeneral && period == "month"

    // Дневной кэш рейтинга: ключ дня начинается в 05:00. Первый вход после
    // 05:00 сохраняет данные на весь учебный день, дальше сеть не дёргается.
    val dayKey = MeshCore.academicDayKey()

    fun load(monthlyMode: Boolean, force: Boolean = false) {
        if (personId.isNullOrBlank()) {
            loading = false
            return
        }
        val subjectKey = if (selectedSubject == "all") "all" else selectedSubject
        val boardKey = "$personId|$subjectKey|${if (monthlyMode) "month" else "year"}"
        val existing = store.loadRankingCache()
        val validDay = existing.optString("day") == dayKey
        val cachedArray = if (!force && validDay) existing.optJSONObject("subjects")?.optJSONArray(personId) else null
        val cachedSubjects = cachedArray?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }
        val cachedBoard = if (!force && validDay) existing.optJSONObject("boards")?.optJSONObject(boardKey) else null
        if (cachedSubjects != null && cachedBoard != null) {
            subjects = cachedSubjects
            board = cachedBoard
            loading = false
            error = null
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val subs = cachedSubjects ?: run {
                        val arr = client.getSubjects(personId, date)
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    }
                    val b = cachedBoard ?: client.getRankBoard(
                        personId = personId,
                        date = date,
                        subjectId = if (selectedSubject == "all") null else selectedSubject,
                        subjectName = subs.firstOrNull { it.optString("id") == selectedSubject }?.optString("name"),
                        monthly = monthlyMode
                    )
                    subs to b
                }
                subjects = result.first
                board = result.second
                val saved = if (validDay) existing else JSONObject().put("day", dayKey)
                val subjectsCache = saved.optJSONObject("subjects") ?: JSONObject()
                subjectsCache.put(personId, JSONArray(result.first))
                val boardsCache = saved.optJSONObject("boards") ?: JSONObject()
                boardsCache.put(boardKey, result.second)
                saved.put("subjects", subjectsCache)
                saved.put("boards", boardsCache)
                store.saveRankingCache(saved)
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(personId, date, selectedSubject, period) { load(monthlyMode) }

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
                    Button(onClick = { load(monthlyMode, force = true) }) { Text("Повторить") }
                }
            }
            else -> {
                val rows = board?.optJSONArray("rows")
                if (rows == null || rows.length() == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Нет данных за эту дату")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
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
                                                        else -> status
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
