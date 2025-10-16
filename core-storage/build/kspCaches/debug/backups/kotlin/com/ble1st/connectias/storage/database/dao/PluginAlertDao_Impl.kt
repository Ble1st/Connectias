package com.ble1st.connectias.storage.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.ble1st.connectias.storage.database.entity.PluginAlertEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PluginAlertDao_Impl(
  __db: RoomDatabase,
) : PluginAlertDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPluginAlertEntity: EntityInsertAdapter<PluginAlertEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPluginAlertEntity = object : EntityInsertAdapter<PluginAlertEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `plugin_alerts` (`id`,`pluginId`,`timestamp`,`alertType`,`severity`,`message`,`metricsSnapshot`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PluginAlertEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.pluginId)
        statement.bindLong(3, entity.timestamp)
        statement.bindText(4, entity.alertType)
        statement.bindText(5, entity.severity)
        statement.bindText(6, entity.message)
        statement.bindText(7, entity.metricsSnapshot)
      }
    }
  }

  public override suspend fun insert(alert: PluginAlertEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPluginAlertEntity.insert(_connection, alert)
  }

  public override suspend fun getAlertsForPlugin(pluginId: String, limit: Int): List<PluginAlertEntity> {
    val _sql: String = "SELECT * FROM plugin_alerts WHERE pluginId = ? ORDER BY timestamp DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, pluginId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPluginId: Int = getColumnIndexOrThrow(_stmt, "pluginId")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfAlertType: Int = getColumnIndexOrThrow(_stmt, "alertType")
        val _columnIndexOfSeverity: Int = getColumnIndexOrThrow(_stmt, "severity")
        val _columnIndexOfMessage: Int = getColumnIndexOrThrow(_stmt, "message")
        val _columnIndexOfMetricsSnapshot: Int = getColumnIndexOrThrow(_stmt, "metricsSnapshot")
        val _result: MutableList<PluginAlertEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PluginAlertEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPluginId: String
          _tmpPluginId = _stmt.getText(_columnIndexOfPluginId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpAlertType: String
          _tmpAlertType = _stmt.getText(_columnIndexOfAlertType)
          val _tmpSeverity: String
          _tmpSeverity = _stmt.getText(_columnIndexOfSeverity)
          val _tmpMessage: String
          _tmpMessage = _stmt.getText(_columnIndexOfMessage)
          val _tmpMetricsSnapshot: String
          _tmpMetricsSnapshot = _stmt.getText(_columnIndexOfMetricsSnapshot)
          _item = PluginAlertEntity(_tmpId,_tmpPluginId,_tmpTimestamp,_tmpAlertType,_tmpSeverity,_tmpMessage,_tmpMetricsSnapshot)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteOldAlerts(cutoffTime: Long) {
    val _sql: String = "DELETE FROM plugin_alerts WHERE timestamp < ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, cutoffTime)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
