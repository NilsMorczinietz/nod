/**
 * Minimal Firebase Cloud Messaging (HTTP v1) client for Workers.
 * Signs a service-account JWT with WebCrypto, exchanges it for an OAuth
 * access token and caches that token until shortly before it expires.
 */
import type { FirebaseClientConfig, PushPayload } from '@nod/protocol'
import { base64Url } from './crypto'

interface ServiceAccount {
  project_id: string
  client_email: string
  private_key: string
}

export interface CachedAccessToken {
  token: string
  expiresAt: number
}

export type SendResult = 'sent' | 'unregistered' | 'failed'

const TOKEN_URL = 'https://oauth2.googleapis.com/token'
const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging'

export const parseServiceAccount = (raw: string | undefined): ServiceAccount | null => {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<ServiceAccount>
    if (!parsed.project_id || !parsed.client_email || !parsed.private_key) return null
    return parsed as ServiceAccount
  } catch {
    console.error('FIREBASE_SERVICE_ACCOUNT is not valid JSON')
    return null
  }
}

export const firebaseClientConfig = (env: Env): FirebaseClientConfig | null => {
  const account = parseServiceAccount(env.FIREBASE_SERVICE_ACCOUNT)
  if (!account || !env.FIREBASE_APP_ID || !env.FIREBASE_API_KEY || !env.FIREBASE_SENDER_ID) {
    return null
  }
  return {
    projectId: account.project_id,
    applicationId: env.FIREBASE_APP_ID,
    apiKey: env.FIREBASE_API_KEY,
    senderId: env.FIREBASE_SENDER_ID,
  }
}

const encodeJson = (value: unknown): string =>
  base64Url(new TextEncoder().encode(JSON.stringify(value)))

const importPrivateKey = (pem: string): Promise<CryptoKey> => {
  const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, '').replace(/\s+/g, '')
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0))
  return crypto.subtle.importKey(
    'pkcs8',
    der,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  )
}

const fetchAccessToken = async (account: ServiceAccount): Promise<CachedAccessToken> => {
  const now = Math.floor(Date.now() / 1000)
  const unsigned = `${encodeJson({ alg: 'RS256', typ: 'JWT' })}.${encodeJson({
    iss: account.client_email,
    scope: SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600,
  })}`
  const key = await importPrivateKey(account.private_key)
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(unsigned),
  )
  const assertion = `${unsigned}.${base64Url(new Uint8Array(signature))}`

  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  })
  if (!response.ok) throw new Error(`OAuth token request failed: ${response.status}`)
  const data = (await response.json()) as { access_token: string; expires_in: number }
  return { token: data.access_token, expiresAt: Date.now() + (data.expires_in - 120) * 1000 }
}

export class FcmClient {
  private cached: CachedAccessToken | null

  constructor(
    private readonly account: ServiceAccount,
    initial: CachedAccessToken | null,
    private readonly persist: (token: CachedAccessToken) => void,
  ) {
    this.cached = initial
  }

  private async accessToken(): Promise<string> {
    if (this.cached && this.cached.expiresAt > Date.now()) return this.cached.token
    this.cached = await fetchAccessToken(this.account)
    this.persist(this.cached)
    return this.cached.token
  }

  /**
   * Sends a data-only message. Interactive items use HIGH priority so they
   * arrive immediately even in Doze; the app always shows a notification for
   * them, which keeps the high-priority quota healthy.
   */
  async send(
    pushToken: string,
    payload: PushPayload,
    opts: { highPriority: boolean; ttlSeconds: number },
  ): Promise<SendResult> {
    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${this.account.project_id}/messages:send`,
      {
        method: 'POST',
        headers: {
          authorization: `Bearer ${await this.accessToken()}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          message: {
            token: pushToken,
            data: payload,
            android: {
              priority: opts.highPriority ? 'HIGH' : 'NORMAL',
              ttl: `${Math.max(0, Math.round(opts.ttlSeconds))}s`,
            },
          },
        }),
      },
    )
    if (response.ok) return 'sent'
    const text = await response.text()
    if (response.status === 404 || text.includes('UNREGISTERED')) return 'unregistered'
    if (response.status === 401) this.cached = null
    console.error('FCM send failed', response.status, text.slice(0, 500))
    return 'failed'
  }
}
