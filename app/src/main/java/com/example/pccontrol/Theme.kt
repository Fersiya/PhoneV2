package com.example.pccontrol

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Премиальная кибер-палитра Obsidian Glass & Neon Accents
val CyberDarkPalette = darkColorScheme(
    primary = Color(0xFF00F0FF),          // Электрический ультра-циан
    onPrimary = Color(0xFF002930),
    primaryContainer = Color(0xFF0E3846),
    onPrimaryContainer = Color(0xFFCEFBFF),
    secondary = Color(0xFFB388FF),        // Неоновый лавандовый индиго
    onSecondary = Color(0xFF280B61),
    secondaryContainer = Color(0xFF3F1F82),
    onSecondaryContainer = Color(0xFFEDE7F6),
    tertiary = Color(0xFF10B981),         // Изумрудно-мятный (онлайн, успех)
    onTertiary = Color(0xFF003822),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF0A0D16),       // Глубокий бархатный Obsidian Black
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF121726),          // Матовая сапфирово-графитовая поверхность
    onSurface = Color(0xFFF8FAFC),        // Кристальный четкий текст
    surfaceVariant = Color(0xFF1B2236),   // Элементы ввода, контейнеры, неактивные чипы
    onSurfaceVariant = Color(0xFF94A3B8), // Вспомогательный серебристо-серый
    outline = Color(0xFF26334D),          // Тонкие рамки с легким неоновым отблеском
    outlineVariant = Color(0xFF1A2234),
    error = Color(0xFFF43F5E),            // Яркий рубиново-розовый
    errorContainer = Color(0xFF881337),
    onErrorContainer = Color(0xFFFFE4E6)
)

// Дополнительные акцентные цвета
object CyberColors {
    val Cyan = Color(0xFF00F0FF)
    val Violet = Color(0xFFB388FF)
    val Emerald = Color(0xFF10B981)
    val Amber = Color(0xFFF59E0B)
    val Ruby = Color(0xFFF43F5E)
    val CardBg = Color(0xFF121726)
    val CardBorder = Color(0xFF26334D)
    val GlassDark = Color(0xCC0A0D16)
}
