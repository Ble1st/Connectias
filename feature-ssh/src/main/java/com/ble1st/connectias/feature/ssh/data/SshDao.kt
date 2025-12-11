package com.ble1st.connectias.feature.ssh.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SshDao {
    @Query("SELECT * FROM ssh_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<SshProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SshProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: SshProfileEntity)
    
    @Query("SELECT * FROM ssh_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): SshProfileEntity?
}
