/*
 * SND_MIX.java
 * Copyright (C) 2004
 * 
 * $Id: SND_MIX.java,v 1.2 2004-09-22 19:22:09 salomo Exp $
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
package jake2.sound.jsound;

import jake2.game.cvar_t;
import jake2.sound.WaveLoader;
import jake2.sound.sfx_t;
import jake2.sound.sfxcache_t;
import jake2.util.Math3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * SND_MIX
 */
public class SND_MIX extends SND_JAVA {

    static final int MAX_CHANNELS = 32;

    static final int MAX_RAW_SAMPLES = 8192;

    static class playsound_t {
        playsound_t prev, next;

        sfx_t sfx;

        float volume;

        float attenuation;

        int entnum;

        int entchannel;

        boolean fixed_origin; // use origin field instead of entnum's origin

        float[] origin = { 0, 0, 0 };

        long begin; // begin on this sample

        public void clear() {
            prev = next = null;
            sfx = null;
            volume = attenuation = begin = entnum = entchannel = 0;
            fixed_origin = false;
            Math3D.VectorClear(origin);
        }
    };

    static class channel_t {
        sfx_t sfx; // sfx number

        int leftvol; // 0-255 volume

        int rightvol; // 0-255 volume

        int end; // end time in global paintsamples

        int pos; // sample position in sfx

        int looping; // where to loop, -1 = no looping OBSOLETE?

        int entnum; // to allow overriding a specific sound

        int entchannel; //

        float[] origin = { 0, 0, 0 }; // only use if fixed_origin is set

        float dist_mult; // distance multiplier (attenuation/clipK)

        int master_vol; // 0-255 master volume

        boolean fixed_origin; // use origin instead of fetching entnum's origin

        boolean autosound; // from an entity->sound, cleared each frame

        void clear() {
            sfx = null;
            dist_mult = leftvol = rightvol = end = pos = looping = entnum = entchannel = master_vol = 0;
            Math3D.VectorClear(origin);
            fixed_origin = autosound = false;
        }
    };

    static class portable_samplepair_t {
        int left;

        int right;
    };

    static cvar_t s_volume;

    static int s_rawend;

    //// snd_mix.c -- portable code to mix sounds for snd_dma.c
    //
    //	#include "client.h"
    //	#include "snd_loc.h"
    //
    static final int PAINTBUFFER_SIZE = 2048;

    //static portable_samplepair_t[] paintbuffer = new
    // portable_samplepair_t[PAINTBUFFER_SIZE];
    static IntBuffer paintbuffer = IntBuffer.allocate(PAINTBUFFER_SIZE * 2);

    static int[][] snd_scaletable = new int[32][256];

    //	int *snd_p, snd_linear_count, snd_vol;
    //	short *snd_out;
    static IntBuffer snd_p;

    static ShortBuffer snd_out;

    static int snd_linear_count;

    static int snd_vol;

    static int paintedtime; // sample PAIRS

    static playsound_t s_pendingplays = new playsound_t();

    //static portable_samplepair_t[] s_rawsamples = new
    // portable_samplepair_t[MAX_RAW_SAMPLES];
    static IntBuffer s_rawsamples = IntBuffer.allocate(MAX_RAW_SAMPLES * 2);

    static channel_t[] channels = new channel_t[MAX_CHANNELS];
    static {
        for (int i = 0; i < MAX_CHANNELS; i++)
            channels[i] = new channel_t();
    }

    static void WriteLinearBlastStereo16() {
        int i;
        int val;

        for (i = 0; i < snd_linear_count; i += 2) {
            val = snd_p.get(i) >> 8;
            if (val > 0x7fff)
                snd_out.put(i, (short) 0x7fff);
            else if (val < (short) 0x8000)
                snd_out.put(i, (short) 0x8000);
            else
                snd_out.put(i, (short) val);

            val = snd_p.get(i + 1) >> 8;
            if (val > 0x7fff)
                snd_out.put(i + 1, (short) 0x7fff);
            else if (val < (short) 0x8000)
                snd_out.put(i + 1, (short) 0x8000);
            else
                snd_out.put(i + 1, (short) val);
        }
    }

    static void TransferStereo16(ByteBuffer pbuf, int endtime) {
        int lpos;
        int lpaintedtime;

        snd_p = paintbuffer;
        lpaintedtime = paintedtime;

        while (lpaintedtime < endtime) {
            // handle recirculating buffer issues
            lpos = lpaintedtime & ((dma.samples >> 1) - 1);

            //			snd_out = (short *) pbuf + (lpos<<1);
            snd_out = pbuf.asShortBuffer();
            snd_out.position(lpos << 1);
            snd_out = snd_out.slice();

            snd_linear_count = (dma.samples >> 1) - lpos;
            if (lpaintedtime + snd_linear_count > endtime)
                snd_linear_count = endtime - lpaintedtime;

            snd_linear_count <<= 1;

            // write a linear blast of samples
            WriteLinearBlastStereo16();

            //snd_p += snd_linear_count;
            paintbuffer.position(snd_linear_count);
            snd_p = paintbuffer.slice();

            lpaintedtime += (snd_linear_count >> 1);
        }
    }

    /*
     * =================== S_TransferPaintBuffer
     * 
     * ===================
     */
    static void TransferPaintBuffer(int endtime) {
        int out_idx;
        int count;
        int out_mask;
        int p;
        int step;
        int val;
        //unsigned long *pbuf;

        ByteBuffer pbuf = ByteBuffer.wrap(dma.buffer);
        pbuf.order(ByteOrder.LITTLE_ENDIAN);

        if (SND_DMA.s_testsound.value != 0.0f) {
            int i;
            int count2;

            // write a fixed sine wave
            count2 = (endtime - paintedtime) * 2;
            int v;
            for (i = 0; i < count2; i += 2) {
                v = (int) (Math.sin((paintedtime + i) * 0.1) * 20000 * 256);
                paintbuffer.put(i, v);
                paintbuffer.put(i + 1, v);
            }
        }

        if (dma.samplebits == 16 && dma.channels == 2) { // optimized case
            TransferStereo16(pbuf, endtime);
        } else { // general case
            p = 0;
            count = (endtime - paintedtime) * dma.channels;
            out_mask = dma.samples - 1;
            out_idx = paintedtime * dma.channels & out_mask;
            step = 3 - dma.channels;

            if (dma.samplebits == 16) {
                //				short *out = (short *) pbuf;
                ShortBuffer out = pbuf.asShortBuffer();
                while (count-- > 0) {
                    val = paintbuffer.get(p) >> 8;
                    p += step;
                    if (val > 0x7fff)
                        val = 0x7fff;
                    else if (val < (short) 0x8000)
                        val = (short) 0x8000;
                    out.put(out_idx, (short) val);
                    //System.out.println(out_idx + " " + val);
                    out_idx = (out_idx + 1) & out_mask;
                }
            } else if (dma.samplebits == 8) {
                //				unsigned char *out = (unsigned char *) pbuf;
                ByteBuffer out = pbuf;
                while (count-- > 0) {
                    val = paintbuffer.get(p) >> 8;
                    p += step;
                    if (val > 0x7fff)
                        val = 0x7fff;
                    else if (val < (short) 0x8000)
                        val = (short) 0x8000;
                    out.put(out_idx, (byte) (val >>> 8));
                    out_idx = (out_idx + 1) & out_mask;
                }
            }
        }
    }

    /*
     * ===============================================================================
     * 
     * CHANNEL MIXING
     * 
     * ===============================================================================
     */
    static void PaintChannels(int endtime) {
        int i;
        int end;
        channel_t ch;
        sfxcache_t sc;
        int ltime, count;
        playsound_t ps;

        snd_vol = (int) (s_volume.value * 256);

        //	  Com_Printf ("%i to %i\n", paintedtime, endtime);
        while (paintedtime < endtime) {
            // if paintbuffer is smaller than DMA buffer
            end = endtime;
            if (endtime - paintedtime > PAINTBUFFER_SIZE)
                end = paintedtime + PAINTBUFFER_SIZE;

            // start any playsounds
            while (true) {
                ps = s_pendingplays.next;
                if (ps == s_pendingplays)
                    break; // no more pending sounds
                if (ps.begin <= paintedtime) {
                    SND_DMA.IssuePlaysound(ps);
                    continue;
                }

                if (ps.begin < end)
                    end = (int) ps.begin; // stop here
                break;
            }

            // clear the paint buffer
            if (s_rawend < paintedtime) {
                //				Com_Printf ("clear\n");
                for (i = 0; i < (end - paintedtime) * 2; i++) {
                    paintbuffer.put(i, 0);
                }
                //memset(paintbuffer, 0, (end - paintedtime) *
                // sizeof(portable_samplepair_t));
            } else { // copy from the streaming sound source
                int s;
                int stop;

                stop = (end < s_rawend) ? end : s_rawend;

                for (i = paintedtime; i < stop; i++) {
                    s = i & (MAX_RAW_SAMPLES - 1);
                    //paintbuffer[i-paintedtime] = s_rawsamples[s];
                    paintbuffer.put((i - paintedtime) * 2, s_rawsamples
                            .get(2 * s));
                    paintbuffer.put((i - paintedtime) * 2 + 1, s_rawsamples
                            .get(2 * s) + 1);
                }
                //			if (i != end)
                //				Com_Printf ("partial stream\n");
                //			else
                //				Com_Printf ("full stream\n");
                for (; i < end; i++) {
                    //paintbuffer[i-paintedtime].left =
                    //paintbuffer[i-paintedtime].right = 0;
                    paintbuffer.put((i - paintedtime) * 2, 0);
                    paintbuffer.put((i - paintedtime) * 2 + 1, 0);
                }
            }

            // paint in the channels.
            //ch = channels;
            for (i = 0; i < MAX_CHANNELS; i++) {
                ch = channels[i];
                ltime = paintedtime;

                while (ltime < end) {
                    if (ch.sfx == null || (ch.leftvol == 0 && ch.rightvol == 0))
                        break;

                    // max painting is to the end of the buffer
                    count = end - ltime;

                    // might be stopped by running out of data
                    if (ch.end - ltime < count)
                        count = ch.end - ltime;

                    sc = WaveLoader.LoadSound(ch.sfx);
                    if (sc == null)
                        break;

                    if (count > 0 && ch.sfx != null) {
                        if (sc.width == 1)// FIXME; 8 bit asm is wrong now
                            PaintChannelFrom8(ch, sc, count, ltime
                                    - paintedtime);
                        else
                            PaintChannelFrom16(ch, sc, count, ltime
                                    - paintedtime);

                        ltime += count;
                    }

                    // if at end of loop, restart
                    if (ltime >= ch.end) {
                        if (ch.autosound) { // autolooping sounds always go back
                                            // to start
                            ch.pos = 0;
                            ch.end = ltime + sc.length;
                        } else if (sc.loopstart >= 0) {
                            ch.pos = sc.loopstart;
                            ch.end = ltime + sc.length - ch.pos;
                        } else { // channel just stopped
                            ch.sfx = null;
                        }
                    }
                }

            }

            // transfer out according to DMA format
            TransferPaintBuffer(end);
            paintedtime = end;
        }
    }

    static void InitScaletable() {
        int i, j;
        int scale;

        s_volume.modified = false;
        for (i = 0; i < 32; i++) {
            scale = (int) (i * 8 * 256 * s_volume.value);
            for (j = 0; j < 256; j++)
                snd_scaletable[i][j] = ((byte) j) * scale;
        }
    }

    static void PaintChannelFrom8(channel_t ch, sfxcache_t sc, int count,
            int offset) {
        int data;
        int[] lscale;
        int[] rscale;
        int sfx;
        int i;
        portable_samplepair_t samp;

        if (ch.leftvol > 255)
            ch.leftvol = 255;
        if (ch.rightvol > 255)
            ch.rightvol = 255;

        //ZOID-- >>11 has been changed to >>3, >>11 didn't make much sense
        //as it would always be zero.
        lscale = snd_scaletable[ch.leftvol >> 3];
        rscale = snd_scaletable[ch.rightvol >> 3];
        sfx = ch.pos;

        //samp = paintbuffer[offset];

        for (i = 0; i < count; i++, offset++) {
            int left = paintbuffer.get(offset * 2);
            int right = paintbuffer.get(offset * 2 + 1);
            data = sc.data[sfx + i];
            left += lscale[data];
            right += rscale[data];
            paintbuffer.put(offset * 2, left);
            paintbuffer.put(offset * 2 + 1, right);
        }

        ch.pos += count;
    }

    private static ByteBuffer bb;

    private static ShortBuffer sb;

    static void PaintChannelFrom16(channel_t ch, sfxcache_t sc, int count,
            int offset) {
        int data;
        int left, right;
        int leftvol, rightvol;
        int sfx;
        int i;
        portable_samplepair_t samp;

        leftvol = ch.leftvol * snd_vol;
        rightvol = ch.rightvol * snd_vol;
        ByteBuffer bb = ByteBuffer.wrap(sc.data);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        sb = bb.asShortBuffer();
        sfx = ch.pos;

        //samp = paintbuffer[offset];
        for (i = 0; i < count; i++, offset++) {
            left = paintbuffer.get(offset * 2);
            right = paintbuffer.get(offset * 2 + 1);
            data = sb.get(sfx + i);
            left += (data * leftvol) >> 8;
            right += (data * rightvol) >> 8;
            paintbuffer.put(offset * 2, left);
            paintbuffer.put(offset * 2 + 1, right);
        }

        ch.pos += count;
    }

}