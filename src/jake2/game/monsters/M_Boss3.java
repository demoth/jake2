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

import jake2.Defines;
import jake2.game.EntThinkAdapter;
import jake2.game.EntUseAdapter;
import jake2.game.GameBase;
import jake2.game.GameUtil;
import jake2.game.edict_t;
import jake2.util.Math3D;

public class M_Boss3 {

    static EntUseAdapter Use_Boss3 = new EntUseAdapter() {
    	public String getID() { return "Use_Boss3"; }
        public void use(edict_t ent, edict_t other, edict_t activator) {
            GameBase.gi.WriteByte(Defines.svc_temp_entity);
            GameBase.gi.WriteByte(Defines.TE_BOSSTPORT);
            GameBase.gi.WritePosition(ent.s.origin);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);
            GameUtil.G_FreeEdict(ent);
        }
    };

    static EntThinkAdapter Think_Boss3Stand = new EntThinkAdapter() {
    	public String getID() { return "Think_Boss3Stand"; }
        public boolean think(edict_t ent) {
            if (ent.s.frame == M_Boss32.FRAME_stand260)
                ent.s.frame = M_Boss32.FRAME_stand201;
            else
                ent.s.frame++;
            ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
            return true;
        }

    };

    /*
     * QUAKED monster_boss3_stand (1 .5 0) (-32 -32 0) (32 32 90)
     * 
     * Just stands and cycles in one place until targeted, then teleports away.
     */
    public static void SP_monster_boss3_stand(edict_t self) {
        if (GameBase.deathmatch.value != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }

        self.movetype = Defines.MOVETYPE_STEP;
        self.solid = Defines.SOLID_BBOX;
        self.model = "models/monsters/boss3/rider/tris.md2";
        self.s.modelindex = GameBase.gi.modelindex(self.model);
        self.s.frame = M_Boss32.FRAME_stand201;

        GameBase.gi.soundindex("misc/bigtele.wav");

        Math3D.VectorSet(self.mins, -32, -32, 0);
        Math3D.VectorSet(self.maxs, 32, 32, 90);

        self.use = Use_Boss3;
        self.think = Think_Boss3Stand;
        self.nextthink = GameBase.level.time + Defines.FRAMETIME;
        GameBase.gi.linkentity(self);
    }
}