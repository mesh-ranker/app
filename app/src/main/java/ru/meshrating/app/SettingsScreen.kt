package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(store: SessionStore) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Тема: тёмная (по умолчанию)", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("Версия: 1.0.0", style = MaterialTheme.typography.bodySmall)
    }
}