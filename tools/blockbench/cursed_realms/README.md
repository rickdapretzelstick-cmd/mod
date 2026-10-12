# Cursed Realm Portal Destinations

Ten editable Blockbench environment models for the places reached through Cursed Breaches and the rifle's forest portal. These are separate assets from the relic trial rooms.

## Open

Extract the ZIP and open any `.bbmodel` in `models/` in Blockbench. The Generic model format supports the large coordinates and freely rotated props. Textures are embedded in each model, with a shared editable atlas in `textures/`.

16 Blockbench units = 1 Minecraft block. Y is up; entrances are toward positive Z. Each model is divided into named architecture/prop groups in its Outliner. Open roofs expose the scenery; rear walls, perimeter elements and architectural fragments establish the environment.

## Destinations

| File | Footprint | Design |
| --- | --- | --- |
| `gojo_realm.bbmodel` | 40 × 40 | Fractured quartz plaza, impossible suspended stairs, broken gates, floating ruins and a blue spatial anomaly |
| `yuji_realm.bbmodel` | 36 × 42 | Warped cinema with six seating rows, screen interference, abstract worm-like film silhouette, stage curtains and projection booth |
| `yuta_realm.bbmodel` | 40 × 40 | Protective courtyard, purple seal, cloisters, garden beds, reflecting channels and an empty rear terrace for the existing Rika |
| `ryu_realm.bbmodel` | 44 × 44 | Stepped impact crater, collapsed buildings, buckled road, glowing fissures and fractured obelisks |
| `hakari_realm.bbmodel` | 40 × 40 | Underground fight club with a central ring, bleachers, gaming tables, house balcony and 777 marquee |
| `rifle_forest.bbmodel` | 44 × 44 | Crooked forest trail, corrupted trees, hunting stands, ladders, fallen timber and stalking clearing |
| `guardian_claw.bbmodel` | 38 × 38 | Ribbed fossil grove and open hunting ground for the existing skeletal claw hunter |
| `guardian_stalker.bbmodel` | 38 × 38 | Abandoned courtyard, boarded windows, broken fountain and cold lamps for the existing pale-haired stalker |
| `guardian_cyclops.bbmodel` | 42 × 42 | Terraced quarry, abandoned hoist, rail approach and open impact floor for the existing heavy cyclops |
| `guardian_veiled.bbmodel` | 40 × 40 | Repeated shrine gates, torn hanging veils, paper seals and rear altar for the existing long-armed veiled curse |

Guardian arena themes are new visual interpretations designed for the four established guardians, not new storylines or progression requirements. Character realm assets do not add or reinstate character-specific Grade 1 story events; use them only where those destination types remain active in the mod.

## Existing creatures stay intact

No Rika or guardian character replacement is included. Yuta's rear terrace is intentionally empty. `manifest.json` marks the existing-Rika anchor and a reserved clearance volume. Place the project's existing Rika there and check its real bounds/animation range; the design does not assume the dimensions of an unseen model.

Other manifests mark suggested arrival, breach and encounter positions. These are local design coordinates in **blocks**, not engine-ready world coordinates. Resolve them against your room origin/orientation and validate collision, floor height and camera clearance when integrating. Footprints list the foundation; branches, gates and other details may extend beyond it. Calculate actual geometry bounds for allocation and culling.

## What is implemented

All ten rooms are built as static textured geometry. Every preview is rendered from the corresponding delivered `.bbmodel`, using the same atlas and cube transforms. Rings, fog-colored details, bright accents, screen interference and suspended objects are geometry/texture representations; they are not live particles, light emitters or animations.

These files are not Minecraft world saves, schematics or a mod JAR. They do not implement portals, collision, AI, encounters, progression, networking or cleanup. Reconstruct large architecture with the mod's block-placement/structure system, and extract detailed props into the model format your existing renderer supports. Do not export the entire large Generic model as one vanilla block-model JSON or spawn an entity per cube.

The layouts deliberately keep a principal encounter space open, but they have not been playtested with the actual creature dimensions, skills or camera system. In particular, cinematic grab-contest framing needs runtime checks.

## Preserve the existing rules during integration

- Cursed-event battles stay inside the Cursed Dimension, not the Overworld.
- News → Compass → Cursed Breach → Cursed Dimension progression and grade escalation remain controlled by the mod.
- Each Grade 1 encounter uses one existing guardian; these four arena assets do not create a four-boss gauntlet.
- The Exorcism Seal captures remnant essence after victory. It is not a summoning ritual or entry requirement.
- The rifle forest retains its separate existing entry/reward flow.

## Sources and validation

`source/build_realms.py` generates these environments using `source/geometry.py`. `source/render_realms.py` renders them with a textured software depth buffer. Requires Python, Pillow and NumPy. The renderer uses DejaVu Sans from the usual Linux font path; change `FONT` if needed elsewhere.

Validation checks JSON loading, unique cube IDs, exactly-once group membership, positive dimensions, six textured cube faces, atlas bounds and embedded texture decoding. The supplied pack was visually inspected through its renders. No Blockbench application import or Minecraft runtime test was available in this environment.
