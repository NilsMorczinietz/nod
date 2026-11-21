package io.github.nilsmorczinietz.nod

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class NodApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        // Must happen before FCM can hand us a message.
        container.push.initFromStore()

        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        processLifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isInForeground = true
            }

            override fun onStop(owner: LifecycleOwner) {
                isInForeground = false
            }
        })
        // Live WebSocket only while the app is visible; FCM covers the background.
        container.appScope.launch {
            processLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.repository.runLive()
            }
        }
        container.appScope.launch { container.push.registerToken() }
        container.appScope.launch { container.updates.checkIfDue() }
    }

    companion object {
        /** Read from FCM's background thread, written on the main thread. */
        @Volatile
        var isInForeground: Boolean = false
            private set
    }
}
