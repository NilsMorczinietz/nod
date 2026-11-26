# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- Notifications on Android show the agent that asks instead of the Nod icon: the Claude mark for
  Claude Code, and the marks of Cursor, Gemini, Windsurf, Copilot, Cline, Zed, OpenCode, Warp and
  Mistral. They are conversation notifications titled "<agent> · <project>", with Nod as a small
  badge. Scripts and unknown agents keep the Nod mark.
- Long messages no longer lose their end. A question Claude asks at the end of a long paragraph
  arrives whole (leading context is dropped instead), and the full message is one tap away in the
  app ("Full message") and on the desktop ("Show full message"). Text over a limit is shortened in
  the middle, never at the end. Completion summaries keep up to 4000 characters.

## [0.2.2] - 2026-11-22

### Changed

- Desktop app icon: white pixel mark on a transparent background (taskbar, tray, installer).

### Fixed

- The recent-activity overview can always be closed again. It could reopen right after closing and
  then ignore the collapse button.
- The island no longer gets stuck when Windows pauses its animations, and an overlay window with
  nothing to show no longer blocks clicks at the top of the screen.
- Fewer redundant window updates for the overlay.

## [0.2.1] - 2026-11-22

### Changed

- Windows installer in the Nod style: teal sidebar and header with the pixel mark, the logo as
  installer icon, clearer texts and "Start Nod" pre-checked on the finish page.
- Replies now also catch requests to answer and open offers without a question mark
  ("…write me „yes“", "if you want, I can …", "still open for you").

### Fixed

- Windows lists the real publisher instead of "github" under installed apps.
- The end-to-end test no longer overwrites the user's `nod` command (the shim directory honours
  `XDG_BIN_HOME`).

## [0.2.0] - 2026-11-22

### Added

- Replies to prose questions: when Claude ends a turn with a question in plain text, it arrives on
  your devices with Yes, No and a free-text reply. Your answer continues the session; typing in the
  terminal withdraws the question. The terminal is never blocked (background Stop hook with
  `asyncRewake`). Turn off with `notify.replies` in the config.
- Android: in-app updates. The app downloads the new APK, verifies it against the release
  checksums and opens the system installer.
- Windows: a log file for diagnosing problems
  (`%LOCALAPPDATA%\io.github.nilsmorczinietz.nod\logs\nod.log`).

### Fixed

- Windows: the main window could open behind other windows or stay blank after an update restart
  or autostart. It now comes to the front reliably, retries its first load and shows
  "Something went wrong · Reload" instead of a blank page.
- Windows: only one instance can run, also across an update restart and copies in other folders.
- Android: the update banner moved to the top of the inbox.
- Android: after an app update, an already used pairing link no longer asks to pair again.

## [0.1.1] - 2026-11-21

### Fixed

- Android: push notifications never arrived when the app was closed. Firebase Messaging needed a
  manifest flag, and pairing could drop the Firebase settings. Pair the phone again after updating.

### Added

- Android: Settings › Delivery checks everything Nod needs to reach you (notifications, lock
  screen, battery, unused-app restrictions, push), with a button to the right system screen.
- Android: a short delivery step after pairing, and an inbox banner while something important is
  missing.
- Android: opening a pairing link while already paired asks before switching relays.

## [0.1.0] - 2026-11-21

First release.

### Added

- Relay on Cloudflare Workers with a single SQLite Durable Object: pairing, device tokens,
  long-polling, hibernating WebSockets, FCM push, expiry and orphan clean-up.
- Routing modes: When I'm away, Every time, Updates only, Terminal. Presence from the Windows
  app keeps the phone quiet while you're at your PC.
- `nod` CLI: Claude Code hooks for permissions, questions, plans, completions and failures,
  pairing via QR code, `nod update` with checksum verification.
- One-command setup (`npm run setup`) and push setup (`npm run setup:push`).
- Other agents: `nod mcp` (MCP server with `request_approval`, `ask_human`, `notify`) and
  `nod ask` / `nod notify` for scripts and CI. Every request names its agent.
- Android app: inbox, decision ledger, lock-screen actions (Approve, Deny, Deny with note),
  answers from the notification, light and dark themes, update check.
- Windows app: a notch-style bar at the top of the screen with keyboard shortcuts, overview
  and main window, adjustable size, separate themes for app and notifications, auto-updates.
- Release pipeline with signed Android APK, Windows installer, checksums and build provenance.
