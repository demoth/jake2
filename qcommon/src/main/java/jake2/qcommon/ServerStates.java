package jake2.qcommon;

public enum ServerStates {
    SS_DEAD,
    /**
     * initial state,
     * precache commands are only valid during loading state
     */
    SS_LOADING,
    SS_GAME,
    SS_CINEMATIC,
    SS_DEMO,
    SS_PIC
}
