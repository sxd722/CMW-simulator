package com.example.cmw_simulator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Sends DSL code to the local Ktor backend for RC compilation.
 * The backend runs on the host machine and returns compiled ByteArray.
 */
object RcCompiler {

    private val client = OkHttpClient()
    private const val BACKEND_URL = "http://10.0.2.2:8080/compile-rc"

    /**
     * Posts the Gemini-generated DSL string to the local backend
     * and returns the compiled RC bytes.
     */
    suspend fun fetchRcBytesFromBackend(dslCode: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val body = dslCode.toRequestBody("text/plain; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(BACKEND_URL)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        Log.d("RcCompiler", "Received ${bytes?.size ?: 0} bytes from backend")
                        bytes
                    } else {
                        val errorBody = response.body?.string()?.take(500) ?: "unknown"
                        Log.e("RcCompiler", "Backend error ${response.code}: $errorBody")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("RcCompiler", "Failed to reach backend", e)
                null
            }
        }
}
