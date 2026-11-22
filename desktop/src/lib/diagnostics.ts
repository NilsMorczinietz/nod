/**
 * Sends uncaught errors to the diagnostic log (logs/nod.log) so user reports
 * can be diagnosed. Installed first thing in every window.
 */
import { api } from './api'

const report = (message: string) => void api.logError(message).catch(() => {})

export const installErrorReporting = (): void => {
  ;(window as unknown as { __nodLoadedAt?: number }).__nodLoadedAt = Date.now()
  window.addEventListener('error', (event) =>
    report(`${event.message} at ${event.filename}:${event.lineno}:${event.colno}`),
  )
  window.addEventListener('unhandledrejection', (event) =>
    report(`unhandled rejection: ${String(event.reason?.stack ?? event.reason)}`),
  )
}

export const reportError = (error: unknown): void =>
  report(`render error: ${String((error as Error)?.stack ?? error)}`)

/** Marks the window as started; the backend reloads windows that never got here. */
export const markReady = (): void => {
  ;(window as unknown as { __nodReady?: boolean }).__nodReady = true
}
