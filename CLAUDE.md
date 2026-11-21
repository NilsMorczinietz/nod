# Nod – notes for coding agents

Monorepo: `relay/` (Cloudflare Worker), `cli/` (Node hook bridge), `android/` (Kotlin/Compose),
`desktop/` (Tauri 2 + Svelte 5), `packages/protocol/` (wire types). All code and docs in English.

## Rules

- The wire protocol lives in `packages/protocol/src/index.ts`. Any change must be mirrored in
  `android/.../data/model` (Kotlin) and `desktop/src-tauri/src/relay/types.rs` (Rust), plus `docs/protocol.md`.
- Design tokens live in `design/tokens.json`; mirror changes in Android `ui/theme` and desktop styles.
- Style precedence: `design/tokens.json` (v4, Pushary reference) > `docs/design.md` > `docs/design-anti-patterns.md`.
- Every UI change must pass `docs/design-anti-patterns.md` where the reference is silent (no glow/glass/gradients,
  no bounce, mono only for code/times, one primary action, sentence case).
- Hooks must fail open: on any error exit 0 without output so Claude Code falls back to the terminal.
- No secrets in the repo (`.dev.vars`, service-account JSON, keystores are git-ignored).

## Commands

- `npm install` · `npm run typecheck` · `npm test` · `npm run e2e` · `npm run lint`
- Relay locally: `cp relay/.dev.vars.example relay/.dev.vars && npm run dev -w @nod/relay`
- CLI: `npm run build -w @nod/cli` → `node cli/dist/nod.mjs …`
- Android: `cd android && ./gradlew assembleDebug`
- Desktop: see `desktop/README.md`
