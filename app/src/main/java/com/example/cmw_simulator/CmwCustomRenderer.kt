package com.example.cmw_simulator

import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.RenderEffect
import android.graphics.Shader as AndroidShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader as ComposeShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.sin

// ── Host Component ───────────────────────────────────────────────────────────

/**
 * Entry-point composable for CMW custom JSON payloads.
 * Wraps the render tree in a continuous animation ticker that provides a
 * monotonic time value (in seconds) to all child renderers.
 */
@Composable
fun CMWWidgetHost(llmJsonPayload: JSONObject) {
    CMWAnimationTicker { currentTime ->
        Box(modifier = Modifier.fillMaxSize()) {
            RenderRCNode(node = llmJsonPayload, time = currentTime)
        }
    }
}

/**
 * Continuous animation ticker using [rememberInfiniteTransition].
 * Drives a float that counts up in seconds (effectively a monotonic clock).
 * This is recomposition-safe: only the animated value changes each frame.
 */
@Composable
fun CMWAnimationTicker(content: @Composable (time: Float) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "cmw_time")
    val timeSeconds by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10000f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(
                durationMillis = 10_000_000,
                easing = LinearEasing
            )
        ),
        label = "cmw_time_seconds"
    )
    content(timeSeconds)
}

// ── JSON Node Router ─────────────────────────────────────────────────────────

/**
 * Routes each JSON node by its `type` field to the appropriate Compose renderer.
 * Supports standard layout nodes (Text, Box, Column, Row, Spacer) and custom
 * CMW hardware-accelerated nodes (LiquidGlassBackground, GlassContainer, FloatingGlassOrb).
 */
@Composable
fun RenderRCNode(node: JSONObject, time: Float) {
    when (node.optString("type")) {
        // ── Standard nodes ──
        "Text" -> StandardTextNode(node)
        "Spacer" -> StandardSpacerNode(node)
        "Box" -> StandardBoxNode(node, time)
        "Column" -> StandardColumnNode(node, time)
        "Row" -> StandardRowNode(node, time)

        // ── CMW custom hardware-accelerated nodes ──
        "LiquidGlassBackground" -> GooeyBackgroundRenderer(node, time)
        "GlassContainer" -> {
            val blurRadius = node.optInt("blurRadius", 30)
            val cornerRadius = node.optInt("cornerRadius", 24)
            val children = node.optJSONArray("children")

            // Two-layer approach: blurred background + crisp content overlay
            Box(
                modifier = Modifier
                    .then(if (node.optBoolean("fillMaxWidth", false)) Modifier.fillMaxWidth() else Modifier)
                    .then(if (node.optBoolean("fillMaxSize", false)) Modifier.fillMaxSize() else Modifier)
            ) {
                // Layer 1: Blurred frosted glass background (no children)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(cornerRadius.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .blur(blurRadius.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(cornerRadius.dp))
                )

                // Layer 2: Crisp content on top (NOT blurred)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(cornerRadius.dp))
                        .padding(24.dp)
                ) {
                    if (children != null) {
                        for (i in 0 until children.length()) {
                            RenderRCNode(children.getJSONObject(i), time)
                        }
                    }
                }
            }
        }

        "FloatingGlassOrb" -> {
            val rawExpression = node.optString("floatExpression", "sin(time * 2.0) * 20")
            val speedMultiplier = Regex("[0-9.]+")
                .findAll(rawExpression)
                .firstOrNull()?.value?.toFloatOrNull() ?: 2.0f

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = sin(time * speedMultiplier) * 50f
                        scaleX = 1f + sin(time * speedMultiplier * 0.5f) * 0.05f
                        scaleY = scaleX
                    }
            ) {
                GlassOrbStub(node.optString("baseColor", "#EC4899"))
            }
        }

        // ── Fallback ──
        else -> {
            Text(
                text = "[Unknown: ${node.optString("type", "?")}]",
                color = Color.Red.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

// ── Gooey Background ─────────────────────────────────────────────────────────

/**
 * Reads an array of hex colours from the JSON `colors` field and renders
 * infinitely moving circle blobs blended together with Android's
 * [RenderEffect.createChainEffect] (ColorMatrix alpha-threshold + Gaussian blur)
 * to produce a liquid / gooey mesh on API 31+.
 *
 * On API < 31 the blobs are rendered with a simple Compose blur fallback.
 */
@Composable
fun GooeyBackgroundRenderer(node: JSONObject, time: Float) {
    val colorsArray = node.optJSONArray("colors")
    val colors = if (colorsArray != null && colorsArray.length() > 0) {
        (0 until colorsArray.length()).map { parseHexColor(colorsArray.getString(it)) }
    } else {
        listOf(Color(0xFF6366F1), Color(0xFF22D3EE), Color(0xFFD946EF))
    }

    // Build the gooey RenderEffect (API 31+)
    val gooeyEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = RenderEffect.createBlurEffect(50f, 50f, AndroidShader.TileMode.CLAMP)
            val matrix = AndroidColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 20f, -2550f
                )
            )
            val colorFilter = RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(matrix)
            )
            RenderEffect.createChainEffect(colorFilter, blur).asComposeRenderEffect()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                renderEffect = gooeyEffect
                alpha = 0.7f
            },
        contentAlignment = Alignment.Center
    ) {
        colors.forEachIndexed { index, color ->
            val phaseOffset = index * 2.094f // ~2π/3
            val offsetX = sin(time * 0.3f + phaseOffset) * 120f
            val offsetY = sin(time * 0.2f + phaseOffset + 1f) * 150f
            val sizeDp = when (index % 3) {
                0 -> 280
                1 -> 240
                else -> 200
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.dp.roundToPx(), offsetY.dp.roundToPx()) }
                    .size(sizeDp.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

// ── Glass Orb Stub ────────────────────────────────────────────────────────────

/**
 * Renders a 3D-looking glass sphere using [Canvas] with radial gradients
 * and inner shadows to simulate glass volume.
 */
@Composable
fun GlassOrbStub(colorHex: String) {
    val baseColor = parseHexColor(colorHex)

    Canvas(modifier = Modifier.size(100.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
        val radius = canvasWidth.coerceAtMost(canvasHeight) / 2f

        // 1. Darker outer ring (shadow depth)
        drawCircle(
            color = baseColor.copy(alpha = 0.4f),
            radius = radius,
            center = center
        )

        // 2. Base gradient sphere
        val baseBrush = object : ShaderBrush() {
            override fun createShader(size: Size): ComposeShader {
                return androidx.compose.ui.graphics.RadialGradientShader(
                    colors = listOf(
                        baseColor.copy(alpha = 0.9f),
                        baseColor.copy(alpha = 0.6f),
                        baseColor.copy(alpha = 0.3f)
                    ),
                    colorStops = listOf(0f, 0.6f, 1f),
                    center = center,
                    radius = radius,
                )
            }
        }
        drawCircle(
            brush = baseBrush,
            radius = radius * 0.95f,
            center = Offset(center.x, center.y + radius * 0.05f)
        )

        // 3. Inner highlight (upper-left shine)
        val highlightBrush = object : ShaderBrush() {
            override fun createShader(size: Size): ComposeShader {
                return androidx.compose.ui.graphics.RadialGradientShader(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f),
                        Color.White.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    colorStops = listOf(0f, 0.4f, 1f),
                    center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
                    radius = radius * 0.6f,
                )
            }
        }
        drawCircle(
            brush = highlightBrush,
            radius = radius * 0.85f,
            center = Offset(center.x - radius * 0.1f, center.y - radius * 0.1f)
        )

        // 4. Tiny specular highlight dot
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = radius * 0.12f,
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.3f)
        )
    }
}

// ── Standard Node Renderers ──────────────────────────────────────────────────

@Composable
private fun StandardTextNode(node: JSONObject) {
    val text = node.optString("text", "")
    val color = parseHexColor(node.optString("color", "#FFFFFF"))
    val fontSize = node.optInt("fontSize", 14)
    val fontWeightStr = node.optString("fontWeight", "Normal")

    Text(
        text = text,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = when (fontWeightStr) {
            "Bold" -> FontWeight.Bold
            "Medium" -> FontWeight.Medium
            "Light" -> FontWeight.Light
            else -> FontWeight.Normal
        }
    )
}

@Composable
private fun StandardSpacerNode(node: JSONObject) {
    val height = node.optInt("height", 8)
    Spacer(modifier = Modifier.height(height.dp))
}

@Composable
private fun StandardBoxNode(node: JSONObject, time: Float) {
    val children = node.optJSONArray("children")
    val mod = buildStandardModifier(node)

    Box(
        modifier = mod,
        contentAlignment = parseAlignment(node.optString("contentAlignment", "TopStart"))
    ) {
        if (children != null) {
            for (i in 0 until children.length()) {
                RenderRCNode(children.getJSONObject(i), time)
            }
        }
    }
}

@Composable
private fun StandardColumnNode(node: JSONObject, time: Float) {
    val children = node.optJSONArray("children")
    val mod = buildStandardModifier(node)
    val spacing = node.optInt("verticalArrangement", 0)

    Column(
        modifier = mod,
        verticalArrangement = if (spacing > 0) Arrangement.spacedBy(spacing.dp) else Arrangement.Top,
        horizontalAlignment = when (node.optString("horizontalAlignment", "Start")) {
            "CenterHorizontally" -> Alignment.CenterHorizontally
            "End" -> Alignment.End
            else -> Alignment.Start
        }
    ) {
        if (children != null) {
            for (i in 0 until children.length()) {
                RenderRCNode(children.getJSONObject(i), time)
            }
        }
    }
}

@Composable
private fun StandardRowNode(node: JSONObject, time: Float) {
    val children = node.optJSONArray("children")
    val mod = buildStandardModifier(node)
    val spacing = node.optInt("horizontalArrangement", 0)

    Row(
        modifier = mod,
        horizontalArrangement = if (spacing > 0) Arrangement.spacedBy(spacing.dp) else Arrangement.Start,
        verticalAlignment = when (node.optString("verticalAlignment", "CenterVertically")) {
            "Top" -> Alignment.Top
            "Bottom" -> Alignment.Bottom
            else -> Alignment.CenterVertically
        }
    ) {
        if (children != null) {
            for (i in 0 until children.length()) {
                RenderRCNode(children.getJSONObject(i), time)
            }
        }
    }
}

// ── Modifier Builder for Standard Nodes ──────────────────────────────────────

private fun buildStandardModifier(node: JSONObject): Modifier {
    var m: Modifier = Modifier
    val modObj = node.optJSONObject("modifier") ?: return m

    if (modObj.optBoolean("fillMaxSize", false)) m = m.fillMaxSize()
    if (modObj.optBoolean("fillMaxWidth", false)) m = m.fillMaxWidth()
    modObj.optInt("width", 0).takeIf { it > 0 }?.let { m = m.width(it.dp) }
    modObj.optInt("height", 0).takeIf { it > 0 }?.let { m = m.height(it.dp) }

    val bgStr = modObj.optString("background", "").takeIf { it.isNotBlank() }
    val cornerRadius = modObj.optInt("cornerRadius", 0).takeIf { it > 0 }
    if (bgStr != null) {
        val bgColor = parseHexColor(bgStr)
        val shape = cornerRadius?.let { RoundedCornerShape(it.dp) } ?: RoundedCornerShape(0.dp)
        m = m.background(bgColor, shape)
    }

    val padding = modObj.opt("padding")
    when (padding) {
        is Int -> m = m.padding(padding.dp)
        is JSONObject -> {
            val start = padding.optInt("start", 0)
            val top = padding.optInt("top", 0)
            val end = padding.optInt("end", 0)
            val bottom = padding.optInt("bottom", 0)
            if (start != 0 || top != 0 || end != 0 || bottom != 0) {
                m = m.padding(start.dp, top.dp, end.dp, bottom.dp)
            }
        }
    }

    return m
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun parseAlignment(name: String): Alignment = when (name) {
    "TopStart" -> Alignment.TopStart
    "TopCenter" -> Alignment.TopCenter
    "TopEnd" -> Alignment.TopEnd
    "CenterStart" -> Alignment.CenterStart
    "Center" -> Alignment.Center
    "CenterEnd" -> Alignment.CenterEnd
    "BottomStart" -> Alignment.BottomStart
    "BottomCenter" -> Alignment.BottomCenter
    "BottomEnd" -> Alignment.BottomEnd
    else -> Alignment.TopStart
}

private fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    return try {
        val argb = when (cleaned.length) {
            6 -> "FF$cleaned".toLong(16)
            8 -> cleaned.toLong(16)
            else -> return Color.White
        }
        Color(argb.toInt())
    } catch (_: NumberFormatException) {
        Color.White
    }
}
