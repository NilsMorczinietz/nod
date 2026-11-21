package io.github.nilsmorczinietz.nod.data

import android.net.Uri

/** `nod://pair?relay=<url>&code=<code>`, as printed and shown as QR code by `nod pair`. */
data class PairingLink(val relay: String, val code: String) {
    val host: String get() = runCatching { Uri.parse(relay).host }.getOrNull() ?: relay

    companion object {
        fun parse(raw: String): PairingLink? {
            val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "nod" || uri.host != "pair") return null
            val relay = uri.getQueryParameter("relay")?.takeIf { it.isNotBlank() } ?: return null
            val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return null
            return PairingLink(relay, code)
        }
    }
}
