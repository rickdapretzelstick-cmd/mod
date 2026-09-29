package dev.rick.jjk.client.anim;

/**
 * Which animation wins when several want the same bones. A higher priority overrides a lower one and can't be cut off
 * by it unless it is interruptible (within its interrupt window).
 */
public enum Priority { IDLE, MOVEMENT, COMBAT, ATTACK, SPECIAL, AWAKENING, RAGDOLL }
