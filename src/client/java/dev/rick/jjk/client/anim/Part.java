package dev.rick.jjk.client.anim;

/**
 * Model parts an animation can drive. {@link #ROOT} turns the whole body about the hips (x: lean forward, y: turn left,
 * z: lean right, degrees); {@link #ROOT_POS} moves it (x: right, y: up, z: forward, in pixels, 16 to a block).
 */
public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, ROOT, ROOT_POS }
