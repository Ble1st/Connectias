package com.ble1st.connectias.core.eventbus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventBus @Inject constructor() {
    private val _events = MutableSharedFlow<Event>(
        replay = 0,  // explicit: no replay for new collectors
        extraBufferCapacity = 64
        // Note: onBufferOverflow parameter requires BufferOverflow enum which may not be available
        // in all Kotlin Coroutines versions. Using default behavior (SUSPEND).
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    suspend fun emit(event: Event) {
        _events.emit(event)
    }

    fun tryEmit(event: Event): Boolean {
        return _events.tryEmit(event)
    }
}

// Note: Changed from sealed class to open class to allow extension from other modules
// Sealed classes cannot be extended from different modules in Kotlin
open class Event {
    data class SecurityCheckCompleted(val isSecure: Boolean) : Event()
    data class ModuleLoaded(val moduleId: String) : Event()
    data class ModuleUnloaded(val moduleId: String) : Event()
}

