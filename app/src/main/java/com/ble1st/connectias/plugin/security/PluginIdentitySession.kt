@file:Suppress("unused") // Security Layer - Session-based identity verification, prevents pluginId spoofing

package com.ble1st.connectias.plugin.security

import android.os.Binder
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Plugin Identity Session Manager
 * 
 * Provides server-side plugin identity verification using session tokens
 * instead of trusting pluginId strings from clients.
 * 
 * SECURITY: Prevents pluginId spoofing by binding identity to Binder session
 */
object PluginIdentitySession {
    
    private val sessionTokenCounter = AtomicLong(1000)
    private val binderToPluginId = ConcurrentHashMap<Int, String>()
    private val pluginIdToSessionToken = ConcurrentHashMap<String, Long>()
    private val sessionTokenToPluginId = ConcurrentHashMap<Long, String>()
    private val registrationLock = ReentrantLock()
    
    /**
     * Registers a plugin with its Binder UID for identity verification
     * Call this when plugin is loaded in sandbox
     * 
     * SECURITY: Uses lock to prevent race conditions during registration
     */
    fun registerPluginSession(pluginId: String, callerUid: Int): Long {
        return registrationLock.withLock {
            val sessionToken = sessionTokenCounter.getAndIncrement()
            
            // Clean up any existing session for this plugin (within lock)
            unregisterPluginSessionUnsafe(pluginId)
            
            binderToPluginId[callerUid] = pluginId
            pluginIdToSessionToken[pluginId] = sessionToken
            sessionTokenToPluginId[sessionToken] = pluginId
            
            Timber.i("[SECURITY] Plugin session registered: $pluginId -> UID:$callerUid, Token:$sessionToken")
            sessionToken
        }
    }

    /**
     * Creates a session token for a plugin without binding it to a Binder UID.
     *
     * This is required when requests originate from an isolated process UID (e.g. sandbox),
     * where Binder.getCallingUid() is not stable per plugin (multiple plugins share one isolated UID).
     *
     * SECURITY: The token becomes the primary identity proof for IPC requests.
     */
    fun createSessionToken(pluginId: String): Long {
        return registrationLock.withLock {
            val sessionToken = sessionTokenCounter.getAndIncrement()

            // Clean up any existing session for this plugin (within lock)
            unregisterPluginSessionUnsafe(pluginId)

            pluginIdToSessionToken[pluginId] = sessionToken
            sessionTokenToPluginId[sessionToken] = pluginId

            Timber.i("[SECURITY] Plugin session token created: $pluginId -> Token:$sessionToken")
            sessionToken
        }
    }
    
    /**
     * Unregisters a plugin session
     */
    fun unregisterPluginSession(pluginId: String) {
        registrationLock.withLock {
            unregisterPluginSessionUnsafe(pluginId)
        }
    }
    
    /**
     * Internal unsafe unregister - must be called within lock
     */
    private fun unregisterPluginSessionUnsafe(pluginId: String) {
        val existingToken = pluginIdToSessionToken.remove(pluginId)
        if (existingToken != null) {
            sessionTokenToPluginId.remove(existingToken)
            
            // Remove by UID (reverse lookup)
            val uidToRemove = binderToPluginId.entries.find { it.value == pluginId }?.key
            if (uidToRemove != null) {
                binderToPluginId.remove(uidToRemove)
            }
            
            Timber.i("[SECURITY] Plugin session unregistered: $pluginId")
        }
    }
    
    /**
     * Verifies plugin identity using Binder.getCallingUid()
     * Returns the actual verified pluginId, or null if verification fails
     */
    fun verifyPluginIdentity(): String? {
        val callerUid = Binder.getCallingUid()
        val verifiedPluginId = binderToPluginId[callerUid]
        
        if (verifiedPluginId == null) {
            Timber.w("[SECURITY] Unknown caller UID: $callerUid - no registered plugin session")
        } else {
            Timber.d("[SECURITY] Verified plugin identity: $verifiedPluginId (UID: $callerUid)")
        }
        
        return verifiedPluginId
    }
    
    /**
     * Validates that claimed pluginId matches caller's verified identity
     * Returns verified pluginId if valid, null if spoofed
     */
    fun validatePluginId(claimedPluginId: String): String? {
        val verifiedPluginId = verifyPluginIdentity()
        
        if (verifiedPluginId == null) {
            Timber.w("[SECURITY] No verified identity for caller - denying access")
            return null
        }
        
        if (claimedPluginId != verifiedPluginId) {
            Timber.e("[SECURITY] SPOOFING ATTEMPT: Caller claims '$claimedPluginId' but verified as '$verifiedPluginId'")
            return null
        }
        
        return verifiedPluginId
    }
    
    /**
     * Gets session token for a plugin
     */
    fun getSessionToken(pluginId: String): Long? {
        return pluginIdToSessionToken[pluginId]
    }
    
    /**
     * Validates session token
     */
    fun validateSessionToken(sessionToken: Long): String? {
        return sessionTokenToPluginId[sessionToken]
    }

    /**
     * Validates that the provided token matches the claimed pluginId.
     * Returns true if valid, false otherwise.
     */
    fun validatePluginToken(claimedPluginId: String, sessionToken: Long): Boolean {
        val actual = validateSessionToken(sessionToken) ?: return false
        return actual == claimedPluginId
    }
    
    /**
     * Debug info
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Plugin Identity Sessions ===")
            appendLine("Registered UIDs: ${binderToPluginId.size}")
            binderToPluginId.forEach { (uid, pluginId) ->
                val token = pluginIdToSessionToken[pluginId]
                appendLine("  UID:$uid -> $pluginId (Token:$token)")
            }
        }
    }
}
