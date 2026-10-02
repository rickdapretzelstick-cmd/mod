# Player animation

Player animations are data. A move's gameplay code names a clip (`Anim.play(user, "rapid_heavy")`) and does nothing
else; what the body does is authored in JSON under `src/main/resources/assets/jjk/animations/`. Nothing here touches
hitboxes, timing, effects, sounds or the camera.

```
animations/
  poses/combat.json        reusable poses (fight_stance, crouch_land, straight_right, ...)
  common/*.json            M1s, dashes, guard, sprint
  gojo/*.json  hakari/*.json  yuji/*.json  yuta/*.json
  rika/*.json              Rika's own skeleton ("rig": "rika")
  reactions/*.json         hitstun, launched, knockdown, guard break, pulled, overload
```

A clip is registered under its `name` (default: the file name) and also as `<folder>/<name>`. F3+T reloads the
files, and so does `/jjkanim reload` without a full resource reload.

## The skeleton

The player model is jointed. At load time, the arms and legs are cut into three textured segments (upper 6 px,
lower 4 px, end 2 px), and the torso into chest (8 px) and abdomen (4 px). Sleeves, pants, jacket and armour are cut
with them. At rest the model is pixel-identical to vanilla. The training dummy uses the same jointed model.

| bone | parent | notes |
|---|---|---|
| `root` | — | between the feet; carries the whole body **without moving the hitbox** |
| `hips` | root | the pelvis: turns and tips the legs and torso together (pivot 12 px up) |
| `chest` | hips | bends at the waist, above the abdomen |
| `neck`, `head` | chest | |
| `rightArm` → `rightForearm` → `rightHand` | chest | shoulder, elbow, wrist |
| `leftArm` → `leftForearm` → `leftHand` | chest | |
| `rightThigh` → `rightShin` → `rightFoot` | hips | hip, knee, ankle |
| `leftThigh` → `leftShin` → `leftFoot` | hips | |

Aliases also work: `waist`, `pelvis`, `torso`, `body`, `upperTorso`, `rightUpperArm`, `rightElbow`, `rightLeg`,
`rightKnee`, `rightCalf`... There are also semantic names: `mainArm`/`mainForearm`/`mainHand` for the right side and
`offArm`/`offForearm`/`offHand` for the left.

Everything is local to the parent. **Rotations** are in degrees, applied X, then Y, then Z (vanilla's order):

- **x+** pitches the bone forward. The chest bows, the head nods down, and an arm at **x -90** points straight ahead
  (-180 is straight up). A forearm at x- folds the elbow; a shin at x+ bends the knee.
- **y+** turns the bone to the character's right. For the chest, y- brings the right shoulder forward.
- **z+** swings a right limb out to the side (and a left limb in). For the chest, head and hips, z+ tips toward the
  character's left.

**Position** is `[right, up, forward]` in pixels (16 per block). It is an offset from the bone's rest place, in the
parent's frame. **Scale** is a factor, either one number or `[x, y, z]`, and applies to that bone's own segment only.

Bones a clip doesn't mention keep vanilla's pose, so walking, looking around and swinging carry on underneath.

## Clip format

```json
{
  "name": "rapid_heavy",
  "reference": "RapidPunches.gif frames 131-187 ...",
  "fps": 50,
  "refStart": 131,
  "duration": 1100,
  "hold": false,
  "loop": false, "loopStart": 0, "loopEnd": 1100,
  "stopAfter": 0,
  "blendIn": 50, "blendEase": "LINEAR", "blendOut": 150,
  "priority": "SPECIAL",
  "layer": "BASE",
  "interruptible": true, "interruptWindow": [0, 400],
  "speed": 1.0,
  "look": 0.3,
  "tremble": 0,
  "keys": [
    {"t": 120, "marker": "anticipation", "ease": "EASE_OUT", "pose": "wide_legs", "bones": {
        "chest": [30, 20, 0],
        "rightArm": {"rot": [35, 10, 20], "ease": "EASE_IN"},
        "root": {"pos": [0, -3, 0]}}},
    {"f": 137, "marker": "impact", "ease": "EASE_IN", "pose": ["straight_right@mirror"]}
  ]
}
```

Only `keys` is required.

**Time.** `t` is milliseconds. `f` is a frame number at `fps`, counted from `refStart`, so a key can use the
reference's own frame number. `duration` also accepts `frames` or `endFrame`, and the loop points accept
`loopStartFrame` and `loopEndFrame`. If there is no duration, the clip ends at its last key.

**Partial keys.** A key sets only the bones and channels it names; each bone channel is its own track. Between two
keys a channel interpolates, before the first it holds the first value, and after the last it holds the last.
`"bone": [x, y, z]` is shorthand for a rotation.

**Easing.** The ease belongs to the key being moved *into*. Set it per key with `ease`, or per bone inside that
bone's object.

| ease | shape |
|---|---|
| `LINEAR` | constant speed: spins, slides |
| `EASE_IN` | slow start, whips into the key: a blow landing |
| `EASE_OUT` | fast start, settles into it: recoil, follow-through, anticipation |
| `EASE_IN_OUT` | the default |
| `STEP` | holds, then jumps on the key's frame: a hard cut, or resetting a 360° spin back to 0 |
| `OVERSHOOT` | flies past and snaps back: a stance snapping in |
| `SPRING` | rings a few times: a heavy impact wobble |

**Poses.** `pose` is a pose name, or a list applied in order. The key's `bones` override the pose. Add `@mirror` to
use a pose left/right mirrored. Poses live in `animations/poses/`, one per file or several under `{"poses": {...}}`,
and may `extends` others.

**Mirroring.** `{"name": "light_2", "mirrorOf": "light_1"}` makes a whole mirrored clip. Mirroring swaps the sides,
flips each rotation's turn and roll (y, z), and flips each position sideways.

**Markers.** `marker` on a key, or a top-level `markers: [{"t": .., "label": ..}]`, names a moment: anticipation,
impact, follow-through, recovery. The debugger shows the current one.

## Model rigs (Rika)

A clip with `"rig": "<model>"` animates a Blockbench model's own skeleton instead of the player's. Rika
(`models/bb/rika.bbmodel`) is the only one so far; her clips live in `animations/rika/` and gameplay plays them on
her entity with `Anim.playOn(rika, "rika_smash")`. Everything above applies: keys, easing, markers, poses, mirroring,
layers and priorities. The differences:

- **Bones keep the model's names**, unchecked and without aliases: `body`, `neck`, `head`, `jaw`,
  `right_upper_arm` → `right_forearm` → `right_hand` and the same on the left, and `tail_*`. Mirroring swaps
  `right_`/`left_` (or `_right`/`_left`).
- **Axes follow the model.** `x-` raises an arm forward (-90 is straight out), `body` x+ leans her forward, and
  `pos` z- is forward.
- **`scale` is a number** (or `[x, y, z]`) and is hierarchical, so a swelling `right_hand` grows the fist and
  everything under it (Rika Smash).
- **Her idle loop** (`rika_idle`) runs underneath every move, so a move clip only needs the bones it changes.

`RikaPreviewClientTest` is the pose gallery for her. Put `clip:ms,ms,...` lines in `build/rikapreview.txt` and it
renders her at each of those times. `rika_calibrate` raises each limb in turn to check the axes against the model.

## Playback rules

- **Layers.**

  | layer | bones it drives |
  |---|---|
  | `BASE` | every bone |
  | `LOWER_BODY` | root, hips and legs |
  | `UPPER_BODY` | chest, head and arms |
  | `HEAD` | neck and head |

  Clips on different layers play together, such as a punch on the upper body over a run on the lower body. A new
  clip replaces the one on its own layer. A `BASE` clip also replaces clips on other layers that don't outrank it.
- **Priority.** In order: `IDLE`, `MOVEMENT`, `COMBAT`, `ATTACK`, `SPECIAL`, `AWAKENING`, `RAGDOLL`. Higher priority
  wins where clips overlap. A lower-priority clip can start over a higher one only if the higher clip is
  `interruptible` and, when it has an `interruptWindow`, only inside that window. Hit reactions are `RAGDOLL` clips,
  chosen from the combat statuses: hitstun (a fresh hit restarts the flinch), launched, knockdown, guard broken,
  pulled and overload.
- **Blending.** `blendIn` is how long a clip takes to take over, shaped by `blendEase`; the clip it replaces stays
  underneath, unfaded, until then. `0` is a hard cut. That suits an impact frame: `rapid_final` and `twofold_2`
  start on the frame their hit lands. A clip whose first key comes later eases out of whatever pose came before it.
  `blendOut` is the fade after the clip ends or is released.
- **Holding, looping and speed.** `hold` keeps the last pose until gameplay releases it (`Anim.play(user, "")`).
  `loop` repeats `loopStart`–`loopEnd` until released. `stopAfter` also ends a hold or loop after that long. `speed`
  scales the clip's clock, multiplied by the speed gameplay passes.
- **Procedural helpers** only fill in what the keys leave alone. `look` (0–1) keeps the head following the player's
  view on clips that don't key the head; the chest twist, arms and so on stay exactly as authored. `tremble` adds
  degrees of shake to animated bones, for straining, struggling or overload.

## Debugger

`/jjkanim ui` opens a control panel that doesn't pause the game or blur the world. It has Play (type a clip name),
Pause/Play, Restart, < Frame, Frame >, 0.25x, 0.5x, 1x and 2x, Skeleton, Axes, Names, Target, Reload and Close.

The same controls exist as commands:

- `/jjkanim play <clip>`, `pause`, `restart`, `step [n]`, `back [n]`, `speed <x>`
- `/jjkanim skeleton`, `axes`, `names`, `hud`
- `/jjkanim target` debugs the entity under the crosshair (for example a training dummy); looking at nothing
  switches back to yourself.
- `/jjkanim list`, `reload`, `stop`, `off`

The readout shows, for each playing clip:
- its group and name, and time in ms against its duration;
- the frame number at the clip's `fps`;
- speed, and the current key with its time;
- the phase marker;
- blend state and weight;
- priority, layer, loop, hold and interrupt state.

The skeleton overlay draws every joint, its connection to its parent, and optionally each bone's axes (red = right,
green = up, blue = forward) and name.

## Matching a reference

Reference accuracy comes first. The workflow:

1. **Get the reference.** A JJS wiki GIF of the move (they run at 50 fps, 20 ms a frame).
2. **Number its frames.**
   `python3 tools/anim_ref.py RapidPunches.gif --from 130 --to 200 --step 3 --crop 60,70,360,380 --out sheet.png`
   prints the frame count and timing and writes a contact sheet with each frame labelled `f<number> <ms>`. Read off
   the phases: where the anticipation starts, the contact frame, how long the follow-through carries, when it's back
   to neutral.
3. **Line it up with gameplay.** Find when the move's hit resolves in its ability code, which sets the hit timing,
   and put the reference's contact frame on it. For example, Rapid Punches' spin kick connects at 550 ms, which is
   frame 27 of the GIF. When a clip starts on its hit (`rapid_final`, `twofold_2`, `volley_final`), give it
   `blendIn: 0` so the impact pose shows on that very frame. If the reference is slower or faster than gameplay,
   either key in ms on the gameplay timeline or key in reference frames and set `speed`.
4. **Key the extremes first.** Start with the contact pose, then the deepest anticipation, the end of the
   follow-through, and the recovery. Use `"fps": 50, "refStart": <first frame>` and `"f": <reference frame>` so the
   file reads like the sheet. Add in-betweens only where the reference's motion isn't a simple ease, such as an arc,
   a spin, or a hitch. Put the whole-body motion where it belongs:
   - spins on `root` y;
   - dips, jumps and steps on `root` pos;
   - flips and bows of the whole body on `hips`, which pivot at the body's centre;
   - bends at the waist on `chest`.
5. **Check it against the reference.** The opt-in `PoseGalleryClientTest` renders exact clip times. It pauses the
   clip in the debugger and scrubs to each time; with `build/posegallery_view.txt` it matches the reference's camera,
   for example `-35 16 3.8` for behind-right. Compare the rendered frames side by side with the reference frames at
   the same times, and fix whatever differs. `build/posegallery_hands` also marks where the effects think the fists
   are.

Exaggeration is welcome: the only limit is the model coming apart. Keep in mind:
- A 360° spin should end at -360 and then `STEP` back to 0, so the next blend doesn't unwind it.
- Crouch with `root` down while the thighs go forward (x-) and the shins bend back (x+), about twice the thigh angle.
