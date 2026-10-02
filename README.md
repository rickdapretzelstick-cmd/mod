# Jujutsu — Gojo Satoru, Kinji Hakari, Yuji Itadori and Yuta Okkotsu

Fabric mod for **Minecraft 26.3** (Java 25, Fabric API). Install the jar plus Fabric API.

## Controls

The Jujutsu Shenanigans PC layout. In combat mode 1-4, Q and F belong to the mod (the hotbar, drop and offhand swap on
those keys are held back); Vanilla Minecraft mode gives them back. Everything can be rebound in Controls.

| Key | Base Gojo | Six Eyes (awakened) |
|---|---|---|
| Left click | M1 chain · hold = charged heavy (always the M1 in combat mode, whatever you hold; it never mines) | same |
| 1 | Lapse Blue | **Lapse Blue MAX** (hold to steer) |
| 2 | Reversal Red | **Reversal Red MAX** |
| 3 | Rapid Punches | **Hollow Purple** |
| 4 | Twofold Kick | **Infinite Void** |
| R | Limitless (Special) | Limitless |
| G | **Awakening** (meter full) · the domain counter | counter |
| F | Block (hold; tap early = parry) | same |
| Q | Dash · ragdoll escape while ragdolled | same |
| W W | Sprint (Minecraft's double tap) | same |
| Space | Jump | same |
| *(unbound)* | Combat / Vanilla Minecraft mode | same |

Melee variations: click in the air for air combos, hold jump on the 4th hit for an uppercut, click while sprinting for a
lunge, look down at a knocked-down enemy for a stomp.

### Satoru Gojo — Honored One

Tuned after the [Jujutsu Shenanigans wiki](https://jujutsu-shenanigans.fandom.com/wiki/Honored_One) (the `gojo`,
`red`, `maxBlue`, `maxRed` and `purple` config sections).

- **Lapse Blue** (13s): aimed at someone within 35 studs, a vacuum pulls them in (5), suspends them in front of Gojo —
  who gains melee i-frames — and he follows with an unblockable kick (7.5). Blocking stops the pull.
- **Reversal Red** (20s): a short wind-up, then an orb that flies 40 studs and bursts in a 15-stud blast (12.5; half,
  with no knockback and never fatal, through a guard). Limitless during the wind-up: he phases behind the target, upside
  down, for a point-blank Red; on an airborne target he kicks first (5 + 12.5); on a target caught mid-move or mid-dash
  they freeze and he calls "Aka" before an enhanced orb (15).
- **Rapid Punches** (15s): a spinning kick locks a nearby enemy (it can't catch a ragdoll); bullet i-frames, 15 punches,
  3 heavy punches, then a final blow that ragdolls them — nearly twice as far if they had just got up. Limitless right
  after it lands is **Face Grater**: he appears before them, drags them along the floor and tosses them (10.2).
- **Twofold Kick** (18s): a rising kick (a guard stops the rest); melee i-frames and an unblockable second kick that
  bounces them higher.
- **Limitless** (Special, 15s, 6% of the meter in the base kit): hand up, the glass shatters, and he is right in front
  of the target under the crosshair; turn the camera during the wind-up to choose where around them. Airborne target:
  he appears over them and kicks them to the floor (8).
- **Six Eyes** (G on a full meter): the blindfold comes off, 25% health back, 60 seconds of Awakening.
  **0.2 Domain**: press R during the Awakening sequence. Infinite Void for two tenths of a second — every enemy in a wide
  range is overloaded for 7 seconds — then a three-phase rush (7×5, 6×20, 65) with i-frames during each run. Two
  targets held at the end are both finished. Afterwards he is burnt out: base kit, all on cooldown except Limitless.
- **Lapse Blue MAX** (17s): a steerable vortex (hold to guide it; walk or hang in the air meanwhile), 20 ticks of 2.2.
  If it kills someone it lingers and keeps pulling everyone else in at half damage.
- **Reversal Red MAX** (10s): a little over a second of charge, then a piercing orb for 100 studs, 30 falling to 7 with
  distance. Airborne: he hovers and aims freely. Limitless during the charge (free): it rebounds to him — a target it
  caught is pulled in for a Black Flash (10), an empty return hits Gojo (15).
- **Unlimited Purple**: Red MAX into the orb Lapse Blue MAX left behind after a kill. Three seconds later it erases
  everything around it (50-100 by distance) and drains the whole Awakening. It plays like the JJS GIF:
  1. A black impact frame as Red tears into the orb, magenta flares streaking across the view, and the world lit magenta.
  2. The orb's dark-blue ink turns magenta around a swelling white-hot core. Lightning crackles out across the whole
     blast radius, and a pink dome marks what it will erase.
  3. A dark shell collapses in onto the core.
  4. The detonation whites out everything near it, then pink sparkles drift up out of the crater.
- **Hollow Purple** (40s): Blue and Red combine and it rushes forward about three seconds in (70, unblockable, erases
  blocks); airborne he hovers with free aim. Blue crackles with lightning in one hand while red wind whips round
  the Red in the other. As they meet, magenta lightning lashes out, and the mass (a white-hot core in a ragged ring
  of dark magenta) is held, then fired.
- **Infinite Void** (4 while awakened, 120s): the domain below, 14 seconds; everyone caught is stunned until it ends.
- **Finishers** (under 20% health): Lapse Blue crushes them in rubble, Red shatters them, Rapid Punches ends in a Black
  Flash, Twofold Kick holds them up for a point-blank Red.

**Infinite Void** builds a real, sealed dome of blocks (walls, ceiling, floor and underground shell) around the fight for
14 seconds. Everything it replaces (including chest contents, block states, waterlogging) is saved to disk first and
restored exactly afterwards. The barrier can't be mined, blown up or pushed. If the server stops or crashes mid-domain,
the world is restored on the next start.

## Characters

Press **K** (or pause menu → *JJK Settings* → *Choose Character...*) to open the character select screen and click a
card. Every registered character gets a card from its own name, title, description, theme and portrait, so adding a
character never touches the screen. Switching only works from a neutral state: not mid-attack or mid-cast, not
awakened or in Jackpot, not in or near a domain or clash, not stunned, and not hit in the last few seconds. The old
kit's techniques, toggles and states are cleared and the new moveset, HUD icons and theme load at once.

| Key | Gojo (Honored One) | Hakari (Restless Gambler) | Hakari in Jackpot | Yuji (Vessel) | King of Curses |
|---|---|---|---|---|---|
| 1 | Lapse Blue | Reserve Balls | Lucky Volley | Cursed Strikes | Dismantle |
| 2 | Reversal Red | Shutter Doors | Lucky Rushdown | Crushing Blow | Open |
| 3 | Rapid Punches | Rough Energy | Overwhelming Luck | Divergent Fist | Rush |
| 4 | Twofold Kick | Fever Breaker | Energy Surge | Manji Kick | Malevolent Shrine |
| R (Special) | Limitless | Door Guard (hold) | Rhythm | Combat Instincts | Cleave |
| G | Awakening (Six Eyes) | Idle Death Gamble | — | Awakening (King of Curses) | counter |

Yuta has four movesets: his own and Rika's, each in base and awakened form. R with Rika out switches between his and
hers.

| Key | Yuta (Cursed Partners) | Rika | True Love (awakened) | Awakened Rika |
|---|---|---|---|---|
| 1 | Severing Path | Rika Smash | Elbow Rush | Rika Downslam |
| 2 | Resolute Slash | Rika Launch | Copy | Rika Slam |
| 3 | Outburst (hold) | Rika Haymaker | Energy Ripple | True Love Beam |
| 4 | Second Wind | — | Authentic Mutual Love | Rika Throw |
| R (Special) | Rika | Rika (back to Yuta) | Rika | Rika (back to Yuta) |
| G | Awakening (True Love) | True Love | Copy Wheel | Copy Wheel |

![Hakari](docs/screenshots/hakari_showcase.png)
![Idle Death Gamble opening](docs/screenshots/hakari_idg_cutin.png)
![The rush](docs/screenshots/hakari_idg_rush.png)
![Idle Death Gamble](docs/screenshots/hakari_idg_interior.png)

### Kinji Hakari — Restless Gambler

Tuned after the [Jujutsu Shenanigans wiki](https://jujutsu-shenanigans.fandom.com/wiki/Restless_Gambler): the wiki's
cooldowns and durations, its studs as blocks (about 3.6 studs to a block), all in the `hakari` section of the config.

- **Reserve Balls** (12s): one steel ball flicked about 65 studs, ricocheting off surfaces while it has distance left
  (much further inside his domain). It stuns whoever it hits, or ragdolls them if it hit within 15 studs.
  *Shutter Doors during the wind-up*: the doors manifest where the ball lands and bounce a target it stunned.
- **Shutter Doors** (15s): two grey doors flash into being, drop flat onto the floor either side of the target from up to
  25 studs away and slide shut under them (Door Guard's double door and Fever Breaker's are the ones that stand up). The target is stunned and Hakari's melee chain jumps to its 3rd hit. Doors that catch nobody
  linger for 7 seconds: jump on them to bounce high (they shatter), and a ragdolled enemy falling on them bounces three
  times, taking damage each time.
- **Rough Energy** (14s): a long wind-up, then an unblockable punch that sends them flying. *In the air*: a short
  hover, then a stomp whose shockwave launches everyone around upward. *From higher than a jump*: the stomp is
  unblockable and does double damage.
- **Fever Breaker** (23s): a reaching kick suspends the target in front of two shutter doors, then a dropkick launches
  them wherever Hakari is facing. *Fever Crush* (Shutter Doors during the wind-up): the doors clamp them while he
  raises his foot, and an unblockable axe kick crushes them. On a ragdolled target the axe kick does double damage and
  shatters the doors, unless he looks slightly away from them to keep them standing.
- **Door Guard** (Special, hold; 16s): a melee hit in the first 0.6s is answered with a punch through the doors that
  repels the attacker. A bullet (projectile) only shatters the doors.
- **Idle Death Gamble** (full Awakening meter; 80s, heals 15% on cast, invincible through the hand sign), presented
  the way Jujutsu Shenanigans does it:
  - **The opening**: a white slash cuts across the screen and opens into a teal band patterned with double helices,
    Hakari's close-up in it and "DOMAIN" / "EXPANSION" on its edges. White smoke bursts off him, white floods out over
    the ground from his feet with ink splashing along its edge (the real domain building), and everything goes white as
    it seals. Everyone inside then rushes through a tunnel of train cars with black speed lines, until the cars
    tumble away.
  - **The room**: endless glowing white. Floor, walls and ceiling are the same flat, self-lit white, with no shading,
    no ambient occlusion and no shadows, and the view blooms white at its edges, so there is no depth to judge by. Only
    the train-car kiosks standing about the floor, and the people in it, stand out. The giant red seven-segment
    counters and a winding train appear for each Riichi.
  - **The rules** stay on screen for everyone inside, their colour cycling: how to reach a Riichi, the pity jackpot and
    the odd/even bonuses top left, the scenarios top right. Everyone caught is frozen in place while they appear; the
    sure-hit does no damage.
  - **Visual moves**: using Reserve Balls or Shutter Doors (the two combined count twice), landing Fever Breaker's
    dropkick, Fever Crush, or a successful Door Guard. Two of them start a **Riichi**.
  - **Riichi**: Transit Card (one star) or Travel Emergency (two stars, better odds). A cut-in plays, the first two
    reels lock and the third spins down.
  - **Four attempts**: a miss goes back to spinning. The fourth scenario is a guaranteed **pity jackpot** with half
    the usual Jackpot time, as long as someone was caught in the domain; without one the domain breaks after it.
  - **Bonuses**: an odd jackpot number gives the next domain better odds, and an even one makes its Riichi scenarios
    play twice as fast. Both are lost on death.
  - **Renewal**: inside the domain, pressing Reserve Balls again within 8 seconds of a ball landing rewinds to that
    moment. Everyone goes back where they stood, and any damage Hakari took since is undone.
- **Jackpot** (100s, or 50s after a pity jackpot): infinite cursed energy, and a Reverse Cursed Technique that runs on
  its own. He heals fast and is effectively immortal, but damage drains the Jackpot meter (empty after 3.33 times his
  max health). Surviving to the end refunds 40% of the Awakening meter, and 25% more for each Jackpot in a row. Missing
  a jackpot or dying resets that. The kit becomes:
  - **Lucky Volley** (10s): a flurry of punches he can walk forward, ending in an unblockable swipe that launches them.
  - **Lucky Rushdown** (15s): a long run; whoever he meets is grabbed by the leg, dragged and thrown. Unblockable.
  - **Overwhelming Luck** (20s): a rushing strike that tosses them, then he sprints after them, grabs everyone it
    caught and finishes with a string of hits and a final punch. Unblockable.
  - **Energy Surge** (25s): a dash punch launches them skyward, and he blinks up and kicks them down.
  - **Rhythm** (8s): he does his dance — arms spread wide, shifting his weight side to side on each beat, a V to finish. Finish the dance uninterrupted to get a stacking speed boost to his moves
    and special, and every cooldown finishes 0.6s sooner.
- **Finishers**: on a target at 20% health or less, Shutter Doors shut them in completely, Lucky Volley's swipe sends
  them flying, and Lucky Rushdown drags them further, hurls them into the air and ends with a leaping punch.

### Yuji Itadori — Vessel

Tuned after the [Jujutsu Shenanigans wiki](https://jujutsu-shenanigans.fandom.com/wiki/Vessel) and its GIFs: the wiki's
damage, cooldowns and durations, studs as blocks, move timings from the GIFs (the `yuji` section of the config). Every
sound is the JJS audio. 85 max HP.

- **Cursed Strikes** (14s): eyes glowing red, he slides forward; whoever he meets eats a flurry of punches (bullet
  i-frames) and a calf kick that stuns them in place. His M1 count carries over and his front dash is shut off for a
  moment. A guard cuts the slide short; 360 blockable; can't bypass ragdoll. *In the air*: a hop, then a dropkick diving
  at the ground that grounds whoever it lands on (unblockable). *Finishers*: a kick, the floor struck to launch them, a
  spin kick; in the air, a Black Flash on landing.
- **Crushing Blow** (15s): cursed energy charges in his hand; a target close enough is grabbed, slammed twice and flung
  skyward; nobody close and the floor takes it (a shockwave that catches people getting up). *In the air*: he dashes
  across the air at them first. *Finisher*: one slam, then a German suplex.
- **Divergent Fist** (18s): a blow, then the cursed energy lagging behind it launches them a beat later (a stun instead
  if it interrupts what they were doing). **Black Flash**: press it again while his body flashes white (the cast bar
  reads BLACK FLASH!). **Black Flash Chain**: a Black Flash on someone's back stuns them and keeps Divergent Fist off
  cooldown; up to four in a row, the fourth a heavy one ("KOKUSEN"), and his side dash comes back after each. Each link
  is a punch, an uppercut or a dropkick at random, and the last one's launch follows it (away, skyward or far).
  *Finishers*: the body shatters; a finishing Black Flash sends them extremely far.
- **Manji Kick** (20s): 0.6s counter stance. A melee hit is answered with an upward roundhouse to the side; a bullet is
  dodged and he swoops in on the shooter. *Finisher*: a leg lock, a spin and a slam that crushes them.
- **Combat Instincts** (Special, 2s): during an M1's or a move's wind-up (not Manji Kick) it cancels it with no endlag
  and keeps the move off cooldown; with the aerial variants the hop is kept for mobility. Takes 3% Awakening if there
  is any. Next to a throwable (barrel, composter, anvil, bookshelf...) it punches the prop across the field for 15
  (needs the 3%).
- **King of Curses** (full meter; 60s, heals 45 HP): he faints and Sukuna takes over — "You're such an annoying brat,"
  the marks and the second pair of eyes on his face, a red aura. **Shrine**: his M1s become slashes reaching three
  times as far (24 studs), 360 blockable, cutting through walls, with no uppercut or downslam.
  - **Cleave** (Special, 12s): a grab, a pause, then a storm of slashes: 40% of their current health, at least 10.
  - **Dismantle** (13s): a barrage of slashes on whoever he faces within 30 studs (17.5; 10 through a guard, which also
    can't be finished by it). *In the air*: a flip and one long unblockable slash down the line.
  - **World Cutting Slash**: Rush during Dismantle's wind-up, then Open, then Cleave. He chants "SCALE OF THE DRAGON",
    "RECOIL", "TWIN METEORS" (uninterruptible), then swings a slash that cuts the world itself (80, less the more it
    hits), with total i-frames. Puts Open on its full cooldown and doubles Dismantle's.
  - **Open** (40s): fire in his hands, a clap, a bow drawn; with i-frames he looses an arrow of fire and a pillar of
    flame goes up where it lands, lifting everyone in it (30, unblockable).
  - **Rush** (15s): straight ahead at incredible speed; whoever he hits is hurled, chased down, kneed skyward and
    slammed back down.
  - **Malevolent Shrine** (120s, 18s): played like the JJS GIF. A cut-in of him in a band of teeth and red pillars, the
    world going black around you, then a white flash as it seals. The camera turns to face him and the shrine rises
    behind him, grey and colourless. It stands on a stone platform: red pillars, a grinning mouth filling the doorway,
    brick eaves and a hipped roof. Its colour floods in with a red flash, over a black void and a pool of blood. Then
    the sure hit: a ceaseless stream of Dismantles, white slashes with black cores tearing through the world and across
    your screen (2 each, 218 over the domain). The slashes don't stun, so you can still move and guard. Guarding is
    the only way to blunt them: 0.5 each, and they can't finish you.

![Malevolent Shrine opening](docs/screenshots/yuji_shrine_cutin.png)
![The shrine rising](docs/screenshots/yuji_shrine_reveal.png)
![Malevolent Shrine](docs/screenshots/yuji_shrine_interior.png)

### Yuta Okkotsu — Cursed Partners

Tuned after the [Jujutsu Shenanigans wiki](https://jujutsu-shenanigans.fandom.com/wiki/Cursed_Partners) and its GIFs:
the wiki's damage, cooldowns and durations, studs as blocks, every move keyed from its GIF (the `yuta` section of the
config). 90 max HP. Rika is the new model, animated with the same clip framework as the players.

- **Swordsmanship**: his M1s draw the katana from the sheath at his left hip. It stays out for 8 seconds after his last
  katana move or M1, then goes back in. The necklace with Rika's ring hangs on his chest.
- **Severing Path** (15s): an 18-stud slide sweeping the floor with the blade (4). Whoever it catches is locked in
  front of him for three quick swings (2.3 each), the last launching them. A guard pushes him off. *Finisher*: the last
  slash beheads them. **Veilstep** (walking backwards): a 27-stud back roll with melee i-frames that launches anyone in
  the way (9).
- **Resolute Slash** (15s): he vanishes mid-swing and reappears at a spot within 25 studs to cut at the target's neck
  (12, unblockable). **Resolute Black Flash**: press it again the moment he reappears (the cast bar reads BLACK FLASH!).
  He vanishes once more and lands a heavy blow amplified by a Black Flash (12), with i-frames.
- **Outburst** (hold, 16s): a hand on the holstered katana, energy pouring in. The draw (2) sets off a burst (4) in
  a 13-stud radius that throws everyone upward. It deals its damage through a guard but can't kill through one. Held,
  it grows through three stages (the bar at his right), each adding 2 damage and about 2 studs of radius. The last
  stage is fully unblockable unless he was hit first. *Counter*: hit in the first 0.25s, the swing parries. A melee
  attacker is stunned, and a projectile is sent back.
- **Second Wind** (16s): a 20-stud rush with melee i-frames. He grabs the face (2) and slams them into the floor (8).
  A whiff leaves a second try. *Variant*: Severing Path right as he collides turns it into a pummelling. That is five
  blows; with the katana out, a few swings and a spinning axe kick.
- **Rika** (Special): the first press manifests her partly at his side. After that, R switches to her moveset (the
  ring glows under the crosshair) and his movement keys fly her about, up to 100 studs from him. Press R again, or use
  one of her moves, and his moveset comes back while she returns to his side. Hold R instead and she stays where she
  is. A double press dismisses her. Whoever the crosshair is on when R is pressed becomes her target. Her three moves
  share one 10s cooldown:
  - **Rika Smash**: her fist swells over the target and slams down (10). A target in the air is dunked into the
    ground (8).
  - **Rika Launch**: a boost forward, or upward in the air. Used mid-move it **feints** that move (6s).
  - **Rika Haymaker**: a slow, heavy blow that knocks them far back (12; 18 through a guard).
- **True Love** (G on a full meter; 60s, heals 25): "Come, Rika. Give me everything." He tears off the necklace and
  puts the ring on. Rika manifests fully behind him and wraps a steel casing around his right arm. **Steel Arm**: his
  fists, each of the first three M1s followed by a quick jab of the casing.
  - **Elbow Rush** (15s): a 38.5-stud dash into an elbow (4). Then he appears behind them with Rika in front, and both
    barrage them (5; 8 with Rika). A last blow launches them (6).
  - **Copy** (15s; 25s for an Awakening move): the technique picked on the Copy Wheel. His own is **Cursed Speech**:
    "動くな!" ("Don't move!") stuns everyone within 35 studs for 2.5 seconds. The rest are taken from whoever Rika
    attacks or kills, and play that sorcerer's move with Yuta as its user: Limitless from Gojo (Reversal Red MAX),
    Doors from Hakari (Shutter Doors), Shrine from Yuji (Dismantle), and Dismantle from Sukuna (Strong Dismantle).
  - **Copy Wheel** (G while awakened): two pages of four around the crosshair, each with its own cooldown. 1-4 pick
    one, R turns the page, and G closes it. Copies are lost on death.
  - **Energy Ripple** (18s): the katana driven into the floor sends out a dome of energy that pushes every enemy within
    27 studs away (19, unblockable). **Fakeout** (press again before the blade lands): a sudden swing instead (7) whose
    energy bursts inside them (12).
  - **Awakened Rika** (R): her moves each have their own cooldown.
    - **Rika Downslam** (13s): her arm slams down on them (8 + 4).
    - **Rika Slam** (13s): she grabs a leg and slams them five times.
    - **True Love Beam** (40s): a pink orb conjured together. Rika places herself before it, reveals her eye and fires
      a beam that erases everything in its path (100, less the more it hits). Press it again in the wind-up for the
      quick beam (22.4, 15s).
    - **Rika Throw** (13s): she picks him up and hurls him. An enemy he crashes into takes 8-18 by airtime. If he hits
      no one, he takes it himself and is truly ragdolled.
  - **Authentic Mutual Love** (4 while awakened; 120s, 45s): a pale stone platform under a black sky, grave crosses,
    rope knots circling overhead and Rika looming. Blades rain down and four land within reach, each carrying a
    technique. Standing by one, he takes it up, runs about 55 studs at the nearest enemy and swings (8), and the
    technique goes off:
    - **Shrine**: four Cleaves; missed, a horizontal Dismantle.
    - **Thin Ice Breaker**: the sky breaks like ice and their ragdoll can't be cancelled.
    - **Clairvoyance**: a manga panel marks them, and their attacks on him are dodged for 10 seconds.
    - **Cursed Speech**: "落ちれ!" ("Plummet!"); missed, "止まれ!" ("Stop!") freezes everyone.
    - **Shikigami**: three flying Rika heads swarm them.

    It breaks if no enemy is inside, and gives no Awakening progress. **Jacob's Ladder** (4 again after four direct
    blade hits): a ray from the sky lifts the target while draining 62.5 HP and 35% of their Awakening meter (50% if
    awakened). It shatters the domain.
- **Finishers** (under 20% health): Severing Path beheads, Resolute Slash cuts through the head, and Outburst bisects.
  Rika Smash leaves a puddle. True Love Beam atomizes into black mist. Jacob's Ladder lifts the soul while the body
  falls.

![Cursed Partners](docs/screenshots/yuta_showcase.png)
![True Love Beam](docs/screenshots/yuta_true_love_beam.png)
![Authentic Mutual Love](docs/screenshots/yuta_domain.png)

## HUD and Vanilla Minecraft mode

CE is a slim vertical bar on the left edge. It eases between values, what you just spent lingers as a pale ghost, it
turns red and pulses when low, and it reads EMPTY when nothing is left. The current moveset is shown on the right as
16x16 pixel-art icons with each one's bound key beside it, following your Controls settings. The icons show when a move
is on cooldown (a receding shade and the time left) and when there isn't enough CE or Awakening meter. When Awakening
starts, the icons flip over to the MAX moves and are named for a moment. The Awakening meter stays its own bar above the
hotbar.

**Vanilla Minecraft mode** (pause menu → *JJK Settings* → *Combat Mode: VANILLA*, or bind *Toggle Combat / Vanilla
Minecraft mode* in Controls) hides every JJK HUD element and switches off all of the mod's ability keys, melee and clash
inputs, so you can build and mine without setting anything off. The choice is saved in `config/jjk.json`.

Infinity is implemented but not currently part of Gojo's moveset; `infinity.inMoveset` in the config brings it back.

![Ability HUD](docs/screenshots/ability_hud.png)

## Animation

Players are jointed and animated from data, JJS-style. The full reference is [docs/ANIMATION.md](docs/ANIMATION.md).

- **A real skeleton:**
  - 17 bones: root, hips, chest, neck, head, and shoulder/elbow/wrist and hip/knee/ankle on each side.
  - The skin, its overlays and armour are cut at the joints; at rest the model looks exactly like vanilla.
  - The root carries the whole body visually: it spins, dips and jumps without moving the hitbox.
- **Animations are JSON:**
  - One folder per character (`assets/jjk/animations/gojo/...`), plus shared poses; they hot-reload.
  - Gameplay only says `Anim.play(user, "rapid_heavy")`.
  - Keys go in ms, or in the reference GIF's own frame numbers. A key can pose some bones and leave the rest.
  - Easing per key or per bone: linear, ease in/out, step, overshoot or spring. Clips and poses mirror.
- **Layered playback:**
  - Base, lower-body, upper-body and head layers.
  - Priorities from idle up to ragdoll, and interrupt windows.
  - Authored blend times, with 0 meaning a hard cut onto an impact frame.
  - Holds, loops and playback speed.
  - Bones a clip leaves alone keep vanilla's walk and look.
  - Hit reactions (flinch, launch, knockdown, guard break) are clips too.
- **A debugger:**
  - `/jjkanim ui` gives play, pause, frame step, 0.25x–2x, restart and target.
  - A skeleton overlay shows joints, bone names and axes.
  - A readout shows time, frame, key, phase, blend, priority and layer.

Rapid Punches, Twofold Kick, Lapse Blue, Reversal Red, Hollow Purple and Infinite Void (Gojo), and Shutter Doors,
Fever Breaker, Lucky Volley, Jackpot and the Rhythm dance (Hakari), and Yuta's whole kit with Rika's, are keyed
frame by frame from the JJS GIFs. Their
contact frames sit on the ticks where the hits resolve. `tools/anim_ref.py` numbers a GIF's frames for that work.
Everything held in a fist (Blue, Red, Purple's two halves, Divergent Fist) follows the real hand through the elbow and
wrist.

The client tests:
- `AnimFrameworkClientTest` checks that every clip gameplay names exists, that the rig reproduces vanilla and bends
  the way its conventions say, and the blending, priority, loop and interrupt rules.
- The opt-in `PoseGalleryClientTest` renders exact clip frames for side-by-side comparison with the references.

## Visual tiers

Every technique has its own look, and power reads at a glance:

1. **Basic** — melee: thin swing arcs, sharp impact stars, crisp rings, small ripples. A hit flash holds at full
   strength for its first moments, so even a three-tick flash is seen.
2. **Base techniques** — Blue (a compact core with light spiralling *in*, arcs of light winding down into it), Red (a
   jittering core throwing spikes *out*, wind whipping round the finger, then a fireball with smoke rolling off it),
   Infinity (faint ripples where attacks stop), Teleport (space folds shut, snaps open).
3. **Awakening** — the reveal pillar and ground waves; Max Blue (event horizon, accretion disk, dark-blue ink, the
   world's light winding in) and Max Red (white wind turning red round his arm as it charges, red lightning,
   catastrophic blast, triple shockwave) as inward/outward equals.
4. **Hollow Purple, Unlimited Purple and Infinite Void** — the only effects with impact frames; Purple is
   awakening-only and lights the world magenta.

The effects are built from a small set of shapes:
- Light: glows, rings, beams and crisp shock shells. A shell is bright only at its silhouette, and the bigger it is,
  the fainter.
- Lightning bolts that re-fork every tick, so they crackle.
- Comet-tailed swirls: wind and energy whipping round a charge.
- Alpha-blended ink: dark energy and smoke, which additive light can't draw.
- A screen tint that holds while a technique lights the world.

The opt-in `FxGalleryClientTest` plays every one of the ~200 effects in front of a fixed camera, and Unlimited Purple
shot by shot, for comparing them.

![Unlimited Purple](docs/screenshots/unlimited_purple.png)

![Base vs Max](docs/screenshots/base_vs_max.png)

Effects fade when the camera is inside them, spawn fewer particles past 48 blocks, and small ones are sound-only past
96 blocks. `particleQuality` (0–3) in the client config scales every particle count.

## Opening a domain

A domain physically builds itself out of blocks, starting at the caster's feet: the ground spreads outward to the
outer ring, the walls rise and curve over into the ceiling, then the underground half seals and a pulse confirms it —
about two seconds, with a bright energy edge tracing the newest blocks. The barrier and sure-hit switch on only once
it is sealed. Everything replaced (including paintings and item frames inside) comes back exactly when it ends.

Every opening is presented: the caster gets a short cinematic (portrait, energy, the structure forming, then
"DOMAIN EXPANSION — INFINITE VOID"); nearby players see it build in their world with a light banner.

**Counter:** when someone nearby starts opening a domain and your Awakening meter is full, your Awakening key becomes
a counter for a moment — instant Awakening, your domain opens at once, a "DOMAIN EXPANSION VS DOMAIN EXPANSION" card
presents both of you, and the domains collide into the clash below. Miss the window and the key is a normal Awakening.

![Formation](docs/screenshots/domain_formation.png)
![Counter](docs/screenshots/domain_counter_versus.png)

## Domain clashes

When two domains overlap they don't just fight on stats — the owners duel for control in a rhythm minigame. Both get
the same chart of prompts (← ↓ ↑ →, played with the arrow keys or A S W D). Each press is judged on timing
(PERFECT / GREAT / GOOD / MISS, windows configurable) and pushes a shared tug-of-war meter; streaks push harder (capped,
so consistency matters more than a lucky run). Every PERFECT sends an energy pulse out of the player through their
domain; misses make it flicker. Whoever holds the meter at the end — or drives it all the way across — wins.
Dead-even clashes go to sudden death. Domain strength, stats and who expanded first never decide it.

**Split territory.** During the clash both interiors are really there, side by side. The space is split down the
middle between the two centers: each side is built from its own domain's blocks and drawn with its own interior (the
Infinite Void's starfield on one side, Idle Death Gamble's white room and trains on the other), and each owner stands
on their side. Where the two spheres overlap their walls open up into one enclosed room. The boundary is driven by
the clash meter, so the side that plays better pushes it back; every PERFECT sends a pulse from the player's side into
the boundary. The rhythm lanes stay over the battle.

**Conquest.** When the duel is decided nothing swaps instantly: the winner's side sweeps across the loser's space over
about three seconds (50/50 → 60/40 → 75/25 → 90/10 → all of it), repainting it block by block. Then the loser's
domain ends, its space becomes the winner's territory (the barrier holds across all of it), and the winner's domain
carries on under its normal rules. When the winner's domain ends, the blocks of both are restored exactly.

![Domain clash](docs/screenshots/domain_clash.png)
![Split territory](docs/screenshots/domain_clash_split.png)
![Conquest](docs/screenshots/domain_clash_conquest.png)

## Temporary battle damage

Every block a technique destroys comes back exactly — state, container contents, the torch on it, the painting on
the wall, the sand that fell — three minutes after *that block* was damaged. Nothing drops, so nothing duplicates.
Player changes made in the meantime are kept (`restoration.conflictPolicy`). Pending restorations survive restarts.
Domain structures are separate and restore as soon as the domain ends. `/jjk restore status|now|forget`.

## Commands (op)

`/jjk arena` test arena with dummies · `/jjk dummy [stand|jump|fight] [n]` · `/jjk dummy domain <void|idg|shrine> [skill]` (solo clash practice: the nearest dummy opens that domain; press Awakening in the counter window to clash it; skill 0–1 is how well it plays) · `/jjk nocooldown true|false` ·
`/jjk awakening <amount>|end` · `/jjk reset` · `/jjk character gojo|hakari|yuji|yuta|none` · `/jjk domain cancel [all]` ·
`/jjk status [target]` · `/jjk config reload`

## Config

`config/jjk.json` holds every tunable (damage, cooldowns, costs, Awakening gain/drain/move costs, domain size/duration,
destruction limits, particle quality, screen effects, sound volume). `/jjk config reload` applies changes.

## Development

- `./gradlew build` — mod jar in `build/libs`
- `./gradlew runGameTest` — server GameTests (combat, techniques, Awakening, domain structure restore/crash recovery)
- `./gradlew runClientGameTest` — drives a real client through the whole kit with real input and takes screenshots
  (headless: run under Xvfb with Mesa; see `src/gametest` for the test-only headless mixin)
- `tools/roblox_sounds.py` rebuilds every sound from the Jujutsu Shenanigans audio (the Roblox IDs per move, fetched from
  Roblox or the JJS Skill Builder mirror at ossaamm.github.io, plus the wiki's jackpot sound); `tools/gen_textures.py`
  regenerates the textures
