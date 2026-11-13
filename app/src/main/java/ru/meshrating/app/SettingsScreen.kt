package ru.meshrating.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SettingsScreen(store: SessionStore, client: MeshClient, profile: JSONObject?, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    val tokenInfo = MeshCore.describeToken(store.token)

    // Оформление и язык хранятся в общих настройках (mesh_settings) и применяются
    // глобально через CompositionLocal из MeshRankerTheme.
    val prefs = remember(context) { context.getSharedPreferences("mesh_settings", android.content.Context.MODE_PRIVATE) }
    val applyTheme = LocalAppTheme.current
    val applyLanguage = LocalAppLanguageSetter.current
    var theme by remember { mutableStateOf(prefs.getString("theme_id", AppTheme.DEFAULT_ID) ?: AppTheme.DEFAULT_ID) }
    var customName by remember { mutableStateOf(prefs.getString("custom_theme_name", "") ?: "") }
    var customColors by remember {
        mutableStateOf(prefs.getString("theme_colors", "67E8F9,071923,102A3A") ?: "67E8F9,071923,102A3A")
    }
    var language by remember { mutableStateOf(LocalAppLanguage.current) }
    val noUpdatesText = tr("Обновлений нет", "No updates")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ProfileBody(store, client, profile, showToken = false)
        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    tr("Оформление", "Appearance"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                AppTheme.ids.forEach { id ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            theme = id
                            prefs.edit().putString("theme_id", id).apply()
                            applyTheme(id)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = theme == id, onClick = null)
                        Spacer(Modifier.width(6.dp))
                        Text(AppTheme.label(id, customName.ifBlank { tr("Моя тема", "My theme") }))
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it; prefs.edit().putString("custom_theme_name", it).apply() },
                    label = { Text(tr("Название своей темы", "Custom theme name")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = customColors,
                    onValueChange = { customColors = it; prefs.edit().putString("theme_colors", it).apply() },
                    label = { Text(tr("Акцент, фон, поверхность (HEX)", "Accent, background, surface (HEX)")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    tr("Пример: 67E8F9,071923,102A3A", "Example: 67E8F9,071923,102A3A"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        theme = "custom"
                        prefs.edit().putString("theme_id", "custom").apply()
                        applyTheme("custom")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Применить свою тему", "Apply custom theme"))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    tr("Язык", "Language"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                listOf("ru" to "Русский", "en" to "English").forEach { (code, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            language = code
                            prefs.edit().putString("language", code).apply()
                            applyLanguage(code)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = language == code, onClick = null)
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }
                Text(
                    tr(
                        "Подписи навигации и настроек переключаются сразу.",
                        "Navigation and settings labels update immediately."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    tr("Приложение", "Application"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                SettingsRow(tr("Сервер", "Server"), MeshCore.HOST.removePrefix("https://"))
                SettingsRow(
                    tr("Хранилище", "Storage"),
                    if (store.isEncrypted()) tr("зашифровано", "encrypted") else tr("приватное", "private")
                )
                SettingsRow(tr("Кэш имён", "Name cache"), tr("включён", "on"))
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
                                updateMessage = if (found == null) noUpdatesText else null
                                checkingUpdates = false
                            }
                        }
                    },
                    enabled = !checkingUpdates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checkingUpdates) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text(tr("Проверить обновления сейчас", "Check for updates now"))
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
                Text(tr("Токен МЭШ", "MESH token"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                SettingsRow(tr("Маска", "Mask"), tokenInfo.optString("mask").ifBlank { "—" })
                SettingsRow(
                    tr("Тип", "Type"),
                    when (tokenInfo.optString("kind")) {
                        "jwt" -> "JWT"
                        "opaque" -> "opaque"
                        else -> "—"
                    }
                )
                if (tokenInfo.has("length")) SettingsRow(tr("Длина", "Length"), tokenInfo.optInt("length").toString())
                if (tokenInfo.has("expiresAt")) {
                    SettingsRow(tr("Истекает", "Expires"), MeshCore.dateShort(
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
                    else Text(tr("Обновить токен", "Refresh token"))
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
                Text(tr("Аккаунт", "Account"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (profile != null) tr("Профиль загружен", "Profile loaded")
                    else tr("Профиль ещё не загружен", "Profile not loaded yet"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Выйти и удалить токен", "Sign out and delete token"))
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
