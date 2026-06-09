package com.example.cmw_simulator

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject

/**
 * Manages local AppFunctions that can be triggered from UI actions.
 * Action URI format: appfn:<function_name>?param1=value1&param2=value2
 *
 * Getter functions: read device state (battery, volume, flashlight) for LLM context.
 * Setter functions: execute actions (vibrate, flashlight, volume, settings).
 */
object AppFunctionManager {

    private var isFlashlightOn = false

    // ── Function Registry ──────────────────────────────────────────────────

    data class AppFunction(
        val name: String,
        val uri: String,
        val description: String,
        val parameters: String,
        val exampleUsage: String
    )

    /**
     * Returns all registered local AppFunctions available on this device.
     * Used by the LLM prompt builder to tell the model what tools it can bind to.
     */
    fun getAvailableFunctions(): List<AppFunction> = listOf(
        AppFunction(
            name = "Vibrate",
            uri = "appfn:vibrate?duration=100",
            description = "Triggers a haptic vibration on the device.",
            parameters = "duration (Long, optional): Duration in ms. Default 100.",
            exampleUsage = "\"clickableAction\": \"appfn:vibrate?duration=150\""
        ),
        AppFunction(
            name = "Toast",
            uri = "appfn:toast?message=<UTF8_TEXT>",
            description = "Displays a native Android Toast popup with a text message.",
            parameters = "message (String, required): The text to show.",
            exampleUsage = "\"clickableAction\": \"appfn:toast?message=Hello+World\""
        ),
        AppFunction(
            name = "Toggle Flashlight",
            uri = "appfn:toggle_flashlight",
            description = "Toggles the device flashlight ON/OFF.",
            parameters = "(none)",
            exampleUsage = "\"clickableAction\": \"appfn:toggle_flashlight\""
        ),
        AppFunction(
            name = "Adjust Volume",
            uri = "appfn:adjust_volume?direction=up",
            description = "Raises or lowers the system music stream volume by one step, showing the system volume UI.",
            parameters = "direction (String, required): \"up\" or \"down\".",
            exampleUsage = "\"clickableAction\": \"appfn:adjust_volume?direction=up\""
        ),
        AppFunction(
            name = "Open Settings",
            uri = "appfn:open_settings",
            description = "Opens the Android system Settings app.",
            parameters = "(none)",
            exampleUsage = "\"clickableAction\": \"appfn:open_settings\""
        ),
        AppFunction(
            name = "Show Fitness Stats",
            uri = "appfn:toast?message=Steps:8742+HR:72bpm+Cal:486",
            description = "Displays a toast with the user's current fitness snapshot (steps, heart rate, calories burned, active minutes, distance).",
            parameters = "(none) — reads local fitness data automatically.",
            exampleUsage = "\"clickableAction\": \"appfn:toast?message=Steps:8742+HR:72bpm+Cal:486\""
        )
    )

    // ──────────────────────────────────────────────────────────
    // 1. Getter Functions: 获取设备状态，组装为 JSON 供大模型参考
    // ──────────────────────────────────────────────────────────

    /**
     * Reads current device state and packages it as a JSONObject.
     * This is injected into the LLM prompt as <DeviceContext> so the model
     * can generate context-aware UI (e.g., low battery warnings, volume controls).
     */
    fun getDeviceContext(context: Context): JSONObject {
        val deviceContext = JSONObject()
        try {
            // Battery level
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            // Charging status
            val batteryStatus: Intent? = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val isCharging = chargePlug == BatteryManager.BATTERY_PLUGGED_AC ||
                    chargePlug == BatteryManager.BATTERY_PLUGGED_USB

            // System volume percentage
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volumePercent = ((currentVolume.toFloat() / maxVolume.toFloat()) * 100).toInt()

            deviceContext.put("battery_level", batteryLevel)
            deviceContext.put("is_charging", isCharging)
            deviceContext.put("system_volume_percent", volumePercent)
            deviceContext.put("flashlight_enabled", isFlashlightOn)
            deviceContext.put("api_level", Build.VERSION.SDK_INT)

            // Fitness metrics (simulated)
            val fitness = getFitnessMetrics()
            deviceContext.put("fitness_steps", fitness.getInt("steps"))
            deviceContext.put("fitness_heart_rate_bpm", fitness.getInt("heartRateBpm"))
            deviceContext.put("fitness_calories", fitness.getInt("caloriesBurned"))
            deviceContext.put("fitness_active_minutes", fitness.getInt("activeMinutes"))
            deviceContext.put("fitness_distance_km", fitness.getDouble("distanceKm"))

            // Countdown target — 60 days from now for marathon simulation
            val targetDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date(System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000))
            deviceContext.put("countdown_target_date", targetDate)
            deviceContext.put("current_date", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date()))

        } catch (e: Exception) {
            e.printStackTrace()
            deviceContext.put("error", "Failed to retrieve full context: ${e.message}")
        }
        return deviceContext
    }

    // ──────────────────────────────────────────────────────────
    // 2. Setter Functions: 响应并处理来自 Widget 的交互动作
    // ──────────────────────────────────────────────────────────

    /**
     * Parses and executes an appfn: URI triggered by a widget interaction.
     * @param context Android context for system service access.
     * @param actionUri The action URI (e.g., "appfn:vibrate?duration=150").
     * @param onStateChanged Optional callback invoked when state changes (e.g., flashlight toggle)
     *                       so the UI can refresh.
     */
    fun executeFunction(context: Context, actionUri: String, onStateChanged: (() -> Unit)? = null) {
        if (!actionUri.startsWith("appfn:")) return

        val command = actionUri.removePrefix("appfn:")
        val cleanCommand = command.substringBefore("?")
        val params = parseParams(command.substringAfter("?", ""))

        try {
            when (cleanCommand) {
                "vibrate" -> {
                    val duration = params["duration"]?.toLongOrNull() ?: 100L
                    triggerVibrate(context, duration)
                }
                "toast" -> {
                    val msg = params["message"] ?: "Hello from AppFunction!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                "toggle_flashlight" -> {
                    toggleFlashlight(context)
                    onStateChanged?.invoke()
                }
                "adjust_volume" -> {
                    val direction = params["direction"] ?: "up"
                    adjustSystemVolume(context, direction)
                    onStateChanged?.invoke()
                }
                "open_settings" -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                else -> {
                    Toast.makeText(context, "未实现的本地能力: $cleanCommand", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "执行本地能力失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Prompt Builder ──────────────────────────────────────────────────

    /**
     * Builds a text section for the LLM system prompt listing available AppFunctions.
     * This is injected dynamically so the model knows what local tools it can use.
     */
    fun buildPromptSection(): String {
        val sb = StringBuilder()
        sb.appendLine("# Dynamic Interactive Power: AppFunctions (CRITICAL)")
        sb.appendLine("Your generated widgets can trigger native system tools on the user's device. To bind a click action to a local tool, set the \"clickableAction\" property in any element's \"modifier\" to a valid appfn URI.")
        sb.appendLine()
        sb.appendLine("Available Local AppFunctions on this device:")
        getAvailableFunctions().forEach { fn ->
            sb.appendLine("- **${fn.name}**: `${fn.uri}`")
            sb.appendLine("  ${fn.description}")
            sb.appendLine("  Parameters: ${fn.parameters}")
            sb.appendLine("  Example: ${fn.exampleUsage}")
        }
        sb.appendLine()
        sb.appendLine("IMPORTANT: Whenever designing interactive elements (buttons, cards, list items), you SHOULD bind at least one of them to an AppFunction using \"clickableAction\". Make the UI feel alive and functional!")
        return sb.toString()
    }

    // ── Private Helpers ──────────────────────────────────────────────────

    /**
     * Returns simulated fitness metrics as a JSONObject.
     * In production this would query Health Connect or a wearable API.
     */
    fun getFitnessMetrics(): JSONObject {
        return JSONObject().apply {
            put("steps", 8742)
            put("heartRateBpm", 72)
            put("caloriesBurned", 486)
            put("activeMinutes", 45)
            put("distanceKm", 6.2)
            put("date", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date()))
        }
    }

    private fun parseParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isBlank()) return map
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                map[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
        }
        return map
    }

    private fun triggerVibrate(context: Context, duration: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun toggleFlashlight(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                isFlashlightOn = !isFlashlightOn
                cameraManager.setTorchMode(cameraId, isFlashlightOn)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "无法控制手电筒", Toast.LENGTH_SHORT).show()
        }
    }

    private fun adjustSystemVolume(context: Context, direction: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val flags = AudioManager.FLAG_SHOW_UI
        if (direction == "up") {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, flags)
        } else {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, flags)
        }
    }

    /**
     * Reset flashlight state (e.g., when the activity is destroyed).
     */
    fun resetFlashlightState() {
        isFlashlightOn = false
    }
}
