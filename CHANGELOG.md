# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
