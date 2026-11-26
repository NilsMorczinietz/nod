/**
 * Replies to prose questions: when Claude ends a turn by asking something in
 * plain text ("Shall I …?"), Nod sends the question to the user's devices and
 * wakes the session with the answer (Stop hook with `asyncRewake`).
 *
 * Pure helpers only – no I/O – so they can be unit-tested.
 */
import type { Item, NewItem, SessionRef } from '@nod/protocol'
import { clipMiddle, markdownTail } from './summarize.ts'

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
  // Requests to reply ("…indem du mir hier „ja“ schreibst", "just reply …").
  /\b(schreib|schreibe|antworte|sag)(st)? mir\b/i,
  /\bdu mir\b.*\b(schreibst|sagst|antwortest|gibst)\b/i,
  /\b(tell|reply to|write to) me\b/i,
  /\bjust (say|reply|type|write)\b/i,
  // Open offers and to-dos for the user.
  /\b(wenn|falls) du (willst|möchtest|magst|es willst)\b/i,
  /\bif you(?:'d| would)? (want|like|prefer)\b/i,
  /\bnoch offen\b/i,
  /\b(still )?(open|pending|left) for you\b/i,
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

/** Longest question we send. It is the notification text and the card heading. */
export const MAX_QUESTION = 280
/** Longest message we attach as context (the relay keeps 4000). */
const MAX_CONTEXT = 3800

/** Sentences with their start offset; "v0.2.2" or "reply.ts" don't end one. */
const sentencesOf = (paragraph: string): { start: number; text: string }[] =>
  [...paragraph.matchAll(/\S[\s\S]*?(?:[.!?]+(?=\s|$)|$)/g)].map((m) => ({
    start: m.index,
    text: m[0].trim(),
  }))

const isAsking = (sentence: string): boolean =>
  /\?["“”»«')]*$/.test(sentence) || ASKING_PHRASES.some((re) => re.test(sentence))

/**
 * The part of the paragraph that asks: the whole paragraph when it fits,
 * else the longest ending that still contains the question, else the
 * question itself. Never cut at the end, that is where the decision is.
 */
const focus = (paragraph: string, sentences: { start: number; text: string }[]): string => {
  if (paragraph.length <= MAX_QUESTION) return paragraph
  for (const [i, sentence] of sentences.entries()) {
    const ending = paragraph.slice(sentence.start)
    if (ending.length <= MAX_QUESTION && sentences.slice(i).some((s) => isAsking(s.text))) {
      return ending
    }
  }
  // The question is followed by long remarks: start at it and take what fits.
  const asking = sentences.findLastIndex((s) => isAsking(s.text))
  let text = sentences[asking]!.text
  for (const next of sentences.slice(asking + 1)) {
    if (text.length + 1 + next.text.length > MAX_QUESTION) break
    text = `${text} ${next.text}`
  }
  return clipMiddle(text, MAX_QUESTION)
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
  const sentences = sentencesOf(paragraph)
  if (!sentences.some((s) => isAsking(s.text))) return null
  // The sentences around the question are its context, so they come along while they fit.
  return focus(paragraph, sentences)
}

/**
 * A reply request. `question` is what the devices lead with; `message` (the
 * whole turn, Markdown) travels as the body so the apps can show the context.
 */
export const replyItem = (
  session: SessionRef,
  question: string,
  ttlSeconds: number,
  message?: string,
): NewItem => {
  const context = message?.trim()
  return {
    kind: 'question',
    session,
    title: `${session.agent ?? 'Claude Code'} is waiting for you`,
    body:
      context && context.length > question.length ? markdownTail(context, MAX_CONTEXT) : question,
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
  }
}

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
