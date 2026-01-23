// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.security

/**
 * Annotation to document which permissions are required for a plugin API method.
 *
 * This is a documentation-only annotation used to make permission requirements
 * clear in the codebase. The actual permission enforcement happens via
 * PermissionPreChecker.
 *
 * Example usage:
 * ```kotlin
 * @RequiresPluginPermission("CAMERA")
 * override fun captureImage(pluginId: String): HardwareResponseParcel {
 *     permissionPreChecker.preCheck(pluginId, "captureImage")
 *     return actualBridge.captureImage(pluginId)
 * }
 * ```
 *
 * @param permissions List of required permissions
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class RequiresPluginPermission(
    vararg val permissions: String
)
