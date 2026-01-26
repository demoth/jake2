# Lights

This document covers only classnames `light` and `target_lightramp`. Not lights related to weapons or explosions.

Lights in quake can be either dynamic or static,
which is defined by the light style of the entity.
Internally dynamic styles **values** are stored as a sequence of chars, 
representing light's magnitude with values between `a` (dark), `m` (normal value).
and `z` (double bright).
So the light style `ma` will flicker between normal and dark every frame.

There are predefined light styles for common effects, see `jake2.game.GameSpawn.defineLightStyles`.

## Static lights
Static lights have the style id set to `0` (default value).

## Predefined dynamic lights
These styles are defined in `jake2.game.GameSpawn.defineLightStyles` from id `1` to `11`

## Switchable dynamic lights
Such dynamic lights can be targeted by a normal trigger (switch on/off) or a `target_lightramp` entity for a more smooth transition.
Note that changing of the light state will emit a network event to all clients and thus will use the bandwidth (disabled in deathmatch)
