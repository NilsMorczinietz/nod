/** Tiny synthesized chimes (no audio assets). */
import type { ItemKind } from '@nod/protocol'

let context: AudioContext | null = null

const NOTES: Record<ItemKind, number[]> = {
  permission: [660, 880],
  question: [740, 988],
  plan: [587, 880],
  completed: [784, 1175],
  failed: [440, 330],
  attention: [880],
}

export const chime = (kind: ItemKind): void => {
  context ??= new AudioContext()
  const now = context.currentTime
  NOTES[kind].forEach((frequency, i) => {
    const osc = context!.createOscillator()
    const gain = context!.createGain()
    const start = now + i * 0.09
    osc.type = 'sine'
    osc.frequency.value = frequency
    gain.gain.setValueAtTime(0, start)
    gain.gain.linearRampToValueAtTime(0.08, start + 0.015)
    gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.35)
    osc.connect(gain).connect(context!.destination)
    osc.start(start)
    osc.stop(start + 0.4)
  })
}
