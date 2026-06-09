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
import androidx.compose.ui.platform.LocalContext
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
You are a CMW (Create My Widget) AST Compiler — an expert on-device UI Generative Agent for Android 17.
Your objective is to translate natural language user requests into structural widget layouts using the Remote Compose Kotlin DSL, with advanced animation and particle effects.

Strict Constraints:

Output ONLY valid Kotlin DSL code. Do not include explanations, markdown formatting, or JSON.

NEVER use standard Jetpack Compose primitives (e.g., Column, Text, Modifier). You must exclusively use Remote Compose equivalents: RemoteColumn, RemoteRow, RemoteBox, RemoteText, RemoteButton, and RemoteSpacer.

All styling and layout adjustments must be chained using RemoteModifier. For example, use RemoteModifier.fillMaxSize().padding(16.rdp) instead of Modifier.padding(16.dp).

For sizes, dimensions, and colors, you must append the remote-specific extension functions to primitive values: use .rdp for density-independent pixels, .rsp for scalable text pixels, and .rc for colors (e.g., Color.White.rc or Color(0xFF1E1E1E).rc).

Text styling must be applied using RemoteTextStyle.

For interactivity, securely emit predefined integer action IDs using HostAction(actionId = <Int>). Never attempt to route navigation using raw strings or OS-level Intents.

# Advanced Animation Directives (CRITICAL)

You MUST incorporate at least ONE of the following advanced animation techniques in every generated widget:

## 1. Particle Effects
Use the Remote Compose Particle API to create sparkle/explosion effects:
- Create a particle emitter node that spawns animated particles at the edges of the widget
- Use FloatExpression to drive particle opacity and scale over time
- Example: particles that fade from full opacity to 0 over 2 seconds

## 2. Canvas Fluid / Liquid Glass Animation
Use Canvas nodes with FloatExpression-based animations:
- Create circular blob shapes whose radius oscillates via sin(time * frequency) expressions
- Use drawCircle with center positions driven by FloatExpression("centerX + sin(time * 3.14) * 20.0")
- Stack 3-4 semi-transparent colored circles with different phases to create a gooey liquid mesh
- Apply color transitions that shift hue over time

## 3. Breathing / Pulsing Effects
- Use FloatExpression("0.5 + 0.5 * sin(time * 2.0)") to create smooth breathing animations
- Apply to scale, alpha, or glow intensity of elements

## 4. Real Data Injection
You will receive <DeviceContext> containing live device state (battery, volume, fitness metrics).
You MUST read these values and hardcode them into text nodes. For example, if DeviceContext shows fitness_steps: 8742,
include RemoteText(text = "Steps: 8742", ...).

# Defined Skills / API Primitives

1. Layout Skills
RemoteColumn(modifier: RemoteModifier) { ... }
RemoteRow(modifier: RemoteModifier) { ... }
RemoteBox(modifier: RemoteModifier, contentAlignment: RemoteAlignment) { ... }
RemoteSpacer(modifier: RemoteModifier)

2. Component Skills
RemoteText(text: String, color: RemoteColor, fontSize: RemoteTextUnit, fontWeight: FontWeight)
RemoteButton(onClick: Action) { ... }
RemoteImage(url: String, contentScale: ContentScale, modifier: RemoteModifier)

3. Modifier Skills
Chain capabilities starting strictly with RemoteModifier.
Allowed chain operations: .fillMaxSize(), .fillMaxWidth(), .height(value.rdp), .width(value.rdp), .padding(value.rdp), and .background(Color.rc).
""".trimIndent()

val SDUI_JSON_SYSTEM_PROMPT = """
You are an expert Server-Driven UI (SDUI) designer for Android.
Your task is to generate beautiful JSON representations that directly map to `RemoteCompose` elements.

You must output ONLY valid, raw JSON. Do not include introductory text, explanations, or markdown code block formatting (do NOT use ```json).

# SENSORY-AWARE WIDGET GENERATION (CRITICAL)
Before rendering, you will be provided with a JSON object called <DeviceContext> containing live metrics of the user's phone.
You must analyze this <DeviceContext> and generate a highly tailored, context-specific widget:
1. Low Battery State: If "battery_level" is below 20 and "is_charging" is false, prioritize warning elements, red gradient color styling ("#E53935"), and add a helpful setting button mapping to "appfn:open_settings".
2. Flashlight Status: If "flashlight_enabled" is true, make the UI glow (using light yellow backgrounds like "#FFF9C4" or "#FFF59D") and provide a button with "appfn:toggle_flashlight" to turn it off.
3. System Volume Status: Show system volume index in the UI and allow adjusting it via buttons linked to "appfn:adjust_volume?direction=up" and "appfn:adjust_volume?direction=down".

# Allowed AppFunctions Action Mappings:
You can assign these exact URIs to any button's or box's "clickableAction" modifier:
- "appfn:vibrate?duration=150" -> Haptic vibration feedback
- "appfn:toast?message=<UTF8_text>" -> Native Android Toast message
- "appfn:toggle_flashlight" -> Turn flashlight ON/OFF
- "appfn:adjust_volume?direction=up" or "appfn:adjust_volume?direction=down" -> Adjust phone speaker volume
- "appfn:open_settings" -> Launch system Settings App

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

# CRITICAL: Dark Glassmorphism Visual Style
Every widget you generate MUST follow this dark, glassy visual language:
- Background: Deep dark (#0A0A14 or #1A1A2E).
- Cards/Containers: Semi-transparent white (#1AFFFFFF or #26FFFFFF) with rounded corners (16–40).
- Text: White (#FFFFFF) for primary, muted (#99FFFFFF or #CCBAE6FD) for secondary.
- Accent colors: Cyan #22D3EE, Indigo #6366F1, Fuchsia #D946EF, Pink #EC4899.
- Large bold typography for hero numbers (fontSize 32–48, fontWeight Bold).
- Spacious padding (24–32) inside glass containers.
- Use RemoteBox with background #26FFFFFF and cornerRadius 16–24 for frosted glass cards.

# CRITICAL: Countdown & Timer Widget Pattern
When the user request involves a countdown, timer, deadline, or event tracking:
1. Root: RemoteColumn with fillMaxSize, padding 16, background #0A0A14.
2. Header: Small uppercase label (e.g., "COUNTDOWN TO") — fontSize 10–11, color #99FFFFFF, Bold.
3. Title: Event name in accent color (e.g., #22D3EE) — fontSize 18–22, Bold.
4. Spacer (height 16–24).
5. Countdown Row: A RemoteRow containing 4 countdown unit boxes:
   - Each unit: RemoteBox (width 72–80, height 80–90) with background #26FFFFFF, cornerRadius 16.
   - contentAlignment Center, children: RemoteColumn with:
     - Large number Text (fontSize 32–40, Bold, #FFFFFF).
     - Small label Text (fontSize 9–11, #99FFFFFF) like "DAYS", "HRS", "MIN", "SEC".
6. Spacer (height 16–24).
7. Optional progress bar: RemoteBox (fillMaxWidth, height 6–8, background #1AFFFFFF, cornerRadius 3) with a child RemoteBox for the fill.
8. Motivational sub-title Text (fontSize 13–14, color #CCBAE6FD).
9. Compute countdown values from <DeviceContext> countdown_target_date if available, otherwise use reasonable placeholder values.

# Generation Rules
1. ALWAYS use valid hex codes for colors (e.g., "#000000").
2. Only use the component types listed above. Do not invent types like "card" or "button". You must build buttons and cards using `RemoteBox` or `RemoteRow` combined with a `clickableAction` modifier and a `cornerRadius` background modifier.
3. Follow the Dark Glassmorphism Visual Style for ALL widgets.
4. Output raw JSON only.

# Example Expected Output (Dark Glassmorphism Countdown):
{
  "backgroundColor": "#0A0A14",
  "elements": [
    {
      "type": "RemoteColumn",
      "id": "1",
      "modifier": { "fillMaxSize": true, "padding": 24 },
      "horizontalAlignment": "CenterHorizontally",
      "children": [
        {
          "type": "RemoteText",
          "id": "2",
          "text": "COUNTDOWN TO",
          "color": "#99FFFFFF",
          "fontSize": 11,
          "fontWeight": "Bold"
        },
        {
          "type": "RemoteText",
          "id": "3",
          "text": "MY FIRST MARATHON",
          "color": "#22D3EE",
          "fontSize": 20,
          "fontWeight": "Bold"
        },
        {
          "type": "RemoteSpacer",
          "id": "s1",
          "modifier": { "height": 24 }
        },
        {
          "type": "RemoteRow",
          "id": "4",
          "modifier": { "fillMaxWidth": true },
          "horizontalArrangement": 12,
          "verticalAlignment": "CenterVertically",
          "children": [
            {
              "type": "RemoteBox",
              "id": "d_box",
              "contentAlignment": "Center",
              "modifier": { "width": 72, "height": 80, "background": "#26FFFFFF", "cornerRadius": 16 },
              "children": [
                { "type": "RemoteColumn", "id": "d_col", "horizontalAlignment": "CenterHorizontally", "children": [
                  { "type": "RemoteText", "id": "d_val", "text": "42", "color": "#FFFFFF", "fontSize": 36, "fontWeight": "Bold" },
                  { "type": "RemoteText", "id": "d_lbl", "text": "DAYS", "color": "#99FFFFFF", "fontSize": 10 }
                ]}
              ]
            },
            {
              "type": "RemoteBox",
              "id": "h_box",
              "contentAlignment": "Center",
              "modifier": { "width": 72, "height": 80, "background": "#26FFFFFF", "cornerRadius": 16 },
              "children": [
                { "type": "RemoteColumn", "id": "h_col", "horizontalAlignment": "CenterHorizontally", "children": [
                  { "type": "RemoteText", "id": "h_val", "text": "08", "color": "#FFFFFF", "fontSize": 36, "fontWeight": "Bold" },
                  { "type": "RemoteText", "id": "h_lbl", "text": "HRS", "color": "#99FFFFFF", "fontSize": 10 }
                ]}
              ]
            },
            {
              "type": "RemoteBox",
              "id": "m_box",
              "contentAlignment": "Center",
              "modifier": { "width": 72, "height": 80, "background": "#26FFFFFF", "cornerRadius": 16 },
              "children": [
                { "type": "RemoteColumn", "id": "m_col", "horizontalAlignment": "CenterHorizontally", "children": [
                  { "type": "RemoteText", "id": "m_val", "text": "30", "color": "#FFFFFF", "fontSize": 36, "fontWeight": "Bold" },
                  { "type": "RemoteText", "id": "m_lbl", "text": "MIN", "color": "#99FFFFFF", "fontSize": 10 }
                ]}
              ]
            },
            {
              "type": "RemoteBox",
              "id": "s_box",
              "contentAlignment": "Center",
              "modifier": { "width": 72, "height": 80, "background": "#26FFFFFF", "cornerRadius": 16 },
              "children": [
                { "type": "RemoteColumn", "id": "s_col", "horizontalAlignment": "CenterHorizontally", "children": [
                  { "type": "RemoteText", "id": "s_val", "text": "15", "color": "#22D3EE", "fontSize": 36, "fontWeight": "Bold" },
                  { "type": "RemoteText", "id": "s_lbl", "text": "SEC", "color": "#99FFFFFF", "fontSize": 10 }
                ]}
              ]
            }
          ]
        },
        {
          "type": "RemoteSpacer",
          "id": "s2",
          "modifier": { "height": 24 }
        },
        {
          "type": "RemoteText",
          "id": "10",
          "text": "Every step brings you closer to the finish line.",
          "color": "#CCBAE6FD",
          "fontSize": 13
        }
      ]
    }
  ]
}
""".trimIndent()

val A2UI_JSON_SYSTEM_PROMPT = """
You are an expert A2UI (Agent-to-UI) designer. You generate native Android UI using the A2UI v0.9 JSON protocol.

You must output ONLY valid JSONL (one JSON object per line). Do not include explanations or markdown.

# A2UI Protocol v0.9 — Message Sequence
You must produce exactly 3 lines in this order:

Line 1 — createSurface:
{"version":"v0.9","createSurface":{"surfaceId":"main","catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json","theme":{"primaryColor":"#6750A4"}}}

Line 2 — updateComponents:
{"version":"v0.9","updateComponents":{"surfaceId":"main","components":[...]}}

Line 3 — updateDataModel (if needed):
{"version":"v0.9","updateDataModel":{"surfaceId":"main","path":"/","value":{...}}}

# Component Types
All components have an "id" (string) and "component" (type string).
One component MUST have id "root".

## Layout Components
- Column: {"id":"..","component":"Column","children":["child1","child2"],"justify":"start|center|end|spaceBetween|spaceEvenly","align":"start|center|end|stretch"}
- Row: {"id":"..","component":"Row","children":["child1","child2"],"justify":"start|center|end|spaceBetween|spaceEvenly","align":"start|center|end|stretch"}
- Card: {"id":"..","component":"Card","child":"singleChildId"}
- List: {"id":"..","component":"List","children":[...],"direction":"vertical|horizontal"}
- Tabs: {"id":"..","component":"Tabs","tabs":[{"title":"Tab1","child":"tab1ContentId"}]}

## Display Components
- Text: {"id":"..","component":"Text","text":"Hello","variant":"h1|h2|h3|h4|h5|body|caption"}
- Image: {"id":"..","component":"Image","url":"https://...","fit":"cover|contain|fill","variant":"icon|avatar|smallFeature|mediumFeature|largeFeature|header"}
- Icon: {"id":"..","component":"Icon","name":"add|close|settings|home|search|star|favorite|delete|edit|share|send|arrowBack|arrowForward|check|warning|volumeUp|volumeDown"}
- Divider: {"id":"..","component":"Divider","axis":"horizontal|vertical"}

## Interaction Components
- Button: {"id":"..","component":"Button","child":"textId","variant":"default|primary|borderless","action":{"event":{"name":"action_name","context":{}}}}
- TextField: {"id":"..","component":"TextField","label":"Name","value":{"path":"/form/name"},"variant":"shortText|longText|number|obscured"}
- CheckBox: {"id":"..","component":"CheckBox","label":"Agree","value":{"path":"/form/agree"}}
- Slider: {"id":"..","component":"Slider","label":"Volume","min":0,"max":100,"value":{"path":"/volume"}}
- ChoicePicker: {"id":"..","component":"ChoicePicker","label":"Choose","variant":"mutuallyExclusive|multipleSelection","options":[{"label":"A","value":"a"}],"value":{"path":"/choice"}}

# SENSORY-AWARE WIDGET GENERATION (CRITICAL)
You will receive <DeviceContext> with live device state. Use it:
1. Low Battery (<20%, not charging): Show red warning card with open_settings action.
2. Flashlight on: Show glow effect and toggle button.
3. Show volume level and provide volume adjustment buttons.

# AppFunctions Integration
For local device actions, use button actions with functionCall:
- {"action":{"functionCall":{"call":"openUrl","args":{"url":"appfn:vibrate?duration=150"}}}}
Or use event actions:
- {"action":{"event":{"name":"vibrate"}}}

The renderer will intercept event names matching "appfn:xxx" patterns as local device functions.

# CRITICAL: Dark Glassmorphism Visual Style
Every widget you generate MUST follow this dark, glassy visual language:
- Theme: Set primaryColor to a neon accent like "#22D3EE" (cyan) or "#6366F1" (indigo).
- Background: Use Card components as frosted glass containers with dark styling.
- Typography: Use h1/h2 variant for hero numbers, caption for labels.
- Accent colors: Cyan #22D3EE, Indigo #6366F1, Fuchsia #D946EF.
- Large bold typography for countdown numbers.

# CRITICAL: Countdown & Timer Widget Pattern
When the user request involves a countdown, timer, deadline, or event tracking:
1. Use Column root with justify "center" and align "center".
2. Add a small caption Text for the header label (e.g., "COUNTDOWN TO").
3. Add an h3 Text for the event title with accent styling.
4. Add a Row containing 4 Card components, one per countdown unit (DAYS, HRS, MIN, SEC).
   - Each Card contains a Column with: h1 Text for the number, caption Text for the label.
5. Add a caption Text for motivational sub-title.
6. Compute countdown values from <DeviceContext> countdown_target_date if available.

# Design Rules
1. Always produce exactly 3 JSONL lines (createSurface, updateComponents, updateDataModel).
2. One component MUST have "id":"root".
3. Use the adjacency list pattern — reference children by ID, not inline.
4. Make UI beautiful, modern, Dark Glassmorphism styled.
5. Bind interactive values to the data model using {"path":"/key"}.
""".trimIndent()

val CMW_JSON_SYSTEM_PROMPT = """
You are an expert Server-Driven UI (SDUI) designer specializing in GPU-accelerated visual effects for Android.
Your task is to generate JSON that combines standard layout nodes with custom hardware-accelerated CMW nodes.

You must output ONLY valid, raw JSON. Do not include introductory text, explanations, or markdown code block formatting (do NOT use ```json).

# Allowed Node Types

## Standard Layout Nodes
1. "Text" — Display text.
   { "type": "Text", "text": "Hello", "color": "#FFFFFF", "fontSize": 24, "fontWeight": "Bold" }

2. "Spacer" — Vertical spacer.
   { "type": "Spacer", "height": 16 }

3. "Box" — Stack children on top of each other.
   { "type": "Box", "contentAlignment": "Center", "modifier": { ... }, "children": [...] }

4. "Column" — Vertical layout.
   { "type": "Column", "verticalArrangement": 8, "horizontalAlignment": "CenterHorizontally", "modifier": { ... }, "children": [...] }

5. "Row" — Horizontal layout.
   { "type": "Row", "horizontalArrangement": 8, "verticalAlignment": "CenterVertically", "modifier": { ... }, "children": [...] }

## Custom CMW Hardware-Accelerated Nodes

6. "LiquidGlassBackground" — Creates a continuous gooey mesh of animated colors.
   { "type": "LiquidGlassBackground", "colors": ["#6366F1", "#22D3EE", "#D946EF"] }
   - Uses Android RenderEffect (GPU-accelerated ColorMatrix + Blur) for liquid/gooey blending.
   - Place as the first child of a Box to create an animated background.

7. "GlassContainer" — A container that applies hardware blur and frosted glass lighting.
   { "type": "GlassContainer", "blurRadius": 30, "cornerRadius": 24, "fillMaxWidth": true, "children": [...] }
   - Renders as a semi-transparent box with blur, border, and rounded corners.

8. "FloatingGlassOrb" — A decorative glass-like 3D sphere that floats continuously.
   { "type": "FloatingGlassOrb", "baseColor": "#EC4899", "floatExpression": "sin(time * 2.0) * 20" }
   - The floatExpression uses sin(time * speed) to drive GPU-accelerated vertical oscillation.
   - Extract the speed multiplier from the expression.

9. "CircularProgressRing" — An animated circular arc/ring indicator.
   { "type": "CircularProgressRing", "progress": 0.75, "strokeColor": "#22D3EE", "ringBackgroundColor": "#1AFFFFFF", "strokeWidth": 6, "size": 80 }
   - Renders a ring-shaped progress arc using Canvas drawArc.
   - "progress" is a float 0.0–1.0 indicating how much of the ring is filled.
   - "size" is the diameter in dp. "strokeWidth" is the ring thickness in dp.
   - Use inside a Box with contentAlignment Center, then overlay a Text node for the value.

## Non-Square / Organic Shape Containers

10. "ShapeContainer" — Clip children into a named polygon shape (hexagon, star, blob, etc.).
   { "type": "ShapeContainer", "shape": "hexagon", "cornerSmoothing": 0.2, "modifier": { ... }, "children": [...] }
   - "shape" (String, required): One of "hexagon", "star", "blob", "pill", "squircle", "heart", "diamond", "octagon", "triangle", "circle".
   - "cornerSmoothing" (Float, optional, default 0.2): Controls smoothness of corner rounding (0 = sharp, 1 = super smooth).
   - "contentAlignment" (String, optional): Alignment for children ("Center", "TopStart", etc.).
   - "modifier" (Object, optional): Standard modifier with background, padding, width, height, fillMaxSize, fillMaxWidth.
   - PREFER ShapeContainer over plain Box for cards and containers to create organic, non-square widget shapes.
   - Use "blob" or "squircle" for smooth organic containers, "hexagon"/"octagon" for geometric looks, "heart" for playful widgets.

11. "MorphShapeContainer" — Animated morphing between two polygon shapes.
   { "type": "MorphShapeContainer", "shapeA": "hexagon", "shapeB": "circle", "morphSpeed": 0.5, "cornerSmoothing": 0.2, "modifier": { ... }, "children": [...] }
   - "shapeA" and "shapeB" (String, required): Two shape names (same values as ShapeContainer).
   - "morphSpeed" (Float, optional, default 0.5): Speed of the morph animation.
   - Continuously morphs between shapeA and shapeA for a mesmerizing animated container.
   - Great for profile cards, hero elements, or interactive widgets.

12. "HandDrawnContainer" — Container with organic, wobbly, hand-drawn style edges.
   { "type": "HandDrawnContainer", "vertices": 12, "wobbleAmount": 8.0, "modifier": { ... }, "children": [...] }
   - "vertices" (Integer, optional, default 12): Number of polygon vertices (more = smoother blob).
   - "wobbleAmount" (Float, optional, default 8.0): How much each vertex deviates (higher = more wobbly).
   - Creates a charming, organic, hand-drawn border effect — perfect for sketchy/playful widgets.

# CRITICAL: Prefer Non-Square Shapes
When generating widget containers, PREFER using ShapeContainer, MorphShapeContainer, or HandDrawnContainer instead of plain Box with cornerRadius.
This creates visually distinctive, non-square widgets as shown in the user's reference screenshots.
Only use plain Box with cornerRadius when the user explicitly asks for rectangular cards.

## Modifier Object (optional on any node)
{ "modifier": { "fillMaxSize": true, "fillMaxWidth": true, "padding": 16, "background": "#1A1A2E", "cornerRadius": 12 } }

# Design Guidelines
1. The root node should typically be a Box with fillMaxSize and a dark background (#0A0A14).
2. Use LiquidGlassBackground as the first child for animated color mesh backgrounds.
3. Overlay GlassContainer on top for frosted glass cards with content.
4. Use FloatingGlassOrb as decorative elements.
5. Keep designs visually stunning with bold color choices.
6. Output raw JSON only — no explanations.

# CRITICAL: Dark Glassmorphism Visual Style
Every widget you generate MUST follow this dark, glassy visual language:
- Background: Deep dark (#0A0A14) with LiquidGlassBackground animated blobs.
- Cards/Containers: Semi-transparent white (#1AFFFFFF or #26FFFFFF) with rounded corners (24–40).
- Text: White (#FFFFFF) for primary, muted (#99FFFFFF or #CCBAE6FD) for secondary.
- Accent colors: Cyan #22D3EE, Indigo #6366F1, Fuchsia #D946EF, Pink #EC4899.
- Large bold typography for hero numbers (fontSize 48–72, fontWeight Bold).
- Spacious padding (24–32) inside glass containers.
- Use GlassContainer for EVERY visible card/section — never plain opaque boxes.

# CRITICAL: Countdown & Timer Widget Pattern
When the user request involves a countdown, timer, deadline, or event tracking:
1. Use a Box root with fillMaxSize + dark background.
2. Add LiquidGlassBackground as the first child.
3. Place a GlassContainer in the center for the main content.
4. Inside the GlassContainer, create a Column with:
   a. A small uppercase header label (e.g., "COUNTDOWN TO", "MARATHON DAY") — fontSize 10–12, color #99FFFFFF, letterSpacing not supported, use uppercase text.
   b. A Spacer (height 16–24).
   c. A Row containing 4 countdown unit cards, each wrapped in a Box with contentAlignment Center:
      - Each unit is a Box (width/height 70–80) with background #26FFFFFF and cornerRadius 16.
      - Inside each Box: a Column centered with the large number Text (fontSize 32–48, Bold, #FFFFFF) and a small label Text (fontSize 9–11, #99FFFFFF) like "DAYS", "HRS", "MIN", "SEC".
   d. A Spacer (height 16–24).
   e. Optional: A "CircularProgressRing" showing overall progress toward the target date.
   f. A motivational sub-title Text (fontSize 14–16, color #CCBAE6FD).
5. Add FloatingGlassOrb elements for decoration.
6. Compute the countdown values from the <DeviceContext> countdown_target_date if available, otherwise use reasonable placeholder values (e.g., 42 days, 08 hours, 30 minutes, 15 seconds).

# Example Output (Countdown Widget):
{
  "type": "Box",
  "modifier": { "fillMaxSize": true, "background": "#0A0A14" },
  "children": [
    { "type": "LiquidGlassBackground", "colors": ["#6366F1", "#22D3EE", "#D946EF"] },
    { "type": "Box", "modifier": { "fillMaxSize": true, "background": "#000000" } },
    { "type": "GlassContainer", "blurRadius": 30, "cornerRadius": 32, "fillMaxWidth": true, "children": [
      { "type": "Column", "horizontalAlignment": "CenterHorizontally", "modifier": { "padding": 32 }, "children": [
        { "type": "Text", "text": "COUNTDOWN TO", "color": "#99FFFFFF", "fontSize": 11, "fontWeight": "Bold" },
        { "type": "Spacer", "height": 4 },
        { "type": "Text", "text": "MY FIRST MARATHON", "color": "#22D3EE", "fontSize": 20, "fontWeight": "Bold" },
        { "type": "Spacer", "height": 24 },
        { "type": "Row", "horizontalArrangement": 12, "verticalAlignment": "CenterVertically", "children": [
          { "type": "Box", "contentAlignment": "Center", "modifier": { "width": 72, "height": 80, "background": "#26FFFFFF", "cornerRadius": 16 }, "children": [
            { "type": "Column", "horizontalAlignment": "CenterHorizontally", "children": [
              { "type": "Text", "text": "42", "color": "#FFFFFF", "fontSize": 36, "fontWeight": "Bold" },
              { "type": "Text", "text": "DAYS", "color": "#99FFFFFF", "fontSize": 10 }
            ]}
          ]},
          { "type": "Box", "contentAlignment": "Center", "modifier": { "width": 72, "height": 80, "background": "#26FFFFFF", "cornerRadius": 16 }, "children": [
            { "type": "Column", "horizontalAlignment": "CenterHorizontally", "children": [
              { "type": "Text", "text": "08", "color": "#FFFFFF", "fontSize": 36, "fontWeight": "Bold" },
              { "type": "Text", "text": "HRS", "color": "#99FFFFFF", "fontSize": 10 }
            ]}
          ]},
          { "type": "Box", "contentAlignment": "Center", "modifier": { "width": 72, "height": 80, "background": "#26FFFFFF", "cornerRadius": 16 }, "children": [
            { "type": "Column", "horizontalAlignment": "CenterHorizontally", "children": [
              { "type": "Text", "text": "30", "color": "#FFFFFF", "fontSize": 36, "fontWeight": "Bold" },
              { "type": "Text", "text": "MIN", "color": "#99FFFFFF", "fontSize": 10 }
            ]}
          ]},
          { "type": "Box", "contentAlignment": "Center", "modifier": { "width": 72, "height": 80, "background": "#26FFFFFF", "cornerRadius": 16 }, "children": [
            { "type": "Column", "horizontalAlignment": "CenterHorizontally", "children": [
              { "type": "Text", "text": "15", "color": "#22D3EE", "fontSize": 36, "fontWeight": "Bold" },
              { "type": "Text", "text": "SEC", "color": "#99FFFFFF", "fontSize": 10 }
            ]}
          ]}
        ]},
        { "type": "Spacer", "height": 24 },
        { "type": "Text", "text": "Every step brings you closer to the finish line.", "color": "#CCBAE6FD", "fontSize": 13 }
      ]}
    ]},
    { "type": "FloatingGlassOrb", "baseColor": "#6366F1", "floatExpression": "sin(time * 1.5) * 25" },
    { "type": "FloatingGlassOrb", "baseColor": "#22D3EE", "floatExpression": "sin(time * 2.0) * 20" }
  ]
}
""".trimIndent()

val LIQUID_GLASS_RC_JSON = """
{
  "backgroundColor": "#0A0A14",
  "elements": [
    {
      "type": "RemoteBox",
      "id": "root",
      "modifier": {
        "fillMaxSize": true,
        "background": "#0A0A14"
      },
      "children": [
        {
          "type": "RemoteBox",
          "id": "glass_card",
          "contentAlignment": "Center",
          "modifier": {
            "width": 360,
            "height": 580,
            "background": "#1AFFFFFF",
            "cornerRadius": 40,
            "padding": 32
          },
          "children": [
            {
              "type": "RemoteColumn",
              "id": "content",
              "horizontalAlignment": "CenterHorizontally",
              "children": [
                {
                  "type": "RemoteRow",
                  "id": "header",
                  "modifier": { "fillMaxWidth": true },
                  "children": [
                    {
                      "type": "RemoteText",
                      "id": "now_playing",
                      "text": "NOW PLAYING",
                      "color": "#99FFFFFF",
                      "fontSize": 10,
                      "fontWeight": "Bold"
                    }
                  ]
                },
                { "type": "RemoteSpacer", "id": "s1", "modifier": { "height": 32 } },
                {
                  "type": "RemoteBox",
                  "id": "art",
                  "modifier": {
                    "width": 200,
                    "height": 200,
                    "background": "#FF22D3EE",
                    "cornerRadius": 100
                  }
                },
                { "type": "RemoteSpacer", "id": "s2", "modifier": { "height": 32 } },
                {
                  "type": "RemoteText",
                  "id": "title",
                  "text": "Nebula Drift",
                  "color": "#FFFFFF",
                  "fontSize": 28,
                  "fontWeight": "Bold"
                },
                {
                  "type": "RemoteText",
                  "id": "artist",
                  "text": "Synthwave Explorers",
                  "color": "#CCBAE6FD",
                  "fontSize": 14
                },
                { "type": "RemoteSpacer", "id": "s3", "modifier": { "height": 32 } },
                {
                  "type": "RemoteBox",
                  "id": "progress_bg",
                  "modifier": {
                    "fillMaxWidth": true,
                    "height": 8,
                    "background": "#1AFFFFFF",
                    "cornerRadius": 4
                  },
                  "children": [
                    {
                      "type": "RemoteBox",
                      "id": "progress_fill",
                      "modifier": {
                        "width": 120,
                        "height": 8,
                        "background": "#FF6366F1",
                        "cornerRadius": 4
                      }
                    }
                  ]
                },
                { "type": "RemoteSpacer", "id": "s4", "modifier": { "height": 32 } },
                {
                  "type": "RemoteRow",
                  "id": "controls",
                  "modifier": { "fillMaxWidth": true },
                  "children": [
                     {
                        "type": "RemoteBox",
                        "id": "play_btn",
                        "modifier": {
                            "width": 80,
                            "height": 80,
                            "background": "#26FFFFFF",
                            "cornerRadius": 40,
                            "clickableAction": "appfn:toast?message=Playing..."
                        },
                        "children": [
                            {
                                "type": "RemoteText",
                                "id": "play_icon",
                                "text": "▶",
                                "color": "#FFFFFF",
                                "fontSize": 32
                            }
                        ]
                     }
                  ]
                }
              ]
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
    onA2uiRender: () -> Unit = {},
    onPredefinedRender: (String) -> Unit = {},
    onCmwRender: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Model selection state
    var selectedModel by remember { mutableStateOf("GLM") }
    val geminiModels = listOf("Gemini", "Gemini JSON", "Gemini A2UI")
    val glmModels = listOf("GLM", "GLM JSON", "GLM A2UI")
    val pasteModels = listOf("Paste DSL", "Paste JSON", "Paste A2UI")
    val predefinedModels = listOf("Liquid Glass")
    val cmwModels = listOf("Gemini CMW", "GLM CMW", "Paste CMW")

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
            "Paste A2UI" -> "粘贴 A2UI JSONL 消息，直接渲染为原生 Android UI。"
            "Gemini" -> "输入自然语言描述，Gemini 将生成 SDUI JSON 并编译为 RC 文档渲染。"
            "GLM" -> "输入自然语言描述，GLM 将生成 SDUI JSON 并编译为 RC 文档渲染。"
            "GLM JSON" -> "输入自然语言描述，GLM 将生成 SDUI JSON 并在本地渲染。"
            "GLM A2UI" -> "输入自然语言描述，GLM 将生成 A2UI 协议 JSONL 并渲染为原生 Android UI。"
            "Gemini JSON" -> "输入自然语言描述，Gemini 将生成 SDUI JSON 并在本地渲染。"
            "Gemini A2UI" -> "输入自然语言描述，Gemini 将生成 A2UI 协议 JSONL 并渲染为原生 Android UI。"
            "Liquid Glass" -> "渲染一个预定义的、具有高性能图形效果的 Liquid Glass 音乐播放器 UI。"
            "Gemini CMW" -> "输入自然语言描述，Gemini 将生成含 GPU 加速效果的自定义 JSON 并本地渲染。"
            "GLM CMW" -> "输入自然语言描述，GLM 将生成含 GPU 加速效果的自定义 JSON 并本地渲染。"
            "Paste CMW" -> "粘贴自定义 CMW JSON（含 LiquidGlassBackground / GlassContainer / FloatingGlassOrb 节点），直接渲染。"
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
        
        // Row 1: Gemini
        Text("Gemini 系列:", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            geminiModels.forEach { model ->
                FilterChip(
                    selected = selectedModel == model,
                    onClick = { selectedModel = model },
                    label = { Text(model) }
                )
            }
        }
        
        // Row 2: GLM
        Text("GLM 系列:", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            glmModels.forEach { model ->
                FilterChip(
                    selected = selectedModel == model,
                    onClick = { selectedModel = model },
                    label = { Text(model) }
                )
            }
        }

        // Row 3: Paste
        Text("手动粘贴:", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pasteModels.forEach { model ->
                FilterChip(
                    selected = selectedModel == model,
                    onClick = { selectedModel = model },
                    label = { Text(model) }
                )
            }
        }

        // Row 4: Predefined
        Text("预定义 UI:", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            predefinedModels.forEach { model ->
                FilterChip(
                    selected = selectedModel == model,
                    onClick = { selectedModel = model },
                    label = { Text(model) }
                )
            }
        }

        // Row 5: CMW Custom JSON
        Text("CMW 自定义 JSON (GPU 效果):", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cmwModels.forEach { model ->
                FilterChip(
                    selected = selectedModel == model,
                    onClick = { selectedModel = model },
                    label = { Text(model) }
                )
            }
        }

        // Prompt input
        val isPasteMode = selectedModel == "Paste DSL" || selectedModel == "Paste JSON" || selectedModel == "Paste A2UI" || selectedModel == "Paste CMW"
        val isPredefinedMode = selectedModel == "Liquid Glass"
        
        if (!isPredefinedMode) {
            val labelText = when (selectedModel) {
                "Paste DSL" -> "Kotlin DSL 代码"
                "Paste JSON" -> "JSON UI"
                "Paste A2UI" -> "A2UI JSONL"
                "Paste CMW" -> "CMW JSON"
                else -> "描述你想要的 UI"
            }
            val placeholderText = when (selectedModel) {
                "Paste DSL" -> "粘贴 Kotlin DSL，例如：RemoteColumn { ... }"
                "Paste JSON" -> "粘贴 JSON，例如：{ \"backgroundColor\": \"#FFF\", \"elements\": [...] }"
                "Paste A2UI" -> "粘贴 A2UI JSONL，每行一个 JSON 消息..."
                "Paste CMW" -> "粘贴 CMW JSON，例如：{ \"type\": \"Column\", \"children\": [...] }"
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
        }

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
        
        // Stats message
        if (viewModel.tokenUsage != null || viewModel.generationSpeed != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                viewModel.tokenUsage?.let { usage ->
                    Text(
                        text = "📊 $usage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                viewModel.generationSpeed?.let { speed ->
                    Text(
                        text = "⚡ $speed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Generate button
        val buttonText = when (selectedModel) {
            "Paste DSL" -> "🚀 编译并渲染"
            "Paste JSON" -> "🚀 直接渲染"
            "Paste A2UI" -> "🚀 直接渲染"
            "Liquid Glass" -> "✨ 渲染音乐播放器"
            "Gemini" -> "✨ 生成并渲染"
            "GLM" -> "✨ 生成并渲染"
            "GLM JSON" -> "✨ 生成并渲染"
            "GLM A2UI" -> "✨ 生成并渲染"
            "Gemini CMW" -> "✨ 生成并渲染"
            "GLM CMW" -> "✨ 生成并渲染"
            "Paste CMW" -> "🚀 直接渲染"
            "Gemini JSON" -> "✨ 生成并渲染"
            "Gemini A2UI" -> "✨ 生成并渲染"
            else -> "🚀 编译并渲染"
        }
        Button(
            onClick = {
                if (!isPredefinedMode && prompt.isBlank()) {
                    errorMessage = when (selectedModel) {
                        "Paste DSL" -> "请粘贴 Kotlin DSL 代码"
                        "Paste JSON" -> "请粘贴 JSON"
                        "Paste A2UI" -> "请粘贴 A2UI JSONL"
                        "Paste CMW" -> "请粘贴 CMW JSON"
                        else -> "请输入 Prompt"
                    }
                    return@Button
                }
                isLoading = true
                errorMessage = null
                viewModel.clear() // Clear old stats

                // ── Predefined: Liquid Glass ──
                if (selectedModel == "Liquid Glass") {
                    onPredefinedRender("LiquidGlass")
                    isLoading = false
                    return@Button
                }

                // ── Paste JSON: local render, no backend ──
                if (selectedModel == "Paste JSON") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            // 1. Convert JSON to Kotlin DSL for display
                            val kotlinDsl = convertJsonToKotlinDsl(prompt)
                            viewModel.setJsonDslData(prompt, kotlinDsl)
                            android.util.Log.d("Generator", "Converted Kotlin DSL:\n$kotlinDsl")
                            statusMessage = "转换后的 Kotlin DSL:\n${kotlinDsl.take(500)}${if (kotlinDsl.length > 500) "\n..." else ""}"

                            // 2. Parse and render
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

                // ── Paste A2UI: local A2UI render, no backend ──
                if (selectedModel == "Paste A2UI") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val normalizedJsonl = normalizeA2uiInput(prompt.trim())
                            viewModel.setA2uiData(normalizedJsonl)
                            android.util.Log.d("Generator", "Paste A2UI normalized JSONL:\n${normalizedJsonl.take(500)}")
                            statusMessage = "A2UI JSONL 已加载，准备渲染..."
                            isLoading = false
                            onA2uiRender()
                        } catch (e: Exception) {
                            errorMessage = "A2UI JSONL 加载失败: ${e.message}"
                            isLoading = false
                        }
                    }
                    return@Button
                }

                // ── Paste CMW: local CMW custom JSON render, no backend ──
                if (selectedModel == "Paste CMW") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val cleaned = cleanJsonResponse(prompt.trim())
                            viewModel.setCmwData(cleaned)
                            android.util.Log.d("Generator", "Paste CMW JSON:\n${cleaned.take(500)}")
                            statusMessage = "CMW JSON 已加载，准备渲染..."
                            isLoading = false
                            onCmwRender()
                        } catch (e: Exception) {
                            errorMessage = "CMW JSON 加载失败: ${e.message}"
                            isLoading = false
                        }
                    }
                    return@Button
                }

                // ── Gemini JSON: generate SDUI JSON via Gemini, render locally ──
                if (selectedModel == "Gemini JSON") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // 1. 获取设备当前的 AppFunctions 实时上下文
                            val contextJson = AppFunctionManager.getDeviceContext(context).toString()
                            val enrichedPrompt = """
                                <DeviceContext>
                                $contextJson
                                </DeviceContext>
                                
                                User Request: $prompt
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在采集本地 AppFunctions 状态并生成 UI..."
                                android.util.Log.d("Generator", "Enriched Prompt:\n$enrichedPrompt")
                            }

                            // 2. 将装配了 AppFunctions 状态的 Prompt 传递给大模型
                            val aiResult = callGeminiJsonApi(enrichedPrompt)
                            val jsonResponse = aiResult.content
                            android.util.Log.d("Generator", "Gemini JSON response: $jsonResponse")
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在解析 JSON..."
                            }
                            val cleaned = cleanJsonResponse(jsonResponse)
                            val doc = parseJsonUiDocument(cleaned)
                            val kotlinDsl = convertJsonToKotlinDsl(cleaned)
                            withContext(Dispatchers.Main) {
                                viewModel.jsonUiDocument = doc
                                viewModel.setJsonDslData(cleaned, kotlinDsl, aiResult.tokenUsage, aiResult.speed)
                                android.util.Log.d("Generator", "Converted Kotlin DSL:\n$kotlinDsl")
                                statusMessage = "转换后的 Kotlin DSL:\n${kotlinDsl.take(500)}${if (kotlinDsl.length > 500) "\n..." else ""}"
                                isLoading = false
                                onJsonRender()
                            }
                        } catch (e: Exception) {
                            val detail = e.message ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                errorMessage = "Gemini JSON 生成失败: $detail"
                                statusMessage = null
                                isLoading = false
                            }
                        }
                    }
                    return@Button
                }

                // ── Gemini A2UI: generate A2UI JSONL via Gemini, render natively ──
                if (selectedModel == "Gemini A2UI") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val contextJson = AppFunctionManager.getDeviceContext(context).toString()
                            val enrichedPrompt = """
                                <DeviceContext>
                                $contextJson
                                </DeviceContext>
                                
                                User Request: $prompt
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在调用 Gemini A2UI 生成原生 UI..."
                                android.util.Log.d("Generator", "A2UI Enriched Prompt:\n$enrichedPrompt")
                            }

                            val aiResult = callGeminiA2uiApi(enrichedPrompt)
                            val jsonResponse = aiResult.content
                            android.util.Log.d("Generator", "Gemini A2UI response: $jsonResponse")

                            // Clean and normalize (auto-wrap raw component arrays)
                            val normalized = normalizeA2uiInput(jsonResponse)
                            android.util.Log.d("Generator", "A2UI Normalized JSONL:\n$normalized")

                            withContext(Dispatchers.Main) {
                                viewModel.setA2uiData(normalized, aiResult.tokenUsage, aiResult.speed)
                                statusMessage = "A2UI JSONL 生成成功"
                                isLoading = false
                                onA2uiRender()
                            }
                        } catch (e: Exception) {
                            val detail = e.message ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                errorMessage = "Gemini A2UI 生成失败: $detail"
                                statusMessage = null
                                isLoading = false
                            }
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
                            // 1. 获取设备当前的 AppFunctions 实时上下文
                            val contextJson = AppFunctionManager.getDeviceContext(context).toString()
                            val enrichedPrompt = """
                                <DeviceContext>
                                $contextJson
                                </DeviceContext>
                                
                                User Request: $prompt
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在采集本地 AppFunctions 状态并生成 UI..."
                                android.util.Log.d("Generator", "Enriched Prompt:\n$enrichedPrompt")
                            }

                            // 2. 将装配了 AppFunctions 状态的 Prompt 传递给大模型
                            val aiResult = callGlmJsonApi(enrichedPrompt)
                            val jsonResponse = aiResult.content
                            android.util.Log.d("Generator", "GLM JSON response: $jsonResponse")
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在解析 JSON..."
                            }
                            val cleaned = cleanJsonResponse(jsonResponse)
                            val doc = parseJsonUiDocument(cleaned)
                            val kotlinDsl = convertJsonToKotlinDsl(cleaned)
                            withContext(Dispatchers.Main) {
                                viewModel.jsonUiDocument = doc
                                viewModel.setJsonDslData(cleaned, kotlinDsl, aiResult.tokenUsage, aiResult.speed)
                                android.util.Log.d("Generator", "Converted Kotlin DSL:\n$kotlinDsl")
                                statusMessage = "转换后的 Kotlin DSL:\n${kotlinDsl.take(500)}${if (kotlinDsl.length > 500) "\n..." else ""}"
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

                // ── GLM A2UI: generate A2UI JSONL via GLM, render natively ──
                if (selectedModel == "GLM A2UI") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val contextJson = AppFunctionManager.getDeviceContext(context).toString()
                            val enrichedPrompt = """
                                <DeviceContext>
                                $contextJson
                                </DeviceContext>
                                
                                User Request: $prompt
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在调用 GLM A2UI 生成原生 UI..."
                                android.util.Log.d("Generator", "A2UI Enriched Prompt:\n$enrichedPrompt")
                            }

                            val aiResult = callGlmA2uiApi(enrichedPrompt)
                            val jsonResponse = aiResult.content
                            android.util.Log.d("Generator", "GLM A2UI response: $jsonResponse")

                            // Clean and normalize (auto-wrap raw component arrays)
                            val normalized = normalizeA2uiInput(jsonResponse)
                            android.util.Log.d("Generator", "A2UI Normalized JSONL:\n$normalized")

                            withContext(Dispatchers.Main) {
                                viewModel.setA2uiData(normalized, aiResult.tokenUsage, aiResult.speed)
                                statusMessage = "A2UI JSONL 生成成功"
                                isLoading = false
                                onA2uiRender()
                            }
                        } catch (e: Exception) {
                            val detail = e.message ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                errorMessage = "GLM A2UI 生成失败: $detail"
                                statusMessage = null
                                isLoading = false
                            }
                        }
                    }
                    return@Button
                }

                // ── Gemini CMW: generate CMW custom JSON via Gemini, render locally ──
                if (selectedModel == "Gemini CMW") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val contextJson = AppFunctionManager.getDeviceContext(context).toString()
                            val enrichedPrompt = """
                                <DeviceContext>
                                $contextJson
                                </DeviceContext>

                                User Request: $prompt
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在调用 Gemini CMW 生成自定义 UI..."
                            }

                            val aiResult = callGeminiCmwApi(enrichedPrompt)
                            val jsonResponse = aiResult.content
                            android.util.Log.d("Generator", "Gemini CMW response: $jsonResponse")

                            val cleaned = cleanJsonResponse(jsonResponse)
                            withContext(Dispatchers.Main) {
                                viewModel.setCmwData(cleaned, aiResult.tokenUsage, aiResult.speed)
                                statusMessage = "CMW JSON 生成成功"
                                isLoading = false
                                onCmwRender()
                            }
                        } catch (e: Exception) {
                            val detail = e.message ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                errorMessage = "Gemini CMW 生成失败: $detail"
                                statusMessage = null
                                isLoading = false
                            }
                        }
                    }
                    return@Button
                }

                // ── GLM CMW: generate CMW custom JSON via GLM, render locally ──
                if (selectedModel == "GLM CMW") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val contextJson = AppFunctionManager.getDeviceContext(context).toString()
                            val enrichedPrompt = """
                                <DeviceContext>
                                $contextJson
                                </DeviceContext>

                                User Request: $prompt
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在调用 GLM CMW 生成自定义 UI..."
                            }

                            val aiResult = callGlmCmwApi(enrichedPrompt)
                            val jsonResponse = aiResult.content
                            android.util.Log.d("Generator", "GLM CMW response: $jsonResponse")

                            val cleaned = cleanJsonResponse(jsonResponse)
                            withContext(Dispatchers.Main) {
                                viewModel.setCmwData(cleaned, aiResult.tokenUsage, aiResult.speed)
                                statusMessage = "CMW JSON 生成成功"
                                isLoading = false
                                onCmwRender()
                            }
                        } catch (e: Exception) {
                            val detail = e.message ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                errorMessage = "GLM CMW 生成失败: $detail"
                                statusMessage = null
                                isLoading = false
                            }
                        }
                    }
                    return@Button
                }

                // ── Gemini: RC JSON AST → Backend Compile → RemoteCompose Player ──
                if (selectedModel == "Gemini") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val contextJson = AppFunctionManager.getDeviceContext(context).toString()
                            val enrichedPrompt = """
                                <DeviceContext>
                                $contextJson
                                </DeviceContext>

                                User Request: $prompt
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在生成 RC JSON AST..."
                            }

                            val aiResult = callGeminiJsonApi(enrichedPrompt)
                            val jsonAst = cleanJsonResponse(aiResult.content)
                            android.util.Log.d("Generator", "Gemini RC AST JSON: $jsonAst")

                            withContext(Dispatchers.Main) {
                                statusMessage = "正在编译 RC 文档..."
                            }

                            val rcBytes = RcCompiler.compileJsonToRc(jsonAst)
                                ?: throw IllegalStateException("Backend compilation failed — no bytes returned")

                            withContext(Dispatchers.Main) {
                                statusMessage = "RC 文档编译成功 (${rcBytes.size} bytes)"
                                viewModel.setGeneratedData(rcBytes, jsonAst, aiResult.tokenUsage, aiResult.speed)
                                isLoading = false
                                onGenerationSuccess()
                            }
                        } catch (e: Exception) {
                            val detail = e.message ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                errorMessage = "Gemini 生成失败: $detail"
                                statusMessage = null
                                isLoading = false
                            }
                        }
                    }
                    return@Button
                }

                // ── GLM: RC JSON AST → Backend Compile → RemoteCompose Player ──
                if (selectedModel == "GLM") {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val contextJson = AppFunctionManager.getDeviceContext(context).toString()
                            val enrichedPrompt = """
                                <DeviceContext>
                                $contextJson
                                </DeviceContext>

                                User Request: $prompt
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                statusMessage = "正在生成 RC JSON AST..."
                            }

                            val aiResult = callGlmJsonApi(enrichedPrompt)
                            val jsonAst = cleanJsonResponse(aiResult.content)
                            android.util.Log.d("Generator", "GLM RC AST JSON: $jsonAst")

                            withContext(Dispatchers.Main) {
                                statusMessage = "正在编译 RC 文档..."
                            }

                            val rcBytes = RcCompiler.compileJsonToRc(jsonAst)
                                ?: throw IllegalStateException("Backend compilation failed — no bytes returned")

                            withContext(Dispatchers.Main) {
                                statusMessage = "RC 文档编译成功 (${rcBytes.size} bytes)"
                                viewModel.setGeneratedData(rcBytes, jsonAst, aiResult.tokenUsage, aiResult.speed)
                                isLoading = false
                                onGenerationSuccess()
                            }
                        } catch (e: Exception) {
                            val detail = e.message ?: e.javaClass.simpleName
                            withContext(Dispatchers.Main) {
                                errorMessage = "GLM 生成失败: $detail"
                                statusMessage = null
                                isLoading = false
                            }
                        }
                    }
                    return@Button
                }

                // ── Paste DSL: remote compilation ──
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        withContext(Dispatchers.Main) {
                            statusMessage = "正在编译 DSL..."
                        }
                        val cleanedJson = cleanJsonResponse(prompt)

                        withContext(Dispatchers.Main) {
                            statusMessage = "正在编译 RC 文档..."
                        }
                        val rcBytes = RcCompiler.fetchRcBytesFromBackend(cleanedJson)
                            ?: throw IllegalStateException("Backend compilation failed — no bytes returned")

                        withContext(Dispatchers.Main) {
                            statusMessage = "RC 文档生成成功 (${rcBytes.size} bytes)"
                            viewModel.setGeneratedData(rcBytes, cleanedJson)
                            isLoading = false
                            onGenerationSuccess()
                        }
                    } catch (e: Exception) {
                        val detail = e.message ?: e.javaClass.simpleName
                        withContext(Dispatchers.Main) {
                            errorMessage = "编译失败: $detail"
                            statusMessage = null
                            isLoading = false
                        }
                    }
                }
            },
            enabled = !isLoading && (isPredefinedMode || prompt.isNotBlank()),
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
            text = "1. 选择 AI 模型 (Gemini 或 GLM)\n" +
                    "2. 在上方输入你想要的 UI 描述\n" +
                    "3. 点击「生成并预览」按钮\n" +
                    "4. AI 将生成 JSON 并编译为 RC 文档\n" +
                    "5. 自动跳转到预览页面渲染\n" +
                    "6. 点击按钮可查看事件日志",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Show generated Kotlin DSL if available (from Paste JSON)
        viewModel.generatedKotlinDsl?.let { dsl ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "转换后的 Kotlin DSL:",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = Color(0xFFF5F5F5)
            ) {
                Text(
                    text = dsl,
                    modifier = Modifier.padding(8.dp),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

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

data class AiResult(
    val content: String,
    val tokenUsage: String? = null,
    val speed: String? = null
)

/**
 * Call the Gemini API with the SDUI JSON system prompt.
 * Uses gemini-1.5-flash to generate JSON UI documents.
 */
/**
 * Builds the full system prompt for JSON-based SDUI generation by combining
 * the base SDUI_JSON_SYSTEM_PROMPT with dynamically injected AppFunctions.
 */
private fun buildJsonSystemPrompt(): String {
    return SDUI_JSON_SYSTEM_PROMPT + "\n\n" + AppFunctionManager.buildPromptSection()
}

/**
 * Builds the full system prompt for A2UI generation.
 */
private fun buildA2uiSystemPrompt(): String {
    return A2UI_JSON_SYSTEM_PROMPT + "\n\n" + AppFunctionManager.buildPromptSection()
}

/**
 * Call the Gemini API with the A2UI system prompt.
 * Uses gemini-1.5-flash to generate A2UI JSONL messages.
 */
private suspend fun callGeminiA2uiApi(prompt: String): AiResult = withContext(Dispatchers.IO) {
    try {
        val apiKey = getGeminiApiKey()
        val fullSystemPrompt = buildA2uiSystemPrompt()
        val startTime = System.currentTimeMillis()

        val model = GenerativeModel(
            modelName = "gemini-3.5-flash",
            apiKey = apiKey,
            systemInstruction = content { text(fullSystemPrompt) }
        )

        val response = model.generateContent(prompt)
        val endTime = System.currentTimeMillis()
        val result = response.text ?: throw IllegalStateException("Empty response from Gemini")

        val usage = response.usageMetadata
        val tokens = usage?.let { "${it.totalTokenCount} tokens" }
        val duration = (endTime - startTime) / 1000.0
        val speed = if (duration > 0 && usage != null) {
            String.format("%.1f tokens/s", usage.candidatesTokenCount / duration)
        } else null

        AiResult(result, tokens, speed)
    } catch (e: Exception) {
        throw Exception("Gemini A2UI API 调用失败: ${e.message}", e)
    }
}

/**
 * Call the Gemini API with the SDUI JSON system prompt.
 * Dynamically injects available AppFunctions into the system prompt
 * so the model knows what local tools it can bind to interactive elements.
 */
private suspend fun callGeminiJsonApi(prompt: String): AiResult = withContext(Dispatchers.IO) {
    try {
        val apiKey = getGeminiApiKey()
        val fullSystemPrompt = buildJsonSystemPrompt()
        val startTime = System.currentTimeMillis()

        val model = GenerativeModel(
            modelName = "gemini-3.5-flash",
            apiKey = apiKey,
            systemInstruction = content { text(fullSystemPrompt) }
        )

        val response = model.generateContent(prompt)
        val endTime = System.currentTimeMillis()
        val result = response.text ?: throw IllegalStateException("Empty response from Gemini")

        val usage = response.usageMetadata
        val tokens = usage?.let { "${it.totalTokenCount} tokens" }
        val duration = (endTime - startTime) / 1000.0
        val speed = if (duration > 0 && usage != null) {
            String.format("%.1f tokens/s", usage.candidatesTokenCount / duration)
        } else null

        AiResult(result, tokens, speed)
    } catch (e: Exception) {
        throw Exception("Gemini JSON API 调用失败: ${e.message}", e)
    }
}

/**
 * Call the GLM API with the SDUI JSON system prompt.
 */
private suspend fun callGlmJsonApi(prompt: String): AiResult = withContext(Dispatchers.IO) {
    try {
        val apiKey = "0a5cd5342cd24f2f8e4e44af433be613.AOsuKOY2hIaOKgVT"
        val client = OkHttp3Client.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val startTime = System.currentTimeMillis()
        val requestJson = JSONObject().apply {
            put("model", "glm-4-flash")
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", buildJsonSystemPrompt())
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
        val endTime = System.currentTimeMillis()
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
        val content = message.getString("content")

        val usage = json.optJSONObject("usage")
        val tokens = usage?.let { "${it.optInt("total_tokens")} tokens" }
        val completionTokens = usage?.optInt("completion_tokens") ?: 0
        val duration = (endTime - startTime) / 1000.0
        val speed = if (duration > 0 && completionTokens > 0) {
            String.format("%.1f tokens/s", completionTokens / duration)
        } else null

        AiResult(content, tokens, speed)
    } catch (e: Exception) {
        throw Exception("GLM JSON API 调用失败: ${e.message}", e)
    }
}

/**
 * Call the GLM API with the A2UI system prompt.
 */
private suspend fun callGlmA2uiApi(prompt: String): AiResult = withContext(Dispatchers.IO) {
    try {
        val apiKey = "0a5cd5342cd24f2f8e4e44af433be613.AOsuKOY2hIaOKgVT"
        val client = OkHttp3Client.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val startTime = System.currentTimeMillis()
        val requestJson = JSONObject().apply {
            put("model", "glm-4-flash")
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", buildA2uiSystemPrompt())
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
        val endTime = System.currentTimeMillis()
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
        val content = message.getString("content")

        val usage = json.optJSONObject("usage")
        val tokens = usage?.let { "${it.optInt("total_tokens")} tokens" }
        val completionTokens = usage?.optInt("completion_tokens") ?: 0
        val duration = (endTime - startTime) / 1000.0
        val speed = if (duration > 0 && completionTokens > 0) {
            String.format("%.1f tokens/s", completionTokens / duration)
        } else null

        AiResult(content, tokens, speed)
    } catch (e: Exception) {
        throw Exception("GLM A2UI API 调用失败: ${e.message}", e)
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

/**
 * Normalizes A2UI input.
 * If the input is a raw JSON array of components, wraps it in the necessary
 * createSurface and updateComponents message envelope.
 */
private fun normalizeA2uiInput(input: String): String {
    val cleaned = cleanJsonResponse(input).trim()
    
    // If it's already A2UI JSONL (starts with {), just return cleaned
    if (cleaned.startsWith("{")) {
        return cleaned
    }
    
    // If it's a raw component array (starts with [), wrap it
        return cleaned
}

/**
 * Call the Gemini API with the CMW custom JSON system prompt.
 * Uses gemini-1.5-flash to generate CMW JSON with hardware-accelerated effects.
 */
private suspend fun callGeminiCmwApi(prompt: String): AiResult = withContext(Dispatchers.IO) {
    try {
        val apiKey = getGeminiApiKey()
        val startTime = System.currentTimeMillis()

        val model = GenerativeModel(
            modelName = "gemini-3.5-flash",
            apiKey = apiKey,
            systemInstruction = content { text(CMW_JSON_SYSTEM_PROMPT) }
        )

        val response = model.generateContent(prompt)
        val endTime = System.currentTimeMillis()
        val result = response.text ?: throw IllegalStateException("Empty response from Gemini")

        val usage = response.usageMetadata
        val tokens = usage?.let { "${it.totalTokenCount} tokens" }
        val duration = (endTime - startTime) / 1000.0
        val speed = if (duration > 0 && usage != null) {
            String.format("%.1f tokens/s", usage.candidatesTokenCount / duration)
        } else null

        AiResult(result, tokens, speed)
    } catch (e: Exception) {
        throw Exception("Gemini CMW API 调用失败: ${e.message}", e)
    }
}

/**
 * Call the GLM API with the CMW custom JSON system prompt.
 * Uses glm-4-flash to generate CMW JSON with hardware-accelerated effects.
 */
private suspend fun callGlmCmwApi(prompt: String): AiResult = withContext(Dispatchers.IO) {
    try {
        val apiKey = "0a5cd5342cd24f2f8e4e44af433be613.AOsuKOY2hIaOKgVT"
        val client = OkHttp3Client.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val startTime = System.currentTimeMillis()
        val requestJson = JSONObject().apply {
            put("model", "glm-4-flash")
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", CMW_JSON_SYSTEM_PROMPT)
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
        val endTime = System.currentTimeMillis()
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
        val content = message.getString("content")

        val usage = json.optJSONObject("usage")
        val tokens = usage?.let { "${it.optInt("total_tokens")} tokens" }
        val completionTokens = usage?.optInt("completion_tokens") ?: 0
        val duration = (endTime - startTime) / 1000.0
        val speed = if (duration > 0 && completionTokens > 0) {
            String.format("%.1f tokens/s", completionTokens / duration)
        } else null

        AiResult(content, tokens, speed)
    } catch (e: Exception) {
        throw Exception("GLM CMW API 调用失败: ${e.message}", e)
    }
}
