# Relic Trial Rooms — V2

Five editable Blockbench environment models, rebuilt around the five-room visual reference.

## Open the models

Extract this ZIP, launch Blockbench, and open a file in `models/` with File → Open Model. Each model uses the Generic format to preserve large dimensions and freely rotated cubes. The texture atlas is embedded in every model; `textures/trial_atlas.png` is also included for editing.

Scale: **16 model units = 1 Minecraft block**. Y is up. The entrance is on positive Z; feature walls sit on negative Z. Roofs are deliberately open or broken to expose the interior and preserve the reference's ruined architecture.

| Room | Floor footprint | Main features |
| --- | --- | --- |
| Yuji | 26 × 26 blocks | Timber room, broken rafters, hanging lights, ivy, VHS shelves, CRT with scanlines and silhouette, sofas, patterned rug and clutter |
| Gojo | 28 × 28 blocks | Quartz colonnade, layered calibration dais, Blue core, horizontal and vertical orbit geometry, floating fragments and four precision stations |
| Yuta | 28 × 28 blocks | Ritual circle, blade altar, amethyst garden, hanging purple flowers, candles and sculpted Rika-inspired guardian relief |
| Ryu | 24 × 40 blocks | Long target gallery, reinforced columns, range markings, blast baffles, scars, lanterns and concentric target wall |
| Hakari | 28 × 28 blocks | Red-and-gold pavilion, stepped stage, three giant reel towers, neon-colored icons, smaller slot cabinets, stools and floating luck symbols |

## Editing and integration

Architecture, props and focal elements are grouped by name in the Outliner. `manifest.json` provides cube counts and interaction-marker coordinates in blocks. Each preview is rendered directly from the delivered model geometry and texture atlas; it is not an image-generation concept render.

These are **static environment assets**, not a mod JAR, world save, schematic or playable trial system. Collision, trial rules, sound, particles, emissive lighting, moving reels and orbit animation must be implemented in the mod. Bright texture colors do not themselves emit light. The Rika backdrop is an architectural sculpture, not a rigged character.

For Minecraft integration, reconstruct large architecture with placed blocks or your existing scene builder. Export individual props through the model format supported by your renderer. Generic `.bbmodel` files are editable project files and are not direct Minecraft block-model JSON. Large rooms and arbitrary rotations should not be exported unchanged as one vanilla block model.

## Included sources

`source/build_rooms.py` regenerates the five models and atlas using Python and Pillow. `source/render_rooms.py` produces the model previews using Python, NumPy and Pillow. Rendering uses a simple textured orthographic depth-buffer renderer. Model JSON, positive cube dimensions, texture UV bounds and group membership were checked programmatically; no in-game test or Blockbench application import was performed in this environment.

Original V1 files are left unchanged. This is a separate V2 collection.
