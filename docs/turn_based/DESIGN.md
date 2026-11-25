# Turn-based battles (beta)

A test branch that turns the fighting into turn-based choices, played as short cinematic scenes. The moves are
rewritten for turns: they are not the real-time abilities. The engine lives in `dev.rick.jjk.battle` and has no
Minecraft code in it, so it runs (and is tested and simulated) on its own.

## The turn

1. **Choose.** Both players pick in secret on a timer: an opener, up to two combo links, and an optional cursed energy
   boost.
2. **Resolve.** The engine plays the turn and returns its beats in order (`BattleEngine.resolve`).
3. **Watch.** Each beat is a scene: attacker clip, target reaction, camera, effects. All viewers play the same beats.
4. **Turn end.** Burn ticks, cursed energy regenerates (+10), momentum updates, then the menu opens again.

## Who gets through: Initiative

> **Initiative = move speed + momentum + boost**

- **Speed:** light 3, medium 2, heavy 1. Close In / Back Off are 3. Guard is instant. Focus always goes last.
  Domains open before anything else.
- **Momentum:** +1 for winning a turn (dealing more damage), up to 2. It resets when you lose one. A Black Flash sets
  it to 2.
- **Boost:** 10 cursed energy per level (up to 3). Each level adds +1 to every move of the plan.
- Knocked **down** last turn gives -1. Inside Unlimited Void it's -2. Malevolent Shrine gives Yuji's strikes +1.

The higher initiative lands first and **cuts the other move off**, with one exception. If the slower move's category
beats the faster one's, it lands too: a **trade**.

- Strike beats Technique (it interrupts the cast).
- Technique beats Guard (it pierces).
- Guard beats Strike (it blocks lights and **parries heavies** with a counter).

**Equal initiative is a clash.** The category that wins the triangle lands at 75%. With the same category, both take
chip damage.

Only a **clean win** goes on into combo links. A clean win means the opener landed and the other move was cut off, or
the other side had no move.

## Combos (stance chains)

A hit leaves the foe in a **stance**: staggered, airborne or down. A link must follow from the stance the previous hit
left. The stance carries into the next turn, so the menu can offer follow-ups (Manji Kick needs a staggered foe). Each
link does 15% less than the one before it.

- **Yuji:** Divergent Fist → Manji Kick → Crushing Blow ("Divergent Black Flash"), or Divergent Fist → Cursed Strikes
  ("Vessel Rush").
- **Gojo:** Blue → Twofold Kick → Red ("Lapse to Reversal").

**Breaking a combo:** a guard can't stop a piercing technique. It still catches every strike link whose speed (plus
boost) is no faster than the guarding character's counter speed: Yuji 3, Gojo 2. So a greedy chain is easy to break
unless it's boosted.

## Range

Close, mid and far. Strikes mostly need close. Moves change the range: Blue pulls the foe in, Red blasts them far, and
Cursed Strikes ends at mid. Back Off is fast, so a slower strike that needed close range whiffs.

## Black Flash, domains, focus

- **Black Flash:** strikes marked for it have an 8% chance, or 25% while "in the zone" (3 turns after one lands). It
  does ×2.5 damage and sets momentum to max.
- **Domain:** the meter fills from damage dealt and taken. A domain opens before anything else.
  - **Unlimited Void:** cancels the foe's move and slows them for 3 turns.
  - **Malevolent Shrine:** 10 damage, then 7 burn per turn for 3 turns, and Yuji's strikes are faster.
  - Both domains at once is a domain clash. One wins; the loser keeps 40 meter.
- **Focus:** +35 cursed energy at the end of the turn, if nothing hits you. A hit cancels it and lands ×1.25.

## UI (keep it simple)

The whole menu is one panel at the bottom of the screen. It fades out while the turn plays.

```
 ┌─ YUJI  Vessel ───────── HP ██████████░░ 82 ┐          ┌ GOJO  Honored One ─ HP ████████░░░░ 64 ┐
 │ CE ▮▮▮▮▮▯▯▯▯▯  Momentum ●●  Domain ▰▰▰▱▱   │  · MID · │ CE ▮▮▮▮▮▮▮▯▯▯  Momentum ○○  Domain ▰▱▱▱▱│
 └──────────────────────────────────────────────┘          └─ status: STAGGERED ───────────────────────┘

                 combo: [ Divergent Fist ] → [ Manji Kick ] → [  +  ]        Speed 3 +2 momentum +1 boost = 6
 ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
 │ 1 Divergent Fist │ │ 2 Manji Kick     │ │ 3 Cursed Strikes │ │ 4 Crushing Blow  │
 │ ✊ STRIKE  ●●●    │ │ ✊ STRIKE  ●●●    │ │ ✊ STRIKE  ●●○    │ │ ✊ STRIKE  ●○○    │
 │ 10 dmg · 0 CE    │ │ LINK ✓ · 5 CE    │ │ 15 dmg · 10 CE   │ │ greyed: Range    │
 └──────────────────┘ └──────────────────┘ └──────────────────┘ └──────────────────┘
   [Q] Counter Stance   [F] Focus   [W] Close In   [S] Back Off   [R] DOMAIN   boost [−] 🔥🔥▫ [+]     [ENTER] Lock in  ⏱ 12
```

**Picking moves:**
- **Four big move cards**, keys 1–4. Each card shows the category icon (fist, swirl, shield), speed pips, damage and
  cost, and one line of text.
- An unusable card is greyed out with the reason ("Out of range", "Needs: Staggered").
- Clicking a card adds it to the **combo strip**. The cards then show only the moves that can follow, marked LINK.
  Backspace removes the last one.

**The rest of the panel:**
- **One row of small buttons** for the shared choices: Guard (the character's own name), Focus, Close In, Back Off,
  and Domain, which glows when the meter is full.
- **Boost:** scroll the mouse or press −/+. The live **initiative preview** shows exactly why you'll be fast or slow.
- **Lock in:** Enter or the button. A timer ring counts down 15 s; when it runs out you take your current pick, or
  Guard.
- **Top:** Pokémon-style bars for both sides (HP, CE, momentum, domain meter, stance and status), with the range in
  the middle.
- **Hover preview:** hovering a card makes your character start that move's wind-up and moves the camera to its
  angle. This only happens on your own screen, so nothing leaks to the other player.
- **While the turn plays:** no menu, just letterbox bars, the move-name title card, damage numbers and the combo
  counter. A "fast" toggle trims repeated scenes.

## Control (implemented)

- **Taking control:** a clean win of the exchange takes **control**. That means the opener landed and the foe's move
  was cut off, or a parry, or winning a clash. Control is +1 per turn (max 2), and you lose it when the foe takes it. A
  trade or block gives nobody control.
- **What it does:**
  - Holding control gives +1 initiative.
  - Combos can be **3 moves** (otherwise 2).
  - At **full control** a complete combo ends in the character's **finisher**: Yuji's Black Flash, Gojo's Limitless
    Barrage.
- **Reversal:** before each link, a defender with 25+ cursed energy may **reverse**: they read the combo, slip it,
  counter and steal control. The chance grows with each link (10% + 5% per link), and guarding defenders can't reverse.
- **On screen:** the controller's combo plays as **one take**.
  - Each hit starts on the last one's follow-through, and the fighters stay where the hits leave them.
  - Juggles go higher, and links speed up.
  - The finisher gets a beat of stillness before it.
  - One continuous camera orbits on the controller's side, pushing in on each contact; a reversal flips it.
  - The letterbox bars close in.

## Menu (implemented)

The menu is laid out like Pokémon: a text box on the left and four options on the right, each a section of up to
four choices.

- FIGHT (four moves, then COMBO: the follow-ups or FINISH)
- DEFEND (guard, Focus)
- MOVE (Close In, Back Off)
- CURSED (Domain when ready, Boost, Forfeit)

Keys 1–4 pick, and Esc goes back. Text during the fight is kept quiet:

- The move name, small in the letterbox.
- A small damage number.
- A title card only for Black Flash, named combos, domains and the result.

## Build order

1. ✅ Engine, kits, AI, rule tests and a balance simulator (JUnit, `BattleEngineTest`, `BattleSimulationTest`).
2. Arena and lock: `/jjk battle <player|ai>`. Teleport both fighters to marks, lock them, end on a KO or forfeit.
3. Menu UI (above). Network: the choices go up, the beats come down.
4. Cinematic player: beats → clips, reactions, camera director, effects. Prove it on Yuji's combo.
5. Reactive idles and the choosing-phase camera. Then the other characters.
