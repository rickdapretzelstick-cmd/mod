# JJK R6 animation converter (PC version)

Turns a screen recording of the R6 avatar doing a move into a clip for the mod. Nothing has to be picked by hand: it
finds every take in the recording, converts each one, and uses the other camera angles to fill in what the main
angle can't see.

## What a recording needs

- The same R6 avatar and outfit as the Cursed Strikes recording (the colour-coded test outfit). The part network in
  `build/synth/partnet2.pt` was trained on it.
- For each take: the avatar **standing still** for a moment before the move and after it, and the camera **not
  moving** during the take. Move the camera between takes as much as you like.
- Several takes of the same move from different sides (right, left, front) give the best result. One take works too.
- Any resolution or camera distance; 30 or 60 fps.

## Setup (once)

1. Install **Python 3.12** from python.org (tick "Add python.exe to PATH").
2. Open a terminal in this folder and run:

   ```
   python -m pip install -r requirements.txt
   ```

That's all. The converter runs on the CPU and uses every core.

## Converting a move

Put the recording anywhere and run, from this folder:

```
convert.bat path\to\recording.mp4 my_move
```

(On macOS or Linux: `sh convert.sh path/to/recording.mp4 my_move`.)

It prints the takes it found, e.g.

```
take 0: frames 96-195
take 1: frames 291-390
take 2: frames 517-616
take 3: frames 789-888
```

and when it is done:

```
done: build/fit/my_move/clip.json
```

Options (add them after the name):

| Option | What it does |
|---|---|
| `--main 1` | Make the clip from take 1 (default: the take that fits its footage best). Pick the take whose camera sees the move from the side. |
| `--hint 364-367:rightThigh:forward` | For a limb the camera sees badly (a kick straight at the lens, motion blur): frames, limb, direction. Limbs: `rightArm leftArm rightThigh leftThigh head`. Directions: `forward back out in up down`. Several: separate with commas. Frame numbers are the video's. |
| `--jobs 2` | Takes converted at the same time (default: cores / 4). |
| `--viewer` | Also rebuild `build/anim_viewer.html`, a web page that plays the clip on the mod's rig. |
| `--fresh` | Redo everything (otherwise a re-run resumes where it stopped). |

Just to see which takes it finds: `python tools\r6_takes.py path\to\recording.mp4`

## Putting it in the game

The clip covers the whole take. Cut the part you want into a game clip:

```
python tools\r6_game_clip.py build\fit\my_move\clip.json TAKE_START FROM TO yuji/my_move --video path\to\recording.mp4
```

`TAKE_START` is the main take's first frame (from the take list), `FROM`/`TO` the recording frames to keep. It writes
`src/main/resources/assets/jjk/animations/yuji/my_move.json`; copy that into the mod's same folder.

## What it does

1. Finds the takes (still before, move, still after, same camera).
2. Tracks the avatar through each take, forwards and backwards from the still frames.
3. Proposes poses per frame and picks the smoothest path through them.
4. Lets joints come away where the footage shows it, then fixes limb flips per body part, using the other takes for
   what this camera can't see.
5. Sets the standing-still frames at the start and end to the rig's exact neutral pose, easing into the move.
6. Writes the clip.

Logs for each step are in `build/convert/<name>/take<N>/` (and `main/` for the final pass).

## Speed

A 100-frame take takes about an hour on 4 cores. More cores make it faster: tracking runs both directions at
once, and every later step splits its frames across all cores. The steps don't use the graphics card yet.
