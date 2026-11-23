# AI 안경 시뮬레이터 Android 앱 계획

## Context

Rokid AI Glasses(https://kr.rokid.com/rokid-ai-glasses)처럼 동작하는 Android 네이티브 앱을 만든다. 폰 카메라가 안경의 POV 카메라 역할을 하고, 화면은 카메라 프리뷰 위에 모노 그린 HUD를 띄우며, AI와 실시간 음성으로 대화한다.

`/Users/symverse/ai-glasses-app`은 빈 폴더이고 git 저장소가 아니다. 새 프로젝트로 시작한다.

사용자 결정:
- AI: **OpenAI Realtime API** (실시간 음성 + 카메라 프레임)
- 1차 범위: **보고 묻기, 간판·메뉴 번역, 대화 번역 자막, 촬영 버튼**
- 호출: **화면 탭** (웨이크워드는 이후 단계)
- 키: 앱 **설정 화면에서 입력해 저장**(빌드에 넣지 않음), 검증은 **실기기**. `local.properties`의 `OPENAI_API_KEY`는 실서버 스모크 테스트만 읽는다

## 컨셉 확정 (2026-11-23, 사용자 그림)
손에 들고 가로로 쓰는 "AI Camera" 뷰어. 카메라 전체 화면 위에 반투명 카드(내 말, AI 답, 번역, 사물 정보)를 얹는다. 헤드셋용 좌우 분할 화면은 하지 않는다. HUD는 모노 그린이 아니라 어두운 남색 카드에 하늘색 테두리다.

그림에 있지만 아직 계획에 없던 것: 화면 속 사물 위치에 붙는 라벨(나무, 카페 등)과 장소 정보(평점, 영업시간). Realtime API는 사물 위치 좌표를 주지 않으므로 별도 단계가 필요하다(이후 단계 참고).

## 접근 방식

### 통신 (OpenAI 공식 문서 2026-11-23 조회 기준)
- **어시스턴트 연결**: OkHttp WebSocket, `wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1`, 헤더 `Authorization: Bearer <키>` (GA 형식, `OpenAI-Beta` 헤더 없음).
  - `session.update`: `type: "realtime"`, `output_modalities: ["audio"]`, 입출력 `audio/pcm` 24kHz, `turn_detection: null`, `audio.input.transcription`(사용자 발화 자막용, 기본 꺼짐이라 명시 필요), `tools`, `instructions`.
  - 음성: `input_audio_buffer.append`(base64 PCM16) → `input_audio_buffer.commit` → `response.create`.
  - 이미지: `conversation.item.create`에 `input_image`(`data:image/jpeg;base64,…`, `detail: "low"`). PNG/JPEG만 지원.
  - 수신: `response.output_audio.delta`, `response.output_audio_transcript.delta`, `response.output_text.delta`, `conversation.item.input_audio_transcription.completed`, `response.done`, `error`.
  - 끼어들기: `response.cancel` + `conversation.item.truncate`(실제 재생된 ms) + 로컬 재생 버퍼 비우기.
- **대화 번역 연결(별도)**: `wss://api.openai.com/v1/realtime/translations?model=gpt-realtime-translate`. 타입과 모델은 연결 시 고정이라 어시스턴트 연결에서 전환할 수 없다.
  - `session.update`로 `audio.output.language: "ko"`와 `audio.input.transcription` 설정, `session.input_audio_buffer.append`를 200ms(9,600바이트) 단위로 무음 포함 계속 전송, commit과 `response.create` 없음, 종료는 `session.close`.
  - 수신: `session.output_transcript.delta`(번역 자막), `session.input_transcript.delta`(원문), `session.output_audio.delta`(무시).
  - 분당 $0.034로 일반 세션보다 싸다. **한국어 지원 여부는 문서에서 확인하지 못했다.** 6단계 시작 때 먼저 실제 연결로 확인하고, 안 되면 일반 세션(`server_vad` + 세션 수준 텍스트 출력 + 번역 지시)으로 대체한다.
- 문서는 모바일에 WebRTC를 권장한다. 1차는 구조가 단순하고 JVM 테스트가 쉬운 WebSocket으로 가고, 전송 계층을 인터페이스 뒤에 두어 나중에 교체할 수 있게 한다.
- 문서는 표준 API 키를 클라이언트에 넣지 말라고 경고한다. 이 앱은 개인용 프로토타입으로만 쓰고, 배포하려면 임시 키(`POST /v1/realtime/client_secrets`)를 발급하는 백엔드가 필요하다(이번 범위 밖).
- 문서에서 확인하지 못해 기기에서 확인할 것: 전사 설정 JSON의 정확한 형태, 이미지 크기 제한, 함수 호출 완료 이벤트 이름.

### 모드 (HUD 하단에서 전환)
| 모드 | 동작 | 연결 |
|---|---|---|
| 보고 묻기 | 탭 → 듣기, 다시 탭 → 프레임 1장 + 음성 전송 → 음성 답 + HUD 자막, 말하는 중 탭 → 중단 | 어시스턴트 |
| 글자 번역 | 탭 → 프레임 1장 + "보이는 글자를 한국어로 번역" 지시 → HUD 텍스트 (`response.create`에 `output_modalities: ["text"]`) | 어시스턴트 |
| 대화 번역 | 탭 → 계속 듣기, 상대 말이 한국어 자막으로 흐름, 다시 탭 → 정지 | 번역 전용 |
| 촬영 | 오른쪽 사진·동영상 버튼으로 갤러리에 저장 (음성 명령 촬영은 하지 않음) | 없음 |

### 구조 (단일 모듈 `app`, 패키지 `com.symverse.aiglasses`)
- `core/` — 순수 Kotlin, JVM 단위 테스트 대상
  - `GlassesSession`: 상태 기계(Idle/Connecting/Listening/Thinking/Speaking/Error)와 보고 묻기·글자 번역·촬영 흐름
  - `TranslationSession`: 대화 번역 자막 흐름
  - `HudState`: 상태 표시, 사용자 발화 자막, AI 자막, 번역 자막, 녹화 표시
  - 포트 인터페이스: `RealtimeTransport`, `FrameSource`, `MicSource`, `AudioSink`, `CaptureController`
- `realtime/` — `RealtimeEvents`, `TranslationEvents`(kotlinx.serialization 인코딩/디코딩, JVM 테스트), `OkHttpRealtimeTransport`(얇은 어댑터)
- `camera/` — CameraX `Preview` + `ImageCapture` + `VideoCapture`. 유스케이스 4개 동시 바인딩은 기기에 따라 실패하므로 `ImageAnalysis`는 쓰지 않고, 질문용 프레임은 `ImageCapture` 메모리 캡처를 긴 변 768px JPEG로 줄여 얻는다. 저장은 MediaStore.
- `audio/` — `AudioRecord`(VOICE_COMMUNICATION, 24kHz mono, 에코 캔슬러) / `AudioTrack` 재생(재생된 ms 추적)
- `ui/` — Compose `GlassesScreen`: `CameraXViewfinder` 전체 화면 + 모노 그린 HUD 오버레이, 권한 요청(CAMERA, RECORD_AUDIO), `GlassesViewModel`

카메라·마이크·네트워크 어댑터는 로직 없이 얇게 두고 기기에서 수동 검증한다. 흐름 로직은 모두 `core`의 가짜 포트로 테스트한다.

### 빌드 환경 (설치된 것 기준)
- Gradle wrapper 9.6.1, AGP 9.5.x(최소 Gradle 9.6.0, 최소 JDK 17, 최대 compileSdk 37)
- **데몬 JVM을 JDK 21로 고정**: `gradle.properties`에 `org.gradle.java.home=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` (현재 `gradle` 실행 JVM은 Homebrew 26, PATH의 `java`는 22라서 고정하지 않으면 지원 범위 밖 JVM으로 돈다)
- compileSdk 37.2(Compose 1.13이 37.1 이상 요구) / targetSdk 37 / **minSdk 29**(저장소 권한 없이 MediaStore 저장), build-tools 36.x
- Kotlin 2.4.21 + `org.jetbrains.kotlin.plugin.compose`, CameraX 1.7.0(`camera-compose` 포함), coroutines 1.11.0, kotlinx.serialization 1.13.0
- 매니페스트 권한: INTERNET, CAMERA, RECORD_AUDIO. `buildFeatures { buildConfig = true }`
- 스캐폴딩에서 확인한 것: AGP 9.5.2, Compose BOM 2026.11.01, OkHttp 5.5.1. AGP 9는 Kotlin이 내장이라 `org.jetbrains.kotlin.android` 플러그인을 적용하지 않는다
- `ANDROID_HOME`이 비어 있으므로 `local.properties`에 `sdk.dir=/Users/symverse/Library/Android/sdk`와 `OPENAI_API_KEY=`를 둔다. 키 값은 사용자가 직접 넣는다.

## 실행 순서

테스트는 한 번에 하나씩 Red → Green → 리팩터 순으로 진행하고, 구조 변경과 동작 변경은 커밋을 나눈다.

### 0단계: 준비 (구조 변경)
- [x] 승인된 이 계획을 `/Users/symverse/ai-glasses-app/plan.md`에 저장
- [x] `git init`, `.gitignore`(`local.properties`, `build/`, `.gradle/`, `.idea/`)
- [x] Gradle 프로젝트 스캐폴딩, 빈 Compose 화면으로 `./gradlew :app:assembleDebug`와 `:app:testDebugUnitTest` 통과 확인 후 커밋

### 1단계: Realtime 프로토콜 (`RealtimeEventsTest`)
- [x] shouldEncodeSessionUpdateWithPcm24kAndNoTurnDetection
- [x] shouldEncodeInputTranscriptionInSessionUpdate
- [x] shouldEncodeAudioAppendAsBase64
- [x] shouldEncodeImageItemAsJpegDataUrl
- [x] shouldEncodeCommitAndResponseCreateWithModalities
- [x] shouldEncodeCancelAndTruncate
- [x] shouldDecodeOutputAudioDelta
- [x] shouldDecodeOutputAudioTranscriptDelta
- [x] shouldDecodeOutputTextDelta
- [x] shouldDecodeInputTranscriptionCompleted
- [x] shouldDecodeResponseDone
- [x] shouldDecodeErrorEvent
- [x] shouldIgnoreUnknownEventType

### 2단계: 보고 묻기 (`GlassesSessionTest`, 가짜 포트)
- [x] shouldConnectAndSendSessionUpdateOnStart
- [x] shouldStreamMicAudioWhileListening
- [x] shouldSendFrameThenCommitAndRequestResponseOnSecondTap
- [x] shouldShowAssistantTranscriptOnHudAsDeltasArrive
- [x] shouldPlayAudioDeltasToSink
- [x] shouldShowUserTranscriptOnHud
- [x] shouldReturnToIdleOnResponseDone
- [x] shouldCancelTruncateAndStopPlaybackWhenTappedWhileSpeaking
- [x] shouldShowErrorOnHudWhenTransportFails
- [x] shouldShowErrorWhenApiKeyMissing
- [x] shouldStopLeftoverPlaybackInsteadOfListeningWhenTappedAfterResponseDone (추가: 응답 생성은 재생보다 먼저 끝난다)
- [x] shouldReconnectWhenTappedAfterError (추가)
- [x] shouldShowApiErrorAndReturnToIdle (추가)
- [x] shouldClearPreviousTextsWhenListeningStarts (추가)

### 3단계: 어댑터와 화면, 첫 실기기 확인 (수동 검증)
- [x] `OkHttpRealtimeTransport`, `CameraFrameSource`, `MicRecorder`, `PcmPlayer`
- [x] `GlassesScreen` 가로 화면 카드형 오버레이(상태·내 말·AI 답·오류), 권한 요청과 거부 안내
- [x] 실서버 스모크 테스트(`OPENAI_LIVE=1`)로 session.update, 이미지 입력, 음성 입출력 이벤트 형태 확인
- [x] 실기기(SM-S938N, Android 17)에서 탭 → 듣기 → 탭 → 카메라 장면에 대한 AI 답 표시 확인
- [x] AI 활성화/중지 버튼(화면 오른쪽 위): shouldCloseConnectionAndTurnOffWhenStopped, shouldIgnoreTapAndLateServerEventsWhileOff, shouldReconnectWhenStartedAfterStop, shouldStayOffWhenConnectionReportsFailureAfterStop. 실기기에서 버튼으로 끄고 다시 켜지는 것 확인
- [x] 설정 화면(오른쪽 위 설정 버튼): API 키 입력·저장, 모델 선택. 키는 더 이상 빌드에 넣지 않고 앱 설정에 저장한다. `SettingsRepositoryTest` 4개, shouldTreatApiErrorOutsideATurnAsConnectionError. 에뮬레이터에서 가짜 값으로 저장 → 오류 표시 → 재시작 후 유지 확인
- [ ] 실기기 설정 화면에서 실제 키를 넣고 연결 확인 (사용자)
- [ ] 실기기에서 사람이 직접 말로 질문하고 스피커로 답을 듣는 확인 (사용자)
- [ ] 입력 음성 전사: 이 OpenAI 프로젝트는 `gpt-4o-mini-transcribe`, `gpt-4o-transcribe`, `whisper-1`, `gpt-realtime-whisper` 모두 접근 권한이 없어 "나" 자막이 나오지 않는다. 프로젝트에서 전사 모델을 허용하면 코드 변경 없이 나온다

### 4단계: 글자 번역
- [x] shouldSendFrameWithTranslateInstructionAndTextOnlyResponse
- [x] shouldShowTranslationTextOnHud
- [x] 모드 전환(질문 / 글자 번역)과 글자 번역 카드
- [x] 실서버 스모크 테스트로 음성 세션의 텍스트 전용 응답 확인 ("EMERGENCY EXIT → 비상구", 오디오 0바이트)
- [ ] 실기기에서 글자 번역 확인 (폰이 잠겨 있어 못 함. 설정 화면에 키 입력도 필요)

### 5단계: 촬영 버튼 (음성 촬영은 하지 않기로 함, 2026-11-23)
- [x] 화면 오른쪽 사진·동영상 버튼. `CaptureActionsTest` 5개: shouldReportSavedPhoto, shouldReportPhotoFailure, shouldStartRecordingOnFirstVideoToggle, shouldStopAndReportSavedVideoOnSecondToggle, shouldReportVideoFailureAndAllowRecordingAgain
- [x] CameraX `ImageCapture`/`VideoCapture`로 갤러리(Pictures/AIGlasses, Movies/AIGlasses)에 저장. 사진은 1920x1440(AI 전송용 프레임과 같은 캡처를 쓴다)
- [x] 실기기(SM-S938N)에서 사진 1장, 동영상 1개(1920x1080, 약 6초) 저장 확인
- [x] HUD 숨기기/보기 버튼(오른쪽 위). 숨겨도 이 버튼, 사진·동영상 버튼, 촬영 결과 알림은 남고 화면 탭 질문도 그대로 된다. 실기기에서 숨김/복원 확인
- [x] 저장된 사진·동영상에는 HUD가 들어가지 않는다(화면이 아니라 카메라 영상을 저장). HUD가 떠 있을 때 찍은 사진으로 확인
- [ ] HUD를 숨긴 상태에서 촬영 버튼이 남는지 실기기 확인 (사용자가 폰을 쓰는 중이라 못 함)
- [ ] 동영상 녹화 중 AI에게 말로 질문했을 때 두 마이크가 함께 도는지 확인 (사용자)

### 블루투스 이어폰 (2026-11-23 추가)
- [x] 듣는 동안만 블루투스 이어폰 마이크로 전환하고, 다 들으면 풀어서 AI 음성은 원래 음질로 재생. `HeadsetMicTest` 3개: shouldUseClassicBluetoothHeadsetWhenConnected, shouldReturnNullWhenNoBluetoothHeadsetIsConnected, shouldPreferLeAudioHeadsetOverClassicBluetooth
- [ ] 실기기에서 블루투스 이어폰으로 확인 (사용자): 이어폰 마이크로 녹음되는지(logcat 태그 `AIGlassesMic`), 말 첫머리가 잘리지 않는지, 답의 첫머리가 이어폰으로 제대로 나오는지

### 6단계: 대화 번역 자막
- [x] 번역 전용 엔드포인트 실제 연결 확인: 이 계정은 `gpt-realtime-translate`에 접근 권한이 없다(model_not_found). 계획대로 일반 세션 방식으로 바꿈
- [x] 실서버 스모크 테스트: 일반 세션 + `server_vad` + 텍스트 출력 + 번역 지시로 영어 음성이 한국어 자막으로 옴
- [x] shouldEncodeConversationTranslationUpdateWithServerVadAndTextOutput (`RealtimeEventsTest`)
- [x] shouldSwitchSessionToTranslationAndStreamMicWhenConversationStarts (`GlassesSessionTest`)
- [x] shouldAppendTranslatedCaptionPerTurnAndKeepListening
- [x] shouldKeepOnlyRecentCaptionLines (최근 3줄)
- [x] shouldRestoreAssistantSessionWhenConversationStops
- [x] shouldEndConversationWhenApiErrorArrives
- [x] shouldIgnoreLateTranslationAfterConversationStops
- [x] 모드 전환에 "대화 번역" 추가, 자막 카드(오른쪽 아래). 대화 번역 중에는 블루투스 이어폰이 있어도 기기 마이크로 듣는다
- [ ] 실기기 확인 (사용자): 상대의 말이 한국어 자막으로 뜨는지, 주변 소음에서 턴이 잘 나뉘는지

### 이후 단계 (이번 범위 밖)
화면 속 사물 위치 라벨(기기 내 사물 검출 + AI 이름 붙이기), 장소 정보(위치 + 지도 API), 웨이크워드, 60분 세션 만료 재연결, 임시 키 백엔드, WebRTC 전송, 오프라인 번역, 텔레프롬프터, 내비게이션.

## 검증
- 단위 테스트: `./gradlew :app:testDebugUnitTest` (작업 중에는 해당 테스트 클래스만 `--tests`로, 단계가 끝날 때 전체 실행)
- 빌드·설치: `./gradlew :app:installDebug` (USB로 연결한 Android 폰, `adb devices`로 확인). 현재 연결된 실기기가 없어 3단계 전에 폰 연결과 API 키 입력이 필요하다.
- 기기 수동 시나리오
  1. 첫 실행에서 카메라·마이크 권한 요청, 전체 화면 프리뷰 위에 그린 HUD 표시
  2. 사물을 비추고 탭 → "이게 뭐야?" → 탭: 음성 답과 HUD 자막, 이어서 후속 질문
  3. AI가 말하는 중 탭 → 즉시 중단
  4. 글자 번역 모드에서 영어 메뉴판 비추고 탭 → 한국어 번역 표시
  5. "사진 찍어줘", "동영상 찍어줘", "그만" → 갤러리에 저장 확인
  6. 대화 번역 모드에서 영어 음성 재생 → 한국어 자막이 흐름
  7. 키를 비운 빌드 → HUD에 키 없음 오류
- API 사용 요금이 발생한다(일반 세션 오디오 입력 $32, 출력 $64 / 1M 토큰, 번역 분당 $0.034). 실기기 검증은 짧게 한다.
