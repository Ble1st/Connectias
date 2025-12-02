package com.ble1st.connectias.feature.network.ssh

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for SSH/Telnet client functionality.
 *
 * Features:
 * - SSH connection (basic implementation)
 * - Telnet connection
 * - Command execution
 * - Session management
 * - Connection history
 */
@Singleton
class SshClientProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeSessions = ConcurrentHashMap<String, TerminalSession>()

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private val _savedHosts = MutableStateFlow<List<SavedHost>>(emptyList())
    val savedHosts: StateFlow<List<SavedHost>> = _savedHosts.asStateFlow()

    /**
     * Connects to a Telnet server.
     */
    suspend fun connectTelnet(
        host: String,
        port: Int = 23,
        timeout: Int = 30000
    ): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeout)
            socket.soTimeout = timeout

            val sessionId = UUID.randomUUID().toString()
            val session = TerminalSession(
                id = sessionId,
                type = SessionType.TELNET,
                host = host,
                port = port,
                socket = socket,
                reader = BufferedReader(InputStreamReader(socket.getInputStream())),
                writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            )

            activeSessions[sessionId] = session
            updateSessionList()

            ConnectionResult.Success(
                sessionId = sessionId,
                host = host,
                port = port,
                protocol = "TELNET"
            )
        } catch (e: Exception) {
            Timber.e(e, "Telnet connection failed to $host:$port")
            ConnectionResult.Error("Connection failed: ${e.message}", e)
        }
    }

    /**
     * Connects to an SSH server (basic implementation).
     * Note: Full SSH requires JSch or similar library for proper SSH2 protocol
     */
    suspend fun connectSsh(
        host: String,
        port: Int = 22,
        username: String,
        password: String? = null,
        privateKey: String? = null,
        timeout: Int = 30000
    ): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeout)
            socket.soTimeout = timeout

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)

            // Read SSH banner
            val banner = reader.readLine()
            Timber.d("SSH Banner: $banner")

            // Note: This is a simplified implementation
            // Full SSH requires proper key exchange, encryption, and authentication
            // Consider using JSch library for production use

            val sessionId = UUID.randomUUID().toString()
            val session = TerminalSession(
                id = sessionId,
                type = SessionType.SSH,
                host = host,
                port = port,
                socket = socket,
                reader = reader,
                writer = writer,
                username = username
            )

            activeSessions[sessionId] = session
            updateSessionList()

            ConnectionResult.Success(
                sessionId = sessionId,
                host = host,
                port = port,
                protocol = "SSH",
                banner = banner
            )
        } catch (e: Exception) {
            Timber.e(e, "SSH connection failed to $host:$port")
            ConnectionResult.Error("SSH connection failed: ${e.message}", e)
        }
    }

    /**
     * Executes a command in a session.
     */
    suspend fun executeCommand(sessionId: String, command: String): CommandResult = 
        withContext(Dispatchers.IO) {
            val session = activeSessions[sessionId]
                ?: return@withContext CommandResult.Error("Session not found")

            try {
                session.writer.println(command)
                session.writer.flush()

                val output = StringBuilder()
                val startTime = System.currentTimeMillis()
                val timeout = 5000L

                while (System.currentTimeMillis() - startTime < timeout) {
                    if (session.reader.ready()) {
                        val line = session.reader.readLine() ?: break
                        output.appendLine(line)
                    } else {
                        kotlinx.coroutines.delay(50)
                    }
                }

                CommandResult.Success(
                    output = output.toString(),
                    executionTime = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                Timber.e(e, "Command execution failed")
                CommandResult.Error("Command failed: ${e.message}", e)
            }
        }

    /**
     * Streams output from a session.
     */
    fun streamOutput(sessionId: String): Flow<String> = flow {
        val session = activeSessions[sessionId] ?: return@flow

        try {
            while (session.socket.isConnected && !session.socket.isClosed) {
                if (session.reader.ready()) {
                    val line = session.reader.readLine()
                    if (line != null) {
                        emit(line)
                    }
                } else {
                    kotlinx.coroutines.delay(50)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error streaming output")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Sends raw input to a session.
     */
    suspend fun sendInput(sessionId: String, input: String): Boolean = withContext(Dispatchers.IO) {
        val session = activeSessions[sessionId] ?: return@withContext false

        try {
            session.writer.print(input)
            session.writer.flush()
            true
        } catch (e: Exception) {
            Timber.e(e, "Error sending input")
            false
        }
    }

    /**
     * Disconnects a session.
     */
    suspend fun disconnect(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val session = activeSessions.remove(sessionId) ?: return@withContext false

        try {
            session.reader.close()
            session.writer.close()
            session.socket.close()
            updateSessionList()
            true
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting session")
            false
        }
    }

    /**
     * Disconnects all sessions.
     */
    suspend fun disconnectAll() = withContext(Dispatchers.IO) {
        activeSessions.keys.forEach { sessionId ->
            disconnect(sessionId)
        }
    }

    /**
     * Gets session info.
     */
    fun getSessionInfo(sessionId: String): SessionInfo? {
        val session = activeSessions[sessionId] ?: return null
        return SessionInfo(
            id = session.id,
            type = session.type,
            host = session.host,
            port = session.port,
            username = session.username,
            isConnected = session.socket.isConnected && !session.socket.isClosed,
            connectedAt = session.connectedAt
        )
    }

    /**
     * Saves a host for quick access.
     */
    fun saveHost(host: SavedHost) {
        _savedHosts.update { it.filter { h -> h.id != host.id } + host }
    }

    /**
     * Removes a saved host.
     */
    fun removeHost(hostId: String) {
        _savedHosts.update { it.filter { h -> h.id != hostId } }
    }

    /**
     * Tests connectivity to a host.
     */
    suspend fun testConnection(host: String, port: Int, timeout: Int = 5000): TestResult = 
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeout)
                val latency = System.currentTimeMillis() - startTime

                var banner: String? = null
                try {
                    socket.soTimeout = 2000
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    if (reader.ready()) {
                        banner = reader.readLine()
                    }
                } catch (e: Exception) {
                    // Banner read timeout is acceptable
                }

                socket.close()

                TestResult(
                    success = true,
                    latency = latency.toInt(),
                    banner = banner
                )
            } catch (e: Exception) {
                TestResult(
                    success = false,
                    error = e.message
                )
            }
        }

    private fun updateSessionList() {
        _sessions.value = activeSessions.values.map { session ->
            SessionInfo(
                id = session.id,
                type = session.type,
                host = session.host,
                port = session.port,
                username = session.username,
                isConnected = session.socket.isConnected && !session.socket.isClosed,
                connectedAt = session.connectedAt
            )
        }
    }
}

/**
 * Terminal session data.
 */
private data class TerminalSession(
    val id: String,
    val type: SessionType,
    val host: String,
    val port: Int,
    val socket: Socket,
    val reader: BufferedReader,
    val writer: PrintWriter,
    val username: String? = null,
    val connectedAt: Long = System.currentTimeMillis()
)

/**
 * Session types.
 */
enum class SessionType {
    SSH,
    TELNET
}

/**
 * Session information.
 */
@Serializable
data class SessionInfo(
    val id: String,
    val type: SessionType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val isConnected: Boolean,
    val connectedAt: Long
)

/**
 * Connection result.
 */
sealed class ConnectionResult {
    data class Success(
        val sessionId: String,
        val host: String,
        val port: Int,
        val protocol: String,
        val banner: String? = null
    ) : ConnectionResult()

    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : ConnectionResult()
}

/**
 * Command execution result.
 */
sealed class CommandResult {
    data class Success(
        val output: String,
        val executionTime: Long
    ) : CommandResult()

    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : CommandResult()
}

/**
 * Connection test result.
 */
@Serializable
data class TestResult(
    val success: Boolean,
    val latency: Int? = null,
    val banner: String? = null,
    val error: String? = null
)

/**
 * Saved host configuration.
 */
@Serializable
data class SavedHost(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val type: SessionType,
    val username: String? = null,
    val usePrivateKey: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
