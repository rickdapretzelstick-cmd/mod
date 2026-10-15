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

## IN PROGRESS when the session ended: "AI overseer" mode (user approved: "Yep")
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
