# Recording the showcase: how to do it well

The brief: record the full showcase in the script ("JJK Mod Showcase: Script & Shot List") from the game itself,
headless, and cut it into a finished video with sound. This is the plan, written before any recording, and the bar it is
checked against.

## What "well" means

1. **Every claim is on screen.** A caption names a technique only while that technique is visibly happening. Nothing is
   faked: every frame is the real game, played by the real ability code.
2. **Smooth, exact motion.** The game is frozen and stepped one tick at a time; each tick is drawn at sub-tick moments
   (the DeltaTracker pin). Normal footage is drawn twice per tick (40 captures a second) and rebuilt on a 60 fps grid;
   hero moments are drawn four times per tick, and slow motion twelve times (quarter speed at 60 fps, no blending).
3. **Motivated camera.** Each shot type has one camera recipe (`ShowcaseCamera`: angle, distance, height, look-ahead)
   and the camera only moves for a reason: a push-in on a charge, an orbit inside a domain, a pull-out as a dome
   forms, a lock-off (frozen camera) for teleports. No cut lands mid-move; each shot holds 0.5 s before and after.
4. **The target sells the hit.** The targets are training dummies (two-legged, so they use the new hit reactions),
   facing the hero, standing still (no AI) unless a clash bot is needed. One shot uses a zombie to show that any
   humanoid mob reacts the same way.
5. **Clean frames.** HUD hidden (except the systems shots), chat hidden, no debug text. Every scene starts from a reset:
   domains cancelled, terrain restored, dummies cleared, cooldowns and costs off, a full meter.
6. **Real sound.** A test mixin logs every sound the game plays (id, game time, position, volume); the assembler mixes
   the mod's own `.ogg` for each at its exact frame, panned and attenuated from the camera. Under it, a music bed per act
   from the mod's JJS tracks, ducked under the big hits; the mix mastered to -14 LUFS with a -1.5 dB ceiling.
7. **A real edit.** Title card, act by act in the script's order, technique captions in each character's colour, a
   white flash on each character change, a short hook up front, and an end card. 1280x720 at 60 fps (H.264, AAC).

## How

- **Recorder:** `ShowcaseFilmClientTest` (opt-in, extends the domain cinematic's tooling). Scenes are methods named for
  the script's acts; `JJK_SCENES` picks which ones a run films (one run per act or two, so memory and disk stay in
  bounds). Abilities fire through the server's `AbilityCaster.input` (the same path the move audit uses), with the dummy
  as the aim target, so nothing depends on a key press landing while the game is frozen. Each section name carries its
  caption: `NAME;TOP LINE;sub line;r,g,b;speed`.
- **Sound log:** `SoundLogMixin` on `SoundManager.play` appends to `screenshots/sounds.txt`.
- **Assembler:** `tools/make_showcase.py` joins the runs (reusing `make_domain_cinematic`'s recording and caption code),
  adds the cards and flashes, and builds the soundtrack from the sound logs and the music beds.

## Checks (each one is done, not assumed)

1. **Smoke test first:** one short scene. Measure frames per second of capture and disk per frame, and look at a
   contact sheet before filming the rest.
2. **After every run:** a contact sheet of every 20th frame per section. Check that the hero is in frame, the target is
   visible, the move actually fired (the recorder logs every refused cast), there's no HUD or chat, and no black or
   duplicated frames. Re-shoot any failure before moving on.
3. **Sound:** each logged sound resolves to a file (count the misses); spot-check that a big hit's sound lands on its
   flash frame.
4. **Final video:** ffprobe the duration, frame rate and streams; sample frames across the whole film; watch the act
   boundaries and the captions.

## Limits (said up front, not hidden)

- Some shots in the script need a second human player or a UI that a headless run can't drive the same way: the
  character select screen is shown as a still, and the beam clash, the Copy Wheel and the cursed rifle are left out
  unless they can be driven reliably. Anything left out is listed when the video is delivered.
- The jackpot is random: the recorder retries Idle Death Gamble until one hits, within a time limit.
- No voice-over is recorded (it would need a voice and a reading); the captions carry the script's lines.
- `.mp4` files are never committed (CLAUDE.md): the video is delivered as a file, and the recorder and tools are what go
  in the repo.
