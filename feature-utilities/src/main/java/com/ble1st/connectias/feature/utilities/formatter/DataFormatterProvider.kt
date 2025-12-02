package com.ble1st.connectias.feature.utilities.formatter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.w3c.dom.Document
import org.xml.sax.InputSource
import timber.log.Timber
import java.io.StringReader
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Provider for data formatting functionality.
 * Supports JSON, XML, and YAML formatting.
 */
@Singleton
class DataFormatterProvider @Inject constructor() {

    private val prettyJson = Json { prettyPrint = true; isLenient = true }
    private val compactJson = Json { isLenient = true }

    /**
     * Formats JSON with indentation.
     */
    fun formatJson(input: String, indent: Int = 2): FormatResult {
        return try {
            val obj = JSONTokener(input).nextValue()
            val formatted = when (obj) {
                is JSONObject -> obj.toString(indent)
                is JSONArray -> obj.toString(indent)
                else -> input
            }
            FormatResult(
                success = true,
                output = formatted,
                inputType = DataType.JSON
            )
        } catch (e: Exception) {
            Timber.e(e, "Error formatting JSON")
            FormatResult(
                success = false,
                output = input,
                error = "Invalid JSON: ${e.message}",
                inputType = DataType.JSON
            )
        }
    }

    /**
     * Minifies JSON by removing whitespace.
     */
    fun minifyJson(input: String): FormatResult {
        return try {
            val obj = JSONTokener(input).nextValue()
            val minified = when (obj) {
                is JSONObject -> obj.toString()
                is JSONArray -> obj.toString()
                else -> input
            }
            FormatResult(
                success = true,
                output = minified,
                inputType = DataType.JSON
            )
        } catch (e: Exception) {
            FormatResult(
                success = false,
                output = input,
                error = "Invalid JSON: ${e.message}",
                inputType = DataType.JSON
            )
        }
    }

    /**
     * Formats XML with indentation.
     */
    fun formatXml(input: String, indent: Int = 2): FormatResult {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(InputSource(StringReader(input)))

            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indent.toString())
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")

            val writer = StringWriter()
            transformer.transform(DOMSource(document), StreamResult(writer))

            FormatResult(
                success = true,
                output = writer.toString(),
                inputType = DataType.XML
            )
        } catch (e: Exception) {
            Timber.e(e, "Error formatting XML")
            FormatResult(
                success = false,
                output = input,
                error = "Invalid XML: ${e.message}",
                inputType = DataType.XML
            )
        }
    }

    /**
     * Formats YAML (basic implementation).
     */
    fun formatYaml(input: String): FormatResult {
        // Basic YAML formatting (proper implementation would use a YAML library)
        return try {
            val lines = input.lines()
            val formatted = lines.joinToString("\n") { line ->
                val trimmed = line.trimStart()
                val indent = line.takeWhile { it.isWhitespace() }
                val normalizedIndent = " ".repeat((indent.length / 2) * 2)
                normalizedIndent + trimmed
            }
            FormatResult(
                success = true,
                output = formatted,
                inputType = DataType.YAML
            )
        } catch (e: Exception) {
            FormatResult(
                success = false,
                output = input,
                error = "Error formatting YAML: ${e.message}",
                inputType = DataType.YAML
            )
        }
    }

    /**
     * Converts JSON to XML.
     */
    fun convertJsonToXml(json: String): ConversionResult {
        return try {
            val jsonObj = JSONObject(json)
            val xml = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<root>")
                jsonToXmlRecursive(jsonObj, this, 1)
                append("</root>")
            }
            ConversionResult(
                success = true,
                output = xml,
                sourceType = DataType.JSON,
                targetType = DataType.XML
            )
        } catch (e: Exception) {
            ConversionResult(
                success = false,
                output = "",
                sourceType = DataType.JSON,
                targetType = DataType.XML,
                error = "Conversion failed: ${e.message}"
            )
        }
    }

    private fun jsonToXmlRecursive(obj: Any?, builder: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)
        when (obj) {
            is JSONObject -> {
                for (key in obj.keys()) {
                    val value = obj.get(key)
                    builder.append("$indentStr<$key>")
                    when (value) {
                        is JSONObject, is JSONArray -> {
                            builder.appendLine()
                            jsonToXmlRecursive(value, builder, indent + 1)
                            builder.append(indentStr)
                        }
                        else -> builder.append(value.toString())
                    }
                    builder.appendLine("</$key>")
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    builder.append("$indentStr<item>")
                    val item = obj.get(i)
                    when (item) {
                        is JSONObject, is JSONArray -> {
                            builder.appendLine()
                            jsonToXmlRecursive(item, builder, indent + 1)
                            builder.append(indentStr)
                        }
                        else -> builder.append(item.toString())
                    }
                    builder.appendLine("</item>")
                }
            }
        }
    }

    /**
     * Validates JSON syntax.
     */
    fun validateJson(input: String): ValidationResult {
        return try {
            JSONTokener(input).nextValue()
            ValidationResult(
                isValid = true,
                dataType = DataType.JSON
            )
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                dataType = DataType.JSON,
                error = e.message,
                errorPosition = findErrorPosition(input, e.message)
            )
        }
    }

    /**
     * Queries JSON using JSONPath-like syntax.
     */
    fun queryJsonPath(json: String, path: String): QueryResult {
        return try {
            val obj = JSONObject(json)
            val parts = path.removePrefix("$.").split(".")
            var current: Any? = obj

            for (part in parts) {
                current = when (current) {
                    is JSONObject -> current.opt(part)
                    is JSONArray -> {
                        val index = part.toIntOrNull()
                        if (index != null) current.opt(index) else null
                    }
                    else -> null
                }
                if (current == null) break
            }

            QueryResult(
                success = true,
                result = current?.toString() ?: "null",
                path = path
            )
        } catch (e: Exception) {
            QueryResult(
                success = false,
                result = "",
                path = path,
                error = e.message
            )
        }
    }

    /**
     * Detects the data type of input.
     */
    fun detectType(input: String): DataType {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> DataType.JSON
            trimmed.startsWith("<") -> DataType.XML
            trimmed.contains(":") && !trimmed.startsWith("{") -> DataType.YAML
            else -> DataType.UNKNOWN
        }
    }

    private fun findErrorPosition(input: String, errorMessage: String?): ErrorPosition? {
        // Try to extract position from error message
        return null
    }
}

/**
 * Supported data types.
 */
enum class DataType {
    JSON,
    XML,
    YAML,
    UNKNOWN
}

/**
 * Result of formatting operation.
 */
@Serializable
data class FormatResult(
    val success: Boolean,
    val output: String,
    val inputType: DataType,
    val error: String? = null
)

/**
 * Result of conversion operation.
 */
@Serializable
data class ConversionResult(
    val success: Boolean,
    val output: String,
    val sourceType: DataType,
    val targetType: DataType,
    val error: String? = null
)

/**
 * Result of validation operation.
 */
@Serializable
data class ValidationResult(
    val isValid: Boolean,
    val dataType: DataType,
    val error: String? = null,
    val errorPosition: ErrorPosition? = null
)

/**
 * Position of an error in the input.
 */
@Serializable
data class ErrorPosition(
    val line: Int,
    val column: Int
)

/**
 * Result of JSON path query.
 */
@Serializable
data class QueryResult(
    val success: Boolean,
    val result: String,
    val path: String,
    val error: String? = null
)
