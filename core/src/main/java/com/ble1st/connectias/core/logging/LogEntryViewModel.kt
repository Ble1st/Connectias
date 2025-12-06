package com.ble1st.connectias.core.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.database.entities.LogEntryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogEntryViewModel @Inject constructor(
    private val systemLogDao: SystemLogDao
) : ViewModel() {

    val logs: Flow<List<LogEntryEntity>> = systemLogDao.getRecentLogs()

    // You can add more functions here to filter logs, etc.
    fun clearOldLogs() {
        viewModelScope.launch {
            // Example: clear logs older than 7 days
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            systemLogDao.deleteOldLogs(sevenDaysAgo)
        }
    }
}
