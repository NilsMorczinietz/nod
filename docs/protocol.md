# Relay protocol (v1)

The source of truth is [`packages/protocol/src/index.ts`](../packages/protocol/src/index.ts).
Kotlin (`android/`) and Rust (`desktop/`) mirror these types by hand. Update all three together.

All endpoints live under `/v1`, take and return JSON, and require
`Authorization: Bearer <token>` unless noted otherwise.

| Token  | Who has it                               |
| ------ | ---------------------------------------- |
| admin  | Machines running Claude Code (`nod` CLI) |
| device | Paired phones and desktops               |

## Endpoints

| Method & path                             | Auth        | Purpose                                                                        |
| ----------------------------------------- | ----------- | ------------------------------------------------------------------------------ |
| `GET /v1/health`                          | none        | Liveness + protocol version                                                    |
| `POST /v1/pairings`                       | admin       | Create a one-time pairing code → `CreatePairingResponse`                       |
| `POST /v1/devices`                        | none (code) | Redeem a code → `RegisterDeviceResponse` (token shown once)                    |
| `GET /v1/devices`                         | admin       | List devices                                                                   |
| `DELETE /v1/devices/:id`                  | admin       | Revoke a device (closes its sockets)                                           |
| `GET /v1/me`                              | device      | Own device + hub settings                                                      |
| `PUT /v1/me/push-token`                   | device      | `{ "pushToken": "<FCM token>" }`                                               |
| `DELETE /v1/me`                           | device      | Unpair yourself                                                                |
| `GET /v1/settings` / `PATCH /v1/settings` | any         | `{ "mode": "away" \| "always" \| "updates" \| "terminal", "paused": boolean }` |
| `POST /v1/items`                          | admin       | Create an item → `{ item, delivered }`                                         |
| `GET /v1/items?status=pending&limit=50`   | any         | Newest first                                                                   |
| `GET /v1/items/:id`                       | any         | One item                                                                       |
| `GET /v1/items/:id/wait?timeout=25`       | admin       | Long-poll until not pending (max 50 s)                                         |
| `POST /v1/items/:id/resolve`              | device      | `ResolveInput` → item, or `409 already_finished`                               |
| `POST /v1/items/:id/cancel`               | admin       | Withdraw a pending item                                                        |
| `GET /v1/stream`                          | device      | WebSocket, see below                                                           |

Errors look like `{ "error": "<code>", "message": "<text>" }`.

## Items

`kind` is one of:

| Kind         | Interactive | Created by hook                                                                            | Answer                                                       |
| ------------ | ----------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------ |
| `permission` | yes         | `PermissionRequest`                                                                        | `allow` / `deny` (+ `alwaysAllow`, `message`)                |
| `question`   | yes         | `PreToolUse` → `AskUserQuestion`, or `Stop` when a turn ends with a question (`allowText`) | `answer` with `answers: { [question]: string[] }`, or `deny` |
| `plan`       | yes         | `PreToolUse` → `ExitPlanMode`                                                              | `allow` (approve) / `deny` with feedback in `message`        |
| `completed`  | no          | `Stop`                                                                                     | –                                                            |
| `failed`     | no          | `StopFailure`                                                                              | –                                                            |
| `attention`  | no          | `Notification` (elicitation, agent needs input, optional idle)                             | –                                                            |

Status lifecycle: `pending → resolved | expired | cancelled`; informational items are `info`.

## Replies to prose questions

When a turn ends with a question in plain text, the CLI's background Stop hook (`asyncRewake`)
sends a `question` item whose single question has `allowText: true` and the options "Yes" / "No".
Clients show a text field; a free-text reply resolves with
`{ "decision": "answer", "answers": {}, "message": "<text>" }`. The hook then exits with code 2 and
Claude Code wakes the session with the reply. A new prompt in the terminal cancels the item.

## Routing modes

| Mode       | Requests (permission, question, plan)                        | Updates (done, failed, attention) |
| ---------- | ------------------------------------------------------------ | --------------------------------- |
| `away`     | Desktop always; phone only while no desktop reports activity | same                              |
| `always`   | Desktop and phone                                            | Desktop and phone                 |
| `updates`  | Not sent; the terminal asks                                  | Desktop and phone                 |
| `terminal` | Not sent; the terminal asks                                  | Not sent                          |

`PATCH { "paused": true }` switches to `terminal`; `{ "paused": false }` restores the previous mode.
When the last active desktop reports `idle` in `away` mode, pending requests are pushed to the phone.

## WebSocket stream

`GET /v1/stream` with the device token in the `Authorization` header.
Clients send the text `ping` every ~30 s; the relay answers `pong` without
waking up. Desktop clients additionally send `{ "type": "presence", "active": true|false }`
whenever the user becomes active or has been idle for ~3 minutes. Messages from the relay (`StreamMessage`):

```jsonc
{ "type": "hello", "protocol": 1, "pending": [/* Item */], "settings": { "mode": "away", "paused": false } }
{ "type": "item", "item": { /* new Item */ } }
{ "type": "update", "item": { /* Item changed status */ } }
{ "type": "settings", "settings": { "mode": "terminal", "paused": true } }
```

## FCM data payload (`PushPayload`)

All values are strings.

```jsonc
{ "type": "item", "id": "…", "kind": "permission", "title": "Run tests", "body": "npm test",
  "project": "acme", "agent": "Claude Code", "expiresAt": "1795282236563", "options": "[\"SQLite\",\"Postgres\"]", "canAlwaysAllow": "1" }
{ "type": "dismiss", "id": "…" }
```

Interactive items are sent with `HIGH` priority and a TTL equal to their remaining
lifetime; `dismiss` and informational items use `NORMAL`.

## Pairing link

`nod://pair?relay=<https origin>&code=<XXXX-XXXX>` is encoded in the QR code
printed by `nod pair`.
