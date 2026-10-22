package com.ivy.home.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.repository.InboxRepository
import com.ivy.data.db.entity.CapturedMessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: InboxRepository,
    private val suggestions: com.ivy.data.repository.PaymentSuggestionRepository,
) : ViewModel() {

    val messages: Flow<List<CapturedMessageEntity>> =
        repository.getAllActiveMessages().map { messages ->
            messages.filter { com.ivy.data.model.PaymentParser.isPaymentMessage(it.title, it.body) }
        }

    val reviewLabels = messages.map { list ->
        list.associate { message -> message.id to suggestions.suggest(
            com.ivy.data.model.PaymentParser.parse(message.title, message.body, message.sender)).reviewLabel }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyMap())

    fun markRead(id: Long) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun dismiss(id: Long) {
        viewModelScope.launch {
            repository.dismiss(id)
        }
    }

    fun dismissAll() {
        viewModelScope.launch {
            repository.dismissAll()
        }
    }
}
