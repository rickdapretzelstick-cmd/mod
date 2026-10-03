# Working on this repo (read first — more than one Claude session commits here)

Two or more Claude Code sessions, on different accounts and machines, share this GitHub repo. Follow these rules so
nobody's work is lost or broken.

## Project
- JJK Fabric mod: Minecraft 26.3, **Java 25**, Mojang mappings, Fabric Loom 1.18. See README.md for the game design.
- Build: `JAVA_HOME=<jdk25> ./gradlew build` (compiles, runs the 159+ server gametests). A green build is the bar.
- Client tests are opt-in: `src/gametest/resources/fabric.mod.json` lists them; a helper script may swap that list
  temporarily — **never commit a swapped/trimmed `fabric.mod.json`** (it must list every server test class).

## Can your session build?
A build needs Java 25 and network access to: `maven.fabricmc.net`, `libraries.minecraft.net`,
`piston-meta.mojang.com`, `piston-data.mojang.com`, `launchermeta.mojang.com`, `resources.download.minecraft.net`,
`plugins.gradle.org`, `repo.maven.apache.org`. If those are blocked or only Java 21 exists:
- Do **not** guess at Minecraft/Fabric APIs and push them as if verified. Write the code, say clearly in the commit
  message `UNBUILT: not compiled — needs a build check`, and push it to **your own branch** (see below).
- A session that can build will compile, test and fix it before it reaches the shared branch or the release jar.
- To fix the environment instead: environment settings → Network access → Custom, add the hosts above (keep the
  default package-manager list), and install a JDK 25 in the setup script.

## Branches — never step on each other
- Shared integration branch: `claude/minecraft-mod-testing-mo7vla`. Only push there commits that **build and pass
  the gametests**.
- Unbuilt or experimental work goes on your own branch: `claude/<topic>-<short-id>`, then gets merged in by a session
  that can build.
- Before every push: `git fetch origin && git rebase origin/<branch>` (or merge), rebuild if anything came in, then
  push. **Never force-push** a shared branch and never rewrite commits that are already pushed.
- If a push is rejected, fetch and integrate — don't overwrite.
- Keep commits small and focused; say what was verified (built? tests? client screenshots?) in the message.

## Files that conflict easily — edit surgically, never regenerate wholesale
- `src/main/resources/assets/jjk/sounds.json` and `registry/ModSounds.java` (append entries; `tools/roblox_sounds.py`
  updates only the names you pass it).
- `config/JJKConfig.java` (bump `CURRENT_VERSION` only when gameplay defaults change).
- `client/fx/ClientFx.java`, `YutaFx.java`, `RyuFx.java` (big switch statements: add cases, don't reorder).
- `release/jujutsu-0.1.0.jar`: only replace it with a jar **you just built from the current branch head** with a green
  build. Never commit a jar built from unpushed or unbuilt code.
- Generated assets (icons, portraits, animation clips, sounds) come from scripts in `tools/`; change the script and
  re-run it rather than hand-editing outputs.

## Never commit
`*.mp4`, `*.m4a`, `__pycache__/`, `build/`, `run/`, secrets, or a trimmed gametest `fabric.mod.json`.

## Commits
End messages with the attribution lines your session was given. Never put model identifiers in commits or files.
