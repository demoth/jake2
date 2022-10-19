package jake2.game;

public interface DamageFlags {

    int DAMAGE_RADIUS = 0x00000001; // damage was indirect
    int DAMAGE_NO_ARMOR = 0x00000002; // armour does not protect from this damage
    int DAMAGE_ENERGY = 0x00000004; // damage is from an energy based weapon
    int DAMAGE_NO_KNOCKBACK = 0x00000008; // do not affect velocity, just view angles
    int DAMAGE_BULLET = 0x00000010; // damage is from a bullet (used for ricochets)
    int DAMAGE_NO_PROTECTION = 0x00000020; // armor, shields, invulnerability, and godmode have no effect

}
