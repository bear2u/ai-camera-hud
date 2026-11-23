package com.symverse.aiglasses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.symverse.aiglasses.core.AppSettings
import com.symverse.aiglasses.core.KeyTestResult

private val PageFill = Color(0xFF07121F)

/** API 키와 모델을 정하는 설정 페이지. 저장하면 [onSave], 그냥 나가면 [onBack]. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (apiKey: String, model: String) -> Unit,
    onBack: () -> Unit,
    testResult: KeyTestResult?,
    onTest: (apiKey: String, model: String) -> Unit,
) {
    val apiKey = rememberTextFieldState(settings.apiKey)
    var model by remember { mutableStateOf(settings.model) }
    var showKey by remember { mutableStateOf(false) }
    var testedModel by remember { mutableStateOf(settings.model) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageFill)
            // 아래에 깔린 카메라 화면으로 탭이 새지 않게 한다.
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("뒤로", onClick = onBack)
            BasicText("설정", style = BodyStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            PillButton("저장", onClick = { onSave(apiKey.text.toString(), model) }, primary = true)
        }

        BasicText("OpenAI API 키", style = TitleStyle.copy(fontSize = 14.sp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicSecureTextField(
                state = apiKey,
                modifier = Modifier.weight(1f),
                textObfuscationMode = if (showKey) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                textStyle = BodyStyle,
                cursorBrush = SolidColor(CardLine),
                decorator = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(CardFill, CardShape)
                            .border(1.dp, CardLine, CardShape)
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (apiKey.text.isEmpty()) BasicText("sk-로 시작하는 키를 붙여 넣으세요", style = BodyStyle.copy(color = InkDim))
                        field()
                    }
                },
            )
            PillButton(if (showKey) "숨기기" else "보기", onClick = { showKey = !showKey })
            PillButton(
                "연결 테스트",
                onClick = {
                    testedModel = model
                    onTest(apiKey.text.toString(), model)
                },
            )
        }
        when (testResult) {
            null -> Unit
            KeyTestResult.Testing -> BasicText("$testedModel 연결을 확인하고 있습니다…", style = BodyStyle.copy(color = InkDim))
            KeyTestResult.Success ->
                BasicText("연결 성공. 이 키로 $testedModel 을(를) 쓸 수 있습니다.", style = BodyStyle.copy(color = Live))
            is KeyTestResult.Failure -> BasicText("연결 실패: ${testResult.message}", style = BodyStyle.copy(color = Alert))
        }
        BasicText("키는 이 기기의 앱 전용 저장소에만 저장되고 OpenAI 서버 연결에만 쓰입니다.", style = TitleStyle)

        BasicText("모델", style = TitleStyle.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 8.dp))
        AppSettings.MODELS.forEach { name ->
            ModelRow(name = name, selected = name == model, onSelect = { model = name })
        }
        BasicText("계정에 권한이 없는 모델을 고르면 연결할 때 오류가 표시됩니다.", style = TitleStyle)
    }
}

@Composable
private fun ModelRow(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardFill, CardShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) CardLine else InkDim.copy(alpha = 0.4f), CardShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(18.dp).border(2.dp, if (selected) CardLine else InkDim, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).background(CardLine, CircleShape))
        }
        BasicText(name, style = BodyStyle)
        if (name == AppSettings.DEFAULT_MODEL) BasicText("기본", style = TitleStyle)
    }
}
