# Minecraft Animation Studio

A mobile-first web app (PWA) for making animations for modded Minecraft, built for phones. Load a
character (or import your own `.bbmodel`), pick a preset or describe a move in plain English, tweak it on a
touch timeline, and export GeckoLib-ready files for your mod.

This folder is a standalone project. It doesn't touch the JJK mod in the rest of this repository.

## Run it

It's static files with no build step.

```bash
cd animation-studio
python3 -m http.server 8765        # or any static file server
# open http://localhost:8765 on your computer, or http://<your-computer-ip>:8765 on your phone
```

To install it on Android, host the folder over HTTPS (GitHub Pages, Netlify, Cloudflare Pages). Open it in
Chrome and choose **Add to Home screen**. The service worker caches everything, so it keeps working offline.
Projects and your library are saved on the device in IndexedDB.

## What a first session looks like

1. Open the app. The sample character **Blocky** is loaded and breathing.
2. Tap **Let's go**. It creates a punch and plays it.
3. Type "make it faster" or "more power", or tap a preset (walk, jump, sword swing, death…).
4. Tap a body part and drag the rings or sliders. The change is keyed at the playhead.
5. **Export → Download mod package** gives you a zip with `src/main/resources/assets/<modid>/…`.

## Features

| Area | What works |
|---|---|
| 3D viewer | three.js, Minecraft box-UV cubes with a nearest-neighbour texture. One finger orbits, two fingers pinch/pan. Tap a body part to select it. Rotate/move gizmo. |
| Models | `.bbmodel` import (cubes, nested groups, box and per-face UV, embedded texture, embedded animations incl. GeckoLib-plugin easings). `.geo.json` + `.png` import. Built-in sample rig with elbows, knees and a held sword (the sword is shown automatically for weapon moves). |
| Model mode | Edit bone rest rotation and pivot, rename bones (animations follow), show/hide bones, map body parts by hand. |
| Timeline | Drag to scrub. Drag ◆ to retime. Tap ◆ for easing, copy/paste, nudge, delete. "All bones" row for whole poses. Drag the end handle to change length. Pinch or +/- to zoom. Loop and 1×/0.5×/0.25× preview. |
| Easing | Beginner presets (Linear, Smooth, Ease in, Ease out, Bounce, Overshoot), plus every GeckoLib easing under **More**. |
| Undo/redo | Every edit goes through one snapshot history (120 steps). Ctrl+Z / Ctrl+Y on desktop. |
| Presets | Idle, walk, run, jump, fall, land, punch, uppercut, kick, sword slash, overhead strike, thrust, block, dodge (side/back), get hit (light / stumble), death (back/forward), charge, energy blast, spin attack, turn, wave, look around, taunt, crouch, 3-hit combo. |
| Sequences | Chain moves; transitions are automatic (each move starts from the previous one's last pose; combos skip the return to stance). |
| Natural motion | Built into every move: anticipation, eased strikes, follow-through/overshoot, hip weight shift, torso leading the arm, counter-rotating head, overlapping action (forearms/head/item trail by about a tick), joint limits. **Polish motion** applies the same principles to hand-keyed animations. |
| Assistant | Plain English → a plan of moves → normal keyframes. Understands sequencing ("…then…", "before", "and"), sides, combos ("three-hit combo"), adverbs (fast, slowly, powerful, subtle, dramatic). Adjustments: faster, slower, more power, less/more exaggerated, more weight, slower recovery, smoother, snappier, applied to the whole clip or to one selected move. **Regenerate** or re-tune a single move without touching the rest. |
| Library | Save to folders; rename, duplicate, move, delete; preview; insert or append into any project. Clips retarget by body part, so they work on other rigs. |
| Projects | Several projects; autosave; backup/restore as `.mcanim.json`. |
| Export | GeckoLib 4 (NeoForge/Fabric 1.20.1–1.21.4, incl. **NeoForge 1.21.1**), GeckoLib 5 (1.21.5+), or raw JSON. Zip with geo.json, animation.json, texture, `.bbmodel`, a Java class of `RawAnimation` constants plus controller/trigger code, and a README. Every export is validated first. |

## Architecture

```
index.html, styles.css, sw.js, manifest.webmanifest   PWA shell (no build step, ES modules + import map)
vendor/        three.js r170 (three.module.min.js, OrbitControls, TransformControls) – MIT
src/core/      pure logic (no DOM): runs in the browser and in Node tests
  animation.js   keyframe data model, sampling (easing + catmull-rom), retime, copy/paste, section splicing
  easing.js      GeckoLib easing set + beginner presets
  model.js       internal model format (Blockbench space), box-UV layout
  rig.js         bone-name → body-part role detection, joint limits
  blocks.js      motion building blocks (poses + timing + principles)
  sequencer.js   blocks → keyframes, automatic transitions, per-section regeneration
  assist.js      overlap, joint limits, "polish" (anticipation / follow-through / weight / head stabilisation)
  adjust.js      plain-language adjustments
  assistant.js   natural-language interpreter (rule-based; single `interpret()` extension point)
  history.js, storage.js, sampleModel.js
src/formats/   bbmodel.js, geo.js, geckolib.js (export + import + validator)
src/export/    package.js (framework adapters: TARGETS), zip.js (dependency-free zip)
src/ui/        viewer.js (three.js), timeline.js, panels.js, dom.js
src/app.js     state, commit/undo, playback, wiring
tests/         core.test.mjs (Node), e2e.cjs (Playwright on a Pixel 7 viewport)
samples/       blocky.bbmodel – a sample to try the importer
```

**Conventions.** Animation values are stored exactly as GeckoLib/Bedrock reads them: degrees, model pixels, scale
multipliers, X/Y rotation sign flipped relative to Blockbench's 3D space. Export is therefore lossless. The viewer
converts to three.js the same way Blockbench previews Bedrock/GeckoLib animations. A key's easing describes the
motion *into* that key (GeckoLib semantics).

**Adding an export framework.** Add an entry to `TARGETS` in `src/export/package.js`. It needs file paths, an
optional Java generator and notes.

**Adding AI.** `interpret(text)` in `src/core/assistant.js` returns `{ kind: 'create', steps: [{ block, opts }] }` or
`{ kind: 'adjust', adjustments: [...] }`. An LLM-backed interpreter only needs to return the same shape. Everything
downstream (compilation, regeneration, editing, export) already works on it.

## Tests

```bash
node tests/core.test.mjs                                   # 20 tests: rig, easing, blocks, assistant, adjust, polish, round trips, validator, packaging
python3 -m http.server 8765 & node tests/e2e.cjs           # Playwright, phone viewport: onboarding, assistant, undo/redo, tap-to-select,
                                                           # slider posing, key drag/easing/delete, polish, library, export zip, reload persistence, landscape
```

## Honest limitations

- **The assistant is rule-based, not an LLM.** It works offline with no key. It understands many phrasings, and the
  example prompts from the brief are covered by tests, but it maps text onto the built-in move vocabulary. It
  can't invent a brand-new kind of motion. The `interpret()` hook is where an LLM adapter would go. It's left out
  on purpose because it would need an API key in the browser or a server.
- **Export is checked against the format, not in-game.** The validator mirrors GeckoLib's JSON rules and cross-checks
  bone names against the geometry. The rotation/position conventions follow Blockbench's Bedrock codec. I couldn't
  run Minecraft + GeckoLib here, so test the first export in your mod. GeckoLib 5 folder paths are the documented
  ones; check them against your exact version.
- **`.bbmodel` mesh elements, locators, Molang expressions, multiple textures and bezier handles** are reported on
  import and simplified (meshes skipped, Molang → 0, bezier → Smooth). They aren't silently faked.
- Presets assume a biped. Other rigs (quadrupeds, dragons) can be imported and keyed by hand, and **Polish** works
  on them, but humanoid presets won't map well.
- Root motion stays in place (walk/run cycle on the spot, as GeckoLib expects). Movement through the world is the
  entity's job.
