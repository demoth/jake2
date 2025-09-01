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

import jake2.game.GameDefines;
import jake2.game.GameEntity;
import jake2.game.GameExportsImpl;
import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.PointTEMessage;
import jake2.qcommon.util.Math3D;

// Boss3
public class M_Makron_Idle {

    static EntUseAdapter Use_Boss3 = new EntUseAdapter() {
        public String getID() {
            return "Use_Boss3";
        }

        public void use(GameEntity ent, GameEntity other, GameEntity activator, GameExportsImpl gameExports) {
            gameExports.gameImports.multicastMessage(ent.s.origin, new PointTEMessage(Defines.TE_BOSSTPORT, ent.s.origin), MulticastTypes.MULTICAST_PVS);
            gameExports.freeEntity(ent);
        }
    };

    static EntThinkAdapter Think_Boss3Stand = new EntThinkAdapter() {
    	public String getID() { return "Think_Boss3Stand"; }
        public boolean think(GameEntity ent, GameExportsImpl gameExports) {
            if (ent.s.frame == M_Makron.FRAME_stand260)
                ent.s.frame = M_Makron.FRAME_stand201;
            else
                ent.s.frame++;
            ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            return true;
        }

    };

    /*
     * QUAKED monster_boss3_stand (1 .5 0) (-32 -32 0) (32 32 90)
     * 
     * Just stands and cycles in one place until targeted, then teleports away.
     */
    public static void SP_monster_boss3_stand(GameEntity self, GameExportsImpl gameExports) {
        if (gameExports.skipForDeathmatch(self)) return;

        self.movetype = GameDefines.MOVETYPE_STEP;
        self.solid = Defines.SOLID_BBOX;
        self.model = "models/monsters/boss3/rider/tris.md2";
        self.s.modelindex = gameExports.gameImports.modelindex(self.model);
        self.s.frame = M_Makron.FRAME_stand201;

        gameExports.gameImports.soundindex("misc/bigtele.wav");

        Math3D.VectorSet(self.mins, -32, -32, 0);
        Math3D.VectorSet(self.maxs, 32, 32, 90);

        self.use = Use_Boss3;
        self.think.action = Think_Boss3Stand;
        self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
        gameExports.gameImports.linkentity(self);
    }
}
