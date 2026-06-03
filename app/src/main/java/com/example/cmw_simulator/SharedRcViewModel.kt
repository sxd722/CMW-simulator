package com.example.cmw_simulator

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SharedRcViewModel : ViewModel() {
    var generatedRcBytes by mutableStateOf<ByteArray?>(null)
        private set
    var generatedJson by mutableStateOf<String?>(null)
        private set

    // Kotlin DSL generated from JSON (for display)
    var generatedKotlinDsl by mutableStateOf<String?>(null)

    // JSON UI document for direct rendering
    var jsonUiDocument by mutableStateOf<JsonUiDocument?>(null)

    // A2UI JSONL content for native rendering
    var a2uiJsonl by mutableStateOf<String?>(null)

    // CMW custom JSON payload for hardware-accelerated rendering
    var cmwJsonPayload by mutableStateOf<String?>(null)
        private set

    // Generation stats
    var tokenUsage by mutableStateOf<String?>(null)
    var generationSpeed by mutableStateOf<String?>(null)

    fun setGeneratedData(bytes: ByteArray, json: String, tokens: String? = null, speed: String? = null) {
        generatedRcBytes = bytes
        generatedJson = json
        tokenUsage = tokens
        generationSpeed = speed
    }

    fun setJsonDslData(json: String, kotlinDsl: String, tokens: String? = null, speed: String? = null) {
        generatedJson = json
        generatedKotlinDsl = kotlinDsl
        tokenUsage = tokens
        generationSpeed = speed
    }

    fun setA2uiData(jsonl: String, tokens: String? = null, speed: String? = null) {
        a2uiJsonl = jsonl
        generatedJson = jsonl
        tokenUsage = tokens
        generationSpeed = speed
    }

    fun setCmwData(json: String, tokens: String? = null, speed: String? = null) {
        cmwJsonPayload = json
        generatedJson = json
        tokenUsage = tokens
        generationSpeed = speed
    }

    fun clear() {
        generatedRcBytes = null
        generatedJson = null
        jsonUiDocument = null
        generatedKotlinDsl = null
        a2uiJsonl = null
        cmwJsonPayload = null
        tokenUsage = null
        generationSpeed = null
    }
}
