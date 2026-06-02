package com.example.cmw_simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import org.json.JSONArray
import org.json.JSONObject

// ── Data Model ──────────────────────────────────────────────────────────────

data class JsonUiDocument(
    val backgroundColor: Color?,
    val elements: List<JsonUiElement>
)

sealed class JsonUiElement {
    data class TextElement(
        val id: String,
        val text: String,
        val color: Color,
        val fontSize: Int
    ) : JsonUiElement()

    data class SpacerElement(
        val id: String,
        val height: Int
    ) : JsonUiElement()

    data class CardElement(
        val id: String,
        val paddingH: Int,
        val paddingV: Int,
        val color: Color,
        val cornerRadius: Int,
        val borderColor: Color?,
        val borderWidth: Int?,
        val actionName: String?,
        val children: List<JsonUiElement>
    ) : JsonUiElement()

    data class RowElement(
        val id: String,
        val children: List<JsonUiElement>
    ) : JsonUiElement()

    data class ButtonElement(
        val id: String,
        val text: String,
        val color: Color,
        val textColor: Color,
        val fontSize: Int,
        val cornerRadius: Int,
        val actionName: String?,
        val borderColor: Color?,
        val borderWidth: Int?
    ) : JsonUiElement()

    data class DividerElement(
        val id: String,
        val color: Color,
        val height: Int
    ) : JsonUiElement()

    data class ColumnElement(
        val id: String,
        val modifier: ModifierData?,
        val verticalArrangement: Int?,
        val horizontalAlignment: String?,
        val children: List<JsonUiElement>
    ) : JsonUiElement()

    data class BoxElement(
        val id: String,
        val modifier: ModifierData?,
        val contentAlignment: String?,
        val children: List<JsonUiElement>
    ) : JsonUiElement()
}

data class ModifierData(
    val padding: Any? = null, // Int or JSONObject
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val fillMaxSize: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val background: Color? = null,
    val cornerRadius: Int? = null,
    val clickableAction: String? = null
)

// ── Parser ──────────────────────────────────────────────────────────────────

fun parseJsonUiDocument(json: String): JsonUiDocument {
    val root = JSONObject(json)
    val bgColor = root.optString("backgroundColor")?.takeIf { it.isNotBlank() }?.toColor()
    val elements = root.optJSONArray("elements")?.parseElements() ?: emptyList()
    return JsonUiDocument(backgroundColor = bgColor, elements = elements)
}

private fun JSONArray.parseElements(): List<JsonUiElement> {
    return (0 until length()).mapNotNull { i ->
        val obj = getJSONObject(i)
        parseElement(obj)
    }
}

private fun parseElement(obj: JSONObject): JsonUiElement? {
    val type = obj.optString("type") ?: return null
    val id = obj.optString("id", "")
    return when (type) {
        "text" -> JsonUiElement.TextElement(
            id = id,
            text = obj.optString("text", ""),
            color = obj.optString("color", "#000000").toColor(),
            fontSize = obj.optInt("fontSize", 14)
        )
        "spacer" -> JsonUiElement.SpacerElement(
            id = id,
            height = obj.optInt("height", 8)
        )
        "card" -> JsonUiElement.CardElement(
            id = id,
            paddingH = obj.optInt("paddingH", 16),
            paddingV = obj.optInt("paddingV", 12),
            color = obj.optString("color", "#FFFFFF").toColor(),
            cornerRadius = obj.optInt("cornerRadius", 12),
            borderColor = obj.optString("borderColor").takeIf { it.isNotBlank() }?.toColor(),
            borderWidth = obj.optInt("borderWidth", 0).takeIf { it > 0 },
            actionName = obj.optString("actionName").takeIf { it.isNotBlank() },
            children = obj.optJSONArray("children")?.parseElements() ?: emptyList()
        )
        "row" -> JsonUiElement.RowElement(
            id = id,
            children = obj.optJSONArray("children")?.parseElements() ?: emptyList()
        )
        "button" -> JsonUiElement.ButtonElement(
            id = id,
            text = obj.optString("text", ""),
            color = obj.optString("color", "#6200EE").toColor(),
            textColor = obj.optString("textColor", "#FFFFFF").toColor(),
            fontSize = obj.optInt("fontSize", 14),
            cornerRadius = obj.optInt("cornerRadius", 20),
            actionName = obj.optString("actionName").takeIf { it.isNotBlank() },
            borderColor = obj.optString("borderColor").takeIf { it.isNotBlank() }?.toColor(),
            borderWidth = obj.optInt("borderWidth", 0).takeIf { it > 0 }
        )
        "divider" -> JsonUiElement.DividerElement(
            id = id,
            color = obj.optString("color", "#CCCCCC").toColor(),
            height = obj.optInt("height", 1)
        )

        // ── Remote Compose types ──
        "RemoteText" -> JsonUiElement.TextElement(
            id = id,
            text = obj.optString("text", ""),
            color = obj.optString("color", "#000000").toColor(),
            fontSize = obj.optInt("fontSize", 14)
        )
        "RemoteSpacer" -> {
            val mod = obj.optModifier()
            JsonUiElement.SpacerElement(
                id = id,
                height = mod?.height ?: obj.optInt("height", 8)
            )
        }
        "RemoteColumn" -> JsonUiElement.ColumnElement(
            id = id,
            modifier = obj.optModifier(),
            verticalArrangement = obj.optInt("verticalArrangement").takeIf { it != 0 },
            horizontalAlignment = obj.optString("horizontalAlignment").takeIf { it.isNotBlank() },
            children = obj.optJSONArray("children")?.parseElements() ?: emptyList()
        )
        "RemoteRow" -> JsonUiElement.RowElement(
            id = id,
            children = obj.optJSONArray("children")?.parseElements() ?: emptyList()
        )
        "RemoteBox" -> JsonUiElement.BoxElement(
            id = id,
            modifier = obj.optModifier(),
            contentAlignment = obj.optString("contentAlignment").takeIf { it.isNotBlank() },
            children = obj.optJSONArray("children")?.parseElements() ?: emptyList()
        )
        "RemoteImage" -> {
            // Render image URL as placeholder text
            val url = obj.optString("url", "")
            JsonUiElement.TextElement(
                id = id,
                text = "[Image: ${url.take(50)}]",
                color = Color.Gray,
                fontSize = 12
            )
        }
        else -> null
    }
}

private fun String.toColor(): Color {
    val hex = removePrefix("#")
    return try {
        val argb = when (hex.length) {
            6 -> "FF$hex".toLong(16)
            8 -> hex.toLong(16)
            else -> return Color.Black
        }
        Color(argb.toInt())
    } catch (_: NumberFormatException) {
        Color.Black
    }
}

private fun JSONObject.optModifier(): ModifierData? {
    val modObj = optJSONObject("modifier") ?: return null
    return ModifierData(
        padding = modObj.opt("padding"),
        fillMaxWidth = modObj.optBoolean("fillMaxWidth", false),
        fillMaxHeight = modObj.optBoolean("fillMaxHeight", false),
        fillMaxSize = modObj.optBoolean("fillMaxSize", false),
        width = modObj.optInt("width", 0).takeIf { it > 0 },
        height = modObj.optInt("height", 0).takeIf { it > 0 },
        background = modObj.optString("background").takeIf { it.isNotBlank() }?.toColor(),
        cornerRadius = modObj.optInt("cornerRadius", 0).takeIf { it > 0 },
        clickableAction = modObj.optString("clickableAction").takeIf { it.isNotBlank() }
    )
}

// ── JSON → Kotlin DSL Converter ────────────────────────────────────────────

fun convertJsonToKotlinDsl(json: String): String {
    val root = JSONObject(json)
    val sb = StringBuilder()
    val bgColor = root.optString("backgroundColor")?.takeIf { it.isNotBlank() }
    if (bgColor != null) {
        sb.appendLine("// Background: $bgColor")
    }
    val elements = root.optJSONArray("elements")
    if (elements != null && elements.length() > 0) {
        // Wrap top-level elements in a RemoteColumn if there are multiple
        if (elements.length() == 1) {
            sb.append(convertElementToDsl(elements.getJSONObject(0), 0))
        } else {
            sb.appendLine("RemoteColumn(")
            sb.appendLine("    modifier = RemoteModifier.fillMaxWidth()")
            sb.appendLine(") {")
            for (i in 0 until elements.length()) {
                sb.append(convertElementToDsl(elements.getJSONObject(i), 1))
            }
            sb.appendLine("}")
        }
    }
    return sb.toString().trim()
}

private fun convertElementToDsl(obj: JSONObject, indent: Int): String {
    val type = obj.optString("type", "")
    val pad = "    ".repeat(indent)
    val sb = StringBuilder()

    when (type) {
        "text", "RemoteText" -> {
            val text = obj.optString("text", "")
            val color = obj.optString("color", "#000000")
            val fontSize = obj.optInt("fontSize", 14)
            val fontWeight = obj.optString("fontWeight", "")
            val mod = obj.optModifier()
            sb.append(pad).append("RemoteText(")
            if (mod != null) {
                sb.appendLine()
                sb.append(pad).append("    modifier = ").append(modifierToDsl(mod, indent + 1)).appendLine(",")
                sb.append(pad).append("    text = \"$text\",")
                sb.appendLine()
                sb.append(pad).append("    color = ${colorToDsl(color)}.rc,")
                sb.appendLine()
                sb.append(pad).append("    fontSize = ${fontSize}.rsp")
                if (fontWeight.isNotBlank()) {
                    sb.appendLine(",")
                    sb.append(pad).append("    fontWeight = FontWeight.$fontWeight")
                }
                sb.appendLine()
                sb.append(pad).append(")")
            } else {
                sb.append("text = \"$text\", ")
                sb.append("color = ${colorToDsl(color)}.rc, ")
                sb.append("fontSize = ${fontSize}.rsp")
                if (fontWeight.isNotBlank()) {
                    sb.append(", fontWeight = FontWeight.$fontWeight")
                }
                sb.append(")")
            }
            sb.appendLine()
        }

        "spacer", "RemoteSpacer" -> {
            val mod = obj.optModifier()
            val height = obj.optInt("height", 0)
            if (mod != null) {
                val modDsl = modifierToDsl(mod, indent)
                sb.append(pad).append("RemoteSpacer(modifier = ").append(modDsl).appendLine(")")
            } else if (height > 0) {
                sb.append(pad).appendLine("RemoteSpacer(modifier = RemoteModifier.height(${height}.rdp))")
            } else {
                sb.append(pad).appendLine("RemoteSpacer(modifier = RemoteModifier.height(8.rdp))")
            }
        }

        "card" -> {
            val paddingH = obj.optInt("paddingH", 16)
            val paddingV = obj.optInt("paddingV", 12)
            val color = obj.optString("color", "#FFFFFF")
            val cornerRadius = obj.optInt("cornerRadius", 12)
            val borderColor = obj.optString("borderColor").takeIf { it.isNotBlank() }
            val borderWidth = obj.optInt("borderWidth", 0).takeIf { it > 0 }
            val actionName = obj.optString("actionName").takeIf { it.isNotBlank() }
            val children = obj.optJSONArray("children")
            sb.append(pad).appendLine("RemoteBox(")
            sb.append(pad).append("    modifier = RemoteModifier")
            sb.append(".fillMaxWidth()")
            sb.append(".background(${colorToDsl(color)}.rc)")
            sb.append(".padding(horizontal = ${paddingH}.rdp, vertical = ${paddingV}.rdp)")
            if (borderColor != null && borderWidth != null) {
                sb.append(".border(${borderWidth}.rdp, ${colorToDsl(borderColor)}.rc)")
            }
            sb.appendLine(",")
            if (actionName != null) {
                sb.append(pad).appendLine("    clickableAction = \"$actionName\",")
            }
            sb.append(pad).appendLine("    cornerRadius = ${cornerRadius}.rdp")
            sb.append(pad).append(") {")
            if (children != null) {
                sb.appendLine()
                for (i in 0 until children.length()) {
                    sb.append(convertElementToDsl(children.getJSONObject(i), indent + 1))
                }
                sb.append(pad).appendLine("}")
            } else {
                sb.appendLine(" }")
            }
        }

        "row", "RemoteRow" -> {
            val mod = obj.optModifier()
            val children = obj.optJSONArray("children")
            sb.append(pad).append("RemoteRow(")
            if (mod != null) {
                sb.appendLine()
                sb.append(pad).append("    modifier = ").append(modifierToDsl(mod, indent + 1))
                sb.appendLine()
                sb.append(pad).appendLine(") {")
            } else {
                sb.appendLine("modifier = RemoteModifier.fillMaxWidth()) {")
            }
            if (children != null) {
                for (i in 0 until children.length()) {
                    sb.append(convertElementToDsl(children.getJSONObject(i), indent + 1))
                }
            }
            sb.append(pad).appendLine("}")
        }

        "button" -> {
            val text = obj.optString("text", "")
            val color = obj.optString("color", "#6200EE")
            val textColor = obj.optString("textColor", "#FFFFFF")
            val fontSize = obj.optInt("fontSize", 14)
            val cornerRadius = obj.optInt("cornerRadius", 20)
            val actionName = obj.optString("actionName").takeIf { it.isNotBlank() }
            sb.append(pad).appendLine("RemoteBox(")
            sb.append(pad).append("    modifier = RemoteModifier")
            sb.append(".background(${colorToDsl(color)}.rc)")
            sb.append(".padding(12.rdp)")
            if (actionName != null) {
                sb.append(".clickable(\"$actionName\")")
            }
            sb.appendLine(",")
            sb.append(pad).appendLine("    cornerRadius = ${cornerRadius}.rdp")
            sb.append(pad).appendLine(") {")
            val btnPad = "    ".repeat(indent + 1)
            sb.append(btnPad).append("RemoteText(text = \"$text\", color = ${colorToDsl(textColor)}.rc, fontSize = ${fontSize}.rsp)")
            sb.appendLine()
            sb.append(pad).appendLine("}")
        }

        "divider" -> {
            val color = obj.optString("color", "#CCCCCC")
            sb.append(pad).appendLine("// Divider: ${colorToDsl(color)}")
        }

        "RemoteColumn" -> {
            val mod = obj.optModifier()
            val vArr = obj.optInt("verticalArrangement").takeIf { it != 0 }
            val hAlign = obj.optString("horizontalAlignment").takeIf { it.isNotBlank() }
            val children = obj.optJSONArray("children")
            sb.append(pad).append("RemoteColumn(")
            if (mod != null || vArr != null || hAlign != null) {
                sb.appendLine()
                if (mod != null) {
                    sb.append(pad).append("    modifier = ").append(modifierToDsl(mod, indent + 1)).appendLine(",")
                }
                if (vArr != null) {
                    sb.append(pad).appendLine("    verticalArrangement = ${vArr}.rdp,")
                }
                if (hAlign != null) {
                    sb.append(pad).appendLine("    horizontalAlignment = \"$hAlign\",")
                }
            }
            sb.append(pad).appendLine(") {")
            if (children != null) {
                for (i in 0 until children.length()) {
                    sb.append(convertElementToDsl(children.getJSONObject(i), indent + 1))
                }
            }
            sb.append(pad).appendLine("}")
        }

        "RemoteBox" -> {
            val mod = obj.optModifier()
            val contentAlign = obj.optString("contentAlignment").takeIf { it.isNotBlank() }
            val children = obj.optJSONArray("children")
            sb.append(pad).append("RemoteBox(")
            if (mod != null || contentAlign != null) {
                sb.appendLine()
                if (mod != null) {
                    sb.append(pad).append("    modifier = ").append(modifierToDsl(mod, indent + 1)).appendLine(",")
                }
                if (contentAlign != null) {
                    sb.append(pad).appendLine("    contentAlignment = \"$contentAlign\",")
                }
            }
            sb.append(pad).appendLine(") {")
            if (children != null) {
                for (i in 0 until children.length()) {
                    sb.append(convertElementToDsl(children.getJSONObject(i), indent + 1))
                }
            }
            sb.append(pad).appendLine("}")
        }

        "RemoteImage" -> {
            val url = obj.optString("url", "")
            sb.append(pad).appendLine("RemoteImage(url = \"$url\")")
        }

        else -> {
            sb.append(pad).appendLine("// Unknown element type: $type")
        }
    }
    return sb.toString()
}

private fun colorToDsl(hex: String): String {
    return if (hex.equals("#000000", ignoreCase = true) || hex.equals("black", ignoreCase = true)) {
        "Color.Black"
    } else if (hex.equals("#FFFFFF", ignoreCase = true) || hex.equals("white", ignoreCase = true)) {
        "Color.White"
    } else if (hex.equals("#888888", ignoreCase = true) || hex.equals("gray", ignoreCase = true)) {
        "Color.Gray"
    } else {
        val cleaned = hex.removePrefix("#")
        if (cleaned.length == 6) "Color(0xFF${cleaned.uppercase()})" else "Color(0x${cleaned.uppercase()})"
    }
}

private fun modifierToDsl(mod: ModifierData, indent: Int): String {
    val parts = mutableListOf<String>()
    if (mod.fillMaxSize) parts.add("fillMaxSize()")
    if (mod.fillMaxWidth) parts.add("fillMaxWidth()")
    if (mod.fillMaxHeight) parts.add("fillMaxHeight()")
    mod.width?.let { parts.add("width(${it}.rdp)") }
    mod.height?.let { parts.add("height(${it}.rdp)") }
    mod.background?.let {
        val hex = colorToArgbHex(it)
        if (mod.cornerRadius != null) {
            parts.add("background(${colorToDsl("#$hex")}.rc, cornerRadius = ${mod.cornerRadius}.rdp)")
        } else {
            parts.add("background(${colorToDsl("#$hex")}.rc)")
        }
    }
    when (val p = mod.padding) {
        is Int -> parts.add("padding(${p}.rdp)")
        is JSONObject -> {
            val start = p.optInt("start", 0)
            val top = p.optInt("top", 0)
            val end = p.optInt("end", 0)
            val bottom = p.optInt("bottom", 0)
            if (start != 0 || top != 0 || end != 0 || bottom != 0) {
                parts.add("padding(start = ${start}.rdp, top = ${top}.rdp, end = ${end}.rdp, bottom = ${bottom}.rdp)")
            }
        }
    }
    if (mod.background == null && mod.cornerRadius != null) {
        parts.add("cornerRadius(${mod.cornerRadius}.rdp)")
    }
    if (mod.clickableAction != null) {
        parts.add("clickable(\"${mod.clickableAction}\")")
    }
    return if (parts.isEmpty()) {
        "RemoteModifier"
    } else {
        "RemoteModifier." + parts.joinToString(".")
    }
}

private fun colorToArgbHex(color: Color): String {
    val argb = (color.value.toULong() and 0xFFFFFFFFu).toLong()
    return String.format("%08X", argb)
}

// ── Compose Renderer ──

@Composable
fun JsonUiRenderer(
    document: JsonUiDocument,
    onAction: (actionName: String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                document.backgroundColor?.let { Modifier.background(it) } ?: Modifier
            )
            .padding(16.dp)
    ) {
        document.elements.forEach { element ->
            RenderElement(element, onAction)
        }
    }
}

private fun buildModifier(mod: ModifierData?): Modifier {
    if (mod == null) return Modifier
    var m: Modifier = Modifier
    if (mod.fillMaxSize) m = m.fillMaxSize()
    if (mod.fillMaxWidth) m = m.fillMaxWidth()
    if (mod.fillMaxHeight) m = m.fillMaxHeight()
    mod.width?.let { m = m.width(it.dp) }
    mod.height?.let { m = m.height(it.dp) }
    mod.background?.let { bg ->
        val shape = mod.cornerRadius?.let { RoundedCornerShape(it.dp) } ?: RoundedCornerShape(0.dp)
        m = m.background(bg, shape)
    }
    when (val p = mod.padding) {
        is Int -> m = m.padding(p.dp)
        is JSONObject -> {
            val start = p.optInt("start", 0)
            val top = p.optInt("top", 0)
            val end = p.optInt("end", 0)
            val bottom = p.optInt("bottom", 0)
            if (start != 0 || top != 0 || end != 0 || bottom != 0) {
                m = m.padding(start.dp, top.dp, end.dp, bottom.dp)
            }
        }
    }
    if (mod.cornerRadius != null && mod.background == null) {
        // cornerRadius only meaningful with a background
    }
    return m
}

@Composable
private fun RenderElement(element: JsonUiElement, onAction: (String) -> Unit) {
    when (element) {
        is JsonUiElement.TextElement -> {
            Text(
                text = element.text,
                color = element.color,
                fontSize = element.fontSize.sp
            )
        }

        is JsonUiElement.SpacerElement -> {
            Spacer(modifier = Modifier.height(element.height.dp))
        }

        is JsonUiElement.CardElement -> {
            val shape = RoundedCornerShape(element.cornerRadius.dp)
            val modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (element.borderColor != null && element.borderWidth != null) {
                        Modifier.border(element.borderWidth.dp, element.borderColor, shape)
                    } else Modifier
                )

            Surface(
                modifier = modifier,
                shape = shape,
                color = element.color,
                onClick = {
                    element.actionName?.let { onAction(it) }
                }
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            horizontal = element.paddingH.dp,
                            vertical = element.paddingV.dp
                        )
                ) {
                    element.children.forEach { child ->
                        RenderElement(child, onAction)
                    }
                }
            }
        }

        is JsonUiElement.RowElement -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                element.children.forEach { child ->
                    RenderElement(child, onAction)
                }
            }
        }

        is JsonUiElement.ButtonElement -> {
            val shape = RoundedCornerShape(element.cornerRadius.dp)
            val colors = ButtonDefaults.buttonColors(
                containerColor = element.color,
                contentColor = element.textColor
            )
            val modifier = Modifier.then(
                if (element.borderColor != null && element.borderWidth != null) {
                    Modifier.border(element.borderWidth.dp, element.borderColor, shape)
                } else Modifier
            )

            Button(
                onClick = { element.actionName?.let { onAction(it) } },
                shape = shape,
                colors = colors,
                modifier = modifier
            ) {
                Text(
                    text = element.text,
                    fontSize = element.fontSize.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        is JsonUiElement.DividerElement -> {
            HorizontalDivider(
                color = element.color,
                thickness = element.height.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is JsonUiElement.ColumnElement -> {
            val mod = buildModifier(element.modifier)
            val clickAction = element.modifier?.clickableAction
            val arrangement = element.verticalArrangement?.dp
            val content = @Composable {
                Column(
                    modifier = mod,
                    verticalArrangement = if (arrangement != null) Arrangement.spacedBy(arrangement) else Arrangement.Top,
                    horizontalAlignment = when (element.horizontalAlignment) {
                        "CenterHorizontally" -> Alignment.CenterHorizontally
                        "End" -> Alignment.End
                        else -> Alignment.Start
                    }
                ) {
                    element.children.forEach { RenderElement(it, onAction) }
                }
            }
            if (clickAction != null) {
                Surface(
                    onClick = { onAction(clickAction) },
                    modifier = mod,
                    color = Color.Transparent
                ) {
                    Column(
                        verticalArrangement = if (arrangement != null) Arrangement.spacedBy(arrangement) else Arrangement.Top,
                        horizontalAlignment = when (element.horizontalAlignment) {
                            "CenterHorizontally" -> Alignment.CenterHorizontally
                            "End" -> Alignment.End
                            else -> Alignment.Start
                        }
                    ) {
                        element.children.forEach { RenderElement(it, onAction) }
                    }
                }
            } else {
                content()
            }
        }

        is JsonUiElement.BoxElement -> {
            val mod = buildModifier(element.modifier)
            val clickAction = element.modifier?.clickableAction
            val alignment = when (element.contentAlignment) {
                "TopStart" -> Alignment.TopStart
                "Center" -> Alignment.Center
                "BottomEnd" -> Alignment.BottomEnd
                "BottomStart" -> Alignment.BottomStart
                "TopEnd" -> Alignment.TopEnd
                else -> Alignment.TopStart
            }
            if (clickAction != null) {
                Surface(
                    onClick = { onAction(clickAction) },
                    modifier = mod,
                    color = Color.Transparent
                ) {
                    Box(
                        contentAlignment = alignment
                    ) {
                        element.children.forEach { RenderElement(it, onAction) }
                    }
                }
            } else {
                Box(
                    modifier = mod,
                    contentAlignment = alignment
                ) {
                    element.children.forEach { RenderElement(it, onAction) }
                }
            }
        }
    }
}
