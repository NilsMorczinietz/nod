/** Thin typed client for the relay HTTP API (admin side). */
import type {
  CreateItemResponse,
  CreatePairingResponse,
  Device,
  HubSettings,
  Item,
  NewItem,
} from '@nod/protocol'

export class RelayError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

export class RelayClient {
  constructor(
    private readonly baseUrl: string,
    private readonly token: string,
  ) {}

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    timeoutMs = 15_000,
  ): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers: {
        authorization: `Bearer ${this.token}`,
        ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    })
    if (response.status === 204) return undefined as T
    const data = (await response.json().catch(() => ({}))) as Record<string, unknown>
    if (!response.ok) {
      const code = typeof data.error === 'string' ? data.error : 'http_error'
      const message = typeof data.message === 'string' ? data.message : `HTTP ${response.status}`
      throw new RelayError(response.status, code, message)
    }
    return data as T
  }

  health = () => this.request<{ ok: boolean; protocol: number }>('GET', '/v1/health')
  settings = () => this.request<HubSettings>('GET', '/v1/settings')
  updateSettings = (patch: Partial<HubSettings>) =>
    this.request<HubSettings>('PATCH', '/v1/settings', patch)

  createItem = (item: NewItem) => this.request<CreateItemResponse>('POST', '/v1/items', item)
  /** Long-poll until the item leaves "pending" or the server-side timeout hits. */
  waitForItem = (id: string, timeoutSeconds: number) =>
    this.request<Item>(
      'GET',
      `/v1/items/${encodeURIComponent(id)}/wait?timeout=${timeoutSeconds}`,
      undefined,
      (timeoutSeconds + 15) * 1000,
    )
  cancelItem = (id: string) =>
    this.request<Item>('POST', `/v1/items/${encodeURIComponent(id)}/cancel`, {}, 3_000)

  createPairing = () => this.request<CreatePairingResponse>('POST', '/v1/pairings', {})
  listDevices = () => this.request<{ devices: Device[] }>('GET', '/v1/devices')
  revokeDevice = (id: string) =>
    this.request<void>('DELETE', `/v1/devices/${encodeURIComponent(id)}`)
}
