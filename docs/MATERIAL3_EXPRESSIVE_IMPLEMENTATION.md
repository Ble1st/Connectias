# Material 3 Expressive Implementation

## Übersicht

Die Connectias App wurde auf Material 3 Expressive umgestellt, um ein mutigeres, expressiveres Design zu erreichen, das den Richtlinien aus `MODERN_UI_DESIGN_M3.md` entspricht.

## Implementierte Features

### 1. Expressive Color Scheme

**Light Theme:**
- Primary: `#0041C4` (Bold Blue)
- Secondary: `#006874` (Expressive Accent)
- Tertiary: `#7D5260` (Bold Accent)
- Surface Container Layering: 5 Ebenen für expressive Tiefe

**Dark Theme:**
- Angepasste Farbpalette für Dark Mode
- Reduzierte Sättigung bei großen Flächen
- Inverse Surface Tokens für Kontrast

**Semantic Colors:**
- Success: `#4CAF50`
- Warning: `#FF9800`
- Error: `#BA1A1A`
- Info: `#2196F3`

### 2. Expressive Typography

- **Headlines:** 32sp, bold, -0.01 letter spacing
- **Titles:** 22sp, bold
- **Body:** 16sp, 0.15 letter spacing
- Verwendung von Material 3 Type Scale

### 3. Expressive Shape System

- **Small Components:** 12dp corner radius
- **Medium Components:** 16dp corner radius
- **Large Components:** 28dp corner radius (heroische Cards)

### 4. Dynamic Color Support

- Aktiviert für Android 12+ (API 31+)
- Verwendet `ThemeOverlay.Material3.DynamicColors.DayNight`
- Fallback auf statisches Markenschema für ältere Android-Versionen

### 5. Expressive Component Styles

**Buttons:**
- `Widget.Connectias.Button.Expressive`: Bold, 16sp, 24dp padding
- `Widget.Connectias.Button.Tonal.Expressive`: Filled Tonal mit kräftigem Akzent
- Mindesthöhe: 56dp

**Cards:**
- `Widget.Connectias.Card.Expressive`: 16dp radius, 4dp elevation
- `Widget.Connectias.Card.Expressive.Hero`: 28dp radius, 8dp elevation

## Verwendung

### In Layouts

```xml
<!-- Expressive Button -->
<com.google.android.material.button.MaterialButton
    style="@style/Widget.Connectias.Button.Expressive"
    android:text="Action" />

<!-- Expressive Hero Card -->
<com.google.android.material.card.MaterialCardView
    style="@style/Widget.Connectias.Card.Expressive.Hero">
    <!-- Content -->
</com.google.android.material.card.MaterialCardView>

<!-- Expressive Typography -->
<TextView
    style="@style/TextAppearance.Connectias.Expressive.Headline"
    android:text="Bold Headline" />
```

### Surface Container Tokens

Verwende die Surface Container Tokens für expressive Layering:

- `?attr/colorSurfaceContainerHighest` - Für Overlays
- `?attr/colorSurfaceContainerHigh` - Für erhabene Elemente
- `?attr/colorSurfaceContainer` - Standard für Cards
- `?attr/colorSurfaceContainerLow` - Für subtile Elemente
- `?attr/colorSurfaceContainerLowest` - Für Hintergründe

## Design-Prinzipien

1. **Expressive Boldness:** Mutige Farben und Typografie, solange Lesbarkeit gewahrt bleibt
2. **Adaptive Design:** Dynamic Color passt sich dem Gerät an
3. **Surface Layering:** Verwendung von Surface Container Tokens für Tiefe
4. **Accessibility:** Mindestens 4.5:1 Kontrast für Text, 3:1 für Icons

## Nächste Schritte

- [ ] Testen auf verschiedenen Android-Versionen (12+ für Dynamic Color)
- [ ] Dark Mode Testing
- [ ] Accessibility Testing (Kontraste, Screen Reader)
- [ ] Anpassung bestehender Layouts für expressive Styles
- [ ] Animationen mit M3 Easing-Kurven hinzufügen

## Referenzen

- [Material 3 Expressive Guidelines](https://m3.material.io/blog/building-with-m3-expressive)
- [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/)
- [MODERN_UI_DESIGN_M3.md](./MODERN_UI_DESIGN_M3.md)

