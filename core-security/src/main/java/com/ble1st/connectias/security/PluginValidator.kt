package com.ble1st.connectias.security

import com.ble1st.connectias.api.PluginInfo
import kotlinx.serialization.json.Json
import java.io.File
import java.util.jar.JarFile

class PluginValidator {
    
    fun validatePlugin(pluginFile: File): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        try {
            // 1. ZIP-Struktur prüfen
            val zipValidation = validateZipStructure(pluginFile)
            errors.addAll(zipValidation.first)
            warnings.addAll(zipValidation.second)
            
            // 2. plugin.json validieren
            val manifest = extractManifest(pluginFile)
            if (manifest != null) {
                val manifestValidation = validateManifest(manifest)
                errors.addAll(manifestValidation.first)
                warnings.addAll(manifestValidation.second)
            } else {
                errors.add(ValidationError("plugin.json not found or invalid"))
            }
            
            // 3. DEX-Datei checken
            val dexValidation = checkDexIntegrity(pluginFile)
            errors.addAll(dexValidation.first)
            warnings.addAll(dexValidation.second)
            
            // 4. Entry-Point-Klasse verifizieren
            if (manifest != null) {
                val entryPointValidation = validateEntryPoint(manifest.entryPoint, pluginFile)
                errors.addAll(entryPointValidation.first)
                warnings.addAll(entryPointValidation.second)
            }
            
            // 5. Version-Kompatibilität prüfen
            if (manifest != null) {
                val versionValidation = validateVersionCompatibility(manifest)
                errors.addAll(versionValidation.first)
                warnings.addAll(versionValidation.second)
            }
            
        } catch (e: Exception) {
            errors.add(ValidationError("Unexpected error during validation: ${e.message}"))
        }
        
        return ValidationResult(errors.isEmpty(), errors, warnings)
    }
    
    fun validateManifest(manifest: PluginInfo): Pair<List<ValidationError>, List<ValidationWarning>> {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        // Required fields
        if (manifest.id.isBlank()) {
            errors.add(ValidationError("Plugin ID is required"))
        }
        
        if (manifest.name.isBlank()) {
            errors.add(ValidationError("Plugin name is required"))
        }
        
        if (manifest.version.isBlank()) {
            errors.add(ValidationError("Plugin version is required"))
        }
        
        if (manifest.entryPoint.isBlank()) {
            errors.add(ValidationError("Entry point is required"))
        }
        
        // ID format validation
        if (!manifest.id.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
            errors.add(ValidationError("Plugin ID contains invalid characters"))
        }
        
        // Version format validation
        if (!manifest.version.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) {
            warnings.add(ValidationWarning("Version format should be x.y.z"))
        }
        
        // Entry point format validation
        if (!manifest.entryPoint.matches(Regex("^[a-zA-Z0-9._]+$"))) {
            errors.add(ValidationError("Entry point contains invalid characters"))
        }
        
        // Permission validation
        manifest.permissions.forEach { permission ->
            if (permission == null) {
                errors.add(ValidationError("Invalid permission specified"))
            }
        }
        
        return Pair(errors, warnings)
    }
    
    fun checkDexIntegrity(dexFile: File): Pair<List<ValidationError>, List<ValidationWarning>> {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        try {
            val jarFile = JarFile(dexFile)
            val dexEntries = jarFile.entries.asSequence()
                .filter { it.name.endsWith(".dex") }
                .toList()
            
            if (dexEntries.isEmpty()) {
                errors.add(ValidationError("No DEX files found"))
            }
            
            dexEntries.forEach { entry ->
                try {
                    jarFile.getInputStream(entry).use { inputStream ->
                        val header = ByteArray(4)
                        inputStream.read(header)
                        
                        // Check DEX magic number
                        if (!header.contentEquals("dex\n".toByteArray())) {
                            errors.add(ValidationError("Invalid DEX file: ${entry.name}"))
                        }
                    }
                } catch (e: Exception) {
                    errors.add(ValidationError("Error reading DEX file ${entry.name}: ${e.message}"))
                }
            }
            
            jarFile.close()
            
        } catch (e: Exception) {
            errors.add(ValidationError("Error checking DEX integrity: ${e.message}"))
        }
        
        return Pair(errors, warnings)
    }
    
    private fun validateZipStructure(pluginFile: File): Pair<List<ValidationError>, List<ValidationWarning>> {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        try {
            val jarFile = JarFile(pluginFile)
            val entries = jarFile.entries.asSequence().map { it.name }.toSet()
            
            // Check for required files
            if (!entries.contains("plugin.json")) {
                errors.add(ValidationError("plugin.json is missing"))
            }
            
            // Check for dangerous files
            val dangerousFiles = entries.filter { 
                it.contains("..") || it.startsWith("/") || it.contains("\\")
            }
            
            if (dangerousFiles.isNotEmpty()) {
                errors.add(ValidationError("Dangerous file paths found: ${dangerousFiles.joinToString()}")
            }
            
            // Check for suspicious files
            val suspiciousFiles = entries.filter { 
                it.endsWith(".so") || it.endsWith(".dll") || it.endsWith(".exe")
            }
            
            if (suspiciousFiles.isNotEmpty()) {
                warnings.add(ValidationWarning("Native binaries found: ${suspiciousFiles.joinToString()}")
            }
            
            jarFile.close()
            
        } catch (e: Exception) {
            errors.add(ValidationError("Error validating ZIP structure: ${e.message}"))
        }
        
        return Pair(errors, warnings)
    }
    
    private fun extractManifest(pluginFile: File): PluginInfo? {
        return try {
            val jarFile = JarFile(pluginFile)
            val manifestEntry = jarFile.getEntry("plugin.json")
            
            if (manifestEntry == null) {
                return null
            }
            
            jarFile.getInputStream(manifestEntry).use { inputStream ->
                val json = inputStream.bufferedReader().readText()
                Json.decodeFromString<PluginInfo>(json)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun validateEntryPoint(entryPoint: String, pluginFile: File): Pair<List<ValidationError>, List<ValidationWarning>> {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        try {
            val jarFile = JarFile(pluginFile)
            val dexEntries = jarFile.entries.asSequence()
                .filter { it.name.endsWith(".dex") }
                .toList()
            
            if (dexEntries.isEmpty()) {
                errors.add(ValidationError("No DEX files to validate entry point"))
                return Pair(errors, warnings)
            }
            
            // Basic entry point validation
            if (!entryPoint.contains(".")) {
                errors.add(ValidationError("Entry point must be a fully qualified class name"))
            }
            
            jarFile.close()
            
        } catch (e: Exception) {
            errors.add(ValidationError("Error validating entry point: ${e.message}"))
        }
        
        return Pair(errors, warnings)
    }
    
    private fun validateVersionCompatibility(manifest: PluginInfo): Pair<List<ValidationError>, List<ValidationWarning>> {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        // Check minimum core version
        val currentCoreVersion = "1.0.0" // This should come from BuildConfig
        val minVersion = manifest.minCoreVersion
        
        if (compareVersions(currentCoreVersion, minVersion) < 0) {
            errors.add(ValidationError("Plugin requires core version $minVersion or higher, current: $currentCoreVersion"))
        }
        
        // Check maximum core version if specified
        manifest.maxCoreVersion?.let { maxVersion ->
            if (compareVersions(currentCoreVersion, maxVersion) > 0) {
                warnings.add(ValidationWarning("Plugin may not be compatible with core version $currentCoreVersion (max: $maxVersion)"))
            }
        }
        
        return Pair(errors, warnings)
    }
    
    private fun compareVersions(version1: String, version2: String): Int {
        val v1Parts = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = version2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val v1Part = v1Parts.getOrNull(i) ?: 0
            val v2Part = v2Parts.getOrNull(i) ?: 0
            
            when {
                v1Part > v2Part -> return 1
                v1Part < v2Part -> return -1
            }
        }
        
        return 0
    }
}
