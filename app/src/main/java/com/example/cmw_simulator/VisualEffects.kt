package com.example.cmw_simulator

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    val id: Int,
    val x: Float,
    val y: Float,
    val angle: Double,
    val speed: Float,
    val color: Color,
    val size: Float
)

/**
 * A lightweight confetti/particle burst effect.
 * When [triggerEvent] changes, a burst of colorful particles sprays from [clickOffset].
 */
@Composable
fun ClickParticleEffect(
    triggerEvent: Long,
    clickOffset: Offset,
    modifier: Modifier = Modifier
) {
    if (triggerEvent == 0L) return

    val density = LocalDensity.current

    val particles = remember(triggerEvent) {
        List(25) { index ->
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val speed = Random.nextFloat() * 15f + 5f
            val size = Random.nextFloat() * 8f + 6f
            val color = Color(
                red = Random.nextFloat(),
                green = Random.nextFloat(),
                blue = Random.nextFloat(),
                alpha = 1.0f
            )
            Particle(index, clickOffset.x, clickOffset.y, angle, speed, color, size)
        }
    }

    val progress = remember(triggerEvent) { Animatable(0f) }

    LaunchedEffect(triggerEvent) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = LinearEasing)
        )
    }

    if (progress.value < 1f) {
        Canvas(modifier = modifier) {
            particles.forEach { particle ->
                val currentDistance = particle.speed * progress.value * 15f
                val currentX = particle.x + (cos(particle.angle) * currentDistance).toFloat()
                val currentY = particle.y + (sin(particle.angle) * currentDistance).toFloat() + (progress.value * 40f)
                val alpha = 1f - progress.value

                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = particle.size * (1f - progress.value * 0.5f),
                    center = Offset(currentX, currentY)
                )
            }
        }
    }
}

// ── Liquid Glass Music Player ──────────────────────────────────────────────

@Composable
fun LiquidGlassMusicPlayer() {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.3f) }
    var isLiked by remember { mutableStateOf(false) }

    // Simulate playback progress
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(500)
            progress = if (progress >= 1f) 0f else progress + 0.003f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A14)),
        contentAlignment = Alignment.Center
    ) {
        // 1. Liquid Gooey Background Animation
        GooeyBackground()

        // 2. Dark Overlay for Contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        // 3. Glassmorphism Card
        GlassCard(
            isPlaying = isPlaying,
            progress = progress,
            isLiked = isLiked,
            onPlayPauseClick = { isPlaying = !isPlaying },
            onLikeClick = { isLiked = !isLiked },
            onSeek = { progress = it }
        )
    }
}

@Composable
fun GooeyBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "blob")
    
    // Blob animation states
    val offsetY1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -150f,
        animationSpec = infiniteRepeatable(tween(10000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = ""
    )
    val offsetX2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearOutSlowInEasing), RepeatMode.Reverse), label = ""
    )

    // Android's equivalent to the SVG feColorMatrix + feGaussianBlur
    val gooeyEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP)
            val matrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 20f, -2550f // Alpha threshold math
                )
            )
            val colorFilter = RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(matrix.values))
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
        // Indigo Blob
        Box(
            modifier = Modifier
                .offset { IntOffset(0, offsetY1.dp.roundToPx()) }
                .size(400.dp)
                .background(Color(0xFF6366F1), CircleShape)
        )
        // Cyan Blob
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX2.dp.roundToPx(), (-offsetY1).dp.roundToPx()) }
                .size(350.dp)
                .background(Color(0xFF22D3EE), CircleShape)
        )
        // Fuchsia Blob
        Box(
            modifier = Modifier
                .offset { IntOffset((-offsetX2).dp.roundToPx(), 0) }
                .size(300.dp)
                .background(Color(0xFFD946EF), CircleShape)
        )
    }
}

@Composable
fun GlassCard(
    isPlaying: Boolean,
    progress: Float,
    isLiked: Boolean,
    onPlayPauseClick: () -> Unit,
    onLikeClick: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .width(360.dp)
            .height(580.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Blurred Background Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(40.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(40.dp))
                .blur(30.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        )

        // 2. Content Layer (Not Blurred)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NOW PLAYING", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onLikeClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFEC4899) else Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Album Art
            AlbumArt(isPlaying)

            Spacer(modifier = Modifier.height(32.dp))

            // Track Info
            Text("Nebula Drift", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Synthwave Explorers", color = Color(0xFFBAE6FD).copy(alpha = 0.8f), fontSize = 14.sp)

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Bar
            ProgressBar(progress, onSeek)

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            PlayerControls(isPlaying, onPlayPauseClick)
        }
    }
}

@Composable
fun AlbumArt(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "spinAnim"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .graphicsLayer {
                // Pause rotation if not playing
                rotationZ = if (isPlaying) rotation else 0f
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer Border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clip(CircleShape)
        ) {
            // Placeholder for Album Art Image (Requires Coil or similar in real app)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF22D3EE), Color(0xFFD946EF))
                        )
                    )
            )
        }
        
        // Vinyl Center Hole
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF0A0A14), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
        )
    }
}

@Composable
fun ProgressBar(progress: Float, onSeek: (Float) -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                .clip(CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF22D3EE), Color(0xFF6366F1), Color(0xFFD946EF))
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val totalSeconds = 240f
            val currentSeconds = progress * totalSeconds
            Text(formatTime(currentSeconds), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Text(formatTime(totalSeconds), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
fun PlayerControls(isPlaying: Boolean, onPlayPauseClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .clickable { onPlayPauseClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Icon(Icons.Filled.SkipNext, "Next", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
    }
}

fun formatTime(seconds: Float): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return "${mins}:${if (secs < 10) "0" else ""}$secs"
}
