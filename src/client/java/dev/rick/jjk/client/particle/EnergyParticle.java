package dev.rick.jjk.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Full-bright, tintable particle used for all cursed-energy effects. Supports growing/shrinking, fading, drag,
 * and attraction toward (or repulsion from) a point, which is how Blue's inward spiral is drawn.
 */
public class EnergyParticle extends SingleQuadParticle {
    public enum Sprite {
        GLOW("glow"), CORE("core"), RING("ring"), STAR("star"), SPARK("spark"), SMOKE("smoke"), SHARD("shard");

        final Identifier id;

        Sprite(String name) {
            this.id = Identifier.fromNamespaceAndPath("jjk", name);
        }
    }

    private final float startSize, endSize;
    private final float startAlpha;
    @Nullable private Vec3 attractor;
    private double attraction;
    private float spin;
    private boolean fadeIn;

    public EnergyParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, Sprite sprite,
                          float r, float g, float b, float alpha, float startSize, float endSize, int lifetime) {
        super(level, x, y, z, vx, vy, vz, sprite(sprite));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 0.92f;
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.alpha = alpha;
        this.startAlpha = alpha;
        this.startSize = startSize;
        this.endSize = endSize;
        this.quadSize = startSize;
        this.lifetime = Math.max(1, lifetime);
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
    }

    private static TextureAtlasSprite sprite(Sprite s) {
        TextureAtlasSprite sp = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(s.id);
        return sp;
    }

    public EnergyParticle friction(float f) {
        this.friction = f;
        return this;
    }

    public EnergyParticle gravity(float g) {
        this.gravity = g;
        return this;
    }

    public EnergyParticle attract(Vec3 point, double strength) {
        this.attractor = point;
        this.attraction = strength;
        return this;
    }

    public EnergyParticle spin(float radPerTick) {
        this.spin = radPerTick;
        return this;
    }

    public EnergyParticle fadeIn() {
        this.fadeIn = true;
        return this;
    }

    public EnergyParticle physics() {
        this.hasPhysics = true;
        return this;
    }

    @Override
    public void tick() {
        if (attractor != null) {
            Vec3 d = attractor.subtract(x, y, z);
            double len = d.length();
            if (len < 0.15 && attraction > 0) {
                remove();
                return;
            }
            Vec3 pull = d.scale(attraction / Math.max(0.3, len));
            xd += pull.x;
            yd += pull.y;
            zd += pull.z;
        }
        oRoll = roll;
        roll += spin;
        super.tick();
        float life = (float) age / lifetime;
        quadSize = Mth.lerp(life, startSize, endSize);
        float fade = fadeIn ? Math.min(1f, life * 4f) * (1f - life) : 1f - life * life;
        alpha = startAlpha * fade * nearCameraFade();
    }

    /** Particles drifting right past the lens would fill the screen with one flat smear, so they fade out up close. */
    private float nearCameraFade() {
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        double d = Math.sqrt(cam.distanceToSqr(x, y, z));
        double near = 0.6 + quadSize * 2.5;
        return (float) Mth.clamp((d - quadSize) / near, 0, 1);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }
}
