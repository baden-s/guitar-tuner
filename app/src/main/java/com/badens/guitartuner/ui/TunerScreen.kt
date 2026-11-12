package com.badens.guitartuner.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.badens.guitartuner.R
import com.badens.guitartuner.audio.AudioSourceType
import com.badens.guitartuner.tuning.GuitarString
import com.badens.guitartuner.tuning.TunerConfig
import com.badens.guitartuner.tuning.TunerState
import com.badens.guitartuner.tuning.TuningStatus
import com.badens.guitartuner.ui.theme.GuitarTunerTheme
import com.badens.guitartuner.ui.theme.TunerColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Thresholds = TunerConfig()

/** The tuner screen wired to its ViewModel. Listens only while the screen is visible. */
@Composable
fun TunerRoute(viewModel: TunerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    KeepScreenOn()
    InTuneHaptics(state.tuner)

    TunerScreen(state = state, onStringTap = viewModel::onStringTapped)
}

@Composable
fun TunerScreen(state: TunerUiState, onStringTap: (GuitarString) -> Unit) {
    val tuner = state.tuner
    val listening = tuner.status != TuningStatus.IDLE && tuner.activeString != null

    val ring by animateColorAsState(ringColor(tuner, listening), label = "ring")
    val bubbleX by animateFloatAsState(
        targetValue = if (listening) Headstock.bubbleX(tuner.cents) else Headstock.CENTER_X,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "bubbleX",
    )
    val contentAlpha by animateFloatAsState(if (listening) 1f else 0f, tween(250), label = "bubbleAlpha")
    val arrow = when {
        !listening -> 0
        tuner.cents < -Thresholds.closeCents -> 1
        tuner.cents > Thresholds.closeCents -> -1
        else -> 0
    }

    // Keep showing the last note while the bubble fades out after silence.
    val lastShown = remember { mutableStateOf<TunerState?>(null) }
    if (listening) lastShown.value = tuner
    val shown = lastShown.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TunerColors.Background)
            .drawBehind { drawGrid(spacing = 24.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusLine(state, ring)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val unit = minOf(maxWidth / Headstock.WIDTH, maxHeight / Headstock.HEIGHT)
            // The animated bubble position is read only in layout and draw, so animating it
            // doesn't recompose the screen on every frame.
            HeadstockArt(
                activeString = tuner.activeString.takeIf { listening },
                highlight = ring,
                bubbleX = { bubbleX },
                arrow = arrow,
                unit = unit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.size(unit * Headstock.WIDTH, unit * Headstock.HEIGHT)) {
                val r = Headstock.BUBBLE_RADIUS
                Bubble(
                    note = shown?.activeString?.noteName,
                    cents = shown?.let { formatCents(it.cents) },
                    ringColor = ring,
                    filled = listening && tuner.status == TuningStatus.IN_TUNE,
                    contentAlpha = contentAlpha,
                    modifier = Modifier
                        .absoluteOffset {
                            val px = unit.toPx()
                            IntOffset(((bubbleX - r) * px).roundToInt(), ((Headstock.TRACK_Y - r) * px).roundToInt())
                        }
                        .size(unit * (2 * r)),
                )
                val br = Headstock.BUTTON_RADIUS
                for ((string, spot) in Headstock.spots) {
                    StringButton(
                        string = string,
                        active = listening && tuner.activeString == string,
                        tuned = string in tuner.tunedStrings,
                        locked = state.lockedString == string,
                        onClick = { onStringTap(string) },
                        modifier = Modifier
                            .absoluteOffset(x = unit * (spot.buttonX - br), y = unit * (spot.y - br))
                            .size(unit * (2 * br)),
                    )
                }
            }
        }
        DebugLine(state)
    }
}

@Composable
private fun StatusLine(state: TunerUiState, ring: Color) {
    val tuner = state.tuner
    val locked = state.lockedString
    val (text, color) = when {
        state.micError -> stringResource(R.string.status_mic_error) to TunerColors.Off
        tuner.status == TuningStatus.IDLE && locked != null ->
            stringResource(R.string.status_play_locked, stringResource(locked.labelRes)) to TunerColors.TextSecondary
        tuner.status == TuningStatus.IDLE -> stringResource(R.string.status_idle) to TunerColors.TextSecondary
        tuner.status == TuningStatus.FLAT -> stringResource(R.string.status_tune_up) to ring
        tuner.status == TuningStatus.SHARP -> stringResource(R.string.status_tune_down) to ring
        tuner.status == TuningStatus.CENTERED -> stringResource(R.string.status_hold) to TunerColors.InTune
        else -> stringResource(R.string.status_in_tune) to TunerColors.InTune
    }
    Text(
        text = text,
        color = color,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(
        text = locked?.let { stringResource(R.string.locked_hint, it.noteName) }.orEmpty(),
        color = TunerColors.Lock,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Bubble(
    note: String?,
    cents: String?,
    ringColor: Color,
    filled: Boolean,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(if (filled) TunerColors.InTune else TunerColors.BubbleFill, label = "bubbleFill")
    val ink = if (filled) Color.Black else TunerColors.TextPrimary
    val description = if (note != null && cents != null && contentAlpha > 0f) {
        stringResource(R.string.bubble_description, note, cents)
    } else {
        ""
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .border(3.dp, ringColor, CircleShape)
            .clearAndSetSemantics { if (description.isNotEmpty()) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.alpha(contentAlpha), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(note.orEmpty(), color = ink, fontSize = 26.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
            Text(cents.orEmpty(), color = ink.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun StringButton(
    string: GuitarString,
    active: Boolean,
    tuned: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(string.labelRes)
    val tunedText = stringResource(R.string.state_tuned)
    val lockedText = stringResource(R.string.state_locked)
    val actionLabel = stringResource(if (locked) R.string.action_unlock else R.string.action_lock)
    val stateText = listOfNotNull(tunedText.takeIf { tuned }, lockedText.takeIf { locked }).joinToString()
    val background by animateColorAsState(
        if (active) TunerColors.ButtonActive else TunerColors.ButtonIdle,
        label = "buttonBackground",
    )
    val borderModifier = when {
        locked -> Modifier.border(2.5.dp, TunerColors.Lock, CircleShape)
        active -> Modifier.border(2.dp, TunerColors.TextPrimary.copy(alpha = 0.85f), CircleShape)
        else -> Modifier
    }

    Box(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = name
            if (stateText.isNotEmpty()) stateDescription = stateText
            role = Role.Button
            onClick(label = actionLabel) {
                onClick()
                true
            }
        },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(background)
                .then(borderModifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(string.noteName, color = TunerColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        if (tuned) {
            TunedBadge(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxSize(0.38f),
            )
        }
    }
}

@Composable
private fun TunedBadge(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(TunerColors.InTune)
        val w = size.width
        val check = Path().apply {
            moveTo(w * 0.28f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.68f)
            lineTo(w * 0.73f, w * 0.36f)
        }
        drawPath(
            check,
            Color.Black,
            style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Small readout for checking mic levels and the audio source on a real device. */
@Composable
private fun DebugLine(state: TunerUiState) {
    val tuner = state.tuner
    val text = buildString {
        append(tuner.frequencyHz?.let { String.format(Locale.US, "%.2f Hz", it) } ?: "— Hz")
        append(String.format(Locale.US, " · %.0f dB", tuner.levelDb))
        state.audioSource?.let { append(" · ").append(it.label) }
    }
    Text(text, color = TunerColors.TextMuted, fontSize = 12.sp)
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** A short vibration each time a string reaches in-tune. */
@Composable
private fun InTuneHaptics(tuner: TunerState) {
    val view = LocalView.current
    val inTuneString = tuner.activeString.takeIf { tuner.status == TuningStatus.IN_TUNE }
    LaunchedEffect(inTuneString) {
        if (inTuneString != null) {
            val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(feedback)
        }
    }
}

private fun ringColor(tuner: TunerState, listening: Boolean): Color = when {
    !listening -> TunerColors.Neutral
    abs(tuner.cents) <= Thresholds.inTuneCents -> TunerColors.InTune
    abs(tuner.cents) <= Thresholds.closeCents -> TunerColors.Close
    else -> TunerColors.Off
}

/** Whole cents with an explicit sign, using a true minus sign. */
internal fun formatCents(cents: Double): String {
    val rounded = cents.roundToInt()
    return when {
        rounded > 0 -> "+$rounded"
        rounded < 0 -> "−${-rounded}"
        else -> "0"
    }
}

@get:StringRes
private val GuitarString.labelRes: Int
    get() = when (this) {
        GuitarString.LOW_E -> R.string.string_low_e
        GuitarString.A -> R.string.string_a
        GuitarString.D -> R.string.string_d
        GuitarString.G -> R.string.string_g
        GuitarString.B -> R.string.string_b
        GuitarString.HIGH_E -> R.string.string_high_e
    }

private fun DrawScope.drawGrid(spacing: Float) {
    var x = 0f
    while (x < size.width) {
        drawLine(TunerColors.Grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += spacing
    }
    var y = 0f
    while (y < size.height) {
        drawLine(TunerColors.Grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += spacing
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 860)
@Composable
private fun TunerScreenSharpPreview() {
    GuitarTunerTheme {
        TunerScreen(
            state = TunerUiState(
                tuner = TunerState(
                    activeString = GuitarString.A,
                    cents = 12.0,
                    frequencyHz = 110.77,
                    status = TuningStatus.SHARP,
                    tunedStrings = setOf(GuitarString.LOW_E, GuitarString.D),
                    levelDb = -31.0,
                ),
                audioSource = AudioSourceType.UNPROCESSED,
            ),
            onStringTap = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 860)
@Composable
private fun TunerScreenIdlePreview() {
    GuitarTunerTheme {
        TunerScreen(state = TunerUiState(), onStringTap = {})
    }
}
