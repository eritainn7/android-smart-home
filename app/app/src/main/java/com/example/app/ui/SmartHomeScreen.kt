package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject
import com.example.app.data.DeviceEvents
import com.example.app.data.HomeRepository
import com.example.app.data.SessionStore
import com.example.app.viewmodels.SmartHomeViewModel

@Composable
fun SmartHomeScreen(repository: HomeRepository, session: SessionStore, deviceEvents: DeviceEvents, onLogout: () -> Unit) {
    val vm: SmartHomeViewModel = viewModel(factory = SmartHomeViewModelFactory(repository, session, deviceEvents))
    val state by vm.state.collectAsState()
    var showGroupDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var homieId by remember { mutableStateOf("") }
    var liveTemperature by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(state.liveEvent) {
        val text = state.liveEvent ?: return@LaunchedEffect
        runCatching {
            val root = JSONObject(text)
            if (root.optString("type") == "telemetry") {
                val data = root.getJSONObject("data")
                if (data.optString("property") == "temperature") {
                    val id = data.getString("deviceId")
                    val device = state.devices.firstOrNull { it.id == id }
                    if (device != null) liveTemperature = liveTemperature + (id to data.getString("value"))
                }
            }
        }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Мой дом", style = MaterialTheme.typography.headlineMedium)
                    Text("Управление устройствами", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onLogout) { Text("Выйти") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::dismissMessages) { Text("Закрыть") } }
            state.info?.let { Text(it, color = MaterialTheme.colorScheme.primary); TextButton(onClick = vm::dismissMessages) { Text("Ок") } }
            Text("Группы", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(0.3f, fill = false)) {
                items(state.groups, key = { it.id }) { group ->
                    FilterChip(selected = group.id == state.selectedGroupId, onClick = { vm.selectGroup(group.id) }, label = { Text(group.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showGroupDialog = true }) { Text("Добавить группу") }
                OutlinedButton(onClick = { showDeviceDialog = true }, enabled = state.selectedGroupId != null) { Text("Добавить устройство") }
            }
            Text("Устройства", style = MaterialTheme.typography.titleMedium)
            if (state.isLoading) CircularProgressIndicator()
            if (state.groups.isEmpty() && !state.isLoading) Text("Групп пока нет. Создайте группу, чтобы добавить устройство.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(state.devices, key = { it.id }) { device ->
                    Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(device.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.deleteDevice(device.id) }) { Text("Удалить") }
                            }
                            Text("ID: ${device.homieDeviceId}", style = MaterialTheme.typography.bodySmall)
                            val temperature = (liveTemperature[device.id] ?: device.properties.optString("temperature")).takeIf { it.isNotBlank() }
                            Text(if (temperature != null) "Температура: $temperature °C" else "Ожидаем данные устройства")
                        }
                    }
                }
            }
        }
    }
    if (showGroupDialog) AlertDialog(
        onDismissRequest = { showGroupDialog = false },
        title = { Text("Новая группа") },
        text = { OutlinedTextField(groupName, { groupName = it }, label = { Text("Название") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (groupName.isNotBlank()) { vm.createGroup(groupName); groupName = ""; showGroupDialog = false } }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = { showGroupDialog = false }) { Text("Отмена") } }
    )
    if (showDeviceDialog) AlertDialog(
        onDismissRequest = { showDeviceDialog = false },
        title = { Text("Добавить устройство") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(deviceName, { deviceName = it }, label = { Text("Название") }, singleLine = true)
            OutlinedTextField(homieId, { homieId = it }, label = { Text("Homie device ID") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { if (deviceName.isNotBlank() && homieId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { vm.addDevice(deviceName, homieId); deviceName = ""; homieId = ""; showDeviceDialog = false } }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = { showDeviceDialog = false }) { Text("Отмена") } }
    )
}

private class SmartHomeViewModelFactory(
    private val repository: HomeRepository,
    private val session: SessionStore,
    private val events: DeviceEvents
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = SmartHomeViewModel(repository, session, events) as T
}
