package com.ble1st.connectias.feature.ssh.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_profiles")
data class SshProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authMode: AuthMode, // Assuming AuthMode is an Enum, Room handles Strings/Ints usually via TypeConverter or name
    val encryptedPassword: String?, // Base64 encoded encrypted password
    val encryptedKeyPath: String?, // Path to private key file
    val encryptedKeyPassword: String? // Password for the private key
)
