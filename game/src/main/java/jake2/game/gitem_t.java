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
package jake2.game;

public class gitem_t {
    private static int id = 0;

    public gitem_t(int xxx) {
        index = xxx;
    }

    public gitem_t(String classname, EntInteractAdapter pickup,
            ItemUseAdapter use, ItemDropAdapter drop,
            EntThinkAdapter weaponthink) {
    }

    public gitem_t(String classname, EntInteractAdapter pickup,
            ItemUseAdapter use, ItemDropAdapter drop,
            EntThinkAdapter weaponthink, String pickup_sound,
            String world_model, int world_model_flags, String view_model,
            String icon, String pickup_name, int count_width, int quantity,
            String ammo, int flags, int weapmodel, gitem_armor_t info, int tag,
            String precaches) {
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

        this.index = id++;
    }

    String classname; // spawning name

    EntInteractAdapter pickup;

    ItemUseAdapter use;

    ItemDropAdapter drop;

    EntThinkAdapter weaponthink;

    String pickup_sound;

    String world_model;

    int world_model_flags;

    String view_model;

    // client side info
    String icon;

    String pickup_name; // for printing on pickup

    int count_width; // number of digits to display by icon

    int quantity; // for ammo how much, for weapons how much is used per shot

    String ammo; // for weapons

    int flags; // IT_* flags

    int weapmodel; // weapon model index (for weapons)

    gitem_armor_t info;

    int tag;

    String precaches; // string of all models, sounds, and images this item will
                      // use

    public int index;
}