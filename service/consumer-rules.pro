# SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2025 Connectias

# Service Module ProGuard Rules
# These rules are applied to consumers of the :service module

# Keep AIDL interfaces and stubs
-keep interface com.ble1st.connectias.service.logging.ILoggingService { *; }
-keep class com.ble1st.connectias.service.logging.ILoggingService$Stub { *; }
-keep class com.ble1st.connectias.service.logging.ILoggingService$Stub$Proxy { *; }

# Keep Parcelables for AIDL
-keep class com.ble1st.connectias.service.logging.ExternalLogParcel { *; }
-keep class com.ble1st.connectias.service.logging.ExternalLogParcel$* { *; }

# Keep Room entities and DAOs
-keep class com.ble1st.connectias.service.logging.ExternalLogEntity { *; }
-keep interface com.ble1st.connectias.service.logging.ExternalLogDao { *; }
-keep class com.ble1st.connectias.service.logging.ExternalLogDatabase { *; }

# Keep LoggingService (referenced in manifest)
-keep class com.ble1st.connectias.service.logging.LoggingService { *; }

# Keep LoggingServiceProxy (injected via Hilt)
-keep class com.ble1st.connectias.service.logging.LoggingServiceProxy { *; }
