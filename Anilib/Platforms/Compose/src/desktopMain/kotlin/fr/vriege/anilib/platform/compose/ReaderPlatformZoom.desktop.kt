package fr.vriege.anilib.platform.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent

private const val READER_WHEEL_ZOOM_FACTOR = 1.12f

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.readerPlatformZoom(zoom: (Float) -> Unit): Modifier =
    onPointerEvent(PointerEventType.Scroll) { event ->
        if (!event.keyboardModifiers.isCtrlPressed) return@onPointerEvent
        val vertical = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
        if (vertical == 0f) return@onPointerEvent
        zoom(if (vertical < 0f) READER_WHEEL_ZOOM_FACTOR else 1f / READER_WHEEL_ZOOM_FACTOR)
        event.changes.forEach { it.consume() }
    }
