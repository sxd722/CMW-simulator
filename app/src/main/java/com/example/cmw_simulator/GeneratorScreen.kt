package com.example.cmw_simulator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient as OkHttp3Client
import okhttp3.Request as OkHttp3Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

val REMOTE_COMPOSE_SYSTEM_PROMPT = """
You are an on-device UI Generative Agent for Android. Your objective is to translate natural language user requests into structural widget layouts using the official androidx.compose.remote Kotlin DSL.

Strict Constraints:



Output ONLY valid Kotlin DSL code. Do not include explanations, markdown formatting, or JSON.

NEVER use standard Jetpack Compose primitives (e.g., Column, Text, Modifier). You must exclusively use Remote Compose equivalents: RemoteColumn, RemoteRow, RemoteBox, RemoteText, RemoteButton, and RemoteSpacer.

All styling and layout adjustments must be chained using RemoteModifier. For example, use RemoteModifier.fillMaxSize().padding(16.rdp) instead of Modifier.padding(16.dp).

For sizes, dimensions, and colors, you must append the remote-specific extension functions to primitive values: use .rdp for density-independent pixels, .rsp for scalable text pixels, and .rc for colors (e.g., Color.White.rc or Color(0xFF1E1E1E).rc).

Text styling must be applied using RemoteTextStyle.

For interactivity, securely emit predefined integer action IDs using HostAction(actionId = <Int>). Never attempt to route navigation using raw strings or OS-level Intents.

Defined Skills / API Primitives

To ensure a small LLM operates reliably, provide it with this strict subset of acceptable functions (skills) it is allowed to construct:

1. Layout Skills



RemoteColumn(modifier: RemoteModifier) {... }

RemoteRow(modifier: RemoteModifier) {... }

RemoteBox(modifier: RemoteModifier, contentAlignment: RemoteAlignment) {... }

RemoteSpacer(modifier: RemoteModifier)

2. Component Skills



RemoteText(text: String, color: RemoteColor, fontSize: RemoteTextUnit, fontWeight: FontWeight)

RemoteButton(onClick: Action) {... }

RemoteImage(url: String, contentScale: ContentScale, modifier: RemoteModifier) 

3. Modifier Skills



Chain capabilities starting strictly with RemoteModifier.

Allowed chain operations: .fillMaxSize(), .fillMaxWidth(), .height(value.rdp), .width(value.rdp), .padding(value.rdp), and .background(Color.rc).

""".trimIndent()

val SDUI_JSON_SYSTEM_PROMPT = """
You are an expert Server-Driven UI (SDUI) generator for Jetpack Compose Remote. Your task is to convert UI requirements into a strict JSON representation that directly maps to standard `RemoteCompose` DSL components.

You must output ONLY valid, raw JSON. Do not include introductory text, explanations, or markdown code block formatting (do NOT use ```json).

# JSON Schema Definition

The root of the JSON must be an object containing:
- "backgroundColor": A hex color string (e.g., "#FFFFFF").
- "elements": An array of UI component objects.

Every UI component in the "elements" or "children" arrays MUST have:
- "type": A string matching a standard RemoteCompose component exactly.
- "id": A unique string identifier (e.g., "1", "2", "3").
- "modifier": An optional object containing styling and layout instructions.

## Supported RemoteCompose Component Types

1. "RemoteText"
   - "text" (String): The text to display.
   - "color" (String): Hex color code.
   - "fontSize" (Integer): Font size (mapped to rsp).
   - "fontWeight" (String, optional): "Normal", "Medium", "Bold".

2. "RemoteImage"
   - "url" (String): URL of the image to load.
   - "contentDescription" (String, optional): Accessibility text.

3. "RemoteSpacer"
   - No extra properties. Relies entirely on the "modifier" for width/height (mapped to rdp).

4. "RemoteColumn"
   - "verticalArrangement" (Integer, optional): Spacing between children.
   - "horizontalAlignment" (String, optional): "Start", "CenterHorizontally", "End".
   - "children" (Array): A list of nested UI components.

5. "RemoteRow"
   - "horizontalArrangement" (Integer, optional): Spacing between children.
   - "verticalAlignment" (String, optional): "Top", "CenterVertically", "Bottom".
   - "children" (Array): A list of nested UI components.

6. "RemoteBox"
   - "contentAlignment" (String, optional): "TopStart", "Center", "BottomEnd", etc.
   - "children" (Array): A list of nested UI components stacked on top of each other.

## Modifiers Definition (RemoteModifier)
Any component can include a "modifier" object. Supported modifier properties include:
- "padding" (Integer or Object): A single integer for all sides, or an object {"start": 0, "top": 0, "end": 0, "bottom": 0}.
- "fillMaxWidth" (Boolean): true to fill available width.
- "fillMaxHeight" (Boolean): true to fill available height.
- "fillMaxSize" (Boolean): true to fill both bounds.
- "width" (Integer): Fixed width.
- "height" (Integer): Fixed height.
- "background" (String): Hex color code for the background.
- "cornerRadius" (Integer): Border radius (mapped to RemoteRoundedCornerShape).
- "clickableAction" (String): The name of the `Action` to trigger when tapped (e.g., "navigate:home").

# Generation Rules
1. ALWAYS use valid hex codes for colors (e.g., "#000000").
2. Only use the component types listed above. Do not invent types like "card" or "button". You must build buttons and cards using `RemoteBox` or `RemoteRow` combined with a `clickableAction` modifier and a `cornerRadius` background modifier.
3. Keep the design clean, modern, and accessible.
4. Output raw JSON only.

# Example Expected Output:
{
  "backgroundColor": "#F5F5F5",
  "elements": [
    {
      "type": "RemoteColumn",
      "id": "1",
      "modifier": {
        "fillMaxSize": true,
        "padding": 16
      },
      "children": [
        {
          "type": "RemoteText",
          "id": "2",
          "text": "Native Server-Driven UI",
          "color": "#1A1A2E",
          "fontSize": 24,
          "fontWeight": "Bold"
        },
        {
          "type": "RemoteSpacer",
          "id": "3",
          "modifier": { "height": 16 }
        },
        {
          "type": "RemoteBox",
          "id": "4",
          "contentAlignment": "Center",
          "modifier": {
            "fillMaxWidth": true,
            "background": "#6200EA",
            "cornerRadius": 12,
            "padding": 16,
            "clickableAction": "action_submit"
          },
          "children": [
             {
                "type": "RemoteText",
                "id": "5",
                "text": "Tap to Submit",
                "color": "#FFFFFF",
                "fontSize": 16,
                "fontWeight": "Medium"
             }
          ]
        }
      ]
    }
  ]
}
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    viewModel: SharedRcViewModel,
    onGenerationSuccess: () -> Unit,
    onJsonRender: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Model selection state
    var selectedModel by remember { mutableStateOf("GLM 5.1") }
    val models = listOf("Gemini", "GLM 5.1", "GLM JSON", "Paste DSL", "Paste JSON")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            text = "AI → RC 生成器 v2",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val descriptionText = when (selectedModel) {
            "Paste DSL" -> "粘贴 Remote Compose Kotlin DSL 代码，发送到后端编译。"
            "Paste JSON" -> "粘贴 JSON UI 描述，直接在本地渲染，无需后端。"
            "GLM JSON" -> "输入自然语言描述，GLM 将生成 SDUI JSON 并在本地渲染。"
            else -> "输入自然语言描述，AI 将生成 Remote Compose 文档并预览。"
        }
        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Model Selection
        Text(
            text = "选择模式:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            models.forEach { model ->
                FilterChip(
                    selected = selectedModel == model,
                    onClick = { selectedModel = model },
                    label = { Text(model) }
                )
            }
        }

        // Prompt input
        val isPasteMode = selectedModel == "Paste DSL" || selectedModel == "Paste JSON"
        val labelText = when (selectedModel) {
            "Paste DSL" -> "Kotlin DSL 代码"
            "Paste JSON" -> "JSON UI"
            "GLM JSON" -> "描述你想要的 UI"
            else -> "描述你想要的 UI"
        }
        val placeholderText = when (selectedModel) {
            "Paste DSL" -> "粘贴 Kotlin DSL，例如：RemoteColumn { ... }"
            "Paste JSON" -> "粘贴 JSON，例如：{ \"backgroundColor\": \"#FFF\", \"elements\": [...] }"
            "GLM JSON" -> "例如：帮我生成一个包含标题和点赞按钮的商品卡片"
            else -> "例如：帮我生成一个包含标题和点赞按钮的商品卡片"
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it
                errorMessage = null
            },
            label = { Text(labelText) },
            placeholder = { Text(placeholderText) },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isPasteMode) 220.dp else 120.dp),
            maxLines = if (isPasteMode) 15 else 5,
            isError = errorMessage != null
        )

        // Error message
        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Status message
        statusMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }

        // Generate button
        val buttonText = when (selectedModel) {
            "Paste DSL" -> "🚀 编译并渲染"
            "Paste JSON" -> "🚀 直接渲染"
            "GLM JSON" -> "✨ 生成并渲染"
            else -> "✨ 生成并预览"
        }
        Button(
            onClick = {
                if (prompt.isBlank()) {
                    errorMessage = when (selectedModel) {
                        "Paste DSL" -> "请粘贴 Kotlin DSL 代码"
                        "Paste JSON" -> "请粘贴 JSON"
                        else -> "请输入 Prompt"
                    }
                    return@Button
                }
                isLoading = true
                errorMessage = null

                // ── Paste JSON: local render, no backend ──
                if (selectedModel == "Paste JSON") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val doc = parseJsonUiDocument(prompt)
                            viewModel.jsonUiDocument = doc
                            isLoading = false
                            onJsonRender()
                        } catch (e: Exception) {
                            errorMessage = "JSON 解析失败: ${e.message}"
                            isLoading = false
                        }
                    }
                    return@Button
                }

                // ── GLM JSON: generate SDUI JSON via GLM, render locally ──
                if (selectedModel == "GLM JSON") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在调用 GLM API 生成 SDUI JSON..."
                            }
                            val jsonResponse = callGlmJsonApi(prompt)
                            android.util.Log.d("Generator", "GLM JSON response: $jsonResponse")
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在解析 JSON..."
                            }
                            val cleaned = cleanJsonResponse(jsonResponse)
                            val doc = parseJsonUiDocument(cleaned)
                            withContext(Dispatchers.Main) {
                                viewModel.jsonUiDocument = doc
                                statusMessage = null
                                isLoading = false
                                onJsonRender()
                            }
                        } catch (e: Exception) {
                            val detail = e.message ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                errorMessage = "GLM JSON 生成失败: $detail"
                                statusMessage = null
                                isLoading = false
                            }
                        }
                    }
                    return@Button
                }

                // ── AI / Paste DSL: remote compilation ──
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val cleanedJson = if (selectedModel == "Paste DSL") {
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在解析 DSL..."
                            }
                            cleanJsonResponse(prompt)
                        } else {
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在调用 $selectedModel API..."
                            }
                            // 1. Call selected AI API
                            val jsonResponse = if (selectedModel == "Gemini") {
                                callGeminiApi(prompt)
                            } else {
                                callGlmApi(prompt)
                            }
                            withContext(Dispatchers.Main) {
                                android.util.Log.d("Generator", "AI response: $jsonResponse")
                                statusMessage = "$selectedModel 响应:\n${jsonResponse.take(200)}..."
                            }

                            // 2. Clean and parse JSON
                            val cleaned = cleanJsonResponse(jsonResponse)
                            android.util.Log.d("Generator", "Cleaned JSON: $cleaned")
                            cleaned
                        }

                        withContext(Dispatchers.Main) {
                            statusMessage = "正在编译 RC 文档..."
                        }

                        // 3. Compile DSL to RC bytes
                        val rcBytes = RcCompiler.fetchRcBytesFromBackend(cleanedJson)
                            ?: throw IllegalStateException("Backend compilation failed — no bytes returned")
                        withContext(Dispatchers.Main) {
                            statusMessage = "RC 文档生成成功 (${rcBytes.size} bytes)"
                        }

                        // 4. Store in ViewModel and navigate
                        withContext(Dispatchers.Main) {
                            viewModel.setGeneratedData(rcBytes, cleanedJson)
                            isLoading = false
                            onGenerationSuccess()
                        }
                    } catch (e: Exception) {
                        val detail = e.message ?: e.javaClass.simpleName
                        val cause = e.cause
                        val causeDetail = if (cause != null) "\nCause: ${cause.message ?: cause.javaClass.simpleName}" else ""
                        val stackTop = e.stackTrace.firstOrNull()?.let { "\n at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" } ?: ""
                        withContext(Dispatchers.Main) {
                            errorMessage = "操作失败: $detail$causeDetail$stackTop"
                            statusMessage = null
                            isLoading = false
                        }
                    }
                }
            },
            enabled = !isLoading && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("处理中...")
            } else {
                Text(buttonText)
            }
        }

        // Info section
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "使用说明",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = "1. 选择 AI 模型 (Gemini 或 GLM 5.1)\n" +
                    "2. 在上方输入你想要的 UI 描述\n" +
                    "3. 点击「生成并预览」按钮\n" +
                    "4. AI 将生成 JSON 并编译为 RC 文档\n" +
                    "5. 自动跳转到预览页面渲染\n" +
                    "6. 点击按钮可查看事件日志",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Show generated JSON if available
        viewModel.generatedJson?.let { json ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "上一次生成的 JSON:",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = Color(0xFFF5F5F5)
            ) {
                Text(
                    text = try {
                        JSONObject(json).toString(2)
                    } catch (_: Exception) {
                        json
                    },
                    modifier = Modifier.padding(8.dp),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Call the Gemini API with the user's prompt and system instruction.
 */
private suspend fun callGeminiApi(prompt: String): String = withContext(Dispatchers.IO) {
    try {
        val apiKey = getGeminiApiKey()

        val model = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            systemInstruction = content { text(REMOTE_COMPOSE_SYSTEM_PROMPT) }
        )

        val response = model.generateContent(prompt)
        val result = response.text ?: throw IllegalStateException("Empty response from Gemini")

        result
    } catch (e: Exception) {
        throw Exception("Gemini API 调用失败: ${e.message}", e)
    }
}

/**
 * Call the GLM 5.1 API (Zhipu AI) with the user's prompt.
 * Uses OpenAI-compatible REST API via OkHttp.
 */
private suspend fun callGlmApi(prompt: String): String = withContext(Dispatchers.IO) {
    try {
        val apiKey = "0a5cd5342cd24f2f8e4e44af433be613.AOsuKOY2hIaOKgVT"
        val client = OkHttp3Client()

        // Build request JSON
        val requestJson = JSONObject().apply {
            put("model", "glm-4-flash")
            put("messages", org.json.JSONArray().apply {
                // System message
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", REMOTE_COMPOSE_SYSTEM_PROMPT)
                })
                // User message
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 2048)
            put("temperature", 0.7)
        }

        val body = requestJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = OkHttp3Request.Builder()
            .url("https://open.bigmodel.cn/api/paas/v4/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw IllegalStateException("Empty response body from GLM")

        if (!response.isSuccessful) {
            throw IllegalStateException(
                "GLM API error ${response.code}: ${responseBody.take(300)}"
            )
        }

        // Parse OpenAI-compatible response
        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) {
            throw IllegalStateException("No choices in GLM response")
        }
        val message = choices.getJSONObject(0).getJSONObject("message")
        val content = message.getString("content")

        content
    } catch (e: Exception) {
        throw Exception("GLM API 调用失败: ${e.message}", e)
    }
}

/**
 * Call the GLM 5.1 API with the SDUI JSON system prompt.
 * Same API key and endpoint as callGlmApi, but uses SDUI_JSON_SYSTEM_PROMPT.
 */
private suspend fun callGlmJsonApi(prompt: String): String = withContext(Dispatchers.IO) {
    try {
        val apiKey = "0a5cd5342cd24f2f8e4e44af433be613.AOsuKOY2hIaOKgVT"
        val client = OkHttp3Client()

        val requestJson = JSONObject().apply {
            put("model", "glm-4-flash")
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SDUI_JSON_SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 4096)
            put("temperature", 0.7)
        }

        val body = requestJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = OkHttp3Request.Builder()
            .url("https://open.bigmodel.cn/api/paas/v4/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw IllegalStateException("Empty response body from GLM")

        if (!response.isSuccessful) {
            throw IllegalStateException(
                "GLM API error ${response.code}: ${responseBody.take(300)}"
            )
        }

        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) {
            throw IllegalStateException("No choices in GLM response")
        }
        val message = choices.getJSONObject(0).getJSONObject("message")
        message.getString("content")
    } catch (e: Exception) {
        throw Exception("GLM JSON API 调用失败: ${e.message}", e)
    }
}

/**
 * Get the Gemini API key from BuildConfig (injected at build time from local.properties).
 */
private fun getGeminiApiKey(): String {
    val key = com.example.cmw_simulator.BuildConfig.GEMINI_API_KEY
    if (key.isNullOrBlank()) {
        throw IllegalStateException(
            "未配置 Gemini API Key。请在 local.properties 中添加:\n" +
                    "GEMINI_API_KEY=your_api_key_here"
        )
    }
    return key
}

/**
 * Clean the response from AI — remove markdown code fences and thinking tags.
 * Returns the raw Kotlin DSL code.
 */
private fun cleanJsonResponse(response: String): String {
    var cleaned = response.trim()

    // Remove <think>...</think> tags (GLM deep thinking output)
    cleaned = cleaned.replace(Regex("(?s)<think.*?</think\\s*>"), "").trim()

    // Remove ```kotlin ... ``` or ```json ... ``` or ``` ... ``` wrappers
    if (cleaned.startsWith("```kotlin")) {
        cleaned = cleaned.removePrefix("```kotlin").removeSuffix("```").trim()
    } else if (cleaned.startsWith("```json")) {
        cleaned = cleaned.removePrefix("```json").removeSuffix("```").trim()
    } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.removePrefix("```").removeSuffix("```").trim()
    }

    return cleaned
}
