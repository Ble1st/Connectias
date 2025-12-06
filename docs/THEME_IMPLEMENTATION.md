# Theme Implementation Documentation

## Overview

This document describes the implementation of the Adeptus Mechanicus theme system in the Connectias application. The implementation enables users to switch between a standard theme and an Adeptus Mechanicus-themed variant, with all UI strings externalized and theme-aware.

**Version:** 1.0.0  
**Date:** 2024  
**Status:** ✅ Fully Implemented

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Theme System Components](#theme-system-components)
3. [String Management System](#string-management-system)
4. [Settings Integration](#settings-integration)
5. [Usage Guide](#usage-guide)
6. [Migration Guide](#migration-guide)
7. [Examples](#examples)
8. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

The theme system is built on three main pillars:

1. **Theme System**: Handles visual appearance (colors, typography, shapes)
2. **String Provider System**: Manages theme-dependent terminology
3. **Settings Integration**: Persists and observes theme preferences

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Settings Screen                          │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  ThemeStyleSelector (Standard / Adeptus Mechanicus)  │  │
│  └─────────────────────────────────────────────────────┘  │
└───────────────────────┬───────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              SettingsViewModel                              │
│  • setThemeStyle(themeStyle: String)                        │
│  • observeThemeStyle() → Flow<String>                      │
└───────────────────────┬───────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│            SettingsRepository                               │
│  • getThemeStyle(): String                                  │
│  • setThemeStyle(themeStyle: String)                        │
│  • observeThemeStyle(): Flow<String>                       │
└───────────────────────┬───────────────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
        ▼                               ▼
┌──────────────────┐          ┌──────────────────┐
│  ConnectiasTheme │          │ ThemeStringProvider│
│  • themeStyle    │          │  • getString()     │
│  • colorScheme   │          │  • mappings       │
│  • typography    │          └───────────────────┘
└──────────────────┘
```

---

## Theme System Components

### 1. ThemeStyle Sealed Class

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/theme/ThemeStyle.kt`

Defines the available theme styles:

```kotlin
sealed class ThemeStyle {
    object Standard : ThemeStyle()
    object AdeptusMechanicus : ThemeStyle()
    
    companion object {
        fun fromString(value: String): ThemeStyle
        fun toString(style: ThemeStyle): String
    }
}
```

### 2. Color Schemes

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/theme/AdeptusMechanicusColorScheme.kt`

Defines Material 3 color schemes for the Adeptus Mechanicus theme:

- **Primary Colors**: Mars Red (#8B0000), Bronze (#CD7F32)
- **Background Colors**: Dark metallic backgrounds
- **Status Colors**: Warning Red (#FF4500), etc.

### 3. Typography

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/theme/AdeptusMechanicusTypography.kt`

Uses custom fonts:
- **Orbitron**: Headlines, titles
- **Rajdhani**: Body text, labels

**Font Files Required:**
- `common/src/main/res/font/orbitron.xml`
- `common/src/main/res/font/rajdhani.xml`

### 4. Theme Composition

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/theme/Theme.kt`

The main `ConnectiasTheme` composable accepts:

```kotlin
@Composable
fun ConnectiasTheme(
    themePreference: String = "system",  // "light", "dark", "system"
    themeStyle: ThemeStyle = ThemeStyle.Standard,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
)
```

**Features:**
- Animated theme transitions using `AnimatedContent`
- Automatic color scheme selection based on `themeStyle`
- Typography selection based on `themeStyle`
- CompositionLocal for theme style access

---

## String Management System

### Architecture

The string management system enables theme-dependent terminology mapping. For example:
- Standard: "Security Dashboard"
- Adeptus Mechanicus: "Cogitator Security Array"

### Components

#### 1. String Mappings

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/strings/StringMappings.kt`

Central mapping table defining terminology translations:

```kotlin
val STRING_MAPPINGS = mapOf(
    "security_dashboard_title" to mapOf(
        ThemeStyle.Standard to "Security Dashboard",
        ThemeStyle.AdeptusMechanicus to "Cogitator Security Array"
    ),
    // ... more mappings
)
```

#### 2. Theme String Provider

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/strings/ThemeStringProvider.kt`

Provides theme-aware string retrieval:

```kotlin
class ThemeStringProvider(private val themeStyle: ThemeStyle) {
    fun getString(key: String, vararg args: Any): String
    private fun getMappedString(key: String): String?
}
```

**Features:**
- Caching for performance
- Fallback to standard strings if mapping not found
- String formatting support

#### 3. Composition Local

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/strings/LocalThemeStringProvider.kt`

Provides easy access in Compose:

```kotlin
val LocalThemeStringProvider = compositionLocalOf<ThemeStringProvider> {
    error("No ThemeStringProvider provided")
}

@Composable
fun getThemedString(@StringRes resId: Int, vararg args: Any): String
```

### Usage Pattern

```kotlin
// ❌ Wrong: Hardcoded string
Text("Security Dashboard")

// ✅ Correct: Theme-aware string
Text(getThemedString(stringResource(R.string.security_dashboard_title)))
```

---

## Settings Integration

### SettingsRepository

**Location:** `core/src/main/java/com/ble1st/connectias/core/settings/SettingsRepository.kt`

**Methods:**
```kotlin
fun getThemeStyle(): String
fun setThemeStyle(themeStyle: String)
fun observeThemeStyle(): Flow<String>
```

**Storage:** Plain SharedPreferences (non-sensitive)

### SettingsViewModel

**Location:** `feature-settings/src/main/java/com/ble1st/connectias/feature/settings/ui/SettingsViewModel.kt`

**Methods:**
```kotlin
fun setThemeStyle(themeStyle: String)
```

**State:**
```kotlin
data class SettingsUiState(
    val themeStyle: String = "standard",
    // ... other fields
)
```

### SettingsScreen

**Location:** `feature-settings/src/main/java/com/ble1st/connectias/feature/settings/ui/SettingsScreen.kt`

**Component:**
```kotlin
@Composable
fun ThemeStyleSelector(
    currentThemeStyle: String,
    onThemeStyleSelected: (String) -> Unit
)
```

Displays radio buttons for theme style selection.

---

## Usage Guide

### For Fragment Developers

#### 1. Using ObserveThemeSettings Helper

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/theme/ThemeHelper.kt`

The easiest way to integrate theme support:

```kotlin
@Composable
fun MyFragmentContent() {
    ObserveThemeSettings { theme, themeStyle, dynamicColor ->
        ConnectiasTheme(
            themePreference = theme,
            themeStyle = themeStyle,
            dynamicColor = dynamicColor
        ) {
            // Your content here
        }
    }
}
```

**In Fragment:**
```kotlin
override fun onCreateView(...): View {
    return ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MyFragmentContent()
        }
    }
}
```

#### 2. Manual Integration

If you need more control:

```kotlin
@Composable
fun MyFragmentContent() {
    val settingsRepository: SettingsRepository = // inject or get from context
    val theme by settingsRepository.observeTheme().collectAsState(initial = settingsRepository.getTheme())
    val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = settingsRepository.getThemeStyle())
    val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = settingsRepository.getDynamicColor())
    val themeStyle = remember(themeStyleString) { ThemeStyle.fromString(themeStyleString) }
    
    ConnectiasTheme(
        themePreference = theme,
        themeStyle = themeStyle,
        dynamicColor = dynamicColor
    ) {
        // Your content here
    }
}
```

### For Screen Composables

#### Using Theme-Aware Strings

```kotlin
@Composable
fun MyScreen() {
    Column {
        // ✅ Correct: Use getThemedString with stringResource
        Text(
            text = getThemedString(stringResource(R.string.my_screen_title)),
            style = MaterialTheme.typography.headlineMedium
        )
        
        // ✅ Correct: With formatting
        Text(
            text = getThemedString(stringResource(R.string.items_count, itemCount)),
            style = MaterialTheme.typography.bodyMedium
        )
        
        // ❌ Wrong: Hardcoded strings
        Text("My Screen Title")
    }
}
```

#### Accessing Theme Style

```kotlin
@Composable
fun MyComponent() {
    val themeStyle = LocalThemeStyle.current
    
    when (themeStyle) {
        is ThemeStyle.Standard -> {
            // Standard theme specific logic
        }
        is ThemeStyle.AdeptusMechanicus -> {
            // Adeptus Mechanicus specific logic
        }
    }
}
```

---

## Migration Guide

### Migrating Existing Screens

Follow these steps to migrate a screen to use theme-aware strings:

#### Step 1: Add String Resources

Add all hardcoded strings to your module's `strings.xml`:

```xml
<!-- feature-myfeature/src/main/res/values/strings.xml -->
<resources>
    <string name="my_screen_title">My Screen Title</string>
    <string name="my_button_text">Click Me</string>
    <string name="items_count">Items: %1$d</string>
</resources>
```

#### Step 2: Import Required Functions

```kotlin
import androidx.compose.ui.res.stringResource
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.myfeature.R
```

#### Step 3: Replace Hardcoded Strings

**Before:**
```kotlin
@Composable
fun MyScreen() {
    Column {
        Text("My Screen Title")
        Button(onClick = {}) {
            Text("Click Me")
        }
        Text("Items: $count")
    }
}
```

**After:**
```kotlin
@Composable
fun MyScreen() {
    Column {
        Text(getThemedString(stringResource(R.string.my_screen_title)))
        Button(onClick = {}) {
            Text(getThemedString(stringResource(R.string.my_button_text)))
        }
        Text(getThemedString(stringResource(R.string.items_count, count)))
    }
}
```

#### Step 4: Add Theme Mappings (Optional)

If you want Adeptus Mechanicus specific terminology, add mappings:

**Location:** `common/src/main/java/com/ble1st/connectias/common/ui/strings/StringMappings.kt`

```kotlin
val STRING_MAPPINGS = mapOf(
    // ... existing mappings
    "my_screen_title" to mapOf(
        ThemeStyle.Standard to "My Screen Title",
        ThemeStyle.AdeptusMechanicus to "Cogitator Interface"
    ),
    "my_button_text" to mapOf(
        ThemeStyle.Standard to "Click Me",
        ThemeStyle.AdeptusMechanicus to "Initiate Protocol"
    ),
)
```

### Migrating Fragments

#### Step 1: Use ObserveThemeSettings

Replace direct `ConnectiasTheme` usage:

**Before:**
```kotlin
setContent {
    ConnectiasTheme {
        MyScreen()
    }
}
```

**After:**
```kotlin
setContent {
    ObserveThemeSettings { theme, themeStyle, dynamicColor ->
        ConnectiasTheme(
            themePreference = theme,
            themeStyle = themeStyle,
            dynamicColor = dynamicColor
        ) {
            MyScreen()
        }
    }
}
```

---

## Examples

### Complete Screen Example

```kotlin
package com.ble1st.connectias.feature.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.example.R

@Composable
fun ExampleScreen(
    state: ExampleState,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title with theme-aware string
        Text(
            text = getThemedString(stringResource(R.string.example_screen_title)),
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Card with theme-aware content
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = getThemedString(stringResource(R.string.example_description)),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Button with theme-aware text
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(getThemedString(stringResource(R.string.example_action_button)))
                }
            }
        }
        
        // Status with formatting
        if (state.items.isNotEmpty()) {
            Text(
                text = getThemedString(
                    stringResource(R.string.example_items_count, state.items.size)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### Complete Fragment Example

```kotlin
package com.ble1st.connectias.feature.example.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ble1st.connectias.common.ui.theme.ObserveThemeSettings
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExampleFragment : Fragment() {
    
    private val viewModel: ExampleViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ObserveThemeSettings { theme, themeStyle, dynamicColor ->
                    ConnectiasTheme(
                        themePreference = theme,
                        themeStyle = themeStyle,
                        dynamicColor = dynamicColor
                    ) {
                        val uiState by viewModel.uiState.collectAsState()
                        ExampleScreen(
                            state = uiState,
                            onAction = { viewModel.performAction() }
                        )
                    }
                }
            }
        }
    }
}
```

---

## Troubleshooting

### Common Issues

#### 1. Strings Not Updating When Theme Changes

**Problem:** Strings remain in standard theme after switching.

**Solution:**
- Ensure you're using `getThemedString()` instead of `stringResource()` directly
- Verify `ThemeStringProvider` is provided via `CompositionLocal`
- Check that `LocalThemeStyle` is set in `ConnectiasTheme`

#### 2. Theme Not Applying in Fragment

**Problem:** Fragment doesn't reflect theme changes.

**Solution:**
- Ensure you're using `ObserveThemeSettings` or manually observing theme state
- Verify `SettingsRepository.observeThemeStyle()` is being called
- Check that `ConnectiasTheme` receives the correct `themeStyle` parameter

#### 3. Missing String Mappings

**Problem:** Some strings don't have Adeptus Mechanicus variants.

**Solution:**
- Add mappings to `StringMappings.kt`
- System will fallback to standard strings if mapping not found
- This is acceptable for strings that don't need theme-specific terminology

#### 4. Fonts Not Loading

**Problem:** Custom fonts (Orbitron/Rajdhani) not displaying.

**Solution:**
- Verify font files exist in `common/src/main/res/font/`
- Check font XML definitions are correct
- Ensure fonts are referenced in `AdeptusMechanicusTypography.kt`

#### 5. Performance Issues

**Problem:** Theme switching is slow.

**Solution:**
- `ThemeStringProvider` uses caching - should be fast
- `AnimatedContent` in `ConnectiasTheme` adds smooth transitions
- If still slow, check for unnecessary recompositions

---

## Best Practices

### 1. Always Use getThemedString()

```kotlin
// ✅ Good
Text(getThemedString(stringResource(R.string.title)))

// ❌ Bad
Text(stringResource(R.string.title))
Text("Hardcoded String")
```

### 2. Externalize All UI Strings

All user-facing text should be in `strings.xml` files, not hardcoded.

### 3. Use ObserveThemeSettings Helper

For fragments, prefer the helper function over manual observation:

```kotlin
// ✅ Good
ObserveThemeSettings { theme, themeStyle, dynamicColor ->
    ConnectiasTheme(...) { }
}

// ⚠️ Acceptable (if you need more control)
// Manual observation
```

### 4. Add Mappings for Important Terms

Focus on key terminology that enhances the theme experience:
- Dashboard titles
- Status messages
- Action buttons
- Navigation labels

### 5. Test Both Themes

Always test your screens in both:
- Standard theme
- Adeptus Mechanicus theme

---

## File Structure Reference

```
common/
├── src/main/
│   ├── java/com/ble1st/connectias/common/ui/
│   │   ├── theme/
│   │   │   ├── ThemeStyle.kt
│   │   │   ├── Theme.kt
│   │   │   ├── AdeptusMechanicusColors.kt
│   │   │   ├── AdeptusMechanicusColorScheme.kt
│   │   │   ├── AdeptusMechanicusTypography.kt
│   │   │   ├── LocalThemeStyle.kt
│   │   │   └── ThemeHelper.kt
│   │   └── strings/
│   │       ├── StringMappings.kt
│   │       ├── ThemeStringProvider.kt
│   │       └── LocalThemeStringProvider.kt
│   └── res/
│       └── font/
│           ├── orbitron.xml
│           └── rajdhani.xml

core/
└── src/main/java/com/ble1st/connectias/core/settings/
    └── SettingsRepository.kt

feature-settings/
└── src/main/java/com/ble1st/connectias/feature/settings/ui/
    ├── SettingsViewModel.kt
    ├── SettingsScreen.kt
    └── SettingsFragment.kt
```

---

## Migration Checklist

When migrating a screen, ensure:

- [ ] All hardcoded strings moved to `strings.xml`
- [ ] All `Text()` calls use `getThemedString(stringResource(...))`
- [ ] Fragment uses `ObserveThemeSettings` or manual theme observation
- [ ] `ConnectiasTheme` receives `themeStyle` parameter
- [ ] Screen tested in both Standard and Adeptus Mechanicus themes
- [ ] Important terminology added to `StringMappings.kt` (optional)

---

## Future Enhancements

Potential improvements for the theme system:

1. **Additional Themes**: Support for more theme variants
2. **Custom Color Schemes**: User-customizable color palettes
3. **Font Selection**: Allow users to choose fonts
4. **Theme Preview**: Preview theme before applying
5. **Export/Import**: Share theme configurations

---

## References

- [Design Document](./ADEPTUS_MECHANICUS_THEME.md) - Original theme design specification
- [Material 3 Documentation](https://m3.material.io/) - Material Design 3 guidelines
- [Jetpack Compose Theming](https://developer.android.com/jetpack/compose/themes) - Compose theming guide

---

## Changelog

### Version 1.0.0 (2024)
- Initial implementation
- Standard and Adeptus Mechanicus themes
- String management system
- Settings integration
- Complete migration of all screens

---

**Document Maintained By:** Development Team  
**Last Updated:** 2024  
**Status:** ✅ Complete and Production Ready

