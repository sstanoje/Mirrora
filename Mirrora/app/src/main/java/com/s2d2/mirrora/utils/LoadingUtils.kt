package com.s2d2.mirrora.utils

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun BlockingLoaderDialog(
    visible: Boolean,
    message: String = "Connecting"
) {
    if (!visible) return
    Dialog(
        onDismissRequest = {}, // block back/outside
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        // Fullscreen scrim, centered content, consumes all input
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .noClicks()
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ThreeDotsLoader(modifier = Modifier.padding(bottom = 16.dp))
                Text(
                    text = animatedEllipsisText(message),
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ThreeDotsLoader(modifier: Modifier = Modifier) {
    val trans = rememberInfiniteTransition(label = "dots3")
    val t by trans.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(), // t increases over time
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "phase"
    )

    // Use NEGATIVE phase offsets so the leading dot is on the LEFT.
    val s1 = 0.7f + 0.3f * sin(t - 0f)                      // left
    val s2 = 0.7f + 0.3f * sin(t - (2 * Math.PI / 3).toFloat()) // middle
    val s3 = 0.7f + 0.3f * sin(t - (4 * Math.PI / 3).toFloat()) // right

    Row(
        modifier = modifier.wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(scale = s1)
        Dot(scale = s2)
        Dot(scale = s3)
    }
}

@Composable
private fun Dot(scale: Float) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .graphicsLayer(
                scaleX = scale.coerceIn(0.6f, 1.2f),
                scaleY = scale.coerceIn(0.6f, 1.2f),
                alpha = 0.5f + 0.5f * ((scale - 0.6f) / 0.6f).coerceIn(0f, 1f)
            )
            .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape)
    )
}

@Composable
private fun animatedEllipsisText(prefix: String): String {
    var dots by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            dots = (dots + 1) % 4
        }
    }
    return prefix + ".".repeat(dots)
}

private fun Modifier.noClicks(): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    clickable(
        indication = null,
        interactionSource = src,
        enabled = true,
        onClick = {} // consume everything
    )
}