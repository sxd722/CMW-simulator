package com.example.cmw_simulator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
