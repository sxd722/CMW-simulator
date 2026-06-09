package com.example.cmw_simulator

import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.a2ui.compose.A2UIConfig
import com.a2ui.compose.A2UIRenderer
import com.a2ui.compose.rememberA2UIRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

// ── Main Navigation with Bottom Tabs ──────────────────────────────────────────

@Composable
fun AppNavigation(sharedViewModel: SharedRcViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val generatorNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("⚙\uFE0F") },
                    label = { Text("Generator", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("\uD83D\uDCD0") },
                    label = { Text("RC Viewer", fontSize = 12.sp) }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> GeneratorTab(sharedViewModel, generatorNavController)
                1 -> RcViewerTab()
            }
        }
    }
}

// ── Tab 1: Generator ──────────────────────────────────────────────────────────

@Composable
private fun GeneratorTab(
    sharedViewModel: SharedRcViewModel,
    navController: androidx.navigation.NavHostController
) {
    NavHost(navController = navController, startDestination = "generator") {
        composable("generator") {
            GeneratorScreen(
                viewModel = sharedViewModel,
                onGenerationSuccess = { navController.navigate("tester") },
                onJsonRender = { navController.navigate("json_renderer") },
                onA2uiRender = { navController.navigate("a2ui_renderer") },
                onPredefinedRender = { type ->
                    if (type == "LiquidGlass") navController.navigate("liquid_glass")
                },
                onCmwRender = { navController.navigate("cmw_renderer") }
            )
        }
        composable("liquid_glass") {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            var isCompiling by remember { mutableStateOf(false) }

            // Save as JSON (no backend needed)
            val saveJsonLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                uri?.let {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(it)?.use { out ->
                                out.write(LIQUID_GLASS_RC_JSON.toByteArray(Charsets.UTF_8))
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "已保存为 JSON 文件", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            // Save as .rc (requires backend compiler)
            val saveRcLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                uri?.let {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            isCompiling = true
                            val rcBytes = RcCompiler.compileJsonToRc(LIQUID_GLASS_RC_JSON)
                            if (rcBytes != null && rcBytes.isNotEmpty()) {
                                context.contentResolver.openOutputStream(it)?.use { out ->
                                    out.write(rcBytes)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "已保存到 .rc 文件 (${rcBytes.size} bytes)", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "编译失败 — 请确认后端编译服务器 (10.0.2.2:8080) 是否已启动", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            isCompiling = false
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Liquid Glass UI 预览",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        Button(
                            onClick = { saveJsonLauncher.launch("liquid_glass.json") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) { Text("📋 保存为 JSON", fontSize = 11.sp) }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { saveRcLauncher.launch("liquid_glass.rc") },
                            enabled = !isCompiling,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Text(if (isCompiling) "编译中..." else "💾 保存为 .rc", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { navController.popBackStack() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) { Text("← 返回", fontSize = 12.sp) }
                    }
                }
                Box(modifier = Modifier.weight(1f)) { LiquidGlassMusicPlayer() }
            }
        }
        composable("a2ui_renderer") {
            val a2uiJsonl = sharedViewModel.a2uiJsonl
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar("A2UI 原生 UI 预览") { navController.popBackStack() }
                if (a2uiJsonl != null) {
                    A2uiPreviewHost(jsonl = a2uiJsonl, modifier = Modifier.weight(1f))
                } else {
                    EmptyState("No A2UI document loaded")
                }
            }
        }
        composable("tester") {
            RemoteComposeTesterScreen(
                rcBytes = sharedViewModel.generatedRcBytes,
                onBack = { navController.popBackStack() }
            )
        }
        composable("json_renderer") {
            val doc = sharedViewModel.jsonUiDocument
            if (doc != null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopBar("JSON UI 预览") { navController.popBackStack() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) { JsonUiRenderer(document = doc) }
                }
            } else { EmptyState("No JSON document loaded") }
        }
        composable("cmw_renderer") {
            val cmwPayload = sharedViewModel.cmwJsonPayload
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar("CMW 自定义 UI 预览") { navController.popBackStack() }
                if (cmwPayload != null) {
                    val cmwJson = remember(cmwPayload) {
                        runCatching { org.json.JSONObject(cmwPayload) }.getOrNull()
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (cmwJson != null) CMWWidgetHost(cmwJson)
                        else Text("JSON 解析错误", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                } else { EmptyState("No CMW document loaded") }
            }
        }
    }
}

// ── Tab 2: Standalone RC Viewer ───────────────────────────────────────────────

@Composable
fun RcViewerTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var documentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var jsonDocument by remember { mutableStateOf<JsonUiDocument?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var eventLogs by remember { mutableStateOf(listOf<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            fileName = queryFileName(it)
            isLoading = true
            errorMessage = null
            jsonDocument = null
            documentBytes = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(it)?.use { input ->
                        input.readBytes()
                    } ?: throw IllegalStateException("无法打开文件流")

                    // Auto-detect: if content starts with '{' it's JSON, otherwise binary RC
                    val head = bytes.take(100).map { it.toInt().toChar() }.joinToString("").trimStart()
                    if (head.startsWith("{")) {
                        val jsonStr = String(bytes, Charsets.UTF_8)
                        val doc = parseJsonUiDocument(jsonStr)
                        withContext(Dispatchers.Main) {
                            jsonDocument = doc
                            isLoading = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            documentBytes = bytes
                            isLoading = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "加载失败: ${e.message}"
                        isLoading = false
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "RC 文档查看器",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "支持 .rc 二进制文件和 .json SDUI 文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "加载中..." else "📂 选择文件")
            }
            fileName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Render Area ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            val jsonDoc = jsonDocument
            val bytes = documentBytes

            when {
                jsonDoc != null -> {
                    // JSON SDUI rendering
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        JsonUiRenderer(document = jsonDoc)
                    }
                }
                bytes != null -> {
                    // Binary RC rendering
                    AndroidView(
                        factory = { ctx ->
                            val playerClass = Class.forName(
                                "androidx.compose.remote.player.view.RemoteComposePlayer"
                            )
                            val constructor = playerClass.getConstructor(android.content.Context::class.java)
                            val player = constructor.newInstance(ctx) as FrameLayout

                            try {
                                val mInnerField = playerClass.getDeclaredField("mInner")
                                mInnerField.isAccessible = true
                                val innerView = mInnerField.get(player)

                                val addListenerMethod = innerView.javaClass.getMethod(
                                    "addIdActionListener",
                                    Class.forName("androidx.compose.remote.player.view.RemoteComposeView\$ClickCallbacks")
                                )

                                val callbackClass = Class.forName(
                                    "androidx.compose.remote.player.view.RemoteComposeView\$ClickCallbacks"
                                )
                                val callback = java.lang.reflect.Proxy.newProxyInstance(
                                    callbackClass.classLoader,
                                    arrayOf(callbackClass)
                                ) { _, method, args ->
                                    if (method.name == "click") {
                                        val id = args[0] as Int
                                        val metadata = args[1] as String
                                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                        eventLogs = eventLogs + "[$timestamp] 点击 → ID: $id, \"$metadata\""
                                        if (metadata.startsWith("appfn:")) {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                AppFunctionManager.executeFunction(context, metadata)
                                            }
                                        }
                                    }
                                    null
                                }
                                addListenerMethod.invoke(innerView, callback)
                            } catch (e: Exception) {
                                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                eventLogs = eventLogs + "[$timestamp] 监听器设置失败: ${e.message}"
                            }

                            player.layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            player
                        },
                        update = { player ->
                            try {
                                val playerClass = player.javaClass
                                val setDocumentMethod = playerClass.getMethod("setDocument", ByteArray::class.java)
                                val lastHash = player.getTag()
                                val currentHash = bytes.contentHashCode()
                                if (lastHash != currentHash) {
                                    setDocumentMethod.invoke(player, bytes)
                                    player.setTag(currentHash)
                                }
                            } catch (e: Exception) {
                                errorMessage = "RC 文档渲染失败: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Text(
                        text = "请选择一个 .rc 或 .json 文件以预览",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Event Log ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "事件日志", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (eventLogs.isNotEmpty()) {
                Button(
                    onClick = { eventLogs = emptyList() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    content = { Text("清空", fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            if (eventLogs.isEmpty()) {
                item {
                    Text(
                        text = "暂无事件。加载文件并交互以查看日志。",
                        color = Color(0xFF888888),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                items(eventLogs) { log ->
                    Text(
                        text = log,
                        color = Color(0xFF00FF00),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ── Reusable Components ───────────────────────────────────────────────────────

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) { Text("← 返回", fontSize = 12.sp) }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color.Gray)
    }
}

// ── Remote Compose Tester (used from Generator tab) ───────────────────────────

@Composable
fun RemoteComposeTesterScreen(
    rcBytes: ByteArray? = null,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var documentBytes by remember { mutableStateOf<ByteArray?>(rcBytes) }
    LaunchedEffect(rcBytes) {
        if (rcBytes != null && documentBytes == null) {
            documentBytes = rcBytes
        }
    }
    var fileName by remember { mutableStateOf<String?>(null) }
    var eventLogs by remember { mutableStateOf(listOf<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            fileName = queryFileName(it)
            isLoading = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(it)?.use { input ->
                        input.readBytes()
                    } ?: throw IllegalStateException("无法打开文件流")
                    withContext(Dispatchers.Main) {
                        documentBytes = bytes
                        isLoading = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "读取文件失败: ${e.message}"
                        isLoading = false
                    }
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    documentBytes?.let { bytes ->
                        context.contentResolver.openOutputStream(it)?.use { out ->
                            out.write(bytes)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "RC 文档测试台", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row {
                if (documentBytes != null) {
                    Button(
                        onClick = { saveLauncher.launch("generated_ui.rc") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { Text("💾 保存到文件", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                onBack?.let {
                    Button(onClick = it, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Text("← 返回", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { filePickerLauncher.launch("*/*") }, enabled = !isLoading) { Text(if (isLoading) "加载中..." else "选择并加载 .rc 文件") }
            fileName?.let { name -> Text(text = name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
            if (rcBytes != null && documentBytes == null) { Text(text = "(AI 生成的文档已加载)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            val bytes = documentBytes
            if (bytes != null) {
                AndroidView(
                    factory = { ctx ->
                        val playerClass = Class.forName("androidx.compose.remote.player.view.RemoteComposePlayer")
                        val constructor = playerClass.getConstructor(android.content.Context::class.java)
                        val player = constructor.newInstance(ctx) as FrameLayout
                        try {
                            val mInnerField = playerClass.getDeclaredField("mInner")
                            mInnerField.isAccessible = true
                            val innerView = mInnerField.get(player)
                            val addListenerMethod = innerView.javaClass.getMethod("addIdActionListener", Class.forName("androidx.compose.remote.player.view.RemoteComposeView\$ClickCallbacks"))
                            val callbackClass = Class.forName("androidx.compose.remote.player.view.RemoteComposeView\$ClickCallbacks")
                            val callback = java.lang.reflect.Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { _, method, args ->
                                if (method.name == "click") {
                                    val id = args[0] as Int
                                    val metadata = args[1] as String
                                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                    eventLogs = eventLogs + "[$timestamp] 点击事件 → 组件ID: $id, 元数据: \"$metadata\""
                                    if (metadata.startsWith("appfn:")) {
                                        coroutineScope.launch(Dispatchers.Main) { AppFunctionManager.executeFunction(context, metadata) }
                                    }
                                }
                                null
                            }
                            addListenerMethod.invoke(innerView, callback)
                        } catch (e: Exception) {
                            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                            eventLogs = eventLogs + "[$timestamp] 监听器设置失败: ${e.message}"
                        }
                        player.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        player
                    },
                    update = { player ->
                        try {
                            val playerClass = player.javaClass
                            val setDocumentMethod = playerClass.getMethod("setDocument", ByteArray::class.java)
                            val lastHash = player.getTag()
                            val currentHash = bytes.contentHashCode()
                            if (lastHash != currentHash) { setDocumentMethod.invoke(player, bytes); player.setTag(currentHash) }
                        } catch (e: Exception) {
                            errorMessage = "RC 文档渲染失败: ${e.message}"
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(text = "请选择一个 .rc 文件以预览", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "事件日志", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (eventLogs.isNotEmpty()) {
                Button(onClick = { eventLogs = emptyList() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("清空日志", fontSize = 12.sp) }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)).padding(8.dp)) {
            if (eventLogs.isEmpty()) {
                item { Text(text = "暂无事件。加载 .rc 文件并与其交互以查看事件日志。", color = Color(0xFF888888), fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            } else {
                items(eventLogs) { log -> Text(text = log, color = Color(0xFF00FF00), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}

// ── A2UI Preview Host ─────────────────────────────────────────────────────────

@Composable
fun A2uiPreviewHost(jsonl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val renderer = rememberA2UIRenderer(
        config = A2UIConfig(
            onAction = { actionMessage ->
                val eventName = actionMessage.action.name
                if (eventName.startsWith("appfn:")) {
                    AppFunctionManager.executeFunction(context, eventName)
                } else {
                    android.widget.Toast.makeText(context, "Action: $eventName", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onOpenUrl = { url ->
                if (url.startsWith("appfn:")) {
                    AppFunctionManager.executeFunction(context, url)
                } else {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "无法打开链接: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    )

    LaunchedEffect(jsonl) {
        renderer.clear()
        renderer.processJsonLines(jsonl)
    }

    A2UIRenderer(renderer, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp))
}

private fun queryFileName(uri: Uri): String? = uri.lastPathSegment ?: uri.toString()
