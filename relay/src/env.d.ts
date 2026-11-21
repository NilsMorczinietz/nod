// Secrets are not visible to `wrangler types`; declare them here (see wrangler.jsonc).
interface Env {
  /** Bearer token of the machines running Claude Code (the CLI). */
  ADMIN_TOKEN: string
  /** Firebase service-account JSON; push is disabled when missing. */
  FIREBASE_SERVICE_ACCOUNT?: string
  /** Public Firebase client config handed to the Android app during pairing. */
  FIREBASE_APP_ID?: string
  FIREBASE_API_KEY?: string
  FIREBASE_SENDER_ID?: string
}
