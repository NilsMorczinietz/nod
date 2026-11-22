import { parseArgs } from 'node:util'
import { askCommand, notifyCommand } from './commands/ask.ts'
import { install, uninstall } from './commands/install.ts'
import { devices, pair, revoke, setMode, setPaused, status, test } from './commands/manage.ts'
import { setup } from './commands/setup.ts'
import { runHook, runReplyHook } from './hook/run.ts'
import { runMcpServer } from './mcp/server.ts'
import { update } from './commands/update.ts'
import { fail, style } from './log.ts'
import { VERSION } from './version.ts'

const HELP = `${style.bold('nod')} – answer Claude Code from your phone and desktop

${style.bold('Usage')}
  nod setup [--relay <url>] [--token <admin-token>]   connect to your relay
  nod install | uninstall                             (un)register Claude Code hooks
  nod pair                                            pair a phone or desktop (QR / code)
  nod devices | revoke <id>                           list or remove paired devices
  nod mode [away|always|updates|terminal]             where requests go (default: away)
  nod pause | resume                                  shortcut for mode terminal / previous mode
  nod status                                          show configuration and relay state
  nod test [permission|question|plan|completed]       send a sample request
  nod ask "<question>" [--approve | --options a,b] [--multi] [--json]
                                                      ask from scripts (exit 0 yes · 1 no · 2 no answer)
  nod notify "<title>" ["<message>"] [--failed]       one-way notification
  nod mcp                                             MCP server for Cursor, Claude Desktop, Codex, …
  nod update [--check]                                install the latest release (checksum-verified)
  nod version                                         print the installed version
  nod hook                                            (internal) called by Claude Code
`

const main = async (): Promise<void> => {
  const { positionals, values } = parseArgs({
    allowPositionals: true,
    options: {
      relay: { type: 'string' },
      token: { type: 'string' },
      help: { type: 'boolean', short: 'h' },
      version: { type: 'boolean', short: 'v' },
      check: { type: 'boolean' },
      approve: { type: 'boolean' },
      options: { type: 'string' },
      multi: { type: 'boolean' },
      details: { type: 'string' },
      agent: { type: 'string' },
      timeout: { type: 'string' },
      json: { type: 'boolean' },
      failed: { type: 'boolean' },
      reply: { type: 'boolean' },
    },
  })
  const [command, arg, arg2] = positionals

  if (values.version) return console.log(VERSION)
  switch (values.help ? 'help' : command) {
    case 'hook':
      return values.reply ? runReplyHook() : runHook()
    case 'setup':
      return setup({ relay: values.relay, token: values.token })
    case 'install':
      return install()
    case 'uninstall':
      return uninstall()
    case 'pair':
      return pair()
    case 'devices':
      return devices()
    case 'revoke':
      return revoke(arg)
    case 'pause':
      return setPaused(true)
    case 'resume':
      return setPaused(false)
    case 'mode':
      return setMode(arg)
    case 'mcp':
      return runMcpServer()
    case 'ask':
      return askCommand({ ...values, text: arg })
    case 'notify':
      return notifyCommand(arg, arg2, values)
    case 'update':
      return update({ check: values.check })
    case 'version':
      return console.log(VERSION)
    case 'status':
      return status()
    case 'test':
      return test(arg)
    case undefined:
    case 'help':
      return console.log(HELP)
    default:
      throw new Error(`Unknown command "${command}". Run \`nod help\`.`)
  }
}

main().catch((error: unknown) => {
  fail(error instanceof Error ? error.message : String(error))
  process.exitCode = 1
})
