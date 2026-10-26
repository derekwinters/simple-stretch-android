package com.derekwinters.stretch.store

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * STORE-SHOT-010 to STORE-SHOT-014: the store shot's geometry, in pixels of the final image.
 * Composed at the device's density (3), where one dp is three of these pixels.
 */
object StoreShotSpec {
    const val WIDTH_PX = 1080
    const val HEIGHT_PX = 2340

    /** STORE-SHOT-011: the one background colour, light and dark alike. */
    val BACKGROUND = Color(0xFF1361A7)

    // STORE-SHOT-012: the caption.
    const val CAPTION_TOP_PX = 108
    const val CAPTION_SIDE_PX = 79
    const val CAPTION_WIDTH_PX = WIDTH_PX - 2 * CAPTION_SIDE_PX
    val HEADLINE_COLOR = Color.White
    const val HEADLINE_SIZE_PX = 90f
    const val HEADLINE_LINE_HEIGHT = 1.18f
    const val HEADLINE_TO_SUPPORTING_PX = 22
    val SUPPORTING_COLOR = Color(0xFFCFE1F5)
    const val SUPPORTING_SIZE_PX = 50f
    const val SUPPORTING_LINE_HEIGHT = 1.35f

    // STORE-SHOT-013: the phone frame.
    const val FRAME_TOP_PX = 650
    const val FRAME_WIDTH_PX = 922
    const val BEZEL_PX = 18
    const val FRAME_CORNER_PX = 79
    val BEZEL_COLOR = Color(0xFF0A0A0B)

    /** The screen's own top corners follow the frame's, inset by the bezel. */
    const val SCREEN_CORNER_PX = FRAME_CORNER_PX - BEZEL_PX

    /** STORE-SHOT-013: the app screen is laid out at the device's natural size, then scaled. */
    val SCREEN_WIDTH: Dp = 411.dp
    val SCREEN_HEIGHT: Dp = 891.dp

    /** Test tag of the whole shot; its bounds are the captured image. */
    const val TAG = "store-shot"

    /** STORE-SHOT-012: the headline's style at [density] (pixels to sp through the density). */
    fun headlineStyle(density: Density): TextStyle = with(density) {
        TextStyle(
            color = HEADLINE_COLOR,
            fontSize = HEADLINE_SIZE_PX.toSp(),
            fontWeight = FontWeight.Bold,
            lineHeight = (HEADLINE_SIZE_PX * HEADLINE_LINE_HEIGHT).toSp(),
            lineHeightStyle = LINE_HEIGHT_STYLE,
            textAlign = TextAlign.Start,
        )
    }

    /** STORE-SHOT-012: the supporting line's style at [density]. */
    fun supportingStyle(density: Density): TextStyle = with(density) {
        TextStyle(
            color = SUPPORTING_COLOR,
            fontSize = SUPPORTING_SIZE_PX.toSp(),
            fontWeight = FontWeight.Normal,
            lineHeight = (SUPPORTING_SIZE_PX * SUPPORTING_LINE_HEIGHT).toSp(),
            lineHeightStyle = LINE_HEIGHT_STYLE,
            textAlign = TextAlign.Start,
        )
    }

    /** Each line is exactly its line height, the text centred in it, nothing trimmed. */
    private val LINE_HEIGHT_STYLE = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
}

/**
 * STORE-SHOT-012 / STORE-SHOT-014: a caption's line counts as the shot renders it: the same
 * styles, at the caption's width, laid out by the platform's text engine at the device density.
 */
object CaptionMeasure {
    fun lines(context: Context, caption: Caption): CaptionLines {
        val density = Density(context)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val width = Constraints(maxWidth = StoreShotSpec.CAPTION_WIDTH_PX)
        fun count(text: String, style: TextStyle) =
            measurer.measure(text = text, style = style, constraints = width).lineCount
        return CaptionLines(
            headline = count(caption.headline, StoreShotSpec.headlineStyle(density)),
            supporting = count(caption.supporting, StoreShotSpec.supportingStyle(density)),
        )
    }
}

/**
 * STORE-SHOT-010: one 1080 x 2340 shot: the solid background (STORE-SHOT-011), the caption
 * (STORE-SHOT-012) and the framed phone screen, cut off by the bottom edge (STORE-SHOT-013).
 * [screen] is the app screen, already in its theme; nothing else here is themed, so the
 * background and caption are the same in light and dark shots (STORE-SHOT-011 invariant).
 * There is no status or navigation bar (STORE-SHOT-014).
 */
@Composable
fun StoreShotFrame(caption: Caption, screen: @Composable () -> Unit) {
    val density = LocalDensity.current
    fun px(value: Int): Dp = with(density) { value.toDp() }

    Box(
        Modifier
            .requiredSize(px(StoreShotSpec.WIDTH_PX), px(StoreShotSpec.HEIGHT_PX))
            .background(StoreShotSpec.BACKGROUND)
            .testTag(StoreShotSpec.TAG),
    ) {
        Column(
            Modifier
                .offset(x = px(StoreShotSpec.CAPTION_SIDE_PX), y = px(StoreShotSpec.CAPTION_TOP_PX))
                .width(px(StoreShotSpec.CAPTION_WIDTH_PX)),
        ) {
            // No maxLines: an over-long caption is caught by CaptionFit, never cut or shrunk.
            Text(caption.headline, style = StoreShotSpec.headlineStyle(density))
            Spacer(Modifier.height(px(StoreShotSpec.HEADLINE_TO_SUPPORTING_PX)))
            Text(caption.supporting, style = StoreShotSpec.supportingStyle(density))
        }

        val frameShape = RoundedCornerShape(
            topStart = px(StoreShotSpec.FRAME_CORNER_PX),
            topEnd = px(StoreShotSpec.FRAME_CORNER_PX),
            bottomEnd = 0.dp,
            bottomStart = 0.dp,
        )
        val screenShape = RoundedCornerShape(
            topStart = px(StoreShotSpec.SCREEN_CORNER_PX),
            topEnd = px(StoreShotSpec.SCREEN_CORNER_PX),
            bottomEnd = 0.dp,
            bottomStart = 0.dp,
        )
        Box(
            Modifier
                .offset(
                    x = px((StoreShotSpec.WIDTH_PX - StoreShotSpec.FRAME_WIDTH_PX) / 2),
                    y = px(StoreShotSpec.FRAME_TOP_PX),
                )
                .width(px(StoreShotSpec.FRAME_WIDTH_PX))
                .height(px(StoreShotSpec.HEIGHT_PX - StoreShotSpec.FRAME_TOP_PX))
                .clip(frameShape)
                .background(StoreShotSpec.BEZEL_COLOR)
                // No bottom bezel: the frame runs off the bottom edge.
                .padding(
                    start = px(StoreShotSpec.BEZEL_PX),
                    top = px(StoreShotSpec.BEZEL_PX),
                    end = px(StoreShotSpec.BEZEL_PX),
                ),
        ) {
            ScaledScreen(Modifier.fillMaxSize().clip(screenShape), screen)
        }
    }
}

/**
 * STORE-SHOT-013: lays [content] out at the device's natural screen size and draws it scaled
 * down to this box's width, top-left anchored, so it looks like the real screen shrunk rather
 * than a narrower layout. Whatever falls below this box is cut off by the clip around it.
 */
@Composable
private fun ScaledScreen(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = { Box(propagateMinConstraints = true) { content() } }, modifier = modifier) { measurables, constraints ->
        val naturalWidth = StoreShotSpec.SCREEN_WIDTH.roundToPx()
        val naturalHeight = StoreShotSpec.SCREEN_HEIGHT.roundToPx()
        val placeable = measurables.single().measure(Constraints.fixed(naturalWidth, naturalHeight))
        val scale = constraints.maxWidth.toFloat() / naturalWidth
        val height = (naturalHeight * scale).roundToInt().coerceAtMost(constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
