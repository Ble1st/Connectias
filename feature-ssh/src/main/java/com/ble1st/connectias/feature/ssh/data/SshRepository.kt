package com.ble1st.connectias.feature.ssh.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshRepository @Inject constructor(
    private val sshDao: SshDao,
    private val secureStorage: SecureStorage
) {

    fun getProfiles(): Flow<List<SshProfile>> = sshDao.getAllProfiles().map { entities ->
        entities.map { entity ->
            SshProfile(
                id = entity.id,
                name = entity.name,
                host = entity.host,
                port = entity.port,
                username = entity.username,
                authMode = entity.authMode,
                password = secureStorage.getPassword(entity.id),
                privateKeyPath = secureStorage.getPrivateKeyPath(entity.id),
                keyPassword = secureStorage.getKeyPassword(entity.id)
            )
        }
    }

    suspend fun saveProfile(profile: SshProfile) {
        val id = if (profile.id.isEmpty()) UUID.randomUUID().toString() else profile.id
        
        val entity = SshProfileEntity(
            id = id,
            name = profile.name,
            host = profile.host,
            port = profile.port,
            username = profile.username,
            authMode = profile.authMode,
            encryptedPassword = null, // Stored in SecureStorage
            encryptedKeyPath = null, // Stored in SecureStorage
            encryptedKeyPassword = null // Stored in SecureStorage
        )
        
        sshDao.insertProfile(entity)
        
        if (!profile.password.isNullOrEmpty()) {
            secureStorage.savePassword(id, profile.password)
        }
        if (!profile.privateKeyPath.isNullOrEmpty()) {
            secureStorage.savePrivateKeyPath(id, profile.privateKeyPath)
        }
        if (!profile.keyPassword.isNullOrEmpty()) {
            secureStorage.saveKeyPassword(id, profile.keyPassword)
        }
    }
    
    suspend fun deleteProfile(profile: SshProfile) {
        sshDao.deleteProfile(
            SshProfileEntity(
                id = profile.id, 
                name = profile.name, 
                host = profile.host, 
                port = profile.port, 
                username = profile.username, 
                authMode = profile.authMode,
                encryptedPassword = null,
                encryptedKeyPath = null,
                encryptedKeyPassword = null
            )
        )
        secureStorage.clearCredentials(profile.id)
    }

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
                    val keyPath = profile.privateKeyPath ?: return@withContext SshConnectionResult(false, "Key path required")
                    val keyFile = File(keyPath)
                    if (!keyFile.exists()) {
                        return@withContext SshConnectionResult(false, "Key file not found: $keyPath")
                    }
                    
                    val keyProvider = if (profile.keyPassword.isNullOrEmpty()) {
                        client.loadKeys(keyPath)
                    } else {
                        client.loadKeys(keyPath, profile.keyPassword)
                    }
                    client.authPublickey(profile.username, keyProvider)
                }
            }
            
            if (client.isAuthenticated) {
                SshConnectionResult(true, "Connection successful")
            } else {
                SshConnectionResult(false, "Authentication failed")
            }
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

