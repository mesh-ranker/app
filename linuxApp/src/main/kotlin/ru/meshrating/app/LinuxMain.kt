package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

private data class StoredSession(val token: String, val student: String, val person: String)
private val sessionFile = File(System.getProperty("user.home"), ".config/mesh-ranker/session.json")

private fun readSession(): StoredSession? = runCatching {
    val json = JSONObject(sessionFile.readText())
    StoredSession(json.getString("token"), json.getString("student"), json.getString("person"))
}.getOrNull()

private fun writeSession(value: StoredSession?) {
    if (value == null) sessionFile.delete()
    else {
        sessionFile.parentFile?.mkdirs()
        sessionFile.writeText(JSONObject().put("token", value.token).put("student", value.student).put("person", value.person).toString())
        sessionFile.setReadable(false, false); sessionFile.setReadable(true, true)
        sessionFile.setWritable(false, false); sessionFile.setWritable(true, true)
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "MESH Ranker") {
        MaterialTheme(colorScheme = darkColorScheme()) { LinuxApp() }
    }
}

@Composable
private fun LinuxApp() {
    var session by remember { mutableStateOf(readSession()) }
    var token by remember { mutableStateOf("") }
    var client by remember { mutableStateOf<MeshClient?>(null) }
    var selected by remember { mutableStateOf("Рейтинг") }
    var payload by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session) {
        session?.let { saved ->
            val api = MeshClient(token = saved.token, profileId = saved.student)
            client = api; loading = true
            runCatching { withContext(Dispatchers.IO) { api.fetchProfile() } }.onFailure { error = MeshCore.describeError(it) }
            loading = false
        }
    }
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("MESH Ranker для Linux", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Вставьте токен МЭШ из school.mos.ru. Сессия хранится в ~/.config/mesh-ranker.")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(token, { token = it }, label = { Text("Токен") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !loading && token.isNotBlank(), onClick = {
                loading = true; error = null
                scope.launch {
                    try {
                        val api = MeshClient(token = token.trim())
                        val result = withContext(Dispatchers.IO) { api.signIn(token.trim()) }
                        val profile = result.optJSONObject("profile") ?: withContext(Dispatchers.IO) { api.fetchProfile() }
                        val child = profile.optJSONArray("children")?.optJSONObject(0) ?: kotlin.error("В профиле не найден ученик")
                        val student = child.optString("id").takeIf { it.isNotBlank() } ?: kotlin.error("Нет ID ученика")
                        val person = (child.optString("personId").takeIf { it.isNotBlank() }
                            ?: child.optString("contingent_guid").takeIf { it.isNotBlank() }) ?: kotlin.error("Нет ID профиля")
                        session = StoredSession(result.optString("token", token.trim()), student, person); writeSession(session)
                    } catch (e: Exception) { error = MeshCore.describeError(e) }
                    finally { loading = false }
                }
            }) { Text(if (loading) "Входим…" else "Войти") }
        }
        return
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(200.dp).fillMaxHeight().padding(16.dp)) {
            Text("MESH Ranker", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            listOf("Рейтинг", "Оценки", "Домашние задания", "Расписание", "Предметы", "Профиль").forEach { item ->
                TextButton(onClick = { selected = item; payload = ""; error = null }) { Text(item) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { writeSession(null); session = null; client = null }) { Text("Выйти") }
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(selected, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Button(enabled = !loading, onClick = {
                val api = client ?: return@Button
                loading = true; error = null
                scope.launch {
                    try {
                        val value = withContext(Dispatchers.IO) {
                            when (selected) {
                                "Рейтинг" -> api.getRank(session!!.person)
                                "Оценки" -> JSONObject().put("marks", JSONArray(api.getMarks(session!!.student, LocalDate.now().minusMonths(1).toString(), MeshCore.today()).toTypedArray()))
                                "Домашние задания" -> JSONObject().put("homeworks", JSONArray(api.getHomeworks(session!!.student, MeshCore.today(), LocalDate.now().plusMonths(1).toString()).toTypedArray()))
                                "Расписание" -> api.getSchedule(session!!.student)
                                "Предметы" -> api.getSubjects(session!!.person)
                                else -> api.fetchProfile()
                            }
                        }
                        payload = when (value) { is JSONObject -> value.toString(2); is JSONArray -> value.toString(2); else -> value.toString() }
                    } catch (e: Exception) { error = MeshCore.describeError(e) }
                    finally { loading = false }
                }
            }) { Text(if (loading) "Загрузка…" else "Обновить") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
            Text(payload.ifBlank { "Нажмите «Обновить», чтобы загрузить данные МЭШ." }, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp))
        }
    }
}
