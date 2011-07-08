package jake2.qcommon;

/*
 JLayer MP3 Decoder & Player code
 6/30/2011 - sfranzyshen - sfranzyshen@hotmail.com
 special thanks to the following people for their contributions to the community.
 Kevin Wayne - wayne@CS.Princeton.EDU
 Joachim Lippold - Newsgroup de.comp.lang.java
 YEAHEYAHYEAH - http://stackoverflow.com/users/723039/yeaheyahyeah
*/

import java.io.*;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;


public class MP3 {
    private String              mp3file = null;
    private Player              player = null;
    private FileInputStream     fis = null;
    private BufferedInputStream bis = null;
    private Bitstream           bs = null;
    private String              id3v2 = null;

    private int                 totallength; // milliseconds
    private int                 totalbytes; //bytes
    private int                 currentpos; // bytes

    private boolean             ispaused = false;
    private boolean             isplaying = false;
    private boolean             islooping = false;
    private boolean             iscomplete = false;
    private boolean             isinitialized = false;
    private boolean             iserror = false;
    
    public MP3(String filename, boolean looping) {
        mp3file = filename;
        islooping = looping;
        if (init() == -1) {
            isinitialized = false;
            iserror = true;
        } else
            isinitialized = true;
            iserror = false;
    }
    
    private int init() {
        try {
            fis = new FileInputStream(mp3file);
        } catch (FileNotFoundException e) {
            Com.DPrintf("MP3 Player File: " + mp3file +" failed to open.\n");
            return -1;
        }
        
        bis = new BufferedInputStream (fis);
        bs = new Bitstream (bis);
        
        try {
            player = new Player(bis);
        } catch (JavaLayerException e1) {
            Com.DPrintf("MP3 Player failed to initialize player.\n");
            return -1;
        }

        try {
            totalbytes = fis.available();
        } catch (IOException e1) {
            Com.DPrintf("MP3 Player failed to initialize totalbytes.\n");
        } // bytes

        totallength = player.getPosition(); // milliseconds
        iscomplete = false;
        InputStream id3in = bs.getRawID3v2();
        
        if (id3in != null) {
            BufferedInputStream id3 = new BufferedInputStream (id3in);

            StringBuffer id3out = new StringBuffer ();
            
            int _char;
            try {
                while ((_char = id3.read())!=-1) 
                    id3out.append((char)_char);
            } catch (IOException e) {
                Com.DPrintf("MP3 player id3out.append failed\n");
            }
            
            try {
                id3.close();
            } catch (IOException e) {
                Com.DPrintf("MP3 player id3.close failed\n");
            }
            id3v2 = id3out.toString();
        } else 
            id3v2 = "";
        return 0;
    }

    public boolean isComplete() {
        if (iscomplete == true)
            return true;
        else
            return false;
    }

    public boolean isInitialized() {
        if (isinitialized == true)
            return true;
        return false;
    }

    public boolean isPlaying() {
        if (isplaying == true)
            return true;
        return false;
    }

    public boolean isPaused() {
        if (ispaused == true)
            return true;
        return false;
    }

    public boolean isError() {
        if (iserror == true)
            return true;
        return false;
    }

    public int getTotalLength() {
        if (isinitialized == false)
            return 0;
        return totallength; // milliseconds
    }
    
    public int getPosition() {
        if (ispaused == true || isplaying == false || isinitialized == false)
            return 0;
        return player.getPosition(); // milliseconds
    }
    
    public String getRawID3v2() {
        return id3v2;
    }

    public void pause() {
        if (ispaused == true || isplaying == false || isinitialized == false)
            return;
        try {
            currentpos = fis.available();
        } catch (IOException e) {
            Com.DPrintf("MP3 player pause() fis.available() failed\n");
            iserror = true;
        }

        ispaused = true;
        player.close();
        Com.DPrintf("MP3 player is paused.\n");
    }
    
    public void resume() {
        if (ispaused == false || isplaying == false || isinitialized == false)
            return;
        if (init() == -1) {
            Com.DPrintf("MP3 player failed to init resume\n");
            isinitialized = false;
            isplaying = false;
            ispaused = false;
            iserror = true;
            return;
        } else {
            try {
                fis.skip(totalbytes - currentpos);
            } catch (IOException e) {
                Com.DPrintf("MP3 player failed to skip resume\n");
                isinitialized = false;
                iserror = true;
                ispaused = false;
                isplaying = false;
            }
            isinitialized = true;
            play();

            if (iserror == false) {
                ispaused = false;
                Com.DPrintf("MP3 player has resumed.\n");
            } else
                Com.DPrintf("MP3 player resume failed?\n");
        }
    }
    
    public void stop() { 
        if (isplaying == true) {
            id3v2 = "";
            totallength = 0;
            totalbytes = 0;
            currentpos = 0;
            ispaused = false;
            isplaying = false;
            islooping = false;
            iscomplete = true;
            isinitialized = false;
            player.close();
        }
    }

    public void play() {
        if (isplaying == true && ispaused == false || isinitialized == false)
            return;

        isplaying = true;
        new Thread() {
            public void run() {
                try { 
                    while (true) {
                        player.play();
                        if (islooping == false || ispaused == true) {
                            iscomplete = true;
                            break;
                        }
                        if (init() == -1) {
                            isinitialized = false;
                            iserror = true;
                            iscomplete = true;
                            Com.DPrintf("MP3 player error while looping.\n");
                            break;
                        }
                        isinitialized = true;
                        iserror = false;
                        Com.DPrintf("MP3 player looping track.\n");
                    }
                    Com.DPrintf("MP3 player finished track.\n");
                }
                catch (Exception e) { 
                    Com.DPrintf("MP3 player failed to play.\n");
                    isplaying = false;
                    iserror = true;
                }
            }
        }.start();
    }
}
