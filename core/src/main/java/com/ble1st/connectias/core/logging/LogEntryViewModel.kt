package com.ble1st.connectias.core.logging

import androidx.lifecycle.ViewModel
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.database.entities.LogEntryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class LogEntryViewModel @Inject constructor(
    systemLogDao: SystemLogDao
) : ViewModel() {

    val logs: Flow<List<LogEntryEntity>> = systemLogDao.getRecentLogs()

}
