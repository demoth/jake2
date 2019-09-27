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
// $Id: PlayerTrail.java,v 1.2 2004-09-22 19:22:04 salomo Exp $
package jake2.game;

import jake2.qcommon.edict_t;
import jake2.qcommon.util.Math3D;

public class PlayerTrail {

    /*
     * ==============================================================================
     * 
     * PLAYER TRAIL
     * 
     * ==============================================================================
     * 
     * This is a circular list containing the a list of points of where the
     * player has been recently. It is used by monsters for pursuit.
     * 
     * .origin the spot .owner forward link .aiment backward link
     */

    static int TRAIL_LENGTH = 8;

    static SubgameEntity trail[] = new SubgameEntity[TRAIL_LENGTH];

    static int trail_head;

    static boolean trail_active = false;
    static {
        //TODO: potential error
        for (int n = 0; n < TRAIL_LENGTH; n++)
            trail[n] = new SubgameEntity(n);
    }

    static int NEXT(int n) {
        return (n + 1) % PlayerTrail.TRAIL_LENGTH;
    }

    static int PREV(int n) {
        return (n + PlayerTrail.TRAIL_LENGTH - 1) % PlayerTrail.TRAIL_LENGTH;
    }

    static void Init() {

        // FIXME || coop
        if (GameBase.deathmatch.value != 0)
            return;

        for (int n = 0; n < PlayerTrail.TRAIL_LENGTH; n++) {
            PlayerTrail.trail[n] = GameUtil.G_Spawn();
            PlayerTrail.trail[n].classname = "player_trail";
        }

        trail_head = 0;
        trail_active = true;
    }

    static void Add(float[] spot) {
        float[] temp = { 0, 0, 0 };

        if (!trail_active)
            return;

        Math3D.VectorCopy(spot, PlayerTrail.trail[trail_head].s.origin);

        PlayerTrail.trail[trail_head].timestamp = GameBase.level.time;

        Math3D.VectorSubtract(spot,
                PlayerTrail.trail[PREV(trail_head)].s.origin, temp);
        PlayerTrail.trail[trail_head].s.angles[1] = Math3D.vectoyaw(temp);

        trail_head = NEXT(trail_head);
    }

    static void New(float[] spot) {
        if (!trail_active)
            return;

        Init();
        Add(spot);
    }

    static SubgameEntity PickFirst(SubgameEntity self) {

        if (!trail_active)
            return null;

        int marker = trail_head;

        for (int n = PlayerTrail.TRAIL_LENGTH; n > 0; n--) {
            if (PlayerTrail.trail[marker].timestamp <= self.monsterinfo.trail_time)
                marker = NEXT(marker);
            else
                break;
        }

        if (GameUtil.visible(self, PlayerTrail.trail[marker])) {
            return PlayerTrail.trail[marker];
        }

        if (GameUtil.visible(self, PlayerTrail.trail[PREV(marker)])) {
            return PlayerTrail.trail[PREV(marker)];
        }

        return PlayerTrail.trail[marker];
    }

    static SubgameEntity PickNext(SubgameEntity self) {

        if (!trail_active)
            return null;

        int marker;
        int n;
        for (marker = trail_head, n = PlayerTrail.TRAIL_LENGTH; n > 0; n--) {
            if (PlayerTrail.trail[marker].timestamp <= self.monsterinfo.trail_time)
                marker = NEXT(marker);
            else
                break;
        }

        return PlayerTrail.trail[marker];
    }

    static SubgameEntity LastSpot() {
        return PlayerTrail.trail[PREV(trail_head)];
    }
}