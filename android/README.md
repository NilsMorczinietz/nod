# Nod for Android

Native Kotlin + Jetpack Compose (Material 3) app. It receives Claude Code requests
from your relay and lets you answer them from the lock screen or in the app.

## Build

Requirements: JDK 17 and an Android SDK with platform 37.2.

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (R8, see "Release signing")
```

The version comes from `gradle.properties` (`nod.versionName`, `nod.versionCode`);
`scripts/version.mjs` in the repo root bumps both.

## How it works

| Concern | Implementation |
|---|---|
| Pairing | `nod pair` on your computer shows a QR code (`nod://pair?relay=…&code=…`). The app scans it with the Google code scanner (no camera permission needed), or you type in the relay URL and code. |
| Credentials | The device token is encrypted with an AES-256-GCM key in the Android Keystore and kept in DataStore. Nothing is backed up. |
| Live updates | While the app is visible it holds a WebSocket to `/v1/stream` (sends `ping` every 30 s and reconnects with backoff). |
| Background | FCM data messages (high priority for requests) → notifications "Claude Code needs you" with **Approve · Deny · Deny with note** (RemoteInput), a countdown chronometer, and up to three answer buttons for single-choice questions. A `BroadcastReceiver` sends the answer without opening the app. |
| Lock screen | Requests use a high-importance channel with public lock-screen visibility, so *Approve · Deny · Deny with note* work without unlocking (RemoteInput included). Settings → Approvals: "Show details on lock screen" (on by default; off hides the command but keeps the actions) and "Require unlock to approve" (off by default, Android 12+; *Deny* always works). |
| Security | Release builds accept HTTPS only. Notification actions are handled by a non-exported receiver. |
| Routing | Settings → "Where requests go": When I'm away · Every time · Updates only · Terminal. |
| Delivery | Settings → Delivery checks, live on every resume: notifications allowed, the Requests channel (high importance, lock screen), lock-screen notifications, battery optimization, "Pause app activity if unused" and push (token registered with the relay). Each row opens the exact system screen. The same checklist runs once right after pairing (skippable), and the inbox shows a dismissible banner while a critical check fails. |
| Design | `ui/theme` mirrors `design/tokens.json` (v4). Geist, Geist Mono and Geist Pixel are bundled (SIL OFL, see `third_party/geist`). Icons are generated from `design/mark.svg` by `scripts/generate_icons.py`. Fresh installs start in light mode. |
| Bold text | Android's "Bold text" adds ~300 to every font weight, both when Compose picks a font and on the variable font's `wght` axis. `ui/theme/FontWeights.kt` cancels both, so Geist renders as designed. Settings → Appearance → "Use system bold text" turns the system behaviour back on. |

Source layout (`app/src/main/java/io/github/nilsmorczinietz/nod/`):

```
data/model      protocol types (mirror of packages/protocol)
data/api        REST client (OkHttp) + WebSocket stream
data/store      DataStore session, Keystore cipher, Firebase config
data/update     GitHub release check
data/           NodRepository: single source of truth for items and pairing
push/           runtime Firebase init, FirebaseMessagingService, debug push receiver
notifications/  channels, notification builder, action receiver
ui/             theme (design tokens), components, inbox, detail, pairing, settings
```

## Push notifications (Firebase)

Push is optional; without it the app still works whenever it is open. The APK contains
**no** `google-services.json`: the relay hands the public Firebase client config to the app
during pairing, and the app initialises Firebase at runtime.

1. Create a free Firebase project and add an **Android app** with the package name
   `io.github.nilsmorczinietz.nod`. Download its `google-services.json` and a
   service-account key (Project settings → Service accounts).
2. From the repo root: `npm run setup:push -- <google-services.json> <service-account.json>`.
   This stores everything as relay **secrets**; nothing goes into `wrangler.jsonc`.
3. Pair the phone again: run `nod pair` and open the link (or scan the QR code with the camera). Nod
   asks before it replaces the current pairing. Settings → Delivery → Push then shows "Firebase
   connected". If registration fails, the row names the reason and offers *Retry*.

## Release signing

`assembleRelease` signs with the keystore from these environment variables (CI secrets or your shell):

| Variable | Meaning |
|---|---|
| `NOD_KEYSTORE_FILE` | Path to the `.jks` / `.keystore` file |
| `NOD_KEYSTORE_PASSWORD` | Keystore password |
| `NOD_KEY_ALIAS` | Key alias |
| `NOD_KEY_PASSWORD` | Key password |

If any is missing, the build falls back to the debug key and prints a Gradle warning. Use that for
local testing only; Android refuses to update an app signed with a different key.

## Updates

There is no Play Store build. On start (at most every 12 h) and via Settings → About →
"Check for updates", the app reads `api.github.com/repos/NilsMorczinietz/nod/releases/latest`.
If the tag (`vX.Y.Z`) is newer than the installed version, Settings → About shows the update
and the inbox shows a small, dismissible banner. *Download* opens the release's `.apk` in the
browser; Android then installs it as usual (no install permission in the app).

## Testing against a local relay

Debug builds allow plain HTTP to `localhost`:

```bash
cd relay && npx wrangler dev --port 8787        # local relay
adb reverse tcp:8787 tcp:8787                   # phone's localhost:8787 → your PC
nod pair                                        # relay URL in the app: http://localhost:8787
nod test permission                             # answer it in the app
```

To smoke-test the **minified release** build the same way, build it with
`./gradlew assembleRelease -Pnod.localTest=true`. Never publish such an APK.

### Notifications without Firebase

Debug and `-Pnod.localTest=true` builds enable a receiver that simulates an FCM data message.
Only `adb` can call it, because it is protected by the `DUMP` permission:

```bash
adb shell am broadcast -a io.github.nilsmorczinietz.nod.debug.PUSH -p io.github.nilsmorczinietz.nod \
  --es type item --es id <item-id> --es kind permission --es title "Run the tests" \
  --es body "npm test" --es project my-app --es expiresAt <epoch-ms>
```

Use a real pending item id from the relay; the notification's actions then answer it.
Add `--es agent Cursor` to see how another agent's requests are titled (default: "Claude Code").

### Screenshots

Debug builds also contain a wallpaper helper (`src/debug`, again `DUMP`-protected) for clean
screenshots on emulators whose shell cannot set wallpapers:

```bash
adb shell am broadcast -a io.github.nilsmorczinietz.nod.debug.WALLPAPER -p io.github.nilsmorczinietz.nod \
  --es top '#2D6E6E' --es bottom '#0B2F30'
```
