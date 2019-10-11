package jake2.game;

public enum WeaponStates {
    WEAPON_READY(0),
    WEAPON_ACTIVATING(1),
    WEAPON_DROPPING(2),
    WEAPON_FIRING(3);

    public final int intValue;

    WeaponStates(int intValue) {
        this.intValue = intValue;
    }

    static WeaponStates fromInt(int in) {
        for (WeaponStates value : WeaponStates.values()) {
            if (value.intValue == in)
                return value;
        }

        return WEAPON_READY;
    }
}
