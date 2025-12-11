package com.ble1st.connectias.feature.ssh.data

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshRepository @Inject constructor() {

    private val profiles = mutableListOf<SshProfile>()

    fun saveProfile(profile: SshProfile): List<SshProfile> {
        val existingIndex = profiles.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(profile.copy(id = UUID.randomUUID().toString()))
        }
        return profiles.toList()
    }

    fun getProfiles(): List<SshProfile> = profiles.toList()

    suspend fun testConnection(profile: SshProfile): SshConnectionResult = withContext(Dispatchers.IO) {
        val client = SSHClient()
        return@withContext try {
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connect(profile.host, profile.port)
            when (profile.authMode) {
                AuthMode.PASSWORD -> {
                    val password = profile.password ?: return@withContext SshConnectionResult(false, "Password required")
                    client.authPassword(profile.username, password)
                }
                AuthMode.KEY -> {
                    return@withContext SshConnectionResult(false, "Passkey/Key auth not supported in this build")
                }
            }
            SshConnectionResult(true, "Connection successful")
        } catch (t: Throwable) {
            Timber.e(t, "SSH connection failed")
            SshConnectionResult(false, t.message ?: "SSH error")
        } finally {
            try {
                client.disconnect()
            } catch (_: Throwable) {
            }
        }
    }
}
