package jake2.game

import jake2.game.items.GameItems.FindItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** A weapon pickup awards inventory and only auto-switches when the weapon can fire. */
class WeaponPickupTest {
    @Test
    fun `weapon pickup considers existing and newly awarded ammo before switching`() {
        val game = GameExportsImpl(TestGameImports())
        val player = game.g_edicts[1]
        val client = GamePlayerInfo(0)
        PlayerClient.InitClientPersistant(client, game)
        player.setClient(client)
        game.gameCvars.g_select_empty.set(true)

        val shotgun = FindItem("Shotgun", game)
        val shells = FindItem("Shells", game)
        val pickup = GameEntity(2).apply {
            item = shotgun
            spawnflags = GameDefines.DROPPED_ITEM
        }
        fun collect() = assertTrue(PlayerWeapon.Pickup_Weapon.interact(pickup, player, game))

        collect()
        assertEquals(1, client.pers.inventory[shotgun.index])
        assertNull(client.newweapon)

        client.pers.inventory[shotgun.index] = 0
        client.pers.inventory[shells.index] = 1
        collect()
        assertSame(shotgun, client.newweapon)

        client.newweapon = null
        client.pers.inventory[shotgun.index] = 0
        client.pers.inventory[shells.index] = 0
        pickup.spawnflags = 0
        collect()
        assertTrue(client.pers.inventory[shells.index] > 0)
        assertSame(shotgun, client.newweapon)

        val bfg = FindItem("BFG10K", game)
        val cells = FindItem("Cells", game)
        pickup.item = bfg
        pickup.spawnflags = GameDefines.DROPPED_ITEM
        client.newweapon = null
        client.pers.inventory[cells.index] = 49
        collect()
        assertNull(client.newweapon)

        client.pers.inventory[bfg.index] = 0
        client.pers.inventory[cells.index] = 50
        collect()
        assertSame(bfg, client.newweapon)
    }
}
