package com.ble1st.connectias.plugin.declarative.packaging

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ble1st.connectias.plugin.declarative.model.DeclarativeSchemaVersions
import com.ble1st.connectias.plugin.security.DeclarativeDeveloperTrustStore
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates signed ".cplug" packages for declarative plugins.
 *
 * Signing:
 * - Key: ECDSA P-256 stored in AndroidKeyStore under alias "connectias_devkey_<developerId>"
 * - Digest: canonical SHA-256 over entries excluding signature.json (same as verifier)
 * - Signature: SHA256withECDSA over contentDigestHex (UTF-8)
 */
class DeclarativePluginPackager(
    private val context: Context
) {
    data class BuildSpec(
        val pluginId: String,
        val pluginName: String,
        val versionName: String,
        val versionCode: Int,
        val developerId: String,
        val capabilities: List<String>,
        val permissions: List<String> = emptyList(),
        val startScreenId: String,
        val uiMainJson: String,
        // Optional additional UI screens (path -> json). Example: "ui/image.json" -> "{...}".
        // When present, the manifest screens list will include ui/main.json plus these entries.
        val uiExtraScreens: Map<String, String> = emptyMap(),
        val flowMainJson: String,
        val description: String? = null,
        val assets: Map<String, ByteArray> = emptyMap(), // relative paths under assets/
    )

    fun buildToFile(spec: BuildSpec, outputFile: File): Result<File> {
        return try {
            outputFile.parentFile?.mkdirs()

            val trustStore = DeclarativeDeveloperTrustStore(context)
            val keyPair = getOrCreateKeyPair(spec.developerId)

            // Add/update truststore for local developer key (so exports can be imported immediately).
            val pubB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            trustStore.addOrReplace(spec.developerId, pubB64)

            val entries = LinkedHashMap<String, ByteArray>()
            entries["plugin-manifest.json"] = buildManifestJson(spec).toByteArray(Charsets.UTF_8)
            entries["ui/main.json"] = spec.uiMainJson.toByteArray(Charsets.UTF_8)
            spec.uiExtraScreens.forEach { (path, json) ->
                val normalized = path.removePrefix("/").replace('\\', '/')
                if (!normalized.startsWith("ui/") || normalized.contains("..")) {
                    return Result.failure(IllegalArgumentException("Invalid ui screen path"))
                }
                if (normalized == "ui/main.json") return@forEach
                entries[normalized] = json.toByteArray(Charsets.UTF_8)
            }
            entries["flows/main.json"] = spec.flowMainJson.toByteArray(Charsets.UTF_8)

            spec.assets.forEach { (relativePath, bytes) ->
                val normalized = relativePath.removePrefix("/").removePrefix("assets/")
                if (normalized.contains("..")) return Result.failure(IllegalArgumentException("Invalid asset path"))
                entries["assets/$normalized"] = bytes
            }

            val contentDigestHex = computeContentDigestHex(entries)
            val signatureBase64 = signDigestHex(keyPair, contentDigestHex)

            val signatureJson = JSONObject().apply {
                put("schemaVersion", DeclarativeSchemaVersions.V1)
                put("algorithm", "SHA256withECDSA")
                put("developerId", spec.developerId)
                put("publicKeyBase64", pubB64)
                put("contentDigestHex", contentDigestHex)
                put("signatureBase64", signatureBase64)
                put("signedAtEpochMillis", System.currentTimeMillis())
            }.toString()

            FileOutputStream(outputFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    entries.keys.sorted().forEach { name ->
                        zos.putNextEntry(ZipEntry(name))
                        zos.write(entries[name]!!)
                        zos.closeEntry()
                    }
                    zos.putNextEntry(ZipEntry("signature.json"))
                    zos.write(signatureJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }

            Timber.i("Declarative plugin exported: ${outputFile.absolutePath}")
            Result.success(outputFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to build declarative plugin package")
            Result.failure(e)
        }
    }

    private fun buildManifestJson(spec: BuildSpec): String {
        val entrypoints = JSONObject().apply {
            put("startScreenId", spec.startScreenId)
            put(
                "screens",
                JSONArray().apply {
                    put("ui/main.json")
                    spec.uiExtraScreens.keys
                        .map { it.removePrefix("/").replace('\\', '/') }
                        .filter { it.startsWith("ui/") && it != "ui/main.json" && !it.contains("..") }
                        .sorted()
                        .forEach { put(it) }
                }
            )
            put("flows", JSONArray().apply { put("flows/main.json") })
        }
        return JSONObject().apply {
            put("schemaVersion", DeclarativeSchemaVersions.V1)
            put("pluginType", "declarative")
            put("pluginId", spec.pluginId)
            put("pluginName", spec.pluginName)
            put("versionName", spec.versionName)
            put("versionCode", spec.versionCode)
            put("developerId", spec.developerId)
            put("capabilities", JSONArray(spec.capabilities))
            if (spec.permissions.isNotEmpty()) put("permissions", JSONArray(spec.permissions))
            put("entrypoints", entrypoints)
            if (!spec.description.isNullOrBlank()) put("description", spec.description)
        }.toString()
    }

    private fun computeContentDigestHex(entries: Map<String, ByteArray>): String {
        val md = MessageDigest.getInstance("SHA-256")
        entries.keys.sorted().forEach { name ->
            md.update(name.toByteArray(Charsets.UTF_8))
            md.update(0)
            md.update(entries[name]!!)
        }
        return md.digest().toHexLower()
    }

    private fun signDigestHex(keyPair: KeyPair, digestHex: String): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(digestHex.toByteArray(Charsets.UTF_8))
        val bytes = sig.sign()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun getOrCreateKeyPair(developerId: String): KeyPair {
        val alias = "connectias_devkey_${developerId}"
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) {
            val privateKey = ks.getKey(alias, null) as java.security.PrivateKey
            val publicKey = ks.getCertificate(alias).publicKey
            return KeyPair(publicKey, privateKey)
        }

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()

        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    private fun ByteArray.toHexLower(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(((b.toInt() shr 4) and 0xF).toString(16))
            sb.append((b.toInt() and 0xF).toString(16))
        }
        return sb.toString()
    }
}

