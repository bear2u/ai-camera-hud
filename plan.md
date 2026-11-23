# AI 안경 시뮬레이터 Android 앱 계획

## Context

Rokid AI Glasses(https://kr.rokid.com/rokid-ai-glasses)처럼 동작하는 Android 네이티브 앱을 만든다. 폰 카메라가 안경의 POV 카메라 역할을 하고, 화면은 카메라 프리뷰 위에 모노 그린 HUD를 띄우며, AI와 실시간 음성으로 대화한다.

`/Users/symverse/ai-glasses-app`은 빈 폴더이고 git 저장소가 아니다. 새 프로젝트로 시작한다.

사용자 결정:
- AI: **OpenAI Realtime API** (실시간 음성 + 카메라 프레임)
- 1차 범위: **보고 묻기, 간판·메뉴 번역, 대화 번역 자막, 음성으로 촬영** 네 가지 모두
- 호출: **화면 탭** (웨이크워드는 이후 단계)
- 키: **앱 내 키**(`local.properties` → `BuildConfig`), 검증은 **실기기**

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
| 음성 촬영 | 보고 묻기에서 "사진 찍어줘 / 동영상 찍어줘 / 그만" → `tools`: `take_photo`, `start_video`, `stop_video` → `function_call_output` | 어시스턴트 |

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
- [ ] shouldConnectAndSendSessionUpdateOnStart
- [ ] shouldStreamMicAudioWhileListening
- [ ] shouldSendFrameThenCommitAndRequestResponseOnSecondTap
- [ ] shouldShowAssistantTranscriptOnHudAsDeltasArrive
- [ ] shouldPlayAudioDeltasToSink
- [ ] shouldShowUserTranscriptOnHud
- [ ] shouldReturnToIdleOnResponseDone
- [ ] shouldCancelTruncateAndStopPlaybackWhenTappedWhileSpeaking
- [ ] shouldShowErrorOnHudWhenTransportFails
- [ ] shouldShowErrorWhenApiKeyMissing

### 3단계: 어댑터와 화면, 첫 실기기 확인 (수동 검증)
- [ ] `OkHttpRealtimeTransport`, `CameraXFrameSource`, `MicRecorder`, `PcmPlayer`
- [ ] `GlassesScreen` HUD(모노 그린, 상태·자막), 권한 요청과 거부 안내
- [ ] 실기기에서 보고 묻기 확인. 실제 이벤트 형태가 문서 요약과 다르면 1단계 테스트부터 고친다

### 4단계: 글자 번역
- [ ] shouldSendFrameWithTranslateInstructionAndTextOnlyResponse
- [ ] shouldShowTranslationTextOnHud
- [ ] HUD 모드 전환 UI, 실기기 확인

### 5단계: 음성으로 촬영
- [ ] shouldRegisterCaptureToolsInSessionUpdate
- [ ] shouldDecodeFunctionCallItem
- [ ] shouldTakePhotoAndReturnFunctionOutputOnTakePhotoCall
- [ ] shouldStartAndStopVideoOnToolCalls
- [ ] shouldShowRecordingIndicatorWhileRecording
- [ ] shouldReturnFailureOutputWhenCaptureFails
- [ ] `CameraXCaptureController`, 실기기 확인. 동영상 오디오 녹음과 음성 명령용 마이크가 함께 도는지 확인하고, 안 되면 1차는 무음 동영상으로 한다

### 6단계: 대화 번역 자막
- [ ] 번역 엔드포인트가 한국어(`ko`)를 받는지 실제 연결로 확인. 안 되면 이 단계 테스트를 일반 세션 방식으로 바꾼다
- [ ] shouldEncodeTranslationSessionUpdateWithTargetLanguage (`TranslationEventsTest`)
- [ ] shouldEncodeTranslationAudioAppend
- [ ] shouldDecodeOutputTranscriptDelta
- [ ] shouldDecodeInputTranscriptDelta
- [ ] shouldOpenTranslationConnectionWhenStarted (`TranslationSessionTest`)
- [ ] shouldSendMicAudioIn200msChunks
- [ ] shouldAppendTranslatedCaptionToHud
- [ ] shouldKeepOnlyRecentCaptionLines
- [ ] shouldSendSessionCloseWhenStopped
- [ ] 실기기 확인

### 이후 단계 (이번 범위 밖)
웨이크워드, 60분 세션 만료 재연결, 임시 키 백엔드, WebRTC 전송, 오프라인 번역, 텔레프롬프터, 내비게이션.

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
