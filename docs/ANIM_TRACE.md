# Reading animations without looking: the animation trace and the move timing audit

`client/debug/AnimTrace` records a fight as text, so animation problems can be found and checked without screenshots.

- **Server events** come through `core/debug/Trace`, a no-op in play: casts beginning, changing phase, ending and
  being interrupted, every clip `Anim.play` starts, and hits.
- **Client frames**, every rendered frame of a traced entity:
  - which clips play (time, weight, fading);
  - body yaw, head yaw and pitch;
  - each bone's local rotation as actually drawn, read from the solved rig. Vanilla frames are solved too.

`AnimTrace.start(name, entityIds...)` starts a trace. `AnimTrace.stop()` writes `anim-trace/<name>.txt` (under
`build/run/clientGameTest` in client tests) and returns the report. The file has:

- **Anomalies:**
  - **pop:** a transition snapping a bone more than 150° a tick.
  - **linger:** a finished move's clips still showing more than 550 ms after it.
  - **idle gap:** a move still running with nothing animating it.
  - **facing:** the body turned away from the aim while casting.
  - **stuck:** a held pose more than 30 ticks after its move.
- **Authored fast motion:** listed as style notes, not engine faults.
- **Casts:** a per-cast summary covering ticks, clips, how far the clips overrun the move, and how long they showed.
- **Timeline:** server events and client frames merged in one timeline.

`MoveTimingAuditClientTest` (opt-in) taps every move of every character at a dummy, traces each one and writes
`anim-trace/AUDIT.txt`. Run it after changing clips or move timings.

## The rules it enforces (and the engine now follows)

- **Blends:** rotations blend as quaternions, so a bone takes the shortest path home.
- **Blend-in:** every clip blends in over at least 90 ms (`AnimLibrary.MIN_BLEND_IN`). Only a STEP ease keeps a hard cut.
- **Follow-through:** when a move ends, its clips become follow-through. They play on for at most 300 ms
  (`AnimPlayer.FOLLOW_THROUGH_MS`), and fade at once if the fighter moves off. A clip started after the move (a
  recovery played from `end()`) is not cut.
- **Held clips:** every held clip the server plays is on `Anim.HELD`, so it is released when its move ends.
  `HeldClipsTest` (a unit test, so part of every build) fails if one is missing.
- **Facing:** while casting, the body faces the aim.
- **Clip timing:** a move's clip should land its contact frame with the hit. Blue's kick now starts so it does
  (`BlueAbility.KICK_CONTACT_TICKS`).
