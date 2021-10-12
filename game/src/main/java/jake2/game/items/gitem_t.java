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

// Created on 20.11.2003 by RST.
package jake2.game.items;

import jake2.game.*;

import java.util.Map;

public class gitem_t {

    public int index;
    public String classname; // spawning name
    public EntInteractAdapter pickup;
    public ItemUseAdapter use;
    public ItemDropAdapter drop;
    public EntThinkAdapter weaponthink;
    public String pickup_sound;
    public String world_model;
    public int world_model_flags;
    public String view_model;
    public String icon;     // client side info
    public String pickup_name; // for printing on pickup
    public int count_width; // number of digits to display by icon
    public int quantity; // for ammo how much, for weapons how much is used per shot
    public String ammo; // for weapons
    public int flags; // IT_* flags
    public int weapmodel; // weapon model index (for weapons)
    public gitem_armor_t info;
    public int tag;
    public String precaches; // string of all models, sounds, and images this item will

    public gitem_t(String classname, EntInteractAdapter pickup,
                   ItemUseAdapter use, ItemDropAdapter drop,
                   EntThinkAdapter weaponthink, String pickup_sound,
                   String world_model, int world_model_flags, String view_model,
                   String icon, String pickup_name, int count_width, int quantity,
                   String ammo, int flags, int weapmodel, gitem_armor_t info, int tag,
                   String precaches, int index) {
        this.classname = classname;
        this.pickup = pickup;
        this.use = use;
        this.drop = drop;
        this.weaponthink = weaponthink;
        this.pickup_sound = pickup_sound;
        this.world_model = world_model;
        this.world_model_flags = world_model_flags;
        this.view_model = view_model;
        this.icon = icon;
        this.pickup_name = pickup_name;
        this.count_width = count_width;
        this.quantity = quantity;
        this.ammo = ammo;
        this.flags = flags;
        this.weapmodel = weapmodel;
        this.info = info;
        this.tag = tag;
        this.precaches = precaches;

        this.index = index;
    }

    @Override
    public String toString() {
        return "gitem_t{" +
                "index=" + index +
                ", classname='" + classname + '\'' +
                ", pickup=" + pickup +
                ", use=" + use +
                ", drop=" + drop +
                ", weaponthink=" + weaponthink +
                ", pickup_sound='" + pickup_sound + '\'' +
                ", world_model='" + world_model + '\'' +
                ", world_model_flags=" + world_model_flags +
                ", view_model='" + view_model + '\'' +
                ", icon='" + icon + '\'' +
                ", pickup_name='" + pickup_name + '\'' +
                ", count_width=" + count_width +
                ", quantity=" + quantity +
                ", ammo='" + ammo + '\'' +
                ", flags=" + flags +
                ", weapmodel=" + weapmodel +
                ", info=" + info +
                ", tag=" + tag +
                ", precaches='" + precaches + '\'' +
                '}';
    }

    public static gitem_t read(Map<String, String> params, int index) {
        return new gitem_t(
                params.get("classname"),
                (EntInteractAdapter) SuperAdapter.getFromID(params.get("pickup")),
                (ItemUseAdapter) SuperAdapter.getFromID(params.get("use")),
                (ItemDropAdapter) SuperAdapter.getFromID(params.get("drop")),
                (EntThinkAdapter) SuperAdapter.getFromID(params.get("weaponthink")),
                params.get("pickup_sound"),
                params.get("world_model"),
                0, //todo
                params.get("view_model"),
                params.get("icon"),
                params.get("pickup_name"),
                Integer.parseInt(params.get("count_width")),
                Integer.parseInt(params.get("quantity")),
                params.get("ammo"),
                readFlags(params.get("flags")),
                Integer.parseInt(params.get("weapmodel")),
                null, //todo
                Integer.parseInt(params.get("tag")), // why?
                params.get("precaches"),
                index
        );
    }

    private static int readFlags(String flags) {
        return 0; //todo
    }
}
