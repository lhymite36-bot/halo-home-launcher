package com.pedro.halo

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberIcon(app: AppEntry?, spec: IconSpec, version: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val v by produceState<ImageBitmap?>(null, app?.key, spec, version) {
        value = if (app == null) null else withContext(Dispatchers.Default) {
            runCatching { IconCache.get(ctx, app, spec) }.getOrNull()
        }
    }
    return v
}

fun labelStyle(color: Color, onWallpaper: Boolean, sizeSp: Float = 12f) = TextStyle(
    color = color,
    fontSize = sizeSp.sp,
    textAlign = TextAlign.Center,
    shadow = if (onWallpaper && color.luminanceSimple() > 0.5f) Shadow(Color(0x88000000), blurRadius = 6f) else null,
)

fun Color.luminanceSimple(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Pressable icon with One UI-like spring "squish". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconCell(
    modifier: Modifier = Modifier,
    iconSize: Dp,
    label: String?,
    labelColor: Color,
    onWallpaper: Boolean,
    haptics: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "press")
    val hf = LocalHapticFeedback.current
    Column(
        modifier
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = {
                if (haptics) hf.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            })
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(iconSize).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) { icon() }
        if (label != null) {
            Spacer(Modifier.height(6.dp))
            Text(label, style = labelStyle(labelColor, onWallpaper), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp))
        }
    }
}

@Composable
fun AppIconImage(app: AppEntry?, spec: IconSpec, version: Int, size: Dp) {
    val bmp = rememberIcon(app, spec, version)
    if (bmp != null) Image(bmp, null, Modifier.size(size))
    else Box(Modifier.size(size).clip(composeIconShape(spec.shape)).background(Color(0x33FFFFFF)))
}

/** One UI-style folder tile: frosted rounded plate with a 2x2 preview of its apps. */
@Composable
fun FolderIconImage(apps: List<AppEntry>, spec: IconSpec, version: Int, size: Dp, dark: Boolean) {
    val plate = if (dark) Color(0x66303036) else Color(0x80FFFFFF)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(plate).padding(size * 0.14f)) {
        val mini = (size - size * 0.28f - 4.dp) / 2
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 2) {
                    val a = apps.getOrNull(r * 2 + c)
                    if (a != null) AppIconImage(a, spec, version, mini) else Spacer(Modifier.width(mini))
                }
            }
        }
    }
}
