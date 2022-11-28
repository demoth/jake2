package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.network.MulticastTypes
import jake2.qcommon.network.messages.server.PointTEMessage

/**
 * QUAKED target_temp_entity (1 0 0) (-8 -8 -8) (8 8 8) 
 * Fire an origin based temp entity event to the clients. 
 * "style" type byte, see [jake2.qcommon.network.messages.server.PointTEMessage.SUBTYPES]
 */
fun targetTempEntity(self: SubgameEntity, game: GameExportsImpl) {
    self.use = tempEntityUse
}

private val tempEntityUse = registerUse("Use_Target_Tent") { self, _, _, game ->  
    game.gameImports.multicastMessage(self.s.origin, PointTEMessage(self.style, self.s.origin), MulticastTypes.MULTICAST_PVS);
}
