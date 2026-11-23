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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.symverse.aiglasses.core.CaptureState
import com.symverse.aiglasses.core.HudState
import com.symverse.aiglasses.core.Mode
import com.symverse.aiglasses.core.Status



/** 카메라 화면 전체 위에 AI 오버레이를 얹는다. 화면 어디든 탭하면 [onTap]. */
@Composable
fun GlassesScreen(
    hud: HudState,
    surfaceRequest: SurfaceRequest?,
    onTap: () -> Unit,
    onToggleAi: () -> Unit,
    onOpenSettings: () -> Unit,
    mode: Mode,
    onSelectMode: (Mode) -> Unit,
    capture: CaptureState,
    onTakePhoto: () -> Unit,
    onToggleVideo: () -> Unit,
) {
    var hudVisible by rememberSaveable { mutableStateOf(true) }
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
            // HUD를 숨겨도 촬영 결과 알림과 촬영 버튼은 남긴다.
            if (!hudVisible) {
                capture.message?.let {
                    TextCard(title = "촬영", text = it, modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.42f))
                }
            }
            if (hudVisible) Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val cardWidth = Modifier.fillMaxWidth(0.42f)
                // 상태와 모드 전환은 왼쪽에 위아래로 두어 오른쪽 버튼들과 겹치지 않게 한다.
                StatusCard(hud.status)
                ModeSwitch(mode = mode, onSelect = onSelectMode)
                if (hud.userText.isNotBlank()) TextCard(title = "나", text = hud.userText, modifier = cardWidth)
                if (hud.translationText.isNotBlank()) TextCard(title = "글자 번역", text = hud.translationText, modifier = cardWidth)
                hud.error?.let { TextCard(title = "오류", text = it, modifier = cardWidth, line = Alert) }
                capture.message?.let { TextCard(title = "촬영", text = it, modifier = cardWidth) }
            }
            Column(
                Modifier.align(Alignment.CenterEnd).width(CaptureColumnWidth),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton("사진", onClick = onTakePhoto, modifier = Modifier.fillMaxWidth())
                RecordButton(recording = capture.recording, onClick = onToggleVideo)
            }

            val bottomCard = when {
                hud.solutionText.isNotBlank() -> "문제 풀이" to hud.solutionText
                hud.captions.isNotEmpty() -> "대화 번역" to hud.captions.joinToString("\n")
                hud.assistantText.isNotBlank() -> "AI 어시스턴트" to hud.assistantText
                else -> null
            }
            if (hudVisible && bottomCard != null) {
                TextCard(
                    title = bottomCard.first,
                    text = bottomCard.second,
                    // 오른쪽 촬영 버튼과 겹치지 않게 비켜 둔다.
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = CaptureColumnWidth + 12.dp)
                        .fillMaxWidth(0.46f),
                    // 풀이는 길어서 더 크게 보여 주고, 손으로 올려 볼 수 있게 자동 스크롤을 끈다.
                    maxHeight = if (hud.solutionText.isNotBlank()) 280.dp else 180.dp,
                    followTail = hud.solutionText.isBlank(),
                )
            }
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // HUD를 숨겨도 이 버튼은 남겨서 다시 켤 수 있게 한다.
                PillButton(if (hudVisible) "HUD 숨기기" else "HUD 보기", onClick = { hudVisible = !hudVisible })
                if (hudVisible) {
                    AiToggle(on = hud.status != Status.Off, onClick = onToggleAi)
                    PillButton("설정", onClick = onOpenSettings)
                }
            }
            if (hudVisible) HintBar(hud.status, mode, Modifier.align(Alignment.BottomStart))
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

private val CaptureColumnWidth = 112.dp

/** 동영상 녹화를 시작하고 멈춘다. 녹화 중에는 빨갛게 표시한다. */
@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(if (recording) Color(0xE6B3261E) else CardFill, PillShape)
            .border(1.dp, if (recording) Alert else CardLine, PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Box(Modifier.size(10.dp).background(if (recording) Ink else Alert, if (recording) RoundedCornerShape(2.dp) else CircleShape))
        BasicText(if (recording) "녹화 중지" else "동영상", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** 화면 탭이 할 일을 고른다. */
@Composable
private fun ModeSwitch(mode: Mode, onSelect: (Mode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.background(CardFill, PillShape).border(1.dp, CardLine, PillShape).padding(4.dp)) {
        ModeOption("질문", selected = mode == Mode.Ask, onClick = { onSelect(Mode.Ask) })
        ModeOption("글자 번역", selected = mode == Mode.TranslateText, onClick = { onSelect(Mode.TranslateText) })
        ModeOption("대화 번역", selected = mode == Mode.Conversation, onClick = { onSelect(Mode.Conversation) })
        ModeOption("문제 풀이", selected = mode == Mode.Solve, onClick = { onSelect(Mode.Solve) })
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(PillShape)
            .background(if (selected) Primary else Color.Transparent, PillShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = BodyStyle.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
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
private fun TextCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    line: Color = CardLine,
    maxHeight: Dp = 180.dp,
    followTail: Boolean = true,
) {
    val scroll = rememberScrollState()
    if (followTail) LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
    Column(
        modifier
            .background(CardFill, CardShape)
            .border(1.dp, line, CardShape)
            .padding(14.dp)
            .heightIn(max = maxHeight)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(title, style = TitleStyle)
        BasicText(text, style = BodyStyle)
    }
}

@Composable
private fun HintBar(status: Status, mode: Mode, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    BasicText(
        hint(status, mode),
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

private fun hint(status: Status, mode: Mode): String = when (status) {
    Status.Off -> "AI가 꺼져 있습니다. 위의 AI 활성화를 누르세요"
    Status.Connecting -> "AI에 연결하고 있습니다"
    Status.Idle -> when (mode) {
        Mode.Ask -> "화면을 탭하고 질문하세요"
        Mode.TranslateText -> "글자를 비추고 화면을 탭하세요"
        Mode.Conversation -> "탭하면 상대의 말을 한국어로 번역합니다"
        Mode.Solve -> "문제를 화면에 크게 비추고 탭하세요"
    }
    Status.Listening ->
        if (mode == Mode.Conversation) "번역하고 있습니다. 탭하면 멈춥니다" else "듣고 있습니다. 다 말했으면 탭하세요"
    Status.Thinking -> "답을 준비하고 있습니다"
    Status.Speaking -> "탭하면 답을 멈춥니다"
    Status.Error -> "탭하면 다시 시도합니다"
}
