package ru.meshrating.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Цель перехода из расписания в конкретное ДЗ. */
data class HomeworkTarget(
    val date: String,
    val subjectId: String = "",
    val subjectName: String = "",
    val text: String = ""
)

/** 5.0 → «5», 4.50 → «4.5», 4.37 → «4.37». */
fun formatMark(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "—"
    val rounded = Math.round(value * 100.0) / 100.0
    if (rounded == Math.floor(rounded)) return rounded.toInt().toString()
    return String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
}

fun markColor(value: Double?): Color = when {
    value == null -> Color(0xFF9AA7BD)
    value >= 4.5 -> Color(0xFF34D399)
    value >= 3.5 -> Color(0xFFFBBF24)
    else -> Color(0xFFF87171)
}

/** Оценка/средний балл в аккуратном бейдже справа. */
@Composable
fun GradeBadge(text: String, value: Double?, modifier: Modifier = Modifier) {
    val color = markColor(value)
    Box(
        modifier = modifier
            .widthIn(min = 48.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** Переключение дня стрелками — одинаковое в расписании и ДЗ. */
@Composable
fun DaySelector(
    date: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Text("‹", style = MaterialTheme.typography.headlineMedium)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(MeshCore.dateRu(date, weekday = true), style = MaterialTheme.typography.titleMedium)
            if (date == MeshCore.today()) {
                Text("Сегодня", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = onNext) {
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
