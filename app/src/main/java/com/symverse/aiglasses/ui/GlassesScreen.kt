package com.symverse.aiglasses.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.symverse.aiglasses.core.CaptureState
import com.symverse.aiglasses.core.HudState
import com.symverse.aiglasses.core.LogEntry
import com.symverse.aiglasses.core.Mode
import com.symverse.aiglasses.core.Speaker
import com.symverse.aiglasses.core.Status

private val CaptureColumnWidth = 112.dp
private val BubbleShape = RoundedCornerShape(12.dp)
private val OtherBubble = Color(0x33FFFFFF)

/**
 * 카메라 화면 전체 위에 HUD를 얹는다. 왼쪽은 채팅창(모드, 대화 기록, 입력), 오른쪽은 버튼들이다.
 * 채팅창 밖의 화면을 탭하면 [onTap] 으로 말하기를 시작하거나 끝낸다.
 */
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
    onSendText: (String) -> Unit,
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
                .imePadding()
                .padding(16.dp),
        ) {
            if (hudVisible) {
                ChatPanel(
                    hud = hud,
                    mode = mode,
                    onSelectMode = onSelectMode,
                    onSendText = onSendText,
                    onMic = onTap,
                    modifier = Modifier.align(Alignment.TopStart).fillMaxHeight().fillMaxWidth(0.46f),
                )
            }
            // HUD를 숨겨도 촬영 버튼, 촬영 결과 알림, HUD 버튼은 남긴다.
            Column(
                Modifier.align(Alignment.CenterEnd).width(CaptureColumnWidth),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton("사진", onClick = onTakePhoto, modifier = Modifier.fillMaxWidth())
                RecordButton(recording = capture.recording, onClick = onToggleVideo)
            }
            capture.message?.let { CaptureNotice(it, Modifier.align(Alignment.BottomEnd)) }
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(if (hudVisible) "HUD 숨기기" else "HUD 보기", onClick = { hudVisible = !hudVisible })
                if (hudVisible) {
                    AiToggle(on = hud.status != Status.Off, onClick = onToggleAi)
                    PillButton("설정", onClick = onOpenSettings)
                }
            }
        }
    }
}

/** 왼쪽 채팅창: 상태, 모드 선택, 위로 올려 볼 수 있는 대화 기록, 글자 입력과 말하기 버튼. */
@Composable
private fun ChatPanel(
    hud: HudState,
    mode: Mode,
    onSelectMode: (Mode) -> Unit,
    onSendText: (String) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 가로 화면에서는 키보드가 높이를 많이 차지하므로, 키보드가 뜨면 위쪽 줄들을 접는다.
    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(
        modifier
            .background(CardFill, CardShape)
            .border(1.dp, CardLine, CardShape)
            // 채팅창 안을 건드리는 것이 화면 탭(말하기)으로 새지 않게 한다.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!typing) {
            StatusRow(hud.status)
            ModeSwitch(mode = mode, onSelect = onSelectMode)
        }
        ChatLog(hud.entries, emptyHint = hint(hud.status, mode), modifier = Modifier.weight(1f).fillMaxWidth())
        hud.error?.let { BasicText("오류: $it", style = TitleStyle.copy(color = Alert), maxLines = 3) }
        if (hud.entries.isNotEmpty() && !typing) BasicText(hint(hud.status, mode), style = TitleStyle, maxLines = 1)
        InputRow(status = hud.status, mode = mode, onSendText = onSendText, onMic = onMic)
    }
}

@Composable
private fun StatusRow(status: Status) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(statusColor(status), CircleShape))
        BasicText("AI Camera", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
        BasicText(statusLabel(status), style = TitleStyle)
    }
}

/** 대화 기록. 맨 아래를 보고 있을 때만 새 글을 따라 내려가서, 위로 올려 읽는 중에는 움직이지 않는다. */
@Composable
private fun ChatLog(entries: List<LogEntry>, emptyHint: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress to (scroll.value >= scroll.maxValue - 48) }
            .collect { (scrolling, atBottom) -> if (scrolling) followTail = atBottom }
    }
    // 새 말풍선이 생기면 다시 맨 아래로 간다.
    LaunchedEffect(entries.size) {
        followTail = true
        scroll.scrollTo(scroll.maxValue)
    }
    LaunchedEffect(entries.lastOrNull()?.text?.length, scroll.maxValue) {
        if (followTail) scroll.scrollTo(scroll.maxValue)
    }
    Column(modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entries.isEmpty()) BasicText(emptyHint, style = BodyStyle.copy(color = InkDim))
        entries.forEach { Bubble(it) }
    }
}

@Composable
private fun Bubble(entry: LogEntry) {
    val mine = entry.speaker == Speaker.User
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine) BasicText(speakerLabel(entry.speaker), style = TitleStyle)
        BasicText(
            entry.text,
            style = BodyStyle,
            modifier = Modifier
                .padding(start = if (mine) 40.dp else 0.dp, end = if (mine) 0.dp else 16.dp)
                .background(if (mine) Primary else OtherBubble, BubbleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** 글자 입력칸, 보내기, 말하기 버튼. 말하기 버튼은 화면 탭과 같은 일을 한다. */
@Composable
private fun InputRow(status: Status, mode: Mode, onSendText: (String) -> Unit, onMic: () -> Unit) {
    val input = rememberTextFieldState()
    val keyboard = LocalSoftwareKeyboardController.current
    val send = {
        val text = input.text.toString()
        if (text.isNotBlank()) {
            onSendText(text)
            input.clearText()
            keyboard?.hide()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            state = input,
            modifier = Modifier.weight(1f),
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = BodyStyle,
            cursorBrush = SolidColor(CardLine),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            onKeyboardAction = { send() },
            decorator = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x66000000), PillShape)
                        .border(1.dp, CardLine, PillShape)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (input.text.isEmpty()) BasicText("메시지 입력", style = BodyStyle.copy(color = InkDim))
                    field()
                }
            },
        )
        PillButton("보내기", onClick = send, primary = true)
        PillButton(micLabel(status, mode), onClick = onMic)
    }
}

@Composable
private fun CaptureNotice(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier = modifier.background(CardFill, PillShape).border(1.dp, CardLine, PillShape).padding(horizontal = 16.dp, vertical = 10.dp),
        style = BodyStyle,
    )
}

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

/** 말하기 버튼과 화면 탭이 할 일을 고른다. 좁으면 옆으로 밀어 볼 수 있다. */
@Composable
private fun ModeSwitch(mode: Mode, onSelect: (Mode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .background(Color(0x66000000), PillShape)
            .border(1.dp, CardLine, PillShape)
            .padding(4.dp),
    ) {
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
            .padding(horizontal = 14.dp),
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

private fun speakerLabel(speaker: Speaker): String = when (speaker) {
    Speaker.User -> "나"
    Speaker.Assistant -> "AI"
    Speaker.Translation -> "글자 번역"
    Speaker.Caption -> "대화 번역"
    Speaker.Solution -> "문제 풀이"
}

private fun micLabel(status: Status, mode: Mode): String = when (status) {
    Status.Listening -> if (mode == Mode.Conversation) "중지" else "전송"
    Status.Speaking -> "중단"
    Status.Error -> "재시도"
    Status.Thinking, Status.Connecting -> "대기"
    Status.Idle, Status.Off -> when (mode) {
        Mode.TranslateText -> "번역"
        Mode.Conversation -> "시작"
        Mode.Ask, Mode.Solve -> "말하기"
    }
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
        Mode.Ask -> "글로 묻거나, 말하기를 누르고 질문하세요"
        Mode.TranslateText -> "글자를 비추고 번역을 누르세요"
        Mode.Conversation -> "시작을 누르면 상대의 말을 한국어로 번역합니다"
        Mode.Solve -> "문제를 크게 비추고 글이나 말로 물어보세요"
    }
    Status.Listening -> when (mode) {
        Mode.Conversation -> "번역하고 있습니다. 중지를 누르면 멈춥니다"
        Mode.Solve -> "듣고 있습니다. 전송을 누르면 풉니다 (말없이 눌러도 됩니다)"
        else -> "듣고 있습니다. 다 말했으면 전송을 누르세요"
    }
    Status.Thinking -> "답을 준비하고 있습니다"
    Status.Speaking -> "중단을 누르면 답을 멈춥니다"
    Status.Error -> "재시도를 누르면 다시 연결합니다"
}
