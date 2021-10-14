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
import org.apache.commons.csv.CSVRecord;

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

    public static gitem_t readFromCsv(CSVRecord params, int index) {
        return new gitem_t(
                params.get("classname"),
                (EntInteractAdapter) SuperAdapter.getFromID(params.get("pickup")),
                (ItemUseAdapter) SuperAdapter.getFromID(params.get("use")),
                (ItemDropAdapter) SuperAdapter.getFromID(params.get("drop")),
                (EntThinkAdapter) SuperAdapter.getFromID(params.get("weaponthink")),
                params.get("pickup_sound"),
                params.get("world_model").isBlank() ? null : params.get("world_model"),
                Integer.parseInt(params.get("world_model_flags")), //todo
                params.get("view_model").isBlank() ? null : params.get("view_model"),
                params.get("icon"),
                params.get("pickup_name"),
                Integer.parseInt(params.get("count_width")),
                Integer.parseInt(params.get("quantity")),
                params.get("ammo").isBlank() ? null : params.get("ammo"),
                Integer.parseInt(params.get("flags")), // todo
                Integer.parseInt(params.get("weapmodel")),
                GameItems.TYPES.get(params.get("armor_info")), //todo
                Integer.parseInt(params.get("tag")), //todo
                params.get("precaches"),
                index
        );
    }

    private static int readFlags(String flags) {
        return 0; //todo
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        gitem_t gitem_t = (gitem_t) o;

        if (index != gitem_t.index) return false;
        if (world_model_flags != gitem_t.world_model_flags) return false;
        if (count_width != gitem_t.count_width) return false;
        if (quantity != gitem_t.quantity) return false;
        if (flags != gitem_t.flags) return false;
        if (weapmodel != gitem_t.weapmodel) return false;
        if (tag != gitem_t.tag) return false;
        if (classname != null ? !classname.equals(gitem_t.classname) : gitem_t.classname != null) return false;
        if (pickup != null ? !pickup.equals(gitem_t.pickup) : gitem_t.pickup != null) return false;
        if (use != null ? !use.equals(gitem_t.use) : gitem_t.use != null) return false;
        if (drop != null ? !drop.equals(gitem_t.drop) : gitem_t.drop != null) return false;
        if (weaponthink != null ? !weaponthink.equals(gitem_t.weaponthink) : gitem_t.weaponthink != null) return false;
        if (pickup_sound != null ? !pickup_sound.equals(gitem_t.pickup_sound) : gitem_t.pickup_sound != null)
            return false;
        if (world_model != null ? !world_model.equals(gitem_t.world_model) : gitem_t.world_model != null) return false;
        if (view_model != null ? !view_model.equals(gitem_t.view_model) : gitem_t.view_model != null) return false;
        if (icon != null ? !icon.equals(gitem_t.icon) : gitem_t.icon != null) return false;
        if (pickup_name != null ? !pickup_name.equals(gitem_t.pickup_name) : gitem_t.pickup_name != null) return false;
        if (ammo != null ? !ammo.equals(gitem_t.ammo) : gitem_t.ammo != null) return false;
        if (info != null ? !info.equals(gitem_t.info) : gitem_t.info != null) return false;
        return precaches != null ? precaches.equals(gitem_t.precaches) : gitem_t.precaches == null;
    }

    @Override
    public int hashCode() {
        int result = index;
        result = 31 * result + (classname != null ? classname.hashCode() : 0);
        result = 31 * result + (pickup != null ? pickup.hashCode() : 0);
        result = 31 * result + (use != null ? use.hashCode() : 0);
        result = 31 * result + (drop != null ? drop.hashCode() : 0);
        result = 31 * result + (weaponthink != null ? weaponthink.hashCode() : 0);
        result = 31 * result + (pickup_sound != null ? pickup_sound.hashCode() : 0);
        result = 31 * result + (world_model != null ? world_model.hashCode() : 0);
        result = 31 * result + world_model_flags;
        result = 31 * result + (view_model != null ? view_model.hashCode() : 0);
        result = 31 * result + (icon != null ? icon.hashCode() : 0);
        result = 31 * result + (pickup_name != null ? pickup_name.hashCode() : 0);
        result = 31 * result + count_width;
        result = 31 * result + quantity;
        result = 31 * result + (ammo != null ? ammo.hashCode() : 0);
        result = 31 * result + flags;
        result = 31 * result + weapmodel;
        result = 31 * result + (info != null ? info.hashCode() : 0);
        result = 31 * result + tag;
        result = 31 * result + (precaches != null ? precaches.hashCode() : 0);
        return result;
    }
}
