package jake2.qcommon;

public interface GameClient {
    player_state_t getPlayerState();

    int getPing();

    void setPing(int ping);

    int getIndex();
}
