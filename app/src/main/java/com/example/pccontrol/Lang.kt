package com.example.pccontrol

import android.content.SharedPreferences
import java.util.Locale

/** Язык интерфейса: «auto» (по системе), «ru» или «en». По умолчанию русский (так же ведут себя юнит-тесты). */
object AppLang {
    @Volatile var english: Boolean = false

    fun init(prefs: SharedPreferences) {
        english = when (prefs.getString("lang", "auto")) {
            "en" -> true
            "ru" -> false
            else -> Locale.getDefault().language !in setOf("ru", "uk", "be", "kk")
        }
    }
}

/** Переводит русский текст интерфейса; в русском режиме возвращает его как есть. */
fun L(s: String): String = if (AppLang.english) Translations.translate(s) else s

object Translations {
    private val exact: Map<String, String> by lazy { EN.filterKeys { !it.contains("{0}") } }
    private val patterns: List<Pair<Regex, String>> by lazy {
        EN.filterKeys { it.contains("{0}") }.map { (k, v) ->
            val parts = k.split(Regex("""\{\d+\}"""))
            val rx = Regex("^" + parts.joinToString("(.+?)") { Regex.escape(it) } + "$", RegexOption.DOT_MATCHES_ALL)
            Triple(rx, v, parts.sumOf { it.length })
        }.sortedByDescending { it.third }.map { it.first to it.second }
    }

    fun translate(s: String): String {
        exact[s]?.let { return it }
        for ((rx, en) in patterns) {
            val m = rx.matchEntire(s) ?: continue
            var out = en
            for (i in 1 until m.groupValues.size) out = out.replace("{${i - 1}}", m.groupValues[i])
            return out
        }
        return s
    }
}
