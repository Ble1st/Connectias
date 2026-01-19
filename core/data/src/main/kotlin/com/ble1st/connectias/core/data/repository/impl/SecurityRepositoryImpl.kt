package com.ble1st.connectias.core.data.repository.impl

import com.ble1st.connectias.core.data.repository.SecurityRepository
import com.ble1st.connectias.core.data.security.SecurityCheckProvider
import com.ble1st.connectias.core.database.dao.SecurityLogDao
import com.ble1st.connectias.core.database.mapper.toEntity
import com.ble1st.connectias.core.database.mapper.toModel
import com.ble1st.connectias.core.model.SecurityCheckResult
import com.ble1st.connectias.core.model.SecurityThreat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SecurityRepository using Room database and security check provider.
 */
@Singleton
class SecurityRepositoryImpl @Inject constructor(
    private val securityLogDao: SecurityLogDao,
    private val securityCheckProvider: SecurityCheckProvider
) : SecurityRepository {

    override suspend fun performSecurityCheck(): SecurityCheckResult {
        return securityCheckProvider.performSecurityCheck()
    }

    override suspend fun logThreat(threat: SecurityThreat) {
        securityLogDao.insert(threat.toEntity())
    }

    override fun getRecentThreats(limit: Int): Flow<List<SecurityThreat>> {
        return securityLogDao.getRecentLogs(limit).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun deleteOldLogs(olderThan: Long) {
        securityLogDao.deleteOldLogs(olderThan)
    }
}
