package com.ble1st.connectias.feature.ssh.data

import android.content.Context
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import timber.log.Timber
import java.io.File
import java.security.PublicKey
import java.util.Base64

/**
 * Host key verifier implementing Trust-On-First-Use (TOFU) security model.
 * 
 * SECURITY WARNING: This implementation accepts host keys on first connection
 * and stores them for future verification. This provides protection against
 * Man-in-the-Middle attacks after the first connection, but the first connection
 * is vulnerable if the connection is intercepted.
 * 
 * For production use, consider:
 * - Showing a dialog to the user on first connection
 * - Allowing users to manually verify and accept host keys
 * - Providing a way to view and manage known hosts
 */
class SimpleHostKeyVerifier(context: Context) : HostKeyVerifier {
    private val knownHostsFile = File(context.filesDir, "known_hosts")
    
    init {
        // Ensure known_hosts file exists
        if (!knownHostsFile.exists()) {
            knownHostsFile.createNewFile()
        }
    }

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val hostKey = "$hostname:$port"
        val keyFingerprint = getKeyFingerprint(key)
        val keyType = KeyType.fromKey(key).toString()
        
        // Read existing known hosts
        val knownHosts = readKnownHosts()
        
        // Check if this host:port combination is already known
        val existingKey = knownHosts[hostKey]
        
        return when {
            existingKey == null -> {
                // First connection - Trust On First Use (TOFU)
                // SECURITY WARNING: First connection is vulnerable to MITM attacks
                Timber.w("First connection to $hostKey - accepting host key (TOFU)")
                Timber.i("Host key fingerprint: $keyFingerprint (type: $keyType)")
                saveKnownHost(hostKey, keyFingerprint, keyType)
                true
            }
            existingKey == keyFingerprint -> {
                // Known host with matching key - safe to proceed
                Timber.d("Verified known host $hostKey")
                true
            }
            else -> {
                // Known host but key changed - potential MITM attack!
                Timber.e("SECURITY WARNING: Host key changed for $hostKey!")
                Timber.e("Expected: $existingKey")
                Timber.e("Received: $keyFingerprint")
                Timber.e("This may indicate a Man-in-the-Middle attack!")
                // Reject connection for security
                false
            }
        }
    }

    override fun findExistingAlgorithms(hostname: String?, port: Int): MutableList<String> {
        if (hostname == null) return mutableListOf()
        
        val hostKey = "$hostname:$port"
        val knownHosts = readKnownHosts()
        
        // Return algorithms if host is known
        return if (knownHosts.containsKey(hostKey)) {
            mutableListOf("ssh-rsa", "ssh-dss", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521", "ssh-ed25519")
        } else {
            mutableListOf()
        }
    }
    
    /**
     * Reads known hosts from file.
     * Format: hostname:port|fingerprint|keyType
     */
    private fun readKnownHosts(): Map<String, String> {
        val knownHosts = mutableMapOf<String, String>()
        
        if (!knownHostsFile.exists() || !knownHostsFile.canRead()) {
            return knownHosts
        }
        
        try {
            knownHostsFile.readLines().forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 2) {
                    knownHosts[parts[0]] = parts[1]
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read known hosts file")
        }
        
        return knownHosts
    }
    
    /**
     * Saves a known host to file.
     */
    private fun saveKnownHost(hostKey: String, fingerprint: String, keyType: String) {
        try {
            knownHostsFile.appendText("$hostKey|$fingerprint|$keyType\n")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save known host")
        }
    }
    
    /**
     * Generates a fingerprint for the public key.
     * Uses SHA-256 hash of the key encoded in Base64.
     */
    private fun getKeyFingerprint(key: PublicKey): String {
        return try {
            val encoded = key.encoded
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(encoded)
            Base64.getEncoder().encodeToString(hash)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate key fingerprint")
            key.encoded.contentToString()
        }
    }
}
