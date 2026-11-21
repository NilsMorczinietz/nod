/** Token generation and constant-time comparison helpers. */

const encoder = new TextEncoder()

export const base64Url = (bytes: Uint8Array): string =>
  btoa(String.fromCharCode(...bytes))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replace(/=+$/, '')

export const randomToken = (prefix: string, byteLength = 32): string => {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength))
  return `${prefix}${base64Url(bytes)}`
}

export const sha256Hex = async (value: string): Promise<string> => {
  const digest = await crypto.subtle.digest('SHA-256', encoder.encode(value))
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

/** Compares two secrets without leaking their content through timing. */
export const secretsEqual = async (a: string, b: string): Promise<boolean> => {
  const [ha, hb] = await Promise.all([
    crypto.subtle.digest('SHA-256', encoder.encode(a)),
    crypto.subtle.digest('SHA-256', encoder.encode(b)),
  ])
  return crypto.subtle.timingSafeEqual(ha, hb)
}

// Crockford-style alphabet without look-alikes (no 0/O, 1/I/L, U).
const PAIRING_ALPHABET = '23456789ABCDEFGHJKMNPQRSTVWXYZ'

/** Human-friendly one-time pairing code, e.g. "K7QM-4XPA" (~39 bits). */
export const pairingCode = (): string => {
  const size = PAIRING_ALPHABET.length
  const limit = 256 - (256 % size) // rejection sampling avoids modulo bias
  const chars: string[] = []
  while (chars.length < 8) {
    for (const byte of crypto.getRandomValues(new Uint8Array(16))) {
      if (byte < limit && chars.length < 8) chars.push(PAIRING_ALPHABET[byte % size]!)
    }
  }
  return `${chars.slice(0, 4).join('')}-${chars.slice(4).join('')}`
}

export const normalizePairingCode = (code: string): string => {
  const raw = code.toUpperCase().replace(/[^0-9A-Z]/g, '')
  return raw.length === 8 ? `${raw.slice(0, 4)}-${raw.slice(4)}` : raw
}
