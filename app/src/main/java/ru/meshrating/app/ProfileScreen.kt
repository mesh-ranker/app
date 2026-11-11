package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ProfileScreen(store: SessionStore, client: MeshClient, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        scope.launch {
            try {
                profile = withContext(Dispatchers.IO) { client.fetchProfile() }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Профиль", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator()
        else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        else {
            profile?.let { p ->
                val child = p.optJSONObject("defaultChild")
                Text("Ученик: ${child?.optString("name", "")}")
                Text("Класс: ${child?.optString("className", "")}")
                Text("Школа: ${child?.optString("schoolName", "")}")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                store.clear()
                onLogout()
            }) { Text("Выйти") }
        }
    }
}