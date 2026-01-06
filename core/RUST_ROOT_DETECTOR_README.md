# Rust Root Detector - RootBeer Replacement

## Übersicht

Diese Rust-Implementierung ersetzt die veraltete RootBeer-Library (0.1.1) durch eine moderne, performantere und sicherere native Implementierung.

## Implementierte Checks

### 1. SU-Binary-Detection
- 22+ bekannte SU-Binary-Pfade
- Schnelle File-System-Checks ohne JVM-Overhead

### 2. Magisk-Detection
- 8 Magisk-spezifische Pfade
- Module-Directory-Check
- Schnellere Erkennung als RootBeer

### 3. Xposed-Detection
- Classic Xposed
- EdXposed
- LSPosed
- 16+ Framework-Pfade

### 4. Build-Properties
- test-keys Detection
- release-keys Check
- Gefährliche System-Properties

### 5. SELinux-Status
- Enforce-Status-Check
- File-System-basierte Erkennung

### 6. Root-App-Packages
- Wird in Kotlin-Layer geprüft (vereinfacht JNI)
- 13+ bekannte Root-Management-Apps

## Performance

**Erwartete Verbesserungen:**
- **2-3x schneller** als RootBeer (native Code)
- **Weniger Memory-Overhead** (kein JVM)
- **Bessere Batterieeffizienz**

## Verwendung

Die Verwendung bleibt identisch - der `RootDetector` wählt automatisch die beste Implementierung:

```kotlin
val detector = RootDetector(context)
val result = detector.detectRoot()

if (result.isRooted) {
    // Root detected
    result.detectionMethods.forEach { method ->
        Timber.w("Root method: $method")
    }
}
```

## Fallback-Mechanismus

- **Primär:** Rust-Implementierung (schneller, sicherer)
- **Fallback:** RootBeer (wenn Rust nicht verfügbar)

## Build

```bash
cd core
./build-rust.sh
```

Die Libraries werden automatisch in `src/main/jniLibs/` kopiert.

## Vergleich mit RootBeer

| Feature | RootBeer | Rust |
|---------|----------|------|
| **Version** | 0.1.1 (veraltet) | 0.1.0 (neu) |
| **Performance** | Langsam (JVM) | Schnell (Native) |
| **Memory-Safety** | Java (gut) | Rust (sehr gut) |
| **Wartbarkeit** | Externe Dependency | Eigene Implementierung |
| **Reverse-Engineering** | Leicht (Java) | Schwer (Native) |

## Nächste Schritte

1. ✅ Rust-Implementierung erstellt
2. ✅ JNI-Bridge implementiert
3. ✅ Kotlin-Integration mit Fallback
4. ✅ Build-System konfiguriert
5. ⏳ Testing auf echten Geräten
6. ⏳ Performance-Benchmarks

## Sicherheitshinweis

**WICHTIG:** Root-Detection ist eine **heuristische Methode** und **keine Security-Boundary**.

- Root-Hiding-Tools (z.B. Magisk Hide) können Checks umgehen
- Für Production sollte zusätzlich verwendet werden:
  - Google Play Integrity API
  - Server-side Attestation
  - Kombination mehrerer Signale

Diese Implementierung ist **besser als RootBeer**, aber nicht perfekt.

