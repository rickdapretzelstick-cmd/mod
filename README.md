# Jujutsu — Phase 1: Gojo Satoru

Fabric mod for **Minecraft 26.3** (Java 25, Fabric API). Install the jar plus Fabric API.

## Controls

| Key | Base Gojo | Awakened Gojo |
|---|---|---|
| Left click (empty hand) | Light chain · hold = charged heavy | same |
| Z | Lapse Blue (hold to steer) | **Lapse Blue: MAX** |
| X | Reversal Red (hold to charge) | **Reversal Red: MAX** |
| C | Infinity on/off | **Hollow Purple** (hold) |
| V | Teleport (3 charges) | Teleport |
| G | **Awaken** (meter full) | **Domain Expansion: Infinite Void** |
| R | Guard (hold; tap early = parry) | same |
| Left Alt | Dash | same |
| \` | Combat stance on/off | same |

Melee variations: click in the air for air combos, hold jump on the 4th hit for an uppercut, click while sprinting for a
lunge, look down at a knocked-down enemy for a stomp.

**Awakening.** The bar above the hotbar fills as you fight (landing hits, dealing/taking damage, blocking, parrying).
When full, press G: Gojo pulls off his blindfold and awakens. His kit switches to the MAX techniques, Infinity is always
on, and the bar becomes a timer that drains; MAX moves also spend it. At zero he returns to his base kit.

**Infinite Void** builds a real, sealed dome of blocks (walls, ceiling, floor and underground shell) around the fight for
14 seconds. Everything it replaces (including chest contents, block states, waterlogging) is saved to disk first and
restored exactly afterwards. The barrier can't be mined, blown up or pushed. If the server stops or crashes mid-domain,
the world is restored on the next start.

## Commands (op)

`/jjk arena` test arena with dummies · `/jjk dummy [stand|jump|fight] [n]` · `/jjk nocooldown true|false` ·
`/jjk awakening <amount>|end` · `/jjk reset` · `/jjk character gojo|none` · `/jjk domain cancel [all]` ·
`/jjk status [target]` · `/jjk config reload`

## Config

`config/jjk.json` holds every tunable (damage, cooldowns, costs, Awakening gain/drain/move costs, domain size/duration,
destruction limits, particle quality, screen effects, sound volume). `/jjk config reload` applies changes.

## Development

- `./gradlew build` — mod jar in `build/libs`
- `./gradlew runGameTest` — server GameTests (combat, techniques, Awakening, domain structure restore/crash recovery)
- `./gradlew runClientGameTest` — drives a real client through the whole kit with real input and takes screenshots
  (headless: run under Xvfb with Mesa; see `src/gametest` for the test-only headless mixin)
- `tools/gen_sounds.py`, `tools/gen_textures.py` regenerate the synthesized sounds and textures
