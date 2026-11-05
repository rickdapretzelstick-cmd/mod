# Archived: the survival system

This folder holds the survival-progression half of the mod, taken out of the build so the mod is just the characters
(Gojo, Hakari, Yuji, Yuta, Ryu) and the cursed tools (Slaughter Demon, Cleaver, Rifle, Blade). Nothing here is compiled
or shipped. Nothing was deleted: every file is here, at the path it had under the repo root, so putting something back
is a move (`git mv archive/survival/<path> <path>`).

## What is in it

- **The news board and contracts**: `progression/investigation` (board, incidents, Cursed Compass, sites, cursed realms,
  contract clearance, protected places), `data/jjk/incidents`, `data/jjk/realm_rooms`.
- **Cursed spirits**: `progression/curse` (the common curses and the Finger Bearer), `progression/guardian` (the four
  Grade 1 guardians, seals and essences), `progression/grade` (curse grades and cursed damage), their renderers, models,
  textures and animation clips (still under `src/main/resources/assets/jjk`, see below).
- **Cursed glasses, bottles, essences, relics**: `ProgressionItems` (the original, with every item), `progression/block`
  (cursed cauldron, soul sand, seal), `progression/item`, `progression/story` (character storylines and relics).
- **Events and trials**: `progression/training` (relic trials), `progression/prison` (Prison Realm),
  `progression/finish` (binds, parry chains, finishers, guardian phases), `progression/contest` (strength contests),
  `progression/moment`, `progression/worldgen`, `data/jjk/worldgen`, `data/jjk/trial_rooms`.
- **Mastery**: `progression/mastery`, `data/jjk/mastery`, its payloads and client screen.
- **Survival core**: `ProgressionBootstrap` (original), `TechniqueProgression`, `KitOwnership`, `PlayerProgression`,
  `ProgressionCommand`, `CurseAggro`, `CursePerception`, `CursedEncounters`.
- **Client**: `client/progression`, `client/investigation`, `client/training`, `client/prison`, `client/finish`,
  `client/mastery`, the curse, anchor, breach, prison and trial renderers, the encounter and story overlays.
- **Assets and data**: the curse, guardian, Prison Realm, realm and trial models, textures and animation clips
  (`src/main/resources/assets/jjk/{models/bb,textures/entity,animations}`), the item models and blockstates of the
  archived items, and the data packs (incidents, realm and trial rooms, worldgen, dimensions, mastery, loot, tags and
  recipes). The lang file and sounds were left alone (unused entries are harmless).
- **Tests**: every gametest that exercised any of it (`src/gametest/java/.../test`).
- **Mixins**: the protected-places mixins, the mob-target mixin, and the below-world mixin for the Prison Realm.

## What replaced it in the live code

- `ProgressionItems` now registers only the four cursed tools. Each is crafted from its base item plus a ghast tear
  (`data/jjk/recipe`) and is in the creative tools tab.
- `progression/mastery/Mastery` is a stand-in: `unlocked` is always true and `param` is always 1.0, so every move and every
  tool move comes whole.
- `ProgressionBootstrap` only initialises the tools and the `/jjk rifle` command.
- Any character can be picked from the K menu; nothing is earned or owned.
- `command/TestCommand` (the `/jjk test` menu) lost its realm, trial, curse and guardian pages (the original is here).

## To bring it back

1. Move the folders you want back to their original paths (they mirror `src/`).
2. Put the original `ProgressionItems.java`, `ProgressionBootstrap.java` and `mastery/Mastery.java` back over the slim
   ones (the originals are archived next to the rest of their package).
3. Re-add what the removed hooks did: the registrations in `ModEntities`, `ModAttachments` and `Network`, the client
   registrations in `JJKClient`, the mixin entries in `jjk.mixins.json` and `fabric.mod.json`, and the character-select
   and `CharacterService` calls into `TechniqueProgression`. `git log -p` on the commit that created this folder shows
   every one of them.
4. The assets and data move back the same way (`git mv archive/survival/src/main/resources/... src/main/resources/...`).
   Lang entries and sounds never left.
