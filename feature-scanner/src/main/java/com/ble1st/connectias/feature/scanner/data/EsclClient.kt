package com.ble1st.connectias.feature.scanner.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

/**
 * eSCL (ePrint Scan Language) client for network printer scanning.
 * Implements the eSCL protocol for scanning via HTTP.
 */
@Singleton
class EsclClient @Inject constructor() {
    
    // Interceptor to fix malformed chunked transfer encoding from some scanners
    private val transferEncodingFixInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        
        // If response has Transfer-Encoding: chunked but causes issues, 
        // read the body and recreate response without chunked encoding
        val transferEncoding = response.header("Transfer-Encoding")
        if (transferEncoding != null && transferEncoding.contains("chunked", ignoreCase = true)) {
            try {
                val body = response.body
                if (body != null) {
                    // Read all bytes from the stream
                    val source = body.source()
                    val buffer = Buffer()
                    source.readAll(buffer)
                    val bytes = buffer.readByteArray()
                    
                    // Create new response without Transfer-Encoding header
                    val newBody = ResponseBody.create(body.contentType(), bytes)
                    return@Interceptor response.newBuilder()
                        .removeHeader("Transfer-Encoding")
                        .body(newBody)
                        .build()
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fix transfer encoding, using original response")
            }
        }
        
        response
    }
    
    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1)) // Force HTTP/1.1 to avoid ProtocolException on some scanners
        .addInterceptor(transferEncodingFixInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // Scanning can take time
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Checks if a scanner supports eSCL protocol.
     */
    suspend fun checkEsclSupport(address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$address/eSCL/ScannerCapabilities"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val isSupported = response.isSuccessful && response.code == 200
            response.close()
            isSupported
        } catch (e: Exception) {
            Timber.d(e, "eSCL check failed for $address")
            false
        }
    }
    
    /**
     * Creates a scan job and returns the job URI.
     */
    suspend fun createScanJob(
        address: String,
        source: ScanSource,
        format: String = "image/jpeg",
        colorMode: String = "Color",
        resolution: Int = 300
    ): String? = withContext(Dispatchers.IO) {
        try {
            val sourceValue = when (source) {
                ScanSource.FLATBED -> "Platen"
                ScanSource.ADF -> "Feeder"
            }
            
            val scanSettingsXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <scan:ScanSettings xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm" xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03">
                    <pwg:Version>2.0</pwg:Version>
                    <pwg:ScanRegions>
                        <pwg:ScanRegion>
                            <pwg:ContentRegionUnits>escl:ThreeHundredthsOfInches</pwg:ContentRegionUnits>
                            <pwg:XOffset>0</pwg:XOffset>
                            <pwg:YOffset>0</pwg:YOffset>
                            <pwg:Width>2550</pwg:Width>
                            <pwg:Height>3300</pwg:Height>
                        </pwg:ScanRegion>
                    </pwg:ScanRegions>
                    <pwg:InputSource>$sourceValue</pwg:InputSource>
                    <pwg:ColorMode>$colorMode</pwg:ColorMode>
                    <pwg:XResolution>$resolution</pwg:XResolution>
                    <pwg:YResolution>$resolution</pwg:YResolution>
                    <pwg:Format>$format</pwg:Format>
                </scan:ScanSettings>
            """.trimIndent()
            
            val url = "http://$address/eSCL/ScanJobs"
            val request = Request.Builder()
                .url(url)
                .post(scanSettingsXml.toRequestBody("application/xml".toMediaType()))
                .addHeader("Content-Type", "application/xml")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Timber.e("Failed to create scan job: ${response.code} ${response.message}")
                response.close()
                return@withContext null
            }
            
            // Extract job URI from Location header or response body
            val location = response.header("Location")
            var jobUri = location
            
            // If Location header is relative, make it absolute
            if (jobUri != null && !jobUri.startsWith("http")) {
                jobUri = if (jobUri.startsWith("/")) {
                    "http://$address$jobUri"
                } else {
                    "http://$address/eSCL/$jobUri"
                }
            }
            
            // Fallback: try to parse from response body if Location header is missing
            if (jobUri == null) {
                val responseBody = response.body?.string()
                
                if (responseBody != null) {
                    Timber.d("Location header missing, parsing response body: ${responseBody.take(200)}")
                    // Try to extract job URI from XML response
                    try {
                        val factory = DocumentBuilderFactory.newInstance()
                        factory.isNamespaceAware = true
                        val builder = factory.newDocumentBuilder()
                        val doc = builder.parse(responseBody.byteInputStream())
                        
                        // Look for JobUri element (try different tag names)
                        val possibleTags = listOf("JobUri", "pwg:JobUri", "scan:JobUri", "jobUri")
                        for (tag in possibleTags) {
                            val jobUriElements = doc.getElementsByTagName(tag)
                            if (jobUriElements.length > 0) {
                                val uri = jobUriElements.item(0).textContent?.trim()
                                if (uri != null && uri.isNotEmpty()) {
                                    jobUri = if (uri.startsWith("http")) uri else "http://$address$uri"
                                    Timber.d("Found job URI in tag '$tag': $jobUri")
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse job URI from response body")
                    }
                }
                
                // Last resort: use request URL
                if (jobUri == null) {
                    jobUri = response.request.url.toString()
                    Timber.d("Using request URL as fallback: $jobUri")
                }
            }
            
            response.close()
            Timber.d("Scan job created: $jobUri")
            jobUri
        } catch (e: Exception) {
            Timber.e(e, "Failed to create scan job")
            null
        }
    }
    
    // Helper class to handle scan job results
    private sealed class ScanJobResult {
        data class Url(val url: String) : ScanJobResult()
        data class Data(val bytes: ByteArray, val contentType: String, val sourceUrl: String) : ScanJobResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Data

                if (!bytes.contentEquals(other.bytes)) return false
                if (contentType != other.contentType) return false
                if (sourceUrl != other.sourceUrl) return false

                return true
            }

            override fun hashCode(): Int {
                var result = bytes.contentHashCode()
                result = 31 * result + contentType.hashCode()
                result = 31 * result + sourceUrl.hashCode()
                return result
            }
        }
    }

    /**
     * Polls the scan job status until it's completed.
     * Some scanners don't support status endpoint, so we try to download the document directly.
     * Returns a ScanJobResult containing either the URL (if status check passed) or the actual data (if direct download passed).
     */
    private suspend fun waitForScanJob(address: String, jobUri: String): ScanJobResult? = withContext(Dispatchers.IO) {
        try {
            val maxAttempts = 60 // 60 seconds max wait time
            var attempts = 0
            var statusEndpointFailed = false
            
            while (attempts < maxAttempts) {
                // First, try to check status via status endpoint
                if (!statusEndpointFailed) {
                    val statusUrl = if (jobUri.startsWith("http")) {
                        jobUri
                    } else {
                        "http://$address$jobUri"
                    }
                    
                    val statusRequest = Request.Builder()
                        .url(statusUrl)
                        .get()
                        .build()
                    
                    val statusResponse = client.newCall(statusRequest).execute()
                    
                    if (statusResponse.isSuccessful) {
                        val body = statusResponse.body?.string() ?: ""
                        statusResponse.close()
                        
                        // Log raw response for debugging (first few attempts only)
                        if (attempts < 3) {
                            Timber.d("Scan job status response (attempt ${attempts + 1}): $body")
                        }
                        
                        // Parse XML to check job state
                        val jobState = parseJobState(body)
                        Timber.d("Parsed job state: '$jobState' (attempt ${attempts + 1}/${maxAttempts})")
                        
                        // Check for completed state (case-insensitive)
                        when (jobState.lowercase()) {
                            "completed" -> {
                                Timber.d("Scan job completed successfully")
                                // Return the status URL as download URL (will try variants in downloadScanResult)
                                val completedUrl = if (statusUrl.startsWith("http")) {
                                    statusUrl
                                } else {
                                    "http://$address$statusUrl"
                                }
                                return@withContext ScanJobResult.Url(completedUrl)
                            }
                            "aborted", "canceled", "cancelled" -> {
                                Timber.e("Scan job aborted or canceled: $jobState")
                                return@withContext null
                            }
                            "processing", "pending" -> {
                                // Still processing
                                Timber.d("Scan job still processing: $jobState")
                                delay(1000)
                                attempts++
                                continue
                            }
                            else -> {
                                // Unknown state - log it and continue waiting
                                Timber.d("Scan job in unknown state: $jobState, continuing to wait...")
                                delay(1000)
                                attempts++
                                continue
                            }
                        }
                    } else if (statusResponse.code == 404) {
                        // Status endpoint not supported, switch to direct download approach
                        Timber.d("Status endpoint returned 404, switching to direct download approach")
                        statusResponse.close()
                        statusEndpointFailed = true
                        // Continue to try direct download
                    } else {
                        Timber.w("Scan job status check failed: ${statusResponse.code} ${statusResponse.message}")
                        statusResponse.close()
                        delay(1000)
                        attempts++
                        continue
                    }
                }
                
                // If status endpoint failed or doesn't exist, try to download document directly
                // This works for scanners that don't support status polling
                // Try different URL variants
                val downloadUrlVariants = listOf(
                    if (jobUri.startsWith("http")) {
                        "$jobUri/NextDocument"
                    } else {
                        "http://$address$jobUri/NextDocument"
                    },
                    if (jobUri.startsWith("http")) {
                        jobUri
                    } else {
                        "http://$address$jobUri"
                    }
                )
                
                var downloadResponse: Response? = null
                var foundWorkingUrl = false
                
                for (downloadUrl in downloadUrlVariants) {
                    val downloadRequest = Request.Builder()
                        .url(downloadUrl)
                        .get()
                        .build()
                    
                    downloadResponse = client.newCall(downloadRequest).execute()
                    
                    when (downloadResponse.code) {
                        200 -> {
                            // Document is ready! 
                            // CRITICAL FIX: Read the body immediately because some scanners discard the document after one read/check.
                            Timber.d("Document is ready for download from: $downloadUrl. Reading bytes immediately.")
                            
                            val contentType = downloadResponse.header("Content-Type") ?: ""
                            val body = downloadResponse.body
                            
                            if (body != null) {
                                try {
                                    val bytes = body.bytes()
                                    downloadResponse.close()
                                    if (bytes.isNotEmpty()) {
                                        return@withContext ScanJobResult.Data(bytes, contentType, downloadUrl)
                                    } else {
                                        Timber.w("Received empty body from $downloadUrl")
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to read bytes during polling")
                                    downloadResponse.close()
                                }
                            } else {
                                downloadResponse.close()
                            }
                            
                            // If reading failed or body was empty but code was 200, we might try again or fail?
                            // Let's assume if 200 and failed read, it's a transient network issue, so retry loop.
                            foundWorkingUrl = true
                            break
                        }
                        202 -> {
                            // Accepted but not ready yet
                            Timber.d("Scan job accepted but not ready yet (202) from: $downloadUrl")
                            downloadResponse.close()
                            foundWorkingUrl = true
                            break
                        }
                        204 -> {
                            // No content - might mean no document yet or scan completed with no pages
                            Timber.d("No content available yet (204) from: $downloadUrl")
                            downloadResponse.close()
                            foundWorkingUrl = true
                            break
                        }
                        503 -> {
                            // Service unavailable - scanner is busy
                            Timber.d("Scanner busy, waiting... (503) from: $downloadUrl")
                            downloadResponse.close()
                            foundWorkingUrl = true
                            break
                        }
                        404 -> {
                            // Not found - try next variant
                            Timber.d("URL not found (404): $downloadUrl, trying next variant")
                            downloadResponse.close()
                            downloadResponse = null
                            continue
                        }
                        else -> {
                            Timber.d("Download check returned: ${downloadResponse.code} ${downloadResponse.message} from: $downloadUrl")
                            downloadResponse.close()
                            foundWorkingUrl = true
                            break
                        }
                    }
                }
                
                if (!foundWorkingUrl) {
                    Timber.w("All download URL variants returned 404, waiting and retrying...")
                }
                
                delay(1000)
                attempts++
            }
            
            Timber.w("Scan job timeout after $maxAttempts attempts")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error polling scan job status")
            null
        }
    }
    
    /**
     * Downloads the scanned document using a specific URL.
     */
    private suspend fun downloadScanResultWithUrl(downloadUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = null
        try {
            Timber.d("Downloading scan result from specific URL: $downloadUrl")
            
            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Timber.e("Failed to download scan result: ${response.code} ${response.message}")
                response.close()
                return@withContext null
            }
            
            val contentType = response.header("Content-Type") ?: ""
            val contentLength = response.header("Content-Length")
            Timber.d("Download response: Content-Type=$contentType, Content-Length=$contentLength, Code=${response.code}")
            
            val body = response.body ?: run {
                Timber.e("Response body is null")
                response.close()
                return@withContext null
            }
            
            // Read bytes manually from stream to avoid chunked transfer encoding issues
            val bytes = try {
                val inputStream = body.byteStream()
                val buffer = java.io.ByteArrayOutputStream()
                val tempBuffer = ByteArray(8192)
                var bytesRead: Int
                
                while (inputStream.read(tempBuffer).also { bytesRead = it } != -1) {
                    buffer.write(tempBuffer, 0, bytesRead)
                }
                
                inputStream.close()
                buffer.toByteArray()
            } catch (e: Exception) {
                Timber.e(e, "Failed to read response body as bytes")
                response.close()
                return@withContext null
            }
            
            Timber.d("Read ${bytes.size} bytes from scan result")
            
            if (bytes.isEmpty()) {
                Timber.e("No data received from scanner")
                response.close()
                return@withContext null
            }
            
            // Process bytes based on content type
            return@withContext processDownloadedBytes(bytes, contentType)
        } catch (e: Exception) {
            Timber.e(e, "Error downloading scan result")
            inputStream?.close()
            null
        }
    }
    
    /**
     * Downloads the scanned document from the job.
     */
    suspend fun downloadScanResult(address: String, jobUri: String): Bitmap? = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = null
        try {
            // Try different URL variants - some scanners use different endpoints
            val urlVariants = listOf(
                // Variant 1: Full URI with /NextDocument
                if (jobUri.startsWith("http")) {
                    "$jobUri/NextDocument"
                } else {
                    "http://$address$jobUri/NextDocument"
                },
                // Variant 2: Job URI directly (some scanners return the download URL)
                if (jobUri.startsWith("http")) {
                    jobUri
                } else {
                    "http://$address$jobUri"
                },
                // Variant 3: Try with different path structure
                if (jobUri.startsWith("http")) {
                    jobUri.replace("/ScanJobs/", "/ScanJobs/").let { 
                        if (!it.endsWith("/NextDocument")) "$it/NextDocument" else it
                    }
                } else {
                    "http://$address/eSCL/ScanJobs/${jobUri.substringAfterLast("/")}/NextDocument"
                }
            )
            
            var response: Response? = null
            var lastError: String? = null
            
            for ((index, url) in urlVariants.withIndex()) {
                Timber.d("Trying download URL variant ${index + 1}: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                response = client.newCall(request).execute()
                
                // 204 No Content means no more documents available
                if (response.code == 204) {
                    Timber.d("No content available (204)")
                    response.close()
                    if (index < urlVariants.size - 1) {
                        continue // Try next variant
                    }
                    return@withContext null
                }
                
                if (response.isSuccessful) {
                    Timber.d("Successfully connected using variant ${index + 1}")
                    break // Found working URL
                } else {
                    lastError = "${response.code} ${response.message}"
                    Timber.w("Variant ${index + 1} failed: $lastError")
                    response.close()
                    response = null
                    if (index < urlVariants.size - 1) {
                        continue // Try next variant
                    }
                }
            }
            
            if (response == null || !response.isSuccessful) {
                Timber.e("Failed to download scan result after trying all variants. Last error: $lastError")
                response?.close()
                return@withContext null
            }
            
            val contentType = response.header("Content-Type") ?: ""
            val contentLength = response.header("Content-Length")
            Timber.d("Download response: Content-Type=$contentType, Content-Length=$contentLength, Code=${response.code}")
            
            val body = response.body ?: run {
                Timber.e("Response body is null")
                response.close()
                return@withContext null
            }
            
            // Read bytes manually from stream to avoid chunked transfer encoding issues
            // Some scanners send malformed chunked responses that OkHttp can't parse
            val bytes = try {
                val inputStream = body.byteStream()
                val buffer = java.io.ByteArrayOutputStream()
                val tempBuffer = ByteArray(8192)
                var bytesRead: Int
                
                while (inputStream.read(tempBuffer).also { bytesRead = it } != -1) {
                    buffer.write(tempBuffer, 0, bytesRead)
                }
                
                inputStream.close()
                buffer.toByteArray()
            } catch (e: Exception) {
                Timber.e(e, "Failed to read response body as bytes")
                response.close()
                return@withContext null
            }
            
            Timber.d("Read ${bytes.size} bytes from scan result")
            
            if (bytes.isEmpty()) {
                Timber.e("No data received from scanner")
                response.close()
                return@withContext null
            }
            
            // Process bytes based on content type
            return@withContext processDownloadedBytes(bytes, contentType)
        } catch (e: Exception) {
            Timber.e(e, "Error downloading scan result")
            inputStream?.close()
            null
        }
    }
    
    /**
     * Processes downloaded bytes and converts them to a Bitmap.
     */
    private fun processDownloadedBytes(bytes: ByteArray, contentType: String): Bitmap? {
        // Log first few bytes to identify format
        val headerBytes = bytes.take(16).joinToString(" ") { "%02X".format(it) }
        val headerString = bytes.take(16).map { if (it in 32..<127) it.toInt().toChar() else '.' }.joinToString("")
        Timber.d("Response header bytes (hex): $headerBytes")
        Timber.d("Response header bytes (ascii): $headerString")
        
        // Check content type and handle accordingly
        return when {
            contentType.contains("application/pdf", ignoreCase = true) || 
            (bytes.size >= 4 && bytes[0] == '%'.toByte() && bytes[1] == 'P'.toByte() && bytes[2] == 'D'.toByte() && bytes[3] == 'F'.toByte()) -> {
                // PDF format - convert to bitmap
                Timber.d("Detected PDF format, converting to bitmap")
                convertPdfToBitmap(bytes)
            }
            contentType.contains("image/", ignoreCase = true) || 
            (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) || // JPEG
            (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) -> { // PNG
                // Image format - decode directly
                Timber.d("Detected image format: $contentType")
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                if (bitmap == null) {
                    Timber.e("Failed to decode bitmap from image data")
                    null
                } else {
                    Timber.d("Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}, format: ${bitmap.config}")
                    bitmap
                }
            }
            else -> {
                // Unknown format - try to decode as image anyway
                Timber.w("Unknown content type: $contentType, attempting to decode as image")
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                if (bitmap != null) {
                    Timber.d("Successfully decoded unknown format as bitmap: ${bitmap.width}x${bitmap.height}")
                    bitmap
                } else {
                    Timber.e("Failed to decode unknown format as bitmap")
                    null
                }
            }
        }
    }
    
    /**
     * Converts PDF bytes to Bitmap (first page only).
     */
    private fun convertPdfToBitmap(pdfBytes: ByteArray): Bitmap? {
        var tempFile: File? = null
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        
        return try {
            // Write PDF bytes to temporary file
            tempFile = File.createTempFile("scan_", ".pdf")
            FileOutputStream(tempFile).use { out ->
                out.write(pdfBytes)
            }
            
            // Open PDF file descriptor
            fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            
            if (pdfRenderer.pageCount == 0) {
                Timber.e("PDF has no pages")
                return null
            }
            
            // Render first page
            val page = pdfRenderer.openPage(0)
            val width = page.width
            val height = page.height
            
            // Create bitmap with appropriate size (scale down if too large)
            val scale = 1.0f
            val scaledWidth = (width * scale).toInt()
            val scaledHeight = (height * scale).toInt()
            
            val bitmap = createBitmap(scaledWidth, scaledHeight)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            
            Timber.d("Successfully converted PDF to bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert PDF to bitmap")
            null
        } finally {
            pdfRenderer?.close()
            try {
                fileDescriptor?.close()
            } catch (e: Exception) {
                Timber.e(e, "Error closing file descriptor")
            }
            tempFile?.delete()
        }
    }
    
    /**
     * Downloads all available pages from a scan job (for ADF).
     */
    suspend fun downloadAllPages(
        address: String,
        jobUri: String,
        onPageDownloaded: (Bitmap, Int) -> Unit
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<Bitmap>()
        var pageNumber = 1
        
        while (true) {
            val bitmap = downloadScanResult(address, jobUri)
            if (bitmap != null) {
                pages.add(bitmap)
                onPageDownloaded(bitmap, pageNumber)
                pageNumber++
                delay(200) // Brief delay between page downloads
            } else {
                // No more pages available
                break
            }
        }
        
        pages
    }
    
    /**
     * Parses the job state from XML response.
     */
    private fun parseJobState(xml: String): String {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xml.byteInputStream())
            
            // Try to find JobState element (various possible names)
            val possibleTags = listOf(
                "JobState",
                "pwg:JobState",
                "scan:JobState",
                "jobState",
                "job-state"
            )
            
            for (tag in possibleTags) {
                val elements = doc.getElementsByTagName(tag)
                if (elements.length > 0) {
                    val state = elements.item(0).textContent?.trim() ?: ""
                    if (state.isNotEmpty()) {
                        Timber.d("Found job state in tag '$tag': '$state'")
                        return state
                    }
                }
            }
            
            // Try to find by XPath or direct traversal
            val root = doc.documentElement
            root?.let {
                // Look for JobState in all child nodes
                val nodes = it.childNodes
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    if (node.nodeName.contains("JobState", ignoreCase = true)) {
                        val state = node.textContent?.trim() ?: ""
                        if (state.isNotEmpty()) {
                            Timber.d("Found job state in node '${node.nodeName}': '$state'")
                            return state
                        }
                    }
                }
            }
            
            Timber.w("Could not find JobState element in XML")
            "Unknown"
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse job state XML")
            // Fallback: simple string search
            val patterns = listOf(
                "<pwg:JobState>",
                "<JobState>",
                "<scan:JobState>",
                "<jobState>",
                "JobState>"
            )
            
            for (pattern in patterns) {
                val index = xml.indexOf(pattern, ignoreCase = true)
                if (index >= 0) {
                    val start = index + pattern.length
                    val end = xml.indexOf("<", start)
                    if (end > start) {
                        val state = xml.substring(start, end).trim()
                        Timber.d("Found job state via fallback pattern '$pattern': '$state'")
                        return state
                    }
                }
            }
            
            Timber.w("Could not parse job state from XML: ${xml.take(200)}...")
            "Unknown"
        }
    }
    
    /**
     * Performs a complete scan operation.
     * For ADF source, this scans only the first page. Use performMultiPageScan for all pages.
     */
    suspend fun performScan(
        address: String,
        source: ScanSource,
        onProgress: (Int, String) -> Unit
    ): Bitmap? {
        onProgress(0, "Checking scanner capabilities...")
        
        // Check eSCL support
        val hasEscl = checkEsclSupport(address)
        if (!hasEscl) {
            Timber.w("Scanner $address does not support eSCL")
            return null
        }
        
        onProgress(10, "Creating scan job...")
        val jobUri = createScanJob(address, source) ?: run {
            Timber.e("Failed to create scan job")
            return null
        }
        
        onProgress(20, "Scanning...")
        val result = waitForScanJob(address, jobUri)
        if (result == null) {
            Timber.e("Scan job did not complete successfully")
            return null
        }
        
        onProgress(80, "Downloading scan result...")
        
        val bitmap = when (result) {
            is ScanJobResult.Url -> {
                // Use the working URL from waitForScanJob, or fallback to constructing from jobUri
                downloadScanResultWithUrl(result.url)
                    ?: downloadScanResult(address, jobUri) // Fallback to original method
            }
            is ScanJobResult.Data -> {
                Timber.d("Using already downloaded data from polling phase")
                processDownloadedBytes(result.bytes, result.contentType)
            }
        }
        
        if (bitmap != null) {
            onProgress(100, "Scan completed")
        } else {
            Timber.e("Failed to download scan result")
        }
        
        return bitmap
    }
    
    /**
     * Performs a multi-page scan operation (for ADF).
     */
    suspend fun performMultiPageScan(
        address: String,
        source: ScanSource,
        onProgress: (Int, String) -> Unit
    ): List<Bitmap> {
        onProgress(0, "Checking scanner capabilities...")
        
        // Check eSCL support
        val hasEscl = checkEsclSupport(address)
        if (!hasEscl) {
            Timber.w("Scanner $address does not support eSCL")
            return emptyList()
        }
        
        onProgress(10, "Creating scan job...")
        val jobUri = createScanJob(address, source) ?: run {
            Timber.e("Failed to create scan job")
            return emptyList()
        }
        
        onProgress(20, "Scanning...")
        val result = waitForScanJob(address, jobUri)
        if (result == null) {
            Timber.e("Scan job did not complete successfully")
            return emptyList()
        }
        
        onProgress(50, "Downloading pages...")
        val pages = mutableListOf<Bitmap>()
        var pageNumber = 1
        var downloadUrl: String  // Default

        // Handle the first page (or setup the URL)
        when (result) {
            is ScanJobResult.Url -> {
                downloadUrl = result.url
                // Download the first page using the URL
                Timber.d("Downloading first page from URL: $downloadUrl")
                val firstBitmap = downloadScanResultWithUrl(downloadUrl)
                    ?: downloadScanResult(address, downloadUrl)
                if (firstBitmap != null) {
                    pages.add(firstBitmap)
                    val progress = 50 + ((pageNumber * 50) / 10).coerceAtMost(95)
                    onProgress(progress, "Downloaded page $pageNumber")
                    pageNumber++
                }
            }
            is ScanJobResult.Data -> {
                Timber.d("Processing first page from polling phase")
                downloadUrl = result.sourceUrl
                val bitmap = processDownloadedBytes(result.bytes, result.contentType)
                if (bitmap != null) {
                    pages.add(bitmap)
                    // Update progress for first page
                    val progress = 50 + ((pageNumber * 50) / 10).coerceAtMost(95)
                    onProgress(progress, "Downloaded page $pageNumber")
                    pageNumber++
                }
            }
        }
        
        // Download remaining pages using NextDocument endpoint
        // downloadAllPages will fetch subsequent pages starting from the second page
        val additionalPages = downloadAllPages(address, downloadUrl) { bitmap, n ->
            // Adjust page number offset if we already had one
            val actualPageNum = pageNumber + (n - 1)
            val progress = 50 + ((actualPageNum * 50) / 10).coerceAtMost(95)
            onProgress(progress, "Downloaded page $actualPageNum")
        }
        
        pages.addAll(additionalPages)
        
        if (pages.isNotEmpty()) {
            onProgress(100, "Scan completed: ${pages.size} page(s)")
        } else {
            Timber.e("No pages downloaded")
        }
        
        return pages
    }
}

