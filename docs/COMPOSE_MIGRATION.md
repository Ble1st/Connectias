# Connectias Jetpack Compose Migration Plan

This document outlines the strategy for migrating the Connectias application UI from XML-based Views to Jetpack Compose.

## Goals
*   Modernize the UI codebase.
*   Improve build speed and developer productivity.
*   Ensure consistency across the application using a shared Design System.
*   Reduce boilerplate code (Adapters, ViewHolders, XML layouts).

## Pre-requisites
*   [x] Enable Compose in `common` module.
*   [x] Enable Compose in `app` module.
*   [x] Create shared `ConnectiasTheme` in `common`.
*   [ ] Enable Compose in all feature modules (`feature-device-info`, `feature-network`, `feature-privacy`, `feature-security`, `feature-utilities`).

## Migration Strategy
1.  **Hybrid Approach:** We will migrate screen by screen. New features should be written in Compose. Existing features will be converted gradually.
2.  **Interop:** Use `ComposeView` in Fragments to host Compose content. Use `AndroidView` in Compose to host legacy Views (if necessary, e.g., for MapViews or complex 3rd party views).
3.  **Theming:** All Compose content must be wrapped in `ConnectiasTheme`.

## Components to Migrate

### Feature: Device Info
| Legacy Component | Target Compose Component | Priority | Status |
| :--- | :--- | :--- | :--- |
| `DeviceInfoFragment` | `DeviceInfoScreen` | High | Pending |
| `BatteryAnalyzerFragment` | `BatteryAnalyzerScreen` | Medium | Pending |
| `ProcessMonitorFragment` | `ProcessMonitorScreen` | Medium | Pending |
| `SensorMonitorFragment` | `SensorMonitorScreen` | Medium | Pending |
| `StorageAnalyzerFragment` | `StorageAnalyzerScreen` | Medium | Pending |
| `item_process.xml` | `ProcessItem` | Medium | Pending |
| `item_sensor.xml` | `SensorItem` | Medium | Pending |
| `item_large_file.xml` | `LargeFileItem` | Medium | Pending |

### Feature: Network
| Legacy Component | Target Compose Component | Priority | Status |
| :--- | :--- | :--- | :--- |
| `NetworkDashboardFragment` | `NetworkDashboardScreen` | High | Pending |
| `WifiAnalyzerFragment` | `WifiAnalyzerScreen` | High | Pending |
| `NetworkMonitorFragment` | `NetworkMonitorScreen` | High | Pending |
| `PortScannerFragment` | `PortScannerScreen` | Medium | Pending |
| `DnsLookupFragment` | `DnsLookupScreen` | Low | Pending |
| `item_wifi_channel.xml` | `WifiChannelItem` | High | Pending |
| `item_port_scan.xml` | `PortScanItem` | Medium | Pending |

### Feature: Privacy
| Legacy Component | Target Compose Component | Priority | Status |
| :--- | :--- | :--- | :--- |
| `PrivacyDashboardFragment` | `PrivacyDashboardScreen` | High | Pending |
| `PermissionsAnalyzerFragment` | `PermissionsAnalyzerScreen` | High | Pending |
| `TrackerDetectionFragment` | `TrackerDetectionScreen` | Medium | Pending |
| `DataLeakageFragment` | `DataLeakageScreen` | Medium | Pending |
| `item_risky_permissions.xml` | `RiskyPermissionItem` | High | Pending |
| `item_tracker.xml` | `TrackerItem` | Medium | Pending |

### Feature: Security
| Legacy Component | Target Compose Component | Priority | Status |
| :--- | :--- | :--- | :--- |
| `SecurityDashboardFragment` | `SecurityDashboardScreen` | High | **In Progress** |
| `EncryptionFragment` | `EncryptionScreen` | Medium | Pending |
| `FirewallAnalyzerFragment` | `FirewallAnalyzerScreen` | Medium | Pending |
| `PasswordStrengthFragment` | `PasswordStrengthScreen` | Low | Pending |
| `CertificateAnalyzerFragment` | `CertificateAnalyzerScreen` | Low | Pending |

### Feature: Utilities
| Legacy Component | Target Compose Component | Priority | Status |
| :--- | :--- | :--- | :--- |
| `UtilitiesDashboardFragment` | `UtilitiesDashboardScreen` | High | Pending |
| `EncodingFragment` | `EncodingScreen` | Low | Pending |
| `HashFragment` | `HashScreen` | Low | Pending |
| `QrCodeFragment` | `QrCodeScreen` | Medium | Pending |
| `ColorFragment` | `ColorScreen` | Low | Pending |
| `TextFragment` | `TextScreen` | Low | Pending |
| `LogFragment` | `LogScreen` | Low | Pending |
| `ApiTesterFragment` | `ApiTesterScreen` | Low | Pending |

## Migration Steps for a Fragment
1.  Enable Compose in the module's `build.gradle.kts`.
2.  Create a new Kotlin file for the screen (e.g., `MyFeatureScreen.kt`).
3.  Create a Composable function `MyFeatureScreen` accepting state and callbacks.
4.  In the Fragment's `onCreateView`, return a `ComposeView`.
5.  Set the content of the `ComposeView` to `ConnectiasTheme { MyFeatureScreen(...) }`.
6.  Remove the XML layout file.
7.  Remove the `ViewBinding` or `findViewById` usage.

## Common Pitfalls
*   **State Management:** Shift from modifying View properties (setText, setVisibility) to declarative state (State<T>, remember).
*   **Navigation:** Continue using Jetpack Navigation with Fragments for now. Compose Navigation can be adopted later once all Fragments are converted.
*   **Theming:** Ensure `ConnectiasTheme` is used to get correct colors and typography.
