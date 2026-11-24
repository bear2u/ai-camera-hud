# AI Camera HUD

An Android app that turns a phone into a handheld "AI glasses" viewer. The rear camera fills the screen, and a heads-up display on top of it lets you talk to an AI about what the camera sees, translate text and speech, and get worked solutions to problems you point the camera at.

The AI side runs on the OpenAI Realtime API over a WebSocket, and walking directions come from the Google Routes API. You bring your own API keys and enter them in the app's settings; no key is built into the app or stored in this repository.

## Features

- **Ask about what you see.** Speak or type a question. The app sends the current camera frame with it and plays the spoken answer, with the transcript shown in the chat panel. Tap again while the AI is speaking to cut it off.
- **Text translation.** Point at a sign, menu, or label and get a Korean translation as text.
- **Conversation interpreting.** The app keeps listening and shows what the other person says as Korean captions, one line per turn.
- **Problem solving.** Point at a problem and ask by voice or text ("solve only number 2", "just a hint"). The answer and the steps appear as text. Sending without speaking solves whatever is visible.
- **Location-aware answers.** With location access allowed, each question carries your coordinates, a street address, and the direction the camera is facing, so the AI can tell which building or place you are looking at.
- **Walking navigation.** In navigation mode, type a destination and a card on the HUD shows the next turn, the distance to it, an arrow pointing toward it relative to where the camera faces, and the remaining distance. Each new step is also read aloud.
- **Chat panel.** Every answer lands in a scrollable chat log on the left, with a mode selector, a text input, and a microphone button. The panel can be shown, made transparent so only the messages float over the camera view, or hidden, and the whole HUD can be hidden too.
- **Photo and video capture.** Buttons on the right save photos and videos to the gallery. Captures contain the camera image only, never the HUD.
- **Send a photo to the chat.** Right after taking a photo, an analyze button attaches it to the chat input. Choose analysis or translation, add a note if you like, and send; the answer comes back as text.
- **Bluetooth earbuds.** When earbuds are connected, the app uses their microphone while it listens to you and releases it afterwards so answers play at normal quality. Interpreting mode always uses the phone's microphone.
- **Settings.** Enter and save the API keys, pick a Realtime model, and run a connection test before saving.

## Requirements

- Android 10 (API 29) or later, with a rear camera and microphone
- An OpenAI API key with access to a Realtime model (the default is `gpt-realtime-2.1`)
- Optional, for navigation: a Google Maps Platform API key with the Routes API enabled
- To build: JDK 21 and the Android SDK with platform 37.2

## Build and install

```bash
./gradlew :app:installDebug
```

`gradle.properties` pins the Gradle daemon to a JDK 21 install path. Change `org.gradle.java.home` to match your machine, or remove the line if your default JDK is 17 or 21. Create `local.properties` with `sdk.dir=<path to your Android SDK>` if Android Studio has not done so.

## API keys

The app needs one key and can use a second. Both are entered in the app (gear icon → settings), never in the source code.

### OpenAI API key (required)

1. Create a key at <https://platform.openai.com/api-keys>.
2. Make sure the project behind the key can use a Realtime model. The app offers `gpt-realtime-2.1` (default), `gpt-realtime-2.1-mini`, `gpt-realtime-2`, `gpt-realtime-1.5`, and `gpt-realtime`.
3. Optional: allow an input transcription model such as `gpt-4o-mini-transcribe` so your spoken questions appear as text in the chat.
4. In the app's settings, paste the key, pick a model, and tap the connection test. It reports success or the server's reason for failure. Then save.

### Google Maps Platform API key (optional, navigation only)

1. In the Google Cloud console, create or pick a project and enable billing on it. The Routes API requires a billing account.
2. Enable the **Routes API** (`routes.googleapis.com`) for the project.
3. Create an API key and restrict it to the Routes API.
4. In the app's settings, paste it into the Google Maps API key field and save.

Without this key every other feature still works; navigation mode shows a message asking for the key. Route requests are billed by Google per request, so check the current Routes API pricing and free usage cap before relying on it.

## Using the app

1. Open the app in landscape and allow camera and microphone access. Allow location access too if you want location-aware answers and navigation.
2. Tap the gear icon and set up your keys as described above.
3. Pick a mode at the top of the chat panel: Ask (질문), Translate (번역), Interpret (통역), Solve (풀이), or Navigate (길안내).
4. Type a message, tap the microphone button, or tap anywhere on the camera view to start and stop speaking. In navigation mode, what you type is the destination; speaking still asks the AI a question.

The icons in the top-right corner hide the chat panel, hide the whole HUD, switch the AI connection off and on, and open settings.

## How it works

The code is split so that the conversation logic runs without Android and is covered by JVM unit tests.

| Package | Role |
|---|---|
| `core` | Session state machine, HUD state, chat history, navigation (route parsing, step tracking, distance and bearing), capture and settings logic, and the interfaces for camera, microphone, audio output, location, and transport |
| `realtime` | Encoding and decoding of Realtime API events, and the OkHttp WebSocket transport |
| `camera` | CameraX preview, frame capture for the AI, photo and video saving |
| `audio` | Microphone recording, PCM playback, Bluetooth microphone routing, spoken navigation prompts |
| `location` | GPS and network position, compass heading, reverse geocoding, and the Google Routes API client |
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

- Keys are entered in the app and stored in the app's private preferences on the device. They are excluded from cloud backup and device transfer.
- Keys are not compiled into the APK and are not in this repository.
- The app connects to OpenAI and Google directly from the phone. That is fine for personal use; an app distributed to other people should issue short-lived client secrets from a backend instead of holding standard API keys.
- When location access is allowed, your coordinates, address, and camera heading are sent to OpenAI with each question, and your position and destination are sent to Google when you start navigation.

## Known limitations

- Voice questions show as "(음성 질문)" in the chat unless the account can use an input transcription model.
- Interpreting uses a general Realtime session with a translation instruction, not the dedicated translation model.
- Photos are saved at 1920x1440 because the same capture pipeline feeds the AI.
- A Realtime session lasts at most 60 minutes; the app does not reconnect on its own yet.
- The interface text is Korean, and translation targets Korean.
- Object labels anchored in the camera view are not implemented.
- Navigation has not been tested against the live Routes API or on a real walk yet. Walking routes are a beta feature of the Routes API and may miss sidewalks or pedestrian paths.
- Navigation destinations are typed, not spoken, and the route is not recalculated if you leave it.

## License

Released under the [MIT License](LICENSE).
