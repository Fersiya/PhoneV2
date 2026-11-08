package com.example.pccontrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedCard
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


@Composable
fun RowScope.CoolButton(
    text: String,
    weight: Float = 1f,
    filled: Boolean = false,
    accentColor: Color? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val trigger: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "press")
    if (filled) {
        Button(
            onClick = trigger,
            interactionSource = interaction,
            contentPadding = KeyPadding,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor ?: MaterialTheme.colorScheme.primary,
                contentColor = if (accentColor != null) Color.White else MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.weight(weight).height(48.dp).scale(pressScale)
        ) { Text(text, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(
            onClick = trigger,
            interactionSource = interaction,
            contentPadding = KeyPadding,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(weight).height(48.dp).scale(pressScale)
        ) { Text(text, textAlign = TextAlign.Center, fontSize = 13.sp) }
    }
}

@Composable
fun RowScope.HoldScrollButton(
    text: String,
    weight: Float = 1f,
    onScroll: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            while (isActive && isHolding) {
                onScroll()
                delay(70L) // Непрерывный плавный скролл при удержании
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isHolding) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .weight(weight)
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onScroll()
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = if (isHolding) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/**
 * Кадр стрима читается ЗДЕСЬ, а не в главном экране: каждый новый кадр перерисовывает только этот блок,
 * а не весь PcControlMainApp (раньше при 60-90 FPS пересобирался весь экран).
 */
@Composable
fun StreamFrameHost(bitmapProvider: () -> ImageBitmap?, content: @Composable (ImageBitmap?) -> Unit) {
    content(bitmapProvider())
}

/** То же для полноэкранного режима: внутри доступен BoxScope (align и т.п.). */
@Composable
fun BoxScope.StreamFrameHostBox(bitmapProvider: () -> ImageBitmap?, content: @Composable BoxScope.(ImageBitmap?) -> Unit) {
    content(bitmapProvider())
}

/** Счётчик FPS/пинга: значения читаются внутри, поэтому он не заставляет перерисовываться родителя. */
@Composable
fun StreamStatsText(fpsProvider: () -> Int, pingProvider: () -> Long, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = "🟢 ${fpsProvider()} FPS • ${pingProvider()} мс",
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF00E5FF)
    )
}

/** Сворачиваемый раздел настроек: заголовок с кратким состоянием, содержимое по нажатию. */
@Composable
fun SettingsSection(
    title: String,
    summary: String = "",
    startExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(startExpanded) }
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (summary.isNotBlank()) {
                        Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "arrow")
                Text("▼", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.graphicsLayer(rotationZ = arrow))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
            ) { Column { content() } }
        }
    }
}


/** Мигающая точка «в эфире»: мягко пульсирует, пока идёт трансляция. */
@Composable
fun PulseDot(color: Color, size: androidx.compose.ui.unit.Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseA")
    val sc by t.animateFloat(0.8f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseS")
    Box(modifier = Modifier.size(size).scale(sc).alpha(a).background(color, CircleShape))
}


/** Плавное появление карточки при первом показе: прозрачность + лёгкий выезд снизу. */
fun Modifier.appear(delayMs: Int = 0): Modifier = composed {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(380, delayMillis = delayMs)) }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/** Крупная плитка действия (иконка + подпись) для питания и быстрых команд. */
@Composable
fun RowScope.ActionTile(
    icon: String,
    label: String,
    tint: Color,
    weight: Float = 1f,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "tile")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tint.copy(alpha = if (pressed) 0.34f else 0.18f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.55f)),
        modifier = Modifier
            .weight(weight)
            .scale(sc)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 28.sp)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
