package ru.meshrating.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeshRankerTheme {
                ComposeApp(this)
            }
        }
    }
}

@Composable
fun MeshRankerTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFFD1D5DB),
        background = Color(0xFF10131A),
        surface = Color(0xFF1B2130),
        onPrimary = Color(0xFF111111),
        onBackground = Color(0xFFE8EDF7),
        onSurface = Color(0xFFE8EDF7)
    )
    MaterialTheme(colorScheme = darkColors, content = content)
}

@Composable
fun ComposeApp(activity: MainActivity) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf("Рейтинг", "Предметы", "Оценки", "ДЗ", "Расписание", "Профиль", "Настройки")
                items.forEach { label ->
                    NavigationBarItem(
                        selected = false,
                        onClick = { },
                        label = { Text(label) },
                        icon = { }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Нативная версия в разработке", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Полный функционал пока в WebView-версии.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                activity.startActivity(Intent(activity, WebViewActivity::class.java))
            }) { Text("Открыть полную версию (WebView)") }
        }
    }
}