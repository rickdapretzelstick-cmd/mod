package dev.rick.jjk.battle;

import dev.rick.jjk.battle.BattleMove.Category;
import dev.rick.jjk.battle.BattleMove.Range;
import dev.rick.jjk.battle.BattleMove.Stance;
import dev.rick.jjk.battle.Beat.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * The turn-based rules. Both sides lock in a {@link Plan} in secret; {@link #resolve} plays the turn out and returns the
 * beats in order.
 *
 * <p><b>Initiative</b> = the move's speed (light 3, medium 2, heavy 1) + 1 while holding control + cursed energy boost (0-3).
 * The faster attack lands first and cuts the slower one off, unless the slower one's category beats the faster one's
 * (Strike beats Technique, Technique beats Guard, Guard beats Strike): then it lands too (a trade). Equal initiative is
 * a clash. Guard is instant: it blocks strikes and parries heavy ones, but techniques pierce it, and after a piercing
 * technique the guard still catches strike links no faster than the guarding character's counter speed. Only a clean
 * win (the opener landed and the other side's move was cut off, or it had none) goes on into its combo links.
 */
public final class BattleEngine {
    public static final int BOOST_COST = 10, MAX_BOOST = 3, MAX_CHAIN = 3, FOCUS_GAIN = 35, REGEN = 10;
    public static final int BURN_DAMAGE = 7, EFFECT_TURNS = 3, ZONE_TURNS = 3, CLASH_LOSER_METER = 40;
    public static final double BLACK_FLASH = 0.08, BLACK_FLASH_ZONE = 0.25, BLACK_FLASH_MULT = 2.5;
    public static final double LINK_DECAY = 0.85, CLASH_WIN = 0.75, CLASH_CHIP = 0.3, BLOCK_CHIP = 0.2, FOCUS_TAKEN = 1.25;
    public static final double METER_DEALT = 0.6, METER_TAKEN = 0.8;
    /** A reversal costs the defender this much cursed energy; its chance grows with each link of the combo. */
    public static final int REVERSAL_COST = 25;
    public static final double REVERSAL_CHANCE = 0.10, REVERSAL_STEP = 0.05;

    /** How many moves a combo may have: two, or three while holding control. */
    public static int maxChain(Fighter f) {
        return f.control >= 1 ? MAX_CHAIN : MAX_CHAIN - 1;
    }

    private BattleEngine() {}

    // ------------------------------------------------------------------------------------------------ legality

    /** Why {@code side} can't open with {@code m} right now, or null if it can. The menu greys a move out with this. */
    public static String whyNot(BattleState s, int side, BattleMove m) {
        if (s.over()) return "The battle is over";
        Fighter me = s.f[side], foe = s.f[1 - side];
        return switch (m.category) {
            case DOMAIN -> me.domainTurns > 0 ? "Domain already open" : me.meter < Fighter.MAX_METER ? "Domain meter not full" : null;
            case MOVE -> m.from.contains(s.range) ? null : m.moveStep < 0 ? "Already close" : "Can't back off further";
            case STRIKE, TECHNIQUE -> !m.from.contains(s.range) ? "Out of range"
                    : m.needs != null && foe.stance != m.needs ? "Needs: " + pretty(m.needs)
                    : me.ce < m.cost ? "Not enough cursed energy" : null;
            default -> null;
        };
    }

    /** Moves that can follow {@code prev} when the range after it is {@code range}. */
    public static List<BattleMove> links(BattleKit kit, BattleMove prev, Range range) {
        List<BattleMove> out = new ArrayList<>();
        for (BattleMove m : kit.moves) if (m.linksAfter(prev) && m.from.contains(range)) out.add(m);
        return out;
    }

    /** The range after {@code m} lands from {@code r}. */
    public static Range rangeAfter(BattleMove m, Range r) {
        return m.rangeAfter != null ? m.rangeAfter : r;
    }

    /** The plan's total cursed energy cost (moves plus boost). */
    public static int cost(BattleKit kit, Plan p) {
        int c = p.boost() * BOOST_COST;
        for (String id : p.chain()) {
            BattleMove m = kit.move(id);
            if (m != null) c += m.cost;
        }
        return c;
    }

    /** Why the plan isn't allowed, or null if it is. */
    public static String validate(BattleState s, int side, Plan p) {
        Fighter me = s.f[side];
        if (p.chain().isEmpty()) return "Pick a move";
        if (p.chain().size() > maxChain(me)) return "Combos are " + maxChain(me) + " moves long (take control for longer)";
        if (p.boost() < 0 || p.boost() > MAX_BOOST) return "Boost is 0 to " + MAX_BOOST;
        BattleMove first = me.kit.move(p.opener());
        if (first == null) return "Unknown move " + p.opener();
        String why = whyNot(s, side, first);
        if (why != null) return why;
        if (!first.category.attack() && (p.chain().size() > 1 || p.boost() > 0)) return "Only attacks can chain or be boosted";
        if (cost(me.kit, p) > me.ce) return "Not enough cursed energy";
        BattleMove prev = first;
        Range r = rangeAfter(first, s.range);
        for (int i = 1; i < p.chain().size(); i++) {
            BattleMove m = me.kit.move(p.chain().get(i));
            if (m == null || !me.kit.moves.contains(m)) return "Unknown link " + p.chain().get(i);
            if (!m.linksAfter(prev)) return m.name + " can't follow " + prev.name;
            if (!m.from.contains(r)) return m.name + " is out of range after " + prev.name;
            prev = m;
            r = rangeAfter(m, r);
        }
        return null;
    }

    public static String pretty(Stance s) {
        return s.name().charAt(0) + s.name().substring(1).toLowerCase();
    }

    // ------------------------------------------------------------------------------------------------ resolution

    /** Plays one turn. Mutates {@code s}; returns what happened, in order. An illegal plan becomes a guard. */
    public static List<Beat> resolve(BattleState s, Plan p0, Plan p1) {
        return new Turn(s, p0, p1).run();
    }

    private static final class Turn {
        final BattleState s;
        final Plan[] plan;
        final BattleMove[] op = new BattleMove[2];
        final boolean[] hit = new boolean[2], guarding = new boolean[2], focusing = new boolean[2];
        final int[] dealt = new int[2], startControl = new int[2];
        /** Who took control this turn (-1: nobody). */
        int controller = -1;
        final List<List<String>> executed = List.of(new ArrayList<>(), new ArrayList<>());
        final List<Beat> beats = new ArrayList<>();

        Turn(BattleState s, Plan p0, Plan p1) {
            this.s = s;
            this.plan = new Plan[] {p0, p1};
        }

        List<Beat> run() {
            if (s.over()) return beats;
            startControl[0] = s.f[0].control;
            startControl[1] = s.f[1].control;
            for (int i = 0; i < 2; i++) {
                Fighter f = s.f[i];
                if (plan[i] == null || validate(s, i, plan[i]) != null) plan[i] = Plan.of(f.kit.guard.id);
                op[i] = f.kit.move(plan[i].opener());
                f.ce -= plan[i].boost() * BOOST_COST;
            }
            domains();
            if (!s.over()) exchange();
            endOfTurn();
            return beats;
        }

        // --- domains open before anything else

        void domains() {
            boolean d0 = op[0].category == Category.DOMAIN, d1 = op[1].category == Category.DOMAIN;
            if (d0 && d1) {
                int w = s.roll() < 0.5 ? 0 : 1;
                beat(w, op[w].id, Kind.DOMAIN_CLASH, 0, 0, false, "");
                s.f[1 - w].meter = CLASH_LOSER_METER;
                op[1 - w] = null;
                openDomain(w);
                op[w] = null;
            } else if (d0 || d1) {
                int d = d0 ? 0 : 1;
                boolean cancels = openDomain(d);
                op[d] = null;
                if (cancels && op[1 - d] != null && !s.over()) {
                    beat(1 - d, op[1 - d].id, Kind.STUNNED, 0, 0, false, "");
                    op[1 - d] = null;
                }
            }
        }

        /** Opens {@code i}'s domain. Returns whether it cancels the foe's move this turn. */
        boolean openDomain(int i) {
            Fighter me = s.f[i], foe = s.f[1 - i];
            me.meter = 0;
            me.domainTurns = EFFECT_TURNS;
            if (me.kit.character.equals("gojo")) {
                foe.slowTurns = EFFECT_TURNS;
                beat(i, me.kit.domain.id, Kind.DOMAIN, 0, 0, false, me.kit.domain.name);
                return true;
            }
            foe.burnTurns = EFFECT_TURNS;
            foe.burnDamage = BURN_DAMAGE;
            me.strikeBoostTurns = EFFECT_TURNS;
            int d = me.kit.domain.power;
            hurt(i, d);
            beat(i, me.kit.domain.id, Kind.DOMAIN, d, 0, false, me.kit.domain.name);
            checkKo(i);
            return false;
        }

        // --- the exchange

        void exchange() {
            for (int i = 0; i < 2; i++) {
                BattleMove m = op[i];
                if (m == null) continue;
                guarding[i] = m.category == Category.GUARD;
                focusing[i] = m.category == Category.FOCUS;
                // An attack that can't reach or has nothing to follow up on misses before anything else happens.
                if (m.category.attack() && !reaches(i, m, false)) {
                    s.f[i].ce -= m.cost;
                    beat(i, m.id, Kind.WHIFF, 0, 0, false, "");
                    op[i] = null;
                }
            }
            BattleMove a = op[0], b = op[1];
            if (a == null && b == null) {
                // nothing
            } else if (a == null || b == null) {
                lone(a == null ? 1 : 0);
            } else if (guarding[0] && guarding[1]) {
                beat(0, a.id, Kind.GUARD, 0, 0, false, "");
                beat(1, b.id, Kind.GUARD, 0, 0, false, "");
            } else if (guarding[0] || guarding[1]) {
                vsGuard(guarding[0] ? 0 : 1);
            } else if (!a.category.attack() && !b.category.attack()) {
                int first = initiative(0) >= initiative(1) ? 0 : 1;
                nonAttack(first);
                nonAttack(1 - first);
            } else if (a.category.attack() != b.category.attack()) {
                int x = a.category.attack() ? 0 : 1, y = 1 - x;
                if (op[y].category == Category.MOVE && initiative(y) > initiative(x)) {
                    nonAttack(y);
                    attack(x);
                } else {
                    boolean landed = attack(x);
                    if (op[y].category == Category.MOVE) {
                        if (landed) beat(y, op[y].id, Kind.INTERRUPTED, 0, 0, false, "");
                        else if (!s.over()) nonAttack(y);
                    }
                }
            } else {
                int i0 = initiative(0), i1 = initiative(1);
                if (i0 == i1) clash();
                else {
                    int fast = i0 > i1 ? 0 : 1, slow = 1 - fast;
                    if (!land(fast, op[fast], 0, Kind.HIT, 1)) {
                        attack(slow);
                    } else if (!s.over() && op[slow].category.beats(op[fast].category)) {
                        land(slow, op[slow], 0, Kind.TRADE, 1);
                    } else if (!s.over()) {
                        beat(slow, op[slow].id, Kind.INTERRUPTED, 0, 0, false, "");
                        takeControl(fast);
                        chain(fast);
                    }
                }
            }
            for (int i = 0; i < 2; i++) {
                if (!focusing[i] || s.over()) continue;
                if (hit[i]) beat(i, BattleKits.FOCUS.id, Kind.INTERRUPTED, 0, 0, false, "");
                else {
                    s.f[i].ce = Math.min(Fighter.MAX_CE, s.f[i].ce + FOCUS_GAIN);
                    beat(i, BattleKits.FOCUS.id, Kind.FOCUS, 0, 0, false, "");
                }
            }
        }

        /** One side acts and the other doesn't. */
        void lone(int i) {
            BattleMove m = op[i];
            if (m.category.attack()) attack(i);
            else if (m.category == Category.GUARD) beat(i, m.id, Kind.GUARD, 0, 0, false, "");
            else nonAttack(i);
        }

        void nonAttack(int i) {
            BattleMove m = op[i];
            if (m == null || m.category != Category.MOVE) return; // focus resolves at the end of the exchange
            s.range = m.moveStep < 0 ? Range.CLOSE : s.range.further();
            beat(i, m.id, Kind.MOVE, 0, 0, false, "");
        }

        /** {@code g} guards against the other side's move. */
        void vsGuard(int g) {
            int o = 1 - g;
            BattleMove m = op[o];
            if (m.category.attack()) {
                if (m.category == Category.TECHNIQUE || m.pierce) {
                    attack(o);
                    return;
                }
                Fighter att = s.f[o];
                att.ce -= m.cost;
                caught(o, m);
                return;
            }
            if (m.category == Category.MOVE) nonAttack(o);
            beat(g, op[g].id, Kind.GUARD, 0, 0, false, "");
        }

        /** A strike of {@code i}'s ran into the other side's guard: a heavy is parried, anything else blocked. */
        void caught(int i, BattleMove m) {
            Fighter att = s.f[i], def = s.f[1 - i];
            if (m.weight == BattleMove.Weight.HEAVY) {
                int d = def.kit.parryDamage;
                hurt(1 - i, d);
                att.stance = Stance.STAGGERED;
                controller = 1 - i;
                beat(i, m.id, Kind.PARRY, d, 0, false, "");
                checkKo(1 - i);
            } else {
                int d = (int) Math.round(m.power * BLOCK_CHIP);
                hurt(i, d);
                att.control = 0;
                // Bounced off the guard: left open for the defender's follow-ups next turn.
                att.stance = Stance.STAGGERED;
                hit[i] = true;
                beat(i, m.id, Kind.BLOCK, d, 0, false, "");
                checkKo(i);
            }
        }

        /** The opener then, if it landed cleanly, the links. Returns whether the opener landed. */
        boolean attack(int i) {
            if (!land(i, op[i], 0, guarding[1 - i] ? Kind.PIERCE : Kind.HIT, 1)) return false;
            takeControl(i);
            chain(i);
            return true;
        }

        void chain(int i) {
            Fighter me = s.f[i], foe = s.f[1 - i];
            Plan p = plan[i];
            boolean complete = true;
            int k = 1;
            for (; k < p.chain().size() && !s.over(); k++) {
                BattleMove m = me.kit.move(p.chain().get(k));
                if (!m.linkFrom.contains(foe.stance)) {
                    me.ce -= m.cost;
                    beat(i, m.id, Kind.WHIFF, 0, k, false, "");
                    complete = false;
                    break;
                }
                if (guarding[1 - i] && m.category == Category.STRIKE && !m.pierce
                        && m.weight.speed + p.boost() <= foe.kit.counterSpeed) {
                    me.ce -= m.cost;
                    caught(i, m);
                    complete = false;
                    break;
                }
                if (reversal(i, k)) {
                    complete = false;
                    break;
                }
                if (!land(i, m, k, guarding[1 - i] ? Kind.PIERCE : Kind.HIT, 1)) {
                    complete = false;
                    break;
                }
            }
            // A full combo at max control ends in the finisher.
            if (complete && !s.over() && p.chain().size() >= 2 && startControl[i] >= Fighter.MAX_CONTROL && controller == i) {
                land(i, me.kit.finisher, k, Kind.FINISHER, 1);
            }
            nameCombo(i);
        }

        /** {@code i} took control: its opener (the last beat) and everything it chains are one sequence. */
        void takeControl(int i) {
            controller = i;
            for (int k = beats.size() - 1; k >= 0; k--) {
                Beat b = beats.get(k);
                if (b.actor() == i && b.kind() != Kind.KO) {
                    beats.set(k, new Beat(b.actor(), b.move(), b.kind(), b.damage(), b.link(), b.blackFlash(), b.note(),
                            b.range(), b.targetStance(), b.hp0(), b.hp1(), true));
                    break;
                }
            }
        }

        /**
         * Before link {@code k} of {@code i}'s combo: the defender may read it and reverse (it costs cursed energy and
         * gets likelier the longer the combo runs). Returns whether it did: control changes hands.
         */
        boolean reversal(int i, int k) {
            Fighter foe = s.f[1 - i];
            if (guarding[1 - i] || foe.ce < REVERSAL_COST) return false;
            if (s.roll() >= REVERSAL_CHANCE + REVERSAL_STEP * k) return false;
            foe.ce -= REVERSAL_COST;
            int d = foe.kit.parryDamage;
            hurt(1 - i, d);
            s.f[i].stance = Stance.STAGGERED;
            hit[i] = true;
            foe.stance = Stance.GROUNDED; // read it and broke out
            controller = 1 - i;
            beat(1 - i, foe.kit.guard.id, Kind.REVERSAL, d, k, false, "");
            checkKo(1 - i);
            return true;
        }

        void clash() {
            BattleMove a = op[0], b = op[1];
            if (a.category.beats(b.category) || b.category.beats(a.category)) {
                int w = a.category.beats(b.category) ? 0 : 1;
                if (land(w, op[w], 0, Kind.CLASH, CLASH_WIN) && !s.over()) {
                    controller = w;
                    beat(1 - w, op[1 - w].id, Kind.INTERRUPTED, 0, 0, false, "");
                }
                return;
            }
            s.f[0].ce -= a.cost;
            s.f[1].ce -= b.cost;
            int d1 = (int) Math.round(a.power * CLASH_CHIP), d0 = (int) Math.round(b.power * CLASH_CHIP);
            hurt(0, d1);
            beat(0, a.id, Kind.CLASH, d1, 0, false, "");
            hurt(1, d0);
            beat(1, b.id, Kind.CLASH, d0, 0, false, "");
            checkKo(0);
            if (!s.over()) checkKo(1);
        }

        /** Whether {@code i}'s move can hit right now (range, and the stance it needs or follows). */
        boolean reaches(int i, BattleMove m, boolean asLink) {
            Stance st = s.f[1 - i].stance;
            if (!m.from.contains(s.range)) return false;
            return asLink ? m.linkFrom.contains(st) : m.needs == null || st == m.needs;
        }

        /** Tries to land {@code m}; pays its cost either way. Returns false on a miss. */
        boolean land(int i, BattleMove m, int link, Kind kind, double mult) {
            Fighter me = s.f[i], foe = s.f[1 - i];
            me.ce -= m.cost;
            if (!reaches(i, m, link > 0)) {
                beat(i, m.id, Kind.WHIFF, 0, link, false, "");
                return false;
            }
            // Each link hits a little softer; the finisher lands whole.
            double d = m.power * mult * (kind == Kind.FINISHER ? 1 : Math.pow(LINK_DECAY, link));
            if (foe.stance == Stance.AIRBORNE) d *= m.vsAirborne;
            if (focusing[1 - i]) d *= FOCUS_TAKEN;
            if (guarding[1 - i] && m.category == Category.TECHNIQUE && !m.pierce) d *= foe.kit.guardTechniqueMult;
            boolean bf = m.blackFlash && s.roll() < (me.zoneTurns > 0 ? BLACK_FLASH_ZONE : BLACK_FLASH);
            if (bf) {
                d *= BLACK_FLASH_MULT;
                me.control = Math.max(me.control, 1);
                me.zoneTurns = ZONE_TURNS;
            }
            foe.stance = m.inflicts;
            s.range = rangeAfter(m, s.range);
            int dmg = (int) Math.round(d);
            hurt(i, dmg);
            executed.get(i).add(m.id);
            beat(i, m.id, kind, dmg, link, bf, "");
            checkKo(i);
            return true;
        }

        void nameCombo(int i) {
            List<String> done = executed.get(i);
            if (done.size() < 2 || beats.isEmpty()) return;
            for (BattleKit.NamedCombo c : s.f[i].kit.combos) {
                List<String> seq = done.size() > c.sequence().size() && done.getLast().equals(s.f[i].kit.finisher.id)
                        ? done.subList(0, done.size() - 1) : done;
                if (!seq.equals(c.sequence())) continue;
                // Put the title card on the combo's last hit.
                for (int k = beats.size() - 1; k >= 0; k--) {
                    Beat b = beats.get(k);
                    if (b.actor() == i && b.move().equals(c.sequence().getLast()) && b.damage() > 0) {
                        beats.set(k, new Beat(b.actor(), b.move(), b.kind(), b.damage(), b.link(), b.blackFlash(), c.name(),
                                b.range(), b.targetStance(), b.hp0(), b.hp1(), b.control()));
                        break;
                    }
                }
            }
        }

        int initiative(int i) {
            BattleMove m = op[i];
            Fighter f = s.f[i];
            int base = switch (m.category) {
                case GUARD -> 99;
                case DOMAIN -> 50;
                case FOCUS -> -10;
                case MOVE -> 3;
                default -> m.weight.speed;
            };
            if (m.category.attack() || m.category == Category.MOVE) {
                base += plan[i].boost() + Math.min(1, f.control);
                if (f.slowTurns > 0) base -= 2;
                if (f.stance == Stance.DOWN) base -= 1;
                if (f.strikeBoostTurns > 0 && m.category == Category.STRIKE) base += 1;
            }
            return base;
        }

        // --- bookkeeping

        /** {@code attacker} deals {@code d} to the other side. */
        void hurt(int attacker, int d) {
            if (d <= 0) return;
            Fighter me = s.f[attacker], foe = s.f[1 - attacker];
            foe.hp -= d;
            hit[1 - attacker] = true;
            dealt[attacker] += d;
            me.meter = Math.min(Fighter.MAX_METER, me.meter + (int) Math.round(d * METER_DEALT));
            foe.meter = Math.min(Fighter.MAX_METER, foe.meter + (int) Math.round(d * METER_TAKEN));
        }

        void checkKo(int attacker) {
            if (s.over() || s.f[1 - attacker].hp > 0) return;
            s.f[1 - attacker].hp = 0;
            s.winner = attacker;
            beat(attacker, "", Kind.KO, 0, 0, false, "");
        }

        void endOfTurn() {
            for (int i = 0; i < 2 && !s.over(); i++) {
                Fighter f = s.f[i];
                if (f.burnTurns > 0) {
                    f.burnTurns--;
                    hurt(1 - i, f.burnDamage);
                    beat(1 - i, "", Kind.BURN, f.burnDamage, 0, false, "");
                    checkKo(1 - i);
                }
            }
            for (int i = 0; i < 2; i++) {
                Fighter f = s.f[i];
                if (f.slowTurns > 0) f.slowTurns--;
                if (f.strikeBoostTurns > 0) f.strikeBoostTurns--;
                if (f.zoneTurns > 0) f.zoneTurns--;
                if (f.domainTurns > 0) f.domainTurns--;
                if (!hit[i]) f.stance = Stance.GROUNDED;
                f.ce = Math.min(Fighter.MAX_CE, Math.max(0, f.ce) + REGEN);
            }
            if (controller >= 0) {
                s.f[controller].control = Math.min(Fighter.MAX_CONTROL, startControl[controller] + 1);
                s.f[1 - controller].control = 0;
            }
            s.turn++;
            if (!s.over() && s.turn > BattleState.TURN_LIMIT) {
                int h0 = s.f[0].hp, h1 = s.f[1].hp;
                s.winner = h0 == h1 ? 2 : h0 > h1 ? 0 : 1;
            }
        }

        void beat(int actor, String move, Kind kind, int damage, int link, boolean bf, String note) {
            boolean control = controller == actor && (link > 0 || kind == Kind.FINISHER || kind == Kind.REVERSAL);
            beats.add(new Beat(actor, move, kind, damage, link, bf, note, s.range, s.f[1 - actor].stance, s.f[0].hp, s.f[1].hp, control));
        }
    }
}
