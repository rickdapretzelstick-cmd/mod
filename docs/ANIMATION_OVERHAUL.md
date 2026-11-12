# Animation overhaul — handoff

An in-progress pass that redesigns every attack animation. This file is the save point: what is done, how it was
done, and exactly what is left. Read it with `docs/ANIMATION.md` (the clip format and the rig).

Branch: `claude/jjk-minecraft-animation-overhaul-0eod07`. Not merged into the shared branch yet.

## State at this save point

- `./gradlew build` on JDK 25 is green: compiles, **all 227 server gametests pass** (two new: `ImpactWaveTests`).
- `python3 tools/anim_preview.py --check`: 372 clips, 0 errors.
- Polish pass (second commit): animkit now picks the Euler twin nearest the previous key for every IK-solved bone, so no
  joint turns the long way round at the end of a move (93 clips changed by it); Sukuna's Rush leap and landing rebuilt;
  Rika's secondary motion deepened (neck whip, jaw, weight sink, tail crack-through); Appetizer's last stage is a beam
  from the cannon swept from the floor to the sky (`ryu_appetizer` keyed to it); the Black Flash is lightning-first;
  the World Cutting Slash is a seam opened in space; Red's point-blank variant shows the whole Red; multi-hit specials
  keep their kill for the final hit (`Hit.Builder.sparing`); Second Helping is target-only. 229 gametests.
- Done since the first save point: Ryu's whole set (`tools/anim_ryu.py`, 48 clips including his own M1 set), Gojo's three
  GIF kicks rebuilt on animkit, the Java wiring below, and the impact-wave VFX system (`core/fx/ImpactWave`,
  `core/fx/ImpactWaves`, `client/fx/ImpactWaveFx`; see the README's "Impact waves").
- **Nothing has been looked at in the game.** Every pose was checked only in the offline previewer (box figure, same
  maths as `Rig.solve`). The client gametests (`PoseGalleryClientTest` etc.) have not been run. Treat in-game look,
  blending between clips and first-person as unverified.
- The Java wiring is done: Gojo, Hakari and Ryu play their own M1 sets (`MeleeMoveset` anim prefixes), Face Grater
  plays `face_grater_drag` / `face_grater_throw`, each character's domain opens with its own clip
  (`JJKCharacter.domainOpeningClip`; `common/domain_release` is the neutral one for characters without their own),
  and `Anim.HELD` knows the new held charges.

## How the work is done

Clips are no longer hand-edited JSON. Each character has a Python script that writes its clips:

| script | writes | status |
|---|---|---|
| `tools/animkit.py` | the library the scripts use (no output) | done |
| `tools/anim_preview.py` | offline filmstrips, validation, lint | done |
| `tools/anim_author.py` | `common/` (default M1 set, dashes, guard) and `reactions/` — 24 clips | done |
| `tools/anim_gojo.py` | `gojo/` — 38 clips | done, 3 clips untouched (below) |
| `tools/anim_hakari.py` | `hakari/` — 41 clips | done |
| `tools/anim_yuji.py` | `yuji/` (Yuji and Sukuna) — 72 clips | done |
| `tools/anim_yuta.py` | `yuta/` — 65 clips | done |
| `tools/anim_rika.py` | adds neck/tail follow-through to `rika/` (15 clips), keeps her hand-keyed motion | done |
| `tools/anim_ryu.py` | `ryu/` — 48 clips (the 35 moves and his own M1 set) | done |
| `tools/gen_tool_kit_anims.py`, `gen_cursed_blade_assets.py`, `gen_contest_anims.py` | `tool/` (56 clips), grapple clips | **old, not started** |

Run a script from `tools/` (`cd tools && python3 anim_gojo.py`); it rewrites its clips. Never edit the JSON by hand.

### animkit in one page

- `P(root=, hips=, chest=, head=, ra=, la=, rl=, ll=, blade=)` builds a whole-body pose.
  - `root` is (right, up, forward) px; `hips` / `chest` are (lean, twist, roll); `head` is where the gaze points in
    the ground frame.
  - Arms: `A(raise, swing, out, elbow, wrist)` by angles, or `H(right, up, fwd)` = wrist at a point on the ground
    frame, or `HC(...)` = wrist at a point carried by the chest (origin: base of the neck). Solved by IK.
  - Legs: `L(swing, knee, out, foot)` by angles (use for kicking and airborne legs), or `F(right, up, fwd, toe=,
    yaw=)` = sole planted at a ground point (IK). Planted feet are what make weight shifts read.
  - `blade=(up, right)` aims a blade held in the right fist (Yuta's katana).
- Each script wraps `P` in a character stance function (`Y`, `G`, `K`, `S`, `T`, `ST`) whose defaults are that
  character's rest pose, so a pose only states what differs.
- `Clip(group, name, NOTE)` **keeps the old file's duration, hold, loop, priority, layer, blends, look and
  tremble**. Those belong to the move; do not change them casually.
  - `.key(t, pose, ease, lag=, marker=)` — `lag` makes hips and legs arrive early, the chest half as early.
  - `.hit(t, wind, hit, ...)` — contact at `t`, an overshoot, an optional settle.
  - `.breathe(t0, pose)` — turns a held pose into a slow breathing loop (sets `loop`).
  - `.spins()` — for clips that turn the root.
- `lerp`, `over`, `tweak`, `only`, `mirror` do pose arithmetic.

### Rules that were followed

- **Contact times are the server's.** Every redesigned clip puts its impact key on the time the old clip had it
  (the old clips were keyed to the abilities' ticks and to the JJS GIFs). Melee: lights open at 100 ms, finisher /
  uppercut / downslam / sprint / stomp at 150 ms, heavy at 200 ms (`MeleeSystem`).
- Names, durations and hold flags are unchanged, so `Anim.HELD` and `AnimRecovery` still match.
- Sign conventions that bit me: hips/chest `y+` turns to the character's right, so a **right-leg side kick needs
  hips y negative**; `F(yaw=)` positive is toes out; a flip goes on `hips` x (pivot at the pelvis), never on root
  tilt (pivot at the feet, the body goes through the floor).

### Checking work

```
cd tools
python3 anim_preview.py --check [clip ...]                 # parse errors + lint (feet through floor, bones that snap)
python3 anim_preview.py out.png clip:0,100,200 other_clip  # filmstrip; BLADE=1 draws the katana
```

Lint lines for `cb_`, `cl_`, `sd_`, `ryu_`, `spin_kick`, `twofold_*` are the old clips. A root or hips "turns 360"
line on a clip with a `STEP` reset is expected.

## What is left, in order

0. **In-game QA has still not been done** (step 5 below): nothing new has been looked at in a running client.
1. ~~**Ryu (35 clips + his M1 set).**~~ Done: `tools/anim_ryu.py`. Rewrite `tools/gen_ryu_anims.py` on animkit (or add `tools/anim_ryu.py` and
   retire the old script's clip writing). Its comments give every move's ticks; keep those times. He needs
   `ryu_light_1`, `ryu_light_2`, `ryu_light_3`, `ryu_light_4`, `ryu_air_1..3`, `ryu_uppercut`, `ryu_downslam`,
   `ryu_sprint`, `ryu_stomp`, `ryu_heavy_charge`, `ryu_heavy` (his chain is 3 long and ends in `ryu_m1_ray`, but
   the fallbacks are still reachable). Style: big, upright, heavy slugs; the ray fired by snapping the pompadour
   forward.
2. **Cursed tools (56 clips in `tool/`)**: `cb_*` (two-handed blade), `cl_*` (cleaver), `sd_*`, `rf_*`, `kit_*`,
   `stance_*`, plus the `grapple_*` contest clips named in `Anim.HELD`. Find which script writes each before
   touching it. `rf_*` have no bone keys (the rifle stance code draws the pose) and `cursed_rifle/*` animate the
   gun model's own skeleton, converted from its `.bbmodel`: leave both alone.
3. **Gojo's three GIF-matched clips** `spin_kick`, `twofold_1`, `twofold_2` were left as they were (already
   frame-matched, and they spin the root). Decide whether to rebuild them.
4. **Wiring (Java, small).** These new clips are written but unused:
   - Per-character M1 sets. Change `MeleeMoveset("", "", ...)` to an anim prefix and keep the fx prefix `""`:
     `gojo_` in `GojoCharacter`, `hakari_` in `HakariCharacter`, `ryu_` in `RyuCharacter` (once step 1 exists).
     Yuji, the cleaver kit and the rifle kit stay on the default set.
   - `ZeroTwoDomain.java:80` plays `light_1` / `light_2` → `gojo_light_1` / `gojo_light_2`.
   - `TeleportAbility.java` plays Hakari's `rushdown_drag` / `rushdown_throw` for Face Grater →
     `face_grater_drag` / `face_grater_throw`.
   - Domain openings: `IdleDeathGambleAbility` → `hakari_domain_release`, `MalevolentShrineAbility` →
     `shrine_domain_release`, `AuthenticMutualLoveAbility` → `yuta_domain_release`. Gojo keeps `domain_release`.
   - Add to `Anim.HELD`: `gojo_heavy_charge`, `hakari_heavy_charge`, `ryu_heavy_charge`, `face_grater_drag`.
   - `common/domain_release.json` is dead: `gojo/domain_release.json` has the same name and overrides it. Either
     delete it or rename Gojo's and make the common one the neutral opening used by `DomainCounter`.
5. **QA.** Build; then run the client pose gallery under `xvfb-run` (both are installed here) for real screenshots:
   list `PoseGalleryClientTest` in `src/gametest/resources/fabric.mod.json` **temporarily and never commit that**,
   put clip names in `build/posegallery.txt`. Check blends between chained M1s, held poses releasing, and that no
   clip name the code plays is missing (`grep -rhoE 'Anim\.play(On)?\([^;]*' src/main/java`).
6. Update `docs/ANIMATION.md` (it still says clips are hand-authored JSON), rebuild `release/jujutsu-0.1.0.jar`
   from a green head, merge into the shared branch.

## Environment notes

- JDK 25 is not preinstalled in a fresh container. Temurin 25 from `api.adoptium.net` to `/opt/jdk25` works;
  build with `JAVA_HOME=/opt/jdk25 ./gradlew build`.
- The first build can fail with `429 Too Many Requests` from Maven Central. Re-running it succeeds.
