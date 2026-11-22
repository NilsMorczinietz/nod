package io.github.nilsmorczinietz.nod.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nilsmorczinietz.nod.data.NodRepository
import io.github.nilsmorczinietz.nod.data.api.ResolveResult
import io.github.nilsmorczinietz.nod.data.model.Decision
import io.github.nilsmorczinietz.nod.data.model.Item
import io.github.nilsmorczinietz.nod.data.model.ResolveInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailEvent {
    /** Answer accepted; the screen closes (the app root plays the celebration). */
    data object Done : DetailEvent
    data class AlreadyAnswered(val by: String?) : DetailEvent
    data class Failed(val reason: String?) : DetailEvent
}

class DetailViewModel(
    private val repository: NodRepository,
    private val itemId: String,
) : ViewModel() {

    val item: StateFlow<Item?> = repository.items
        .map { it[itemId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.items.value[itemId])

    /** Selected option labels per question index. */
    private val _selections = MutableStateFlow<Map<Int, Set<String>>>(emptyMap())
    val selections: StateFlow<Map<Int, Set<String>>> = _selections.asStateFlow()

    private val _submitting = MutableStateFlow<Decision?>(null)
    val submitting: StateFlow<Decision?> = _submitting.asStateFlow()

    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch { repository.loadItem(itemId) }
    }

    fun toggle(questionIndex: Int, label: String, multiSelect: Boolean) = _selections.update { current ->
        val chosen = current[questionIndex].orEmpty()
        val next = when {
            !multiSelect -> setOf(label)
            label in chosen -> chosen - label
            else -> chosen + label
        }
        current + (questionIndex to next)
    }

    fun allow(always: Boolean = false) = submit(ResolveInput(Decision.Allow, alwaysAllow = always.takeIf { it }))

    fun deny(message: String? = null) = submit(ResolveInput(Decision.Deny, message = message?.takeIf(String::isNotBlank)))

    /** Reply question: one of its options ("Yes" / "No"). */
    fun answerOption(label: String) {
        val question = item.value?.questions?.singleOrNull() ?: return
        submit(ResolveInput(Decision.Answer, answers = mapOf(question.question to listOf(label))))
    }

    /** Reply question: free text instead of an option. */
    fun reply(text: String) = submit(ResolveInput(Decision.Answer, answers = emptyMap(), message = text))

    fun submitAnswers() {
        val questions = item.value?.questions ?: return
        val answers = questions.mapIndexedNotNull { index, q ->
            _selections.value[index]?.takeIf { it.isNotEmpty() }?.let { selected ->
                // Keep the option order Claude proposed.
                q.question to q.options.map { it.label }.filter(selected::contains)
            }
        }.toMap()
        submit(ResolveInput(Decision.Answer, answers = answers))
    }

    private fun submit(input: ResolveInput) {
        if (_submitting.value != null) return
        _submitting.value = input.decision
        viewModelScope.launch {
            try {
                val event = when (val result = repository.resolve(itemId, input)) {
                    is ResolveResult.Resolved -> DetailEvent.Done
                    is ResolveResult.AlreadyFinished -> DetailEvent.AlreadyAnswered(result.item.resolution?.deviceName)
                }
                _events.tryEmit(event)
            } catch (e: Exception) {
                _events.tryEmit(DetailEvent.Failed(e.message))
            } finally {
                _submitting.value = null
            }
        }
    }
}
