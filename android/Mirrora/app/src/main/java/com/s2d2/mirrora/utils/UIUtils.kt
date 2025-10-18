package com.s2d2.mirrora.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun dpScaled(dpValue: Float, scale: Float): Dp = (dpValue * scale).dp

@Composable
fun spFromDpText(ctx: Context, dpValue: Float) =
    (dpValue * ctx.resources.displayMetrics.density / ctx.resources.displayMetrics.scaledDensity).sp

fun spFloatFromDpText(ctx: Context, dpValue: Float): Float =
    dpValue * ctx.resources.displayMetrics.density / ctx.resources.displayMetrics.scaledDensity

fun dpToPx(context: Context, dp: Dp): Int =
    (dp.value * context.resources.displayMetrics.density).toInt()