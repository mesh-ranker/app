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
fun SubjectsScreen(store: SessionStore, client: MeshClient, personId: String?) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var subjects by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    LaunchedEffect(personId) {
        if (personId == null) { loading = false; return@LaunchedEffect }
        loading = true
        scope.launch {
            try {
                val arr = withContext(Dispatchers.IO) { client.getSubjects(personId, MeshCore.today()) }
                subjects = (0 until arr.length()).map { arr.getJSONObject(it) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Предметы", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator()
        else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        else {
            LazyColumn {
                items(subjects) { s ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.optString("name"))
                            Text(
                                String.format("%.2f", s.optDouble("average", 0.0)),
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    s.optDouble("average", 0.0) >= 4.5 -> Color(0xFF34D399)
                                    s.optDouble("average", 0.0) >= 3.5 -> Color(0xFFFBBF24)
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