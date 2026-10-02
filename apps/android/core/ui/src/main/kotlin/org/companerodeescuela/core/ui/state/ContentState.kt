package org.companerodeescuela.core.ui.state

import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome

/**
 * The four states every data-backed screen has to render.
 *
 * [Empty] is separate from [Success] because "you have no tasks today" and
 * "here are your tasks" are different screens for the user, and collapsing
 * them is how apps end up showing a blank list with no explanation.
 */
sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>
    data object Empty : ContentState<Nothing>
    data class Content<T>(val value: T) : ContentState<T>
    data class Failed(val error: AppError) : ContentState<Nothing>
}

/**
 * Maps a repository [Outcome] into a [ContentState].
 *
 * `isEmpty` is supplied by the caller because emptiness is domain knowledge:
 * a list with no items is empty, but a schedule with no items today might
 * still want to show the next day.
 */
fun <T> Outcome<T>.toContentState(isEmpty: (T) -> Boolean): ContentState<T> = when (this) {
    is Outcome.Success -> if (isEmpty(value)) ContentState.Empty else ContentState.Content(value)
    is Outcome.Failure -> ContentState.Failed(error)
}
