package ru.meshrating.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SettingsScreen(store: SessionStore, client: MeshClient, profile: JSONObject?, onLogout: () -> Unit, onThemeChange: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    val prefs = remember(context) { context.getSharedPreferences("mesh_settings", android.content.Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(prefs.getString("language", "ru") ?: "ru") }
    var theme by remember { mutableStateOf(prefs.getString("theme_id", "midnight") ?: "midnight") }
    var customName by remember { mutableStateOf(prefs.getString("custom_theme_name", "Моя тема") ?: "Моя тема") }
    var customColors by remember { mutableStateOf(prefs.getString("theme_colors", "67E8F9,071923,102A3A") ?: "67E8F9,071923,102A3A") }
    val english = language == "en"
    val tokenInfo = MeshCore.describeToken(store.token)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ProfileBody(store, client, profile, showToken = false)
        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(if (english) "Appearance" else "Оформление", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                listOf("midnight" to "Полночь", "ocean" to "Океан", "forest" to "Лес", "sunset" to "Закат", "light" to "Светлая", "custom" to customName).forEach { (id, label) ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = theme == id, onClick = { theme = id; prefs.edit().putString("theme_id", id).apply(); onThemeChange(id) })
                        Text(label)
                    }
                }
                OutlinedTextField(value = customName, onValueChange = { customName = it; prefs.edit().putString("custom_theme_name", it).apply() }, label = { Text(if (english) "Custom theme name" else "Название своей темы") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = customColors, onValueChange = { customColors = it; prefs.edit().putString("theme_colors", it).apply() }, label = { Text(if (english) "Primary, background, surface (hex)" else "Акцент, фон, поверхность (HEX через запятую)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(if (english) "Example: 67E8F9,071923,102A3A" else "Пример: 67E8F9,071923,102A3A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { theme = "custom"; prefs.edit().putString("theme_id", "custom").apply(); onThemeChange("custom") }, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Save and apply custom theme" else "Сохранить и применить свою тему") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(if (english) "Language" else "Язык", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                listOf("ru" to "Русский", "en" to "English").forEach { (code, label) ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = language == code, onClick = { language = code; prefs.edit().putString("language", code).apply() })
                        Text(label)
                    }
                }
                Text(if (english) "Navigation and settings labels update immediately." else "Подписи навигации и настроек переключаются сразу.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(if (english) "Application" else "Приложение", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                SettingsRow("Сервер", MeshCore.HOST.removePrefix("https://"))
                SettingsRow("Хранилище", if (store.isEncrypted()) "зашифровано" else "приватное")
                SettingsRow("Кэш имён", "включён")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (!checkingUpdates) {
                            checkingUpdates = true
                            updateMessage = null
                            scope.launch {
                                val found = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
                                store.lastUpdateCheck = System.currentTimeMillis()
                                updateInfo = found
                                updateMessage = if (found == null) "Обновлений нет" else null
                                checkingUpdates = false
                            }
                        }
                    },
                    enabled = !checkingUpdates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checkingUpdates) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Проверить обновления сейчас")
                }
                updateMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Токен МЭШ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                SettingsRow("Маска", tokenInfo.optString("mask").ifBlank { "—" })
                SettingsRow(
                    "Тип",
                    when (tokenInfo.optString("kind")) {
                        "jwt" -> "JWT"
                        "opaque" -> "opaque"
                        else -> "—"
                    }
                )
                if (tokenInfo.has("length")) SettingsRow("Длина", tokenInfo.optInt("length").toString())
                if (tokenInfo.has("expiresAt")) {
                    SettingsRow("Истекает", MeshCore.dateShort(
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date(tokenInfo.optLong("expiresAt")))
                    ))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        refreshing = true
                        message = null
                        error = null
                        scope.launch {
                            val fresh = withContext(Dispatchers.IO) { client.refreshToken() }
                            if (fresh.isNullOrBlank()) {
                                error = "МЭШ не выдал новый токен. Возможно, сессия завершена — войдите заново."
                            } else {
                                store.token = fresh
                                client.token = fresh
                                message = "Токен обновлён"
                            }
                            refreshing = false
                        }
                    },
                    enabled = !refreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (refreshing) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Обновить токен")
                }
                message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Аккаунт", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (profile != null) "Профиль загружен" else "Профиль ещё не загружен",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Выйти и удалить токен")
                }
            }
        }
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Доступно обновление") },
            text = {
                Text("На GitHub уже есть версия ${info.versionName}. Можно скачать и установить её.")
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
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
