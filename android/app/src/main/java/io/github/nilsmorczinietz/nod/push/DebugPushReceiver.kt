package io.github.nilsmorczinietz.nod.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.nilsmorczinietz.nod.NodApp
import kotlinx.coroutines.launch

/**
 * Simulates an FCM data message so notifications can be tested without a
 * Firebase project. Only enabled in debug and `-Pnod.localTest=true` builds
 * (manifest placeholder), and it requires the DUMP permission, i.e. adb:
 *
 *   adb shell am broadcast -a io.github.nilsmorczinietz.nod.debug.PUSH \
 *     -p io.github.nilsmorczinietz.nod --es type item --es id <item-id> --es kind permission \
 *     --es title "Run tests" --es body "npm test" --es project acme --es expiresAt <epoch-ms> --es canAlwaysAllow 1
 */
class DebugPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val data = extras.keySet().associateWith { extras.getString(it).orEmpty() }
        val container = (context.applicationContext as NodApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.pushHandler.handle(data)
            } finally {
                pending.finish()
            }
        }
    }
}
