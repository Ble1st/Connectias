package com.ble1st.connectias.feature.dnstools.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TextParseException
import org.xbill.DNS.Type
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsToolsRepository @Inject constructor(
    private val dnsHistoryDao: DnsHistoryDao
) {

    val history: Flow<List<DnsHistoryEntity>> = dnsHistoryDao.getAllHistory()

    suspend fun clearHistory() = dnsHistoryDao.clearAll()
    suspend fun deleteHistoryItem(item: DnsHistoryEntity) = dnsHistoryDao.delete(item)

    suspend fun resolveDns(domain: String, type: Int = Type.A): DnsQueryResult = withContext(Dispatchers.IO) {
        val result = try {
            val lookup = Lookup(domain, type)
            lookup.setResolver(SimpleResolver("8.8.8.8"))
            val records = lookup.run()?.toList().orEmpty()
            val mapped = records.map { it.rdataToString() }
            DnsQueryResult(domain = domain, type = Type.string(type), records = mapped)
        } catch (t: TextParseException) {
            Timber.e(t, "Invalid domain: %s", domain)
            DnsQueryResult(domain = domain, type = Type.string(type), records = emptyList(), error = "Invalid domain")
        } catch (t: Exception) {
            Timber.e(t, "DNS lookup failed for %s", domain)
            DnsQueryResult(domain = domain, type = Type.string(type), records = emptyList(), error = t.message)
        }
        
        // Save to history
        saveHistory(
            "$domain (${Type.string(type)})", 
            if (result.error != null) "Error: ${result.error}" else "${result.records.size} records found",
            result.records.joinToString("\n")
        )
        
        return@withContext result
    }

    suspend fun fetchDmarc(domain: String): DnsQueryResult = resolveDns("_dmarc.$domain", Type.TXT)

    private suspend fun saveHistory(query: String, summary: String, full: String) {
        dnsHistoryDao.insert(
            DnsHistoryEntity(
                toolType = ToolType.DNS,
                query = query,
                resultSummary = summary,
                fullResult = full,
                timestamp = System.currentTimeMillis()
            )
        )
    }

}
