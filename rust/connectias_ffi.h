/*
 * Connectias FFI Bridge – C Header
 * 
 * Sichere FFI-Interface zwischen Rust und C/Dart
 * 
 * SICHERHEIT:
 * - Alle Pointer MÜSSEN validiert werden
 * - Alle Strings MÜSSEN mit connectias_free_string() freigegeben werden
 * - Fehler abrufen mit connectias_get_last_error()
 * 
 * Build: cargo build --lib -p connectias_ffi
 */

#ifndef CONNECTIAS_FFI_H
#define CONNECTIAS_FFI_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * FEHLER CODES
 * ========================================================================== */

#define FFI_SUCCESS                     0
#define FFI_ERROR_INVALID_UTF8         -1
#define FFI_ERROR_NULL_POINTER         -2
#define FFI_ERROR_INIT_FAILED          -3
#define FFI_ERROR_PLUGIN_NOT_FOUND     -4
#define FFI_ERROR_EXECUTION_FAILED     -5
#define FFI_ERROR_SECURITY_VIOLATION   -6
#define FFI_ERROR_LOCK_POISONED        -7

/* ============================================================================
 * INITIALISIERUNG & SYSTEM
 * ========================================================================== */

/**
 * Initialisiere die FFI Library
 * MUSS einmalig vor anderen Funktionen aufgerufen werden
 * 
 * @return FFI_SUCCESS oder Fehler-Code
 */
int32_t connectias_init(void);

/**
 * Gib die FFI Bridge Version zurück
 * 
 * @return Versions-String (z.B. "0.1.0")
 */
const char* connectias_version(void);

/**
 * Gib Systeminfo aus (für Debugging)
 * Format: "OS: linux, CPU: x86_64, Arch: x86_64"
 * 
 * @return System-Info String
 */
const char* connectias_get_system_info(void);

/* ============================================================================
 * PLUGIN MANAGEMENT
 * ========================================================================== */

/**
 * Lade ein Plugin aus einer WASM-Datei
 * 
 * @param plugin_path Pfad zum Plugin (WASM-Datei)
 * @return Plugin-ID (muss mit connectias_free_string freigegeben werden)
 *         NULL bei Fehler
 */
const char* connectias_load_plugin(const char* plugin_path);

/**
 * Entlade ein Plugin
 * 
 * @param plugin_id Eindeutige Plugin-ID
 * @return FFI_SUCCESS oder Fehler-Code
 */
int32_t connectias_unload_plugin(const char* plugin_id);

/**
 * Führe ein Plugin aus
 * 
 * @param plugin_id Eindeutige Plugin-ID
 * @param command Command Name
 * @param args_json JSON-Objekt mit Arguments (z.B. "{\"key\": \"value\"}")
 * @param output_json Zeiger auf Output String (muss mit connectias_free_string freigegeben werden)
 * @return FFI_SUCCESS oder Fehler-Code
 */
int32_t connectias_execute_plugin(
    const char* plugin_id,
    const char* command,
    const char* args_json,
    const char** output_json
);

/**
 * Liste alle geladenen Plugins auf
 * 
 * @return JSON-Array mit Plugin-Informationen
 *         (muss mit connectias_free_string freigegeben werden)
 */
const char* connectias_list_plugins(void);

/* ============================================================================
 * SECURITY (RASP - Runtime Application Self-Protection)
 * ========================================================================== */

/**
 * Führe vollständigen RASP-Security Check durch
 * 
 * KRITISCH: Bei Rückgabe > 0 MUSS die App sofort beendet werden!
 * 
 * @return 0 = sicher, > 0 = gefährdet/beendet, < 0 = Fehler
 */
int32_t connectias_rasp_check_environment(void);

/**
 * Prüfe auf Root/Super-User Zugriff
 * 
 * @return 0 = safe, 1 = suspicious, 2 = compromised
 */
int32_t connectias_rasp_check_root(void);

/**
 * Prüfe auf Debugger
 * 
 * @return 0 = safe, 1 = suspicious, 2 = compromised
 */
int32_t connectias_rasp_check_debugger(void);

/**
 * Prüfe auf Emulator/Virtualisierung
 * 
 * @return 0 = safe, 1 = suspicious, 2 = compromised
 */
int32_t connectias_rasp_check_emulator(void);

/**
 * Prüfe auf Tamper/Manipulation
 * 
 * @return 0 = safe, 1 = suspicious, 2 = compromised
 */
int32_t connectias_rasp_check_tamper(void);

/* ============================================================================
 * FEHLERBEHANDLUNG
 * ========================================================================== */

/**
 * Hole den letzten Fehler-String
 * 
 * Der Fehler wird nach dem Abruf gelöscht!
 * 
 * @return Fehler-String (muss mit connectias_free_string freigegeben werden)
 *         NULL wenn kein Fehler
 */
const char* connectias_get_last_error(void);

/**
 * Freigabe eines FFI-Strings
 * 
 * KRITISCH: Nur für Pointer verwenden, die von Connectias FFI generiert wurden!
 * 
 * @param s Zu freigebender String-Pointer
 */
void connectias_free_string(const char* s);

/* ============================================================================
 * MEMORY MANAGEMENT
 * ========================================================================== */

/**
 * Allokiere Memory
 * 
 * @param size Größe in Bytes (max 100MB)
 * @return Pointer oder NULL bei Fehler
 */
void* connectias_malloc(size_t size);

/**
 * Freigabe von Memory
 * 
 * @param ptr Pointer (muss von connectias_malloc kommen)
 * @param size Allokierte Größe
 */
void connectias_free(void* ptr, size_t size);

/**
 * Gib Memory-Statistiken aus
 * 
 * @return Statistik-String
 */
const char* connectias_get_memory_stats(void);

/* ============================================================================
 * HILFSMAKROS FÜR SICHERE NUTZUNG
 * ========================================================================== */

#define CONNECTIAS_CHECK_NULL(ptr) \
    do { \
        if ((ptr) == NULL) { \
            fprintf(stderr, "ERROR: Null pointer in %s:%d\n", __FILE__, __LINE__); \
            return FFI_ERROR_NULL_POINTER; \
        } \
    } while(0)

#define CONNECTIAS_FREE_STRING(str) \
    do { \
        if ((str) != NULL) { \
            connectias_free_string(str); \
            (str) = NULL; \
        } \
    } while(0)

#define CONNECTIAS_GET_ERROR() connectias_get_last_error()

#ifdef __cplusplus
}
#endif

#endif // CONNECTIAS_FFI_H
