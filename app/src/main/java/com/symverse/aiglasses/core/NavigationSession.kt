package com.symverse.aiglasses.core

enum class NavPhase { Off, Searching, Guiding, Arrived, Failed }

/** HUD의 길안내 카드에 그릴 내용. */
data class NavHud(
    val phase: NavPhase = NavPhase.Off,
    val destination: String = "",
    val guidance: NavState? = null,
    val message: String? = null,
)

/** 출발지에서 목적지까지의 도보 경로를 구해 온다. */
interface RouteFetcher {
    fun fetch(origin: LatLng, destination: String, onResult: (RouteResult) -> Unit)
}

/** 목적지를 받아 경로를 구하고, 위치가 바뀔 때마다 안내를 갱신한다. */
class NavigationSession(
    private val routes: RouteFetcher,
    private val location: LocationSource,
    private val announce: (String) -> Unit = {},
    private val onChanged: (NavHud) -> Unit = {},
) {
    var hud = NavHud()
        private set(value) {
            field = value
            onChanged(value)
        }

    private var navigator: Navigator? = null

    /** 경로 요청마다 올린다. 중지했거나 새로 시작한 뒤에 오는 옛 결과를 버린다. */
    private var request = 0

    @Synchronized
    fun start(destination: String) {
        val target = destination.trim()
        if (target.isEmpty()) return
        navigator = null
        val origin = location.current()?.position
        if (origin == null) {
            hud = NavHud(NavPhase.Failed, target, message = "현재 위치를 아직 모릅니다. 위치 권한과 GPS를 확인하세요.")
            return
        }
        hud = NavHud(phase = NavPhase.Searching, destination = target)
        val id = ++request
        routes.fetch(origin, target) { result -> onRoute(origin, id, result) }
    }

    /** 위치가 바뀔 때마다 부른다. */
    @Synchronized
    fun onLocation(position: LatLng) {
        val guide = navigator ?: return
        val before = hud.guidance?.stepIndex
        val state = guide.update(position)
        if (state.arrived) {
            navigator = null
            hud = hud.copy(phase = NavPhase.Arrived, guidance = state)
            announce("목적지에 도착했습니다.")
            return
        }
        hud = hud.copy(guidance = state)
        if (state.stepIndex != before) announce(state.currentInstruction)
    }

    @Synchronized
    fun stop() {
        request++
        navigator = null
        hud = NavHud()
    }

    @Synchronized
    private fun onRoute(origin: LatLng, id: Int, result: RouteResult) {
        if (id != request) return
        when (result) {
            is RouteResult.Found -> Unit
            RouteResult.NotFound -> {
                hud = hud.copy(phase = NavPhase.Failed, message = "'${hud.destination}'까지 가는 도보 경로를 찾지 못했습니다.")
                return
            }
            is RouteResult.Failed -> {
                hud = hud.copy(phase = NavPhase.Failed, message = result.message)
                return
            }
        }
        val guide = Navigator(result.route).also { navigator = it }
        val state = guide.update(origin)
        hud = hud.copy(phase = NavPhase.Guiding, guidance = state)
        announce("${hud.destination}까지 ${Geo.formatDistance(state.remainingMeters)}. ${state.currentInstruction}")
    }
}
