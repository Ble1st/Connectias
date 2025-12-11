package com.ble1st.connectias.feature.ssh.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.ssh.data.AuthMode
import com.ble1st.connectias.feature.ssh.data.SshProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class TerminalState(
    val output: String = "",
    val isConnected: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SshTerminalViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state

    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var outputStream: OutputStream? = null

    fun connect(profile: SshProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client = SSHClient()
                client?.addHostKeyVerifier(PromiscuousVerifier())
                client?.connect(profile.host, profile.port)

                when (profile.authMode) {
                    AuthMode.PASSWORD -> {
                        client?.authPassword(profile.username, profile.password)
                    }
                    AuthMode.KEY -> {
                        val keyProvider = if (profile.keyPassword.isNullOrEmpty()) {
                            client?.loadKeys(profile.privateKeyPath)
                        } else {
                            client?.loadKeys(profile.privateKeyPath, profile.keyPassword)
                        }
                        client?.authPublickey(profile.username, keyProvider)
                    }
                }

                if (client?.isAuthenticated == true) {
                    session = client?.startSession()
                    session?.allocateDefaultPTY()
                    shell = session?.startShell()
                    outputStream = shell?.outputStream
                    
                    _state.update { it.copy(isConnected = true, output = "Connected to ${profile.host}\n") }
                    
                    startReading(shell?.inputStream)
                } else {
                     _state.update { it.copy(error = "Authentication failed") }
                }

            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun startReading(inputStream: InputStream?) {
        inputStream ?: return
        val buffer = ByteArray(1024)
        try {
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                val text = String(buffer, 0, read)
                _state.update { it.copy(output = it.output + text) }
            }
        } catch (e: Exception) {
            // Connection closed or error
        } finally {
            disconnect()
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write((command + "\n").toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to send command") }
            }
        }
    }

    fun disconnect() {
        try {
            session?.close()
            client?.disconnect()
        } catch (e: Exception) {
            // Ignore
        }
        _state.update { it.copy(isConnected = false, output = it.output + "\nDisconnected.") }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
