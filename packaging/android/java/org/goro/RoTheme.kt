package org.goro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compose port of ui/rotheme (Go). Values are copied from rotheme.Default so the
 * native screens and the Go-drawn windows read as one skin. Sizes are scaled up
 * for touch (the Go UI is 11px text for a mouse).
 */
object Ro {
    val WindowBody = Color(0xFFFFFFFF)
    val TitleTop = Color(0xFFD6E8FA)
    val Title = Color(0xFFB8D6F2)
    val Border = Color(0xFF76A0CE)
    val Footer = Color(0xFFF4F6F8)
    val FooterLine = Color(0xFFAEB4BC)
    val Panel = Color(0xFFFAFCFF)
    val Button = Color(0xFFECF4FC)
    val ButtonDown = Color(0xFFC6DEF5)
    val ButtonBorder = Color(0xFF8AAED6)
    val Disabled = Color(0xFFE2E6EB)
    val Text = Color(0xFF26303A)
    val TitleText = Color(0xFF163658)
    val Label = Color(0xFF3A689C)
    val Muted = Color(0xFF62707E)
    val InputFocus = Color(0xFF528AC8)
    val Selected = Color(0xFFDEEDFC) // characterSelectSelectedBG
    val Error = Color(0xFFB3261E)

    val ButtonRadius = 6.dp
    val WindowRadius = 6.dp
    val TitleHeight = 36.dp
    val MinTouch = 44.dp
}

private val RoColorScheme = lightColorScheme(
    primary = Ro.Border,
    onPrimary = Ro.TitleText,
    primaryContainer = Ro.Selected,
    onPrimaryContainer = Ro.TitleText,
    secondaryContainer = Ro.Button,
    onSecondaryContainer = Ro.Text,
    background = Ro.WindowBody,
    onBackground = Ro.Text,
    surface = Ro.WindowBody,
    onSurface = Ro.Text,
    surfaceVariant = Ro.Panel,
    onSurfaceVariant = Ro.Muted,
    outline = Ro.Border,
    outlineVariant = Ro.FooterLine,
    error = Ro.Error,
)

// Go bundles Inter; the system sans is the stand-in until the font is added to res/font.
private val RoTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun RoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RoColorScheme, typography = RoTypography, content = content)
}

private fun lighter(c: Color, factor: Float): Color =
    if (factor <= 1f) c else lerp(c, Color.White, 1f - 1f / factor)

/** buttonTitleBarGradient(2): the title gradient lightened 2x and flipped. */
private fun buttonGradient(pressed: Boolean, enabled: Boolean): Pair<Color, Color> = when {
    !enabled -> Ro.Disabled to lerp(Ro.Disabled, Color.White, 0.42f)
    pressed -> Ro.ButtonDown to lerp(Ro.ButtonDown, Color.White, 0.42f)
    else -> lighter(Ro.Title, 2f) to lighter(Ro.TitleTop, 2f)
}

/** Title bar + body + striped footer, matching ui.Win(Title, Content, Footer). */
@Composable
fun RoWindow(
    title: String,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Ro.WindowRadius)
    Column(
        modifier
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Ro.WindowBody)
            .border(1.dp, Ro.Border, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Ro.TitleHeight)
                .background(Brush.verticalGradient(listOf(Ro.TitleTop, Ro.Title)))
                .drawBehind {
                    val line = 1.dp.toPx()
                    drawRect(Ro.Border, Offset(0f, size.height - line), Size(size.width, line))
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                title,
                color = Ro.TitleText,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        content()
        if (footer != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Ro.Footer)
                    .drawBehind {
                        val stripe = 2.dp.toPx()
                        var y = 0f
                        while (y < size.height) {
                            drawRect(Color.White.copy(alpha = 0.5f), Offset(0f, y), Size(size.width, minOf(stripe, size.height - y)))
                            y += stripe * 2
                        }
                        drawRect(Ro.FooterLine, Offset.Zero, Size(size.width, 1.dp.toPx()))
                    }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
        }
    }
}

/** rotheme.Button: lightened title gradient, soft drop shadow, top reflection, 1dp border. */
@Composable
fun RoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    bold: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (top, bottom) = buttonGradient(pressed, enabled)
    val shape = RoundedCornerShape(Ro.ButtonRadius)
    val radius = Ro.ButtonRadius
    Box(
        modifier
            .defaultMinSize(minWidth = 72.dp, minHeight = Ro.MinTouch)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                val r = CornerRadius(radius.toPx())
                drawRoundRect(Color(0x26666666), Offset(0f, 2.dp.toPx()), size, r)
                drawRoundRect(Color(0x59666666), Offset(0f, 1.dp.toPx()), size, r)
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawBehind {
                val inset = 3.dp.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = 0.4f),
                    Offset(inset, inset),
                    Size(size.width - inset * 2, size.height / 2 - inset),
                    CornerRadius(radius.toPx()),
                )
            }
            .border(1.dp, if (enabled) Ro.ButtonBorder else Ro.FooterLine, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Ro.Label)
            Text(
                text,
                color = if (enabled) Ro.Text else Ro.Muted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** rotheme.TextField: panel fill, inset shadow, FooterLine border that turns InputFocus. */
@Composable
fun RoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = if (enabled) Ro.Text else Ro.Muted, fontSize = 15.sp, fontFamily = FontFamily.SansSerif),
        cursorBrush = SolidColor(Ro.Text),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Ro.MinTouch)
                    .background(if (enabled) Ro.Panel else Ro.Disabled)
                    .drawBehind {
                        for (row in 0 until 4) {
                            drawRect(
                                Color(0xFF526C8A).copy(alpha = (34 - row * 7) / 255f),
                                Offset(0f, row * 1.dp.toPx()),
                                Size(size.width, 1.dp.toPx()),
                            )
                        }
                    }
                    .border(1.dp, if (focused) Ro.InputFocus else Ro.FooterLine)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { inner() }
                trailing?.invoke()
            }
        },
    )
}

/** rotheme.Checkbox: gradient box with a thin check mark. */
@Composable
fun RoCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (top, bottom) = buttonGradient(pressed, enabled)
    val shape = RoundedCornerShape(5.dp)
    val mark = if (enabled) Ro.Text else Ro.Muted
    Row(
        modifier
            .heightIn(min = Ro.MinTouch)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, interactionSource = source, indication = null, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .border(1.dp, if (enabled) Ro.ButtonBorder else Ro.Disabled, shape),
        ) {
            if (checked) {
                Canvas(Modifier.size(22.dp)) {
                    val u = size.width / 17f // the Go check mark is drawn on a 17px box
                    val w = 1.6f * u
                    drawLine(mark, Offset(4 * u, 8 * u), Offset(7 * u, 11 * u), w, StrokeCap.Round)
                    drawLine(mark, Offset(7 * u, 11 * u), Offset(14 * u, 4 * u), w, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = mark, style = MaterialTheme.typography.bodyMedium)
    }
}

/** PanelBody box with a 1dp window border; [selected] uses the character-select highlight. */
@Composable
fun RoPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .background(if (selected) Ro.Selected else Ro.Panel)
        .border(1.dp, if (selected) Ro.ButtonBorder else Ro.Border)
    Box(if (onClick != null) base.clickable(onClick = onClick) else base) { content() }
}
