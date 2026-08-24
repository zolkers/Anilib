package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction

@Composable
internal fun rememberSearchFocusRequester(active: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    return requester
}

internal fun Modifier.searchFocus(requester: FocusRequester): Modifier = focusRequester(requester)

internal fun searchKeyboardOptions(): KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)

internal fun searchKeyboardActions(search: () -> Unit = { }): KeyboardActions =
    KeyboardActions(onSearch = { search() })
