# Move file format (jjk_move v1)

Made by the move editor (`tools/build_move_editor.py` → `build/move_editor.html`), run by `core.move.MoveAbility`.
Files go in `<game>/config/jjk/moves/<id>.json`; `/jjkmove reload` reads them, `/jjkmove bind <1-5|r|g|q|f> <id>` puts one
on a key (kept per player in `bindings.json`), `/jjkmove play <id>` runs one. Two examples ship in
`src/main/resources/jjk_moves/` and are copied into an empty folder.

Times are milliseconds (the game rounds to 50 ms ticks). Offsets are `[right, up, forward]` blocks from the user's feet
and facing; velocities the same axes in blocks per tick.

```jsonc
{
  "format": "jjk_move", "version": 1,
  "id": "my_move", "name": "My Move", "duration": 800,
  "cost": 10, "cooldown": 4,            // cursed energy; seconds
  "technique": false,                   // blocked by burnout / technique locks, deals technique damage
  "uninterruptible": false, "movement": 0.4, "air": true,
  "animation": {                        // a normal clip (client.anim.AnimLibrary): same keys, bones, easings
    "priority": "SPECIAL", "blendIn": 75, "blendOut": 150, "hold": false,
    "keys": [{ "t": 0, "ease": "EASE_IN_OUT", "bones": { "rightArm": { "rot": [-90, 0, 0], "pos": [0, 0, 0] } } }],
    "use": "manji_slam"                 // optional: play this existing clip instead of the keys
  },
  "events": [
    { "type": "hitbox", "id": "h1", "start": 250, "end": 350, "shape": "box|sphere", "offset": [0, 1.1, 1],
      "size": [1.2, 1.4, 1.6], "radius": 1, "aim": false, "damage": 6, "knockback": { "right": 0, "up": 0.2, "forward": 0.8 },
      "hitstun": 12, "status": "", "statusTicks": 0, "tags": ["melee"], "rehit": 0, "impactFx": "hit_heavy",
      "impactScale": 1, "blockable": true, "grab": false },
    { "type": "grab", "start": 300, "end": 900, "from": "h1", "hold": [0, 1.7, 0.7], "release": [0, -1.2, 0.6],
      "releaseDamage": 8, "releaseFx": "hit_slam" },
    { "type": "effect", "t": 250, "fx": "swing", "offset": [0, 1.2, 1], "dir": [0, 0, 1], "scale": 1, "onTarget": false },
    { "type": "sound", "t": 240, "sound": "swing_heavy", "volume": 1, "pitch": 1 },     // or "minecraft:..."
    { "type": "camera", "t": 300, "kind": "shake|flash|fov|hitstop|impact", "intensity": 0.5, "duration": 8, "radius": 16, "color": "ffffffff" },
    { "type": "motion", "t": 200, "end": 300, "mode": "add|set", "velocity": [0, 0, 0.6] },   // end > t: held each tick
    { "type": "status", "start": 0, "end": 300, "status": "melee_armor" },                  // on the user
    { "type": "logic", "start": 400, "end": 400, "when": "always|hit|miss|grabbed|notGrabbed|airborne|grounded|press",
      "from": "", "action": "end|move", "move": "next_move" },  // press: the same key again inside [start, end]
    { "type": "anim", "t": 500, "clip": "manji_slam", "speed": 1 }                        // an existing clip
  ]
}
```

The move's own clip reaches clients under the name `move_<id>` (MoveSyncPayload on join and on reload). A plain clip
saved from the editor ("Download clip only") is the mod's ordinary clip format for `assets/jjk/animations/<group>/`.
