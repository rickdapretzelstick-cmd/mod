JJK R6 animation converter
==========================

Turns a screen recording of the R6 avatar doing a move into an animation clip for the mod.

Start
-----
1. Unzip this folder somewhere you can write to (Desktop or Documents, not Program Files).
2. Double-click JJKConverter.exe. (If Windows says "Windows protected your PC", click More info, then Run anyway:
   the app is not signed.)
3. Browse... to your recording, type a name for the move (lowercase, e.g. cursed_strikes), press Convert.

Nothing else to install.

What a recording needs
----------------------
- The same R6 avatar and colour-coded test outfit as the Cursed Strikes recording.
- For each take: the avatar standing still for a moment before the move and after it, and the camera not moving
  during the take. Move the camera between takes as much as you like.
- Several takes of the same move from different sides (right, left, front) give the best result. One take works too.

The window
----------
Find takes        lists the takes it finds in the recording (numbered from 0), without converting.
Main take         which take the clip is made from (empty: the one that fits its footage best). Pick the one whose
                  camera sees the move from the side.
Hints             for a limb the camera sees badly (a kick straight at the lens, motion blur):
                  FRAMES:LIMB:DIRECTION, e.g. 364-367:rightThigh:forward. Limbs: rightArm leftArm rightThigh
                  leftThigh head. Directions: forward back out in up down. Several: separate with commas.
                  Frame numbers are the video's.
Build the viewer  also makes build\anim_viewer.html, a web page that plays the clip on the mod's rig.
Start over        redo everything. Otherwise Convert resumes where it stopped (Stop and closing are safe).
Open results      the finished clips: build\fit\<name>\clip.json
Open logs         each step's log: build\convert\<name>\

It takes a while: roughly an hour per take on a 4-core PC, faster with more cores. It runs on the processor (not the
graphics card) and uses every core, so the PC will be busy.

Putting it in the game
----------------------
The clip covers the whole take. To cut the part you want into a game clip, open a Command Prompt in this folder:

  jjk_worker.exe tools\r6_game_clip.py build\fit\my_move\clip.json TAKE_START FROM TO yuji/my_move --video C:\path\to\recording.mp4

TAKE_START is the main take's first frame (from Find takes), FROM and TO the recording frames to keep. It writes
src\main\resources\assets\jjk\animations\yuji\my_move.json; copy that into the mod's same folder.

Checking the app
----------------
  jjk_worker.exe tools\app_selftest.py
prints SELFTEST OK when everything it needs works on this PC.
