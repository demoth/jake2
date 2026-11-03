package jake2.server;

import jake2.qcommon.ServerStates;

import java.util.Locale;

/**
 * Used when changing map (via console or by change_map triggers)
 */
public class ChangeMapInfo {
    final String levelString;
    final String mapName;
    final boolean newUnit;
    final String nextServer;
    final String spawnPoint;
    final ServerStates state;
    final boolean isDemo;
    final boolean isLoadgame;

    /**
     * @param levelString - change map command. The full syntax is:
     *
     * [*]mapname$spawnpoint+nextserver
     *
     * command from the console or progs. Map can also be a .cin, .dm2, or still image
     * (.pcx, .tga, .png, .jpg, .jpeg) file.
     *
     * Nextserver is used to allow a cinematic to play, then proceed to
     * another level:
     *
     * map tram.cin+jail_e3
     *
     */
    ChangeMapInfo(String levelString, boolean isDemo, boolean isLoadgame) {
        this.levelString = levelString;
        this.isLoadgame = isLoadgame;
        this.isDemo = isDemo;
        newUnit = levelString.startsWith("*");

        // next server
        int plus = levelString.indexOf('+');
        if (plus != -1) {
            nextServer = levelString.substring(plus + 1);
            levelString = levelString.substring(0, plus);
        } else {
            nextServer = "";
        }

        // spawn point
        int dollar = levelString.indexOf('$');
        if (dollar != -1) {
            spawnPoint = levelString.substring(dollar + 1);
            levelString = levelString.substring(0, dollar);
        } else {
            spawnPoint = "";
        }

        // skip the end-of-unit flag * if necessary
        if (levelString.startsWith("*"))
            mapName = levelString.substring(1);
        else
            mapName = levelString;

        String mediaName = mapName.toLowerCase(Locale.ROOT);
        if (mediaName.endsWith(".cin")) {
            state = ServerStates.SS_CINEMATIC;
        } else if (mediaName.endsWith(".dm2")) {
            if (!isDemo) {
                // todo remove
                throw new IllegalStateException(".dm2 server should have isDemo=true");
            }
            state = ServerStates.SS_DEMO;
        } else if (mediaName.endsWith(".pcx") || mediaName.endsWith(".tga")
                || mediaName.endsWith(".png") || mediaName.endsWith(".jpg")
                || mediaName.endsWith(".jpeg")) {
            state = ServerStates.SS_PIC;
        } else {
            state = ServerStates.SS_GAME;
        }
    }

    @Override
    public String toString() {
        return "ChangeMapInfo{" +
                "levelString='" + levelString + '\'' +
                ", mapName='" + mapName + '\'' +
                ", newUnit=" + newUnit +
                ", nextServer='" + nextServer + '\'' +
                ", spawnPoint='" + spawnPoint + '\'' +
                ", state=" + state +
                '}';
    }
}
