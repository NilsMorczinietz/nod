/**
 * A rAF-driven tween with a fixed duration and easing (the island morph,
 * 320 ms cubic-bezier from tokens.json). `current` is reactive; `onRest`
 * runs when the tween reaches its target and wasn't superseded.
 */
import { reducedMotion } from './motion'

export class Tween {
  current = $state(0)
  #target = 0
  #frame = 0

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
    cancelAnimationFrame(this.#frame)
    this.#frame = 0
    this.#target = target
    if (instant || reducedMotion() || this.current === target) {
      this.current = target
      onRest?.()
      return
    }
    const from = this.current
    const start = performance.now()
    const { duration, easing } = this.motion
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      this.current = from + (target - from) * easing(t)
      if (t < 1) this.#frame = requestAnimationFrame(tick)
      else {
        this.#frame = 0
        onRest?.()
      }
    }
    this.#frame = requestAnimationFrame(tick)
  }

  get target(): number {
    return this.#target
  }
}
