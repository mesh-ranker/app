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
fun ScheduleScreen(store: SessionStore, client: MeshClient, studentId: String?) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lessons by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val date = MeshCore.today()

    LaunchedEffect(studentId) {
        if (studentId == null) { loading = false; return@LaunchedEffect }
        loading = true
        scope.launch {
            try {
                val arr = withContext(Dispatchers.IO) { client.getSchedule(studentId, date) }
                lessons = (0 until arr.length()).map { arr.getJSONObject(it) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Расписание", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator()
        else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        else {
            LazyColumn {
                items(lessons) { l ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(l.optString("subject"))
                                Text("${l.optString("begin", "—")}–${l.optString("end", "")}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(l.optString("room"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}