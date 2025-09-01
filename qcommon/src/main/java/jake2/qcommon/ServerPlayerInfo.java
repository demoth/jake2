package jake2.qcommon;

/**
 * Represents connected client information exposed from the server for the game side
 */
public interface ServerPlayerInfo {
    player_state_t getPlayerState();

    int getPing();

    void setPing(int ping);

    int getIndex();
}
