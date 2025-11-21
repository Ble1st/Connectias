package com.ble1st.connectias.core.eventbus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventBus @Inject constructor() {
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    suspend fun emit(event: Event) {
        _events.emit(event)
    }

    fun tryEmit(event: Event): Boolean {
        return _events.tryEmit(event)
    }
}

sealed class Event {
    data class SecurityCheckCompleted(val isSecure: Boolean) : Event()
    data class ModuleLoaded(val moduleId: String) : Event()
    data class ModuleUnloaded(val moduleId: String) : Event()
}

