package jake2.qcommon;

/**
 * Represents connected client information linked to edict
 */
public interface GameClient {
    player_state_t getPlayerState();

    int getPing();

    void setPing(int ping);

    int getIndex();
}
