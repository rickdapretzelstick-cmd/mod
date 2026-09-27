package dev.rick.jjk.core.defense;

/** A defense's verdict on an incoming attack. */
public record DefenseResult(Kind kind, float damageScale, String reason) {
    public enum Kind {
        /** Not handled; the next defense layer gets a look. */
        PASS,
        /** Let through with damage scaled; later layers still apply. */
        REDUCE,
        /** Stopped: no damage, knockback or hitstun. */
        NEGATE,
        /** Guarded: scaled damage, no hitstun. */
        BLOCK,
        /** Guarded at the perfect moment. */
        PARRY,
        /** Defense collapsed; the attack lands and the defender is guard-broken. */
        BREAK
    }

    public static final DefenseResult PASS = new DefenseResult(Kind.PASS, 1f, "");

    public static DefenseResult negate(String reason) {
        return new DefenseResult(Kind.NEGATE, 0f, reason);
    }

    public static DefenseResult reduce(float scale, String reason) {
        return new DefenseResult(Kind.REDUCE, scale, reason);
    }

    public static DefenseResult block(float scale) {
        return new DefenseResult(Kind.BLOCK, scale, "guard");
    }
}
