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

// Created on 28.12.2003 by RST.

package jake2.game;

import jake2.qcommon.Defines;
import jake2.qcommon.edict_t;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class PlayerHud {

    /*
     * ======================================================================
     * 
     * INTERMISSION
     * 
     * ======================================================================
     */

    public static void MoveClientToIntermission(SubgameEntity ent, GameExportsImpl gameExports) {
        gclient_t client = ent.getClient();
        if (gameExports.cvarCache.deathmatch.value != 0 || gameExports.cvarCache.coop.value != 0)
            client.showscores = true;
        Math3D.VectorCopy(gameExports.level.intermission_origin, ent.s.origin);
        client.getPlayerState().pmove.origin[0] = (short) (gameExports.level.intermission_origin[0] * 8);
        client.getPlayerState().pmove.origin[1] = (short) (gameExports.level.intermission_origin[1] * 8);
        client.getPlayerState().pmove.origin[2] = (short) (gameExports.level.intermission_origin[2] * 8);
        Math3D.VectorCopy(gameExports.level.intermission_angle,
                client.getPlayerState().viewangles);
        client.getPlayerState().pmove.pm_type = Defines.PM_FREEZE;
        client.getPlayerState().gunindex = 0;
        client.getPlayerState().blend[3] = 0;
        client.getPlayerState().rdflags &= ~Defines.RDF_UNDERWATER;

        // clean up powerup info
        client.quad_framenum = 0;
        client.invincible_framenum = 0;
        client.breather_framenum = 0;
        client.enviro_framenum = 0;
        client.grenade_blew_up = false;
        client.grenade_time = 0;

        ent.viewheight = 0;
        ent.s.modelindex = 0;
        ent.s.modelindex2 = 0;
        ent.s.modelindex3 = 0;
        ent.s.modelindex = 0;
        ent.s.effects = 0;
        ent.s.sound = 0;
        ent.solid = Defines.SOLID_NOT;

        // add the layout

        if (gameExports.cvarCache.deathmatch.value != 0 || gameExports.cvarCache.coop.value != 0) {
            DeathmatchScoreboardMessage(ent, null, gameExports);
            gameExports.gameImports.unicast(ent, true);
        }

    }

    public static void BeginIntermission(SubgameEntity targ, GameExportsImpl gameExports) {

        if (gameExports.level.intermissiontime != 0)
            return; // already activated

        gameExports.game.autosaved = false;

        // respawn any dead clients
        int i;
        SubgameEntity client;
        for (i = 0; i < gameExports.game.maxclients; i++) {
            client = gameExports.g_edicts[1 + i];
            if (!client.inuse)
                continue;
            if (client.health <= 0)
                PlayerClient.respawn(client, gameExports);
        }

        gameExports.level.intermissiontime = gameExports.level.time;
        gameExports.level.changemap = targ.map;

        if (gameExports.level.changemap.indexOf('*') > -1) {
            if (gameExports.cvarCache.coop.value != 0) {
                for (i = 0; i < gameExports.game.maxclients; i++) {
                    client = gameExports.g_edicts[1 + i];
                    if (!client.inuse)
                        continue;
                    // strip players of all keys between units
                    for (int n = 1; n < gameExports.items.itemlist.length; n++) {
                        // null pointer exception fixed. (RST) 
                        if (gameExports.items.itemlist[n] != null)
                            if ((gameExports.items.itemlist[n].flags & GameDefines.IT_KEY) != 0) {
                                gclient_t otherClient = client.getClient();
                                otherClient.pers.inventory[n] = 0;
                            }
                    }
                }
            }
        } else {
            if (0 == gameExports.cvarCache.deathmatch.value) {
                gameExports.level.exitintermission = true; // go immediately to the
                                                        // next level
                return;
            }
        }

        gameExports.level.exitintermission = false;

        // find an intermission spot
        edict_t ent = GameBase.G_FindEdict(null, GameBase.findByClass,
                "info_player_intermission", gameExports);
        if (ent == null) { // the map creator forgot to put in an intermission
                           // point...
            ent = GameBase.G_FindEdict(null, GameBase.findByClass,
                    "info_player_start", gameExports);
            if (ent == null)
                ent = GameBase.G_FindEdict(null, GameBase.findByClass,
                        "info_player_deathmatch", gameExports);
        } else { // chose one of four spots
            i = Lib.rand() & 3;
            EdictIterator es = null;

            while (i-- > 0) {
                es = GameBase.G_Find(es, GameBase.findByClass,
                        "info_player_intermission", gameExports);

                if (es == null) // wrap around the list
                    continue;
                ent = es.o;
            }
        }

        Math3D.VectorCopy(ent.s.origin, gameExports.level.intermission_origin);
        Math3D.VectorCopy(ent.s.angles, gameExports.level.intermission_angle);

        // move all clients to the intermission point
        for (i = 0; i < gameExports.game.maxclients; i++) {
            client = gameExports.g_edicts[1 + i];
            if (!client.inuse)
                continue;
            MoveClientToIntermission(client, gameExports);
        }
    }

    /*
     * ================== 
     * DeathmatchScoreboardMessage
     * ==================
     */
    public static void DeathmatchScoreboardMessage(edict_t ent, edict_t killer, GameExportsImpl gameExports) {
        StringBuffer string = new StringBuffer(1400);

        int stringlength;
        int i, j, k;
        int sorted[] = new int[Defines.MAX_CLIENTS];
        int sortedscores[] = new int[Defines.MAX_CLIENTS];
        int score, total;
        int picnum;
        int x, y;
        gclient_t cl;
        edict_t cl_ent;
        String tag;

        // sort the clients by score
        total = 0;
        for (i = 0; i < gameExports.game.maxclients; i++) {
            cl_ent = gameExports.g_edicts[1 + i];
            if (!cl_ent.inuse || gameExports.game.clients[i].resp.spectator)
                continue;
            score = gameExports.game.clients[i].resp.score;
            for (j = 0; j < total; j++) {
                if (score > sortedscores[j])
                    break;
            }
            for (k = total; k > j; k--) {
                sorted[k] = sorted[k - 1];
                sortedscores[k] = sortedscores[k - 1];
            }
            sorted[j] = i;
            sortedscores[j] = score;
            total++;
        }

        // print level name and exit rules

        // add the clients in sorted order
        if (total > 12)
            total = 12;
        
        for (i = 0; i < total; i++) {
            cl = gameExports.game.clients[sorted[i]];
            cl_ent = gameExports.g_edicts[1 + sorted[i]];

            picnum = gameExports.gameImports.imageindex("i_fixme");
            x = (i >= 6) ? 160 : 0;
            y = 32 + 32 * (i % 6);

            // add a dogtag
            if (cl_ent == ent)
                tag = "tag1";
            else if (cl_ent == killer)
                tag = "tag2";
            else
                tag = null;

            if (tag != null) {
                string.append("xv ").append(x + 32).append(" yv ").append(y)
                        .append(" picn ").append(tag);
            }

            // send the layout
            string
                    .append(" client ")
                    .append(x)
                    .append(" ")
                    .append(y)
                    .append(" ")
                    .append(sorted[i])
                    .append(" ")
                    .append(cl.resp.score)
                    .append(" ")
                    .append(cl.getPing())
                    .append(" ")
                    .append(
                            (gameExports.level.framenum - cl.resp.enterframe) / 600);
        }

        gameExports.gameImports.WriteByte(NetworkCommands.svc_layout);
        gameExports.gameImports.WriteString(string.toString());
    }

    /*
     * ================== 
     * DeathmatchScoreboard
     * 
     * Draw instead of help message. Note that it isn't that hard to overflow
     * the 1400 byte message limit! 
     * ==================
     */
    public static void DeathmatchScoreboard(SubgameEntity ent, GameExportsImpl gameExports) {
        DeathmatchScoreboardMessage(ent, ent.enemy, gameExports);
        gameExports.gameImports.unicast(ent, true);
    }

    /*
     * =============== 
     * G_SetStats 
     * ===============
     */
    public static void G_SetStats(SubgameEntity ent, GameExportsImpl gameExports) {
        gitem_t item;
        int index, cells = 0;
        int power_armor_type;

        //
        // health
        //
        gclient_t client = ent.getClient();
        client.getPlayerState().stats[Defines.STAT_HEALTH_ICON] = (short) gameExports.level.pic_health;
        client.getPlayerState().stats[Defines.STAT_HEALTH] = (short) ent.health;

        //
        // ammo
        //
        if (0 == client.ammo_index /*
                                        * ||
                                        * !ent.client.pers.inventory[ent.client.ammo_index]
                                        */
        ) {
            client.getPlayerState().stats[Defines.STAT_AMMO_ICON] = 0;
            client.getPlayerState().stats[Defines.STAT_AMMO] = 0;
        } else {
            item = gameExports.items.itemlist[client.ammo_index];
            client.getPlayerState().stats[Defines.STAT_AMMO_ICON] = (short) gameExports.gameImports
                    .imageindex(item.icon);
            client.getPlayerState().stats[Defines.STAT_AMMO] = (short) client.pers.inventory[client.ammo_index];
        }

        //
        // armor
        //
        power_armor_type = GameItems.PowerArmorType(ent, gameExports);
        if (power_armor_type != 0) {
            cells = client.pers.inventory[GameItems
                    .FindItem("cells", gameExports).index];
            if (cells == 0) { // ran out of cells for power armor
                ent.flags &= ~GameDefines.FL_POWER_ARMOR;
                gameExports.gameImports
                        .sound(ent, Defines.CHAN_ITEM, gameExports.gameImports
                                .soundindex("misc/power2.wav"), 1,
                                Defines.ATTN_NORM, 0);
                power_armor_type = 0;
                ;
            }
        }

        index = GameItems.ArmorIndex(ent, gameExports);
        if (power_armor_type != 0
                && (0 == index || 0 != (gameExports.level.framenum & 8))) { // flash
                                                                         // between
                                                                         // power
                                                                         // armor
                                                                         // and
                                                                         // other
                                                                         // armor
                                                                         // icon
            client.getPlayerState().stats[Defines.STAT_ARMOR_ICON] = (short) gameExports.gameImports
                    .imageindex("i_powershield");
            client.getPlayerState().stats[Defines.STAT_ARMOR] = (short) cells;
        } else if (index != 0) {
            item = GameItems.GetItemByIndex(index, gameExports);
            client.getPlayerState().stats[Defines.STAT_ARMOR_ICON] = (short) gameExports.gameImports
                    .imageindex(item.icon);
            client.getPlayerState().stats[Defines.STAT_ARMOR] = (short) client.pers.inventory[index];
        } else {
            client.getPlayerState().stats[Defines.STAT_ARMOR_ICON] = 0;
            client.getPlayerState().stats[Defines.STAT_ARMOR] = 0;
        }

        //
        // pickup message
        //
        if (gameExports.level.time > client.pickup_msg_time) {
            client.getPlayerState().stats[Defines.STAT_PICKUP_ICON] = 0;
            client.getPlayerState().stats[Defines.STAT_PICKUP_STRING] = 0;
        }

        //
        // timers
        //
        if (client.quad_framenum > gameExports.level.framenum) {
            client.getPlayerState().stats[Defines.STAT_TIMER_ICON] = (short) gameExports.gameImports
                    .imageindex("p_quad");
            client.getPlayerState().stats[Defines.STAT_TIMER] = (short) ((client.quad_framenum - gameExports.level.framenum) / 10);
        } else if (client.invincible_framenum > gameExports.level.framenum) {
            client.getPlayerState().stats[Defines.STAT_TIMER_ICON] = (short) gameExports.gameImports
                    .imageindex("p_invulnerability");
            client.getPlayerState().stats[Defines.STAT_TIMER] = (short) ((client.invincible_framenum - gameExports.level.framenum) / 10);
        } else if (client.enviro_framenum > gameExports.level.framenum) {
            client.getPlayerState().stats[Defines.STAT_TIMER_ICON] = (short) gameExports.gameImports
                    .imageindex("p_envirosuit");
            client.getPlayerState().stats[Defines.STAT_TIMER] = (short) ((client.enviro_framenum - gameExports.level.framenum) / 10);
        } else if (client.breather_framenum > gameExports.level.framenum) {
            client.getPlayerState().stats[Defines.STAT_TIMER_ICON] = (short) gameExports.gameImports
                    .imageindex("p_rebreather");
            client.getPlayerState().stats[Defines.STAT_TIMER] = (short) ((client.breather_framenum - gameExports.level.framenum) / 10);
        } else {
            client.getPlayerState().stats[Defines.STAT_TIMER_ICON] = 0;
            client.getPlayerState().stats[Defines.STAT_TIMER] = 0;
        }

        //
        // selected item
        //
        // bugfix rst
        if (client.pers.selected_item <= 0)
            client.getPlayerState().stats[Defines.STAT_SELECTED_ICON] = 0;
        else
            client.getPlayerState().stats[Defines.STAT_SELECTED_ICON] = (short) gameExports.gameImports
                    .imageindex(gameExports.items.itemlist[client.pers.selected_item].icon);

        client.getPlayerState().stats[Defines.STAT_SELECTED_ITEM] = (short) client.pers.selected_item;

        //
        // layouts
        //
        client.getPlayerState().stats[Defines.STAT_LAYOUTS] = 0;

        if (gameExports.cvarCache.deathmatch.value != 0) {
            if (client.pers.health <= 0
                    || gameExports.level.intermissiontime != 0
                    || client.showscores)
                client.getPlayerState().stats[Defines.STAT_LAYOUTS] |= 1;
            if (client.showinventory && client.pers.health > 0)
                client.getPlayerState().stats[Defines.STAT_LAYOUTS] |= 2;
        } else {
            if (client.showscores || client.showhelp)
                client.getPlayerState().stats[Defines.STAT_LAYOUTS] |= 1;
            if (client.showinventory && client.pers.health > 0)
                client.getPlayerState().stats[Defines.STAT_LAYOUTS] |= 2;
        }

        //
        // frags
        //
        client.getPlayerState().stats[Defines.STAT_FRAGS] = (short) client.resp.score;

        //
        // help icon / current weapon if not shown
        //
        if (client.pers.helpchanged != 0
                && (gameExports.level.framenum & 8) != 0)
            client.getPlayerState().stats[Defines.STAT_HELPICON] = (short) gameExports.gameImports
                    .imageindex("i_help");
        else if ((client.pers.hand == Defines.CENTER_HANDED || client.getPlayerState().fov > 91)
                && client.pers.weapon != null)
            client.getPlayerState().stats[Defines.STAT_HELPICON] = (short) gameExports.gameImports
                    .imageindex(client.pers.weapon.icon);
        else
            client.getPlayerState().stats[Defines.STAT_HELPICON] = 0;

        client.getPlayerState().stats[Defines.STAT_SPECTATOR] = 0;
    }

    /*
     * =============== 
     * G_CheckChaseStats 
     * ===============
     */
    public static void G_CheckChaseStats(edict_t ent, GameExportsImpl gameExports) {

        for (int i = 1; i <= gameExports.game.maxclients; i++) {
            gclient_t cl = gameExports.g_edicts[i].getClient();
            if (!gameExports.g_edicts[i].inuse || cl.chase_target != ent)
                continue;
            //memcpy(cl.ps.stats, ent.client.ps.stats, sizeof(cl.ps.stats));
            System.arraycopy(ent.getClient().getPlayerState().stats, 0, cl.getPlayerState().stats, 0,
                    Defines.MAX_STATS);

            G_SetSpectatorStats(gameExports.g_edicts[i], gameExports);
        }
    }

    /*
     * =============== 
     * G_SetSpectatorStats 
     * ===============
     */
    public static void G_SetSpectatorStats(SubgameEntity ent, GameExportsImpl gameExports) {
        gclient_t cl = ent.getClient();

        if (null == cl.chase_target)
            G_SetStats(ent, gameExports);

        cl.getPlayerState().stats[Defines.STAT_SPECTATOR] = 1;

        // layouts are independant in spectator
        cl.getPlayerState().stats[Defines.STAT_LAYOUTS] = 0;
        if (cl.pers.health <= 0 || gameExports.level.intermissiontime != 0
                || cl.showscores)
            cl.getPlayerState().stats[Defines.STAT_LAYOUTS] |= 1;
        if (cl.showinventory && cl.pers.health > 0)
            cl.getPlayerState().stats[Defines.STAT_LAYOUTS] |= 2;

        if (cl.chase_target != null && cl.chase_target.inuse)
            //cl.ps.stats[STAT_CHASE] = (short) (CS_PLAYERSKINS +
            // (cl.chase_target - g_edicts) - 1);
            cl.getPlayerState().stats[Defines.STAT_CHASE] = (short) (Defines.CS_PLAYERSKINS
                    + cl.chase_target.index - 1);
        else
            cl.getPlayerState().stats[Defines.STAT_CHASE] = 0;
    }

}