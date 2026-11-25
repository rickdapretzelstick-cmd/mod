# Prompt: Control Sequences (one fighter takes over the turn)

> Paste below the line into a Claude session working in this repo, on branch `claude/turn-based-beta-t1`.

---

Build **Control Sequences** for the turn-based battle mode. When one fighter wins the turn's exchange, they take
control: their whole planned combo plays as one continuous, cohesive fight scene. It should not look like separate
moves with cuts between them; it should look like a choreographed anime beatdown. Read `CLAUDE.md`,
`docs/turn_based/DESIGN.md`, `battle/BattleEngine.java`, `battle/game/BattleScenes.java`, `battle/game/BattleSession.java`
and `client/battle/BattleClient.java` first.

## The rule

- The exchange decides control, using the existing initiative and triangle rules. A **clean win** means the opener
  landed and the foe's move was cut off. That gives control; a trade, clash, block or whiff gives nobody control.
- The controller's chain plays as one **sequence**. Its length grows with **Control**, a new per-fighter meter that
  replaces momentum:
  - 2 links normally.
  - 3 links once you've taken control 2 turns running.
  - A **finisher** slot at max Control.
  - Losing an exchange resets Control.
- The defender is not just a punching bag:
  - Their locked-in Guard still catches slow strike links (the existing counter-speed rule).
  - They get **one Reversal window** per sequence: a timing prompt at a link's contact, costing 25 cursed energy.
    Landing it steals control. The sequence stops, the camera whips around, and a short counter plays.
  - Keep the timing optional, as a settings toggle; without it, the Reversal is a stat roll on cursed energy.
- Named combos and Black Flash stay as they are, but inside a sequence they become its climax beats.

## Engine (`dev.rick.jjk.battle`, no Minecraft code)

- Add `Sequence` to the turn result: `{controller, beats[], finisher?, reversalAt?}`. A beat stays the unit of rules;
  a sequence groups them so the presentation layer can treat them as one take.
- Add `Fighter.control` and the length rule above. `BattleAI` must value taking control, and `BattleSimulationTest`
  must still hold balance at 30–70% and use every move.
- Reversal resolution:
  - It is deterministic from the seed when the timing input is off.
  - With timing on, the server sends a `ReversalWindow(session, beatIndex, windowMs)` and resolves on the
    defender's input, or on a timeout.

## Stitching moves into one scene (the cohesive part)

Each move's scene data gains the points it can be joined at:

- `chainIn`: the tick to start from when this move is a link. Skip its windup and start mid-motion.
- `chainOut`: the earliest tick the next link may begin. That's the recovery frame, before the move's
  follow-through ends.
- `endPose`: the stance and positions the move ends in: target height (grounded / juggle / wall), distance, and
  which side the attacker is on.
- `entry`: what it needs at the start (juggle height, close, the target's facing).

The sequence builder overlaps move N's `chainOut` with move N+1's `chainIn`. It also blends their clips (the clip
system already blends; use a short `blendIn`), so hands never snap back to idle between hits. Then:

- **Positions:** carry them through. An AIRBORNE `endPose` keeps the target up and juggles it higher, a wall `endPose`
  pins it, and the attacker repositions in the overlap (a dash, a hop, a teleport flash for Gojo).
- **Pacing:** speed up through the sequence (each link ×1.1). Then one hit-stop freeze (3–5 ticks) before the
  finisher, and slow-mo at its contact.
- **Effects and sound:** escalate. Each link's impact effect is a step bigger; the final hit gets the flash, the
  radial and the shake.

## Camera (one take)

A sequence gets one continuous camera path instead of per-beat cuts:

- Start over the attacker's shoulder.
- Orbit with the action, keeping the 180-degree rule and flipping only on a Reversal.
- Rise with juggles.
- Push in on each contact.
- Hard-cut once to a low hero shot for the finisher, then pull out wide as the target crashes.

Use the shot vocabulary in `client/camera/Shots.java` and spline between key frames.

On screen:

- A hit counter that builds up.
- The combo name slamming in on the finisher.
- Letterbox bars that close tighter as Control rises.
- On a Reversal, a "REVERSAL!" card, a colour flip, and the camera whipping around.

## Verify

- Engine: unit tests for the control length, the Reversal (seeded), guard-catching inside a sequence, and the
  finisher unlocking.
- Balance: run the simulation.
- In game:
  - Extend `BattleClientTest`, or add `ControlSequenceClientTest`, with a forced seed where Yuji takes control 3 turns
    running.
  - Screenshot every 4 ticks through the sequence and check for pops, snaps to idle, or a camera inside a body.
  - Check that the full `./gradlew build` is green before pushing.
- If the move creator (`docs/turn_based/MOVE_CREATOR_PROMPT.md`) exists, add `chainIn`/`chainOut`/`endPose` to its
  scene export and a "sequence preview" that stitches 2–4 moves the same way, with oddity checks at the joins:
  - Pose jumps.
  - The target teleporting between `endPose` and `entry`.
  - The camera crossing the line.
