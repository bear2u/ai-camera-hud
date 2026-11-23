package com.symverse.aiglasses.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.symverse.aiglasses.core.HudState
import com.symverse.aiglasses.core.Status



/** 카메라 화면 전체 위에 AI 오버레이를 얹는다. 화면 어디든 탭하면 [onTap]. */
@Composable
fun GlassesScreen(
    hud: HudState,
    surfaceRequest: SurfaceRequest?,
    onTap: () -> Unit,
    onToggleAi: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (surfaceRequest != null) {
            CameraXViewfinder(surfaceRequest = surfaceRequest, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onTap() } }
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.42f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCard(hud.status)
                if (hud.userText.isNotBlank()) TextCard(title = "나", text = hud.userText)
                hud.error?.let { TextCard(title = "오류", text = it, line = Alert) }
            }
            if (hud.assistantText.isNotBlank()) {
                TextCard(
                    title = "AI 어시스턴트",
                    text = hud.assistantText,
                    modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.46f),
                )
            }
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AiToggle(on = hud.status != Status.Off, onClick = onToggleAi)
                PillButton("설정", onClick = onOpenSettings)
            }
            HintBar(hud.status, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun StatusCard(status: Status) {
    Row(
        Modifier.background(CardFill, CardShape).border(1.dp, CardLine, CardShape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(statusColor(status), CircleShape))
        BasicText("AI Camera", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
        BasicText(statusLabel(status), style = TitleStyle)
    }
}

/** AI 연결을 켜고 끄는 버튼. */
@Composable
private fun AiToggle(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(PillShape)
            .background(if (on) CardFill else Primary, PillShape)
            .border(1.dp, CardLine, PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(if (on) Alert else Ink, if (on) RoundedCornerShape(2.dp) else CircleShape))
        BasicText(if (on) "AI 중지" else "AI 활성화", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun TextCard(title: String, text: String, modifier: Modifier = Modifier, line: Color = CardLine) {
    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
    Column(
        modifier
            .background(CardFill, CardShape)
            .border(1.dp, line, CardShape)
            .padding(14.dp)
            .heightIn(max = 180.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(title, style = TitleStyle)
        BasicText(text, style = BodyStyle)
    }
}

@Composable
private fun HintBar(status: Status, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    BasicText(
        hint(status),
        modifier = modifier.background(CardFill, shape).border(1.dp, CardLine, shape).padding(horizontal = 18.dp, vertical = 10.dp),
        style = BodyStyle,
    )
}

private fun statusColor(status: Status): Color = when (status) {
    Status.Off -> InkDim
    Status.Error -> Alert
    else -> Live
}

private fun statusLabel(status: Status): String = when (status) {
    Status.Off -> "꺼짐"
    Status.Connecting -> "연결 중"
    Status.Idle -> "대기"
    Status.Listening -> "듣는 중"
    Status.Thinking -> "생각하는 중"
    Status.Speaking -> "말하는 중"
    Status.Error -> "오류"
}

private fun hint(status: Status): String = when (status) {
    Status.Off -> "AI가 꺼져 있습니다. 위의 AI 활성화를 누르세요"
    Status.Connecting -> "AI에 연결하고 있습니다"
    Status.Idle -> "화면을 탭하고 질문하세요"
    Status.Listening -> "듣고 있습니다. 다 말했으면 탭하세요"
    Status.Thinking -> "답을 준비하고 있습니다"
    Status.Speaking -> "탭하면 답을 멈춥니다"
    Status.Error -> "탭하면 다시 시도합니다"
}
