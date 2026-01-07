package com.ble1st.connectias.core.plugin

import android.os.Build
import com.ble1st.connectias.plugin.native.INativeLibraryManager
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeLibraryManager : INativeLibraryManager {
    
    private val loadedLibraries = mutableSetOf<String>()
    private val loadLock = Any()
    
    override suspend fun loadLibrary(
        libraryName: String,
        libraryPath: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            synchronized(loadLock) {
                if (loadedLibraries.contains(libraryName)) {
                    Timber.d("Library already loaded: $libraryName")
                    return@withContext Result.success(Unit)
                }
                
                if (!libraryPath.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Library file does not exist: ${libraryPath.absolutePath}")
                    )
                }
                
                val abi = getCurrentAbi()
                Timber.d("Loading library: $libraryName for ABI: $abi from ${libraryPath.absolutePath}")
                
                System.load(libraryPath.absolutePath)
                
                loadedLibraries.add(libraryName)
                Timber.i("Successfully loaded library: $libraryName")
                Result.success(Unit)
            }
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load library: $libraryName - UnsatisfiedLinkError")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load library: $libraryName")
            Result.failure(e)
        }
    }
    
    override suspend fun unloadLibrary(libraryName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            synchronized(loadLock) {
                if (loadedLibraries.remove(libraryName)) {
                    Timber.w("Library marked as unloaded: $libraryName (Note: JVM cannot actually unload .so files)")
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Library not loaded: $libraryName"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload library: $libraryName")
            Result.failure(e)
        }
    }
    
    override fun isLoaded(libraryName: String): Boolean {
        return synchronized(loadLock) {
            loadedLibraries.contains(libraryName)
        }
    }
    
    override fun getLoadedLibraries(): List<String> {
        return synchronized(loadLock) {
            loadedLibraries.toList()
        }
    }
    
    private fun getCurrentAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }
    
    fun getSupportedAbis(): List<String> {
        return Build.SUPPORTED_ABIS.toList()
    }
    
    fun validateAbi(libraryPath: File): Result<String> {
        val supportedAbis = Build.SUPPORTED_ABIS
        val pathStr = libraryPath.absolutePath
        
        for (abi in supportedAbis) {
            if (pathStr.contains(abi)) {
                return Result.success(abi)
            }
        }
        
        return Result.failure(
            IllegalArgumentException(
                "Library ABI not supported. Supported ABIs: ${supportedAbis.joinToString()}, " +
                "Library path: $pathStr"
            )
        )
    }
}
