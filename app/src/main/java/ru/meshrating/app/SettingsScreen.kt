package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(store: SessionStore) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        scope.launch {
            try {
                val client = MeshClient(baseUrl = MeshCore.HOST, token = store.token, profileId = store.studentId)
                profile = withContext(Dispatchers.IO) { client.fetchProfile() }
            } catch (_: Exception) { }
            finally { loading = false }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator()
        else {
            profile?.let { p ->
                val child = p.optJSONObject("defaultChild")
                Text("Класс: ${child?.optString("className", "")}")
                Text("Школа: ${child?.optString("schoolName", "")}")
            }
            Spacer(Modifier.height(8.dp))
            Text("Токен: ${store.token?.take(8) ?: "—"}…")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { store.clear() }) { Text("Выйти и удалить токен") }
        }
    }
}