/** Waiting for an answer, shared by the hook, `nod ask` and the MCP server. */
import type { Item } from '@nod/protocol'
import { logToFile } from './log.ts'
import type { RelayClient } from './relay-client.ts'

const POLL_SECONDS = 25

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * Long-polls the relay until the item leaves "pending" or the deadline passes.
 * Network errors are retried with capped exponential backoff. Returns null on timeout.
 */
export const waitForDecision = async (
  client: RelayClient,
  item: Item,
  deadline: number,
): Promise<Item | null> => {
  let backoff = 1000
  while (Date.now() < deadline) {
    const remaining = Math.ceil((deadline - Date.now()) / 1000)
    try {
      const current = await client.waitForItem(
        item.id,
        Math.max(1, Math.min(POLL_SECONDS, remaining)),
      )
      if (current.status !== 'pending') return current
      backoff = 1000
    } catch (error) {
      await logToFile(`wait ${item.id} failed, retrying`, error)
      await sleep(backoff)
      backoff = Math.min(backoff * 2, 8000)
    }
  }
  return null
}
