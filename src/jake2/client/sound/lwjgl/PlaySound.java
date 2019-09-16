/*
 * Created on Dec 22, 2004
 *
 * Copyright (C) 2003
 *
 * $Id: PlaySound.java,v 1.3 2012-04-12 22:01:24 cawe Exp $
 */
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
package jake2.client.sound.lwjgl;

import jake2.qcommon.Globals;
import jake2.qcommon.util.Math3D;

/**
 * PlaySound
 * 
 * @author cwei
 */
public class PlaySound {

    final static int MAX_PLAYSOUNDS = 128;

    // list with sentinel
    private static PlaySound freeList;
    private static PlaySound playableList;

    private static PlaySound[] backbuffer = new PlaySound[MAX_PLAYSOUNDS];
    static {
        for (int i = 0; i < backbuffer.length; i++) {
            backbuffer[i] = new PlaySound();
        }
        // init the sentinels
        freeList = new PlaySound();
        playableList = new PlaySound();
        // reset the lists
        reset();
    }

    // sound attributes
    int type;
    int entnum;
    int entchannel;
    int bufferId;
    float volume;
    float attenuation;
    float[] origin = { 0, 0, 0 };

    // begin time in ms
    private long beginTime;

    // for linked list
    private PlaySound prev, next;

    private PlaySound() {
        prev = next = null;
        this.clear();
    }

    private void clear() {
        type = bufferId = entnum = entchannel = -1;
        // volume = attenuation = beginTime = 0;
        attenuation = beginTime = 0;
        // Math3D.VectorClear(origin);
    }

    static void reset() {
        // init the sentinels
        freeList.next = freeList.prev = freeList;
        playableList.next = playableList.prev = playableList;

        // concat the the freeList
        PlaySound ps;
        for (int i = 0; i < backbuffer.length; i++) {
            ps = backbuffer[i];
            ps.clear();
            ps.prev = freeList;
            ps.next = freeList.next;
            ps.prev.next = ps;
            ps.next.prev = ps;
        }
    }

    static PlaySound nextPlayableSound() {
        PlaySound ps = null;
        while (true) {
            ps = playableList.next;
            if (ps == playableList || ps.beginTime > Globals.cl.time)
                return null;
            PlaySound.release(ps);
            return ps;
        }
    }

    private static PlaySound get() {
        PlaySound ps = freeList.next;
        if (ps == freeList)
            return null;

        ps.prev.next = ps.next;
        ps.next.prev = ps.prev;
        return ps;
    }

    private static void add(PlaySound ps) {

        PlaySound sort = playableList.next;

        for (; sort != playableList && sort.beginTime < ps.beginTime; sort = sort.next)
            ;
        ps.next = sort;
        ps.prev = sort.prev;
        ps.next.prev = ps;
        ps.prev.next = ps;
    }

    private static void release(PlaySound ps) {
        ps.prev.next = ps.next;
        ps.next.prev = ps.prev;
        // add to free list
        ps.next = freeList.next;
        freeList.next.prev = ps;
        ps.prev = freeList;
        freeList.next = ps;
    }

    static void allocate(float[] origin, int entnum, int entchannel,
            int bufferId, float volume, float attenuation, float timeoffset) {

        PlaySound ps = PlaySound.get();

        if (ps != null) {
            // find the right sound type
            if (entnum == Globals.cl.playernum + 1) {
                ps.type = Channel.LISTENER;
            } else if (origin != null) {
                ps.type = Channel.FIXED;
                Math3D.VectorCopy(origin, ps.origin);
            } else {
                ps.type = Channel.DYNAMIC;
            }
            ps.entnum = entnum;
            ps.entchannel = entchannel;
            ps.bufferId = bufferId;
            ps.volume = volume;
            ps.attenuation = attenuation;
            ps.beginTime = Globals.cl.time + (long) (timeoffset * 1000);
            PlaySound.add(ps);
        } else {
            System.err.println("PlaySounds out of Limit");
        }
    }
}
