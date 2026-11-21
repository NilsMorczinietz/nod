/**
 * Worker entry point. Everything below /v1 is handled by the single Hub
 * Durable Object; the worker itself is stateless.
 */
import { PROTOCOL_VERSION } from '@nod/protocol'
import { json } from './http'

export { Hub } from './hub'

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url)

    if (url.pathname === '/' || url.pathname === '/v1/health') {
      return json({ ok: true, service: 'nod-relay', protocol: PROTOCOL_VERSION })
    }
    if (url.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(url.hostname)) {
      return json({ error: 'https_required' }, 400)
    }
    if (!url.pathname.startsWith('/v1/')) {
      return json({ error: 'not_found' }, 404)
    }
    // One installation = one hub. The name is arbitrary but must stay stable.
    return env.HUB.getByName('hub').fetch(request)
  },
} satisfies ExportedHandler<Env>
