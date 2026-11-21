/**
 * Motion from design/tokens.json: enter (opacity + 4 px rise, 200 ms) and the
 * island morph (320 ms). Curves are CSS cubic-beziers, evaluated here for
 * JS-driven tweens. "Reduce motion" turns everything into plain crossfades.
 */
import type { TransitionConfig } from 'svelte/transition'
import tokens from '../../../design/tokens.json'

export const reducedMotion = (): boolean => matchMedia('(prefers-reduced-motion: reduce)').matches

/** Evaluates `cubic-bezier(x1, y1, x2, y2)` like the browser does. */
export const cubicBezier = (css: string): ((t: number) => number) => {
  const [x1, y1, x2, y2] = (css.match(/[\d.]+/g) ?? []).map(Number) as [
    number,
    number,
    number,
    number,
  ]
  const ax = 1 + 3 * x1 - 3 * x2
  const bx = 3 * x2 - 6 * x1
  const cx = 3 * x1
  const ay = 1 + 3 * y1 - 3 * y2
  const by = 3 * y2 - 6 * y1
  const cy = 3 * y1
  const sampleX = (t: number) => ((ax * t + bx) * t + cx) * t
  const sampleY = (t: number) => ((ay * t + by) * t + cy) * t
  const slopeX = (t: number) => (3 * ax * t + 2 * bx) * t + cx
  return (x: number) => {
    if (x <= 0) return 0
    if (x >= 1) return 1
    let t = x
    for (let i = 0; i < 8; i++) {
      const error = sampleX(t) - x
      if (Math.abs(error) < 1e-5) break
      const slope = slopeX(t)
      if (Math.abs(slope) < 1e-6) break
      t -= error / slope
    }
    return sampleY(Math.min(1, Math.max(0, t)))
  }
}

const { enter, island } = tokens.motion
export const ISLAND_MOTION = { duration: island.duration, easing: cubicBezier(island.easing) }
const enterEasing = cubicBezier(enter.easing)

/** Enter: opacity + translateY(4 px). Reduce motion: opacity only. */
export const rise = (_node: Element, { delay = 0 }: { delay?: number } = {}): TransitionConfig => ({
  delay,
  duration: enter.duration,
  easing: enterEasing,
  css: (t, u) =>
    reducedMotion()
      ? `opacity: ${t};`
      : `opacity: ${t}; transform: translateY(${u * enter.translateY}px);`,
})

/** Plain crossfade (island content swaps). */
export const fade = (
  _node: Element,
  { delay = 0, duration = enter.duration }: { delay?: number; duration?: number } = {},
): TransitionConfig => ({
  delay: reducedMotion() ? 0 : delay,
  duration,
  css: (t) => `opacity: ${t};`,
})
