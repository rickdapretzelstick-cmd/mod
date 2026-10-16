# Cursed Strikes footage fit — saved state (pick up here)

Footage: "gd6", the template-clothing recording of Cursed Strikes from four still cameras:
https://drive.google.com/file/d/1iMwe8u8qDOYuudO9Mfn435WrQbF2y85e/view (1080p, 30.06 fps, 966 frames).
Download in a cloud session: `curl -L "https://drive.usercontent.google.com/download?id=1iMwe8u8qDOYuudO9Mfn435WrQbF2y85e&export=download&confirm=t" -o video.mp4`.
Palette: `tools/palettes/side_colours.json` (Roblox default template clothing).

## Repetitions (from `r6_fit_torch.find_moves`)

| view  | idle start | move      | idle end | camera (yaw, pitch) |
|-------|-----------:|-----------|---------:|---------------------|
| back  | 100        | 104-186   | 194      | 346, 13             |
| right | 290        | 299-384   | 390      | 256, 1              |
| left  | 516        | 525-608   | 614      | 90, 14              |
| front | 790        | 798-881   | 888      | 180, 60 (far, small)|

## Where things stand

- Per-frame tracker `r6_fit_torch.track_frame`: ~5 s/frame (max ~7), clean frames >= 0.80 agreement; frames with the
  red energy trail, smoke dome or the Dummy in front still 0.4-0.8.
- `tracks/`: every repetition tracked forwards and backwards (`tools/r6_track_rep.py` output). Copy to
  `build/fit/tracks/` to use them without re-tracking (~11 min for all eight with 4 jobs side by side).
- Four-view combination `tools/r6_fuse_views.py` (one pose per moment fitting all views). Evaluate with
  `FUSE_DIR=build/fit/tracks EVAL=3 DIRS=fwd,bwd python3 tools/r6_fuse_views.py` (~1.5 min):
  baseline combined pose left .655 / back .632 / right .635 / front .405, all-view mean **.582**
  (each view's own tracking .812 / .798 / .823 / .707).
- Published preview clips: `cursed_strikes_left_clip.json` (side view alone), `cursed_strikes_fused_fwd_clip.json`
  (four views, forward tracks). Viewer: https://claude.ai/artifact/JK8NYCyfE9kRSoXrzX5zCY

## Unfinished work (stopped when saving; NOT verified)

`wip/` holds patches against commit e047e91 (`git am tools/fit_data/cursed_strikes_gd6/wip/<name>/*.patch`):

- `align/` — local time warp per view (dynamic programming on the cross-view fit around the integer shift), frames
  windowed in parallel. Re-run of the saved code: all-view mean .582 -> **.618** (an earlier .706 did not reproduce).
- `mvsearch/` — the pose searched directly with the four-view score (coordinate ascent over limb grids, multi-start).
  Reported .606-.613.
- Not started: per-view camera re-placement for the combined pose and occlusion-aware view weights; four-view
  gradient polish with the soft rasteriser (`Fit` takes several views).

Next steps: apply `align`, re-run EVAL, then layer `mvsearch` on it; have each checked for metric gaming (EVAL scoring,
agreement() and classification must stay as they are; one pose per moment for all views); then write the clip
(`DIRS=fwd,bwd python3 tools/r6_fuse_views.py`), rebuild the viewer (`python3 tools/build_anim_viewer.py`) and
republish it.

## Precision round (patches against d4c0e99, reviewed)

- `calib/` — hard_draw drew every box ~0.5 px too big per side (a real bug: EXACT_EDGES), hard_refine (sub-pixel local
  search), idle calibration. Clean idle frames reach .97-.99 (left/back/right). Needs: refine from spare time only,
  calibration sanity guard, deadline cap.
- `res/` — fine_refine in a 224 window (~1 window px per footage px); +1-2 frozen-scorer points, +2-8 on well-started
  frames. Needs: return the 112 score by default, band width from footage px per window px, continuity cost.
- `outline/` — the template's printed dark outlines -> UNKNOWN; mostly a measurement change (+2-6 on side-view idle
  frames, no fit gain); keep out of headline numbers, or redo as a 'body, colour unknown' class.
Remaining below .80: effect/occlusion frames (red strike trail, smoke dome, the Dummy in front) -- wrong poses.

## Benchmark harness (single-view tracker)

`bench/`: `make_cache.py VIDEO NAME IDLE LAST` caches the state before a sequence; `bench.sh TOOLS LABEL SEQ FIRST LAST 1`
runs a tracker over it under a lock and scores it with a frozen copy of the fitter (`frozen/` = a copy of
`tools/r6_fit.py`, `r6_fit_torch.py`, `r6_rects.py`, `palettes/`). Sequences used: left 526-546, right 300-320,
back 106-126 (caches from idle 516/290/100). Paths inside point at the old session's scratch directory; adjust `B`.
Run several fitting jobs side by side only with `OMP_NUM_THREADS=1` (maths libraries' thread pools otherwise make
each job ~30x slower).
