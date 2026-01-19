# Connectias App – Feature/Service Mapping (Code-Based)

**Source:** Codeanalyse (kein Doc-Input). Mapping: **Modul → Feature/Service → Implementierung (Klassen/Dateien)**.

## 1) App-Entry & Prozessarchitektur
- **Application Init & Prozess-Gating** → `ConnectiasApplication` (Sandbox-Erkennung, Timber, Exception-Handler, Plugin-State) @`app/src/main/java/com/ble1st/connectias/ConnectiasApplication.kt`
- **Main Activity & UI-Init** → `MainActivity` (Splash, Security-Check, Navigation, Module Discovery, Plugin Setup) @`app/src/main/java/com/ble1st/connectias/MainActivity.kt`
- **Security-Block Screen** → `SecurityBlockedActivity` @`app/src/main/java/com/ble1st/connectias/SecurityBlockedActivity.kt`
- **Manifest-Entry Points** → `AndroidManifest.xml` @`app/src/main/AndroidManifest.xml`

## 2) Navigation & UI Screens
- **Navigation Graph** → `nav_graph.xml` @`app/src/main/res/navigation/nav_graph.xml`
- **Dashboard** → `DashboardFragment`, `DashboardScreen` @`app/src/main/java/com/ble1st/connectias/ui/dashboard/DashboardFragment.kt`
- **Settings** → `SettingsFragment`, `SettingsScreen`, `SettingsViewModel` @`feature-settings/src/main/java/com/ble1st/connectias/feature/settings/ui/*`
- **Log Viewer** → `LogViewerFragment`, `LogViewerScreen`, `LogEntryViewModel` @`app/src/main/java/com/ble1st/connectias/ui/logging/*`
- **Plugin Management** → `PluginManagementFragment`, `PluginManagementScreen` @`app/src/main/java/com/ble1st/connectias/ui/plugin/*`
- **Plugin Store** → `PluginStoreFragment`, `PluginStoreScreen` @`app/src/main/java/com/ble1st/connectias/ui/plugin/store/*`
- **Plugin Permission Detail** → `PluginPermissionDetailFragment`, `PluginPermissionDetailScreen` @`app/src/main/java/com/ble1st/connectias/ui/plugin/*`
- **Plugin Security Dashboard** → `PluginSecurityDashboardFragment`, `PluginSecurityDashboard` @`app/src/main/java/com/ble1st/connectias/ui/plugin/security/*`
- **Network Policy Config** → `NetworkPolicyConfigurationFragment`, `NetworkPolicyConfigurationScreen` @`app/src/main/java/com/ble1st/connectias/ui/plugin/security/*`
- **Security Audit Dashboard** → `SecurityAuditDashboardFragment`, `SecurityAuditDashboardScreen` @`app/src/main/java/com/ble1st/connectias/ui/plugin/security/*`
- **Plugin Loading Progress** → `PluginLoadingProgress` @`app/src/main/java/com/ble1st/connectias/ui/plugin/streaming/PluginLoadingProgress.kt`

## 3) Module & Feature Registry
- **Module Catalog (statisch)** → `ModuleCatalog` @`core/src/main/java/com/ble1st/connectias/core/module/ModuleCatalog.kt`
- **Module Registry (runtime)** → `ModuleRegistry` @`core/src/main/java/com/ble1st/connectias/core/module/ModuleRegistry.kt`
- **UI-Integration (Features+Plugins)** → `MainActivity`, `DashboardFragment` @`app/src/main/java/com/ble1st/connectias/MainActivity.kt`, @`app/src/main/java/com/ble1st/connectias/ui/dashboard/DashboardFragment.kt`

## 4) Core Services & Eventing
- **SecurityService** → `SecurityService` @`core/src/main/java/com/ble1st/connectias/core/services/SecurityService.kt`
- **NetworkService/SystemService** → `NetworkService`, `SystemService` @`core/src/main/java/com/ble1st/connectias/core/services/*`
- **Event Bus** → `EventBus` @`core/src/main/java/com/ble1st/connectias/core/eventbus/EventBus.kt`

## 5) Domain UseCases
- **Logs Read** → `GetLogsUseCase` @`core/domain/src/main/kotlin/com/ble1st/connectias/core/domain/GetLogsUseCase.kt`
- **Log Write + Cleanup** → `LogMessageUseCase` @`core/domain/src/main/kotlin/com/ble1st/connectias/core/domain/LogMessageUseCase.kt`
- **Security Status** → `GetSecurityStatusUseCase` @`core/domain/src/main/kotlin/com/ble1st/connectias/core/domain/GetSecurityStatusUseCase.kt`
- **Data Cleanup** → `CleanupOldDataUseCase` @`core/domain/src/main/kotlin/com/ble1st/connectias/core/domain/CleanupOldDataUseCase.kt`

## 6) Data & Logging (Room + SQLCipher)
- **Room Database** → `ConnectiasDatabase` @`core/database/src/main/kotlin/com/ble1st/connectias/core/database/ConnectiasDatabase.kt`
- **DAOs** → `SystemLogDao`, `SecurityLogDao` @`core/database/src/main/kotlin/com/ble1st/connectias/core/database/dao/*`
- **DB Module (SQLCipher, KeyManager)** → `DatabaseModule` @`app/src/main/java/com/ble1st/connectias/di/DatabaseModule.kt`
- **Repositories** → `LogRepository`, `LogRepositoryImpl`, `SecurityRepository` @`core/data/src/main/kotlin/com/ble1st/connectias/core/data/repository/*`
- **Timber DB Logging** → `ConnectiasLoggingTree`, `LoggingTreeEntryPoint` @`core/src/main/java/com/ble1st/connectias/core/logging/*`

## 7) Security (Core)
- **RASP Manager** → `RaspManager` @`core/src/main/java/com/ble1st/connectias/core/security/RaspManager.kt`
- **Detectors** → `RootDetector`, `DebuggerDetector`, `EmulatorDetector`, `TamperDetector` @`core/src/main/java/com/ble1st/connectias/core/security/*`
- **Key Management** → `KeyManager` @`core/src/main/java/com/ble1st/connectias/core/security/KeyManager.kt`
- **SSL Pinning** → `SslPinningManager` @`core/src/main/java/com/ble1st/connectias/core/security/ssl/SslPinningManager.kt`

## 8) Plugin System (Core Lifecycle)
- **Sandboxed Manager** → `PluginManagerSandbox` @`app/src/main/java/com/ble1st/connectias/plugin/PluginManagerSandbox.kt`
- **Sandbox Service (isolated process)** → `PluginSandboxService` @`app/src/main/java/com/ble1st/connectias/core/plugin/PluginSandboxService.kt`
- **Non-sandbox Manager** → `PluginManager` @`app/src/main/java/com/ble1st/connectias/plugin/PluginManager.kt`
- **Plugin Module DI** → `PluginModule` @`app/src/main/java/com/ble1st/connectias/plugin/PluginModule.kt`

## 9) Plugin Permissions
- **Permission Manager** → `PluginPermissionManager` @`app/src/main/java/com/ble1st/connectias/plugin/PluginPermissionManager.kt`
- **Manifest Parser** → `PluginManifestParser` @`app/src/main/java/com/ble1st/connectias/plugin/PluginManifestParser.kt`
- **Permission UI** → `PluginPermissionDetailScreen` @`app/src/main/java/com/ble1st/connectias/ui/plugin/PluginPermissionDetailScreen.kt`

## 10) Plugin Import
- **Import Handler** → `PluginImportHandler` @`app/src/main/java/com/ble1st/connectias/plugin/PluginImportHandler.kt`

## 11) Plugin Store & Updates
- **GitHub Store** → `GitHubPluginStore` @`app/src/main/java/com/ble1st/connectias/plugin/store/GitHubPluginStore.kt`
- **Streaming GitHub Store** → `StreamingGitHubPluginStore` @`app/src/main/java/com/ble1st/connectias/plugin/store/StreamingGitHubPluginStore.kt`
- **Update Worker** → `PluginUpdateWorker` @`app/src/main/java/com/ble1st/connectias/plugin/version/PluginUpdateService.kt`

## 12) Plugin Streaming Subsystem
- **Streaming Manager** → `PluginStreamingManager` @`app/src/main/java/com/ble1st/connectias/plugin/streaming/PluginStreamingManager.kt`
- **Stream Cache** → `StreamCache`, `IStreamCache` @`app/src/main/java/com/ble1st/connectias/plugin/streaming/StreamCache.kt`, @`app/src/main/java/com/ble1st/connectias/plugin/streaming/StreamingModule.kt`
- **Lazy Loader** → `LazyPluginLoader` @`app/src/main/java/com/ble1st/connectias/plugin/streaming/LazyPluginLoader.kt`
- **Mapped Loader** → `MappedPluginLoader` @`app/src/main/java/com/ble1st/connectias/plugin/streaming/MappedPluginLoader.kt`
- **Models & States** → `PluginStreamModels`, `PluginLoadingState` @`app/src/main/java/com/ble1st/connectias/plugin/streaming/*`
- **Streaming DI** → `StreamingModule` @`app/src/main/java/com/ble1st/connectias/plugin/streaming/StreamingModule.kt`

## 13) Plugin Versioning & Rollback
- **Version Manager** → `PluginVersionManager` @`app/src/main/java/com/ble1st/connectias/plugin/version/PluginVersionManager.kt`
- **Rollback Manager** → `PluginRollbackManager` @`app/src/main/java/com/ble1st/connectias/plugin/version/PluginRollbackManager.kt`
- **Versioned Manager** → `VersionedPluginManager` @`app/src/main/java/com/ble1st/connectias/plugin/version/VersionedPluginManager.kt`

## 14) Plugin Security & Audit
- **Zero-Trust Verifier** → `ZeroTrustVerifier` @`app/src/main/java/com/ble1st/connectias/plugin/security/ZeroTrustVerifier.kt`
- **Security Audit Manager** → `SecurityAuditManager` @`app/src/main/java/com/ble1st/connectias/plugin/security/SecurityAuditManager.kt`
- **Enhanced Network Policy** → `EnhancedPluginNetworkPolicy` @`app/src/main/java/com/ble1st/connectias/plugin/security/EnhancedPluginNetworkPolicy.kt`
- **Behavior Analyzer** → `PluginBehaviorAnalyzer` @`app/src/main/java/com/ble1st/connectias/plugin/security/PluginBehaviorAnalyzer.kt`
- **Resource Limiter** → `PluginResourceLimiter` @`app/src/main/java/com/ble1st/connectias/plugin/security/PluginResourceLimiter.kt`
- **Thread Monitor** → `PluginThreadMonitor` @`app/src/main/java/com/ble1st/connectias/plugin/security/PluginThreadMonitor.kt`
- **Security DI** → `SecurityModule` @`app/src/main/java/com/ble1st/connectias/plugin/security/SecurityModule.kt`

## 15) Hardware & File Bridges (Sandbox Isolation)
- **Hardware Bridge (Camera/Net/Printer/Bluetooth)** → `HardwareBridgeService` @`app/src/main/java/com/ble1st/connectias/hardware/HardwareBridgeService.kt`
- **File System Bridge** → `FileSystemBridgeService` @`app/src/main/java/com/ble1st/connectias/core/plugin/FileSystemBridgeService.kt`

---

## 16) Visuelle Architektur (Mermaid)

```mermaid
flowchart LR
    subgraph UI[UI Layer]
        A[MainActivity]
        DASH[Dashboard]
        SET[Settings]
        LOG[Log Viewer]
        PM[Plugin Management]
        PSTORE[Plugin Store]
        PSEC[Plugin Security]
        PPERM[Plugin Permission Detail]
        NPOL[Network Policy Config]
        AUD[Security Audit Dashboard]
    end

    subgraph CORE[Core Layer]
        SRV[SecurityService]
        NWS[NetworkService]
        SYS[SystemService]
        EVT[EventBus]
        LOGTREE[LoggingTree]
        USES[UseCases]
    end

    subgraph DATA[Data Layer]
        DB[(Room + SQLCipher)]
        LOGDAO[SystemLogDao]
        SECD[SecurityLogDao]
        REPO[Repositories]
    end

    subgraph PLUGIN[Plugin System]
        PMS[PluginManagerSandbox]
        PSS[PluginSandboxService]
        PMN[PluginManager (Non-sandbox)]
        PPERMGR[PluginPermissionManager]
        PMAN[PluginManifestParser]
        PIMP[PluginImportHandler]
        STORE[GitHubPluginStore]
        UPD[PluginUpdateWorker]
    end

    subgraph STREAM[Streaming Subsystem]
        PSM[PluginStreamingManager]
        CACHE[StreamCache]
        LAZY[LazyPluginLoader]
        MAP[MappedPluginLoader]
    end

    subgraph SEC[Plugin Security]
        ZTV[ZeroTrustVerifier]
        AUDM[SecurityAuditManager]
        ENP[EnhancedNetworkPolicy]
        BEH[BehaviorAnalyzer]
        RES[ResourceLimiter]
        THR[ThreadMonitor]
    end

    subgraph BRIDGE[Bridges (Main Process)]
        HW[HardwareBridgeService]
        FS[FileSystemBridgeService]
    end

    A --> DASH
    A --> SET
    A --> LOG
    A --> PM
    PM --> PPERM
    PM --> PSEC
    PM --> NPOL
    PM --> AUD
    PM --> PSTORE

    DASH --> PMS
    PM --> PMS
    PSTORE --> STORE
    LOG --> USES
    USES --> REPO --> DB
    LOGTREE --> REPO

    PMS <--> PSS
    PSS --> HW
    PSS --> FS
    PMS --> PPERMGR
    PMS --> ZTV
    PMS --> RES
    PMS --> THR

    STORE --> PMS
    UPD --> PMS
    PSM --> PMS
    PSM --> CACHE
    PSM --> LAZY
    PSM --> MAP

    ZTV --> AUDM
    ENP --> AUDM
    BEH --> AUDM
    RES --> AUDM
    SRV --> AUDM

    CORE --> DATA
    UI --> CORE
    UI --> PLUGIN
    PLUGIN --> SEC
    PLUGIN --> STREAM
    PLUGIN --> BRIDGE
```

**Hinweis:** Dies ist ein codebasiertes Mapping. Keine externen Docs wurden verwendet.
