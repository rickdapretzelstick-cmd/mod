# Prompt: fix animation endings, swirling arms, and aimed moves facing the wrong way

> For a Claude session in this repo, on its own branch off `claude/minecraft-mod-testing-mo7vla`. Read `CLAUDE.md` first.

Fix three animation problems in the JJK mod. Each has a known root cause; fix that cause in the shared animation code,
not clip by clip.

## 1. Arms rotate weirdly at the end of many animations

**Cause:** when a clip blends out, `client/anim/rig/Rig.solve` interpolates each bone from the clip's rotation back to
vanilla's pose one Euler angle at a time (`rot[k] += (value[k] - rot[k]) * weight`). `PoseFrame.add` does the same when
two clips cross-fade, and so does the head-look blend. For a bone posed far from rest on more than one axis, a raised
or twisted arm for instance, per-axis interpolation follows a curved path through other orientations, so the arm
visibly swirls or rolls. (Interpolating between keys inside a clip stays Euler: authors rely on that for spins.)

**Fix:** blend rotations as quaternions.
- In `Rig.solve`, build the vanilla rotation and the clip rotation with `rotationZYX(z, y, x)` (the order the rig and
  `ModelPart` use), then `slerp` by the weight and use the quaternion directly. Do the same for the head-look blend.
- In `PoseFrame.add`, for the ROT channel, slerp the accumulated rotation toward the new one by `a / w2`, which is the
  same factor the linear formula uses. Store the result back as ZYX Euler.
- Weights of exactly 0 or 1 must give exactly the old result.

## 2. Attack animations don't end when the attack does

**Cause:** a one-shot clip always plays its full `duration` (plus `blendOut`) on the client, whatever the move does.
The server ends the cast and sends `CastPayload(entity, "", …)`, but the client only uses that for charge visuals. So
when a clip runs longer than its move, the pose hangs on after the attack is over. Held clips are already released at
cast end (`Anim.releaseHeld`); one-shots are not.

**Fix:** finish a cast's own clips when the cast ends.
- On the client, when a cast starts for an entity, remember the entity's `AnimPlayer` sequence number.
- When that cast ends, stop every non-endless, not-yet-stopping clip started since then, with a short fade of
  `min(blendOut, 160 ms)`. Skip clips that are already within the fade of their end.
- Skip casts that lasted under 2 ticks. Instant moves do everything in one tick, and their clip is the whole visual.
- Clips started after the cast ended (a follow-up, a melee swing) are untouched.

## 3. Ryu's beam (and other aimed moves) fire where you look while the body faces elsewhere

**Cause:** vanilla lets a standing player's body lag up to about 50° behind the head, and only catches up when
walking. Every aimed move fires along the look direction, but the clips are authored relative to the body. So Granite
Blast's cannon pose points one way while the beam goes another.

**Fix:** while an entity is casting (it's in `ClientState.CASTS`), snap its body yaw to its head yaw every client tick,
after entities tick. Body rotation is computed on each client, so this is client-side and covers every viewer.

## Verify

- `./gradlew build` must be green: all server gametests plus the unit tests.
- Run the client tests that touch the animation framework (`AnimFrameworkClientTest`; it is in the default client
  list) and fix anything they expose.
- Visual check with a client test:
  - Screenshot a clip's last 300 ms with a raised and twisted arm, before and after the change. The arm must travel
    straight home, not roll.
  - Screenshot Ryu firing Granite Blast while looking 40° off his body's facing. Body and beam must line up.
- Commit with what was verified, merge with the shared branch, rebuild, and push. Then rebuild the release jar from the
  pushed head.
