# Nod Desktop (Windows)

A tray app that shows Claude Code requests in **the island**: a black,
notch-like surface at the top centre of your screen. Collapsed it is a slim
bar ("Claude Code · project", "Needs you"); when Claude needs you it opens
into a card where you approve or deny (also with **Ctrl+Shift+Y** /
**Ctrl+Shift+N** from anywhere), pick answers to questions, or review plans.
Clicking the bar shows recent sessions and where requests go
(When I'm away · Every time · Updates only · Terminal). A bottom-right position
is available in Settings. Built with **Tauri 2** (Rust) and **Svelte 5**.

**Size and themes.** The island is about 340 × 30 px collapsed and 400 px wide
expanded at 100 %. It follows the Windows display scale and the Windows
"Text size" setting, times Settings › Appearance › Notification size
(Small 85 % / Default / Large 115 %). The app theme (System / Light / Dark)
and the notification theme (Dark, like a notch / Light / Match app) are
separate settings; both are stored in the config file.

## How it connects

```
Claude Code ─hook─▶ nod CLI ─HTTPS─▶ relay (Cloudflare) ─WebSocket─▶ Nod Desktop
                                         ▲                               │
                                         └──── POST /v1/items/:id/resolve ┘
```

- **Rust owns the connection and the secret.** `src-tauri/src/relay/stream.rs`
  keeps a WebSocket to `GET /v1/stream` open (Authorization header, `ping`
  every 30 s, reconnect with exponential backoff + jitter). The relay uses the
  Durable Object hibernation API, so an idle connection costs nothing.
- The device token lives in the **Windows Credential Manager**
  (`keyring-core` + `windows-native-keyring-store`); non-secret settings live in
  `%APPDATA%\io.github.nilsmorczinietz.nod\config.json`.
- TLS uses the Windows certificate store (SChannel via `native-tls`), so no
  crypto library is bundled.
- If the device is revoked on the relay (`nod revoke`), the stream closes with
  code 4001 and the app returns to the pairing screen.
- The overlay is shown with `SWP_NOACTIVATE`, so the island never steals focus
  from your editor or terminal. The global shortcuts are registered only while
  a request is on screen, so they don't shadow other apps the rest of the time.
- **Presence:** every 15 s the app checks the last keyboard/mouse input
  (`GetLastInputInfo`) and reports `{"type":"presence","active":…}` on the
  stream (active = input within 3 min), on every change and after each
  reconnect. In mode "When I'm away" the relay keeps the phone quiet while you
  are at the PC and hands pending requests to the phone when you leave.
- **Motion:** the island's visible height is a `clip-path` driven by a
  critically damped spring from `design/tokens.json` (no overshoot, no
  width/height animation); content crossfades. The transparent window grows
  before the motion and shrinks after it, so the shadow is never clipped.

## Pairing

1. On the machine running Claude Code: `nod pair`.
2. In Nod Desktop, paste the printed `nod://pair?…` link (or relay URL + code).

Scripted alternative: `Nod.exe --pair "nod://pair?relay=…&code=…"`. If Nod is
already running, the link is forwarded to the running instance.

## Develop

Requires Node 22+, Rust stable and, on Windows, the WebView2 runtime (preinstalled on Windows 11).

```bash
npm install            # from the repo root (npm workspaces)
npm run dev -w @nod/desktop          # Vite only (UI in a browser, no Tauri APIs)
npx tauri dev          # inside desktop/, on Windows: full app with hot reload
npm run typecheck -w @nod/desktop    # svelte-check, strict
```

Design tokens are generated from `../design/tokens.json` into
`src/styles/tokens.css` (`npm run tokens`, also part of `dev`/`build`); spring
constants are imported from the same file. The island always uses the dark
palette plus the `color.island` block. Typography is Geist Sans and Geist Mono
(SIL Open Font License 1.1), bundled via `@fontsource-variable`. UI changes must
pass `../docs/design-anti-patterns.md`.

## Build

The version lives in `desktop/package.json` only (`tauri.conf.json` reads it via
`"version": "../package.json"`); keep `src-tauri/Cargo.toml` in sync (`scripts/version.mjs`
in the repo root bumps both).

**On Windows** (MSVC toolchain): `npx tauri build` produces `Nod.exe` and an NSIS installer.
With `TAURI_SIGNING_PRIVATE_KEY` set it also writes the signed updater artifacts.

**Cross-compile from Linux / WSL** (no admin rights needed, for local testing):

```bash
rustup target add x86_64-pc-windows-gnu
# llvm-mingw (ucrt, ubuntu-x86_64) from https://github.com/mstorsjo/llvm-mingw/releases
# extracted to ~/.local/opt/llvm-mingw (or set LLVM_MINGW)
npm run build:windows   # → dist-windows/Nod.exe + WebView2Loader.dll
```

The cross build produces `Nod.exe` plus `WebView2Loader.dll` (keep them together, no installer).
It needs the WebView2 runtime, which ships with Windows 10 (21H2+) and 11.

## Release

Releases are built by GitHub Actions (`.github/workflows/release.yml`, `tauri-apps/tauri-action`
on `windows-latest`): bump the version, push a tag, and the workflow uploads the NSIS installer,
its `.sig` and `latest.json` to the GitHub release.

Signing: the updater key pair was created with
`npx tauri signer generate --ci -w ~/.config/nod-release/tauri-updater.key`. The public key is in
`tauri.conf.json` (`plugins.updater.pubkey`); the private key never goes into the repo. Store it as
the GitHub secret `TAURI_SIGNING_PRIVATE_KEY` (empty password, so no password secret is needed).

## Updates

Release builds check
`https://github.com/NilsMorczinietz/nod/releases/latest/download/latest.json` on startup and
every 6 hours (dev builds don't). An available update only shows up quietly, in Settings › About
("Update x available · Install and restart") and as a tray item. Nothing installs without that
click; the signature is verified, the NSIS installer runs in passive mode and Nod restarts.
Settings › About also has "Check for updates" and a link to the release notes.

## Layout

```
src/
  main/        main window: pairing, inbox (pending + history), settings
  overlay/     the island: bar ⇄ card ⇄ overview, outcome and info states
  components/  RequestCard, Pill, Glyph, CodeBlock, Segmented, Toggle, Mark, …
  lib/         api bridge, reactive hub store, spring + motion, status, modes, clock, markdown, sound
  styles/      tokens.css (generated) + base.css
src-tauri/src/
  lib.rs       app setup, plugins, CLI args
  relay/       types (protocol mirror), HTTPS client, WebSocket stream
  presence.rs  "is the user at this PC" (last input within 3 min)
  shortcuts.rs Ctrl+Shift+Y / Ctrl+Shift+N, registered only while a request shows
  session.rs   pairing / unpairing
  store.rs     config file + credential store
  overlay.rs   anchoring (top centre / bottom right) and no-activate showing
  tray.rs      tray icon and menu
  commands.rs  commands callable from the webviews (see capabilities/)
```
