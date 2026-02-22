package org.demoth.cake.stages.ingame.effects

import jake2.qcommon.Defines

internal data class MuzzleFlash2Profile(
    val soundPaths: List<String> = emptyList(),
    val attenuation: Float = Defines.ATTN_NORM.toFloat(),
    val spawnSmokeAndFlash: Boolean = false,
    val dynamicLight: MuzzleFlash2DynamicLight = MuzzleFlash2DynamicLight(),
)

internal data class MuzzleFlash2DynamicLight(
    val radius: Float = 200f,
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 0f,
    val lifetimeMs: Int = 0,
)

/**
 * Data-driven grouping for `MuzzleFlash2Message.flashType`.
 *
 * Why:
 * original Quake2 groups large contiguous style ranges with identical behavior. Keeping this
 * mapping isolated avoids switch bloat in [ClientEffectsSystem] and makes extension safer.
 *
 * Constraint:
 * entries here intentionally cover only `MuzzleFlash2`-driven behavior. Continuous per-entity
 * effect lights (`EF_*`) are handled in `Game3dScreen.collectEntityEffectDynamicLights()`.
 *
 * //fixme: terrible hard coupling of client and game logic.
 */
internal object MuzzleFlash2Profiles {
    private val byFlashType: Map<Int, MuzzleFlash2Profile> = buildMap {
        val machineGun = MuzzleFlash2Profile(spawnSmokeAndFlash = true)

        registerRange(
            Defines.MZ2_INFANTRY_MACHINEGUN_1..Defines.MZ2_INFANTRY_MACHINEGUN_13,
            machineGun.withSound("sound/infantry/infatck1.wav")
        )
        registerValues(
            intArrayOf(
                Defines.MZ2_SOLDIER_MACHINEGUN_1,
                Defines.MZ2_SOLDIER_MACHINEGUN_2,
                Defines.MZ2_SOLDIER_MACHINEGUN_3,
                Defines.MZ2_SOLDIER_MACHINEGUN_4,
                Defines.MZ2_SOLDIER_MACHINEGUN_5,
                Defines.MZ2_SOLDIER_MACHINEGUN_6,
                Defines.MZ2_SOLDIER_MACHINEGUN_7,
                Defines.MZ2_SOLDIER_MACHINEGUN_8
            ), machineGun.withSound("sound/soldier/solatck3.wav")
        )
        registerRange(
            Defines.MZ2_GUNNER_MACHINEGUN_1..Defines.MZ2_GUNNER_MACHINEGUN_8,
            machineGun.withSound("sound/gunner/gunatck2.wav")
        )
        registerValues(
            intArrayOf(
                Defines.MZ2_ACTOR_MACHINEGUN_1,
                Defines.MZ2_SUPERTANK_MACHINEGUN_1,
                Defines.MZ2_SUPERTANK_MACHINEGUN_2,
                Defines.MZ2_SUPERTANK_MACHINEGUN_3,
                Defines.MZ2_SUPERTANK_MACHINEGUN_4,
                Defines.MZ2_SUPERTANK_MACHINEGUN_5,
                Defines.MZ2_SUPERTANK_MACHINEGUN_6,
                Defines.MZ2_TURRET_MACHINEGUN
            ), machineGun.withSound("sound/infantry/infatck1.wav")
        )
        registerValues(
            intArrayOf(
                Defines.MZ2_BOSS2_MACHINEGUN_L1,
                Defines.MZ2_BOSS2_MACHINEGUN_L2,
                Defines.MZ2_BOSS2_MACHINEGUN_L3,
                Defines.MZ2_BOSS2_MACHINEGUN_L4,
                Defines.MZ2_BOSS2_MACHINEGUN_L5,
                Defines.MZ2_CARRIER_MACHINEGUN_L1,
                Defines.MZ2_CARRIER_MACHINEGUN_L2
            ), machineGun.withSound("sound/infantry/infatck1.wav", Defines.ATTN_NONE.toFloat())
        )
        registerRange(
            Defines.MZ2_TANK_MACHINEGUN_1..Defines.MZ2_TANK_MACHINEGUN_19,
            machineGun.withSounds(
                "sound/tank/tnkatk2a.wav",
                "sound/tank/tnkatk2b.wav",
                "sound/tank/tnkatk2c.wav",
                "sound/tank/tnkatk2d.wav",
                "sound/tank/tnkatk2e.wav"
            )
        )
        registerRange(
            Defines.MZ2_JORG_MACHINEGUN_L1..Defines.MZ2_JORG_MACHINEGUN_L6,
            machineGun.withSound("sound/boss3/xfire.wav")
        )
        registerRange(Defines.MZ2_JORG_MACHINEGUN_R1..Defines.MZ2_JORG_MACHINEGUN_R6, machineGun)
        registerValues(
            intArrayOf(
                Defines.MZ2_BOSS2_MACHINEGUN_R1,
                Defines.MZ2_BOSS2_MACHINEGUN_R2,
                Defines.MZ2_BOSS2_MACHINEGUN_R3,
                Defines.MZ2_BOSS2_MACHINEGUN_R4,
                Defines.MZ2_BOSS2_MACHINEGUN_R5,
                Defines.MZ2_CARRIER_MACHINEGUN_R1,
                Defines.MZ2_CARRIER_MACHINEGUN_R2
            ), machineGun
        )

        registerValues(
            intArrayOf(
                Defines.MZ2_SOLDIER_BLASTER_1,
                Defines.MZ2_SOLDIER_BLASTER_2,
                Defines.MZ2_SOLDIER_BLASTER_3,
                Defines.MZ2_SOLDIER_BLASTER_4,
                Defines.MZ2_SOLDIER_BLASTER_5,
                Defines.MZ2_SOLDIER_BLASTER_6,
                Defines.MZ2_SOLDIER_BLASTER_7,
                Defines.MZ2_SOLDIER_BLASTER_8,
                Defines.MZ2_TURRET_BLASTER
            ), MuzzleFlash2Profile().withSound("sound/soldier/solatck2.wav")
        )
        registerValues(
            intArrayOf(Defines.MZ2_FLYER_BLASTER_1, Defines.MZ2_FLYER_BLASTER_2),
            MuzzleFlash2Profile().withSound("sound/flyer/flyatck3.wav")
        )
        registerValues(
            intArrayOf(Defines.MZ2_MEDIC_BLASTER_1),
            MuzzleFlash2Profile().withSound("sound/medic/medatck1.wav")
        )
        registerValues(
            intArrayOf(Defines.MZ2_HOVER_BLASTER_1),
            MuzzleFlash2Profile().withSound("sound/hover/hovatck1.wav")
        )
        registerValues(
            intArrayOf(Defines.MZ2_FLOAT_BLASTER_1),
            MuzzleFlash2Profile().withSound("sound/floater/fltatck1.wav")
        )

        registerValues(
            intArrayOf(
                Defines.MZ2_SOLDIER_SHOTGUN_1,
                Defines.MZ2_SOLDIER_SHOTGUN_2,
                Defines.MZ2_SOLDIER_SHOTGUN_3,
                Defines.MZ2_SOLDIER_SHOTGUN_4,
                Defines.MZ2_SOLDIER_SHOTGUN_5,
                Defines.MZ2_SOLDIER_SHOTGUN_6,
                Defines.MZ2_SOLDIER_SHOTGUN_7,
                Defines.MZ2_SOLDIER_SHOTGUN_8
            ), MuzzleFlash2Profile(spawnSmokeAndFlash = true).withSound("sound/soldier/solatck1.wav")
        )

        registerRange(
            Defines.MZ2_TANK_BLASTER_1..Defines.MZ2_TANK_BLASTER_3,
            MuzzleFlash2Profile().withSound("sound/tank/tnkatck3.wav")
        )
        registerValues(
            intArrayOf(Defines.MZ2_CHICK_ROCKET_1, Defines.MZ2_TURRET_ROCKET),
            MuzzleFlash2Profile()
                .withSound("sound/chick/chkatck2.wav")
                .withLight(red = 1f, green = 0.5f, blue = 0.2f)
        )
        registerRange(
            Defines.MZ2_TANK_ROCKET_1..Defines.MZ2_TANK_ROCKET_3,
            MuzzleFlash2Profile()
                .withSound("sound/tank/tnkatck1.wav")
                .withLight(red = 1f, green = 0.5f, blue = 0.2f)
        )
        registerValues(
            intArrayOf(
                Defines.MZ2_SUPERTANK_ROCKET_1,
                Defines.MZ2_SUPERTANK_ROCKET_2,
                Defines.MZ2_SUPERTANK_ROCKET_3,
                Defines.MZ2_BOSS2_ROCKET_1,
                Defines.MZ2_BOSS2_ROCKET_2,
                Defines.MZ2_BOSS2_ROCKET_3,
                Defines.MZ2_BOSS2_ROCKET_4,
                Defines.MZ2_CARRIER_ROCKET_1,
                Defines.MZ2_CARRIER_ROCKET_2,
                Defines.MZ2_CARRIER_ROCKET_3,
                Defines.MZ2_CARRIER_ROCKET_4
            ), MuzzleFlash2Profile()
                .withSound("sound/tank/rocket.wav")
                .withLight(red = 1f, green = 0.5f, blue = 0.2f)
        )
        registerRange(
            Defines.MZ2_GUNNER_GRENADE_1..Defines.MZ2_GUNNER_GRENADE_4,
            MuzzleFlash2Profile()
                .withSound("sound/gunner/gunatck3.wav")
                .withLight(red = 1f, green = 0.5f, blue = 0f)
        )

        registerRange(
            Defines.MZ2_MAKRON_BLASTER_1..Defines.MZ2_MAKRON_BLASTER_17,
            MuzzleFlash2Profile().withSound("sound/makron/blaster.wav")
        )
        registerValues(
            intArrayOf(
                Defines.MZ2_STALKER_BLASTER,
                Defines.MZ2_DAEDALUS_BLASTER,
                Defines.MZ2_MEDIC_BLASTER_2,
                Defines.MZ2_WIDOW_BLASTER
            ), MuzzleFlash2Profile().withSound("sound/tank/tnkatck3.wav")
        )
        registerRange(
            Defines.MZ2_WIDOW_BLASTER_SWEEP1..Defines.MZ2_WIDOW_BLASTER_SWEEP9,
            MuzzleFlash2Profile().withSound("sound/tank/tnkatck3.wav")
        )
        registerRange(
            Defines.MZ2_WIDOW_BLASTER_100..Defines.MZ2_WIDOW_RUN_8,
            MuzzleFlash2Profile().withSound("sound/tank/tnkatck3.wav")
        )

        registerValues(
            intArrayOf(Defines.MZ2_WIDOW_DISRUPTOR),
            MuzzleFlash2Profile()
                .withSound("sound/weapons/disint2.wav")
                .withLight(red = -1f, green = -1f, blue = -1f, lifetimeMs = 100)
        )
    }

    fun resolve(flashType: Int): MuzzleFlash2Profile? {
        return byFlashType[flashType]
    }

    private fun MutableMap<Int, MuzzleFlash2Profile>.registerRange(
        range: IntRange,
        profile: MuzzleFlash2Profile,
    ) {
        for (flashType in range) {
            put(flashType, profile)
        }
    }

    private fun MutableMap<Int, MuzzleFlash2Profile>.registerValues(
        values: IntArray,
        profile: MuzzleFlash2Profile,
    ) {
        values.forEach { flashType ->
            put(flashType, profile)
        }
    }

    private fun MuzzleFlash2Profile.withSound(
        path: String,
        attenuation: Float = this.attenuation
    ): MuzzleFlash2Profile {
        return copy(soundPaths = listOf(path), attenuation = attenuation)
    }

    private fun MuzzleFlash2Profile.withSounds(vararg paths: String): MuzzleFlash2Profile {
        return copy(soundPaths = paths.toList())
    }

    private fun MuzzleFlash2Profile.withLight(
        radius: Float = dynamicLight.radius,
        red: Float = dynamicLight.red,
        green: Float = dynamicLight.green,
        blue: Float = dynamicLight.blue,
        lifetimeMs: Int = dynamicLight.lifetimeMs,
    ): MuzzleFlash2Profile {
        return copy(
            dynamicLight = MuzzleFlash2DynamicLight(
                radius = radius,
                red = red,
                green = green,
                blue = blue,
                lifetimeMs = lifetimeMs,
            )
        )
    }
}
