package com.example.pccontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs

/**
 * Вкладка «Тачпад»: большая панель с жестами как на ноутбуке.
 * 1 палец — курсор, тап — ЛКМ, удержание — ПКМ;
 * 2 пальца — прокрутка, тап двумя — ПКМ;
 * 3 пальца — свайп влево/вправо = Alt+Tab, вверх = Win+Tab, вниз = Win+D.
 */
@Composable
fun ColumnScope.TouchpadTab(ui: PcState) {
    with(ui) {
        var lastGesture by remember { mutableStateOf("") }

        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().appear(0)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Тачпад", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Скорость: ${"%.1f".format(mouseSensitivity)}×", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = mouseSensitivity,
                    onValueChange = { mouseSensitivity = it },
                    onValueChangeFinished = { save() },
                    valueRange = 0.8f..3.0f,
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .pointerInput(host, token) {
                            val slop = viewConfiguration.touchSlop
                            val scrollStep = 28f
                            val swipeDist = 140f
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val t0 = down.uptimeMillis
                                var maxFingers = 1
                                var moved = false
                                var scrollAcc = 0f
                                var scrolled = false
                                var cx = 0f
                                var cy = 0f
                                var longDone = false
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }
                                    val n = pressed.size
                                    if (n > maxFingers) maxFingers = n
                                    if (n > 0) {
                                        val avgDx = pressed.sumOf { it.positionChange().x.toDouble() }.toFloat() / n
                                        val avgDy = pressed.sumOf { it.positionChange().y.toDouble() }.toFloat() / n
                                        when {
                                            n == 1 && maxFingers == 1 -> {
                                                accDx += avgDx * mouseSensitivity
                                                accDy += avgDy * mouseSensitivity
                                                if (abs(avgDx) + abs(avgDy) > 0f) {
                                                    val d = pressed[0].position - down.position
                                                    if (abs(d.x) + abs(d.y) > slop) moved = true
                                                }
                                                if (!moved && !longDone && event.changes[0].uptimeMillis - t0 > 450) {
                                                    longDone = true
                                                    sendMouseAction("right")
                                                    lastGesture = "ПКМ"
                                                }
                                            }
                                            n == 2 && maxFingers == 2 -> {
                                                scrollAcc += avgDy
                                                if (abs(avgDy) > 0f) moved = moved || abs(scrollAcc) > slop
                                                while (abs(scrollAcc) >= scrollStep) {
                                                    val s = if (scrollAcc > 0) 1 else -1
                                                    sendMouseAction("scroll", dy = s)
                                                    scrollAcc -= s * scrollStep
                                                    scrolled = true
                                                }
                                            }
                                            n >= 3 -> {
                                                cx += avgDx
                                                cy += avgDy
                                            }
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })

                                if (maxFingers >= 3) {
                                    when {
                                        abs(cx) > swipeDist && abs(cx) > abs(cy) -> {
                                            run("/media/alt_tab", refreshAfter = false); lastGesture = "Alt+Tab"
                                        }
                                        cy < -swipeDist -> { run("/media/win_tab", refreshAfter = false); lastGesture = "Обзор окон" }
                                        cy > swipeDist -> { run("/media/win_d", refreshAfter = false); lastGesture = "Свернуть всё" }
                                    }
                                } else if (!moved && !scrolled && !longDone) {
                                    if (maxFingers == 2) { sendMouseAction("right"); lastGesture = "ПКМ" }
                                    else if (maxFingers == 1) { sendMouseAction("left"); lastGesture = "ЛКМ" }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (lastGesture.isEmpty()) "Проведи пальцем по панели" else lastGesture,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    "1 палец: курсор • тап — ЛКМ • удержание — ПКМ\n2 пальца: прокрутка • тап — ПКМ\n3 пальца: ←→ Alt+Tab • ↑ обзор окон • ↓ свернуть всё",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoolButton("ЛКМ") { sendMouseAction("left") }
                    CoolButton("2× Клик") { sendMouseAction("double") }
                    CoolButton("ПКМ") { sendMouseAction("right") }
                    CoolButton("Колёсико") { sendMouseAction("middle") }
                }
            }
        }
    }
}
