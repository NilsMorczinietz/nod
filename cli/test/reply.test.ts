import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import type { Item } from '@nod/protocol'
import { MAX_QUESTION, pendingQuestion, replyItem, replyText } from '../src/hook/reply.ts'
import { clipMiddle, markdownTail } from '../src/hook/summarize.ts'

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

  it('detects requests to reply and open to-dos', () => {
    const glib =
      'Noch offen für dich: die GitHub-Sicherheitswarnung zu glib schließen, entweder selbst über den ' +
      'Link von vorhin oder indem du mir hier „ja, schließen“ schreibst.'
    assert.ok(pendingQuestion(`Release ist draußen.\n\n${glib}`))
    assert.ok(pendingQuestion('Alles gepusht. Wenn du willst, lege ich noch den PR an.'))
    assert.ok(pendingQuestion('Deployed. Just reply "go" and I will tag the release.'))
    assert.ok(pendingQuestion('Done. If you want, I can also add the dark theme.'))
  })

  it('ignores status reports that only mention people or offers in passing', () => {
    assert.equal(pendingQuestion('Ich melde mich, sobald der Build fertig ist.'), null)
    assert.equal(pendingQuestion('Der Agent hat mir den Bericht geschickt. Alles grün.'), null)
    assert.equal(pendingQuestion('The CLI now tells you which device answered.'), null)
  })

  it('ignores questions that are not in the last paragraph', () => {
    assert.equal(
      pendingQuestion('Why did it fail? The cache was stale.\n\nFixed and verified.'),
      null,
    )
  })

  it('keeps the question when the last paragraph is long', () => {
    const context =
      'Ich habe den Build v0.2.2 geprüft, die Tests laufen und die APK ist signiert. '.repeat(6)
    const question = pendingQuestion(
      `Kurz vorab.\n\n${context}Soll ich den Release jetzt veröffentlichen?`,
    )!
    assert.ok(question.length <= MAX_QUESTION)
    assert.match(question, /Soll ich den Release jetzt veröffentlichen\?$/)
    assert.doesNotMatch(question, /^…/)
    // Sentences are cut at their start, never inside "v0.2.2".
    assert.match(question, /^Ich habe den Build v0\.2\.2/)
  })

  it('starts at the question when long remarks follow it', () => {
    const remark =
      'Das dauert etwa zehn Minuten, weil der Build über alle vier Architekturen läuft. '
    const question = pendingQuestion(`Soll ich pushen? ${remark.repeat(5)}`)!
    assert.match(question, /^Soll ich pushen\? Das dauert/)
    assert.ok(question.length <= MAX_QUESTION)
  })

  it('shortens a single huge question in the middle', () => {
    const question = pendingQuestion(
      `Soll ich ${'sehr '.repeat(100)}lange Dinge wirklich jetzt löschen?`,
    )!
    assert.ok(question.length <= MAX_QUESTION)
    assert.match(question, /^Soll ich sehr/)
    assert.match(question, / … .*wirklich jetzt löschen\?$/)
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

  it('sends the whole message as context', () => {
    const message = '## Status\n\nAlles grün.\n\nSoll ich?'
    assert.equal(replyItem(session, 'Soll ich?', 600, message).body, message)
    assert.equal(replyItem(session, 'Soll ich?', 600, 'Soll ich?').body, 'Soll ich?')
    assert.equal(replyItem(session, 'Soll ich?', 600).body, 'Soll ich?')
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

describe('clipping', () => {
  it('clipMiddle keeps start and end', () => {
    const text = `Start ${'mitte '.repeat(50)}Ende?`
    const clipped = clipMiddle(text, 60)
    assert.ok(clipped.length <= 60)
    assert.match(clipped, /^Start /)
    assert.match(clipped, /Ende\?$/)
    assert.equal(clipMiddle('kurz', 60), 'kurz')
  })

  it('markdownTail keeps the end and never starts inside a code block', () => {
    const md = `Intro.\n\n\`\`\`bash\n${'echo hi\n\n'.repeat(30)}\`\`\`\n\nSoll ich?`
    const tail = markdownTail(md, 120)
    assert.ok(tail.length <= 120)
    assert.ok(tail.endsWith('Soll ich?'))
    assert.ok(tail.startsWith('…'))
    assert.equal((tail.match(/```/g) ?? []).length % 2, 0)
  })
})
