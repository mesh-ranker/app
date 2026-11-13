package ru.meshrating.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Система оформления приложения.
 *
 * Палитры хранятся в настройках ключом `theme_id`, пользовательская тема — строкой
 * `theme_colors` вида `АКЦЕНТ,ФОН,ПОВЕРХНОСТЬ` (HEX без решётки). Идентификаторы
 * совпадают с desktop/desktop-версией, чтобы настройки переносились между клиентами.
 */
object AppTheme {
    const val DEFAULT_ID = "midnight"

    /** Порядок вариантов в списке настроек. */
    val ids = listOf("midnight", "ocean", "forest", "sunset", "light", "custom")

    /** Соответствие «встроенный id → тройка цветов (акцент, фон, поверхность)». */
    private val palettes = mapOf(
        "midnight" to Triple(0xFFD1D5DB, 0xFF10131A, 0xFF1B2130),
        "ocean" to Triple(0xFF67E8F9, 0xFF071923, 0xFF102A3A),
        "forest" to Triple(0xFF86EFAC, 0xFF0D1B16, 0xFF183126),
        "sunset" to Triple(0xFFFDA4AF, 0xFF211318, 0xFF352027),
        "light" to Triple(0xFF2563EB, 0xFFF5F7FB, 0xFFFFFFFF)
    )

    /** Цветовая схема Material 3 по id темы и (для `custom`) строке пользовательских цветов. */
    fun colors(id: String, custom: String?): ColorScheme {
        val palette = if (id == "custom" && !custom.isNullOrBlank()) {
            parseCustom(custom) ?: palettes.getValue(DEFAULT_ID)
        } else {
            palettes[id] ?: palettes.getValue(DEFAULT_ID)
        }
        val primary = Color(palette.first)
        val background = Color(palette.second)
        val surface = Color(palette.third)
        return if (id == "light") {
            lightColorScheme(primary = primary, background = background, surface = surface)
        } else {
            darkColorScheme(
                primary = primary,
                background = background,
                surface = surface,
                onPrimary = Color(0xFF111111),
                onBackground = Color(0xFFE8EDF7),
                onSurface = Color(0xFFE8EDF7)
            )
        }
    }

    /** `"67E8F9,071923,102A3A"` → Triple. Неверный формат → null (палитра по умолчанию). */
    private fun parseCustom(value: String): Triple<Long, Long, Long>? = runCatching {
        val parts = value.split(",").map { it.trim().removePrefix("#").toLong(16) }
        if (parts.size < 3) null else Triple(parts[0], parts[1], parts[2])
    }.getOrNull()

    /** Человекочитаемое имя темы для настроек. */
    fun label(id: String, customName: String = "Моя тема"): String = when (id) {
        "midnight" -> "Полночь"
        "ocean" -> "Океан"
        "forest" -> "Лес"
        "sunset" -> "Закат"
        "light" -> "Светлая"
        "custom" -> customName.ifBlank { "Моя тема" }
        else -> id
    }
}

/** Применение темы из настроек: сохраняет id и перечитывает цвета. */
val LocalAppTheme = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Текущий язык интерфейса: `ru` или `en`. */
val LocalAppLanguage = staticCompositionLocalOf { "ru" }

/** Смена языка из настроек. */
val LocalAppLanguageSetter = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Выбор строки интерфейса по текущему языку. */
@Composable
fun tr(ru: String, en: String): String = if (LocalAppLanguage.current == "en") en else ru

/** Короткая подпись языка для селектора. */
fun languageLabel(code: String): String = if (code == "en") "English" else "Русский"
