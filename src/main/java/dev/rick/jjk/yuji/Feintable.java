package dev.rick.jjk.yuji;

/**
 * A Vessel move that Combat Instincts can take back during its wind-up: the move stops with no endlag and stays off
 * cooldown. Whatever momentum the wind-up gave him (the aerial variants' hops) is kept.
 */
public interface Feintable {
    /** Still in the wind-up (nothing committed yet). */
    boolean feintable();
}
