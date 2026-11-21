# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- `nod mcp`: MCP server with `request_approval`, `ask_human` and `notify`, so Cursor, Claude
  Desktop, Codex and other MCP clients can ask through Nod.
- `nod ask` and `nod notify` for scripts and CI (exit code 0 approved, 1 denied, 2 no answer).
- Every request names its `agent`; the apps show it.
- One-command setup (`npm run setup`, `npm run setup:push`), `nod update`, release pipeline.

## [0.1.0] - 2026-11-21

First release.

### Added

- Relay on Cloudflare Workers with a single SQLite Durable Object: pairing, device tokens,
  long-polling, hibernating WebSockets, FCM push, expiry and orphan clean-up.
- Routing modes: When I'm away, Every time, Updates only, Terminal. Presence from the Windows
  app keeps the phone quiet while you're at your PC.
- `nod` CLI: one-command setup, Claude Code hooks for permissions, questions, plans, completions
  and failures, pairing via QR code, `nod update` with checksum verification.
- Android app: inbox, decision ledger, lock-screen actions (Approve, Deny, Deny with note),
  answers to questions from the notification, light and dark themes.
- Windows app: a notch-style island at the top of the screen with keyboard shortcuts, an
  overview and the main window; adjustable size and separate themes for app and notifications.
