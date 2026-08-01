package com.topjohnwu.magisk.ui.pivot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.databinding.Observable
import androidx.databinding.ObservableList
import com.topjohnwu.magisk.databinding.DiffItem
import com.topjohnwu.magisk.databinding.DiffList
import com.topjohnwu.magisk.databinding.FilterList

/**
 * Bridges the app's DataBinding reactive primitives into Jetpack Compose so the ported Metro
 * sections can reuse the existing ViewModels verbatim (no logic rewrite).
 *
 * Two things need observing:
 *  - [ObservableList] structure changes (insert/remove/move/change) -> [asComposeState].
 *  - Per-item / per-ViewModel {@literal @}Bindable property changes -> [observeAsTick].
 */

/**
 * Snapshots an [ObservableList] into immutable Compose state. Recomposes whenever the list's
 * structure changes. Item content changes are surfaced per-row via [observeAsTick].
 */
@Composable
fun <T> ObservableList<T>.asComposeState(): State<List<T>> {
    val state = remember(this) { mutableStateOf(toList()) }
    DisposableEffect(this) {
        val callback = object : ObservableList.OnListChangedCallback<ObservableList<T>>() {
            override fun onChanged(sender: ObservableList<T>) {
                state.value = sender.toList()
            }

            override fun onItemRangeChanged(sender: ObservableList<T>, start: Int, count: Int) {
                state.value = sender.toList()
            }

            override fun onItemRangeInserted(sender: ObservableList<T>, start: Int, count: Int) {
                state.value = sender.toList()
            }

            override fun onItemRangeMoved(
                sender: ObservableList<T>,
                from: Int,
                to: Int,
                count: Int
            ) {
                state.value = sender.toList()
            }

            override fun onItemRangeRemoved(sender: ObservableList<T>, start: Int, count: Int) {
                state.value = sender.toList()
            }
        }
        addOnListChangedCallback(callback)
        state.value = toList()
        onDispose { removeOnListChangedCallback(callback) }
    }
    return state
}

/**
 * [DiffList] only exposes the immutable [List] surface, but the concrete implementation returned by
 * `diffList()` is always an [ObservableList]. Bridge through that so list-backed ViewModels
 * (e.g. logs) observe the same way as the [MergeObservableList]-backed ones.
 */
@Composable
@Suppress("UNCHECKED_CAST")
fun <T : DiffItem<*>> DiffList<T>.asComposeState(): State<List<T>> =
    (this as ObservableList<T>).asComposeState()

/** [FilterList] is implemented by the same observable backing list as [DiffList]. */
@Composable
@Suppress("UNCHECKED_CAST")
fun <T : DiffItem<*>> FilterList<T>.asComposeState(): State<List<T>> =
    (this as ObservableList<T>).asComposeState()

/**
 * Returns a state-backed counter that increments on every {@literal @}Bindable property change of
 * [observable]. Read the returned value inside a composable (e.g. `key(observable.observeAsTick())`)
 * to force a recomposition that re-reads the observable's current property values.
 */
@Composable
fun Observable.observeAsTick(): Int {
    var tick by remember(this) { mutableIntStateOf(0) }
    DisposableEffect(this) {
        val callback = object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                tick++
            }
        }
        addOnPropertyChangedCallback(callback)
        onDispose { removeOnPropertyChangedCallback(callback) }
    }
    return tick
}
