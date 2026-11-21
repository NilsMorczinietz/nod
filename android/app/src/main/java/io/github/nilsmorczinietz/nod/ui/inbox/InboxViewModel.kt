package io.github.nilsmorczinietz.nod.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nilsmorczinietz.nod.data.ConnectionState
import io.github.nilsmorczinietz.nod.data.NodRepository
import io.github.nilsmorczinietz.nod.data.api.ResolveResult
import io.github.nilsmorczinietz.nod.data.update.UpdateChecker
import io.github.nilsmorczinietz.nod.data.update.UpdateInfo
import io.github.nilsmorczinietz.nod.data.update.UpdateState
import io.github.nilsmorczinietz.nod.data.model.Decision
import io.github.nilsmorczinietz.nod.data.model.Item
import io.github.nilsmorczinietz.nod.data.model.ItemKind
import io.github.nilsmorczinietz.nod.data.model.ItemStatus
import io.github.nilsmorczinietz.nod.ui.components.WorkingSession
import io.github.nilsmorczinietz.nod.data.model.ResolveInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class InboxState(
    val pending: List<Item> = emptyList(),
    val history: List<Item> = emptyList(),
    /** Sessions with recent activity, no open request and no "done" yet. */
    val working: List<WorkingSession> = emptyList(),
    /** Today's numbers (local time), computed from the items we know about. */
    val projectsToday: Int = 0,
    val approvedToday: Int = 0,
    val deniedToday: Int = 0,
    val decisionsToday: Int = 0,
    val paused: Boolean = false,
    val connection: ConnectionState = ConnectionState.Offline,
    /** Items currently being answered from the inbox, with the chosen decision. */
    val resolving: Map<String, Decision> = emptyMap(),
)

sealed interface InboxMessage {
    data class AlreadyAnswered(val by: String?) : InboxMessage
    data class Failed(val reason: String?) : InboxMessage
}

class InboxViewModel(
    private val repository: NodRepository,
    private val updates: UpdateChecker,
) : ViewModel() {

    /** A newer release the user hasn't dismissed yet (quiet banner at the bottom). */
    val update: StateFlow<UpdateInfo?> = combine(updates.state, updates.dismissedVersion) { state, dismissed ->
        (state as? UpdateState.Available)?.info?.takeIf { it.version != dismissed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissUpdate(version: String) {
        viewModelScope.launch { updates.dismiss(version) }
    }

    private val resolving = MutableStateFlow<Map<String, Decision>>(emptyMap())
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _messages = MutableSharedFlow<InboxMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<InboxState> = combine(
        repository.items,
        repository.settings,
        repository.connection,
        resolving,
    ) { items, settings, connection, resolvingMap ->
        val all = items.values.sortedByDescending(Item::createdAt)
        // Answered cards stay briefly so the outcome pill can be read before they fade out.
        val pending = all.filter { it.status == ItemStatus.Pending || it.id in resolvingMap }
        val pendingIds = pending.mapTo(HashSet(), Item::id)
        val history = all.filter { it.id !in pendingIds }.take(HISTORY_LIMIT)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun isToday(epochMs: Long) = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate() == today
        val decidedToday = all.mapNotNull { it.resolution }.filter { isToday(it.at) }
        InboxState(
            pending = pending,
            history = history,
            working = workingSessions(all, pendingIds),
            projectsToday = all.filter { isToday(it.createdAt) }.map { it.session.project }.distinct().size,
            approvedToday = decidedToday.count { it.decision != Decision.Deny },
            deniedToday = decidedToday.count { it.decision == Decision.Deny },
            decisionsToday = decidedToday.size,
            paused = settings.paused,
            connection = connection,
            resolving = resolvingMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxState())

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { repository.refresh() }.onFailure { _messages.tryEmit(InboxMessage.Failed(it.message)) }
            _refreshing.value = false
        }
    }

    fun resume() {
        viewModelScope.launch {
            runCatching { repository.resume() }.onFailure { _messages.tryEmit(InboxMessage.Failed(it.message)) }
        }
    }

    fun quickResolve(item: Item, decision: Decision) {
        if (item.id in resolving.value) return
        resolving.update { it + (item.id to decision) }
        viewModelScope.launch {
            try {
                when (val result = repository.resolve(item.id, ResolveInput(decision))) {
                    is ResolveResult.Resolved -> delay(OUTCOME_HOLD_MS)
                    is ResolveResult.AlreadyFinished -> _messages.tryEmit(InboxMessage.AlreadyAnswered(result.item.resolution?.deviceName))
                }
            } catch (e: Exception) {
                _messages.tryEmit(InboxMessage.Failed(e.message))
            } finally {
                resolving.update { it - item.id }
            }
        }
    }

    /**
     * A session counts as working when its latest item is recent, it has no
     * open request and Claude hasn't reported "done" or "failed" yet.
     */
    private fun workingSessions(all: List<Item>, pendingIds: Set<String>): List<WorkingSession> {
        val now = System.currentTimeMillis()
        return all.groupBy { it.session.id }.values.mapNotNull { items ->
            val latest = items.maxBy(Item::createdAt)
            val active = now - latest.createdAt < WORKING_WINDOW_MS &&
                items.none { it.id in pendingIds } &&
                latest.kind != ItemKind.Completed && latest.kind != ItemKind.Failed
            if (!active) return@mapNotNull null
            WorkingSession(latest.session.project, latest.session.agentName, latest.createdAt, latest.permission?.summary ?: latest.title)
        }.sortedByDescending(WorkingSession::since)
    }

    private companion object {
        const val WORKING_WINDOW_MS = 15 * 60 * 1000L
        const val HISTORY_LIMIT = 40
        const val OUTCOME_HOLD_MS = 900L
    }
}
