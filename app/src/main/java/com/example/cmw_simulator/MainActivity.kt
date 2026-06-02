package com.example.cmw_simulator

import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@Composable
fun AppNavigation(sharedViewModel: SharedRcViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "generator") {
        composable("generator") {
            GeneratorScreen(
                viewModel = sharedViewModel,
                onGenerationSuccess = {
                    navController.navigate("tester")
                },
                onJsonRender = {
                    navController.navigate("json_renderer")
                }
            )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "JSON UI 预览",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = { navController.popBackStack() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("← 返回", fontSize = 12.sp)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        JsonUiRenderer(document = doc)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No JSON document loaded", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun RemoteComposeTesterScreen(
    rcBytes: ByteArray? = null,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var documentBytes by remember { mutableStateOf<ByteArray?>(rcBytes) }
    // Sync documentBytes when rcBytes changes (e.g., returning from generator with new data)
    LaunchedEffect(rcBytes) {
        if (rcBytes != null && documentBytes == null) {
            documentBytes = rcBytes
        }
    }
    var fileName by remember { mutableStateOf<String?>(null) }
    var eventLogs by remember { mutableStateOf(listOf<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // File picker launcher
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ── Top Controls Area ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RC 文档测试台",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row {
                onBack?.let {
                    Button(
                        onClick = it,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        content = { Text("← 返回", fontSize = 12.sp) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

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
                Text(if (isLoading) "加载中..." else "选择并加载 .rc 文件")
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

            if (rcBytes != null && documentBytes == null) {
                Text(
                    text = "(AI 生成的文档已加载)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Render Area (Core) ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bytes = documentBytes
            if (bytes != null) {
                // Use reflection to bypass @RestrictTo annotations on RemoteComposePlayer
                AndroidView(
                    factory = { ctx ->
                        // Create RemoteComposePlayer via reflection
                        val playerClass = Class.forName(
                            "androidx.compose.remote.player.view.RemoteComposePlayer"
                        )
                        val constructor = playerClass.getConstructor(android.content.Context::class.java)
                        val player = constructor.newInstance(ctx) as FrameLayout

                        // Don't set document in factory — the view has no surface yet.
                        // The update block will set it once the view is attached.

                        // Access mInner field via reflection and add click listener
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
                                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                        .format(Date())
                                    val log = "[$timestamp] 点击事件 → 组件ID: $id, 元数据: \"$metadata\""
                                    eventLogs = eventLogs + log

                                    // 拦截并执行 AppFunctions
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
                            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date())
                            eventLogs = eventLogs + "[$timestamp] 监听器设置失败: ${e.message}"
                        }

                        player.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        player
                    },
                    update = { player ->
                        // Only set document when bytes actually change to avoid resetting the player
                        try {
                            val playerClass = player.javaClass
                            val setDocumentMethod = playerClass.getMethod("setDocument", ByteArray::class.java)
                            // Use getTag to track last-set bytes and avoid redundant calls
                            val lastHash = player.getTag()
                            val currentHash = bytes.contentHashCode()
                            if (lastHash != currentHash) {
                                setDocumentMethod.invoke(player, bytes)
                                player.setTag(currentHash)
                            }
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "请选择一个 .rc 文件以预览",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Event Log Area ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "事件日志",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            if (eventLogs.isNotEmpty()) {
                Button(
                    onClick = { eventLogs = emptyList() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    content = { Text("清空日志", fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            if (eventLogs.isEmpty()) {
                item {
                    Text(
                        text = "暂无事件。加载 .rc 文件并与其交互以查看事件日志。",
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

/**
 * Query the content resolver for a human-readable file name from a Uri.
 */
private fun queryFileName(uri: Uri): String? {
    return uri.lastPathSegment ?: uri.toString()
}
