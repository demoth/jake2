package jake2.server;

import jake2.qcommon.ServerStates;

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

    ChangeMapInfo(String levelString) {
        this.levelString = levelString;
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

        if (levelString.endsWith(".cin")) {
            state = ServerStates.SS_CINEMATIC;
        } else if (levelString.endsWith(".dm2")) {
            state = ServerStates.SS_DEMO;
        } else if (levelString.endsWith(".pcx")) {
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
