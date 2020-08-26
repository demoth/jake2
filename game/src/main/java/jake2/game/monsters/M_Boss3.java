/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 13.11.2003 by RST.
// $Id: M_Boss3.java,v 1.3 2005-11-20 22:18:33 salomo Exp $
package jake2.game.monsters;

import jake2.game.*;
import jake2.qcommon.Defines;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.util.Math3D;

public class M_Boss3 {

    static EntUseAdapter Use_Boss3 = new EntUseAdapter() {
    	public String getID() { return "Use_Boss3"; }
        public void use(SubgameEntity ent, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            gameExports.gameImports.WriteByte(NetworkCommands.svc_temp_entity);
            gameExports.gameImports.WriteByte(Defines.TE_BOSSTPORT);
            gameExports.gameImports.WritePosition(ent.s.origin);
            gameExports.gameImports.multicast(ent.s.origin, MulticastTypes.MULTICAST_PVS);
            GameUtil.G_FreeEdict(ent, gameExports);
        }
    };

    static EntThinkAdapter Think_Boss3Stand = new EntThinkAdapter() {
    	public String getID() { return "Think_Boss3Stand"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            if (ent.s.frame == M_Boss32.FRAME_stand260)
                ent.s.frame = M_Boss32.FRAME_stand201;
            else
                ent.s.frame++;
            ent.nextthink = gameExports.level.time + Defines.FRAMETIME;
            return true;
        }

    };

    /*
     * QUAKED monster_boss3_stand (1 .5 0) (-32 -32 0) (32 32 90)
     * 
     * Just stands and cycles in one place until targeted, then teleports away.
     */
    public static void SP_monster_boss3_stand(SubgameEntity self, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.deathmatch.value != 0) {
            GameUtil.G_FreeEdict(self, gameExports);
            return;
        }

        self.movetype = GameDefines.MOVETYPE_STEP;
        self.solid = Defines.SOLID_BBOX;
        self.model = "models/monsters/boss3/rider/tris.md2";
        self.s.modelindex = gameExports.gameImports.modelindex(self.model);
        self.s.frame = M_Boss32.FRAME_stand201;

        gameExports.gameImports.soundindex("misc/bigtele.wav");

        Math3D.VectorSet(self.mins, -32, -32, 0);
        Math3D.VectorSet(self.maxs, 32, 32, 90);

        self.use = Use_Boss3;
        self.think = Think_Boss3Stand;
        self.nextthink = gameExports.level.time + Defines.FRAMETIME;
        gameExports.gameImports.linkentity(self);
    }
}