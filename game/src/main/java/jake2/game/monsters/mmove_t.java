/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/

// Created on 11.11.2003 by RST.

package jake2.game.monsters;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.SuperAdapter;
import jake2.qcommon.filesystem.QuakeFile;

import java.io.IOException;

/**
 * Represents a state of animation: a collection of {@link mframe_t} and an end function.
 */
public class mmove_t {

    public mmove_t(int firstframe, int lastframe, mframe_t[] frames, EntThinkAdapter endfunc) {

        this.firstframe = firstframe;
        this.lastframe = lastframe;
        this.frames = frames;
        this.endfunc = endfunc;

        //assert frames.length == lastframe - firstframe + 1;
    }

    public final int firstframe;
    public final int lastframe;

    public final mframe_t[] frames;
    public final EntThinkAdapter endfunc;

    public void write(QuakeFile f) throws IOException {
        f.writeInt(firstframe);
        f.writeInt(lastframe);
        f.writeInt(frames.length);
        for (mframe_t frame : frames)
            frame.write(f);
        SuperAdapter.writeAdapter(f, endfunc);
    }

    public static mmove_t read(QuakeFile f) throws IOException {

        int firstframe = f.readInt();
        int lastframe = f.readInt();

        int len = f.readInt();

        mframe_t[] frame = new mframe_t[len];
        for (int n = 0; n < len; n++) {
            frame[n] = new mframe_t();
            frame[n].read(f);
        }
        EntThinkAdapter endfunc = (EntThinkAdapter) SuperAdapter.readAdapter(f);

        return new mmove_t(firstframe, lastframe, frame, endfunc);
    }
}
