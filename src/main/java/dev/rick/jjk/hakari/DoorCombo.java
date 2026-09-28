package dev.rick.jjk.hakari;

/**
 * A move with a wind-up that Shutter Doors can be pressed into: Reserve Balls (the doors appear where the ball lands)
 * and Fever Breaker (Fever Crush). Pressing Shutter Doors then puts both moves on their usual cooldowns.
 */
interface DoorCombo {
    /** Still in the wind-up, and not combined yet. */
    boolean acceptsDoors();

    void addDoors();
}
