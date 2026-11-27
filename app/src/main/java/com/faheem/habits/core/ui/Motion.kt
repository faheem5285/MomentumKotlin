package com.faheem.habits.core.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Every animated component uses this helper so reduced-effects mode actually
 * disables motion instead of merely shortening it.
 */
fun <T> animationSpecFor(
    reduceEffects: Boolean,
    durationMillis: Int = MomentumTokens.motionMedium,
    easing: Easing = FastOutSlowInEasing
): AnimationSpec<T> = if (reduceEffects) snap() else tween(durationMillis, easing = easing)

@Composable
fun <T> rememberAnimationSpecFor(
    reduceEffects: Boolean,
    durationMillis: Int = MomentumTokens.motionMedium,
    easing: Easing = FastOutSlowInEasing
): AnimationSpec<T> = remember(reduceEffects, durationMillis, easing) {
    animationSpecFor<T>(reduceEffects, durationMillis, easing)
}
