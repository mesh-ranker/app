package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun MarksScreen(store: SessionStore, client: MeshClient, studentId: String?) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var marks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val from = MeshCore.addDays(MeshCore.today(), -30)
    val to = MeshCore.today()

    LaunchedEffect(studentId) {
        if (studentId == null) { loading = false; return@LaunchedEffect }
        loading = true
        scope.launch {
            try {
                marks = withContext(Dispatchers.IO) { client.getMarks(studentId, from, to) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Оценки", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator()
        else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        else {
            LazyColumn {
                items(marks) { m ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(m.optString("subjectName"))
                                Text(m.optString("date"), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                m.opt("value")?.toString() ?: "—",
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    (m.optDouble("value", 0.0)) >= 4.5 -> Color(0xFF34D399)
                                    (m.optDouble("value", 0.0)) >= 3.5 -> Color(0xFFFBBF24)
                                    else -> Color(0xFFF87171)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}