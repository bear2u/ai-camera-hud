# AI Camera HUD

An Android app that turns a phone into a handheld "AI glasses" viewer. The rear camera fills the screen, and a heads-up display on top of it lets you talk to an AI about what the camera sees, translate text and speech, and get worked solutions to problems you point the camera at.

The AI side runs on the OpenAI Realtime API over a WebSocket. You bring your own API key and enter it in the app's settings; no key is built into the app or stored in this repository.

## Features

- **Ask about what you see.** Speak or type a question. The app sends the current camera frame with it and plays the spoken answer, with the transcript shown in the chat panel. Tap again while the AI is speaking to cut it off.
- **Text translation.** Point at a sign, menu, or label and get a Korean translation as text.
- **Conversation interpreting.** The app keeps listening and shows what the other person says as Korean captions, one line per turn.
- **Problem solving.** Point at a problem and ask by voice or text ("solve only number 2", "just a hint"). The answer and the steps appear as text. Sending without speaking solves whatever is visible.
- **Chat panel.** Every answer lands in a scrollable chat log on the left, with a mode selector, a text input, and a microphone button. The panel and the whole HUD can each be hidden.
- **Photo and video capture.** Buttons on the right save photos and videos to the gallery. Captures contain the camera image only, never the HUD.
- **Bluetooth earbuds.** When earbuds are connected, the app uses their microphone while it listens to you and releases it afterwards so answers play at normal quality. Interpreting mode always uses the phone's microphone.
- **Settings.** Enter and save the API key, pick a Realtime model, and run a connection test before saving.

## Requirements

- Android 10 (API 29) or later, with a rear camera and microphone
- An OpenAI API key with access to a Realtime model (the default is `gpt-realtime-2.1`)
- To build: JDK 21 and the Android SDK with platform 37.2

## Build and install

```bash
./gradlew :app:installDebug
```

`gradle.properties` pins the Gradle daemon to a JDK 21 install path. Change `org.gradle.java.home` to match your machine, or remove the line if your default JDK is 17 or 21. Create `local.properties` with `sdk.dir=<path to your Android SDK>` if Android Studio has not done so.

## Using the app

1. Open the app in landscape and allow camera and microphone access.
2. Tap the gear icon, paste your API key, choose a model, tap the connection test, then save.
3. Pick a mode at the top of the chat panel: Ask (질문), Translate (번역), Interpret (통역), or Solve (풀이).
4. Type a message, tap the microphone button, or tap anywhere on the camera view to start and stop speaking.

The icons in the top-right corner hide the chat panel, hide the whole HUD, switch the AI connection off and on, and open settings.

## How it works

The code is split so that the conversation logic runs without Android and is covered by JVM unit tests.

| Package | Role |
|---|---|
| `core` | Session state machine, HUD state, chat history, capture and settings logic, and the interfaces for camera, microphone, audio output, and transport |
| `realtime` | Encoding and decoding of Realtime API events, and the OkHttp WebSocket transport |
| `camera` | CameraX preview, frame capture for the AI, photo and video saving |
| `audio` | Microphone recording, PCM playback, Bluetooth microphone routing |
| `ui` | Jetpack Compose screens: the camera HUD and the settings page |

Audio is 24 kHz 16-bit mono PCM in both directions. Camera frames are sent as JPEG, in high detail for translation and problem solving so small print stays readable.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

The unit tests need no device and no network. A separate set of live tests in `RealtimeLiveSmokeTest` talks to the real API and is skipped unless you opt in. To run it, put `OPENAI_API_KEY=<your key>` in `local.properties` (which is git-ignored) and set `OPENAI_LIVE=1`:

```bash
OPENAI_LIVE=1 ./gradlew :app:testDebugUnitTest --tests '*RealtimeLiveSmokeTest'
```

These tests use your API quota.

## API key handling

- The key is entered in the app and stored in the app's private preferences on the device. It is excluded from cloud backup and device transfer.
- The key is not compiled into the APK and is not in this repository.
- The app connects to OpenAI directly from the phone. That is fine for personal use; an app distributed to other people should issue short-lived client secrets from a backend instead of holding a standard API key.

## Known limitations

- Voice questions show as "(음성 질문)" in the chat unless the account can use an input transcription model.
- Interpreting uses a general Realtime session with a translation instruction, not the dedicated translation model.
- Photos are saved at 1920x1440 because the same capture pipeline feeds the AI.
- A Realtime session lasts at most 60 minutes; the app does not reconnect on its own yet.
- The interface text is Korean, and translation targets Korean.
- Object labels anchored in the camera view are not implemented.
