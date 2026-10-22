package com.ivy.home.trips

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.db.entity.TripGroupEntity
import com.ivy.data.db.entity.TripExpenseEntity
import com.ivy.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltViewModel
class TripGroupsViewModel @Inject constructor(private val repository: TripRepository) : ViewModel() {
    val groups = repository.observeGroups()
    var people by mutableStateOf<List<String>>(emptyList()); private set
    var selected by mutableStateOf<TripGroupEntity?>(null); private set
    var expenses by mutableStateOf<List<TripExpenseEntity>>(emptyList()); private set
    var balances by mutableStateOf<List<TripRepository.Balance>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    init { refreshPeople() }
    fun refreshPeople() = viewModelScope.launch { people = withContext(Dispatchers.IO) { repository.people() } }
    fun open(group: TripGroupEntity?) {
        selected = group; expenses = emptyList(); balances = emptyList(); error = null
        if (group != null) viewModelScope.launch {
            loading = true
            try {
                val result = withContext(Dispatchers.IO) { repository.expenses(group.id) to repository.balances(group.id) }
                if (selected?.id == group.id) { expenses = result.first; balances = result.second }
            } catch (e: Exception) { error = "Unable to load group: ${e.message.orEmpty()}" }
            finally { loading = false }
        }
    }
    fun save(id: String?, name: String, members: List<String>, done: () -> Unit) {
        if (saving) return
        saving = true; error = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.saveGroup(id, name, members) }
                refreshPeople(); if (selected?.id == id) open(selected?.copy(name = name.trim(), membersJson = Json.encodeToString(members)))
                done()
            } catch (e: Exception) { error = e.message ?: "Unable to save group" }
            finally { saving = false }
        }
    }
}
