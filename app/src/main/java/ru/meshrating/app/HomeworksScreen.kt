package ru.meshrating.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun HomeworksScreen(store: SessionStore, client: MeshClient, studentId: String?) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var homeworks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val from = MeshCore.addDays(MeshCore.today(), -7)
    val to = MeshCore.today()

    LaunchedEffect(studentId) {
        if (studentId == null) { loading = false; return@LaunchedEffect }
        loading = true
        scope.launch {
            try {
                homeworks = withContext(Dispatchers.IO) { client.getHomeworks(studentId, from, to) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Домашние задания", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator()
        else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        else {
            LazyColumn {
                items(homeworks) { h ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(h.optString("subjectName"), fontWeight = FontWeight.Bold)
                            Text(h.optString("description"), style = MaterialTheme.typography.bodySmall)
                            val due = h.optString("dueDate").takeIf { it.isNotBlank() && it != "null" } ?: "—"
                            Text("Сдать до: $due", style = MaterialTheme.typography.bodySmall)
                            // ЦДЗ
                            val materials = h.optJSONArray("materials")
                            if (materials != null) {
                                for (i in 0 until materials.length()) {
                                    val mat = materials.optJSONObject(i) ?: continue
                                    val title = mat.optString("title").ifBlank { mat.optString("action_name") }
                                    val urls = mat.optJSONArray("urls")
                                    if (urls != null && urls.length() > 0) {
                                        for (j in 0 until urls.length()) {
                                            val u = urls.optJSONObject(j)?.optString("url") ?: ""
                                            if (u.isNotBlank()) {
                                                Text(title.ifBlank { "Открыть ЦДЗ" }, color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                                                    })
                                            }
                                        }
                                    } else if (title.isNotBlank()) {
                                        Text(title, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            // Вложения
                            val attachments = h.optJSONArray("attachments")
                            if (attachments != null) {
                                for (i in 0 until attachments.length()) {
                                    val a = attachments.optJSONObject(i) ?: continue
                                    val name = a.optString("name").ifBlank { a.optString("file_name") }
                                    val url = a.optString("url")
                                    if (url.isNotBlank()) {
                                        Text("📎 $name", color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            })
                                    } else if (name.isNotBlank()) {
                                        Text("📎 $name", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}