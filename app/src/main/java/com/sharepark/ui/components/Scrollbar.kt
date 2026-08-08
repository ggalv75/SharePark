package com.sharepark.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A thin vertical scrollbar that only renders when the content actually overflows the viewport.
 */
fun Modifier.simpleVerticalScrollbar(
    state: LazyListState,
    color: Color,
    width: Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()

    val layoutInfo = state.layoutInfo
    val totalItemsCount = layoutInfo.totalItemsCount
    val visibleItemsInfo = layoutInfo.visibleItemsInfo

    if (totalItemsCount > 0 && visibleItemsInfo.isNotEmpty() && visibleItemsInfo.size < totalItemsCount) {
        val firstVisibleIndex = visibleItemsInfo.first().index
        val viewportHeight = size.height
        val thumbHeight = (visibleItemsInfo.size.toFloat() / totalItemsCount) * viewportHeight
        val thumbOffsetY = (firstVisibleIndex.toFloat() / totalItemsCount) * viewportHeight
        val widthPx = width.toPx()
        val x = size.width - widthPx

        drawRoundRect(
            color = color,
            topLeft = Offset(x, thumbOffsetY),
            size = Size(widthPx, thumbHeight),
            alpha = 0.5f,
            cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
        )
    }
}

/**
 * A thin vertical scrollbar for a plain Modifier.verticalScroll(ScrollState) container.
 */
fun Modifier.simpleVerticalScrollbar(
    state: ScrollState,
    color: Color,
    width: Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()

    val maxValue = state.maxValue
    if (maxValue > 0) {
        val viewportHeight = size.height
        val contentHeight = viewportHeight + maxValue
        val thumbHeight = (viewportHeight / contentHeight) * viewportHeight
        val thumbOffsetY = (state.value.toFloat() / maxValue) * (viewportHeight - thumbHeight)
        val widthPx = width.toPx()
        val x = size.width - widthPx

        drawRoundRect(
            color = color,
            topLeft = Offset(x, thumbOffsetY),
            size = Size(widthPx, thumbHeight),
            alpha = 0.5f,
            cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
        )
    }
}
