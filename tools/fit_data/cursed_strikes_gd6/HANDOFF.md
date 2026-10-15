# Handoff: footage -> animation fitting (Cursed Strikes), for the next Claude session

Branch: `claude/affectionate-bell-8rq1m2` (everything below is pushed there). Read `CLAUDE.md` (repo rules) and
`README.md` in this folder (footage link, repetition ranges, earlier numbers) first.

## Goal (the user's)
Turn JJS (Roblox) move footage into animation clips for the mod rig. Target: **every frame >= 90% colour agreement,
< 10 s per frame**. The user is fine with slower methods if they work; dislikes long silent runs; wants plain progress.

## Setup in a fresh container
1. Footage gd6: `curl -L "https://drive.usercontent.google.com/download?id=1iMwe8u8qDOYuudO9Mfn435WrQbF2y85e&export=download&confirm=t" -o video.mp4`
   (Cursed Strikes from 4 still cameras; template clothing; palette `tools/palettes/side_colours.json`).
2. `pip install torch opencv-python scipy pillow numpy` if missing. CPU only; 4 CPUs.
3. Several fitting jobs side by side: ALWAYS `OMP_NUM_THREADS=1` (else ~30x slower). One job alone: default threads ok
   (track_frame itself now pins torch to 1 thread).
4. Tracks already made (old tracker): `tools/fit_data/cursed_strikes_gd6/tracks/*.json` -> copy to `build/fit/tracks/`.

## Repetitions (frames in gd6)
back idle 100, move 104-186, end idle 194 | right 290, 299-384, 390 | left (side) 516, 525-608, 614 |
front (far overhead) 790, 798-881, 888.

## Tools (all Python, no Java; no gradle build needed for these)
- `tools/r6_fit_torch.py`: `track_frame` (per frame: window that follows the rig, re-centre, gradient steps,
  `pose_search` below 0.85, sub-pixel `hard_refine`, fine stage at 224 px on leftover time, hard cap ~9 s),
  `fit_idle` / `calibrate_idle` (start pose + still camera from an idle frame), `hard_draw` (fast OpenCV scorer, exact
  edges), `part_agreement`.
- `tools/r6_track_rep.py VIDEO NAME IDLE_S IDLE_E fwd|bwd OUTDIR`: one repetition tracked forwards/backwards.
- `tools/r6_fuse_views.py`: four views -> one pose per moment (EVAL=3 mode for quick scoring). FUSE_DIR, DIRS, NAME env.
- `tools/build_anim_viewer.py`: rebuilds `build/anim_viewer.html` from clips + `build/fit/*/conversion.json`; published
  as artifact https://claude.ai/artifact/JK8NYCyfE9kRSoXrzX5zCY (republish with the Artifact tool, url=that).
- Bench harness: `bench/` here (make_cache, bench.sh, run_candidate, score.py; frozen scorer = copy of the tools at a
  fixed commit). Its paths point at the old scratch dir; edit `B`.

## Current numbers (frozen scorer, 21-frame hard stretches)
- Per view tracking: side mean .76 (min .41, 7/21 >= .90), right .77 (min .47, 4/21), back .79 (min .46-.61, 6/21),
  front .70 (min .10, 3/21). Worst frame time 7.0 s (all views < 10 s).
- Standing frames: .97-.99 after the exact-edge fix + sub-pixel refine -> 90% is reachable when the pose is right.
- Clean moving frames .80-.92. Effect frames (red strike trail, smoke dome, the Dummy in front) .40-.77.
- The frozen scorer counts trail pixels as body (23-50% of body pixels on trail frames) -> those frames are capped at
  ~.5-.77 by the ruler. Next session should freeze a v2 ruler = current tools (glow rejection) and report both.
- Four-view combined pose (r6_fuse_views): all-view mean .582 -> .618 with the time-warp patch (`wip/align`, not merged).

## Why moving frames lag
Effects (biggest), fast arms (90 deg between frames at 30 fps; search lands in wrong local optima), repeated template
colours (red head/torso front/arm fronts; cyan shirt top/leg fronts), single-view depth ambiguity, motion blur,
errors carrying forward frame to frame.

## Whole-clip selection, the automatic method (tools/r6_global.py) -- use this before the overseer
Rick: "we can't be doing patch fixes". Cause of misses like the frame-592 kick: the scorer already prefers the right
pose (.83 vs .76), but track_frame pays a motion cost per frame (pose_search: cont_w per 90 deg, leg_cost past 30 deg,
thigh grid only +-45 forward/back), so a sudden real move loses to the last frame's pose, and the error carries on.
r6_global: per frame, candidates found on the footage alone (the track's pose, the backward track's, the neighbours'
poses, each refined and then limb_sweep'd over every limb's full range with no motion or stance cost), all scored in
one common window (union of their outlines, so a compact pose can't hide colour it misses); then Viterbi picks one
candidate per frame maximising total agreement less motion beyond a free allowance (limbs 45-60 deg/frame, hips 25;
.0008 per deg beyond). A move seen over several frames wins; a one-frame fluke loses to its neighbours. No AI or hand
input. ~2 s/frame-candidate, 3 min for the side view with 4 jobs.
```
OMP_NUM_THREADS=1 python3 tools/r6_global.py candidates build/video.mp4 build/fit/oversee left build/fit/oversee
python3 tools/r6_global.py select build/fit/oversee left 0.0008
python3 tools/r6_oversee.py clip build/video.mp4 build/fit/oversee/left_global.json left cursed_strikes_left_global
```
Side view (98 moving frames, common-window scores): track .862 mean, 46 >= .90, min .525 -> global .883, 53 >= .90,
min .588; 592 gets the kick by itself. Lambda .0003-.005 all land .877-.885. A second round seeded from the chosen
path: .887, 55 (small). Best candidate per frame averages .887-.892, so the pose search is close to saturated: the
remaining low frames (543-546, 555, 560-569, 582, 589) are trail / smoke / Dummy-occlusion frames where the ruler
counts effect pixels as body -- next lever is the ruler (glow rejection, v2), then the other three views.

## Motion scanner, the independent backup (tools/r6_motion.py)
Rick asked for a second scanner that works separately. It never scores colours: points on the boxes (a 3 px grid on
every visible face, lifted to each box's own 3-D coords) are followed by dense optical flow (DIS, forward-backward
checked), and the pose is solved by least squares to carry them there (turn/tilt held).
- Free-running from the idle frame (`VIDEO TRACKDIR left fwd OUTDIR`): holds idle (.93-.97) but drifts off at the
  first fast move (526 -> .47, ~.05 by 550): the faces are flat colour, so flow only grips edges and letters.
- One-step mode (`step VIDEO build/fit/oversee/left_global.json left OUTDIR`, 1 min): each frame's chosen pose
  carried one frame forward/back by the flow alone, no drift. Agrees with the colour path within 25 deg on hips .92,
  head .88, legs .84-.91, arms .59-.75. Frames where the two agree on every bone (52/95) average .933 colour
  agreement; where they disagree (43) .831 -- a confidence signal that needs no colour score. As extra candidates
  (`merge OUTDIR left`) they are picked 5 times and do not move the score (.882, 53). Use: trust map / what to flag.

## AI overseer: built and run on the side view (tools/r6_oversee.py)
Stages (see the file's header): `flag` -> `sheets` -> Claude reads the PNGs and writes a corrections JSON -> `apply` ->
`clip`. All Python; ~8 s per sheet (8 candidates locally refined), 4 jobs side by side (JOBS=4, OMP_NUM_THREADS=1).
```
python3 tools/r6_oversee.py flag   build/fit/oversee left
python3 tools/r6_oversee.py sheets build/video.mp4 build/fit/oversee left build/fit/oversee      # all flagged frames
python3 tools/r6_oversee.py apply  build/video.mp4 build/fit/oversee left build/fit/oversee tools/fit_data/cursed_strikes_gd6/oversee/left_corrections.json
python3 tools/r6_oversee.py clip   build/video.mp4 build/fit/oversee left cursed_strikes_left_oversee
```
Candidates per sheet: A current fit, B previous frame's pose, C backward track's pose, D arms swapped L/R, E/F/G body
turned +90/-90/180, H arms flipped forward/back; each shown over the footage with an inset from 90 deg round. Flags:
agreement < .85, a bone's direction jumping > 60 deg between frames, or the backward track > 45 deg off (limb twist
ignored). Apply refines the seed locally (no search), keeps it unless .03 worse, re-tracks the next frames from it
until they rejoin the old track (2 frames within 12 deg) or one comes out .02 worse (old track kept from there).

Round 1 results (fresh tracks committed in `oversee/`; same 112-px agreement as track_frame, all 98 moving frames):
fresh forward track mean .872, min .579, 48/98 >= .90 -> after 12 corrections (11 kept) mean .881, min .631, 51/98.
Picks of the backward track's pose (C) gave the big wins on trail frames (547 .63->.83, 550 .66->.77, 591 .78->.85);
hand-written arm poses (555, 566) did not help. Frame 592 (Rick spotted it in the viewer): the right leg kicks out
sideways toward the Dummy but the fit had it hidden behind the body (0 px seen: a hidden part has nothing pulling it
out, and pose_search's leg cost keeps legs near standing); a hand-written kick (rightThigh.z 90) took it .77 -> .83.
Sheets now also show four kick candidates (I/J each leg out sideways, K/L each leg forward). 62 of 99 frames flagged; most remaining low frames (543-545, 560-568,
582, 589, 592-595) had no better candidate. Next: more candidate kinds (pose_search's own runners-up, per-arm
swaps, legs swapped), a second round on the overseen track, then the other three views and fusion.

## Earlier design notes for the AI overseer
Idea: tracker flags suspect frames (score < .85, joint jump > ~60 deg, views disagree); renders review sheets
(footage | current fit overlay | 3-4 alternative hypotheses: previous pose, arms swapped L/R, body turned +-90/180,
arm flipped forward/back, the backward track's pose; plus each rig seen from 90 deg for depth); Claude looks at the
sheet and picks a candidate or writes a correction JSON, e.g. `{"542": {"pick": "B"}}` or
`{"542": {"pose": {"rightArm.x": -90, "rightArm.z": 0}, "turn": 90}}`; the tool seeds from it, refines locally
(track_step + hard_refine, camera turn/tilt locked), keeps it if the score is not worse than ~.02-.03, re-tracks the
following frames from it, logs every AI-corrected frame. Start with the side view (516-614), then all four views.
Suggested file: `tools/r6_oversee.py` (stages: track -> sheets -> apply). Was just started: a fresh side-view track with
the merged tracker: `OMP_NUM_THREADS=1 python3 tools/r6_track_rep.py video.mp4 left 516 614 fwd build/fit/oversee`
(re-run it; ~8 min). Then build the sheet/apply stages, run it on Cursed Strikes, compare with the numbers above,
write the clip (`F.write_clip` / `F.write_conversion`, see r6_fuse_views.py's tail), rebuild + republish the viewer.

## Other options discussed with the user
- Fallbacks: pose by hand in Roblox Studio over the video and convert the saved KeyframeSequence (.rbxmx) to a clip
  (converter not written yet); the drag-to-fix page `tools/keypose_editor/` (built, not published).
- Recording advice: Body Colors per part with no red (red = the strike trail), zoom in, 60 fps, Dummy behind the
  character, lowest graphics quality (may thin effects); a friend filming a second angle simultaneously.
- Do NOT rip JJS animation assets (only the game owner can load them; exploit tools break Roblox rules).

## Unmerged patches in `wip/` (against noted base commits)
`align` (fusion time warp, .582 -> .618), `mvsearch` (four-view pose search, ~.61), `outline` (printed dark outlines
-> UNKNOWN; mostly a ruler change, keep out of headline numbers). `calib`, `res`, `precise`, `effects` are merged.
