/** Human status of an item, mapped to the badge variants from tokens.json. */
import type { Item, ItemKind } from '@nod/protocol'

export type BadgeVariant = 'approved' | 'denied' | 'waiting' | 'neutral'

export interface Status {
  label: string
  variant: BadgeVariant
  /** Colour for the island glyph. */
  tone: string
  /** Glyph for the island bar: a drawn check, a cross, or a dot. */
  glyph: 'check' | 'cross' | 'dot'
}

export const KIND_LABEL: Record<ItemKind, string> = {
  permission: 'Permission',
  question: 'Question',
  plan: 'Plan',
  completed: 'Done',
  failed: 'Failed',
  attention: 'Waiting for you',
}

const APPROVED = { variant: 'approved', tone: 'var(--c-primary)', glyph: 'check' } as const
const DENIED = { variant: 'denied', tone: 'var(--c-danger)', glyph: 'cross' } as const
const WAITING = { variant: 'waiting', tone: 'var(--c-waiting)', glyph: 'dot' } as const
const NEUTRAL = { variant: 'neutral', tone: 'var(--c-muted-foreground)', glyph: 'dot' } as const

export const statusOf = (item: Item): Status => {
  const r = item.resolution
  switch (item.status) {
    case 'pending':
      return { label: 'Needs you', ...WAITING }
    case 'expired':
      return { label: 'Expired', ...NEUTRAL }
    case 'cancelled':
      return { label: 'Terminal', ...NEUTRAL }
    case 'info':
      if (item.kind === 'completed') return { label: 'Done', ...APPROVED }
      if (item.kind === 'failed') return { label: 'Failed', ...DENIED }
      return { label: 'Waiting for you', ...WAITING }
    case 'resolved':
      if (r?.decision === 'answer') return { label: 'Answered', ...APPROVED }
      if (r?.decision === 'deny') {
        return { label: item.kind === 'plan' ? 'Rejected' : 'Denied', ...DENIED }
      }
      return { label: 'Approved', ...APPROVED }
  }
}

/** Display name of the asking agent; sessions without one come from Claude Code. */
export const agentName = (item: Item): string => item.session.agent ?? 'Claude Code'

/** "now", "3m ago", "2h ago", "4d ago" */
export const relativeTime = (epochMs: number, now = Date.now()): string => {
  const s = Math.max(0, Math.round((now - epochMs) / 1000))
  if (s < 60) return 'now'
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`
  return `${Math.floor(s / 86400)}d ago`
}
