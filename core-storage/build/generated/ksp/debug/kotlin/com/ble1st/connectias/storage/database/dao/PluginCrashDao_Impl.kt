package com.ble1st.connectias.storage.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.ble1st.connectias.storage.database.entity.PluginCrashEntity
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
public class PluginCrashDao_Impl(
  __db: RoomDatabase,
) : PluginCrashDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPluginCrashEntity: EntityInsertAdapter<PluginCrashEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPluginCrashEntity = object : EntityInsertAdapter<PluginCrashEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `plugin_crashes` (`id`,`pluginId`,`timestamp`,`errorMessage`,`stackTrace`,`errorType`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PluginCrashEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.pluginId)
        statement.bindLong(3, entity.timestamp)
        statement.bindText(4, entity.errorMessage)
        statement.bindText(5, entity.stackTrace)
        statement.bindText(6, entity.errorType)
      }
    }
  }

  public override suspend fun insert(crash: PluginCrashEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPluginCrashEntity.insert(_connection, crash)
  }

  public override suspend fun getCrashesForPlugin(pluginId: String): List<PluginCrashEntity> {
    val _sql: String = "SELECT * FROM plugin_crashes WHERE pluginId = ? ORDER BY timestamp DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, pluginId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPluginId: Int = getColumnIndexOrThrow(_stmt, "pluginId")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfErrorMessage: Int = getColumnIndexOrThrow(_stmt, "errorMessage")
        val _columnIndexOfStackTrace: Int = getColumnIndexOrThrow(_stmt, "stackTrace")
        val _columnIndexOfErrorType: Int = getColumnIndexOrThrow(_stmt, "errorType")
        val _result: MutableList<PluginCrashEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PluginCrashEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPluginId: String
          _tmpPluginId = _stmt.getText(_columnIndexOfPluginId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpErrorMessage: String
          _tmpErrorMessage = _stmt.getText(_columnIndexOfErrorMessage)
          val _tmpStackTrace: String
          _tmpStackTrace = _stmt.getText(_columnIndexOfStackTrace)
          val _tmpErrorType: String
          _tmpErrorType = _stmt.getText(_columnIndexOfErrorType)
          _item = PluginCrashEntity(_tmpId,_tmpPluginId,_tmpTimestamp,_tmpErrorMessage,_tmpStackTrace,_tmpErrorType)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
