# Cursed Realm Environment Models

Six editable Blockbench 5.x Generic Model projects:

- `models/mine_realm.bbmodel` — enclosed cavern, rough ceiling, timber supports, dead-end rails, webs and six suspended lanterns.
- `models/house_realm.bbmodel` — 45-block hall, five staggered partitions spaced seven blocks apart, windows, overturned furniture, barrels, cold hearth, red bed and ceiling.
- `models/pasture_realm.bbmodel` — dead field, two interrupted square corrals, bones, blackened hay, crooked tree and four post lanterns.
- `models/cliff_realm.bbmodel` — floating stone fragment, blackstone underside, jagged north cliff, dead brush and four lantern posts.
- `models/finger_bearer_realm.bbmodel` — circular battle hall, seven-block walls plus coping, brick floor rings, central seal, eight basalt pillars and hanging chains.
- `models/hollow_realm.bbmodel` — ragged fallback island, six differently broken pillars, fallen fragments and three lanterns.

## Open and edit

Unzip, then use File > Open Model in Blockbench and select a `.bbmodel`. Textures are embedded in every project and also supplied as `textures/realm_atlas.png`. Models use the Generic Model format to allow environment-scale geometry beyond normal Java block-model bounds. All parts are editable cuboids. Group names separate floors, walls, ceilings, lights and props. Everything is visible in the saved projects; hide `ceiling` and relevant wall groups when editing interiors.

Scale: **16 Blockbench units = 1 Minecraft block**. Origin: floor centre, Y=0 is the walking surface; north is negative Z. See `manifest.json` for exact bounds. No creature models or animation tracks are included; these are static environments.

## Previews

`previews/` contains geometry-derived isometric renders and a six-panel contact sheet. Roofs and selected near walls are removed ONLY in preview rendering to show interiors. The full geometry remains in the models. Preview lighting is illustrative and is not an in-game screenshot. All textures are original procedural pixel textures; no generated concept artwork or extracted game assets are included.

## Integration into the mod

This pack does not modify CursedRealms.java or provide a drop-in JAR. The existing mod source and loader were not supplied, so in-game loading, collisions, spawning and lighting have not been tested.

Use the six exact realm IDs above for the existing encounter mappings. `geometry/<realm_id>.json` supplies the same cuboids in block units with semantic groups and materials for adapting to your renderer. It is a documented custom interchange format, NOT a native Minecraft model or schematic. Each cuboid has `a` (minimum XYZ), `b` (maximum XYZ), `material`, `group` and `name`. The `.bbmodel` files carry UVs and embedded texture data. Lantern emissive rendering and actual light sources must be configured in your runtime.

Keep collision floors, walls, boundaries and arena protection in the existing realm system. A rendered mesh does not create Minecraft collision automatically. Preserve the cliff's falling entry, existing encounter/spawn behavior, and shared red-void dimension. Do not add a skybox or red cube around each model. Make the original procedural hollow fallback remain available if model loading itself fails; a fallback model alone cannot recover from a broken renderer. Preserve the three-minute restoration behavior where already implemented.

For performant rendering, merge static geometry, cull internal faces and chunk large scenes in the target exporter/renderer. The source projects favor editable parts over draw-call optimization. Do not export these whole arenas as a single ordinary Java block model.

## Rebuild and validation

`build_models.py` regenerates all models, textures, interchange geometry and preview images using Python and Pillow. The included files were checked for valid JSON, positive cuboid dimensions, resolved texture references, exact outliner membership and embedded PNG validity. Preview images were visually inspected. Native Blockbench UI loading and Minecraft runtime behavior have not been exercised in this environment.
