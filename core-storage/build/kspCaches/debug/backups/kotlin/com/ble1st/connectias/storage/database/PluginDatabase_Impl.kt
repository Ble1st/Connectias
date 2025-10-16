package com.ble1st.connectias.storage.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.ble1st.connectias.storage.database.dao.PluginAlertDao
import com.ble1st.connectias.storage.database.dao.PluginAlertDao_Impl
import com.ble1st.connectias.storage.database.dao.PluginCrashDao
import com.ble1st.connectias.storage.database.dao.PluginCrashDao_Impl
import com.ble1st.connectias.storage.database.dao.PluginDao
import com.ble1st.connectias.storage.database.dao.PluginDao_Impl
import com.ble1st.connectias.storage.database.dao.PluginLogDao
import com.ble1st.connectias.storage.database.dao.PluginLogDao_Impl
import com.ble1st.connectias.storage.database.dao.PluginPermissionDao
import com.ble1st.connectias.storage.database.dao.PluginPermissionDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PluginDatabase_Impl : PluginDatabase() {
  private val _pluginDao: Lazy<PluginDao> = lazy {
    PluginDao_Impl(this)
  }

  private val _pluginPermissionDao: Lazy<PluginPermissionDao> = lazy {
    PluginPermissionDao_Impl(this)
  }

  private val _pluginAlertDao: Lazy<PluginAlertDao> = lazy {
    PluginAlertDao_Impl(this)
  }

  private val _pluginLogDao: Lazy<PluginLogDao> = lazy {
    PluginLogDao_Impl(this)
  }

  private val _pluginCrashDao: Lazy<PluginCrashDao> = lazy {
    PluginCrashDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "d4ab989ac0e30cfc5d85f372402baa3d", "7cc0bde8958adfc4c76b2b447c9da178") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `plugins` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `version` TEXT NOT NULL, `author` TEXT NOT NULL, `installPath` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `installedAt` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `plugin_permissions` (`pluginId` TEXT NOT NULL, `permission` TEXT NOT NULL, `granted` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`pluginId`, `permission`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `plugin_alerts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pluginId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `alertType` TEXT NOT NULL, `severity` TEXT NOT NULL, `message` TEXT NOT NULL, `metricsSnapshot` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `plugin_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pluginId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `level` INTEGER NOT NULL, `tag` TEXT, `message` TEXT NOT NULL, `throwable` TEXT)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `plugin_crashes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pluginId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `errorMessage` TEXT NOT NULL, `stackTrace` TEXT NOT NULL, `errorType` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd4ab989ac0e30cfc5d85f372402baa3d')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `plugins`")
        connection.execSQL("DROP TABLE IF EXISTS `plugin_permissions`")
        connection.execSQL("DROP TABLE IF EXISTS `plugin_alerts`")
        connection.execSQL("DROP TABLE IF EXISTS `plugin_logs`")
        connection.execSQL("DROP TABLE IF EXISTS `plugin_crashes`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsPlugins: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlugins.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("version", TableInfo.Column("version", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("author", TableInfo.Column("author", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("installPath", TableInfo.Column("installPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("installedAt", TableInfo.Column("installedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("lastUpdated", TableInfo.Column("lastUpdated", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlugins: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPlugins: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPlugins: TableInfo = TableInfo("plugins", _columnsPlugins, _foreignKeysPlugins, _indicesPlugins)
        val _existingPlugins: TableInfo = read(connection, "plugins")
        if (!_infoPlugins.equals(_existingPlugins)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |plugins(com.ble1st.connectias.storage.database.entity.PluginEntity).
              | Expected:
              |""".trimMargin() + _infoPlugins + """
              |
              | Found:
              |""".trimMargin() + _existingPlugins)
        }
        val _columnsPluginPermissions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPluginPermissions.put("pluginId", TableInfo.Column("pluginId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginPermissions.put("permission", TableInfo.Column("permission", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginPermissions.put("granted", TableInfo.Column("granted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginPermissions.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPluginPermissions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPluginPermissions: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPluginPermissions: TableInfo = TableInfo("plugin_permissions", _columnsPluginPermissions, _foreignKeysPluginPermissions, _indicesPluginPermissions)
        val _existingPluginPermissions: TableInfo = read(connection, "plugin_permissions")
        if (!_infoPluginPermissions.equals(_existingPluginPermissions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |plugin_permissions(com.ble1st.connectias.storage.database.entity.PluginPermissionEntity).
              | Expected:
              |""".trimMargin() + _infoPluginPermissions + """
              |
              | Found:
              |""".trimMargin() + _existingPluginPermissions)
        }
        val _columnsPluginAlerts: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPluginAlerts.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginAlerts.put("pluginId", TableInfo.Column("pluginId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginAlerts.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginAlerts.put("alertType", TableInfo.Column("alertType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginAlerts.put("severity", TableInfo.Column("severity", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginAlerts.put("message", TableInfo.Column("message", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginAlerts.put("metricsSnapshot", TableInfo.Column("metricsSnapshot", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPluginAlerts: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPluginAlerts: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPluginAlerts: TableInfo = TableInfo("plugin_alerts", _columnsPluginAlerts, _foreignKeysPluginAlerts, _indicesPluginAlerts)
        val _existingPluginAlerts: TableInfo = read(connection, "plugin_alerts")
        if (!_infoPluginAlerts.equals(_existingPluginAlerts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |plugin_alerts(com.ble1st.connectias.storage.database.entity.PluginAlertEntity).
              | Expected:
              |""".trimMargin() + _infoPluginAlerts + """
              |
              | Found:
              |""".trimMargin() + _existingPluginAlerts)
        }
        val _columnsPluginLogs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPluginLogs.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginLogs.put("pluginId", TableInfo.Column("pluginId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginLogs.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginLogs.put("level", TableInfo.Column("level", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginLogs.put("tag", TableInfo.Column("tag", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginLogs.put("message", TableInfo.Column("message", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginLogs.put("throwable", TableInfo.Column("throwable", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPluginLogs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPluginLogs: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPluginLogs: TableInfo = TableInfo("plugin_logs", _columnsPluginLogs, _foreignKeysPluginLogs, _indicesPluginLogs)
        val _existingPluginLogs: TableInfo = read(connection, "plugin_logs")
        if (!_infoPluginLogs.equals(_existingPluginLogs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |plugin_logs(com.ble1st.connectias.storage.database.entity.PluginLogEntity).
              | Expected:
              |""".trimMargin() + _infoPluginLogs + """
              |
              | Found:
              |""".trimMargin() + _existingPluginLogs)
        }
        val _columnsPluginCrashes: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPluginCrashes.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginCrashes.put("pluginId", TableInfo.Column("pluginId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginCrashes.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginCrashes.put("errorMessage", TableInfo.Column("errorMessage", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginCrashes.put("stackTrace", TableInfo.Column("stackTrace", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPluginCrashes.put("errorType", TableInfo.Column("errorType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPluginCrashes: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPluginCrashes: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPluginCrashes: TableInfo = TableInfo("plugin_crashes", _columnsPluginCrashes, _foreignKeysPluginCrashes, _indicesPluginCrashes)
        val _existingPluginCrashes: TableInfo = read(connection, "plugin_crashes")
        if (!_infoPluginCrashes.equals(_existingPluginCrashes)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |plugin_crashes(com.ble1st.connectias.storage.database.entity.PluginCrashEntity).
              | Expected:
              |""".trimMargin() + _infoPluginCrashes + """
              |
              | Found:
              |""".trimMargin() + _existingPluginCrashes)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "plugins", "plugin_permissions", "plugin_alerts", "plugin_logs", "plugin_crashes")
  }

  public override fun clearAllTables() {
    super.performClear(false, "plugins", "plugin_permissions", "plugin_alerts", "plugin_logs", "plugin_crashes")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(PluginDao::class, PluginDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PluginPermissionDao::class, PluginPermissionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PluginAlertDao::class, PluginAlertDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PluginLogDao::class, PluginLogDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PluginCrashDao::class, PluginCrashDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun pluginDao(): PluginDao = _pluginDao.value

  public override fun pluginPermissionDao(): PluginPermissionDao = _pluginPermissionDao.value

  public override fun pluginAlertDao(): PluginAlertDao = _pluginAlertDao.value

  public override fun pluginLogDao(): PluginLogDao = _pluginLogDao.value

  public override fun pluginCrashDao(): PluginCrashDao = _pluginCrashDao.value
}
