package com.celoext.ui.components

import com.celoext.ui.theme.kimiLike
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celoext.Graph
import com.celoext.ui.theme.Ic
import com.celoext.ui.theme.Oc

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = Oc.c.text,
    weight: FontWeight? = null,
    family: FontFamily? = null,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text, modifier = modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = family ?: Oc.body, maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = align, lineHeight = lineHeight,
    )
}

/** Serif heading. */
@Composable
fun Heading(text: String, modifier: Modifier = Modifier, size: TextUnit = 26.sp, color: Color = Oc.c.text, align: TextAlign? = null) =
    Txt(text, modifier, size, color, FontWeight.Normal, Oc.heading, align = align, lineHeight = size * 1.22f)

@Composable
fun Ico(v: ImageVector, modifier: Modifier = Modifier, tint: Color = Oc.c.text, size: Dp = 24.dp) {
    androidx.compose.foundation.Image(
        imageVector = v, contentDescription = null, modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun rememberHaptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    return { if (Graph.prefs.haptics) h.performHapticFeedback(HapticFeedbackType.LongPress) }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Oc.c.text,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    bg: Color? = null,
) {
    Box(
        modifier.size(size).clip(CircleShape).then(if (bg != null) Modifier.background(bg) else Modifier).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = tint, size = iconSize) }
}

/** Small top app bar (Material 3): navigation icon, title on the left, actions on the right. */
@Composable
fun TopBar(
    title: String? = null,
    left: @Composable (() -> Unit)? = null,
    right: @Composable RowScope.() -> Unit = {},
    serif: Boolean = false,
) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { androidx.compose.runtime.CompositionLocalProvider(LocalInTopBar provides true) { left?.invoke() } }
        if (title != null) Txt(
            title, Modifier.align(Alignment.CenterStart).padding(start = if (left != null) 56.dp else 16.dp, end = 56.dp),
            size = 22.sp, maxLines = 1,
        )
        androidx.compose.runtime.CompositionLocalProvider(LocalInTopBar provides true) {
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = right)
        }
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, icon: ImageVector? = null, enabled: Boolean = true) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { Ico(icon, tint = androidx.compose.material3.LocalContentColor.current, size = 20.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = Oc.body)
    }
    val m = modifier.height(48.dp)
    if (filled) androidx.compose.material3.Button(onClick, m, enabled = enabled, shape = CircleShape, content = content)
    else androidx.compose.material3.OutlinedButton(onClick, m, enabled = enabled, shape = CircleShape, content = content)
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, danger: Boolean = false) {
    val c = Oc.c
    val label: @Composable RowScope.() -> Unit = { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = Oc.body) }
    val m = modifier.height(36.dp)
    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
    when {
        filled -> androidx.compose.material3.Button(
            onClick, m, shape = CircleShape, contentPadding = pad,
            colors = if (danger) androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.danger, contentColor = Color.White)
            else androidx.compose.material3.ButtonDefaults.buttonColors(),
            content = label,
        )
        danger -> androidx.compose.material3.OutlinedButton(
            onClick, m, shape = CircleShape, contentPadding = pad,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = c.danger),
            border = BorderStroke(1.dp, c.danger.copy(alpha = .5f)), content = label,
        )
        else -> androidx.compose.material3.FilledTonalButton(onClick, m, shape = CircleShape, contentPadding = pad, content = label)
    }
}

@Composable
fun OcSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Txt(text, modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp), size = 14.sp, color = Oc.c.accent, weight = FontWeight.Medium)
}

/** A group of rows in one rounded tile: 2 dp gaps between rows, large outer corners (M3 segmented list). */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(28.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp), content = content,
    )
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color = Oc.c.text,
    circleIcon: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Oc.c
    Row(
        modifier.fillMaxWidth().background(c.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 64.dp).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val danger = titleColor != c.text
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (danger) titleColor.copy(alpha = .14f) else c.progressTrack), contentAlignment = Alignment.Center) {
                Ico(icon, tint = if (danger) titleColor else c.text, size = 22.dp)
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(title, size = 16.sp, color = titleColor)
            if (subtitle != null) Txt(subtitle, size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        if (chevron) Ico(Ic.ChevronRight, tint = c.textDim, size = 22.dp)
    }
}


@Composable
fun Card(modifier: Modifier = Modifier, shape: Shape = Oc.round(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(shape).background(Oc.c.surface), content = content)
}

/** The mark: an alpha mask tinted with [color] (the accent by default). */
@Composable
fun Logo(size: Dp = 56.dp, color: Color = Oc.c.accent) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.celoext.R.drawable.logo_mark),
        contentDescription = null, modifier = Modifier.size(size),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit, colorFilter = ColorFilter.tint(color),
    )
}

@Composable
fun Spinner(size: Dp = 22.dp, color: Color = Oc.c.accent, stroke: Dp = 2.5.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(size).rotate(a)) {
        val w = stroke.toPx()
        drawArc(color, 0f, 270f, false, topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Work indicator: the mark, softly pulsing. */
@Composable
fun PulsingLogo(size: Dp = 22.dp, color: Color = Oc.c.accent) {
    val t = rememberInfiniteTransition(label = "pulse")
    val blink by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.celoext.R.drawable.logo_mark),
        contentDescription = null, modifier = Modifier.size(size).alpha(blink),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit, colorFilter = ColorFilter.tint(color),
    )
}

@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, warn: Boolean = false) {
    val c = Oc.c
    val animated by androidx.compose.animation.core.animateFloatAsState(fraction, tween(380), label = "bar")
    val fill = if (warn) c.warn else c.progressFill
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.progressTrack)) {
        Box(Modifier.fillMaxWidth(animated.coerceIn(0f, 1f).coerceAtLeast(0.04f)).height(8.dp).clip(CircleShape).background(fill))
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = Oc.c.textDim, bg: Color = Oc.c.surfaceHigh) {
    Box(modifier.clip(Oc.round(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Txt(text, size = 12.sp, color = color, weight = FontWeight.Medium)
    }
}

@Composable
fun OcTextField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = Oc.c
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, minLines = if (singleLine) 1 else minLines,
        placeholder = { Txt(placeholder, size = 16.sp, color = c.textDim) },
        textStyle = TextStyle(fontSize = 16.sp, fontFamily = if (mono) FontFamily.Monospace else Oc.body),
        shape = Oc.round(12.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    skipHalf: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Oc.c
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipHalf),
        containerColor = c.surface,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            if (title != null) {
                Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
                    IconBtn(if (onBack != null) Ic.Back else Ic.Close, onBack ?: onDismiss, Modifier.align(Alignment.CenterStart))
                    Txt(title, Modifier.align(Alignment.Center), size = 20.sp)
                }
            }
            content()
        }
    }
}

@Composable
fun OcDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "OK",
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancel",
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = Oc.c
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        shape = Oc.round(28.dp),
        title = { Txt(title, size = 24.sp) },
        text = content,
        confirmButton = {
            if (onConfirm != null) androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) c.danger else c.accent, fontFamily = Oc.body)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(if (onConfirm == null) "OK" else dismissText, color = c.accent, fontFamily = Oc.body)
            }
        },
    )
}

/** Picker row in dialogs/sheets: accent color and a check on the selected one. */
@Composable
fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, subtitle: String? = null, badge: String? = null) {
    val c = Oc.c
    val col = if (selected) c.accent else c.text
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ico(icon, tint = col, size = 24.dp); Spacer(Modifier.width(20.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(label, size = 17.sp, color = col)
                if (badge != null) { Spacer(Modifier.width(10.dp)); Chip(badge) }
            }
            if (subtitle != null) Txt(subtitle, size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
        if (selected) Ico(Ic.Check, tint = c.accent, size = 24.dp)
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, (key, label) ->
            SegmentedButton(
                selected = key == selected, onClick = { onSelect(key) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label, fontSize = 14.sp, maxLines = 1, fontFamily = Oc.body) },
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ico(icon, tint = Oc.c.textDim, size = 72.dp)
        Spacer(Modifier.height(24.dp))
        Txt(title, size = 22.sp, color = Oc.c.textDim, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt(text, size = 17.sp, color = Oc.c.textDim.copy(alpha = .85f), align = TextAlign.Center)
    }
}
