package jake2.qcommon;

import java.util.List;

/**
 * Functions exported by the game system to the engine
 */
public interface GameExports {
    /**
     * Processes the commands the player enters in the quake console.
     */
    void ClientCommand(ServerEntity ent, List<String> args);

    /**
     * Advances the world by Defines.FRAMETIME (0.1) seconds.
     */
    void G_RunFrame();

    /**
     * This will be called whenever the game goes to a new level, and when the
     * user explicitly saves the game.
     *
     * Game information include cross level data, like multi level triggers,
     * help computer info, and all client states.
     *
     * A single player death will automatically restore from the last save
     * position.
     */
    void WriteGame(String filename, boolean autosave);

    void readGameLocals(String filename);

    void WriteLevel(String filename);

    /**
     * SpawnEntities will allready have been called on the level the same way it
     * was when the level was saved.
     *
     * That is necessary to get the baselines set up identically.
     *
     * The server will have cleared all of the world links before calling
     * ReadLevel.
     *
     * No clients are connected yet.
     */
    void ReadLevel(String filename);

    /**
     * Creates a server's entity / program execution context by parsing textual
     * entity definitions out of an ent file.
     */
    void SpawnEntities(String mapname, String entities, String spawnpoint);

    /**
     * ServerCommand will be called when an "sv" command is issued.
     */
    void ServerCommand(List<String> args);

    /**
     * Called when a client has finished connecting, and is ready to be placed
     * into the game. This will happen every level load.
     */
    void ClientBegin(ServerEntity ent);

    /**
     * Called whenever the player updates a userinfo variable.
     *
     * The game can override any of the settings in place (forcing skins or
     * names, etc) before copying it off.
     *
     */
    String ClientUserinfoChanged(ServerEntity ent, String userinfo);

    /**
     * Called when a player begins connecting to the server. The game can refuse
     * entrance to a client by returning false. If the client is allowed, the
     * connection process will continue and eventually get to ClientBegin()
     * Changing levels will NOT cause this to be called again, but loadgames
     * will.
     */
    boolean ClientConnect(ServerEntity ent, String userinfo);

    /**
     * Called when a player drops from the server. Will not be called between levels.
     */
    void ClientDisconnect(ServerEntity ent);

    /**
     * This will be called once for each client frame, which will usually be a
     * couple times for each server frame.
     */
    void ClientThink(ServerEntity ent, usercmd_t ucmd);

    ServerEntity getEdict(int index);

    int getNumEdicts();

    /**
     * Persist whatever is required from the previous instance.
     * @param oldGame
     * @param spawnPoint
     */
    void fromPrevious(GameExports oldGame, String spawnPoint);

    void SaveClientData();

}
