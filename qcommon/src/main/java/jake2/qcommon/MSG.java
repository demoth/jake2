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

// Created on 29.11.2003 by RST.
// $Id: MSG.java,v 1.8 2005-12-18 22:10:02 cawe Exp $
package jake2.qcommon;

public class MSG extends Globals {

    /*
     * ================== WriteDeltaEntity
     * 
     * Writes part of a packetentities message. Can delta from either a baseline
     * or a previous packet_entity ==================
     */
    public static void WriteDeltaEntity(entity_state_t from, entity_state_t to,
            sizebuf_t buffer, boolean force, boolean newentity) {

        int deltaFlags = getDeltaFlags(from, to, newentity);

        //
        // write the message
        //
        if (deltaFlags == 0 && !force)
            return; // nothing to send!

        sizebuf_t.WriteByte(buffer, (byte) (deltaFlags & 255));

        if ((deltaFlags & 0xff000000) != 0) {
            sizebuf_t.WriteByte(buffer, (byte) (deltaFlags >>> 8 & 255));
            sizebuf_t.WriteByte(buffer, (byte) (deltaFlags >>> 16 & 255));
            sizebuf_t.WriteByte(buffer, (byte) (deltaFlags >>> 24 & 255));
        } else if ((deltaFlags & 0x00ff0000) != 0) {
            sizebuf_t.WriteByte(buffer, (byte) (deltaFlags >>> 8 & 255));
            sizebuf_t.WriteByte(buffer, (byte) (deltaFlags >>> 16 & 255));
        } else if ((deltaFlags & 0x0000ff00) != 0) {
            sizebuf_t.WriteByte(buffer, (byte) (deltaFlags >>> 8 & 255));
        }

        //----------

        if ((deltaFlags & U_NUMBER16) != 0)
            buffer.WriteShort(to.number);
        else
            sizebuf_t.WriteByte(buffer, (byte) to.number);

        if ((deltaFlags & U_MODEL) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.modelindex);
        if ((deltaFlags & U_MODEL2) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.modelindex2);
        if ((deltaFlags & U_MODEL3) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.modelindex3);
        if ((deltaFlags & U_MODEL4) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.modelindex4);

        if ((deltaFlags & U_FRAME8) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.frame);
        if ((deltaFlags & U_FRAME16) != 0)
            buffer.WriteShort(to.frame);

        if ((deltaFlags & U_SKIN8) != 0 && (deltaFlags & U_SKIN16) != 0) //used for laser
                                                             // colors
            sizebuf_t.WriteInt(buffer, to.skinnum);
        else if ((deltaFlags & U_SKIN8) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.skinnum);
        else if ((deltaFlags & U_SKIN16) != 0)
            buffer.WriteShort(to.skinnum);

        if ((deltaFlags & (U_EFFECTS8 | U_EFFECTS16)) == (U_EFFECTS8 | U_EFFECTS16))
            sizebuf_t.WriteInt(buffer, to.effects);
        else if ((deltaFlags & U_EFFECTS8) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.effects);
        else if ((deltaFlags & U_EFFECTS16) != 0)
            buffer.WriteShort(to.effects);

        if ((deltaFlags & (U_RENDERFX8 | U_RENDERFX16)) == (U_RENDERFX8 | U_RENDERFX16))
            sizebuf_t.WriteInt(buffer, to.renderfx);
        else if ((deltaFlags & U_RENDERFX8) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.renderfx);
        else if ((deltaFlags & U_RENDERFX16) != 0)
            buffer.WriteShort(to.renderfx);

        if ((deltaFlags & U_ORIGIN1) != 0)
            sizebuf_t.WriteCoord(buffer, to.origin[0]);
        if ((deltaFlags & U_ORIGIN2) != 0)
            sizebuf_t.WriteCoord(buffer, to.origin[1]);
        if ((deltaFlags & U_ORIGIN3) != 0)
            sizebuf_t.WriteCoord(buffer, to.origin[2]);

        if ((deltaFlags & U_ANGLE1) != 0)
            sizebuf_t.WriteAngle(buffer, to.angles[0]);
        if ((deltaFlags & U_ANGLE2) != 0)
            sizebuf_t.WriteAngle(buffer, to.angles[1]);
        if ((deltaFlags & U_ANGLE3) != 0)
            sizebuf_t.WriteAngle(buffer, to.angles[2]);

        if ((deltaFlags & U_OLDORIGIN) != 0) {
            sizebuf_t.WriteCoord(buffer, to.old_origin[0]);
            sizebuf_t.WriteCoord(buffer, to.old_origin[1]);
            sizebuf_t.WriteCoord(buffer, to.old_origin[2]);
        }

        if ((deltaFlags & U_SOUND) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.sound);
        if ((deltaFlags & U_EVENT) != 0)
            sizebuf_t.WriteByte(buffer, (byte) to.event);
        if ((deltaFlags & U_SOLID) != 0)
            buffer.WriteShort(to.solid);
    }

    private static int getDeltaFlags(entity_state_t from, entity_state_t to, boolean newentity) {
        if (0 == to.number)
            Com.Error(ERR_FATAL, "Unset entity number");
        if (to.number >= MAX_EDICTS)
            Com.Error(ERR_FATAL, "Entity number >= MAX_EDICTS");

        // send an update
        int deltaFlags = 0;

        if (to.number >= 256)
            deltaFlags |= U_NUMBER16; // number8 is implicit otherwise

        if (to.origin[0] != from.origin[0])
            deltaFlags |= U_ORIGIN1;
        if (to.origin[1] != from.origin[1])
            deltaFlags |= U_ORIGIN2;
        if (to.origin[2] != from.origin[2])
            deltaFlags |= U_ORIGIN3;

        if (to.angles[0] != from.angles[0])
            deltaFlags |= U_ANGLE1;
        if (to.angles[1] != from.angles[1])
            deltaFlags |= U_ANGLE2;
        if (to.angles[2] != from.angles[2])
            deltaFlags |= U_ANGLE3;

        if (to.skinnum != from.skinnum) {
            if (to.skinnum < 256)
                deltaFlags |= U_SKIN8;
            else if (to.skinnum < 0x10000)
                deltaFlags |= U_SKIN16;
            else
                deltaFlags |= U_SKIN8 | U_SKIN16;
        }

        if (to.frame != from.frame) {
            if (to.frame < 256)
                deltaFlags |= U_FRAME8;
            else
                deltaFlags |= U_FRAME16;
        }

        if (to.effects != from.effects) {
            if (to.effects < 256)
                deltaFlags |= U_EFFECTS8;
            else if (to.effects < 0x8000)
                deltaFlags |= U_EFFECTS16;
            else
                deltaFlags |= U_EFFECTS8 | U_EFFECTS16;
        }

        if (to.renderfx != from.renderfx) {
            if (to.renderfx < 256)
                deltaFlags |= U_RENDERFX8;
            else if (to.renderfx < 0x8000)
                deltaFlags |= U_RENDERFX16;
            else
                deltaFlags |= U_RENDERFX8 | U_RENDERFX16;
        }

        if (to.solid != from.solid)
            deltaFlags |= U_SOLID;

        // event is not delta compressed, just 0 compressed
        if (to.event != 0)
            deltaFlags |= U_EVENT;

        if (to.modelindex != from.modelindex)
            deltaFlags |= U_MODEL;
        if (to.modelindex2 != from.modelindex2)
            deltaFlags |= U_MODEL2;
        if (to.modelindex3 != from.modelindex3)
            deltaFlags |= U_MODEL3;
        if (to.modelindex4 != from.modelindex4)
            deltaFlags |= U_MODEL4;

        if (to.sound != from.sound)
            deltaFlags |= U_SOUND;

        if (newentity || (to.renderfx & RF_BEAM) != 0)
            deltaFlags |= U_OLDORIGIN;

        //----------

        if ((deltaFlags & 0xff000000) != 0)
            deltaFlags |= U_MOREBITS3 | U_MOREBITS2 | U_MOREBITS1;
        else if ((deltaFlags & 0x00ff0000) != 0)
            deltaFlags |= U_MOREBITS2 | U_MOREBITS1;
        else if ((deltaFlags & 0x0000ff00) != 0)
            deltaFlags |= U_MOREBITS1;
        return deltaFlags;
    }

    public static int getDeltaSize(entity_state_t from, entity_state_t to, boolean newentity) {
        int flags = getDeltaFlags(from, to, newentity);
        if (flags == 0)
            return 0;

        int result = 1;

        if ((flags & 0xff000000) != 0) {
            result += 3;
        } else if ((flags & 0x00ff0000) != 0) {
            result += 2;
        } else if ((flags & 0x0000ff00) != 0) {
            result += 1;
        }

        //----------

        if ((flags & U_NUMBER16) != 0)
            result += 2;
        else
            result +=1;

        if ((flags & U_MODEL) != 0)
            result += 1;
        if ((flags & U_MODEL2) != 0)
            result += 1;
        if ((flags & U_MODEL3) != 0)
            result += 1;
        if ((flags & U_MODEL4) != 0)
            result += 1;

        if ((flags & U_FRAME8) != 0)
            result += 1;
        if ((flags & U_FRAME16) != 0)
            result += 2;

        if ((flags & U_SKIN8) != 0 && (flags & U_SKIN16) != 0) //used for laser
            // colors
            result += 4;
        else if ((flags & U_SKIN8) != 0)
            result += 1;
        else if ((flags & U_SKIN16) != 0)
            result += 2;

        if ((flags & (U_EFFECTS8 | U_EFFECTS16)) == (U_EFFECTS8 | U_EFFECTS16))
            result += 4;
        else if ((flags & U_EFFECTS8) != 0)
            result += 1;
        else if ((flags & U_EFFECTS16) != 0)
            result += 2;

        if ((flags & (U_RENDERFX8 | U_RENDERFX16)) == (U_RENDERFX8 | U_RENDERFX16))
            result += 4;
        else if ((flags & U_RENDERFX8) != 0)
            result += 1;
        else if ((flags & U_RENDERFX16) != 0)
            result += 2;

        if ((flags & U_ORIGIN1) != 0)
            result += 2;
        if ((flags & U_ORIGIN2) != 0)
            result += 2;
        if ((flags & U_ORIGIN3) != 0)
            result += 2;

        if ((flags & U_ANGLE1) != 0)
            result += 1;
        if ((flags & U_ANGLE2) != 0)
            result += 1;
        if ((flags & U_ANGLE3) != 0)
            result += 1;

        if ((flags & U_OLDORIGIN) != 0) {
            result += 6;
        }

        if ((flags & U_SOUND) != 0)
            result += 1;
        if ((flags & U_EVENT) != 0)
            result += 1;
        if ((flags & U_SOLID) != 0)
            result += 2;

        return result;
    }

    //============================================================

}