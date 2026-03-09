package org.demoth.cake.ui.menu

import java.util.ArrayDeque

class MenuEventBus(
    initialState: MenuStateSnapshot = MenuStateSnapshot(),
) {
    private val intents = ArrayDeque<MenuIntent>()
    private val signals = ArrayDeque<MenuSignal>()
    private var latestState: MenuStateSnapshot = initialState

    fun postIntent(intent: MenuIntent) {
        intents.addLast(intent)
    }

    fun drainIntents(
        maxItems: Int = DEFAULT_DRAIN_LIMIT,
        consumer: (MenuIntent) -> Unit,
    ) {
        var remaining = maxItems.coerceAtLeast(0)
        while (remaining > 0 && intents.isNotEmpty()) {
            consumer(intents.removeFirst())
            remaining--
        }
    }

    fun postSignal(signal: MenuSignal) {
        if (signal is MenuSignal.StateUpdated) {
            latestState = signal.snapshot
        }
        signals.addLast(signal)
    }

    fun postState(snapshot: MenuStateSnapshot) {
        postSignal(MenuSignal.StateUpdated(snapshot))
    }

    fun latestState(): MenuStateSnapshot = latestState

    fun drainSignals(
        maxItems: Int = DEFAULT_DRAIN_LIMIT,
        consumer: (MenuSignal) -> Unit,
    ) {
        var remaining = maxItems.coerceAtLeast(0)
        while (remaining > 0 && signals.isNotEmpty()) {
            consumer(signals.removeFirst())
            remaining--
        }
    }

    fun clear() {
        intents.clear()
        signals.clear()
    }

    companion object {
        const val DEFAULT_DRAIN_LIMIT: Int = 128
    }
}
