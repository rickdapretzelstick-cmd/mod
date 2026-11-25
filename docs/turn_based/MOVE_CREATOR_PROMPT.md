# Prompt: build the JJK Move Creator

> Paste everything below the line into a new Claude session (Claude Code in this repo works best, so it can read the
> files named here). It builds a browser tool for authoring turn-based battle moves that drop straight into the
> Minecraft mod, with a live preview, automatic checks for visual problems, and a share link so anyone can watch.

---

You are building **JJK Move Creator**, a single-page web app (one `index.html`, plain JS + three.js from a CDN, no build
step) for authoring the turn-based battle moves of a Minecraft Fabric mod. It lives in `tools/move_creator/` of the repo
`rickdapretzelstick-cmd/mod`, branch `claude/turn-based-beta-t1`. The goal is to iterate on moves far faster than
rebuilding Minecraft: author, preview, catch visual oddities automatically, then export files the mod loads unchanged.

## 1. What a move is (the three files it exports)

Read these first; your exports must match them exactly:

- `src/main/java/dev/rick/jjk/battle/BattleMove.java` and `BattleKits.java`: the move's **rules**. These are its id,
  name, line, category (STRIKE / TECHNIQUE / GUARD / FOCUS / MOVE / DOMAIN) and weight (LIGHT 3 / MEDIUM 2 / HEAVY 1).
  Then power, cost, hits, `from` ranges (CLOSE / MID / FAR), `needs` stance, `linkFrom` stances, `inflicts` stance
  (GROUNDED / STAGGERED / AIRBORNE / DOWN), `rangeAfter`, blackFlash, pierce and vsAirborne.
- `src/main/java/dev/rick/jjk/battle/game/BattleScenes.java`: the move's **scene**. That's the clips the attacker plays
  at which ticks (20 ticks = 1 s), when the attacker lunges in (-1 = stays put), and the contact ticks (several for a
  multi-hit). It also covers the windup effect, the contact effect, a late effect (+4 ticks), and the target's
  reaction by stance (`reaction_hitstun`, `reaction_hit_launched`, `reaction_knockdown`, `reaction_pulled`,
  `reaction_guard_broken`).
- `src/main/resources/assets/jjk/animations/<group>/<name>.json`: **animation clips**. Use exactly this format
  (see `AnimLibrary.java` for every field):
  - Top level: `name`, `duration` (ms), `blendIn`, `blendOut` (ms), and `priority`, one of IDLE, MOVEMENT, COMBAT,
    ATTACK, SPECIAL, AWAKENING or RAGDOLL. Optional `hold`, `loop`, `authored`.
  - `keys`: a list of `{t (ms), ease?, bones: {bone: {rot: [x,y,z] degrees, pos: [x,y,z] pixels}}}`.
  - Eases: LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, STEP, BACK, SPRING.

The mod should load moves as data, not Java. Add (or ask the mod session to add) these loaders, then export into them:

- `src/main/resources/data/jjk/battle/kits/<character>.json`: the kit's moves, guard and domain, with the same fields
  as the builder.
- `src/main/resources/assets/jjk/battle/scenes/<move_id>.json`:
  `{clips:[{t,clip,speed?}], lungeAt, contacts:[t...], windupFx, contactFx, lateFx, sounds:[{t,id,pitch}], camera:[shot...]}`.
- Clip JSONs go into the existing animations folder.

Until the loaders exist, also generate the matching Java snippet (a `BattleMove.of(...)` builder chain and a
`MOVES` map entry) so the move can be pasted in.

## 2. The rig (match the mod exactly or previews lie)

- It's a Minecraft player model (64×64 skin, units in pixels; 16 px = 1 block). The skin is the default Steve, or one
  the user loads.
- Every limb is cut into jointed segments (see `client/anim/rig/MeshSplit.java`).
  - Arms and legs split into upper (6 px), lower (4 px) and end (2 px), giving elbows/wrists and knees/ankles.
  - The torso splits into chest (8 px) and abdomen (4 px, driven by the hips).
- Bones: `root` (pos only), `hips`, `chest`, `head`, `rightArm`/`leftArm`, `rightForearm`/`leftForearm`,
  `rightHand`/`leftHand`, `rightThigh`/`leftThigh`, `rightShin`/`leftShin`, `rightFoot`/`leftFoot`.
  - Pivots are vanilla HumanoidModel's (shoulders at y=2 from the neck, hips at y=12). The child joints sit at the
    segment boundaries, at the centre of the limb's cross-section.
- Rotation conventions (verify against `tools/anim_author.py` and a known clip like
  `animations/yuji/divergent_punch.json`):
  - Arm x<0 raises forward; arm z>0 swings the right arm out (mirrored for left); forearm x<0 folds the elbow.
  - Thigh x<0 swings the leg forward; shin x>0 bends the knee.
  - chest/hips y<0 turns the right shoulder forward; hips x>0 leans forward.
  - root pos is (right, up, forward) in pixels.
- Load every clip in `src/main/resources/assets/jjk/animations/**` into a searchable library (the creator works from
  existing material first).

## 3. The editor

- **Stage:** two fighters on a stone floor, placed exactly as `BattleSession` places them: half-distance 1.25 / 3.2 /
  6.5 blocks for CLOSE / MID / FAR, facing each other. The attacker lunges to 1.1 blocks from the target, and AIRBORNE
  lifts the target 1.6 blocks.
- **Timeline:** a scene timeline in ticks.
  - Lanes for attacker clips, target reactions, effects (as labelled markers plus simple particle stand-ins), sounds,
    contacts and camera shots.
  - Drag to retime; scrub, play, loop, and 0.25× / 0.5× / 1× speed.
- **Clip editor:** a keyframe editor for one clip.
  - Select a bone and rotate it with gizmos or numeric fields; set each key's ease; copy, paste or mirror a pose
    (left↔right).
  - Onion skin of the previous and next key.
  - IK helpers for hands and feet: pin a foot to the floor while posing the hips.
- **Camera:** port the shot vocabulary from `client/camera/Shots.java` (twoShot, overShoulder, lowHero, orbit, topDown)
  and the battle director in `client/battle/BattleClient.java`'s `camera()`. The preview's camera then matches the
  game, including cuts on contact and the 180-degree rule.
- **Rules panel:** the move's stats, with live validation through a JS port of `BattleEngine.validate` / `whyNot` /
  `links` (keep the rules identical; port `BattleEngineTest` cases as self-tests that run on load).
- **Combo preview:** chain 2–3 moves and play them back to back, as the game does for links (links play at 1.35× speed).
- **Exchange preview:** pick both sides' moves and play the resolved turn using the ported engine. This is the real
  "watch it play out" view.

## 4. Visual oddity detection (run on every edit, list problems with a click-to-jump)

- **Joint limits:** knee/elbow bending backwards, more than 160° fold, or heads turned more than 90°.
- **Pops:** angular velocity spikes between keys (a bone moving more than ~720°/s outside a STEP ease or a contact
  frame), and the first or last key jumping away from the blend pose.
- **Ground:** feet below the floor, or feet sliding while they should be planted (a foot moving more than 0.3 px per
  tick while it is the lowest point and the root is still).
- **Intersections:** hands through the torso or head, and limb-through-limb (capsule tests on the segments).
- **Reach:** the striking hand or foot not within 0.6 blocks of the target's hitbox at a contact tick.
- **Timing:**
  - A contact before the attacker's clip reaches its extended pose.
  - A reaction starting more than 2 ticks after contact.
  - The scene length shorter than its last cue.
  - A clip `duration` shorter than its last key.
- **Camera:** the camera inside a fighter or the floor; either fighter out of frame at a contact; a cut that crosses
  the 180-degree line.
- **Data:** an unknown clip, effect or bone name, or a move that fails validation.

Show a red, amber or green badge per issue, draw markers on the timeline, and highlight the offending bone in the
viewport.

## 5. Sharing ("what we're cooking")

- **Export:** a ZIP of the files from §1, plus a `move.preview.json` bundle with everything needed to replay the move.
- **Share view:** a read-only viewer mode (`?view=<bundle url or inline base64>`) that plays the scene or exchange on
  loop with the oddity report.
- **Recording:** a "record" button that renders the preview to a WebM with MediaRecorder, so a clip can be posted
  anywhere.

## 6. Getting it into Minecraft and keeping both sides in agreement

- Write a small check, `tools/move_creator/verify.mjs`, run with node. It loads the exports, runs the ported validation
  and the oddity checks headlessly, and fails on errors. Run it before committing.
- In the mod, a client gametest should play an exported scene through `/jjk battle` and take screenshots at each
  contact. Use the pattern in `src/gametest/java/dev/rick/jjk/test/BattleClientTest.java`. Compare those screenshots
  with the creator's preview frames at the same ticks.
- Keep the creator's engine port in step with `BattleEngine.java`. Add a JSON fixture of resolved turns generated by
  the Java tests, and have the creator's self-tests replay it and match the beats exactly.

## 7. Working rules for this repo

- Read `CLAUDE.md` first.
  - Work on your own branch (`claude/move-creator-<id>`) and never force-push.
  - Generated assets come from scripts in `tools/`.
  - Never commit `*.mp4`, `build/`, `run/` or a trimmed gametest `fabric.mod.json`.
- Start with a vertical slice before widening:
  1. Load one existing clip, `yuji/divergent_punch`.
  2. Play the Divergent Fist scene on two rigs with the battle camera.
  3. Flag one planted-foot slide.
  4. Export the scene JSON.
- Deliverables, in order:
  1. Viewer plus clip library.
  2. Timeline and scene export.
  3. Engine port and exchange preview.
  4. Oddity checks.
  5. Clip keyframe editor.
  6. Share and record.
