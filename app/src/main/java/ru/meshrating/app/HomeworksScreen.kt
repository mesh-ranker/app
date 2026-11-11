package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun HomeworksScreen(store: SessionStore, client: MeshClient, studentId: String?) {
    val scope = rememberCoroutineScope()
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
                            Text(h.optString("subjectName"), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(h.optString("description"), style = MaterialTheme.typography.bodySmall)
                            Text("Сдать до: ${h.optString("dueDate", "—")}", style = MaterialTheme.typography.bodySmall)
                            Text(if (h.optBoolean("isDone")) "выполнено" else "не выполнено", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}