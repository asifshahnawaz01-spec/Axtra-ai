# AXTRA AI — Personal Jarvis Voice Assistant
**Owner:** 🅜🅡 🅐🅢🅘🅕  
**Tagline:** *Your Personal AI Companion*

---

## 1. Architecture Overview

```
+-------------------------------------------------------------------+
|                        Jetpack Compose UI                         |
|  Splash | Onboarding | LockScreen | Home Orb | Commands & Tools   |
|  Routines | History & Memories | Settings | KeepAlive | Health    |
+-------------------------------------------------------------------+
                                  |
                        Hilt ViewModels (StateFlow)
                                  |
+---------------------------------+---------------------------------+
|         AI Brain Module         |      Voice & Hotword Engine     |
| - Firebase AI Logic (Gemini)    | - AxtraVoiceInteractionService  |
| - App Check (Debug/reCAPTCHA)   | - HotwordCoordinator ("Astra")  |
| - JsonValidator (Schema Repair) | - AxtraSpeechRecognizer (ur-PK) |
| - OfflineCommandParser          | - AxtraTtsEngine + Foreground   |
+---------------------------------+---------------------------------+
                                  |
+---------------------------------+---------------------------------+
|      Phone Control Engine       |  Cloud & Local Persistence      |
| - ActionExecutor (Step Queue)   | - Firebase Firestore + Auth     |
| - AxtraAccessibilityService     | - Google Credential Manager     |
| - AxtraNotificationListener     | - Room (memories/notes/todos)   |
| - OverlayService (Auto-Execute) | - DataStore + EncryptedKeyStore |
+---------------------------------+---------------------------------+
```

---

## 2. How to Open & Build in Android Studio

1. Open Android Studio (Ladybug / Meerkat or newer) and choose **Open** on this project directory.
2. Let Gradle sync (AGP 9.1.1, Kotlin 2.2.10, KSP 2.3.5, Hilt 2.59.2, Min SDK 26, Target SDK 36).
3. Build debug APK: `gradle :app:assembleDebug` (or click **Run ▶** in Android Studio).
4. Run unit & Robolectric tests: `gradle :app:testDebugUnitTest`.

---

## 3. Firebase AI Logic, App Check & Cloud Firestore Setup

1. **No Manual Gemini API Key Needed:** Axtra AI uses **Firebase AI Logic (`Firebase.ai`)** with **Firebase App Check** (Debug provider in debug builds, reCAPTCHA Enterprise in release builds).
2. Sign in with your Google Account on launch via **Credential Manager** to sync memories, notes, expenses, to-dos, and routines to **Cloud Firestore**.
3. Open **Settings > AI Brain** to select the Gemini model (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`), adjust temperature/tokens, and tap **Test Gemini AI**.
4. Open **Settings > Cloud Account & Firestore Sync** to sync local records or manage your session.

---

## 4. Enabling Permissions & Keeping Axtra Alive

1. On first launch, complete the **18-card Permissions Onboarding Wizard** (or revisit **Settings > Permission Health** anytime).
2. Enable **Accessibility Service** (`Settings > Accessibility > Axtra AI`) for multi-step UI automation, Focus Mode, Volume-Down shortcut, and Screen Assistant.
3. Enable **Display Over Other Apps** for the floating orb / edge glow.
4. Enable **Notification Access** for notification reading, summarization, and smart replies.
5. Open **Settings > Keep Axtra Alive Guide** to disable battery optimization and follow OEM-specific steps for Xiaomi/MIUI, Oppo/Realme, Vivo, Samsung, Infinix/Tecno, and Huawei.

---

## 5. Known Android Limitations & Workarounds

| Limitation | Reason (Android OS) | Axtra AI Workaround |
|---|---|---|
| Direct Wi-Fi / Mobile Data toggle | Restricted for apps on Android 10+ (API 29+) | Opens official `Settings.Panel.ACTION_WIFI` / `ACTION_INTERNET_CONNECTIVITY` |
| Call Recording | Blocked on Android 9+ without privileged system signature | Not implemented; respects Google Play & Android security policies |
| Background Microphone on Android 14+ | Strict foreground service microphone start rules | Starts `AxtraForegroundService` (`foregroundServiceType="microphone"`) from UI/Quick Tile + pauses on `<10%` battery or active call |
| Banking / Payment Screens | `FLAG_SECURE` & security sensitivity | Blocked by default via `BlockedAppsRepository`; Axtra never taps final Pay/Confirm |
| System Dialog Auto-Click | Android blocks Accessibility from clicking permission grant dialogs | Shows clear user prompt & opens exact system setting screen |

---

## 6. Feature & Testing Checklist

| Feature | Status | How to Test |
|---|---|---|
| Gemini Brain (Firebase AI Logic) + JSON Validation + 429 Backoff | Ready | Tap **Test Gemini AI** in Settings > AI, send Urdu/Saraiki/English command |
| Offline Command Parser | Ready | Turn off internet and say `"Torch on karo"` or `"YouTube kholo"` |
| Urdu Speech-to-Text + TTS + Barge-in | Ready | Tap Home AI Orb, speak Urdu, then tap Orb mid-reply to interrupt |
| Always-On Service + Tile + Widget + Boot | Ready | Toggle **Always On** on Home or Quick Settings Tile; verify persistent notification |
| VoiceInteractionService Hotword ("Hey Astra" / "Hello Astra" / "Astra") | Ready | Enable Hotword in Settings > Voice, tap **Set Default Assistant**, and say `"Hey Astra"` or `"Astra, torch on karo"` |
| Floating Orb Auto-Execute (Without Opening App) | Ready | Grant Overlay permission, enable Always On, tap floating orb on any screen to speak & auto-execute; long-press for quick menu |
| Multi-Step Planner + 10s Confirmation | Ready | Send `"Ahmed ko WhatsApp par likho ke main late hun"`, verify step progress & confirmation |
| Blocked Apps Guard | Ready | Open a banking app or add package in Settings; verify Screen Assistant refuses reading |
| 18 Smart Tools (Briefing, Prayer, OCR, QR, Notes, Expenses, Todos, Focus, Health, Find Phone) | Ready | Open **Commands > Smart Tools** tab and test each interactive tool card |
| Backup & Restore + Delete All Data | Ready | Use **Settings > Export Backup / Import Backup** or **Privacy > Delete All My Data** |
