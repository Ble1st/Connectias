## Connectias Architecture

### 1. Overview

Connectias is an Android-first Flutter application that provides a unified toolkit for working with:

- USB mass‑storage devices (sticks, HDDs, SSDs, optical drives)
- Video‑DVDs, Audio‑CDs and data discs attached via USB
- Local storage browsing (file explorer)
- Optional networking and utility modules (network scanning, DNS tools, password tools, scanner, notes)

The core design goal is:

- A **Flutter UI** that presents a dashboard and feature modules.
- A **very small Kotlin bridge** that only talks to the Android USB host APIs.
- **Rust libraries** that implement all heavy protocol and parsing logic (SCSI/BOT, filesystems, networking, cryptography, DNS, etc.).

The app intentionally does **not** expose a plugin system in this design; features are built‑in modules.

---

### 2. High‑Level Architecture

#### 2.1 Logical View

- **Presentation layer (Flutter/Dart)**
  - Application shell (navigation, feature flags, theming)
  - Main dashboard screen
  - Feature UIs (File Explorer, DVD player surface, Network tools, DNS tools, Password tools, Scanner, Notes, Settings)

- **Platform bridge layer**
  - **Kotlin USB bridge** (Android only)
    - Uses `UsbManager`, `UsbDevice`, `UsbDeviceConnection`, `UsbInterface`, `UsbEndpoint`
    - Exposes a minimal API to Dart:
      - Enumerate USB devices and basic metadata
      - Request and check user permission
      - Open/close devices
      - Perform bulk transfers (IN/OUT) for selected endpoints

- **Rust core layer**
  - USB / SCSI / block‑device logic
  - Filesystem implementations (NTFS/FAT32/ext4 stub)
  - DVD/CD parsing and navigation (via Rust or Rust + C FFI)
  - Network tools (port scanner, host scanner, ARP, SSL inspection)
  - DNS tools (trust‑dns or equivalent)
  - Password tools (generation and analysis, e.g. ring)

The data always flows through Android’s official USB host APIs; Rust never accesses USB hardware directly. Rust is used for protocol implementation and heavy computation, not for interacting with kernel APIs.

#### 2.2 Runtime / Data Flow (Simplified)

```mermaid
flowchart LR
  subgraph UI[Flutter / Dart]
    Dashboard
    FileExplorer
    DVDPlayerUI
    NetworkUI
    DNSUI
    PasswordUI
    ScannerUI
    SettingsUI
  end

  UI -->|MethodChannel| KotlinUSB[Android USB Bridge (Kotlin)]
  UI -->|dart:ffi| RustCore[Rust Core Libraries]

  KotlinUSB -->|bulkTransfer IN/OUT| RustUSB[USB / SCSI / FS (Rust)]
  RustCore --> RustUSB
  RustCore --> RustNetwork[Network / DNS / Password (Rust)]
  RustCore --> RustDVD[DVD / CD Logic (Rust + optional C FFI)]
```

---

### 3. Modules and Responsibilities

This section describes the conceptual modules. Concrete package names and file paths can be adjusted to match the actual codebase.

#### 3.1 App Shell

**Responsibilities**

- Application startup, dependency wiring at Flutter level.
- Global navigation and routing between feature modules.
- Global error handling (showing error dialogs, snackbars, etc.).
- Managing feature flags (e.g. enabling DVD, network, DNS, password, scanner).
- Providing a shared theme and localization.

**Key concepts**

- Single top‑level `MaterialApp` or `CupertinoApp`.
- A home screen that acts as the main dashboard.
- Per‑feature routes and nested navigators if needed.

#### 3.2 Dashboard Module

**Responsibilities**

- Acts as the **main entry point** for the user.
- Displays:
  - Connected USB devices and optical drives (if available).
  - Entry points for:
    - Storage & Media (File Explorer + DVD/CD)
    - Network & DNS tools
    - Password & security tools
    - Notes
    - Scanner
    - Settings

**Interactions**

- Uses Kotlin USB bridge to list connected devices and their types.
- Navigates into File Explorer or DVD player UI when a device is selected.

#### 3.3 File Explorer Module

**Responsibilities**

- Presents a filesystem‑like view over:
  - Local storage (if required)
  - Mounted USB block devices (NTFS/FAT32/ext4 via Rust)
  - Data DVDs/CDs (via the same block abstraction)
- Supports:
  - Browsing directories
  - Opening files (delegating to viewers / external intents where needed)
  - File operations (copy, move, delete, rename) where allowed
  - Showing file and volume properties

**Implementation notes**

- File and directory trees are retrieved from Rust:
  - Rust exposes a virtual filesystem API (e.g. list directory, read metadata, open file, read/write blocks).
  - Dart calls into Rust using `dart:ffi` (or a bridge like flutter_rust_bridge).
- For USB volumes:
  - Dart asks Rust to enumerate volumes on a given block device.
  - Rust uses SCSI/BOT and filesystem logic to expose a hierarchical view.

#### 3.4 USB Core Module (Rust + Kotlin Bridge)

**Kotlin (Android) responsibilities**

- Discover USB devices using `UsbManager.getDeviceList()`.
- Request permission using `UsbManager.requestPermission()`.
- Open a device, claim the relevant interface, and identify bulk IN/OUT endpoints.
- Provide a minimal API to Dart:
  - `listUsbDevices(): List<UsbDeviceInfo>`
  - `openDevice(deviceId): UsbSessionHandle`
  - `bulkTransferIn(session, length): ByteArray`
  - `bulkTransferOut(session, data): Int`
  - `closeDevice(session)`
- All device access remains on the Android side; Dart never touches Java types directly.

**Rust responsibilities**

- Implement SCSI Bulk‑Only Transport (BOT) over the generic bulk IN/OUT operations that Kotlin exposes.
- Implement SCSI commands for:
  - Device inquiry
  - Read capacity
  - Read(10)/Write(10)
  - Other commands necessary for optical drives and advanced features.
- Wrap SCSI as a **block‑device abstraction** (e.g. traits like `read_block(lba, count)`, `write_block(lba, data)`).
- Communicate with Dart through FFI, using opaque handles for devices/sessions.

#### 3.5 Filesystem Module (Rust)

**Responsibilities**

- Provide filesystem implementations on top of the block‑device abstraction:
  - NTFS reading and writing (primary focus).
  - FAT32 reading/writing.
  - ext4 stub or partial implementation if required.
- Expose a filesystem API to Dart:
  - Enumerate volumes / partitions.
  - List directory contents.
  - Retrieve file metadata.
  - Open files and stream their contents.
  - Perform modification operations where supported (create/delete/rename/copy/move/write).

**Key ideas**

- All filesystem detail stays in Rust for performance and safety.
- From Dart’s perspective, filesystems are opaque and accessed via a thin Dart model or wrapper.

#### 3.6 DVD / CD Module

**Responsibilities**

- Handle:
  - Video DVDs (menus, chapters, audio tracks, subtitles).
  - Audio CDs (track enumeration and playback).
  - Data DVDs/CDs (treated like regular filesystems).

**Implementation options**

- Use C libraries such as `libdvdread`, `libdvdnav`, `libdvdcss` and wrap them in Rust via FFI.
- Alternatively, implement DVD parsing and navigation in pure Rust (long‑term goal; higher effort).

**Integration with Flutter**

- A native playback engine such as LibVLC is used for actual video/audio playback.
- Custom I/O callbacks are implemented so that LibVLC reads its data:
  - Ultimately from the same block‑device abstraction that Rust exposes (for physical DVDs).
  - Or from local files when playing ripped content.
- Flutter controls playback via a platform plugin that forwards play/pause/seek commands to the underlying player.

#### 3.7 Network Tools Module (Rust)

**Responsibilities**

- Provide:
  - Port scanner
  - Host scanner (e.g. discovering hosts in a subnet)
  - ARP inspection
  - SSL/TLS certificate inspection

**Architecture**

- Implemented as Rust libraries that expose synchronous or asynchronous APIs.
- Dart calls into Rust via FFI, passing configuration (target host/ports, timeout, etc.).
- Results are marshalled back to Flutter as structured data models.

#### 3.8 DNS Tools Module (Rust)

**Responsibilities**

- Perform DNS queries and diagnostics.

**Implementation**

- Built on top of a Rust DNS library (for example trust‑dns).
- Exposes methods like:
  - `resolveA(hostname)`, `resolveAAAA`, `resolveMX`, `resolveTXT`, etc.
  - Possibly more advanced diagnostic tools (trace, timing info).

#### 3.9 Password Tools Module (Rust)

**Responsibilities**

- Generate secure passwords according to user parameters (length, character sets, rules).
- Analyse passwords for strength and common weaknesses.

**Implementation**

- Uses a crypto library such as `ring` under the hood (for secure randomness and entropy).
- Exposes simple APIs to Dart for:
  - Password generation.
  - Password strength scoring.

#### 3.10 Scanner Module

**Responsibilities**

- Discover and communicate with network scanners (e.g. via eSCL or similar protocols).
- Provide basic scanning functionality (e.g. scan a page and save as an image or PDF).

**Implementation**

- Lower‑level networking logic can live in Rust for robustness.
- Flutter handles device discovery list UIs and progress/status display.

#### 3.11 Notes (Notepad) Module

**Responsibilities**

- Provide a lightweight note‑taking feature.
- Plain‑text storage with optional categorization.

**Implementation**

- Entirely in Flutter/Dart.
- Local storage via platform‑independent packages (e.g. SQLite, Hive, or shared_preferences depending on needs).

#### 3.12 Settings Module

**Responsibilities**

- Centralized configuration for:
  - Enabling/disabling feature modules (DVD, network, DNS, password, scanner).
  - Tuning timeouts and performance parameters.
  - UI preferences (theme, language).

**Implementation**

- Settings model in Dart, persisted locally.
- Reflects feature flags that the app uses to hide/show certain modules and screens.

---

### 4. Cross‑Cutting Concerns

#### 4.1 Error Handling

- Kotlin bridge:
  - Wraps Android USB errors in domain‑specific error codes/messages.
  - Never throws raw platform exceptions across the method channel; instead returns structured results.
- Rust:
  - Uses `Result` types with rich error enums (e.g. transport error, SCSI error, filesystem error, parse error).
  - Errors are converted into FFI‑safe representations (integers + message strings or tagged enums).
- Flutter:
  - Maps domain errors to user‑friendly messages and retry options.

#### 4.2 Logging

- Each layer logs at its own level:
  - Kotlin: USB events (device attached/detached, permission granted/denied, transfer failures).
  - Rust: protocol‑level details (SCSI commands, network errors, DNS failures, etc.).
  - Flutter: user actions and navigation.
- For production builds, logs are limited and can be routed to a centralized logging solution if needed.

#### 4.3 Security (High‑Level)

- The architecture never bypasses Android’s permission model.
- All USB access goes through `UsbManager` and explicit user consent dialogs.
- Network‑related modules are sandboxed within the app; no privileged operations are assumed.
- Sensitive operations (e.g. password generation, cryptography) are handled in Rust to avoid mistakes in hand‑rolled implementations.

---

### 5. Build, Configuration, and Feature Flags

- Core mobile app is managed by Flutter’s `pubspec.yaml`.
- Android‑specific configuration (Kotlin bridge, native libraries, FFI) is handled in the Android Gradle project.
- Rust code is built into shared libraries (`.so` files) that are:
  - Loaded by the Flutter app via `dart:ffi`.
  - Optionally loaded by Kotlin/Java when necessary (e.g. for native player integration).
- Feature flags:
  - Implemented in Dart (e.g. using a configuration service).
  - Control visibility and availability of modules such as DVD, networking, DNS, password tools, scanner.

---

### 6. Non‑Goals

- No generic third‑party plugin system: all modules are first‑party and tightly integrated.
- No direct kernel or raw USB access from Rust: all hardware access goes through official Android APIs.
- No dependence on Kotlin for non‑USB features: once the device is abstracted as a generic block device or network interface, all heavy logic lives in Rust and is driven by Flutter.

---

### 7. Future Extensions

- Replace C DVD libraries with pure Rust implementations if maturity allows.
- Share core Rust crates between Android and other platforms (Linux desktop, potentially Windows) to reuse protocol and filesystem logic.
- Add more diagnostic modules (e.g. advanced SSL/TLS analysis, traceroute).

