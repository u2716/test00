package chimahon.gemini

import android.graphics.Bitmap
import android.util.Base64
import android.util.JsonReader
import android.util.Log
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader

sealed interface GeminiError {
    object NoApiKey : GeminiError
    object NoInput : GeminiError
    object CustomEndpointBlank : GeminiError
    object CustomEndpointInvalid : GeminiError
    data class Http(val code: Int, val message: String) : GeminiError
    data class Generic(val message: String) : GeminiError
}

class HttpAnalysisException(val code: Int, override val message: String) : Exception("Error: $code $message")

class GeminiService(
    baseClient: OkHttpClient,
    private val preferences: GeminiPreferences,
) {
    // Gemini streams tokens over a long-lived connection. OkHttp's default
    // 10s read timeout aborts any request where the server is silent for
    // more than 10s — which happens on cold model start, or between tokens
    // if the model is thinking. 60s is the read timeout only: it fails
    // after 60s of *silence*, not after 60s total, so a stream that is
    // still producing tokens is never cut off. Deliberately NOT using
    // callTimeout, which would cap the entire response.
    private val client: OkHttpClient = baseClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(60, TimeUnit.SECONDS)
        .build()

    val fontSize: Int get() = preferences.geminiFontSize
    val fontFamily: String get() = preferences.geminiFontFamily
    fun buildUrl(model: String, customEndpoint: String): String {
        return if (model == GeminiConfig.MODEL_CUSTOM) {
            val raw = customEndpoint.trim()
            require(raw.isNotBlank()) { "CUSTOM_ENDPOINT_BLANK" }
            val httpUrl = raw.toHttpUrlOrNull()
            require(httpUrl != null && httpUrl.isHttps) { "CUSTOM_ENDPOINT_INVALID" }

            val pathSegments = httpUrl.pathSegments.toMutableList()
            if (pathSegments.isNotEmpty()) {
                val lastIndex = pathSegments.size - 1
                val lastSegment = pathSegments[lastIndex]
                val cleanedLastSegment = lastSegment
                    .removeSuffix(GeminiConfig.STREAM_ENDPOINT_SUFFIX)
                    .removeSuffix(":generateContent")
                pathSegments[lastIndex] = cleanedLastSegment + GeminiConfig.STREAM_ENDPOINT_SUFFIX
            }

            httpUrl.newBuilder()
                .apply {
                    encodedPath("/")
                    pathSegments.forEach { addPathSegment(it) }
                }
                .build()
                .toString()
        } else {
            "${GeminiConfig.BASE_URL}$model${GeminiConfig.STREAM_ENDPOINT_SUFFIX}"
        }
    }

    fun streamAnalysis(
        text: String?,
        screenshot: Bitmap?,
        forceText: Boolean = false,
        forceScreenshot: Boolean = false,
    ): Flow<String> = flow {
        val apiKey = preferences.geminiApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("API_KEY_BLANK")
        }

        val model = preferences.geminiModel
        val customEndpoint = preferences.geminiCustomEndpoint
        val sendScreenshot = preferences.geminiSendScreenshot
        val prompt = preferences.geminiPrompt.ifBlank { GeminiConfig.DEFAULT_PROMPT }
        val url = buildUrl(model, customEndpoint)

        val cleanedText = text?.trim().orEmpty()
        val truncatedText = if (cleanedText.length > GeminiConfig.MAX_OCR_TEXT_CHARS) {
            cleanedText.take(GeminiConfig.MAX_OCR_TEXT_CHARS) + "\n...[truncated]"
        } else {
            cleanedText
        }

        // Precedence: forceText > forceScreenshot > sendScreenshot pref.
        // The pref is only respected when neither force flag is set, so
        // the manga/novel readers always send their bitmap and the video
        // player always sends text, regardless of the global toggle.
        val wantScreenshot = when {
            forceText -> false
            forceScreenshot -> true
            else -> sendScreenshot
        }
        val useScreenshot = wantScreenshot && screenshot != null
        if (!useScreenshot && truncatedText.isBlank()) {
            throw IllegalArgumentException("NO_INPUT")
        }

        val partsArray = JSONArray()
        if (useScreenshot) {
            partsArray.put(JSONObject().apply { put("text", prompt) })
            val stream = ByteArrayOutputStream()
            screenshot.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val base64Data = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            partsArray.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Data)
                })
            })
        } else {
            partsArray.put(JSONObject().apply { put("text", "$prompt\n\n$truncatedText") })
        }

        val requestBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader(GeminiConfig.HEADER_API_KEY, apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        val coroutineJob = currentCoroutineContext()[Job]
        val cancellationHandle = coroutineJob?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    val errorMessage = parseErrorMessage(errorBody).ifBlank { response.message }
                    throw HttpAnalysisException(response.code, errorMessage)
                }

                val responseStream = response.body?.byteStream() ?: return@use
                // Gemini's streamGenerateContent API returns incremental delta chunks for
                // each candidate part in the response stream, so accumulating chunks via
                // `content += delta` in the caller is the expected and correct behavior.
                parseResponseStream(responseStream) { delta ->
                    emit(delta)
                }
            }
        } finally {
            cancellationHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun parseResponseStream(inputStream: InputStream, onDelta: suspend (String) -> Unit) {
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8")).apply { isLenient = true }
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            while (reader.hasNext()) {
                parseCandidateChunk(reader, onDelta)
            }
            reader.endArray()
        } else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
            parseCandidateChunk(reader, onDelta)
        }
    }

    suspend fun parseCandidateChunk(reader: JsonReader, onDelta: suspend (String) -> Unit) {
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "candidates" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "content" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                if (reader.nextName() == "parts" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            if (reader.nextName() == "text" && reader.peek() == JsonToken.STRING) {
                                                onDelta(reader.nextString())
                                            } else {
                                                reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    }
                                    reader.endArray()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
    }

    fun parseErrorMessage(json: String): String {
        return try {
            val obj = JSONObject(json)
            if (obj.has("error")) {
                obj.getJSONObject("error").optString("message", "")
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w("GeminiService", "Failed to parse error body: ${e.message}")
            ""
        }
    }
}
