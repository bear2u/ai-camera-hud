package com.symverse.aiglasses.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

internal enum class HudIcon { Send, Mic, Stop, Shutter, Camera, Record, Eye, EyeOff, Power, Gear, Dots }

/** 아이콘만 있는 둥근 버튼. [description] 은 화면 낭독기가 읽는 이름이다. */
@Composable
internal fun IconButton(
    icon: HudIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = CardFill,
    line: Color = CardLine,
    tint: Color = Ink,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(1.dp, line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) { draw(icon, tint) }
    }
}

/** 24x24 격자에 맞춰 그린다. */
private fun DrawScope.draw(icon: HudIcon, tint: Color) {
    val u = size.minDimension / 24f
    val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun at(x: Float, y: Float) = Offset(x * u, y * u)
    when (icon) {
        HudIcon.Send -> drawPath(
            Path().apply {
                moveTo(3 * u, 20 * u)
                lineTo(21 * u, 12 * u)
                lineTo(3 * u, 4 * u)
                lineTo(6.5f * u, 12 * u)
                close()
            },
            tint,
        )
        HudIcon.Mic -> {
            drawRoundRect(tint, topLeft = at(9f, 3f), size = Size(6 * u, 11 * u), cornerRadius = CornerRadius(3 * u))
            drawArc(tint, 0f, 180f, false, topLeft = at(5.5f, 4.5f), size = Size(13 * u, 13 * u), style = line)
            drawLine(tint, at(12f, 17.5f), at(12f, 21f), strokeWidth = 2 * u, cap = StrokeCap.Round)
            drawLine(tint, at(9f, 21f), at(15f, 21f), strokeWidth = 2 * u, cap = StrokeCap.Round)
        }
        HudIcon.Stop -> drawRoundRect(tint, topLeft = at(6f, 6f), size = Size(12 * u, 12 * u), cornerRadius = CornerRadius(2 * u))
        HudIcon.Shutter -> {
            drawCircle(tint, radius = 9 * u, center = at(12f, 12f), style = line)
            drawCircle(tint, radius = 5 * u, center = at(12f, 12f))
        }
        HudIcon.Camera -> {
            drawRoundRect(tint, topLeft = at(3f, 7f), size = Size(18 * u, 13 * u), cornerRadius = CornerRadius(2.5f * u), style = line)
            drawPath(
                Path().apply {
                    moveTo(8.5f * u, 7 * u)
                    lineTo(10 * u, 4.5f * u)
                    lineTo(14 * u, 4.5f * u)
                    lineTo(15.5f * u, 7 * u)
                },
                tint,
                style = line,
            )
            drawCircle(tint, radius = 3.5f * u, center = at(12f, 13.5f), style = line)
        }
        HudIcon.Record -> drawCircle(tint, radius = 7 * u, center = at(12f, 12f))
        HudIcon.Eye, HudIcon.EyeOff -> {
            drawPath(
                Path().apply {
                    moveTo(2 * u, 12 * u)
                    quadraticTo(12 * u, 2 * u, 22 * u, 12 * u)
                    quadraticTo(12 * u, 22 * u, 2 * u, 12 * u)
                    close()
                },
                tint,
                style = line,
            )
            drawCircle(tint, radius = 3 * u, center = at(12f, 12f))
            if (icon == HudIcon.EyeOff) drawLine(tint, at(4f, 20f), at(20f, 4f), strokeWidth = 2 * u, cap = StrokeCap.Round)
        }
        HudIcon.Power -> {
            drawArc(tint, -60f, 300f, false, topLeft = at(4f, 5f), size = Size(16 * u, 16 * u), style = line)
            drawLine(tint, at(12f, 3f), at(12f, 12f), strokeWidth = 2 * u, cap = StrokeCap.Round)
        }
        HudIcon.Gear -> {
            drawCircle(tint, radius = 6 * u, center = at(12f, 12f), style = line)
            drawCircle(tint, radius = 2 * u, center = at(12f, 12f))
            for (step in 0 until 8) {
                val angle = Math.toRadians(step * 45.0)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(tint, at(12 + dx * 7.5f, 12 + dy * 7.5f), at(12 + dx * 10f, 12 + dy * 10f), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
            }
        }
        HudIcon.Dots -> listOf(6f, 12f, 18f).forEach { drawCircle(tint, radius = 1.8f * u, center = at(it, 12f)) }
    }
}
