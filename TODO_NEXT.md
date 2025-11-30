# Next Steps & Improvements for Connectias

Based on the codebase analysis, here are the priority tasks for upcoming development to ensure the project's scalability and robustness.

## 1. WASM Runtime Enhancements (Priority: High)
The current `WasmRuntime` implementation using Chicory is functional but limited to simple numeric types.
- **Problem:** `export.apply()` assumes 0 arguments and simple return values. Real-world plugins need to pass Strings (JSON configurations, results).
- **Action Item:** Implement a Memory Bridge between Kotlin and WASM.
    - Implement helper functions to write Strings into WASM Linear Memory (UTF-8 encoding).
    - Implement helper functions to read Strings from WASM Linear Memory (pointers).
    - Define a standard ABI (Application Binary Interface) for plugins (e.g., `allocate`, `deallocate`).

## 2. UI Consolidation (Priority: Medium)
The project currently mixes classic Android Views (XML, RecyclerView) with Jetpack Compose.
- **Problem:** Increases APK size and maintenance complexity.
- **Action Item:** Commit to **Jetpack Compose** as the primary UI framework.
    - Ensure new features (like the detailed WASM Plugin UI) are built 100% in Compose.
    - Plan a gradual migration of existing Fragments (like `SecurityDashboardFragment`) to Composable functions.

## 3. Test Coverage & Quality Assurance
- **Action Item:** Expand the test suite.
    - **Integration Tests:** Create tests for `RaspManager` to verify that security checks don't trigger false positives on standard emulators/devices.
    - **WASM Tests:** Unit tests for `WasmRuntime` using sample WASM binaries to verify the new Memory Bridge logic.

## 4. CI/CD & Automation
- **Action Item:** Setup a basic CI pipeline.
    - Create a GitHub Actions workflow (or local git hook) that runs `./gradlew test` and `./gradlew lint` before allowing commits/pushes.

---
*Created on Nov 28, 2025*
