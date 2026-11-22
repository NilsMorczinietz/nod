/**
 * Replies to prose questions: when Claude ends a turn by asking something in
 * plain text ("Shall I …?"), Nod sends the question to the user's devices and
 * wakes the session with the answer (Stop hook with `asyncRewake`).
 *
 * Pure helpers only – no I/O – so they can be unit-tested.
 */
import type { Item, NewItem, SessionRef } from '@nod/protocol'

/** Phrases that ask for a decision even without a question mark. */
const ASKING_PHRASES = [
  /\b(soll|sollen|darf|kann) ich\b/i,
  /\b(willst|möchtest|magst) du\b/i,
  /\bsag(e)? (mir )?bescheid\b/i,
  /\bgib (mir )?bescheid\b/i,
  /\b(shall|should|can|may) i\b/i,
  /\b(do|would) you (want|like)\b/i,
  /\bwant me to\b/i,
  /\blet me know\b/i,
]

const stripMarkdown = (text: string): string =>
  text
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/^\s*(?:[-*>]|\d+\.)\s+/gm, '')
    .replace(/^#{1,6}\s+/gm, '')

/** Last paragraph of the message, without Markdown, normalised whitespace. */
const lastParagraph = (message: string): string => {
  const paragraphs = stripMarkdown(message)
    .split(/\n\s*\n/)
    .map((p) => p.replace(/\s+/g, ' ').trim())
    .filter(Boolean)
  return paragraphs.at(-1) ?? ''
}

/**
 * The question the user is expected to answer, or null if the message doesn't
 * end by asking something. Only the final paragraph counts: questions in the
 * middle of a long answer are usually rhetorical or already resolved.
 */
export const pendingQuestion = (message: string | undefined): string | null => {
  if (!message) return null
  const paragraph = lastParagraph(message)
  if (!paragraph) return null
  const sentences = paragraph.match(/[^.!?]+[.!?]*/g)?.map((s) => s.trim()) ?? [paragraph]
  const asks = sentences.some((s) => /\?\s*$/.test(s) || ASKING_PHRASES.some((re) => re.test(s)))
  if (!asks) return null
  // Send the whole paragraph: the sentences around the question are its context.
  return paragraph.length > 300 ? `${paragraph.slice(0, 299)}…` : paragraph
}

export const replyItem = (session: SessionRef, question: string, ttlSeconds: number): NewItem => ({
  kind: 'question',
  session,
  title: `${session.agent ?? 'Claude Code'} is waiting for you`,
  body: question,
  ttlSeconds,
  questions: [
    {
      question,
      header: '',
      multiSelect: false,
      options: [{ label: 'Yes' }, { label: 'No' }],
      allowText: true,
    },
  ],
})

/**
 * Text that wakes Claude with the user's answer, or null when there is
 * nothing to deliver (dismissed, expired, cancelled).
 */
export const replyText = (item: Item): string | null => {
  const r = item.resolution
  if (item.status !== 'resolved' || !r || r.decision !== 'answer') return null
  const answer =
    r.message?.trim() ||
    Object.values(r.answers ?? {})
      .flat()
      .join(', ')
  if (!answer) return null
  return `The user replied to your question via Nod (on ${r.deviceName}): ${answer}`
}
