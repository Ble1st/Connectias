package com.ble1st.connectias.storage.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.ble1st.connectias.storage.database.entity.PluginLogEntity
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
public class PluginLogDao_Impl(
  __db: RoomDatabase,
) : PluginLogDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPluginLogEntity: EntityInsertAdapter<PluginLogEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPluginLogEntity = object : EntityInsertAdapter<PluginLogEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `plugin_logs` (`id`,`pluginId`,`timestamp`,`level`,`tag`,`message`,`throwable`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PluginLogEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.pluginId)
        statement.bindLong(3, entity.timestamp)
        statement.bindLong(4, entity.level.toLong())
        val _tmpTag: String? = entity.tag
        if (_tmpTag == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpTag)
        }
        statement.bindText(6, entity.message)
        val _tmpThrowable: String? = entity.throwable
        if (_tmpThrowable == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpThrowable)
        }
      }
    }
  }

  public override suspend fun insert(log: PluginLogEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPluginLogEntity.insert(_connection, log)
  }

  public override suspend fun getLogsForPlugin(pluginId: String, limit: Int): List<PluginLogEntity> {
    val _sql: String = "SELECT * FROM plugin_logs WHERE pluginId = ? ORDER BY timestamp DESC LIMIT ?"
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
        val _columnIndexOfLevel: Int = getColumnIndexOrThrow(_stmt, "level")
        val _columnIndexOfTag: Int = getColumnIndexOrThrow(_stmt, "tag")
        val _columnIndexOfMessage: Int = getColumnIndexOrThrow(_stmt, "message")
        val _columnIndexOfThrowable: Int = getColumnIndexOrThrow(_stmt, "throwable")
        val _result: MutableList<PluginLogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PluginLogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPluginId: String
          _tmpPluginId = _stmt.getText(_columnIndexOfPluginId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpLevel: Int
          _tmpLevel = _stmt.getLong(_columnIndexOfLevel).toInt()
          val _tmpTag: String?
          if (_stmt.isNull(_columnIndexOfTag)) {
            _tmpTag = null
          } else {
            _tmpTag = _stmt.getText(_columnIndexOfTag)
          }
          val _tmpMessage: String
          _tmpMessage = _stmt.getText(_columnIndexOfMessage)
          val _tmpThrowable: String?
          if (_stmt.isNull(_columnIndexOfThrowable)) {
            _tmpThrowable = null
          } else {
            _tmpThrowable = _stmt.getText(_columnIndexOfThrowable)
          }
          _item = PluginLogEntity(_tmpId,_tmpPluginId,_tmpTimestamp,_tmpLevel,_tmpTag,_tmpMessage,_tmpThrowable)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteOldLogs(cutoffTime: Long) {
    val _sql: String = "DELETE FROM plugin_logs WHERE timestamp < ?"
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
