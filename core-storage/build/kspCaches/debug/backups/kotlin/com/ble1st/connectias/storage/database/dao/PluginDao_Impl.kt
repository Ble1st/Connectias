package com.ble1st.connectias.storage.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.ble1st.connectias.storage.database.entity.PluginEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class PluginDao_Impl(
  __db: RoomDatabase,
) : PluginDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPluginEntity: EntityInsertAdapter<PluginEntity>

  private val __deleteAdapterOfPluginEntity: EntityDeleteOrUpdateAdapter<PluginEntity>

  private val __updateAdapterOfPluginEntity: EntityDeleteOrUpdateAdapter<PluginEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPluginEntity = object : EntityInsertAdapter<PluginEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `plugins` (`id`,`name`,`version`,`author`,`installPath`,`enabled`,`installedAt`,`lastUpdated`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PluginEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.version)
        statement.bindText(4, entity.author)
        statement.bindText(5, entity.installPath)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        statement.bindLong(7, entity.installedAt)
        statement.bindLong(8, entity.lastUpdated)
      }
    }
    this.__deleteAdapterOfPluginEntity = object : EntityDeleteOrUpdateAdapter<PluginEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `plugins` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PluginEntity) {
        statement.bindText(1, entity.id)
      }
    }
    this.__updateAdapterOfPluginEntity = object : EntityDeleteOrUpdateAdapter<PluginEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `plugins` SET `id` = ?,`name` = ?,`version` = ?,`author` = ?,`installPath` = ?,`enabled` = ?,`installedAt` = ?,`lastUpdated` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PluginEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.version)
        statement.bindText(4, entity.author)
        statement.bindText(5, entity.installPath)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        statement.bindLong(7, entity.installedAt)
        statement.bindLong(8, entity.lastUpdated)
        statement.bindText(9, entity.id)
      }
    }
  }

  public override suspend fun insertPlugin(plugin: PluginEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPluginEntity.insert(_connection, plugin)
  }

  public override suspend fun deletePlugin(plugin: PluginEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfPluginEntity.handle(_connection, plugin)
  }

  public override suspend fun updatePlugin(plugin: PluginEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfPluginEntity.handle(_connection, plugin)
  }

  public override suspend fun getEnabledPlugins(): List<PluginEntity> {
    val _sql: String = "SELECT * FROM plugins WHERE enabled = 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfInstallPath: Int = getColumnIndexOrThrow(_stmt, "installPath")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _columnIndexOfInstalledAt: Int = getColumnIndexOrThrow(_stmt, "installedAt")
        val _columnIndexOfLastUpdated: Int = getColumnIndexOrThrow(_stmt, "lastUpdated")
        val _result: MutableList<PluginEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PluginEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpVersion: String
          _tmpVersion = _stmt.getText(_columnIndexOfVersion)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpInstallPath: String
          _tmpInstallPath = _stmt.getText(_columnIndexOfInstallPath)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpInstalledAt: Long
          _tmpInstalledAt = _stmt.getLong(_columnIndexOfInstalledAt)
          val _tmpLastUpdated: Long
          _tmpLastUpdated = _stmt.getLong(_columnIndexOfLastUpdated)
          _item = PluginEntity(_tmpId,_tmpName,_tmpVersion,_tmpAuthor,_tmpInstallPath,_tmpEnabled,_tmpInstalledAt,_tmpLastUpdated)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllPlugins(): List<PluginEntity> {
    val _sql: String = "SELECT * FROM plugins"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfInstallPath: Int = getColumnIndexOrThrow(_stmt, "installPath")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _columnIndexOfInstalledAt: Int = getColumnIndexOrThrow(_stmt, "installedAt")
        val _columnIndexOfLastUpdated: Int = getColumnIndexOrThrow(_stmt, "lastUpdated")
        val _result: MutableList<PluginEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PluginEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpVersion: String
          _tmpVersion = _stmt.getText(_columnIndexOfVersion)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpInstallPath: String
          _tmpInstallPath = _stmt.getText(_columnIndexOfInstallPath)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpInstalledAt: Long
          _tmpInstalledAt = _stmt.getLong(_columnIndexOfInstalledAt)
          val _tmpLastUpdated: Long
          _tmpLastUpdated = _stmt.getLong(_columnIndexOfLastUpdated)
          _item = PluginEntity(_tmpId,_tmpName,_tmpVersion,_tmpAuthor,_tmpInstallPath,_tmpEnabled,_tmpInstalledAt,_tmpLastUpdated)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPlugin(pluginId: String): PluginEntity? {
    val _sql: String = "SELECT * FROM plugins WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, pluginId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfInstallPath: Int = getColumnIndexOrThrow(_stmt, "installPath")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _columnIndexOfInstalledAt: Int = getColumnIndexOrThrow(_stmt, "installedAt")
        val _columnIndexOfLastUpdated: Int = getColumnIndexOrThrow(_stmt, "lastUpdated")
        val _result: PluginEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpVersion: String
          _tmpVersion = _stmt.getText(_columnIndexOfVersion)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpInstallPath: String
          _tmpInstallPath = _stmt.getText(_columnIndexOfInstallPath)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpInstalledAt: Long
          _tmpInstalledAt = _stmt.getLong(_columnIndexOfInstalledAt)
          val _tmpLastUpdated: Long
          _tmpLastUpdated = _stmt.getLong(_columnIndexOfLastUpdated)
          _result = PluginEntity(_tmpId,_tmpName,_tmpVersion,_tmpAuthor,_tmpInstallPath,_tmpEnabled,_tmpInstalledAt,_tmpLastUpdated)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deletePluginById(pluginId: String) {
    val _sql: String = "DELETE FROM plugins WHERE id = ?"
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
