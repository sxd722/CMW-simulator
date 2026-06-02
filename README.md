# CMW-simulator

An Android application that demonstrates **Server-Driven UI (SDUI)** using [Jetpack Compose Remote](https://developer.android.com/develop/ui/compose/remote). The app lets you generate UI from natural language prompts via AI models (Gemini / GLM), paste DSL code or JSON, and render it natively on-device.

## Features

| Mode | Description |
|---|---|
| **Gemini** | Enter a natural language prompt; Gemini generates Remote Compose Kotlin DSL, which is compiled to binary RC by a local backend and rendered in the player. |
| **GLM 5.1** | Same flow as Gemini, but uses Zhipu AI's GLM-4-flash model. |
| **GLM JSON** | Enter a natural language prompt; GLM generates an SDUI JSON document which is rendered **locally** — no backend needed. |
| **Paste DSL** | Paste Remote Compose Kotlin DSL code; it is sent to the local backend for compilation and rendered in the player. |
| **Paste JSON** | Paste an SDUI JSON document; it is rendered locally without any backend or API call. |

The app also includes a **file picker** to load pre-built `.rc` binary documents directly.

---

## Prerequisites

| Requirement | Minimum Version |
|---|---|
| **Android Studio** | Ladybug (2024.2.1) or newer — Hedgehog/Iguana also work |
| **Android SDK** | API 37 (compile), API 29 (minimum) |
| **JDK** | 17 (bundled with recent Android Studio) |
| **Emulator / Device** | Android 10 (API 29) or above |
| **Internet access** | Required for Gemini / GLM API calls |
| **(Optional) RC Backend** | A Ktor server on `http://10.0.2.2:8080/compile-rc` for DSL compilation |

---

## Download & Open

### Option A: Clone with Git

```bash
git clone <repository-url> CMWsimulator
cd CMWsimulator
```

### Option B: Download ZIP

1. Download the repository ZIP and extract it.
2. Open Android Studio → **File → Open** → select the extracted `CMWsimulator` folder.

---

## Configuration

### 1. API Keys

Create or edit `local.properties` in the **project root** (this file is git-ignored) and add:

```properties
# Optional — enables the "Gemini" mode
GEMINI_API_KEY=your_gemini_api_key_here
```

> **Note:** The GLM API key is embedded in the app for convenience. If you need to change it, edit the `apiKey` constant in `GeneratorScreen.kt` (functions `callGlmApi` and `callGlmJsonApi`).

### 2. Gradle Sync

After opening the project, Android Studio should prompt you to sync Gradle. If not:

- Click the **"Sync Project with Gradle Files"** button (🐘 icon in the toolbar), or
- Go to **File → Sync Project with Gradle Files**

Wait for the sync to complete. The first sync may download dependencies and take a few minutes.

---

## Set Up the Emulator

### Create a Virtual Device

1. Open **Tools → Device Manager** (or click the device dropdown in the toolbar).
2. Click **"Create Virtual Device"**.
3. Select a device profile (e.g., **Pixel 6** or **Pixel 8**) → **Next**.
4. Choose a system image:
   - **Recommended:** API 34 or 35 with **Google APIs** (not Google Play).
   - **Minimum:** API 29 (Android 10).
   - If the image is not downloaded, click **"Download"** next to it.
5. Click **Next → Finish**.

### Start the Emulator

1. In the **Device Manager**, click the **▶ (Play)** button next to your AVD.
2. Wait for the emulator to boot to the home screen.
3. Make sure the emulator appears in the run configuration dropdown (top toolbar).

### (Optional) Connect a Physical Device

1. Enable **Developer Options** and **USB Debugging** on the device.
2. Connect via USB — the device should appear in the run dropdown.

---

## Build & Run

### From Android Studio

1. Select the **`app`** module and your emulator/device from the toolbar dropdown.
2. Click the **▶ Run** button (or press **Shift+F10**).
3. The app will build, install, and launch on the emulator.

### From the Command Line

```bash
# Debug build
./gradlew :app:assembleDebug

# Install and run on connected device/emulator
./gradlew :app:installDebug
```

---

## Testing the App

### GLM JSON (no backend needed)

1. Launch the app.
2. Select the **"GLM JSON"** chip.
3. Type a prompt, e.g.: `帮我生成一个包含标题和点赞按钮的商品卡片`
4. Tap **"✨ 生成并渲染"**.
5. The GLM API will generate an SDUI JSON document and navigate to the local JSON renderer.

### Paste JSON (fully offline)

1. Select the **"Paste JSON"** chip.
2. Paste an SDUI JSON document into the text field.
3. Tap **"🚀 直接渲染"**.
4. The JSON is parsed and rendered locally — no network or backend needed.

Example JSON:
```json
{
  "backgroundColor": "#F5F5F5",
  "elements": [
    {
      "type": "RemoteText",
      "id": "1",
      "text": "Hello, Remote Compose!",
      "color": "#1A1A2E",
      "fontSize": 24,
      "fontWeight": "Bold"
    }
  ]
}
```

### Paste DSL / Gemini / GLM 5.1 (requires backend)

These modes send Kotlin DSL to a local Ktor backend at `http://10.0.2.2:8080/compile-rc` which compiles it to binary RC format. If the backend is not running, these modes will fail with a connection error.

The `10.0.2.2` address maps to the host machine's `localhost` from within the Android emulator. If you are using a physical device, change `BACKEND_URL` in `RcCompiler.kt` to your machine's LAN IP (e.g., `http://192.168.1.100:8080/compile-rc`).

### Loading .rc Files

From the **RC 文档测试台** (tester screen), tap **"选择并加载 .rc 文件"** to pick a pre-compiled Remote Compose binary file from device storage.

---

## Project Structure

```
app/src/main/java/com/example/cmw_simulator/
├── MainActivity.kt          # Navigation, RC tester screen, AndroidView RC player
├── GeneratorScreen.kt       # AI prompt UI, API calls (Gemini/GLM), system prompts
├── JsonUiRenderer.kt        # SDUI JSON parser & Compose renderer
├── RcCompiler.kt            # Backend HTTP client for DSL → RC compilation
└── SharedRcViewModel.kt     # Shared state between generator and tester screens
```

## Key Dependencies

| Library | Purpose |
|---|---|
| `androidx.compose.remote:*` (v1.0.0-alpha11) | Remote Compose player & creation |
| `com.google.ai.client.generativeai` | Google Generative AI SDK (Gemini) |
| `com.squareup.okhttp3:okhttp` | HTTP client for GLM API calls |
| `androidx.navigation:navigation-compose` | Screen navigation |
| `androidx.compose.material3` | Material Design 3 UI components |

## Troubleshooting

| Problem | Solution |
|---|---|
| **Gradle sync fails** | Make sure you have JDK 17 and Android SDK API 37 installed. Check internet connection for dependency downloads. |
| **"未配置 Gemini API Key" error** | Add `GEMINI_API_KEY=...` to `local.properties` in the project root. |
| **"Failed to reach backend" error** | The RC compilation backend is not running. This only affects Gemini, GLM 5.1, and Paste DSL modes. Use GLM JSON or Paste JSON instead. |
| **Emulator won't start** | Ensure Hardware Acceleration (HAXM on Windows/Intel, Hypervisor on AMD/Mac) is enabled in BIOS. |
| **App crashes on launch** | Check that the emulator is running API 29+. Run `./gradlew :app:installDebug` for a clean install. |
| **RC player shows blank** | This has been fixed — ensure you have the latest code. The `setDocument()` call now only happens after the view is attached and only when bytes change. |

## License

This project is for educational and demonstration purposes.
