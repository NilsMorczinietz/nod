#!/usr/bin/env bash
# End-to-end test: starts a throw-away local relay, then drives the real CLI
# hook through pairing, allow/deny, questions, cancel and pause.
# Usage: npm run e2e
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
PORT=${PORT:-8799}
R="http://127.0.0.1:$PORT"
ADM=nod_adm_e2e_test_token
TMP=$(mktemp -d)
# Isolate every location the CLI writes to, so the test never touches a real installation.
export XDG_CONFIG_HOME=$TMP/cfg XDG_STATE_HOME=$TMP/state XDG_DATA_HOME=$TMP/data XDG_BIN_HOME=$TMP/bin CLAUDE_CONFIG_DIR=$TMP/claude
NOD="node $ROOT/cli/dist/nod.mjs"

# wrangler spawns workerd children: run it in its own process group and kill the whole group.
cleanup() { [[ -n "${WRANGLER_PID:-}" ]] && kill -- "-$WRANGLER_PID" 2>/dev/null; rm -rf "$TMP"; }
trap cleanup EXIT

pass() { printf '\033[32m✓\033[0m %s\n' "$1"; }
die() { printf '\033[31m✗\033[0m %s\n' "$1"; exit 1; }
jq_() { node -e "const d=JSON.parse(require('fs').readFileSync(0,'utf8')); console.log(eval('d'+process.argv[1]))" "$1"; }

curl -sf -m 2 "$R/v1/health" >/dev/null && die "port $PORT is already in use (set PORT=...)"
npm run build -w @nod/cli >/dev/null
echo "ADMIN_TOKEN=$ADM" > "$TMP/.dev.vars"
cd "$ROOT/relay"
setsid npx wrangler dev --port "$PORT" --ip 127.0.0.1 --persist-to "$TMP/wrangler" \
  --env-file "$TMP/.dev.vars" >"$TMP/wrangler.log" 2>&1 &
WRANGLER_PID=$!
cd "$ROOT"
for _ in $(seq 1 60); do curl -sf -m 2 "$R/v1/health" >/dev/null && break; sleep 1; done
curl -sf -m 2 "$R/v1/health" >/dev/null || { cat "$TMP/wrangler.log"; die "relay did not start"; }
pass "relay up"

$NOD setup --relay "$R" --token "$ADM" >/dev/null && $NOD install >/dev/null && pass "setup + install"
grep -q 'nod.mjs\\" hook' "$CLAUDE_CONFIG_DIR/settings.json" || die "hooks missing in settings.json"

hook() { echo "$1" | timeout "${2:-20}" $NOD hook; }
PERM='{"session_id":"s","cwd":"/w/acme","hook_event_name":"PermissionRequest","tool_name":"Bash","tool_input":{"command":"ls"},"permission_suggestions":[{"type":"addRules","behavior":"allow","rules":[]}]}'

[[ -z "$(hook "$PERM" 5)" ]] && pass "no devices → terminal fallback"

CODE=$(curl -s -X POST "$R/v1/pairings" -H "authorization: Bearer $ADM" | jq_ .code)
TOK=$(curl -s -X POST "$R/v1/devices" -d "{\"code\":\"$CODE\",\"name\":\"E2E\",\"kind\":\"android\"}" | jq_ .token)
[[ "$TOK" == nod_dev_* ]] && pass "pairing"
[[ $(curl -s -o /dev/null -w '%{http_code}' -X POST "$R/v1/devices" -d "{\"code\":\"$CODE\",\"name\":\"x\",\"kind\":\"android\"}") == 403 ]] && pass "code is single-use"

answer() { # $1 = resolve body; waits until the hook's request shows up (up to 20 s)
  local id=""
  for _ in $(seq 1 40); do
    sleep 0.5
    id=$(curl -s "$R/v1/items?status=pending" -H "authorization: Bearer $TOK" | jq_ '.items[0]?.id ?? ""')
    [[ -n "$id" ]] && break
  done
  curl -s -o /dev/null -X POST "$R/v1/items/$id/resolve" -H "authorization: Bearer $TOK" -d "$1"
}

answer '{"decision":"allow","alwaysAllow":true}' & AP=$!; OUT=$(hook "$PERM"); wait $AP
[[ $(echo "$OUT" | jq_ .hookSpecificOutput.decision.behavior) == allow ]] && pass "allow (+ always rules)"

answer '{"decision":"deny","message":"nope"}' & AP=$!; OUT=$(hook "$PERM"); wait $AP
[[ $(echo "$OUT" | jq_ .hookSpecificOutput.decision.message) == nope ]] && pass "deny with message"

Q='{"session_id":"s","cwd":"/w","hook_event_name":"PreToolUse","tool_name":"AskUserQuestion","tool_input":{"questions":[{"question":"Q?","header":"Q","options":[{"label":"A"},{"label":"B"}]}]}}'
answer '{"decision":"answer","answers":{"Q?":["B"]}}' & AP=$!; OUT=$(hook "$Q"); wait $AP
[[ $(echo "$OUT" | jq_ '.hookSpecificOutput.updatedInput.answers["Q?"]') == B ]] && pass "question answered"

echo "$PERM" | $NOD hook & HP=$!; sleep 1.5; kill -TERM $HP; wait $HP || true
[[ $(curl -s "$R/v1/items?limit=1" -H "authorization: Bearer $TOK" | jq_ '.items[0].status') == cancelled ]] && pass "SIGTERM cancels the item"

$NOD pause >/dev/null
START=$(date +%s); [[ -z "$(hook "$PERM" 5)" && $(( $(date +%s) - START )) -lt 3 ]] && pass "paused → immediate fallback"
$NOD resume >/dev/null

# WebSocket stream: hello carries the settings; presence reports are accepted.
HELLO=$(R="$R" TOK="$TOK" node -e '
  const ws = new WebSocket(process.env.R.replace("http", "ws") + "/v1/stream", { headers: { authorization: "Bearer " + process.env.TOK } })
  ws.onmessage = (e) => { ws.send(JSON.stringify({ type: "presence", active: true })); ws.send("ping"); console.log(JSON.parse(e.data).settings.mode); ws.close(); setTimeout(() => process.exit(0), 200) }
  ws.onerror = () => { console.log("error"); process.exit(1) }
  setTimeout(() => process.exit(1), 5000)')
[[ "$HELLO" == away ]] && pass "websocket hello + presence (default mode away)"

$NOD mode updates >/dev/null
[[ -z "$(hook "$PERM" 5)" ]] && pass "mode updates → requests stay in the terminal"
echo '{"session_id":"s8","cwd":"/w","hook_event_name":"Stop","last_assistant_message":"upd"}' | $NOD hook
[[ $(curl -s "$R/v1/items?limit=1" -H "authorization: Bearer $TOK" | jq_ '.items[0].body') == upd ]] && pass "mode updates → completions still delivered"
$NOD mode away >/dev/null

echo '{"session_id":"s9","cwd":"/w","hook_event_name":"Stop","last_assistant_message":"**Done**"}' | $NOD hook
[[ $(curl -s "$R/v1/items?limit=1" -H "authorization: Bearer $TOK" | jq_ '.items[0].body') == Done ]] && pass "completed notification"

# Replies to prose questions (Stop hook with asyncRewake)
grep -q '"asyncRewake": true' "$CLAUDE_CONFIG_DIR/settings.json" && pass "reply hook registered with asyncRewake"
ASK='{"session_id":"r1","cwd":"/w","hook_event_name":"Stop","stop_hook_active":false,"last_assistant_message":"Fertig.\n\nSoll ich den Release anlegen?"}'
echo '{"session_id":"r1","cwd":"/w","hook_event_name":"UserPromptSubmit","prompt":"x"}' | $NOD hook
BEFORE=$(curl -s "$R/v1/items?limit=1" -H "authorization: Bearer $TOK" | jq_ '.items[0].id')
echo "$ASK" | $NOD hook
[[ $(curl -s "$R/v1/items?limit=1" -H "authorization: Bearer $TOK" | jq_ '.items[0].id') == "$BEFORE" ]] && pass "no 'completed' push for a turn that ends with a question"

answer '{"decision":"answer","answers":{},"message":"ja, aber erst morgen"}' & AP=$!
set +e; ERR=$(echo "$ASK" | timeout 30 $NOD hook --reply 2>&1 >/dev/null); CODE=$?; set -e; wait $AP
[[ $CODE == 2 && "$ERR" == *"ja, aber erst morgen"* ]] && pass "reply wakes Claude (exit 2 + reply on stderr)"

echo "$ASK" | $NOD hook --reply 2>"$TMP/late.err" & RP=$!
for _ in $(seq 1 40); do sleep 0.5; [[ -n $(curl -s "$R/v1/items?status=pending" -H "authorization: Bearer $TOK" | jq_ '.items[0]?.id ?? ""') ]] && break; done
echo '{"session_id":"r1","cwd":"/w","hook_event_name":"UserPromptSubmit","prompt":"answered in the terminal"}' | $NOD hook
set +e; wait $RP; CODE=$?; set -e
[[ $CODE == 0 && ! -s "$TMP/late.err" && $(curl -s "$R/v1/items?limit=1" -H "authorization: Bearer $TOK" | jq_ '.items[0].status') == cancelled ]] \
  && pass "terminal answer withdraws the question; no late wake-up"

echo; pass "all e2e checks passed"
