package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ProfileScreen(store: SessionStore, client: MeshClient, initialProfile: JSONObject?, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("Профиль", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        ProfileBody(store, client, initialProfile)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Выйти") }
    }
}

/** Общая часть профиля: используется и на экране «Профиль», и в объединённых настройках. */
@Composable
fun ProfileBody(
    store: SessionStore,
    client: MeshClient,
    initialProfile: JSONObject?,
    showToken: Boolean = true
) {
    val scope = rememberCoroutineScope()
    var profile by remember(initialProfile) { mutableStateOf(initialProfile) }
    var loading by remember { mutableStateOf(initialProfile == null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                profile = withContext(Dispatchers.IO) { client.fetchProfile() }
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(initialProfile) {
        if (initialProfile != null) {
            profile = initialProfile
            loading = false
        } else {
            load()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            error != null -> {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { load() }) { Text("Повторить") }
            }
            profile != null -> {
                val p = profile!!
                val children = (0 until (p.optJSONArray("children")?.length() ?: 0))
                    .mapNotNull { p.optJSONArray("children")?.optJSONObject(it) }
                val child = p.optJSONObject("defaultChild") ?: children.firstOrNull()
                val parent = p.optJSONObject("parent")

                ProfileSection("Ученик") {
                    ProfileRow("ФИО", child?.optString("name").orEmpty().ifBlank { "—" })
                    ProfileRow("Класс", child?.optString("className").orEmpty().ifBlank { "—" })
                    val school = child?.optString("schoolName").orEmpty().ifBlank {
                        child?.optString("schoolShort").orEmpty()
                    }
                    ProfileRow("Школа", school.ifBlank { "—" })
                    val studentEmail = child?.optString("email").orEmpty()
                    val studentPhone = MeshCore.formatPhone(child?.optString("phone"))
                    if (studentEmail.isNotBlank()) ProfileRow("Электронная почта", studentEmail)
                    if (studentPhone.isNotBlank()) ProfileRow("Телефон", studentPhone)
                }

                if (children.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    ProfileSection("Дети") {
                        children.forEach { c ->
                            ProfileRow(c.optString("name").ifBlank { "Ученик" }, c.optString("className"))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ProfileSection("Родитель") {
                    ProfileRow("ФИО", parent?.optString("name").orEmpty().ifBlank { "—" })
                    val email = parent?.optString("email").orEmpty()
                    val phone = MeshCore.formatPhone(parent?.optString("phone"))
                    if (email.isNotBlank()) ProfileRow("Электронная почта", email)
                    if (phone.isNotBlank()) ProfileRow("Телефон", phone)
                }

                if (showToken) {
                    Spacer(Modifier.height(8.dp))
                    ProfileSection("Токен МЭШ") {
                        val info = MeshCore.describeToken(store.token)
                        ProfileRow("Маска", info.optString("mask").ifBlank { "—" })
                        ProfileRow(
                            "Тип",
                            when (info.optString("kind")) {
                                "jwt" -> "Подписанный ключ доступа"
                                "opaque" -> "Защищённая строка"
                                else -> "—"
                            }
                        )
                        if (info.has("expiresAt")) {
                            val expired = info.optBoolean("expired")
                            ProfileRow(
                                "Истекает",
                                MeshCore.dateShort(
                                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                        .format(java.util.Date(info.optLong("expiresAt")))
                                ) + if (expired) " (истёк)" else ""
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
