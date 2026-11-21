package io.github.nilsmorczinietz.nod.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nilsmorczinietz.nod.DeepLinks
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.NodEvent
import io.github.nilsmorczinietz.nod.data.PairingState
import io.github.nilsmorczinietz.nod.ui.detail.DetailScreen
import io.github.nilsmorczinietz.nod.ui.detail.DetailViewModel
import io.github.nilsmorczinietz.nod.ui.inbox.InboxMessage
import io.github.nilsmorczinietz.nod.ui.inbox.InboxScreen
import io.github.nilsmorczinietz.nod.ui.inbox.InboxViewModel
import io.github.nilsmorczinietz.nod.ui.pairing.PairingScreen
import io.github.nilsmorczinietz.nod.ui.pairing.PairingViewModel
import io.github.nilsmorczinietz.nod.ui.settings.SettingsScreen
import io.github.nilsmorczinietz.nod.ui.settings.SettingsViewModel
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import kotlinx.coroutines.launch

/** Destinations of the (deliberately tiny) navigation stack. */
private sealed interface Route {
    data object Inbox : Route
    data object Settings : Route
    data class Detail(val id: String) : Route

    companion object {
        fun encode(route: Route) = when (route) {
            Inbox -> "inbox"
            Settings -> "settings"
            is Detail -> "detail:${route.id}"
        }

        fun decode(value: String): Route = when {
            value == "settings" -> Settings
            value.startsWith("detail:") -> Detail(value.removePrefix("detail:"))
            else -> Inbox
        }
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map(Route::encode) },
    restore = { saved -> mutableStateListOf<Route>().apply { addAll(saved.map(Route::decode)) } },
)

@Composable
fun NodRoot(deepLinks: DeepLinks) {
    val container = appContainer()
    val repository = container.repository
    val pairing by repository.pairing.collectAsStateWithLifecycle()
    val colors = NodTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (val state = pairing) {
            PairingState.Loading -> Unit
            PairingState.Unpaired -> {
                val viewModel = nodViewModel { PairingViewModel(it.repository) }
                val link by deepLinks.pairLink.collectAsStateWithLifecycle()
                LaunchedEffect(link) {
                    link?.let {
                        viewModel.onPairingLink(it)
                        deepLinks.pairLink.value = null
                    }
                }
                PairingScreen(viewModel)
            }
            is PairingState.Paired -> PairedContent(deepLinks, showMessage, state.session.deviceId)
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        ) { data ->
            Snackbar(data, shape = NodShapes.button, containerColor = colors.popover, contentColor = colors.foreground)
        }
    }
}

@Composable
private fun PairedContent(deepLinks: DeepLinks, showMessage: (String) -> Unit, deviceId: String) {
    val stack = rememberSaveable(deviceId, saver = BackStackSaver) { mutableStateListOf<Route>(Route.Inbox) }
    var forward by remember { mutableStateOf(true) }
    val navigate: (Route) -> Unit = { forward = true; stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex) } }

    RequestNotificationPermission()

    val openItem by deepLinks.itemId.collectAsStateWithLifecycle()
    LaunchedEffect(openItem) {
        openItem?.let {
            if (stack.lastOrNull() != Route.Detail(it)) navigate(Route.Detail(it))
            deepLinks.itemId.value = null
        }
    }
    val answeredElsewhere = stringResource(R.string.message_answered_elsewhere)
    val repository = appContainer().repository
    LaunchedEffect(Unit) {
        repository.events.collect { event ->
            if (event is NodEvent.AnsweredElsewhere) showMessage(answeredElsewhere.format(event.item.resolution?.deviceName.orEmpty()))
        }
    }

    BackHandler(enabled = stack.size > 1, onBack = back)

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            val direction = if (forward) 1 else -1
            (slideInHorizontally(tween(320)) { it / 5 * direction } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(320)) { -it / 8 * direction } + fadeOut(tween(160)))
        },
        label = "nav",
    ) { route ->
        when (route) {
            Route.Inbox -> {
                val viewModel = nodViewModel { InboxViewModel(it.repository, it.updates) }
                InboxMessages(viewModel, showMessage)
                InboxScreen(viewModel, onOpenItem = { navigate(Route.Detail(it)) }, onOpenSettings = { navigate(Route.Settings) })
            }
            Route.Settings -> {
                val viewModel = nodViewModel { SettingsViewModel(it.repository, it.sessionStore, it.push, it.updates, it.appearance) }
                SettingsScreen(viewModel, onBack = back, onMessage = showMessage)
            }
            is Route.Detail -> {
                val viewModel = nodViewModel(key = route.id) { DetailViewModel(it.repository, route.id) }
                DetailScreen(viewModel, onBack = back, onMessage = showMessage)
            }
        }
    }
}

@Composable
private fun InboxMessages(viewModel: InboxViewModel, showMessage: (String) -> Unit) {
    val alreadyAnswered = stringResource(R.string.message_already_answered)
    val alreadyAnsweredBy = stringResource(R.string.message_already_answered_by)
    val failed = stringResource(R.string.message_failed)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            showMessage(
                when (message) {
                    is InboxMessage.AlreadyAnswered -> message.by?.let { alreadyAnsweredBy.format(it) } ?: alreadyAnswered
                    is InboxMessage.Failed -> failed
                },
            )
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifier = appContainer().notifier
    LaunchedEffect(Unit) {
        if (!asked && !notifier.canPost()) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

