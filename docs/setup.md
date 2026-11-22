# Setup guide

About 10 minutes, once. Everything runs on free tiers.

**You need:**

- [Node.js](https://nodejs.org) 22 or newer (`node --version`)
- [Claude Code](https://code.claude.com) on the same machine
- A free [Cloudflare account](https://dash.cloudflare.com/sign-up)
- An Android phone and/or a Windows PC for the apps

## 1. Relay, CLI and hooks

```bash
git clone https://github.com/NilsMorczinietz/nod.git
cd nod
npm install
npm run setup
```

Choose **1 (first machine)**. The script then:

1. logs you in to Cloudflare (opens the browser once);
2. deploys the relay to your account and shows its URL, e.g. `https://nod-relay.you.workers.dev`;
3. creates an **admin token**. Save it in your password manager, because you need it for other machines;
4. installs the `nod` command and registers the hooks in `~/.claude/settings.json` (it writes a
   backup first).

Restart any running Claude Code sessions afterwards.

> If the script says `~/.local/bin` is not on your `PATH`, add it (e.g. in `~/.profile`), or run
> the CLI as `node ~/.local/share/nod/nod.mjs`.

## 2. Install the apps

Download them from the [latest release](https://github.com/NilsMorczinietz/nod/releases/latest):

| Device  | File                          | Notes                                                                                                       |
| ------- | ----------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Android | `Nod-<version>.apk`           | Open the file on the phone and allow "Install unknown apps" for your browser once.                          |
| Windows | `Nod_<version>_x64-setup.exe` | Windows may show "Windows protected your PC" because the app isn't code-signed: **More info › Run anyway**. |

## 3. Pair your devices

```bash
nod pair
```

- **Android:** open Nod › **Scan QR code**.
- **Windows:** open Nod and paste the `nod://pair?…` link shown under the QR code.

Each code works once and expires after 10 minutes; run `nod pair` again for the next device.

Try it:

```bash
nod test permission    # approve or deny it on a device
nod test question
nod test completed
```

## 4. Push notifications on Android (optional, recommended)

Without push, the Android app only receives requests while it is open. Push uses Firebase Cloud
Messaging, which is free.

1. Open the [Firebase console](https://console.firebase.google.com) › **Add project** (Google
   Analytics is not needed).
2. **Add app › Android**, package name **`io.github.nilsmorczinietz.nod`**, then **Register
   app** and download **`google-services.json`**. Skip the rest of the wizard.
3. **Project settings › Service accounts › Generate new private key**, which downloads a JSON
   file.
4. Back in the `nod` folder:

   ```bash
   npm run setup:push -- path/to/google-services.json path/to/service-account.json
   ```

5. Pair the phone again (`nod pair`), then delete both downloaded files.

Check: `nod devices` shows `push ✓` next to your phone.

## More machines

On every other machine (or WSL distro) where you use Claude Code:

```bash
git clone https://github.com/NilsMorczinietz/nod.git && cd nod && npm install
npm run setup          # choose 2, then enter the relay URL and the admin token
```

Native Windows Claude Code works the same way in PowerShell. The `nod` command lands in
`%LOCALAPPDATA%\nod\bin`; add that folder to your `PATH` if the script asks you to.

## Other agents and scripts

Cursor, Claude Desktop, Codex and others can use Nod through its MCP server, and scripts through
`nod ask` / `nod notify`. See [integrations.md](integrations.md).

## Everyday use

| Command                                    | What it does                                                   |
| ------------------------------------------ | -------------------------------------------------------------- |
| `nod status`                               | Relay, hooks, mode and paired devices at a glance              |
| `nod mode away\|always\|updates\|terminal` | Where requests go (see below)                                  |
| `nod pause` / `nod resume`                 | Quickly switch everything back to the terminal, and back again |
| `nod devices` / `nod revoke <id>`          | List or remove paired devices                                  |
| `NOD_DISABLED=1 claude`                    | Run one Claude Code session without Nod                        |

**Modes** (also selectable in both apps):

| Mode                            | Requests                                                  | "Done" messages |
| ------------------------------- | --------------------------------------------------------- | --------------- |
| `away`: When I'm away (default) | Windows app always; phone only while you're not at the PC | same            |
| `always`: Every time            | Windows app and phone                                     | same            |
| `updates`: Updates only         | Stay in the terminal                                      | Windows + phone |
| `terminal`: Terminal            | Stay in the terminal                                      | none            |

**Settings** live in `~/.config/nod/config.json` (Windows: `%APPDATA%\nod\config.json`):

```jsonc
{
  "waitSeconds": 600, // how long a request waits for an answer before the terminal asks
  "minTaskSeconds": 20, // only send "done" for turns that took at least this long
  "notify": { "completed": true, "failed": true, "idle": false, "replies": true },
}
```

`replies`: when Claude ends a turn with a question ("Shall I …?"), it is sent to your devices and
your answer (Yes, No or your own text) continues the session. Typing in the terminal withdraws it.

Run `nod install` after changing `waitSeconds`, because it updates the hook timeouts.

## Updating

| Part        | How                                                                            |
| ----------- | ------------------------------------------------------------------------------ |
| CLI         | `nod update` (downloads the latest release and verifies its checksum)          |
| Android app | Settings › About shows when an update is available; tap **Download**           |
| Windows app | Settings › About › **Install and restart** when an update is available         |
| Relay       | In the `nod` folder: `git pull && npm install && npm run deploy -w @nod/relay` |

All releases are on the [releases page](https://github.com/NilsMorczinietz/nod/releases), with
notes in [CHANGELOG.md](../CHANGELOG.md).

## Uninstall

```bash
nod uninstall                         # removes the hooks from ~/.claude/settings.json
rm -rf ~/.config/nod ~/.local/share/nod ~/.local/state/nod ~/.local/bin/nod
cd relay && npx wrangler delete       # deletes the relay and all its data
```

Then uninstall the apps as usual.

## Troubleshooting

| Problem                                                 | Fix                                                                                                                                          |
| ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Nothing happens when Claude Code asks                   | `nod status`: are the hooks installed and the mode not `terminal`? Restart Claude Code after `nod install`.                                  |
| The phone gets nothing while the app is closed          | `nod devices` must show `push ✓`, otherwise do [step 4](#4-push-notifications-on-android-optional-recommended). Allow notifications for Nod. |
| The phone stays silent, the Windows app gets everything | Expected in mode "When I'm away" while you use the PC. `nod mode always` sends to both.                                                      |
| "Pairing code is invalid or expired"                    | Codes are single-use and expire after 10 minutes. Run `nod pair` again.                                                                      |
| Hook errors                                             | See `~/.local/state/nod/hook.log` (Windows: `%LOCALAPPDATA%\nod\state\hook.log`).                                                            |
| Relay errors                                            | `cd relay && npx wrangler tail` shows live logs.                                                                                             |

Still stuck? [Open an issue](https://github.com/NilsMorczinietz/nod/issues/new/choose) with
`nod version`, the app version and the relevant log lines (remove any tokens).
