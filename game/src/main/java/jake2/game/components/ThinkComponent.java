package jake2.game.components;

import jake2.game.adapters.EntThinkAdapter;

public class ThinkComponent {
    public EntThinkAdapter action;
    // todo: switch to relative to game level time
    public float nextTime;

    /**
     * Called in the beginning of the physics & other interaction logic
     * fixme: used only for the viper bomb for rotation, is there a better way to implement this?
     */
    public EntThinkAdapter prethink;

}
