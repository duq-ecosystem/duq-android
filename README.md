# Jarvis Android

> Voice-controlled AI assistant for Android with Keycloak SSO

Mobile client for Jarvis AI with wake word detection, voice activity detection, and automatic cross-channel synchronization with Telegram.

## Features

- 🔐 **Keycloak SSO** - Single Sign-On authentication via OIDC
- 🎤 **Wake Word Detection** - "JARVIS" using Porcupine (sensitivity: 0.5f)
- 🗣️ **Voice Activity Detection** - Silero VAD with 2s silence timeout
- 💬 **Unified Conversations** - Shared history between Mobile and Telegram
- 🔄 **Auto-Sync** - Messages automatically appear in Telegram
- ⚡ **Reactive UI** - Room DB + Flow for instant updates
- 🧠 **Cross-Channel Memory** - 20 message history + Cortex wiki memory
- 📱 **App-Only Mode** - Microphone disabled when minimized (battery efficient)

## Quick Start

### Prerequisites

- Android SDK 26+ (Android 8.0+)
- Porcupine API key ([get free key](https://console.picovoice.ai/))
- Keycloak account (login via app)

### Installation

1. Clone repository:
   ```bash
   git clone https://github.com/Danny-sth/vtoroy-android.git jarvis-android
   cd jarvis-android
   ```

2. Build APK:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install on device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. Configure app:
   - Open app → Settings
   - Tap "Login with Keycloak" → authenticate
   - Enter Porcupine API key
   - Grant microphone permission

### Quick Test

```bash
# Set Porcupine key via ADB (for testing)
adb shell am start -n com.jarvis.android/.MainActivity \
  --es porcupine_key "YOUR_PORCUPINE_KEY"

# View logs
adb logcat -s JarvisListenerService:D WakeWordManager:D KeycloakAuthManager:D
```

## Authentication

### Keycloak SSO

The app uses **Keycloak OIDC** for authentication via the AppAuth library:

- **Flow**: Authorization Code + PKCE
- **Tokens**: Access token (short-lived), Refresh token (long-lived)
- **Auto-refresh**: Tokens refreshed automatically before expiry
- **Logout**: Clears local tokens

### Keycloak Configuration

Keycloak client (`jarvis-android`):
- **Client Type**: Public
- **Redirect URI**: `com.jarvis.android://oauth/callback`
- **PKCE**: Required (S256)

### Token Storage

Tokens stored securely in Android DataStore:
- `access_token` - Keycloak JWT for API calls
- `refresh_token` - For obtaining new access tokens
- `id_token` - User identity information
- `token_expires_at` - Expiration timestamp

## Architecture

```
Wake Word (Porcupine "JARVIS")
       ↓
JarvisListenerService (foreground)
       ↓
VoiceCommandProcessor
       ↓
AudioRecorder (PCM 16kHz → WAV)
       ↓
VoiceActivityDetector (Silero, 2s silence)
       ↓
POST /api/voice (Keycloak JWT) → PostgreSQL → Room DB → Flow → UI
       ↓                              ↓
   AudioPlayer                   Telegram (auto-sync)
```

### Authentication Flow

```
User taps "Login with Keycloak"
       ↓
AppAuth opens Chrome Custom Tab
       ↓
Keycloak login page (90.156.230.49:8180)
       ↓
User authenticates
       ↓
Redirect: com.jarvis.android://oauth/callback
       ↓
AppAuth receives authorization code
       ↓
Exchange code for tokens
       ↓
Store tokens in DataStore
       ↓
Fetch user info from Keycloak
       ↓
Ready to use Jarvis!
```

### Data Sync

```
Mobile App → jarvis-gateway → PostgreSQL (source of truth)
                                    ↓
                    ┌───────────────┴──────────────┐
                    ▼                              ▼
              Room DB (cache)                Telegram Bot
                    ↓                        (auto-push)
              Flow → LazyColumn
            (reactive updates)
```

**Features:**
- 📱 Mobile messages → Telegram: `*[Mobile App]*\n\n{text}`
- 🤖 Assistant responses → Telegram: `*[Jarvis]*\n\n{response}`
- 🔄 Pull-on-focus: app loads latest messages when gaining foreground
- ⚡ Reactive UI: Room Flow auto-updates when new messages arrive

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.2.0 |
| UI | Jetpack Compose (BOM 2024.11.00) + Material3 |
| DI | Hilt 2.56 |
| Auth | AppAuth 0.11.1 (OIDC) |
| Database | Room 2.6.1 (offline cache) |
| Network | OkHttp 4.12.0 |
| Wake Word | Porcupine Android 3.0.2 |
| VAD | Silero android-vad 2.0.10 |
| Audio | Media3 ExoPlayer 1.2.1 |
| Persistence | DataStore Preferences |

## Project Structure

```
app/src/main/java/com/jarvis/android/
├── auth/                     # Keycloak OIDC
│   ├── KeycloakConfig.kt     # Keycloak URLs, client config
│   └── KeycloakAuthManager.kt # Auth flow, token management
├── service/                  # JarvisListenerService, VoiceCommandProcessor
├── wakeword/                 # Porcupine WakeWordManager
├── audio/                    # AudioRecorder, AudioPlayer, VAD
├── network/                  # JarvisApiClient + API models
├── data/                     # Repositories, Room DB, models
│   ├── SettingsRepository.kt # Token storage
│   ├── ConversationRepository.kt
│   ├── local/                # Room entities, DAOs, database
│   └── model/                # Message, Conversation
├── ui/                       # Compose screens + ViewModels
│   ├── MainScreen.kt
│   ├── SettingsScreen.kt     # Keycloak login UI
│   ├── ConversationViewModel.kt
│   └── components/           # MessageBubble, MessagesList
├── di/                       # Hilt modules
└── util/                     # Constants, permissions
```

## API Integration

**Backend**: `https://on-za-menya.online` (jarvis-gateway)
**Auth**: Keycloak JWT in Authorization header

### Endpoints

```http
POST /api/voice
Authorization: Bearer <keycloak-jwt>
Content-Type: multipart/form-data
Body: audio (WAV file)
Response: { text: string, audio: base64 OGG }

GET /api/conversations
Authorization: Bearer <keycloak-jwt>
Response: [{ id, userId, title, startedAt, lastMessageAt }]

GET /api/conversations/{id}/messages?limit=50
Authorization: Bearer <keycloak-jwt>
Response: [{ id, conversationId, role, content, createdAt }]

POST /api/conversations
Authorization: Bearer <keycloak-jwt>
Body: { title?: string }
Response: { id, userId, title, startedAt }
```

### Telegram Integration

Backend automatically syncs Mobile messages to Telegram:
- User message: `📱 *[Mobile App]*\n\n{command}`
- Assistant response: `🤖 *[Jarvis]*\n\n{response}`
- Commands: `/history [N]` - view last N messages

## Development

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Run tests
./gradlew test
```

### Debugging

```bash
# Auth logs
adb logcat -s KeycloakAuthManager:D SettingsRepository:D

# Voice pipeline logs
adb logcat -s WakeWordManager:D VoiceActivityDetector:D JarvisApiClient:D

# All app logs
adb logcat | grep -E "Jarvis|WakeWord|VoiceActivity|ApiClient|Keycloak"

# Conversation sync logs
adb logcat -s ConversationViewModel:D ConversationRepository:D

# Clear app data
adb shell pm clear com.jarvis.android
```

### Testing

#### Manual Test: Keycloak Login
1. Open app → Settings
2. Tap "Login with Keycloak"
3. Authenticate in browser
4. Verify user info displayed in app

#### Manual Test: Voice Command
1. Open app
2. Say "JARVIS"
3. Say command (e.g., "What's the weather?")
4. Check Telegram for auto-synced messages

#### Manual Test: Token Refresh
1. Login to app
2. Wait for token to near expiry
3. Make API call
4. Verify automatic token refresh in logs

## Configuration

### Keycloak Server

Update `auth/KeycloakConfig.kt`:
```kotlin
const val KEYCLOAK_URL = "http://90.156.230.49:8180"
const val REALM = "jarvis"
const val CLIENT_ID = "jarvis-android"
```

### Wake Word Sensitivity

Adjust in `wakeword/WakeWordManager.kt`:
```kotlin
.setSensitivity(0.5f)  // 0.0-1.0: lower = faster, higher = stricter
```

### VAD Silence Timeout

Adjust in `audio/VoiceActivityDetector.kt`:
```kotlin
private val silenceTimeoutMs = 2000L  // milliseconds
```

## Troubleshooting

### Login fails
- Check Keycloak server is accessible: `curl http://90.156.230.49:8180/realms/jarvis`
- Verify `jarvis-android` client exists in Keycloak
- Check redirect URI matches: `com.jarvis.android://oauth/callback`
- Check logs: `adb logcat -s KeycloakAuthManager:D`

### Token expired / 401 errors
- App should auto-refresh tokens
- If fails, logout and re-login
- Check logs: `adb logcat -s JarvisApiClient:D`

### Wake word not detected
- Check Porcupine API key is valid
- Lower sensitivity (0.5f → 0.3f)
- Ensure microphone permission granted
- Check logs: `adb logcat -s WakeWordManager:D`

### UI not updating
- Check Room DB Flow is active
- Verify `refreshMessages()` called after voice command
- Check logs: `adb logcat -s ConversationViewModel:D`

## Documentation

Detailed documentation in `.claude/` directory:

```
.claude/
├── CLAUDE.md                    # Quick reference for developers
├── NOTES.md                     # Session notes, decisions
├── docs/
│   ├── architecture.md          # Full tech stack, state machine
│   ├── services.md              # Android services explained
│   ├── dependencies.md          # Libraries reference
│   └── conversation-sync.md     # Conversation sync guide
└── skills/
    ├── build-deploy.md          # Build and deployment
    ├── debug-voice.md           # Voice pipeline debugging
    ├── add-feature.md           # Adding features
    └── troubleshooting.md       # Common errors
```

## Contributing

1. Fork repository
2. Create feature branch: `git checkout -b feature/my-feature`
3. Commit changes: `git commit -am 'Add feature'`
4. Push to branch: `git push origin feature/my-feature`
5. Submit pull request

## License

Private project - © 2026 Danny-sth

## Related Projects

- [jarvis-gateway](https://github.com/Danny-sth/jarvis-gateway) - Backend API gateway
- [not-that-jarvis](https://github.com/Danny-sth/not-that-jarvis) - Core AI agent

## Support

For issues and questions, see `.claude/skills/troubleshooting.md` or create an issue.

---

**Built with ❤️ and Claude Code**
