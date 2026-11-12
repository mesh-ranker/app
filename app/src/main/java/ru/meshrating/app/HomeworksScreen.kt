package ru.meshrating.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

@Composable
fun HomeworksScreen(
    store: SessionStore,
    client: MeshClient,
    studentId: String?,
    target: HomeworkTarget? = null,
    onTargetConsumed: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    var loading by remember { mutableStateOf(studentId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var homeworks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    // По умолчанию — ДЗ, заданные НА ЗАВТРА.
    var selectedDate by remember { mutableStateOf(MeshCore.addDays(MeshCore.today(), 1)) }
    // due — домашнее задание к уроку выбранного дня (как в дневнике);
    // assigned — по дате, когда его задали.
    var mode by remember { mutableStateOf("due") }
    var focusIndex by remember { mutableStateOf<Int?>(null) }
    var launchingMaterialId by remember { mutableStateOf<String?>(null) }
    // Локальные (оптимистичные) отметки «выполнено»: entryStudentId → сделано.
    var doneOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var savingDone by remember { mutableStateOf<Set<String>>(emptySet()) }
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

    fun openMaterial(homeworkEntryId: String, material: JSONObject) {
        val directUrl = material.optString("url").trim()
        if (directUrl.isNotBlank()) {
            openUrl(uriHandler, directUrl, context)
            return
        }
        val materialId = material.optString("uuid")
            .ifBlank { material.optString("materialId") }
            .trim()
        if (homeworkEntryId.isBlank() || materialId.isBlank()) return
        launchingMaterialId = materialId
        scope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    client.launchHomeworkMaterial(homeworkEntryId, materialId)
                }
                openUrl(uriHandler, url, context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, MeshCore.describeError(e), Toast.LENGTH_LONG).show()
            } finally {
                launchingMaterialId = null
            }
        }
    }

    // Отметка/снятие отметки «выполнено». Сразу меняем UI, затем подтверждаем у МЭШ;
    // при ошибке откатываем и показываем сообщение.
    fun toggleDone(homework: JSONObject, target: Boolean) {
        val entryId = homework.optString("entryStudentId").ifBlank { homework.optString("id") }
        if (entryId.isBlank()) return
        val type = homework.optString("entryType").ifBlank { "OO" }
        val previous = doneOverrides[entryId] ?: homework.optBoolean("isDone")
        if (previous == target) return
        doneOverrides = doneOverrides + (entryId to target)
        savingDone = savingDone + entryId
        scope.launch {
            try {
                withContext(Dispatchers.IO) { client.setHomeworkDone(entryId, type, target) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                doneOverrides = doneOverrides + (entryId to previous)
                Toast.makeText(context, MeshCore.describeError(e), Toast.LENGTH_LONG).show()
            } finally {
                savingDone = savingDone - entryId
            }
        }
    }

    LaunchedEffect(studentId, selectedDate) { load() }

    LaunchedEffect(target) {
        val destination = target ?: return@LaunchedEffect
        if (mode != "due") mode = "due"
        if (selectedDate != destination.date) selectedDate = destination.date
    }

    val filtered = remember(homeworks, mode, selectedDate) {
        homeworks
            .filter { h ->
                val day = if (mode == "assigned") h.optString("assignedOn") else h.optString("dueDate")
                day == selectedDate
            }
            .sortedWith(compareBy({ it.optString("subjectName") }, { it.optString("dueDate") }))
    }

    LaunchedEffect(target, selectedDate, loading, filtered) {
        val destination = target ?: return@LaunchedEffect
        if (loading || selectedDate != destination.date) return@LaunchedEffect
        val index = findHomeworkIndex(filtered, destination)
        if (index >= 0) {
            listState.animateScrollToItem(index)
            focusIndex = index
            delay(1_000)
            focusIndex = null
        }
        onTargetConsumed()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        DaySelector(
            date = selectedDate,
            onPrev = { selectedDate = MeshCore.addDays(selectedDate, -1) },
            onNext = { selectedDate = MeshCore.addDays(selectedDate, 1) }
        )
        RelativeDayChips(
            today = MeshCore.today(),
            selectedDate = selectedDate,
            onSelect = { selectedDate = it }
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
            filtered.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (mode == "due") "На этот день домашнего задания нет" else "В этот день ничего не задавали")
                }
            }
            else -> {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    itemsIndexed(filtered) { index, h ->
                        val entryId = h.optString("entryStudentId").ifBlank { h.optString("id") }
                        HomeworkCard(
                            h = h,
                            uriHandler = uriHandler,
                            context = context,
                            highlighted = focusIndex == index,
                            dimmed = focusIndex != null && focusIndex != index,
                            launchingMaterialId = launchingMaterialId,
                            onOpenMaterial = ::openMaterial,
                            isDone = doneOverrides[entryId] ?: h.optBoolean("isDone"),
                            savingDone = entryId.isNotBlank() && entryId in savingDone,
                            onToggleDone = { target -> toggleDone(h, target) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkCard(
    h: JSONObject,
    uriHandler: UriHandler,
    context: Context,
    highlighted: Boolean,
    dimmed: Boolean,
    launchingMaterialId: String?,
    onOpenMaterial: (String, JSONObject) -> Unit,
    isDone: Boolean,
    savingDone: Boolean,
    onToggleDone: (Boolean) -> Unit
) {
    val description = h.optString("description").trim().let { if (it == ".") "" else it }
    val assigned = h.optString("assignedOn").takeIf { it.isNotBlank() && it != "null" }
    val due = h.optString("dueDate").takeIf { it.isNotBlank() && it != "null" }
    val materials = h.optJSONArray("materials")
    val attachments = h.optJSONArray("attachments")
    val homeworkEntryId = h.optString("homeworkEntryId").ifBlank { h.optString("id") }
    val cardAlpha = if (dimmed) 0.45f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(cardAlpha),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (highlighted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = { onToggleDone(it) },
                    enabled = !savingDone
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    h.optString("subjectName").ifBlank { "Задание" },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (savingDone) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
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
                val attachmentTypes = setOf("attachment", "attachments", "file", "files")
                val visible = (0 until materials.length())
                    .mapNotNull { materials.optJSONObject(it) }
                    .filter {
                        it.optString("type").lowercase(Locale.US) !in attachmentTypes &&
                            (it.optString("title").isNotBlank() || it.optString("typeName").isNotBlank())
                    }
                if (visible.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Материалы и ЦДЗ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    visible.forEach { material ->
                        val title = material.optString("title").ifBlank {
                            material.optString("typeName").ifBlank { "Материал" }
                        }
                        val url = material.optString("url").trim()
                        val materialId = material.optString("uuid")
                            .ifBlank { material.optString("materialId") }
                            .trim()
                        val canOpen = url.isNotBlank() || materialId.isNotBlank()
                        val action = material.optString("actionName").trim()
                        val typeLabel = material.optString("typeName").trim()
                        val opening = launchingMaterialId != null && launchingMaterialId == materialId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 40.dp)
                                .then(
                                    if (canOpen) {
                                        Modifier.clickable { onOpenMaterial(homeworkEntryId, material) }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                "• $title",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            val subline = when {
                                opening -> "Открываем…"
                                action.isNotBlank() && canOpen -> "$action · нажмите, чтобы открыть"
                                action.isNotBlank() -> action
                                typeLabel.isNotBlank() && canOpen -> "$typeLabel · нажмите, чтобы открыть"
                                typeLabel.isNotBlank() -> typeLabel
                                canOpen -> "Нажмите, чтобы открыть"
                                else -> ""
                            }
                            if (subline.isNotBlank()) {
                                Text(
                                    subline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 10.dp)
                                )
                            }
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
                            Modifier.clickable { openUrl(uriHandler, url, context) }
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

private data class RelativeDay(val offset: Int, val label: String)

private val RELATIVE_DAYS = listOf(
    RelativeDay(-2, "Позавчера"),
    RelativeDay(-1, "Вчера"),
    RelativeDay(0, "Сегодня"),
    RelativeDay(1, "Завтра"),
    RelativeDay(2, "Послезавтра")
)

/** Быстрые кнопки «Позавчера … Послезавтра»; выбранный день подсвечивается. */
@Composable
private fun RelativeDayChips(
    today: String,
    selectedDate: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RELATIVE_DAYS.forEach { day ->
            val date = MeshCore.addDays(today, day.offset)
            FilterChip(
                selected = selectedDate == date,
                onClick = { onSelect(date) },
                label = { Text(day.label) }
            )
        }
    }
}

private fun findHomeworkIndex(items: List<JSONObject>, target: HomeworkTarget): Int {
    fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    val targetText = normalize(target.text)
    val targetSubjectName = normalize(target.subjectName)
    val targetSubjectId = target.subjectId.trim()
    var bestIndex = -1
    var bestScore = 0

    items.forEachIndexed { index, homework ->
        val subjectId = homework.optString("subjectId")
        val subjectName = normalize(homework.optString("subjectName"))
        val text = normalize(homework.optString("description"))
        val sameId = targetSubjectId.isNotBlank() && subjectId == targetSubjectId
        val sameName = targetSubjectName.isNotBlank() &&
            (subjectName == targetSubjectName ||
                subjectName.contains(targetSubjectName) ||
                targetSubjectName.contains(subjectName))
        val sameText = targetText.isNotBlank() &&
            (text.contains(targetText) || targetText.contains(text))
        if (!sameId && !sameName && !sameText) return@forEachIndexed

        val score = when {
            sameText && (sameId || sameName) -> 4
            sameText -> 3
            sameId -> 2
            sameName -> 1
            else -> 0
        }
        if (score > bestScore) {
            bestIndex = index
            bestScore = score
        }
    }
    return bestIndex
}

private fun openUrl(uriHandler: UriHandler, url: String, context: Context) {
    if (url.isBlank()) return
    runCatching { uriHandler.openUri(url) }.onFailure {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}
