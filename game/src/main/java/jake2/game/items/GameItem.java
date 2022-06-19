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
import jake2.game.adapters.*;
import org.apache.commons.csv.CSVRecord;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static jake2.game.GameDefines.*;
import static jake2.qcommon.Defines.*;

/**
 * gitem_t
 */
public class GameItem {

    public int index;
    public String classname; // spawning name
    public EntInteractAdapter pickup;
    public ItemUseAdapter use;
    public ItemDropAdapter drop;
    public EntThinkAdapter weaponthink;
    public String pickup_sound;
    public String world_model;
    public int world_model_flags; // usually Defines.EF_ROTATE, but can be EF_GIB
    public String view_model;
    public String icon;     // client side info
    public String pickup_name; // for printing on pickup
    public int count_width; // number of digits to display by icon
    public int quantity; // for ammo how much, for weapons how much is used per shot
    public String ammo; // for weapons, pickup_name of the ammo item
    public int flags; // IT_* flags
    public int weapmodel; // weapon model index (for weapons)
    public gitem_armor_t info;
    public String precaches; // string of all models, sounds, and images this item will

    private static final Map<String, Integer> ITEM_TYPE_MAP = Map.of(
            "IT_WEAPON", IT_WEAPON,
            "IT_AMMO", IT_AMMO,
            "IT_ARMOR", IT_ARMOR,
            "IT_STAY_COOP", IT_STAY_COOP,
            "IT_KEY", IT_KEY,
            "IT_POWERUP", IT_POWERUP
    );

    private static final Map<String, Integer> EFFECTS_MAP = createEffectsMap();

    private static Map<String, Integer> createEffectsMap() {
        final HashMap<String, Integer> result = new HashMap<>();
        result.put("EF_ROTATE", EF_ROTATE);
        result.put("EF_GIB", EF_GIB);
        result.put("EF_BLASTER", EF_BLASTER);
        result.put("EF_ROCKET", EF_ROCKET);
        result.put("EF_GRENADE", EF_GRENADE);
        result.put("EF_HYPERBLASTER", EF_HYPERBLASTER);
        result.put("EF_BFG", EF_BFG);
        result.put("EF_COLOR_SHELL", EF_COLOR_SHELL);
        result.put("EF_POWERSCREEN", EF_POWERSCREEN);
        result.put("EF_ANIM01", EF_ANIM01);
        result.put("EF_ANIM23", EF_ANIM23);
        result.put("EF_ANIM_ALL", EF_ANIM_ALL);
        result.put("EF_ANIM_ALLFAST", EF_ANIM_ALLFAST);
        result.put("EF_FLIES", EF_FLIES);
        result.put("EF_QUAD", EF_QUAD);
        result.put("EF_PENT", EF_PENT);
        result.put("EF_TELEPORTER", EF_TELEPORTER);
        result.put("EF_FLAG1", EF_FLAG1);
        result.put("EF_FLAG2", EF_FLAG2);
        result.put("EF_IONRIPPER", EF_IONRIPPER);
        result.put("EF_GREENGIB", EF_GREENGIB);
        result.put("EF_BLUEHYPERBLASTER", EF_BLUEHYPERBLASTER);
        result.put("EF_SPINNINGLIGHTS", EF_SPINNINGLIGHTS);
        result.put("EF_PLASMA", EF_PLASMA);
        result.put("EF_TRAP", EF_TRAP);
        result.put("EF_TRACKER", EF_TRACKER);
        result.put("EF_DOUBLE", EF_DOUBLE);
        result.put("EF_SPHERETRANS", EF_SPHERETRANS);
        result.put("EF_TAGTRAIL", EF_TAGTRAIL);
        result.put("EF_HALF_DAMAGE", EF_HALF_DAMAGE);
        result.put("EF_TRACKERTRAIL", EF_TRACKERTRAIL);
        return Collections.unmodifiableMap(result);
    }

    public GameItem(String classname, EntInteractAdapter pickup,
                    ItemUseAdapter use, ItemDropAdapter drop,
                    EntThinkAdapter weaponthink, String pickup_sound,
                    String world_model, int world_model_flags, String view_model,
                    String icon, String pickup_name, int count_width, int quantity,
                    String ammo, int flags, int weapmodel, gitem_armor_t info,
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
        this.precaches = precaches;
        this.index = index;
    }

    /**
     * Translate a bit flag expression like `EF_ROTATE|EF_QUAD` into integer
     *
     * @param input - input expression
     * @param dict
     * @return
     */
    static int parseFlags(String input, Map<String, Integer> dict) {
        if (input == null || input.isBlank())
            return 0;

        if (dict == null || dict.isEmpty())
            throw new IllegalArgumentException("dict cannot be empty");

        if (input.contains("|")) {
            int result = 0;
            String[] tags = input.split("\\|");
            for (String tag : tags) {
                if (!dict.containsKey(tag)) {
                    throw new IllegalArgumentException("Unknown flag: " + tag + " in input: " + input);
                }
                result |= dict.get(tag);
            }
            return result;
        } else {
            if (!dict.containsKey(input)) {
                throw new IllegalArgumentException("Unknown flag " + input);
            }
            return dict.get(input);
        }
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
                ", precaches='" + precaches + '\'' +
                '}';
    }

    // todo: parse flag values properly
    public static GameItem readFromCsv(CSVRecord params, int index) {
        return new GameItem(
                params.get("classname").isBlank() ? null : params.get("classname"),
                (EntInteractAdapter) SuperAdapter.getFromID(params.get("pickup")),
                (ItemUseAdapter) SuperAdapter.getFromID(params.get("use")),
                (ItemDropAdapter) SuperAdapter.getFromID(params.get("drop")),
                (EntThinkAdapter) SuperAdapter.getFromID(params.get("weaponthink")),
                params.get("pickup_sound"),
                params.get("world_model").isBlank() ? null : params.get("world_model"),
                parseFlags(params.get("world_model_flags"), EFFECTS_MAP), //todo
                params.get("view_model").isBlank() ? null : params.get("view_model"),
                params.get("icon"),
                params.get("pickup_name"),
                Integer.parseInt(params.get("count_width")),
                Integer.parseInt(params.get("quantity")),
                params.get("ammo").isBlank() ? null : params.get("ammo"),
                parseFlags(params.get("flags"), ITEM_TYPE_MAP),
                Integer.parseInt(params.get("weapmodel")),
                GameItems.TYPES.get(params.get("armor_info")), //todo
                params.get("precaches"),
                index
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameItem gitem_t = (GameItem) o;

        if (index != gitem_t.index) return false;
        if (world_model_flags != gitem_t.world_model_flags) return false;
        if (count_width != gitem_t.count_width) return false;
        if (quantity != gitem_t.quantity) return false;
        if (flags != gitem_t.flags) return false;
        if (weapmodel != gitem_t.weapmodel) return false;
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
        result = 31 * result + (precaches != null ? precaches.hashCode() : 0);
        return result;
    }
}
