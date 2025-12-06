# Navigation Graph Changes - DVD/CD Modules

**Date:** 2025-12-06
**Author:** Gemini CLI Agent

---

## Overview

Due to ongoing modularization efforts and the removal of DVD/CD-related functionality from the `feature-usb` module, the main application's navigation graph (`app/src/main/res/navigation/nav_graph.xml`) was encountering resource linking errors.

These errors occurred because the navigation destinations `nav_dvd_cd_detail` and `nav_dvd_player` were referencing string resources (e.g., `@string/nav_dvd_cd_detail`) that were either removed or moved to the `feature-dvd` module. When the `app` module attempted to link these resources, it could not find them, leading to a build failure.

## Changes Made

To resolve the build errors and align with the modular architecture, the following fragment declarations have been commented out in `app/src/main/res/navigation/nav_graph.xml`:

*   **`nav_dvd_cd_detail`**: This navigation destination pointed to `DvdCdDetailFragment`, which is responsible for displaying the details of a detected DVD/CD.
*   **`nav_dvd_player`**: This navigation destination pointed to `DvdPlayerFragment`, which is responsible for the actual playback of DVD content.

**Original Code (commented out in `nav_graph.xml`):**

```xml
    <!-- USB Tools -->
    <!--
    <fragment
        android:id="@+id/nav_dvd_cd_detail"
        android:name="com.ble1st.connectias.feature.dvd.ui.DvdCdDetailFragment"
        android:label="@string/nav_dvd_cd_detail" />

    <fragment
        android:id="@+id/nav_dvd_player"
        android:name="com.ble1st.connectias.feature.dvd.ui.DvdPlayerFragment"
        android:label="@string/nav_dvd_player" />
    -->
```

## Implications

*   **Temporary Disabling of DVD/CD Navigation:** With these entries commented out, the main application's navigation graph no longer directly supports navigating to the DVD/CD detail screen or the DVD player screen from the main `app` module.
*   **Build Fix:** This change resolves the `AAPT: error: resource string/... not found` errors, allowing the application to compile successfully.

## Future Work / Re-enabling

To re-enable DVD/CD navigation, the following steps would be necessary:

1.  **Define Navigation within `feature-dvd`:** The `feature-dvd` module should define its own internal navigation graph, or expose its destinations in a way that the main `app` module can access them without relying on shared string resources. This might involve using deep links or NavHost controllers within the `feature-dvd` module itself.
2.  **Remove `@string/` references:** Update the `<fragment>` declarations to use hardcoded labels (e.g., `android:label="DVD/CD Details"`) or context-specific string resources if the goal is to make these accessible from the `app` module directly.
3.  **Ensure all required data is passed:** When navigating to the DVD player, ensure that all necessary arguments (e.g., `UsbDevice`, `devicePath`, and a mechanism to retrieve the `ScsiDriver` session) are correctly bundled and passed to the destination.
4.  **Integrate with main Navigation:** Re-introduce a mechanism (e.g., an `Action` or a dynamic navigation graph inclusion) from the `app` module to the `feature-dvd` module's entry points.
