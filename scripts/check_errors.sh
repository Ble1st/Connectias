#!/bin/bash
# Fehlerprüf-Script für Connectias
# Erkennt Fehler in Dart/Flutter, Rust und Android/Kotlin
# Gibt strukturierte Ergebnisse als TXT aus

set -euo pipefail

# Farben für Terminal-Ausgabe (optional, werden nicht in TXT gespeichert)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Projekt-Root-Verzeichnis
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# Timestamp für Ausgabedatei
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
REPORTS_DIR="$PROJECT_ROOT/reports"
OUTPUT_FILE="$REPORTS_DIR/error_report_${TIMESTAMP}.txt"

# Erstelle reports/ Verzeichnis falls nicht vorhanden
mkdir -p "$REPORTS_DIR"

# Fehlerzähler
CRITICAL_ERRORS=0
HIGH_ERRORS=0
MEDIUM_ERRORS=0
LOW_ERRORS=0
WARNINGS=0

# Temporäre Dateien für Zwischenergebnisse
TMP_DIR=$(mktemp -d)
trap "rm -rf $TMP_DIR" EXIT

DART_ANALYZE_OUTPUT="$TMP_DIR/dart_analyze.txt"
DART_TEST_OUTPUT="$TMP_DIR/dart_test.txt"
DART_FORMAT_OUTPUT="$TMP_DIR/dart_format.txt"
RUST_CHECK_OUTPUT="$TMP_DIR/rust_check.txt"
RUST_CLIPPY_OUTPUT="$TMP_DIR/rust_clippy.txt"
RUST_TEST_OUTPUT="$TMP_DIR/rust_test.txt"
RUST_FMT_OUTPUT="$TMP_DIR/rust_fmt.txt"
RUST_AUDIT_OUTPUT="$TMP_DIR/rust_audit.txt"
ANDROID_BUILD_OUTPUT="$TMP_DIR/android_build.txt"
ANDROID_LINT_OUTPUT="$TMP_DIR/android_lint.txt"
ANDROID_TEST_OUTPUT="$TMP_DIR/android_test.txt"

# Hilfsfunktion: Fehler kategorisieren
categorize_error() {
    local error_text="$1"
    if echo "$error_text" | grep -qiE "(critical|fatal|security|vulnerability|exploit|injection|buffer overflow|null pointer|segmentation|memory leak|race condition|deadlock)"; then
        echo "CRITICAL"
    elif echo "$error_text" | grep -qiE "(error|failed|failure|exception|panic|abort|assertion failed)"; then
        echo "HIGH"
    elif echo "$error_text" | grep -qiE "(warning|deprecated|unused|clippy::|lint)"; then
        echo "MEDIUM"
    else
        echo "LOW"
    fi
}

# Hilfsfunktion: Check durchführen und Ergebnisse sammeln
run_check() {
    local name="$1"
    local command="$2"
    local output_file="$3"
    local timeout="${4:-600}" # Default 10 Minuten
    
    echo "[$(date +'%H:%M:%S')] Starte: $name"
    
    if timeout "$timeout" bash -c "$command" > "$output_file" 2>&1; then
        echo "[$(date +'%H:%M:%S')] ✓ Abgeschlossen: $name"
        return 0
    else
        local exit_code=$?
        if [ $exit_code -eq 124 ]; then
            echo "[$(date +'%H:%M:%S')] ⏱ Timeout: $name"
            echo "TIMEOUT nach ${timeout}s" >> "$output_file"
        else
            echo "[$(date +'%H:%M:%S')] ✗ Fehler: $name (Exit-Code: $exit_code)"
        fi
        return $exit_code
    fi
}

# Header für TXT-Report
{
    echo "=================================================================================="
    echo "CONNECTIAS FEHLERPRÜF-REPORT"
    echo "=================================================================================="
    echo ""
    echo "Generiert am: $(date '+%Y-%m-%d %H:%M:%S %Z')"
    echo "Projekt-Verzeichnis: $PROJECT_ROOT"
    echo "Report-Datei: $OUTPUT_FILE"
    echo ""
    echo "=================================================================================="
    echo ""
} > "$OUTPUT_FILE"

echo -e "${BLUE}🔍 Connectias Fehlerprüf-Script${NC}"
echo -e "${BLUE}=================================${NC}"
echo ""
echo "Ausgabedatei: $OUTPUT_FILE"
echo ""

# ============================================================================
# PHASE 1: Dart/Flutter Checks
# ============================================================================

echo -e "${BLUE}📱 Phase 1: Dart/Flutter Checks${NC}"
echo "----------------------------------------"

{
    echo "=================================================================================="
    echo "PHASE 1: DART/FLUTTER CHECKS"
    echo "=================================================================================="
    echo ""
} >> "$OUTPUT_FILE"

# 1.1 Flutter Analyze
if command -v flutter &> /dev/null; then
    if run_check "Flutter Analyze" "flutter analyze" "$DART_ANALYZE_OUTPUT" 300; then
        {
            echo "✓ Flutter Analyze: ERFOLG"
            echo ""
        } >> "$OUTPUT_FILE"
    else
        {
            echo "✗ Flutter Analyze: FEHLER GEFUNDEN"
            echo "--------------------------------------------------------------------------------"
            cat "$DART_ANALYZE_OUTPUT"
            echo ""
        } >> "$OUTPUT_FILE"
        
        # Zähle Fehler
        local error_count=$(grep -ciE "(error|warning|info)" "$DART_ANALYZE_OUTPUT" || echo "0")
        if [ "$error_count" -gt 0 ]; then
            local category=$(categorize_error "$(head -20 "$DART_ANALYZE_OUTPUT")")
            case "$category" in
                CRITICAL) ((CRITICAL_ERRORS+=error_count)) ;;
                HIGH) ((HIGH_ERRORS+=error_count)) ;;
                MEDIUM) ((MEDIUM_ERRORS+=error_count)) ;;
                LOW) ((LOW_ERRORS+=error_count)) ;;
            esac
        fi
    fi
else
    {
        echo "⚠ Flutter Analyze: Flutter nicht gefunden oder nicht im PATH"
        echo ""
    } >> "$OUTPUT_FILE"
    echo -e "${YELLOW}⚠ Flutter nicht gefunden${NC}"
fi

# 1.2 Flutter Tests
if command -v flutter &> /dev/null; then
    if run_check "Flutter Tests" "flutter test" "$DART_TEST_OUTPUT" 600; then
        {
            echo "✓ Flutter Tests: ALLE BESTANDEN"
            echo ""
        } >> "$OUTPUT_FILE"
    else
        {
            echo "✗ Flutter Tests: FEHLER GEFUNDEN"
            echo "--------------------------------------------------------------------------------"
            cat "$DART_TEST_OUTPUT"
            echo ""
        } >> "$OUTPUT_FILE"
        
        local failed_tests=$(grep -ciE "(failed|exception|error)" "$DART_TEST_OUTPUT" || echo "0")
        if [ "$failed_tests" -gt 0 ]; then
            ((HIGH_ERRORS+=failed_tests))
        fi
    fi
else
    {
        echo "⚠ Flutter Tests: Flutter nicht gefunden"
        echo ""
    } >> "$OUTPUT_FILE"
fi

# 1.3 Dart Format Check
if command -v dart &> /dev/null; then
    if run_check "Dart Format Check" "dart format --set-exit-if-changed ." "$DART_FORMAT_OUTPUT" 120; then
        {
            echo "✓ Dart Format: KORREKT"
            echo ""
        } >> "$OUTPUT_FILE"
    else
        {
            echo "✗ Dart Format: FORMATIERUNGSFEHLER"
            echo "--------------------------------------------------------------------------------"
            cat "$DART_FORMAT_OUTPUT"
            echo ""
        } >> "$OUTPUT_FILE"
        ((MEDIUM_ERRORS++))
    fi
else
    {
        echo "⚠ Dart Format: Dart nicht gefunden"
        echo ""
    } >> "$OUTPUT_FILE"
fi

# ============================================================================
# PHASE 2: Rust Checks
# ============================================================================

echo -e "${BLUE}🦀 Phase 2: Rust Checks${NC}"
echo "----------------------------------------"

{
    echo ""
    echo "=================================================================================="
    echo "PHASE 2: RUST CHECKS"
    echo "=================================================================================="
    echo ""
} >> "$OUTPUT_FILE"

# 2.1 Cargo Check
if command -v cargo &> /dev/null; then
    if run_check "Cargo Check" "cargo check --all --message-format=short 2>&1" "$RUST_CHECK_OUTPUT" 600; then
        {
            echo "✓ Cargo Check: KOMPILIERUNG ERFOLGREICH"
            echo ""
        } >> "$OUTPUT_FILE"
    else
        {
            echo "✗ Cargo Check: KOMPILIERUNGSFEHLER"
            echo "--------------------------------------------------------------------------------"
            cat "$RUST_CHECK_OUTPUT"
            echo ""
        } >> "$OUTPUT_FILE"
        
        local compile_errors=$(grep -ciE "(error\[|error:|failed to compile)" "$RUST_CHECK_OUTPUT" || echo "0")
        if [ "$compile_errors" -gt 0 ]; then
            ((HIGH_ERRORS+=compile_errors))
        fi
    fi
else
    {
        echo "⚠ Cargo Check: Rust/Cargo nicht gefunden"
        echo ""
    } >> "$OUTPUT_FILE"
    echo -e "${YELLOW}⚠ Rust/Cargo nicht gefunden${NC}"
fi

# 2.2 Cargo Clippy
if command -v cargo &> /dev/null; then
    if cargo clippy --version &> /dev/null; then
        if run_check "Cargo Clippy" "cargo clippy --all -- -D warnings 2>&1" "$RUST_CLIPPY_OUTPUT" 600; then
            {
                echo "✓ Cargo Clippy: KEINE WARNINGS"
                echo ""
            } >> "$OUTPUT_FILE"
        else
            {
                echo "✗ Cargo Clippy: WARNINGS GEFUNDEN"
                echo "--------------------------------------------------------------------------------"
                cat "$RUST_CLIPPY_OUTPUT"
                echo ""
            } >> "$OUTPUT_FILE"
            
            local clippy_warnings=$(grep -ciE "(warning:|clippy::)" "$RUST_CLIPPY_OUTPUT" || echo "0")
            if [ "$clippy_warnings" -gt 0 ]; then
                ((MEDIUM_ERRORS+=clippy_warnings))
            fi
        fi
    else
        {
            echo "⚠ Cargo Clippy: Nicht installiert (Installation: cargo install clippy)"
            echo ""
        } >> "$OUTPUT_FILE"
    fi
else
    {
        echo "⚠ Cargo Clippy: Rust/Cargo nicht gefunden"
        echo ""
    } >> "$OUTPUT_FILE"
fi

# 2.3 Cargo Test
if command -v cargo &> /dev/null; then
    if run_check "Cargo Tests" "cargo test --all --lib --tests 2>&1" "$RUST_TEST_OUTPUT" 900; then
        {
            echo "✓ Cargo Tests: ALLE BESTANDEN"
            echo ""
        } >> "$OUTPUT_FILE"
    else
        {
            echo "✗ Cargo Tests: FEHLER GEFUNDEN"
            echo "--------------------------------------------------------------------------------"
            cat "$RUST_TEST_OUTPUT"
            echo ""
        } >> "$OUTPUT_FILE"
        
        local test_failures=$(grep -ciE "(test.*FAILED|panicked|assertion failed)" "$RUST_TEST_OUTPUT" || echo "0")
        if [ "$test_failures" -gt 0 ]; then
            ((HIGH_ERRORS+=test_failures))
        fi
    fi
else
    {
        echo "⚠ Cargo Tests: Rust/Cargo nicht gefunden"
        echo ""
    } >> "$OUTPUT_FILE"
fi

# 2.4 Cargo Format Check
if command -v cargo &> /dev/null; then
    if cargo fmt --version &> /dev/null; then
        if run_check "Cargo Format Check" "cargo fmt --all -- --check 2>&1" "$RUST_FMT_OUTPUT" 120; then
            {
                echo "✓ Cargo Format: KORREKT"
                echo ""
            } >> "$OUTPUT_FILE"
        else
            {
                echo "✗ Cargo Format: FORMATIERUNGSFEHLER"
                echo "--------------------------------------------------------------------------------"
                cat "$RUST_FMT_OUTPUT"
                echo ""
            } >> "$OUTPUT_FILE"
            ((MEDIUM_ERRORS++))
        fi
    else
        {
            echo "⚠ Cargo Format: Nicht installiert (Teil von rustfmt)"
            echo ""
        } >> "$OUTPUT_FILE"
    fi
else
    {
        echo "⚠ Cargo Format: Rust/Cargo nicht gefunden"
        echo ""
    } >> "$OUTPUT_FILE"
fi

# 2.5 Cargo Audit (Security Scan)
if command -v cargo-audit &> /dev/null || command -v cargo &> /dev/null && cargo audit --version &> /dev/null 2>&1; then
    if run_check "Cargo Audit" "cargo audit 2>&1" "$RUST_AUDIT_OUTPUT" 300; then
        {
            echo "✓ Cargo Audit: KEINE VULNERABILITIES GEFUNDEN"
            echo ""
        } >> "$OUTPUT_FILE"
    else
        {
            echo "✗ Cargo Audit: VULNERABILITIES GEFUNDEN"
            echo "--------------------------------------------------------------------------------"
            cat "$RUST_AUDIT_OUTPUT"
            echo ""
        } >> "$OUTPUT_FILE"
        
        local vulnerabilities=$(grep -ciE "(vulnerability|CVE-|advisory)" "$RUST_AUDIT_OUTPUT" || echo "0")
        if [ "$vulnerabilities" -gt 0 ]; then
            ((CRITICAL_ERRORS+=vulnerabilities))
        fi
    fi
else
    {
        echo "⚠ Cargo Audit: Nicht installiert (Installation: cargo install cargo-audit)"
        echo ""
    } >> "$OUTPUT_FILE"
fi

# ============================================================================
# PHASE 3: Android/Kotlin Checks
# ============================================================================

echo -e "${BLUE}🤖 Phase 3: Android/Kotlin Checks${NC}"
echo "----------------------------------------"

{
    echo ""
    echo "=================================================================================="
    echo "PHASE 3: ANDROID/KOTLIN CHECKS"
    echo "=================================================================================="
    echo ""
} >> "$OUTPUT_FILE"

# Prüfe ob Android-Verzeichnis existiert
if [ -d "$PROJECT_ROOT/android" ]; then
    cd "$PROJECT_ROOT/android"
    
    # 3.1 Android Build
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew 2>/dev/null || true
        
        if run_check "Android Build" "./gradlew assembleDebug --no-daemon 2>&1" "$ANDROID_BUILD_OUTPUT" 900; then
            {
                echo "✓ Android Build: ERFOLGREICH"
                echo ""
            } >> "$OUTPUT_FILE"
        else
            {
                echo "✗ Android Build: BUILD-FEHLER"
                echo "--------------------------------------------------------------------------------"
                cat "$ANDROID_BUILD_OUTPUT"
                echo ""
            } >> "$OUTPUT_FILE"
            
            local build_errors=$(grep -ciE "(error|failed|FAILURE)" "$ANDROID_BUILD_OUTPUT" || echo "0")
            if [ "$build_errors" -gt 0 ]; then
                ((HIGH_ERRORS+=build_errors))
            fi
        fi
    else
        {
            echo "⚠ Android Build: gradlew nicht gefunden"
            echo ""
        } >> "$OUTPUT_FILE"
    fi
    
    # 3.2 Android Lint
    if [ -f "./gradlew" ]; then
        if run_check "Android Lint" "./gradlew lint --no-daemon 2>&1" "$ANDROID_LINT_OUTPUT" 600; then
            {
                echo "✓ Android Lint: KEINE PROBLEME"
                echo ""
            } >> "$OUTPUT_FILE"
        else
            {
                echo "✗ Android Lint: PROBLEME GEFUNDEN"
                echo "--------------------------------------------------------------------------------"
                cat "$ANDROID_LINT_OUTPUT"
                echo ""
            } >> "$OUTPUT_FILE"
            
            local lint_issues=$(grep -ciE "(error|warning|issue)" "$ANDROID_LINT_OUTPUT" || echo "0")
            if [ "$lint_issues" -gt 0 ]; then
                ((MEDIUM_ERRORS+=lint_issues))
            fi
        fi
    else
        {
            echo "⚠ Android Lint: gradlew nicht gefunden"
            echo ""
        } >> "$OUTPUT_FILE"
    fi
    
    # 3.3 Android Tests
    if [ -f "./gradlew" ]; then
        if run_check "Android Tests" "./gradlew test --no-daemon 2>&1" "$ANDROID_TEST_OUTPUT" 600; then
            {
                echo "✓ Android Tests: ALLE BESTANDEN"
                echo ""
            } >> "$OUTPUT_FILE"
        else
            {
                echo "✗ Android Tests: FEHLER GEFUNDEN"
                echo "--------------------------------------------------------------------------------"
                cat "$ANDROID_TEST_OUTPUT"
                echo ""
            } >> "$OUTPUT_FILE"
            
            local test_failures=$(grep -ciE "(failed|FAILURE|error)" "$ANDROID_TEST_OUTPUT" || echo "0")
            if [ "$test_failures" -gt 0 ]; then
                ((HIGH_ERRORS+=test_failures))
            fi
        fi
    else
        {
            echo "⚠ Android Tests: gradlew nicht gefunden"
            echo ""
        } >> "$OUTPUT_FILE"
    fi
    
    cd "$PROJECT_ROOT"
else
    {
        echo "⚠ Android-Verzeichnis nicht gefunden - Android-Checks übersprungen"
        echo ""
    } >> "$OUTPUT_FILE"
    echo -e "${YELLOW}⚠ Android-Verzeichnis nicht gefunden${NC}"
fi

# ============================================================================
# ZUSAMMENFASSUNG
# ============================================================================

echo ""
echo -e "${BLUE}📊 Zusammenfassung${NC}"
echo "----------------------------------------"

{
    echo ""
    echo "=================================================================================="
    echo "ZUSAMMENFASSUNG"
    echo "=================================================================================="
    echo ""
    echo "Fehler-Kategorisierung:"
    echo "--------------------------------------------------------------------------------"
    echo "CRITICAL (Kritisch):    $CRITICAL_ERRORS"
    echo "HIGH (Hoch):            $HIGH_ERRORS"
    echo "MEDIUM (Mittel):        $MEDIUM_ERRORS"
    echo "LOW (Niedrig):          $LOW_ERRORS"
    echo ""
    echo "GESAMT:                 $((CRITICAL_ERRORS + HIGH_ERRORS + MEDIUM_ERRORS + LOW_ERRORS))"
    echo ""
    
    TOTAL_ERRORS=$((CRITICAL_ERRORS + HIGH_ERRORS + MEDIUM_ERRORS + LOW_ERRORS))
    
    if [ $TOTAL_ERRORS -eq 0 ]; then
        echo "✓ ERGEBNIS: KEINE FEHLER GEFUNDEN"
        echo ""
        echo "Alle Checks erfolgreich abgeschlossen!"
    elif [ $CRITICAL_ERRORS -gt 0 ]; then
        echo "✗ ERGEBNIS: KRITISCHE FEHLER GEFUNDEN"
        echo ""
        echo "⚠ WARNUNG: Es wurden $CRITICAL_ERRORS kritische Fehler gefunden!"
        echo "Diese müssen sofort behoben werden."
    elif [ $HIGH_ERRORS -gt 0 ]; then
        echo "✗ ERGEBNIS: FEHLER GEFUNDEN"
        echo ""
        echo "⚠ WARNUNG: Es wurden $HIGH_ERRORS Fehler gefunden, die behoben werden sollten."
    else
        echo "⚠ ERGEBNIS: WARNINGS UND HINWEISE GEFUNDEN"
        echo ""
        echo "Es wurden $((MEDIUM_ERRORS + LOW_ERRORS)) Warnings/Hinweise gefunden."
        echo "Diese sollten überprüft werden."
    fi
    
    echo ""
    echo "=================================================================================="
    echo "ENDE DES REPORTS"
    echo "=================================================================================="
    echo ""
    echo "Report-Datei: $OUTPUT_FILE"
    echo ""
} >> "$OUTPUT_FILE"

# Zeige Zusammenfassung im Terminal
cat >> "$OUTPUT_FILE" << EOF
Detaillierte Informationen finden Sie in der vollständigen Ausgabedatei.
EOF

echo -e "CRITICAL: ${RED}$CRITICAL_ERRORS${NC}"
echo -e "HIGH:    ${RED}$HIGH_ERRORS${NC}"
echo -e "MEDIUM:  ${YELLOW}$MEDIUM_ERRORS${NC}"
echo -e "LOW:     ${YELLOW}$LOW_ERRORS${NC}"
echo ""
echo -e "${GREEN}✓ Report gespeichert: $OUTPUT_FILE${NC}"
echo ""

# Exit-Code basierend auf Fehlern
TOTAL_ERRORS=$((CRITICAL_ERRORS + HIGH_ERRORS))
if [ $TOTAL_ERRORS -eq 0 ]; then
    exit 0
else
    exit 1
fi

