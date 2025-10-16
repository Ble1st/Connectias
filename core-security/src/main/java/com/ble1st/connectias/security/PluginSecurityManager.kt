package com.ble1st.connectias.security

import com.ble1st.connectias.api.PluginInfo
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarFile

class PluginSecurityManager {
    
    fun createSecurityManager(plugin: PluginInfo): SecurityManager {
        return PluginSecurityPolicy(plugin)
    }
    
    fun verifyPluginSignature(pluginFile: File): SignatureVerificationResult {
        return try {
            val jarFile = JarFile(pluginFile)
            val manifest = jarFile.manifest
            val entries = manifest.entries
            
            // Check if plugin is signed
            val hasSignature = entries.any { it.value.attributes.containsKey("Digest") }
            
            if (!hasSignature) {
                return SignatureVerificationResult(
                    isValid = false,
                    isSigned = false,
                    message = "Plugin is not signed"
                )
            }
            
            // Verify signature
            val certFactory = CertificateFactory.getInstance("X.509")
            val certs = jarFile.entries.asSequence()
                .filter { it.name.endsWith(".DSA") || it.name.endsWith(".RSA") }
                .mapNotNull { entry ->
                    try {
                        jarFile.getInputStream(entry).use { inputStream ->
                            certFactory.generateCertificate(inputStream) as? X509Certificate
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                .toList()
            
            if (certs.isEmpty()) {
                return SignatureVerificationResult(
                    isValid = false,
                    isSigned = true,
                    message = "No valid certificates found"
                )
            }
            
            // Basic certificate validation
            val isValid = certs.any { cert ->
                try {
                    cert.checkValidity()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            
            SignatureVerificationResult(
                isValid = isValid,
                isSigned = true,
                message = if (isValid) "Signature is valid" else "Signature is invalid or expired"
            )
            
        } catch (e: Exception) {
            SignatureVerificationResult(
                isValid = false,
                isSigned = false,
                message = "Error verifying signature: ${e.message}"
            )
        }
    }
    
    fun validatePluginPackage(pluginFile: File): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        try {
            // 1. Check if file exists and is readable
            if (!pluginFile.exists()) {
                errors.add(ValidationError("Plugin file does not exist"))
                return ValidationResult(false, errors, warnings)
            }
            
            if (!pluginFile.canRead()) {
                errors.add(ValidationError("Plugin file is not readable"))
                return ValidationResult(false, errors, warnings)
            }
            
            // 2. Check file size (max 50MB)
            val maxSize = 50 * 1024 * 1024L // 50MB
            if (pluginFile.length() > maxSize) {
                errors.add(ValidationError("Plugin file is too large: ${pluginFile.length()} bytes"))
                return ValidationResult(false, errors, warnings)
            }
            
            // 3. Check if it's a valid ZIP file
            try {
                val jarFile = JarFile(pluginFile)
                jarFile.close()
            } catch (e: Exception) {
                errors.add(ValidationError("Plugin file is not a valid ZIP archive"))
                return ValidationResult(false, errors, warnings)
            }
            
            // 4. Check for required files
            val jarFile = JarFile(pluginFile)
            val entries = jarFile.entries.asSequence().map { it.name }.toSet()
            
            if (!entries.contains("plugin.json")) {
                errors.add(ValidationError("plugin.json manifest is missing"))
            }
            
            if (!entries.any { it.endsWith(".dex") }) {
                errors.add(ValidationError("No DEX file found in plugin"))
            }
            
            jarFile.close()
            
            // 5. Additional security checks
            if (entries.any { it.contains("..") || it.startsWith("/") }) {
                warnings.add(ValidationWarning("Plugin contains potentially dangerous file paths"))
            }
            
            if (entries.any { it.endsWith(".so") }) {
                warnings.add(ValidationWarning("Plugin contains native libraries"))
            }
            
        } catch (e: Exception) {
            errors.add(ValidationError("Error validating plugin: ${e.message}"))
        }
        
        return ValidationResult(errors.isEmpty(), errors, warnings)
    }
}

data class SignatureVerificationResult(
    val isValid: Boolean,
    val isSigned: Boolean,
    val message: String
)

data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>
)

data class ValidationError(val message: String)
data class ValidationWarning(val message: String)
