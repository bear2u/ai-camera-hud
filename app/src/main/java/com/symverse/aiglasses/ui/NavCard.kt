package com.symverse.aiglasses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.symverse.aiglasses.core.Geo
import com.symverse.aiglasses.core.NavHud
import com.symverse.aiglasses.core.NavPhase

/**
 * 길안내 카드. 화살표는 카메라가 향한 방향([heading])을 기준으로 다음 지점이 있는 쪽을 가리킨다.
 * 방향을 모르면 화살표 없이 글자만 보여 준다.
 */
@Composable
internal fun NavCard(nav: NavHud, heading: Float?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.background(CardFill, CardShape).border(1.dp, CardLine, CardShape).padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val guidance = nav.guidance
        if (nav.phase == NavPhase.Guiding && guidance != null && heading != null) {
            HudIconImage(
                HudIcon.Arrow,
                modifier = Modifier.size(52.dp).rotate(guidance.bearingToNext - heading),
                tint = Live,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (nav.phase) {
                NavPhase.Searching -> BasicText("'${nav.destination}' 가는 길을 찾고 있습니다", style = BodyStyle)
                NavPhase.Failed -> BasicText(nav.message.orEmpty(), style = BodyStyle.copy(color = Alert))
                NavPhase.Arrived -> BasicText("${nav.destination}에 도착했습니다", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                NavPhase.Guiding -> if (guidance != null) {
                    BasicText(
                        "${Geo.formatDistance(guidance.distanceToNextMeters)} 앞 ${guidance.nextInstruction}",
                        style = BodyStyle.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
                    )
                    BasicText("지금: ${guidance.currentInstruction}", style = BodyStyle.copy(color = InkDim), maxLines = 2)
                    BasicText("${nav.destination}까지 ${Geo.formatDistance(guidance.remainingMeters)}", style = TitleStyle)
                    // Google 이 도보 경로를 보여 줄 때 반드시 표시하라고 요구하는 안내.
                    BasicText("도보 경로는 베타 기능이라 보도나 보행로가 빠져 있을 수 있습니다.", style = TitleStyle)
                }
                NavPhase.Off -> Unit
            }
        }
        IconButton(HudIcon.Close, "길안내 끝내기", onClick = onClose, modifier = Modifier.size(40.dp))
    }
}
