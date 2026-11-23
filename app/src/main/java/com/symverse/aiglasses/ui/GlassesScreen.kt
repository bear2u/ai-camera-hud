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
import com.symverse.aiglasses.core.NavHud
import com.symverse.aiglasses.core.NavPhase
import com.symverse.aiglasses.core.Speaker
import com.symverse.aiglasses.core.Status

private val RecordingFill = Color(0xE6B3261E)
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
    nav: NavHud,
    heading: Float?,
    onStopNavigation: () -> Unit,
) {
    var hudVisible by rememberSaveable { mutableStateOf(true) }
    var chatVisible by rememberSaveable { mutableStateOf(true) }
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
            if (hudVisible && chatVisible) {
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
            Column(Modifier.align(Alignment.CenterEnd), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(HudIcon.Camera, "사진 촬영", onClick = onTakePhoto)
                if (capture.recording) {
                    IconButton(HudIcon.Stop, "녹화 중지", onClick = onToggleVideo, fill = RecordingFill, line = Alert)
                } else {
                    IconButton(HudIcon.Record, "동영상 녹화", onClick = onToggleVideo, tint = Alert)
                }
            }
            capture.message?.let { CaptureNotice(it, Modifier.align(Alignment.BottomEnd)) }
            if (hudVisible && nav.phase != NavPhase.Off) {
                NavCard(
                    nav = nav,
                    heading = heading,
                    onClose = onStopNavigation,
                    // 위쪽 아이콘 줄 아래, 오른쪽 촬영 버튼 왼쪽에 둔다.
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 60.dp).fillMaxWidth(0.42f),
                )
            }
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hudVisible) {
                    // 채팅창만 접어서 카메라 화면을 넓게 본다. 화면 탭으로 말하기는 그대로 된다.
                    IconButton(
                        if (chatVisible) HudIcon.ChatOff else HudIcon.Chat,
                        if (chatVisible) "채팅창 숨기기" else "채팅창 보기",
                        onClick = { chatVisible = !chatVisible },
                        fill = if (chatVisible) CardFill else Primary,
                    )
                    IconButton(HudIcon.EyeOff, "HUD 숨기기", onClick = { hudVisible = false })
                    val aiOn = hud.status != Status.Off
                    IconButton(
                        HudIcon.Power,
                        if (aiOn) "AI 끄기" else "AI 켜기",
                        onClick = onToggleAi,
                        fill = if (aiOn) CardFill else Primary,
                        tint = if (aiOn) Live else Ink,
                    )
                    IconButton(HudIcon.Gear, "설정", onClick = onOpenSettings)
                } else {
                    IconButton(HudIcon.Eye, "HUD 보기", onClick = { hudVisible = true })
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(statusColor(hud.status), CircleShape))
                ModeSwitch(mode = mode, onSelect = onSelectMode)
            }
        }
        ChatLog(hud.entries, emptyHint = hint(hud.status, mode), modifier = Modifier.weight(1f).fillMaxWidth())
        hud.error?.let { BasicText("오류: $it", style = TitleStyle.copy(color = Alert), maxLines = 3) }
        // 오류는 바로 위 줄에 사유가 나오므로 상태 글자를 또 쓰지 않는다.
        if (hud.status != Status.Idle && hud.status != Status.Error) {
            BasicText(statusLabel(hud.status), style = TitleStyle, maxLines = 1)
        }
        InputRow(status = hud.status, mode = mode, onSendText = onSendText, onMic = onMic)
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
        IconButton(HudIcon.Send, "보내기", onClick = send, fill = Primary)
        MicButton(status = status, mode = mode, onClick = onMic)
    }
}

/** 말하기 버튼. 화면 탭과 같은 일을 하고, 지금 누르면 무슨 일이 일어나는지 아이콘으로 보여 준다. */
@Composable
private fun MicButton(status: Status, mode: Mode, onClick: () -> Unit) {
    when (status) {
        Status.Listening, Status.Speaking ->
            IconButton(HudIcon.Stop, micLabel(status, mode), onClick = onClick, fill = RecordingFill, line = Alert)
        Status.Thinking, Status.Connecting -> IconButton(HudIcon.Dots, micLabel(status, mode), onClick = onClick, tint = InkDim)
        Status.Idle, Status.Off, Status.Error -> IconButton(
            if (mode == Mode.TranslateText) HudIcon.Shutter else HudIcon.Mic,
            micLabel(status, mode),
            onClick = onClick,
        )
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
        ModeOption("번역", selected = mode == Mode.TranslateText, onClick = { onSelect(Mode.TranslateText) })
        ModeOption("통역", selected = mode == Mode.Conversation, onClick = { onSelect(Mode.Conversation) })
        ModeOption("풀이", selected = mode == Mode.Solve, onClick = { onSelect(Mode.Solve) })
        ModeOption("길안내", selected = mode == Mode.Navigate, onClick = { onSelect(Mode.Navigate) })
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
        Mode.Ask, Mode.Solve, Mode.Navigate -> "말하기"
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
    Status.Off -> "AI가 꺼져 있습니다. 오른쪽 위 전원 버튼을 누르세요"
    Status.Connecting -> "AI에 연결하고 있습니다"
    Status.Idle -> when (mode) {
        Mode.Ask -> "보이는 것에 대해 글이나 말로 물어보세요"
        Mode.TranslateText -> "글자를 비추고 버튼을 누르면 번역합니다"
        Mode.Conversation -> "마이크를 누르면 상대의 말을 한국어로 통역합니다"
        Mode.Solve -> "문제를 크게 비추고 글이나 말로 물어보세요"
        Mode.Navigate -> "목적지를 입력하면 걸어가는 길을 안내합니다"
    }
    Status.Listening -> when (mode) {
        Mode.Conversation -> "통역하고 있습니다"
        else -> "듣고 있습니다"
    }
    Status.Thinking -> "답을 준비하고 있습니다"
    Status.Speaking -> "답하고 있습니다"
    Status.Error -> "마이크 버튼을 누르면 다시 연결합니다"
}
