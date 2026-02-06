package com.ble1st.connectias.ui.plugin.declruns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.domain.GetLogsUseCase
import com.ble1st.connectias.core.model.LogEntry
import com.ble1st.connectias.core.model.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeclarativeFlowRunViewModel @Inject constructor(
    getLogsUseCase: GetLogsUseCase
) : ViewModel() {

    private val limit = MutableStateFlow(2000)

    // Raw logs (DB) – we reuse existing use case and filter in-memory.
    private val logs: StateFlow<List<LogEntry>> =
        limit
            .combine(getLogsUseCase(minLevel = LogLevel.DEBUG, limit = 2000)) { l, res ->
                // "limit" here is still applied later, GetLogsUseCase limit kept stable
                res.logs.take(l)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<DeclarativeRunEvent>> =
        logs.map { list ->
            list.asSequence()
                .filter { DeclarativeFlowRunParser.isDeclarativeRelevant(it) }
                .mapNotNull { DeclarativeFlowRunParser.parse(it) }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pluginIds: StateFlow<List<String>> =
        events.map { ev ->
            ev.map { it.pluginId }.distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<DeclarativeRunStats> =
        events.map { ev ->
            val flowRuns = ev.filterIsInstance<DeclarativeRunEvent.FlowRun>()
            val audits = ev.filterIsInstance<DeclarativeRunEvent.AuditEvent>()
            DeclarativeRunStats(
                total = ev.size,
                ok = flowRuns.count { it.ok },
                failed = flowRuns.count { !it.ok },
                rateLimited = audits.count { it.type == "rate_limited" }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeclarativeRunStats(0, 0, 0, 0))

    fun setLimit(newLimit: Int) {
        limit.value = newLimit.coerceIn(200, 5000)
    }
}

