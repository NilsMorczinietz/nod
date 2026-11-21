/** Small HTTP helpers shared by the worker entry point and the hub. */

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string = code,
  ) {
    super(message)
  }
}

const SECURITY_HEADERS = {
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff',
} as const

export const json = (data: unknown, status = 200): Response =>
  new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', ...SECURITY_HEADERS },
  })

export const noContent = (): Response =>
  new Response(null, { status: 204, headers: SECURITY_HEADERS })

export const errorResponse = (error: unknown): Response => {
  if (error instanceof HttpError) {
    return json({ error: error.code, message: error.message }, error.status)
  }
  console.error('unhandled error', error)
  return json({ error: 'internal', message: 'Internal error' }, 500)
}

const MAX_BODY_BYTES = 256 * 1024

/** Reads and parses a JSON body with a hard size limit. */
export const readJson = async (request: Request): Promise<unknown> => {
  const declared = Number(request.headers.get('content-length') ?? 0)
  if (declared > MAX_BODY_BYTES) throw new HttpError(413, 'payload_too_large')
  const text = await request.text()
  if (text.length > MAX_BODY_BYTES) throw new HttpError(413, 'payload_too_large')
  try {
    return JSON.parse(text)
  } catch {
    throw new HttpError(400, 'invalid_json')
  }
}

export const bearerToken = (request: Request): string | null => {
  const header = request.headers.get('authorization')
  if (!header?.startsWith('Bearer ')) return null
  const token = header.slice('Bearer '.length).trim()
  return token.length > 0 ? token : null
}
