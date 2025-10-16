package com.ble1st.connectias.storage.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.ble1st.connectias.storage.database.entity.PluginPermissionEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PluginPermissionDao_Impl(
  __db: RoomDatabase,
) : PluginPermissionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPluginPermissionEntity: EntityInsertAdapter<PluginPermissionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPluginPermissionEntity = object : EntityInsertAdapter<PluginPermissionEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `plugin_permissions` (`pluginId`,`permission`,`granted`,`timestamp`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PluginPermissionEntity) {
        statement.bindText(1, entity.pluginId)
        statement.bindText(2, entity.permission)
        val _tmp: Int = if (entity.granted) 1 else 0
        statement.bindLong(3, _tmp.toLong())
        statement.bindLong(4, entity.timestamp)
      }
    }
  }

  public override suspend fun insert(entity: PluginPermissionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPluginPermissionEntity.insert(_connection, entity)
  }

  public override suspend fun getPermission(pluginId: String, permission: String): PluginPermissionEntity? {
    val _sql: String = "SELECT * FROM plugin_permissions WHERE pluginId = ? AND permission = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, pluginId)
        _argIndex = 2
        _stmt.bindText(_argIndex, permission)
        val _columnIndexOfPluginId: Int = getColumnIndexOrThrow(_stmt, "pluginId")
        val _columnIndexOfPermission: Int = getColumnIndexOrThrow(_stmt, "permission")
        val _columnIndexOfGranted: Int = getColumnIndexOrThrow(_stmt, "granted")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: PluginPermissionEntity?
        if (_stmt.step()) {
          val _tmpPluginId: String
          _tmpPluginId = _stmt.getText(_columnIndexOfPluginId)
          val _tmpPermission: String
          _tmpPermission = _stmt.getText(_columnIndexOfPermission)
          val _tmpGranted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfGranted).toInt()
          _tmpGranted = _tmp != 0
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _result = PluginPermissionEntity(_tmpPluginId,_tmpPermission,_tmpGranted,_tmpTimestamp)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllForPlugin(pluginId: String) {
    val _sql: String = "DELETE FROM plugin_permissions WHERE pluginId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, pluginId)
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
