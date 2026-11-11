package ru.meshrating.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(this)
        setContent {
            MeshRankerTheme {
                AppRoot(store, this)
            }
        }
    }
}

@Composable
fun MeshRankerTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFFD1D5DB),
        background = Color(0xFF10131A),
        surface = Color(0xFF1B2130),
        onPrimary = Color(0xFF111111),
        onBackground = Color(0xFFE8EDF7),
        onSurface = Color(0xFFE8EDF7)
    )
    MaterialTheme(colorScheme = darkColors, content = content)
}

@Composable
fun AppRoot(store: SessionStore, activity: MainActivity) {
    var token by remember { mutableStateOf(store.token ?: "") }
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var children by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var activeStudentId by remember { mutableStateOf(store.studentId) }
    var activePersonId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(if (store.token != null) "ranking" else "login") }
    val scope = rememberCoroutineScope()

    val client = remember {
        MeshClient(
            baseUrl = MeshCore.HOST,
            token = store.token,
            profileId = store.studentId,
            onToken = { newToken -> store.token = newToken }
        )
    }

    if (screen == "login") {
        LoginScreen(
            onLogin = { rawToken ->
                loading = true
                error = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { client.signIn(rawToken) }
                        store.save(result.optString("token", rawToken), result.optString("profileId"), result.optString("child", null)?.let { JSONObject(it).optString("id") }, null)
                        val prof = withContext(Dispatchers.IO) { client.fetchProfile() }
                        profile = prof
                        children = (0 until prof.optJSONArray("children").length()).map { prof.optJSONArray("children").getJSONObject(it) }
                        activeStudentId = children.firstOrNull()?.optString("id")
                        activePersonId = children.firstOrNull()?.optString("personId")
                        store.studentId = activeStudentId
                        store.profileId = activeStudentId
                        token = result.optString("token", rawToken)
                        client.token = token
                        client.profileId = activeStudentId
                        screen = "ranking"
                    } catch (e: Exception) {
                        error = e.message ?: "Ошибка входа"
                    } finally {
                        loading = false
                    }
                }
            },
            loading = loading,
            error = error
        )
    } else {
        MainScaffold(store, client, activePersonId, activeStudentId, onLogout = {
            store.clear()
            token = ""
            profile = null
            children = emptyList()
            activeStudentId = null
            activePersonId = null
            screen = "login"
        })
    }
}

@Composable
fun MainScaffold(store: SessionStore, client: MeshClient, personId: String?, studentId: String?, onLogout: () -> Unit) {
    var currentTab by remember { mutableStateOf("ranking") }
    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf("ranking" to "Рейтинг", "subjects" to "Предметы", "marks" to "Оценки", "homeworks" to "ДЗ", "schedule" to "Расписание", "profile" to "Профиль", "settings" to "Настройки")
                items.forEach { (key, label) ->
                    NavigationBarItem(
                        selected = currentTab == key,
                        onClick = { currentTab = key },
                        label = { Text(label) },
                        icon = { }
                    )
                }
            }
        }
    ) { padding ->
        when (currentTab) {
            "ranking" -> RankingScreen(store, client, personId, onLogout)
            "subjects" -> SubjectsScreen(store, client, personId)
            "marks" -> MarksScreen(store, client, studentId)
            "homeworks" -> HomeworksScreen(store, client, studentId)
            "schedule" -> ScheduleScreen(store, client, studentId)
            "profile" -> ProfileScreen(store, client, onLogout)
            "settings" -> SettingsScreen(store)
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String) -> Unit, loading: Boolean, error: String?) {
    var tokenInput by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Вход в МЭШ", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("1. Откройте https://school.mos.ru/v2/token/refresh?roleId=2&subsystem=2", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("2. Скопируйте токен и вставьте сюда", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("Токен МЭШ") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onLogin(tokenInput) },
            enabled = !loading && tokenInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else Text("Войти")
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}