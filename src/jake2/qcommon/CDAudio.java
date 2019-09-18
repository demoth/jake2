/*

Jake2-CDAudio-patch
sfranzyshen - sfranzyshen hotmail com
6/30/2011

Notice:
	This patch does not allow you to play mp3s from Jake2. It simply emulates quake2's cd code in Jake2 by 
playing mp3s from the baseq2 (or sub game) folders. so, if you wanted to do something like "play someaudio.mp3" 
at the console, you are out of luck here. (can you code that? start here http://www.quakewiki.net/quakesrc/30.html)

	However! if you want to play Jake2 and have mp3s of the cd's tracks playing in the background, just as you would 
using the stock 3.21 client and an original Quake2 CD (with the audio tracks on it) in the cdrom drive. Then this patch
is for You!

Rather then needing the cd in the cdrom drive. We create mp3s out of each of the audio tracks (Quake2 has 10)
from the cd. then number them 1.mp3 - 10.mp3 (We can have upto 99 tracks) and place them under your baseq2 folder in a 
sub folder named soundtrack. (ex. c:\quake2\baseq2\soundtrack\1.mpg)

About:
	This patch adds the ability to play Quake2's soundtrack within Jake2. (as you would be able to using the (id's)
stock Quake2 3.21 client.) Becuase there is no (good) crossplatform Java CDROM drive control library available, we used
MP3 files instead. The MP3's files are stored within the baseq2/soundtrack directory, or within a game folder /soundtrack
directory.(maybe even in a pak or jar file too? or streaming over the internet!) Limited to 99 tracks (two digits from 
map .bsp file). To play the mp3s, we added the JLayer library to handle decoding and playing the mp3 files. All the cd 
commands have been re-implemted (on, off, reset, remap, close, play, loop, stop, pause, resume, eject, info) although many
of them don't realy do anything (ex. eject) special. To control the cd functions, two cvars are used. The first is cd_nocd,
and the other is cd_volume. We added CDAudio.java (should rename to CD.java) & MP3.java files to the source code, and 
modified all the files that were missing the "cd code" from the Jake2 source.

Install:
	Obviousely you will need to apply this patch to the Jake2 source and rebuild. We are not providing anyway to 
create the mp3 files from your orginal Quake2 CD. However, it isn't difficult to do. See "CD Ripper" and rip your 
original cd audio tracks into mp3 files named 1.mp3 2.mp3 3.mp3, etc. for each of the 10 audio tracks. You can edit 
the id3 tags to reflect the Quake2 titles, ... (see cddb, freedb.org, CDex). Place the mp3 files (1.mp3, 2.mp3, 3.mp3, ...)
into a soundtrack directory under your quake2/baseq2 directory. (ex. if you have quake2 installed in c:\quake2, then 
put 1.mp3 into c:\quake2\baseq2\soundtrack\1.mp3 ... etc. under unix (linux, macos) put them in your home folder (~) 
.quake2/baseq2/soundtrack/1.mp3 ...) Then kick off Jake2, sit back, and amp-up your game play with the stimulating 
soundtrack! (Did you know that Rob Zommbie did the music on the intro.cin file?)

*/


package jake2.qcommon;


/*
 * CDAudio.java
 * sfranzyshen - sfranzyshen hotmail com
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

import jake2.game.Cmd;
import jake2.qcommon.util.Lib;

import java.io.File;
import java.util.List;

public final class CDAudio {
    
    private static boolean       cdValid = false;
    public static boolean       playing = false;
    private static boolean       wasPlaying = false;
    public static boolean       initialized = false;
    private static boolean       enabled = true;
    private static boolean       playLooping = false;
    private static float         cdvolume = 0.0f;
    //public static byte        remap[100];
    private static byte          playTrack;
    private static byte          maxTrack;

    private static MP3                  mp3;
    private static int                  cdfile = -1;
    private static cvar_t               cd_volume;
    private static cvar_t               cd_nocd;
    private static cvar_t               cddir;

    /**
     * CD_f
     */
    private static void CD_f(List<String> args) {

            String  command;
 
            if (args.size() < 2)
                return;

            command = args.get(1);

            if (command.equalsIgnoreCase("on")) {
                enabled = true;
                return;
            }

            if (command.equalsIgnoreCase("off")) {
                if (playing)
                    Stop();
                enabled = false;
                return;
            }

            if (command.equalsIgnoreCase("reset")) {
                enabled = true;
                if (playing)
                    Stop();
                
                //for (n = 0; n < 100; n++)
                //      CDAudio.remap[n] = n;

                GetAudioDiskInfo();
                return;
            }

            if (command.equalsIgnoreCase("remap")) {
                //ret = Cmd.Argc() - 2;
                //if (ret <= 0) {
                //      for (n = 1; n < 100; n++)
                //              if (remap[n] != n)
                //                      Com_Printf("  %u -> %u\n", n, remap[n]);
                //      return;
                //}
                //for (n = 1; n <= ret; n++)
                //      remap[n] = atoi(Cmd_Argv (n+1));
                return;
            }

            if (command.equalsIgnoreCase("close")) {
                CloseDoor();
                return;
            }

            if (!cdValid) {
                GetAudioDiskInfo();
                if (!cdValid) {
                   Com.Printf("Audio CD (MP3s) No CD in player.\n");
                   return;
                }
            }

            if (command.equalsIgnoreCase("play") && args.size() == 3) {
                    Play(Lib.atoi(args.get(2)), false);
               return;
            }

            if (command.equalsIgnoreCase("loop") && args.size() == 3) {
                    Play(Lib.atoi(args.get(2)), true);
                return;
            }

            if (command.equalsIgnoreCase("stop")) {
                Stop();
                return;
            }

            if (command.equalsIgnoreCase("pause")) {
                Pause();
                return;
            }

            if (command.equalsIgnoreCase("resume")) {
                Resume();
                return;
            }

            if (command.equalsIgnoreCase("eject")) {
                if (playing)
                    Stop();
                Eject();
                cdValid = false;
                return;
            }

            if (command.equalsIgnoreCase("info")) {
                Com.Printf(maxTrack + " tracks (MP3s) on the cd\n");
                if (playing)
                    if (playLooping)
                        Com.Printf("Currently looping track " + playTrack +"\n");
                    else
                        Com.Printf("Currently playing track " + playTrack +"\n");
                else if (wasPlaying)
                    if (playLooping)
                        Com.Printf("Paused looping track " + playTrack +"\n");
                    else
                        Com.Printf("Paused playing track " + playTrack +"\n");
                Com.Printf("Volume is " + cdvolume + "\n");
            }
        }

    public static void Play(int track, boolean looping) {
        if (cdfile == -1 || !enabled)
            return;

        if (!cdValid) {
            GetAudioDiskInfo();
            if (!cdValid)
                return;
        }

        //track = remap[track];

        if (track < 1 || track > maxTrack) {
            Com.Printf("CD Audio (MP3s) Bad track number " + track +".\n");
            return;
        }

        if (playing)
        {
                if (playTrack == track)
                        return;
                Stop();
        }

        // play mp3 file
        String mp3filename = cddir.string + "\\soundtrack\\" +track + ".mp3";
        mp3 = new MP3(mp3filename, looping);
        mp3.play();
        if (mp3.isError() == true)
            return;
            
        playLooping = looping;
        playTrack = (byte) track;
        playing = true;

        if (cd_volume.value == 0.0f)
                Stop ();
        if (looping == true)
            Com.DPrintf("CD Audio (MP3s) Looping track " + track + "\n");
        else
            Com.DPrintf("CD Audio (MP3s) Playing track " + track + "\n");
    }

    public static void Stop() {
        if (cdfile == -1 || enabled == false || playing == false)
            return;
        
        // stop mp3
        mp3.stop();
        wasPlaying = false;
        playing = false;
        Com.DPrintf("CD Audio (MP3s) Stopping track\n");
    }

    private static void Eject() {
       if (cdfile == -1 || enabled == false)
            return;
       // do nothing
       Com.DPrintf("CD Audio (MP3s) Ejected\n"); 
    }

    private static void CloseDoor() {
        if (cdfile == -1 || enabled == false)
            return;
        // do nothing
        Com.DPrintf("CD Audio (MP3s) CloseDoor\n");
    }

    private static int GetAudioDiskInfo() {
        cdValid = false;
        maxTrack = 0;
        
        // check for mp3's if no mp3's return -1
        int numFiles = 0;

        String folder = cddir.string + "/soundtrack";
        File f = new File(folder);

        if(f.exists() && f.isDirectory()) {
            Com.DPrintf("CD Audio we found the sountrack folder\n");
            File[] children = f.listFiles();
            for(int i = 0; i < children.length; i++) {
                boolean isText = checkMP3(children[i]);
                if(isText)
                    numFiles++;
            }
            maxTrack = (byte) numFiles;
            Com.DPrintf("CD Audio we found " + maxTrack + " MP3s in the folder\n");
            if (maxTrack < 1)
                return -1;
            cdValid = true;
            return 0;
        }
        return -1;
    }

    private static boolean checkMP3(File f) {
        String name = f.getName();
        if(name.contains(".mp3"))
            return true;
        else
            return false;
    }

    private static void Pause() {
        if (cdfile == -1 || enabled == false || playing == false)
            return;

        // pause mp3
        mp3.pause();
        if (mp3.isError() == false) {
            wasPlaying = playing;
            playing = false;
            Com.DPrintf("CD Audio (MP3s) Paused \n");    
        }
    }

    private static void Resume() {
        if (cdfile == -1 || enabled == false || cdValid == false || wasPlaying == false)
            return; 

        // resume mp3
        mp3.resume();
        if (mp3.isError() == false) {
            wasPlaying = playing;
            playing = true;
            Com.DPrintf("CD Audio (MP3s) Resumed\n"); 
        }
    }

    public static void Update() {
        return;
    }

    public static int Init() {
        cd_nocd = Cvar.Get ("cd_nocd", "0", Defines.CVAR_ARCHIVE );
        
        if ( (int)cd_nocd.value != 0 )
                return -1;
        
        cddir = Cvar.Get ("cddir", "C:\\Quake2\\baseq2", Defines.CVAR_ARCHIVE );        
        cd_volume = Cvar.Get ("cd_volume", "1", Defines.CVAR_ARCHIVE);
        cdvolume = cd_volume.value;
        

        //for (i = 0; i < 100; i++)
        //        remap[i] = i;


        if (GetAudioDiskInfo()  == -1)
        {
                Com.Printf("CD Audio No CD (MP3s) in player\n");
                //cdValid = false;
                cdfile = -1;
                initialized = false;
                enabled = false;
                return -1;
        }

        Cmd.AddCommand ("cd", CDAudio::CD_f);
        
        //cdValid = true;
        cdfile = 0;
        initialized = true;
        enabled = true;

        Com.Printf("CD Audio (MP3s) Initialized\n");
        return 0;
    }

    public static void Shutdown() {
        if (initialized == false)
            return;
        Stop();
        cdfile = -1;
        Com.Printf("CD Audio (MP3s) Shutdown\n");
    }
}
