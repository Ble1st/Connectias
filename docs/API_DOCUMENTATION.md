# Connectias API Documentation

## Übersicht

Diese Dokumentation beschreibt die wichtigsten APIs und Klassen der Connectias App. Die App verwendet eine modulare Architektur mit Dependency Injection über Hilt.

## Core Module

### Module Discovery

#### `ModuleCatalog`

Zentrale Registrierung aller Module mit Metadaten.

```kotlin
object ModuleCatalog {
    val CORE_MODULES: List<ModuleMetadata>
    val OPTIONAL_MODULES: List<ModuleMetadata>
    val ALL_MODULES: List<ModuleMetadata>
    
    fun findById(id: String): ModuleMetadata?
    fun getByCategory(category: ModuleCategory): List<ModuleMetadata>
    fun getAvailableModules(): List<ModuleMetadata>
}
```

**ModuleMetadata:**
- `id: String` - Eindeutige Modul-ID
- `name: String` - Anzeigename
- `fragmentClassName: String` - Fragment-Klasse für Navigation
- `category: ModuleCategory` - Kategorie (SECURITY, NETWORK, PRIVACY, etc.)
- `isCore: Boolean` - Ob Modul immer aktiv ist

#### `ModuleRegistry`

Runtime-Registrierung und Verwaltung von Modulen.

```kotlin
@Singleton
class ModuleRegistry @Inject constructor() {
    fun registerFromMetadata(metadata: ModuleMetadata, isActive: Boolean)
    fun getActiveModules(): List<Module>
    fun isModuleActive(moduleId: String): Boolean
}
```

## Feature Modules

### Utilities Module

#### `HashProvider`

Hash- und Checksum-Berechnungen.

```kotlin
@Singleton
class HashProvider @Inject constructor() {
    enum class HashAlgorithm {
        MD5, SHA1, SHA256, SHA512
    }
    
    suspend fun calculateTextHash(text: String, algorithm: HashAlgorithm): String?
    suspend fun calculateFileHash(filePath: String, algorithm: HashAlgorithm): String?
    suspend fun verifyTextHash(text: String, expectedHash: String, algorithm: HashAlgorithm): Boolean
    suspend fun verifyFileHash(filePath: String, expectedHash: String, algorithm: HashAlgorithm): Boolean
}
```

#### `EncodingProvider`

Encoding/Decoding-Operationen.

```kotlin
@Singleton
class EncodingProvider @Inject constructor() {
    enum class EncodingType {
        BASE64, BASE32, HEX, URL, HTML_ENTITY, UNICODE
    }
    
    suspend fun encode(text: String, type: EncodingType): String?
    suspend fun decode(text: String, type: EncodingType): String?
}
```

### Security Module

#### `PasswordStrengthProvider`

Passwort-Stärke-Analyse und Generator.

```kotlin
@Singleton
class PasswordStrengthProvider @Inject constructor() {
    suspend fun analyzePassword(password: String): PasswordStrength
    suspend fun generatePassword(length: Int = 16, includeSpecial: Boolean = true): String
}

data class PasswordStrength(
    val score: Int,              // 0-10
    val strength: Strength,      // VERY_WEAK, WEAK, MODERATE, STRONG, VERY_STRONG
    val feedback: List<String>,  // Detailliertes Feedback
    val entropy: Double          // Entropie in Bits
)
```

#### `EncryptionProvider`

Verschlüsselung/Entschlüsselung mit AES-256-GCM.

```kotlin
@Singleton
class EncryptionProvider @Inject constructor() {
    suspend fun encryptText(plaintext: String, password: String): EncryptionResult
    suspend fun decryptText(encryptedData: String, iv: String, password: String): DecryptionResult
    suspend fun generateKey(): String
}

data class EncryptionResult(
    val encryptedData: String,  // Base64 encoded
    val iv: String,             // Base64 encoded
    val success: Boolean,
    val error: String?
)

data class DecryptionResult(
    val plaintext: String,
    val success: Boolean,
    val error: String?
)
```

### Network Module

#### `DnsLookupProvider`

DNS-Lookup und Diagnostik.

```kotlin
@Singleton
class DnsLookupProvider @Inject constructor() {
    enum class RecordType(val dnsType: Int) {
        A, AAAA, MX, TXT, CNAME, NS, PTR
    }
    
    suspend fun lookup(domain: String, recordType: RecordType, dnsServer: String? = null): List<DnsRecord>
    suspend fun reverseLookup(ipAddress: String): String?
    suspend fun testDnsServer(dnsServer: String): Long  // Response time in ms
}

data class DnsRecord(
    val name: String,
    val type: RecordType,
    val value: String,
    val ttl: Int
)
```

#### `PortScanner`

Port-Scanning für Netzwerkgeräte.

```kotlin
@Singleton
class PortScanner @Inject constructor() {
    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeout: Int = 1000
    ): List<PortScanResult>
}

data class PortScanResult(
    val port: Int,
    val isOpen: Boolean,
    val service: String?,
    val responseTime: Long
)
```

### Privacy Module

#### `TrackerDetectionProvider`

Tracker-Erkennung in Apps.

```kotlin
@Singleton
class TrackerDetectionProvider @Inject constructor() {
    suspend fun detectTrackers(): List<TrackerInfo>
    suspend fun scanApp(packageName: String): List<TrackerInfo>
}

data class TrackerInfo(
    val packageName: String,
    val appName: String,
    val trackers: List<Tracker>,
    val riskLevel: RiskLevel
)

data class Tracker(
    val name: String,
    val category: TrackerCategory,
    val domains: List<String>
)
```

#### `PermissionsAnalyzerProvider`

Erweiterte Berechtigungsanalyse.

```kotlin
@Singleton
class PermissionsAnalyzerProvider @Inject constructor() {
    suspend fun analyzePermissions(): List<RiskyPermission>
    suspend fun getAppPermissions(packageName: String): List<PermissionInfo>
}

data class RiskyPermission(
    val packageName: String,
    val appName: String,
    val permissions: List<PermissionInfo>,
    val riskLevel: RiskLevel,
    val reasons: List<String>
)
```

## Error Handling

### `NetworkResult<T>`

Type-safe Error-Handling für Netzwerk-Operationen.

```kotlin
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(
        val message: String,
        val errorType: ErrorType
    ) : NetworkResult<Nothing>()
}

enum class ErrorType {
    PermissionDenied,
    NetworkError,
    ConfigurationUnavailable,
    Timeout,
    Unknown
}
```

## Dependency Injection

Alle Provider werden über Hilt injiziert. Jedes Feature-Modul hat ein eigenes Hilt-Modul:

- `UtilitiesModule` - Utilities Dependencies
- `BackupModule` - Backup Dependencies
- `NetworkModule` - Network Dependencies (falls vorhanden)
- etc.

## ViewModels

Alle ViewModels folgen dem MVVM-Pattern:

```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val provider: FeatureProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun performAction() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = provider.performOperation()
            _uiState.value = UiState.Success(result)
        }
    }
}
```

## Testing

### Unit Tests

Unit Tests für Provider verwenden `kotlinx.coroutines.test.runTest`:

```kotlin
@Test
fun `test operation`() = runTest {
    val provider = FeatureProvider()
    val result = provider.performOperation()
    assertNotNull(result)
}
```

### Integration Tests

Integration Tests für Module-Discovery und Navigation:

```kotlin
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @Test
    fun testModuleCatalogContainsAllModules() {
        val allModules = ModuleCatalog.ALL_MODULES
        assertTrue(allModules.isNotEmpty())
    }
}
```

## Best Practices

1. **Coroutines**: Alle suspend-Funktionen verwenden `Dispatchers.IO` oder `Dispatchers.Default`
2. **Error Handling**: Verwende `NetworkResult<T>` für type-safe Error-Handling
3. **Logging**: Verwende Timber für Logging
4. **Dependency Injection**: Alle Provider sind `@Singleton` und werden über Hilt injiziert
5. **Testing**: Schreibe Unit Tests für alle Provider-Logik

## Weitere Informationen

- [Architektur Plan](ARCHITECTURE_PLAN.md)
- [User Guide](USER_GUIDE.md)
- [Material 3 Expressive Implementation](MATERIAL3_EXPRESSIVE_IMPLEMENTATION.md)

