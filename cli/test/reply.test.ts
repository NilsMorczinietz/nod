import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import type { Item } from '@nod/protocol'
import { pendingQuestion, replyItem, replyText } from '../src/hook/reply.ts'

describe('pendingQuestion', () => {
  it('finds a German question at the end', () => {
    const msg = 'Ja, das kannst du löschen.\n\nWenn du „ja“ sagst, mache ich das.\n\nSoll ich?'
    assert.equal(pendingQuestion(msg), 'Soll ich?')
  })

  it('finds an English question in the last paragraph', () => {
    assert.equal(
      pendingQuestion('Tests pass.\n\nShall I commit and push this? It takes a minute.'),
      'Shall I commit and push this? It takes a minute.',
    )
  })

  it('detects asking phrases without a question mark', () => {
    assert.ok(pendingQuestion('Alles fertig. Sag Bescheid, wenn ich den Release anlegen soll.'))
    assert.ok(pendingQuestion('Done. Let me know if you want the dark theme too.'))
  })

  it('ignores questions that are not in the last paragraph', () => {
    assert.equal(
      pendingQuestion('Why did it fail? The cache was stale.\n\nFixed and verified.'),
      null,
    )
  })

  it('ignores plain status messages and code', () => {
    assert.equal(pendingQuestion('Release v0.1.1 is published.'), null)
    assert.equal(pendingQuestion('Run this:\n\n```bash\nnod status ?\n```'), null)
    assert.equal(pendingQuestion(undefined), null)
  })
})

describe('replyItem / replyText', () => {
  const session = { id: 's', agent: 'Claude Code', project: 'nod', cwd: '/w', host: 'h' }
  const item = (
    resolution: Partial<NonNullable<Item['resolution']>>,
    status: Item['status'] = 'resolved',
  ): Item => ({
    ...replyItem(session, 'Soll ich?', 600),
    id: 'i',
    status,
    createdAt: 0,
    expiresAt: 1,
    resolution: { decision: 'answer', deviceId: 'd', deviceName: 'Pixel', at: 1, ...resolution },
  })

  it('offers Yes / No and free text', () => {
    const q = replyItem(session, 'Soll ich?', 600).questions![0]!
    assert.deepEqual(
      q.options.map((o) => o.label),
      ['Yes', 'No'],
    )
    assert.equal(q.allowText, true)
  })

  it('prefers the free-text reply', () => {
    assert.match(replyText(item({ message: 'ja, aber ohne Push' }))!, /: ja, aber ohne Push$/)
  })

  it('falls back to the chosen option', () => {
    assert.match(replyText(item({ answers: { 'Soll ich?': ['Yes'] } }))!, /\(on Pixel\): Yes$/)
  })

  it('delivers nothing when dismissed or expired', () => {
    assert.equal(replyText(item({ decision: 'deny' })), null)
    assert.equal(replyText({ ...item({}), status: 'expired' }), null)
  })
})
