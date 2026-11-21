# Using Nod with other agents and tools

Nod was built for Claude Code, but the relay doesn't care who asks. Every request carries an
`agent` name ("Claude Code", "Cursor", "Script", …), and the apps show it. There are three ways in,
from strongest to most flexible:

| Way                               | For                                                    | Enforced?                                  |
| --------------------------------- | ------------------------------------------------------ | ------------------------------------------ |
| **Hooks**                         | Claude Code (installed by `npm run setup`)             | Yes: the agent waits for your answer       |
| **MCP server** (`nod mcp`)        | Cursor, Claude Desktop, Codex, Gemini CLI, Windsurf, … | Cooperative: the agent decides when to ask |
| **CLI** (`nod ask`, `nod notify`) | Shell scripts, CI jobs, cron, anything with a shell    | Yes, if your script checks the exit code   |

## MCP server

`nod mcp` is a stdio [Model Context Protocol](https://modelcontextprotocol.io) server with three
tools:

| Tool               | What it does                                                           |
| ------------------ | ---------------------------------------------------------------------- |
| `request_approval` | Approve / Deny on your devices. The agent waits for the answer.        |
| `ask_human`        | A question with 2–8 options (single or multi-select). The agent waits. |
| `notify`           | A one-way message ("done", "failed" or "info").                        |

The server also tells the agent to ask before destructive, irreversible or costly actions, and to
never proceed when you deny or don't answer.

Add it to your client's MCP configuration. Most clients use this shape (Cursor:
`~/.cursor/mcp.json`, Claude Desktop: `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "nod": { "command": "nod", "args": ["mcp"] }
  }
}
```

If `nod` is not on the client's `PATH`, use `"command": "node"` with
`"args": ["/home/you/.local/share/nod/nod.mjs", "mcp"]`. The agent's name is taken from the MCP
client; set `"env": { "NOD_AGENT": "My agent" }` to override it.

Codex CLI (`~/.codex/config.toml`):

```toml
[mcp_servers.nod]
command = "nod"
args = ["mcp"]
```

> MCP is cooperative: the model decides when to call the tools, so it can't guarantee that every
> risky action is gated. For hard guarantees, use the agent's own approval settings (or hooks,
> where the agent offers them) and let Nod handle the "ask me" part.

## Scripts and CI: `nod ask` and `nod notify`

```bash
# Approve / deny: exit code 0 = approved, 1 = denied, 2 = no answer
nod ask --approve "Deploy api-gateway to production?" --details "$(git log -1 --oneline)" \
  && ./deploy.sh

# A choice: the chosen option is printed
env=$(nod ask "Which environment?" --options staging,production) || exit 1

# Fire and forget
nod notify "Nightly backup finished" "42 GB in 3 min"
nod notify "Nightly backup failed" "Disk full" --failed
```

| Option                | Meaning                                                    |
| --------------------- | ---------------------------------------------------------- |
| `--approve`           | Approve / Deny instead of options                          |
| `--options a,b,c`     | Answer options (`--multi` allows several, one per line)    |
| `--details "…"`       | Extra context shown under the question                     |
| `--timeout <seconds>` | How long to wait (default: `waitSeconds` from the config)  |
| `--agent "<name>"`    | Name shown in the apps (default "Script", or `$NOD_AGENT`) |
| `--json`              | Print the full outcome as JSON                             |

## Hooks for other agents

Agents that offer blocking hooks (a script that runs before a tool call and can allow or deny it)
can get the same enforced flow as Claude Code. The mapping lives in `cli/src/hook/`: add an adapter
that turns the agent's hook payload into a Nod item and the answer back into the agent's expected
output. Pull requests are welcome, see [CONTRIBUTING.md](../CONTRIBUTING.md).

## Raw HTTP

Anything that speaks HTTP can use the relay directly with the admin token: `POST /v1/items`, then
`GET /v1/items/:id/wait` until the item is no longer pending. See [protocol.md](protocol.md).
