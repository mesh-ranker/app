package ru.meshrating.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                AppRoot(store)
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
fun AppRoot(store: SessionStore) {
    var token by remember { mutableStateOf(store.token ?: "") }
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var children by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var activeStudentId by remember { mutableStateOf(store.studentId) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(if (store.token != null) "ranking" else "login") }
    val scope = rememberCoroutineScope()

    if (screen == "login") {
        LoginScreen(
            onLogin = { rawToken ->
                loading = true
                error = null
                scope.launch {
                    try {
                        val client = MeshClient(baseUrl = MeshCore.HOST, token = rawToken, profileId = null, timeoutMs = 20000, onToken = { newToken ->
                            store.token = newToken
                        })
                        val result = withContext(Dispatchers.IO) { client.signIn(rawToken) }
                        store.save(result.optString("token", rawToken), result.optString("profileId"), result.optString("childId"), result.optString("parentName"))
                        // fetch profile
                        val prof = withContext(Dispatchers.IO) { client.fetchProfile() }
                        profile = prof
                        children = (0 until prof.optJSONArray("children").length()).map { prof.optJSONArray("children").getJSONObject(it) }
                        activeStudentId = children.firstOrNull()?.optString("id")
                        store.studentId = activeStudentId
                        token = result.optString("token", rawToken)
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
        // Placeholder for now; will be replaced with real screens
        RankingScreenPlaceholder(onLogout = {
            store.clear()
            token = ""
            profile = null
            children = emptyList()
            activeStudentId = null
            screen = "login"
        })
    }
}

@Composable
fun LoginScreen(onLogin: (String) -> Unit, loading: Boolean, error: String?) {
    var tokenInput by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Вход в МЭШ", style = MaterialTheme.typography.headlineMedium)
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

@Composable
fun RankingScreenPlaceholder(onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Рейтинг (в разработке)", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogout) { Text("Выйти") }
    }
}