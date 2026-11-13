package ru.meshrating.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mesh_settings", android.content.Context.MODE_PRIVATE) }
    var themeId by remember { mutableStateOf(prefs.getString("theme_id", AppTheme.DEFAULT_ID) ?: AppTheme.DEFAULT_ID) }
    var themeColors by remember { mutableStateOf(prefs.getString("theme_colors", null)) }
    var language by remember { mutableStateOf(prefs.getString("language", "ru") ?: "ru") }
    val colors = remember(themeId, themeColors) { AppTheme.colors(themeId, themeColors) }

    val applyTheme: (String) -> Unit = { id ->
        themeId = id
        prefs.edit().putString("theme_id", id).apply()
        // Перечитываем пользовательские цвета: их могли изменить в настройках.
        themeColors = prefs.getString("theme_colors", null)
    }
    val applyLanguage: (String) -> Unit = { code ->
        language = code
        prefs.edit().putString("language", code).apply()
    }

    CompositionLocalProvider(
        LocalAppTheme provides applyTheme,
        LocalAppLanguage provides language,
        LocalAppLanguageSetter provides applyLanguage
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
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
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

    // Проверка обновлений на GitHub Releases: не чаще одного раза в 15 минут.
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        if (now - store.lastUpdateCheck < UpdateChecker.CHECK_INTERVAL_MS) return@LaunchedEffect
        store.lastUpdateCheck = now
        val found = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
        if (found != null) updateInfo = found
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

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Доступно обновление") },
            text = {
                Text(
                    "На GitHub уже есть версия ${info.versionName}. " +
                        "Можно скачать и установить её по ссылке."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateInfo = null
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                        }
                    }
                ) {
                    Text("Скачать")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text("Позже")
                }
            }
        )
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

/** Состояние экрана для истории кнопки/жеста «назад». */
private data class NavState(
    val tab: String,
    val rankingSubjectId: String? = null,
    val marksSubjectId: String? = null,
    val marksSubjectName: String? = null,
    val target: HomeworkTarget? = null
)

@OptIn(ExperimentalMaterial3Api::class)
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
    var marksSubjectId by remember { mutableStateOf<String?>(null) }
    var marksSubjectName by remember { mutableStateOf<String?>(null) }
    var homeworkTarget by remember { mutableStateOf<HomeworkTarget?>(null) }
    val backStack = remember { mutableStateListOf<NavState>() }

    fun currentState() = NavState(
        tab = currentTab,
        rankingSubjectId = rankingSubjectId,
        marksSubjectId = marksSubjectId,
        marksSubjectName = marksSubjectName,
        target = homeworkTarget
    )

    fun navigateTo(next: NavState) {
        if (next == currentState()) return
        backStack.add(currentState())
        currentTab = next.tab
        rankingSubjectId = next.rankingSubjectId
        marksSubjectId = next.marksSubjectId
        marksSubjectName = next.marksSubjectName
        homeworkTarget = next.target
    }

    val hasContext = backStack.isNotEmpty() || rankingSubjectId != null ||
        marksSubjectId != null || homeworkTarget != null

    // Жест/кнопка «назад» сначала проходят по истории экранов, а не сворачивают приложение.
    BackHandler(enabled = hasContext) {
        if (backStack.isNotEmpty()) {
            val previous = backStack.removeAt(backStack.lastIndex)
            currentTab = previous.tab
            rankingSubjectId = previous.rankingSubjectId
            marksSubjectId = previous.marksSubjectId
            marksSubjectName = previous.marksSubjectName
            homeworkTarget = previous.target
        } else {
            rankingSubjectId = null
            marksSubjectId = null
            marksSubjectName = null
            homeworkTarget = null
        }
    }

    val screenTitle = when (currentTab) {
        "ranking" -> tr("Рейтинг", "Ranking")
        "marks" -> if (marksSubjectName.isNullOrBlank()) tr("Оценки", "Marks") else "${tr("Оценки", "Marks")} · $marksSubjectName"
        "homeworks" -> tr("Домашние задания", "Homework")
        "schedule" -> tr("Расписание", "Schedule")
        "food" -> tr("Еда и баланс", "Food & balance")
        "settings" -> tr("Настройки", "Settings")
        else -> tr("МЭШ Рейтинг", "MESH Ranking")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (currentTab == "marks" && marksSubjectId != null) {
                        TextButton(
                            onClick = {
                                navigateTo(NavState(tab = "ranking", rankingSubjectId = marksSubjectId))
                            }
                        ) {
                            Text("Рейтинг")
                        }
                    }
                    IconButton(onClick = { navigateTo(NavState(tab = "settings")) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Настройки и профиль"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Triple("ranking", tr("Рейтинг", "Rating"), R.drawable.ic_nav_ranking),
                    Triple("marks", tr("Оценки", "Marks"), R.drawable.ic_nav_marks),
                    Triple("homeworks", tr("ДЗ", "Homework"), R.drawable.ic_nav_homeworks),
                    Triple("schedule", tr("Расписание", "Schedule"), R.drawable.ic_nav_schedule)
                )
                items.forEach { (key, label, iconRes) ->
                    NavigationBarItem(
                        selected = currentTab == key,
                        onClick = { navigateTo(NavState(tab = key)) },
                        label = { Text(label) },
                        icon = {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = label
                            )
                        }
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
                "marks" -> MarksScreen(
                    store,
                    client,
                    studentId,
                    personId = personId,
                    subjectId = marksSubjectId,
                    onOpenSubject = { id, name ->
                        if (id.isNotBlank()) {
                            navigateTo(
                                NavState(
                                    tab = "marks",
                                    marksSubjectId = id,
                                    marksSubjectName = name
                                )
                            )
                        }
                    }
                )
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
                    onHomeworkClick = { navigateTo(NavState(tab = "homeworks", target = it)) },
                    onFoodClick = { navigateTo(NavState(tab = "food")) }
                )
                "food" -> FoodScreen(store, client, personId)
                "settings" -> SettingsScreen(store, client, profile, onLogout)
            }
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String) -> Unit, loading: Boolean, error: String?) {
    var tokenInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val url = MeshCore.loginUrl()
    val webLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = result.data?.getStringExtra(SchoolLoginActivity.EXTRA_TOKEN)
            if (!token.isNullOrBlank()) onLogin(token)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Вход в МЭШ", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))

        Text(
            "Способ 1. Вставить токен вручную",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Откройте: $url",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        )
        Text(
            "Скопируйте токен со страницы и вставьте сюда",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("Токен МЭШ") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onLogin(tokenInput) },
            enabled = !loading && tokenInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else Text("Войти с токеном")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Способ 2. Войти через сайт МЭШ",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Откроется school.mos.ru. После входа приложение перехватит Authorization-токен и само закроет окно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                webLogin.launch(Intent(context, SchoolLoginActivity::class.java))
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Открыть school.mos.ru")
        }

        Spacer(Modifier.height(24.dp))
        QrLoginPanel(onLogin = onLogin)

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}