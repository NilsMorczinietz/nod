/** One shared 1 Hz clock for all countdowns (instead of a timer per card). */
class Clock {
  now = $state(Date.now())
  #users = 0
  #timer: ReturnType<typeof setInterval> | undefined

  /** Call from an effect; returns the release function. */
  use(): () => void {
    if (this.#users++ === 0) {
      this.now = Date.now()
      this.#timer = setInterval(() => (this.now = Date.now()), 1000)
    }
    return () => {
      if (--this.#users === 0) clearInterval(this.#timer)
    }
  }
}

export const clock = new Clock()

/** "4:32" for a remaining duration in ms. */
export const formatRemaining = (ms: number): string => {
  const total = Math.max(0, Math.ceil(ms / 1000))
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}
