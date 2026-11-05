package com.example.pccontrol

import androidx.compose.foundation.BorderStroke
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
    if (filled) {
        Button(
            onClick = trigger,
            contentPadding = KeyPadding,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor ?: MaterialTheme.colorScheme.primary,
                contentColor = if (accentColor != null) Color.White else MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.weight(weight)
        ) { Text(text, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(
            onClick = trigger,
            contentPadding = KeyPadding,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(weight)
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
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text, textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = if (isHolding) FontWeight.Bold else FontWeight.Medium)
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
