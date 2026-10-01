package com.sharepark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Muted gray used for secondary labels across the app. */
val MutedText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Large, heavy screen title on the plain canvas — replaces the stock TopAppBar so every screen
 * opens with the same bold headline. [actions] sit in a soft gray pill at the end of the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    ) {
        if (navigationIcon != null || actions != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationIcon?.invoke()
                Spacer(modifier = Modifier.weight(1f))
                if (actions != null) {
                    PillActions(content = actions)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText
            )
        }
    }
}

/** A row of icon actions grouped inside one rounded gray pill. */
@Composable
fun PillActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** White circular icon button with a faint lift — floats over maps and sheets. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(size)
            .pressScale(interactionSource)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f)),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

/** White rounded tile with no border or shadow — the basic building block of every list. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val shape = MaterialTheme.shapes.medium
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier
                .fillMaxWidth()
                .pressScale(interactionSource, scaleDown = 0.98f),
            shape = shape,
            colors = colors
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Card header: icon badge + bold title, used at the top of informational cards. */
@Composable
fun CardTitle(
    title: String,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.secondary
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBadge(
                icon = icon,
                tint = tint,
                size = 36.dp,
                iconSize = 19.dp,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Tappable list row: icon badge, title + description, trailing chevron. */
@Composable
fun NavigationRowCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.secondary
) {
    AppCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, tint = tint, modifier = Modifier.padding(end = 14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = MutedText.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * A compact stat: bold value with an optional small-caps unit, and a muted label with an icon
 * underneath — e.g. "12 דק׳" over "לפני · חנה".
 */
@Composable
fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null
) {
    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)) { append(value) }
                if (unit != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) { append(" $unit") }
                }
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MutedText, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                maxLines = 1
            )
        }
    }
}

/** Thin vertical divider between stats in a row. */
@Composable
fun StatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

/** Small grabber bar at the top of a bottom sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(40.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline)
    )
}

/** Full-width pill call-to-action. */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(54.dp)
            .pressScale(interactionSource),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Quiet gray pill — the secondary action next to a [PrimaryPillButton]. */
@Composable
fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    PrimaryPillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

/** Muted section label above a group of cards, with an optional text action at the end. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(4.dp)
            )
        }
    }
}
