# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
