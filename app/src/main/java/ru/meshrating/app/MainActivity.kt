package ru.meshrating.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
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
    var activeStudentId by remember { mutableStateOf(store.studentId) }
    var activePersonId by remember { mutableStateOf(store.personId) }
    var booting by remember { mutableStateOf(store.token != null && (store.studentId == null || store.personId == null)) }
    var bootError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var loginLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(if (store.token != null) "main" else "login") }
    val scope = rememberCoroutineScope()

    val client = remember {
        MeshClient(
            baseUrl = MeshCore.HOST,
            token = store.token,
            profileId = store.studentId,
            nameCache = store.loadNameCache(),
            onToken = { newToken -> store.token = newToken },
            onName = { id, name -> store.saveName(id, name) }
        )
    }

    fun applyProfile(prof: JSONObject): Boolean {
        profile = prof
        val ch = (0 until (prof.optJSONArray("children")?.length() ?: 0))
            .mapNotNull { prof.optJSONArray("children")?.optJSONObject(it) }
        val child = ch.firstOrNull()
        val studentId = child?.optString("id")?.takeIf { it.isNotBlank() }
        val personId = child?.optString("personId")?.takeIf { it.isNotBlank() }
            ?: child?.optString("contingent_guid")?.takeIf { it.isNotBlank() }
        if (studentId != null) {
            activeStudentId = studentId
            store.studentId = studentId
            store.profileId = studentId
            client.profileId = studentId
        }
        if (personId != null) {
            activePersonId = personId
            store.personId = personId
        }
        store.save(
            store.token,
            studentId,
            studentId,
            personId,
            prof.optJSONObject("parent")?.optString("name")
        )
        return studentId != null && personId != null
    }

    fun logout() {
        store.clear()
        token = ""
        profile = null
        activeStudentId = null
        activePersonId = null
        client.token = null
        client.profileId = null
        screen = "login"
        bootError = null
        booting = false
        loginError = null
    }

    // При каждом старте приложения сначала добираем профиль. Если id ученика ещё нет,
    // экран рейтинга не показываем — иначе первый запрос уходит с пустым personId и МЭШ отвечает 400.
    LaunchedEffect(store.token, retryKey) {
        val savedToken = store.token
        if (savedToken.isNullOrBlank()) {
            screen = "login"
            booting = false
            return@LaunchedEffect
        }
        client.token = savedToken
        client.profileId = store.studentId
        val hasIds = !store.studentId.isNullOrBlank() && !store.personId.isNullOrBlank()
        if (!hasIds) {
            booting = true
            bootError = null
        }
        try {
            val prof = withContext(Dispatchers.IO) { client.fetchProfile() }
            if (!applyProfile(prof)) {
                bootError = "В профиле МЭШ не найден ученик. Войдите заново."
            } else {
                bootError = null
            }
        } catch (e: Exception) {
            val meshError = e as? MeshCore.MeshError
            if (meshError?.code == "unauthorized") {
                logout()
                return@LaunchedEffect
            }
            if (!hasIds) bootError = MeshCore.describeError(e)
        } finally {
            booting = false
        }
    }

    when {
        screen == "login" -> LoginScreen(
            onLogin = { rawToken ->
                loginLoading = true
                loginError = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { client.signIn(rawToken) }
                        val tokenValue = result.optString("token", rawToken)
                        val prof = result.optJSONObject("profile")
                            ?: withContext(Dispatchers.IO) { client.fetchProfile() }
                        store.token = tokenValue
                        client.token = tokenValue
                        token = tokenValue
                        if (!applyProfile(prof)) {
                            throw MeshCore.MeshError(
                                "В профиле МЭШ не найден ученик. Проверьте, что вы вошли как родитель.",
                                "no-student"
                            )
                        }
                        bootError = null
                        screen = "main"
                    } catch (e: Exception) {
                        loginError = MeshCore.describeError(e)
                    } finally {
                        loginLoading = false
                    }
                }
            },
            loading = loginLoading,
            error = loginError
        )

        booting -> BootScreen()

        bootError != null && (activePersonId == null || activeStudentId == null) -> BootErrorScreen(
            message = bootError ?: "Не удалось загрузить профиль",
            onRetry = { retryKey++ },
            onLogout = { logout() }
        )

        else -> MainScaffold(
            store = store,
            client = client,
            profile = profile,
            personId = activePersonId,
            studentId = activeStudentId,
            onLogout = { logout() }
        )
    }
}

@Composable
fun BootScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Загружаем профиль МЭШ…")
    }
}

@Composable
fun BootErrorScreen(message: String, onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Не удалось открыть профиль", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Повторить") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onLogout) { Text("Войти заново") }
    }
}

@Composable
fun MainScaffold(
    store: SessionStore,
    client: MeshClient,
    profile: JSONObject?,
    personId: String?,
    studentId: String?,
    onLogout: () -> Unit
) {
    var currentTab by remember { mutableStateOf("ranking") }
    var rankingSubjectId by remember { mutableStateOf<String?>(null) }
    var homeworkTarget by remember { mutableStateOf<HomeworkTarget?>(null) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf("ranking" to "Рейтинг", "subjects" to "Предметы", "marks" to "Оценки", "homeworks" to "ДЗ", "schedule" to "Расписание", "profile" to "Профиль", "settings" to "Настройки")
                items.forEach { (key, label) ->
                    NavigationBarItem(
                        selected = currentTab == key,
                        onClick = {
                            currentTab = key
                            if (key == "ranking") rankingSubjectId = null
                            if (key != "homeworks") homeworkTarget = null
                        },
                        label = { Text(label) },
                        icon = { }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                "ranking" -> RankingScreen(
                    store,
                    client,
                    personId,
                    onLogout,
                    selectedSubjectId = rankingSubjectId,
                    onSubjectChange = { rankingSubjectId = it }
                )
                "subjects" -> SubjectsScreen(
                    store,
                    client,
                    personId,
                    onOpenSubject = { id, _ ->
                        if (id.isNotBlank()) {
                            rankingSubjectId = id
                            currentTab = "ranking"
                        }
                    }
                )
                "marks" -> MarksScreen(store, client, studentId)
                "homeworks" -> HomeworksScreen(
                    store,
                    client,
                    studentId,
                    target = homeworkTarget,
                    onTargetConsumed = { homeworkTarget = null }
                )
                "schedule" -> ScheduleScreen(
                    store,
                    client,
                    studentId,
                    onHomeworkClick = {
                        homeworkTarget = it
                        currentTab = "homeworks"
                    }
                )
                "profile" -> ProfileScreen(store, client, profile, onLogout)
                "settings" -> SettingsScreen(store, client, profile, onLogout)
            }
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
        val context = LocalContext.current
        val url = MeshCore.loginUrl()
        Text(
            text = "1. Откройте: $url",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        )
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