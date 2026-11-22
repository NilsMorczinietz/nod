/**
 * A rAF-driven tween with a fixed duration and easing (the island morph,
 * 320 ms cubic-bezier from tokens.json). `current` is reactive; `onRest`
 * runs when the tween reaches its target and wasn't superseded.
 *
 * WebView2 pauses requestAnimationFrame while it considers the window hidden
 * or occluded, so a timer finishes the tween if frames stop coming. Without
 * it, `onRest` (which hides the overlay window) might never run.
 */
import { reducedMotion } from './motion'

/** Extra time before the timer takes over from requestAnimationFrame. */
const FALLBACK_SLACK_MS = 250

export class Tween {
  current = $state(0)
  #target = 0
  #frame = 0
  #fallback: ReturnType<typeof setTimeout> | undefined

  constructor(
    initial: number,
    private readonly motion: { duration: number; easing: (t: number) => number },
  ) {
    this.current = initial
    this.#target = initial
  }

  set(
    target: number,
    { instant = false, onRest }: { instant?: boolean; onRest?: () => void } = {},
  ) {
    this.#cancel()
    this.#target = target
    if (instant || reducedMotion() || this.current === target) {
      this.current = target
      onRest?.()
      return
    }
    const from = this.current
    const start = performance.now()
    const { duration, easing } = this.motion
    const finish = () => {
      this.#cancel()
      this.current = target
      onRest?.()
    }
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      if (t >= 1) return finish()
      this.current = from + (target - from) * easing(t)
      this.#frame = requestAnimationFrame(tick)
    }
    this.#frame = requestAnimationFrame(tick)
    this.#fallback = setTimeout(finish, duration + FALLBACK_SLACK_MS)
  }

  get target(): number {
    return this.#target
  }

  #cancel() {
    cancelAnimationFrame(this.#frame)
    clearTimeout(this.#fallback)
    this.#frame = 0
    this.#fallback = undefined
  }
}
