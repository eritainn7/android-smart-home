package com.example.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.data.DeviceEvents
import com.example.app.data.HomeDevice
import com.example.app.data.HomeGroup
import com.example.app.data.HomeRepository
import com.example.app.data.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val groups: List<HomeGroup> = emptyList(),
    val devices: List<HomeDevice> = emptyList(),
    val selectedGroupId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val liveEvent: String? = null
)

class SmartHomeViewModel(
    private val repository: HomeRepository,
    private val session: SessionStore,
    private val deviceEvents: DeviceEvents
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()
    private var liveJob: Job? = null

    fun start() {
        loadGroups()
        val token = session.token() ?: return
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            deviceEvents.connect(com.example.app.BuildConfig.API_BASE_URL, token).collect { event ->
                _state.value = _state.value.copy(liveEvent = event)
                loadDevices()
            }
        }
    }

    fun selectGroup(id: String?) {
        _state.value = _state.value.copy(selectedGroupId = id)
        loadDevices()
    }

    fun loadGroups() = viewModelScope.launch {
        val token = session.token() ?: return@launch
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val groups = repository.groups(token)
            val selected = _state.value.selectedGroupId?.takeIf { id -> groups.any { it.id == id } } ?: groups.firstOrNull()?.id
            _state.value = _state.value.copy(groups = groups, selectedGroupId = selected, isLoading = false)
            loadDevices()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = error.message ?: "Не удалось загрузить группы")
        }
    }

    fun loadDevices() = viewModelScope.launch {
        val token = session.token() ?: return@launch
        try {
            val devices = repository.devices(token, _state.value.selectedGroupId)
            _state.value = _state.value.copy(devices = devices)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(error = error.message ?: "Не удалось загрузить устройства")
        }
    }

    fun createGroup(name: String) = viewModelScope.launch {
        val token = session.token() ?: return@launch
        try {
            val group = repository.createGroup(token, name)
            _state.value = _state.value.copy(info = "Группа добавлена", selectedGroupId = group.id)
            loadGroups()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(error = error.message ?: "Не удалось создать группу")
        }
    }

    fun addDevice(name: String, homieId: String) = viewModelScope.launch {
        val token = session.token() ?: return@launch
        val groupId = _state.value.selectedGroupId ?: run { _state.value = _state.value.copy(error = "Сначала выберите группу"); return@launch }
        try {
            repository.addDevice(token, groupId, name, homieId)
            _state.value = _state.value.copy(info = "Устройство добавлено")
            loadDevices()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(error = error.message ?: "Не удалось добавить устройство")
        }
    }

    fun deleteDevice(id: String) = viewModelScope.launch {
        val token = session.token() ?: return@launch
        try {
            repository.deleteDevice(token, id)
            loadDevices()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(error = error.message ?: "Не удалось удалить устройство")
        }
    }

    fun dismissMessages() { _state.value = _state.value.copy(error = null, info = null) }
    override fun onCleared() { liveJob?.cancel() }
}
