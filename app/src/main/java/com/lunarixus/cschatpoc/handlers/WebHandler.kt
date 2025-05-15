package com.lunarixus.cschatpoc.handlers

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Handles network communication with a remote chat server.
 */
class WebHandler {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val serverUrl = "http://190.10.118.54:5000" // TODO: Don't hardcode this

    /**
     * Sends a message to the python server.
     *
     * @param query The user's query message.
     * @param callback A callback function to process responses from the server.
     *                 The first parameter is the response text, and the second is the "thinking" status (if supported by the model).
     * @param onComplete A callback function that is executed upon request completion.
     */
    fun sendMessage(query: String, callback: (String, String) -> Unit, onComplete: () -> Unit) {
        val json = """{"query": "$query"}""".trimIndent()
        val requestBody = RequestBody.create("application/json".toMediaType(), json)

        val request = Request.Builder()
            .url("$serverUrl/chat")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Error: ${e.message}", "")
                onComplete()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val inputStream = response.body?.byteStream()
                    if (inputStream != null) {
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val responseBuilder = StringBuilder()
                        val thinkingBuilder = StringBuilder()
                        var isThinking = false
                        var line: String?

                        // Server uses streamed responses
                        // Some models do 'thinking' which we need to split from
                        // the actual response
                        while (reader.readLine().also { line = it } != null) {
                            if (line!!.isNotBlank()) {
                                when {
                                    line!!.contains("<think>") -> {
                                        isThinking = true
                                        val cleanLine = line!!.replace("<think>", "").trim()
                                        thinkingBuilder.append(cleanLine).append(" ")
                                        callback(
                                            "",
                                            thinkingBuilder.toString().trim()
                                        )
                                    }

                                    isThinking && line!!.contains("</think>") -> {
                                        isThinking = false
                                        val cleanLine = line!!.replace("</think>", "").trim()
                                        thinkingBuilder.append(cleanLine)
                                            .append(" ")
                                        callback(
                                            "",
                                            thinkingBuilder.toString().trim()
                                        )
                                    }

                                    isThinking -> {
                                        val cleanLine = line!!.trim()
                                        thinkingBuilder.append(cleanLine).append(" ")
                                        callback(
                                            "",
                                            thinkingBuilder.toString().trim()
                                        )
                                    }

                                    else -> {
                                        responseBuilder.append(line).append("\n")
                                        callback(
                                            responseBuilder.toString().trim(),
                                            thinkingBuilder.toString().trim()
                                        )
                                    }
                                }
                            }
                        }

                        val fullResponse = responseBuilder.toString().trim()

                        if (fullResponse.isNotEmpty()) {
                            callback(
                                fullResponse,
                                thinkingBuilder.toString().trim()
                            )
                        }
                    } else {
                        callback("Error: Empty response", "")
                    }
                    onComplete()
                }
            }
        })
    }

    /**
     * Tests the connection to the server by hitting the /health endpoint.
     *
     * @param onResult A callback function that returns `true` if the server responds with "online", `false` otherwise.
     */
    fun testConnection(onResult: (Boolean) -> Unit) {
        val request = Request.Builder()
            .url("$serverUrl/health")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = response.body?.string()?.trim()
                    onResult(response.isSuccessful && body == "online")
                }
            }
        })
    }
}