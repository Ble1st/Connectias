#!/bin/bash
# Projekt-Scanner Script
# Analysiert das Projekt und generiert einen Diagnostik-Report

echo "🔍 Connectias Projekt-Scanner"
echo "=================================="
echo ""

# Finde alle HashMap::new() und Vec::new() ohne Capacity
echo "📊 Phase 1: Collection-Allocation-Analyse"
echo "----------------------------------------"
echo ""

echo "Suche nach HashMap::new() ohne Capacity..."
grep -rn "HashMap::new()" crates/connectias-core/src --include="*.rs" | \
    grep -v "//" | \
    grep -v "test" | \
    head -10 | while read line; do
    echo "  ⚠️  $line"
done

echo ""
echo "Suche nach Vec::new() ohne Capacity..."
grep -rn "Vec::new()" crates/connectias-core/src --include="*.rs" | \
    grep -v "//" | \
    grep -v "test" | \
    head -10 | while read line; do
    echo "  ⚠️  $line"
done

echo ""
echo "Suche nach potentiellen Race-Conditions (std::sync::Mutex in async)..."
grep -rn "std::sync::Mutex" crates/connectias-core/src --include="*.rs" | \
    grep -v "//" | \
    grep -v "test" | while read line; do
    # Prüfe ob in async Funktion
    file=$(echo "$line" | cut -d: -f1)
    line_num=$(echo "$line" | cut -d: -f2)
    if grep -n "async fn\|tokio::spawn\|\.await" "$file" | grep -q "$line_num"; then
        echo "  🔴 POTENTIELLES PROBLEM: $line"
    fi
done

echo ""
echo "Suche nach block_on() in async Context..."
grep -rn "block_on\|\.block_on" crates/connectias-core/src --include="*.rs" | \
    grep -v "//" | \
    grep -v "test" | while read line; do
    echo "  ⚠️  $line"
done

echo ""
echo "✅ Scan abgeschlossen!"
echo ""
echo "💡 Nächste Schritte:"
echo "   - Verwende hashmap_with_capacity!() für gefundene HashMap::new()"
echo "   - Verwende vec_with_capacity!() für gefundene Vec::new()"
echo "   - Ersetze std::sync::Mutex durch tokio::sync::Mutex in async Code"

