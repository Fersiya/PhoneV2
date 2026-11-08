package com.example.pccontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Кнопка «Найти установленные»: просит ПК показать известные программы, которых нет в списке, и добавляет выбранные. */
@Composable
fun DetectedAppsButton(ui: PcState) {
    with(ui) {
        var found by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        var picked by remember { mutableStateOf(setOf<String>()) }

        OutlinedButton(
            onClick = {
                triggerHapticClick()
                scope.launch {
                    status = L("Ищу установленные программы…")
                    status = try {
                        val r = http(host.trim(), token.trim(), "/apps/detected", "GET")
                        when {
                            r.code == 200 -> {
                                val arr = JSONArray(r.body)
                                val list = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("id") to o.getString("title") } }
                                if (list.isEmpty()) L("Новых программ не найдено") else {
                                    picked = list.map { it.first }.toSet(); found = list; ""
                                }
                            }
                            r.code == 404 -> L("Нужно обновить хост на ПК (версия 1.9.1 или новее)")
                            r.code == 401 -> L("Неверный токен")
                            else -> L("Ошибка ${r.code}")
                        }
                    } catch (e: Exception) { L("ПК недоступен (${e.javaClass.simpleName})") }
                }
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text(L("✨ Найти установленные программы"), fontSize = 13.sp) }

        found?.let { list ->
            AlertDialog(
                onDismissRequest = { found = null },
                title = { Text(L("Найдено на ПК")) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        list.forEach { (id, title) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = id in picked, onCheckedChange = { picked = if (it) picked + id else picked - id })
                                Text(title, fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = picked.isNotEmpty(), onClick = {
                        val ids = picked
                        found = null
                        scope.launch {
                            val body = JSONObject().put("ids", JSONArray(ids.toList())).toString()
                            status = try {
                                val r = http(host.trim(), token.trim(), "/apps/detected/add", "POST", body)
                                if (r.code == 200) L("Программы добавлены ✔") else L("Не удалось добавить: ${r.body.ifBlank { L("ошибка ${r.code}") }}")
                            } catch (e: Exception) { L("ПК недоступен (${e.javaClass.simpleName})") }
                            delay(300); refresh()
                        }
                    }) { Text(L("Добавить (${picked.size})")) }
                },
                dismissButton = { TextButton(onClick = { found = null }) { Text(L("Отмена")) } }
            )
        }
    }
}
