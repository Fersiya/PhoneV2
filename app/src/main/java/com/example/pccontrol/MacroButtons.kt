package com.example.pccontrol

import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Шаг своей кнопки: kind = key | app | text | wait. */
data class MacroStep(val kind: String, val value: String, val label: String)
data class MacroButton(val id: String, val name: String, val steps: List<MacroStep>)

/** Что умеет сервер из горячих клавиш (имя действия → подпись). */
val MacroKeyCatalog: List<Pair<String, String>> = listOf(
    "alt_tab" to L("Alt+Tab (окна)"), "win_d" to L("Win+D (свернуть всё)"), "win_tab" to L("Win+Tab (обзор)"),
    "alt_f4" to L("Alt+F4 (закрыть окно)"), "task_mgr" to L("Диспетчер задач"),
    "ctrl_z" to L("Ctrl+Z (отмена)"), "ctrl_c" to "Ctrl+C", "ctrl_v" to "Ctrl+V",
    "close_tab" to L("Закрыть вкладку"), "reopen_tab" to L("Вернуть вкладку"), "next_tab" to L("Следующая вкладка"),
    "key_enter" to "Enter", "key_esc" to "Esc", "key_space" to L("Пробел"), "key_backspace" to "Backspace",
    "play_pause" to L("Пауза / Play"), "next" to L("Следующий трек"), "prev" to L("Предыдущий трек"),
    "vol_up_10" to L("Громче +10%"), "vol_down_10" to L("Тише −10%"), "vol_mute" to L("Без звука"),
    "yt_fullscreen" to L("Полный экран (F)"), "audio_output" to L("Выбор аудиовыхода")
)

fun loadMacros(prefs: SharedPreferences): List<MacroButton> = try {
    val arr = JSONArray(prefs.getString("macro_buttons", "[]"))
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val st = o.getJSONArray("steps")
        MacroButton(
            o.getString("id"), o.getString("name"),
            (0 until st.length()).map { j ->
                val s = st.getJSONObject(j)
                MacroStep(s.getString("kind"), s.getString("value"), s.optString("label", s.getString("value")))
            }
        )
    }
} catch (_: Exception) { emptyList() }

fun saveMacros(prefs: SharedPreferences, list: List<MacroButton>) {
    val arr = JSONArray()
    list.forEach { m ->
        arr.put(JSONObject().apply {
            put("id", m.id); put("name", m.name)
            put("steps", JSONArray().also { sa ->
                m.steps.forEach { s -> sa.put(JSONObject().apply { put("kind", s.kind); put("value", s.value); put("label", s.label) }) }
            })
        })
    }
    prefs.edit().putString("macro_buttons", arr.toString()).apply()
}

/** Выполняет шаги по очереди; при первой ошибке останавливается. */
fun PcState.runMacro(m: MacroButton) {
    if (host.isBlank() || token.isBlank()) { status = L("Сначала подключитесь к ПК"); return }
    triggerHapticClick()
    scope.launch {
        for ((i, s) in m.steps.withIndex()) {
            status = L("«${m.name}»: шаг ${i + 1}/${m.steps.size}")
            val res = when (s.kind) {
                "key" -> command(host.trim(), token.trim(), "/media/${s.value}", "POST")
                "app" -> command(host.trim(), token.trim(), "/apps/${s.value}/launch", "POST")
                "text" -> command(host.trim(), token.trim(), "/type", "POST", JSONObject().apply { put("text", s.value); put("enter", false) }.toString())
                "wait" -> { delay(s.value.toLongOrNull() ?: 500L); L("Готово ✔") }
                else -> L("Готово ✔")
            }
            if (res != L("Готово ✔")) { status = L("«${m.name}»: шаг ${i + 1} — $res"); return@launch }
        }
        status = L("«${m.name}» выполнено ✔")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnScope.MacroCard(ui: PcState) {
    with(ui) {
        var macros by remember { mutableStateOf(loadMacros(prefs)) }
        var editing by remember { mutableStateOf<MacroButton?>(null) }

        OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().appear(140)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(L("⭐ Мои кнопки"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FilledTonalButton(
                        onClick = { editing = MacroButton("m" + System.currentTimeMillis(), "", emptyList()) },
                        contentPadding = KeyPadding, shape = RoundedCornerShape(10.dp)
                    ) { Text(L("＋ Новая")) }
                }
                if (macros.isEmpty()) {
                    Text(
                        L("Собери свою кнопку из шагов: горячие клавиши, запуск программы, текст, пауза. Нажатие выполняет все шаги по порядку, долгое нажатие — правка."),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                macros.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .combinedClickable(onClick = { runMacro(m) }, onLongClick = { editing = m })
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(m.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(L("${m.steps.size} шаг."), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        editing?.let { cur ->
            MacroEditor(
                ui = ui,
                initial = cur,
                isNew = macros.none { it.id == cur.id },
                onSave = { m ->
                    macros = if (macros.any { it.id == m.id }) macros.map { if (it.id == m.id) m else it } else macros + m
                    saveMacros(prefs, macros); editing = null
                },
                onDelete = { macros = macros.filter { it.id != cur.id }; saveMacros(prefs, macros); editing = null },
                onDismiss = { editing = null }
            )
        }
    }
}

@Composable
private fun MacroEditor(ui: PcState, initial: MacroButton, isNew: Boolean, onSave: (MacroButton) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var steps by remember { mutableStateOf(initial.steps) }
    var keyMenu by remember { mutableStateOf(false) }
    var appMenu by remember { mutableStateOf(false) }
    var waitMenu by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showText by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) L("Новая кнопка") else L("Правка кнопки")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 24) name = it }, label = { Text(L("Название")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(L("Шаги (${steps.size}):"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (steps.isEmpty()) Text(L("Пока пусто — добавь шаги ниже"), fontSize = 12.sp)
                steps.forEachIndexed { i, s ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${s.label}", fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { steps = steps.filterIndexed { j, _ -> j != i } }, contentPadding = KeyPadding) { Text("✕") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { keyMenu = true }, contentPadding = KeyPadding, modifier = Modifier.fillMaxWidth()) { Text(L("⌨ Клавиша"), fontSize = 12.sp) }
                        DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                            MacroKeyCatalog.forEach { (k, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { steps = steps + MacroStep("key", k, label); keyMenu = false })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { appMenu = true }, contentPadding = KeyPadding, modifier = Modifier.fillMaxWidth()) { Text(L("🚀 Программа"), fontSize = 12.sp) }
                        DropdownMenu(expanded = appMenu, onDismissRequest = { appMenu = false }) {
                            if (ui.apps.isEmpty()) DropdownMenuItem(text = { Text(L("Список программ пуст")) }, onClick = { appMenu = false })
                            ui.apps.forEach { a ->
                                DropdownMenuItem(text = { Text(a.title) }, onClick = { steps = steps + MacroStep("app", a.id, L("Запуск: ${a.title}")); appMenu = false })
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showText = !showText }, contentPadding = KeyPadding, modifier = Modifier.weight(1f)) { Text(L("✍ Текст"), fontSize = 12.sp) }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { waitMenu = true }, contentPadding = KeyPadding, modifier = Modifier.fillMaxWidth()) { Text(L("⏱ Пауза"), fontSize = 12.sp) }
                        DropdownMenu(expanded = waitMenu, onDismissRequest = { waitMenu = false }) {
                            listOf(500L to L("0,5 с"), 1000L to L("1 с"), 2000L to L("2 с"), 5000L to L("5 с")).forEach { (ms, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = { steps = steps + MacroStep("wait", ms.toString(), L("Пауза $l")); waitMenu = false })
                            }
                        }
                    }
                }
                if (showText) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = textInput, onValueChange = { textInput = it }, label = { Text(L("Печать на ПК")) }, singleLine = true, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = {
                            if (textInput.isNotBlank()) { steps = steps + MacroStep("text", textInput, L("Текст: ${textInput.take(20)}")); textInput = ""; showText = false }
                        }, contentPadding = KeyPadding) { Text("＋") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && steps.isNotEmpty(),
                onClick = { onSave(initial.copy(name = name.trim(), steps = steps)) }
            ) { Text(L("Сохранить")) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(L("Удалить")) }
                TextButton(onClick = onDismiss) { Text(L("Отмена")) }
            }
        }
    )
}
