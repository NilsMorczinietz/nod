/**
 * `nod ask` and `nod notify`: use Nod from scripts, CI jobs or any agent with a shell.
 *
 *   nod ask --approve "Deploy to production?" && ./deploy.sh
 *   choice=$(nod ask "Which environment?" --options staging,production)
 *   nod notify "Backup finished" "42 GB in 3 min"
 *
 * Exit codes: 0 approved/answered · 1 denied · 2 no answer (timeout, paused, no device) · 3 error.
 */
import { approvalItem, ask, cancel, notifyItem, questionItem, send, sessionFor } from '../asking.ts'
import { loadConfig, type Config } from '../config.ts'

export interface AskArgs {
  text?: string
  approve?: boolean
  options?: string
  multi?: boolean
  details?: string
  agent?: string
  timeout?: string
  json?: boolean
}

const requireConfig = async (): Promise<Config> => {
  const config = await loadConfig()
  if (!config) throw new Error('Not configured. Run `nod setup` first.')
  return config
}

export const askCommand = async (args: AskArgs): Promise<void> => {
  if (!args.text)
    throw new Error('Usage: nod ask "<question>" [--approve | --options a,b,c] [--multi]')
  const config = await requireConfig()
  const session = sessionFor(args.agent ?? process.env.NOD_AGENT ?? 'Script')
  const timeout = args.timeout ? Number(args.timeout) : config.waitSeconds
  const options = (args.options ?? '')
    .split(',')
    .map((o) => o.trim())
    .filter(Boolean)

  const item =
    args.approve || options.length === 0
      ? approvalItem(session, args.text, args.details)
      : questionItem(session, args.text, options, args.multi === true, args.details)

  let itemId: string | undefined
  process.once('SIGINT', () => {
    void (itemId ? cancel(config, itemId) : Promise.resolve()).finally(() => process.exit(130))
  })
  const outcome = await ask(config, item, timeout, (id) => (itemId = id))

  if (args.json) console.log(JSON.stringify(outcome))
  else if (outcome.status === 'answered') console.log(outcome.answers.join('\n'))
  else if (outcome.status === 'denied' && outcome.reason) console.error(outcome.reason)

  process.exitCode = outcome.status === 'denied' ? 1 : outcome.status === 'no_answer' ? 2 : 0
}

export const notifyCommand = async (
  title: string | undefined,
  message: string | undefined,
  args: { failed?: boolean; agent?: string },
): Promise<void> => {
  if (!title) throw new Error('Usage: nod notify "<title>" ["<message>"] [--failed]')
  const config = await requireConfig()
  const session = sessionFor(args.agent ?? process.env.NOD_AGENT ?? 'Script')
  await send(config, notifyItem(session, title, message, args.failed ? 'failed' : 'done'))
}
