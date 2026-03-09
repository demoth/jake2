package org.demoth.cake.ui.menu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MenuEventBusTest {

    @Test
    fun drainsIntentsInFifoOrderWithLimit() {
        val bus = MenuEventBus()
        bus.postIntent(MenuIntent.OpenMainMenu)
        bus.postIntent(MenuIntent.OpenProfileEditor)
        bus.postIntent(MenuIntent.RequestStateSync)

        val drained = mutableListOf<MenuIntent>()
        bus.drainIntents(maxItems = 2) { drained += it }

        assertEquals(
            listOf(
                MenuIntent.OpenMainMenu,
                MenuIntent.OpenProfileEditor,
            ),
            drained,
        )

        val tail = mutableListOf<MenuIntent>()
        bus.drainIntents { tail += it }
        assertEquals(listOf(MenuIntent.RequestStateSync), tail)
    }

    @Test
    fun tracksLatestStateWhenStateSignalPosted() {
        val bus = MenuEventBus()
        val updated = MenuStateSnapshot(
            activeScreen = MenuScreen.PROFILE_EDIT,
            mainMenu = MainMenuState(activeProfileId = "mod", canDisconnect = true),
        )

        bus.postSignal(MenuSignal.StateUpdated(updated))

        assertEquals(updated, bus.latestState())
    }
}
