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

import jake2.qcommon.util.Math3D;

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
public class PlayerTrail {

    int TRAIL_LENGTH;
    SubgameEntity[] trail;
    int trail_head;
    boolean trail_active;
    private final GameExportsImpl gameExports;

    public PlayerTrail(GameExportsImpl gameExports) {
        this.gameExports = gameExports;
        TRAIL_LENGTH = 16;
        trail = new SubgameEntity[TRAIL_LENGTH];
        trail_active = false;
    }

    int NEXT(int n) {
        return (n + 1) % TRAIL_LENGTH;
    }

    int PREV(int n) {
        return (n + TRAIL_LENGTH - 1) % TRAIL_LENGTH;
    }

    // todo: should just part of the constructor?
    void Init() {

        // FIXME || coop
        if (this.gameExports.gameCvars.deathmatch.value != 0)
            return;

        for (int n = 0; n < TRAIL_LENGTH; n++) {
            trail[n] = GameUtil.G_Spawn(this.gameExports);
            trail[n].classname = "player_trail";
        }

        trail_head = 0;
        trail_active = true;
    }

    void Add(float[] spot, float time) {
        float[] temp = { 0, 0, 0 };

        if (!trail_active)
            return;

        Math3D.VectorCopy(spot, trail[trail_head].s.origin);

        trail[trail_head].timestamp = time;

        Math3D.VectorSubtract(spot, trail[PREV(trail_head)].s.origin, temp);
        trail[trail_head].s.angles[1] = Math3D.vectoyaw(temp);

        trail_head = NEXT(trail_head);
    }

    SubgameEntity PickFirst(SubgameEntity self) {

        if (!trail_active)
            return null;

        int marker = trail_head;

        for (int n = TRAIL_LENGTH; n > 0; n--) {
            if (trail[marker].timestamp <= self.monsterinfo.trail_time)
                marker = NEXT(marker);
            else
                break;
        }

        if (GameUtil.visible(self, trail[marker], this.gameExports)) {
            return trail[marker];
        }

        if (GameUtil.visible(self, trail[PREV(marker)], this.gameExports)) {
            return trail[PREV(marker)];
        }

        return trail[marker];
    }

    SubgameEntity PickNext(SubgameEntity self) {

        if (!trail_active)
            return null;

        int marker;
        int n;
        for (marker = trail_head, n = TRAIL_LENGTH; n > 0; n--) {
            if (trail[marker].timestamp <= self.monsterinfo.trail_time)
                marker = NEXT(marker);
            else
                break;
        }

        return trail[marker];
    }

    SubgameEntity LastSpot() {
        return trail[PREV(trail_head)];
    }
}