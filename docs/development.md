# Development

## Repository layout

```
nod/
├── relay/              Cloudflare Worker + Hub Durable Object (TypeScript)
├── cli/                `nod` CLI and Claude Code hook bridge (TypeScript → one file: dist/nod.mjs)
├── android/            Android app (Kotlin, Jetpack Compose)          → android/README.md
├── desktop/            Windows app (Tauri 2 + Svelte 5)               → desktop/README.md
├── packages/protocol/  Wire types: the contract between all parts
├── design/             Design tokens, logo and its generator
├── docs/               Architecture, protocol, design, setup
├── scripts/            setup, version bump, end-to-end test
└── .github/            CI, release and relay-deploy workflows
```

## Prerequisites

| For             | You need                                                                                      |
| --------------- | --------------------------------------------------------------------------------------------- |
| Relay, CLI      | Node.js 22+                                                                                   |
| Android         | JDK 17, Android SDK with platform `android-37.2`                                              |
| Windows desktop | Rust stable; on Windows the MSVC build tools, or cross-compile from WSL (`desktop/README.md`) |

```bash
npm install
```

## Everyday commands

| Command                                 | What it does                                                                                |
| --------------------------------------- | ------------------------------------------------------------------------------------------- |
| `npm run typecheck`                     | All TypeScript workspaces (relay, CLI, protocol, desktop UI)                                |
| `npm test`                              | Unit tests (CLI hook mapping)                                                               |
| `npm run e2e`                           | Starts a throw-away local relay and drives the real hook through the full request lifecycle |
| `npm run lint` · `npm run format`       | oxlint · Prettier                                                                           |
| `cd android && ./gradlew assembleDebug` | Android debug APK                                                                           |
| `npm run build:windows -w @nod/desktop` | Windows build from WSL (see desktop/README.md)                                              |

## Local relay

```bash
cp relay/.dev.vars.example relay/.dev.vars      # ADMIN_TOKEN=nod_adm_local_development_only
npm run dev -w @nod/relay                       # http://127.0.0.1:8787

# Use a separate CLI config so your real setup stays untouched:
export XDG_CONFIG_HOME=/tmp/nod-dev/config XDG_STATE_HOME=/tmp/nod-dev/state XDG_DATA_HOME=/tmp/nod-dev/data
npm run build -w @nod/cli
node cli/dist/nod.mjs setup --relay http://127.0.0.1:8787 --token nod_adm_local_development_only
node cli/dist/nod.mjs pair
node cli/dist/nod.mjs test permission
```

Plain `http` is accepted for `localhost` only. To reach the local relay from a phone, use
`adb reverse tcp:8787 tcp:8787` (details in `android/README.md`).

## Rules

- **Protocol:** `packages/protocol/src/index.ts` is the source of truth. Mirror every change in
  `android/…/data/model`, `desktop/src-tauri/src/relay/types.rs` and `docs/protocol.md`.
- **Hooks fail open:** on any error the hook exits 0 without output, so Claude Code falls back to
  the terminal. Never let a hook block or crash Claude Code.
- **Design:** `design/tokens.json` › `docs/design.md` › `docs/design-anti-patterns.md`, in that
  order of precedence.
- **No secrets in the repo** (`.dev.vars`, service-account JSON and keystores are git-ignored).

## Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request:

| Job                   | Runs                                                       | When                                       |
| --------------------- | ---------------------------------------------------------- | ------------------------------------------ |
| Relay, CLI & protocol | lint, format check, typecheck, unit tests, end-to-end test | always                                     |
| Android               | `assembleDebug` and unit tests                             | `android/` or `design/` changed            |
| Windows desktop       | `svelte-check` and a release build (MSVC)                  | `desktop/`, `packages/`, `design/` changed |
| CI passed             | one summary check for branch protection                    | always                                     |

`.github/workflows/codeql.yml` scans the TypeScript and the workflows weekly and on every PR.

Dependabot keeps npm, Gradle, Cargo and Actions dependencies current (weekly).

## GitHub repository settings (maintainer)

- **About:** description "Answer Claude Code from your phone and desktop. Self-hosted, free.",
  topics `claude-code`, `mcp`, `human-in-the-loop`, `cloudflare-workers`, `android`, `tauri`.
- **Branch protection for `main`:** require the status check **CI passed** (it covers every CI
  job, including skipped ones) and linear history.
- **Security:** enable private vulnerability reporting, Dependabot alerts and security updates.
  CodeQL runs from `.github/workflows/codeql.yml`.
- **Social preview:** upload `docs/images/social-preview.png` (Settings › General › Social preview);
  `scripts/render-mockups.sh` regenerates it together with the README images.
- **Actions:** all third-party actions are pinned to commit SHAs; Dependabot updates the pins.

## Releasing

### One-time setup (maintainer)

Add these **repository secrets** (Settings › Secrets and variables › Actions):

| Secret                                                                   | How to create it                                                                                                                                                                                        |
| ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ANDROID_KEYSTORE_BASE64`                                                | `keytool -genkeypair -v -keystore nod-release.jks -alias nod -keyalg RSA -keysize 4096 -validity 10000`, then `base64 -w0 nod-release.jks`. Keep the `.jks` safe: losing it means users must reinstall. |
| `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | The values you chose above (alias `nod`).                                                                                                                                                               |
| `TAURI_SIGNING_PRIVATE_KEY` (+ `_PASSWORD` if set)                       | `npx tauri signer generate -w ~/.config/nod-release/tauri-updater.key`. The public key goes into `desktop/src-tauri/tauri.conf.json` › `plugins.updater.pubkey`.                                        |

Optional, for the "Deploy relay" workflow: `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`.

### Each release

```bash
npm run version:set -- 0.2.0        # bumps every package, Cargo.toml and the Android version
# move the "Unreleased" notes in CHANGELOG.md under "## [0.2.0] - YYYY-MM-DD"
git commit -am "Release v0.2.0"
git tag v0.2.0 && git push --follow-tags
```

The tag triggers `.github/workflows/release.yml`:

1. checks that the tag matches the code version and creates a draft release with the notes from
   `CHANGELOG.md`;
2. builds and attaches `nod.mjs` (CLI), `Nod-<version>.apk` (signed) and the Windows installer
   with its updater signature and `latest.json`;
3. writes `SHA256SUMS`, signs build provenance for every file and publishes the release.

Verify a download: `gh attestation verify Nod-0.2.0.apk --repo NilsMorczinietz/nod`.

### How clients update

| Client  | Mechanism                                                                                                                      |
| ------- | ------------------------------------------------------------------------------------------------------------------------------ |
| CLI     | `nod update` reads the latest GitHub release, verifies `nod.mjs` against `SHA256SUMS`, replaces itself and refreshes the hooks |
| Windows | Tauri updater reads `releases/latest/download/latest.json` and verifies the signature before installing                        |
| Android | Checks the GitHub releases API (at most every 12 h) and links to the new APK; Android verifies that the signing key matches    |
| Relay   | Self-hosters run `git pull && npm install && npm run deploy -w @nod/relay` (or the Deploy relay workflow)                      |

Forks can point the CLI at their own releases with `NOD_REPO=owner/name`.
