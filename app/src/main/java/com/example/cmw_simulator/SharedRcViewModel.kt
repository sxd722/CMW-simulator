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

    // JSON UI document for direct rendering
    var jsonUiDocument by mutableStateOf<JsonUiDocument?>(null)

    fun setGeneratedData(bytes: ByteArray, json: String) {
        generatedRcBytes = bytes
        generatedJson = json
    }

    fun clear() {
        generatedRcBytes = null
        generatedJson = null
        jsonUiDocument = null
    }
}
