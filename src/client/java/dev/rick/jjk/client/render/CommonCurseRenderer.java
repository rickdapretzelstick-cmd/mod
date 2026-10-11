package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.ClientProgression;
import dev.rick.jjk.client.anim.AnimLibrary;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.model.BbModel;
import dev.rick.jjk.client.model.BbModels;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * One renderer for every common curse: its Blockbench model ({@code models/bb/<kind>.bbmodel}, 16 units to the block,
 * front -Z), its texture ({@code textures/entity/<kind>.png}) and its clips ({@code animations/<kind>/}). The server
 * plays the attack, hurt and death clips; here the idle and move loops follow how fast it is actually moving, but only
 * while nothing more important (an attack, a flinch, the death) is playing. Only drawn for a player who perceives curses.
 *
 * <p>Its fight state reads at a glance: winding up, it pulses a hot warning orange (lit, so it shows in the dark);
 * exposed or staggered, it flashes white and gold; frozen in a hit's hitstop it shudders; staggered it reels; enraged it
 * runs red.
 */
public class CommonCurseRenderer<T extends CommonCurseEntity> extends EntityRenderer<T, CommonCurseRenderer.State> {
    private final String kind;
    private final RenderType solid;
    private final float scale;
    private final int baseTint;

    public static class State extends EntityRenderState {
        public float yaw;
        public float hurt;
        public boolean visible;
        public boolean windup, exposed, frozen, staggered, enraged;
        public float now;
        public int id;
        @Nullable public PoseFrame pose;
    }

    public CommonCurseRenderer(EntityRendererProvider.Context ctx, String kind, float shadow) {
        this(ctx, kind, shadow, 1f, 0xFFFFFFFF);
    }

    /** A curse drawn with another's model (a placeholder): scaled, and tinted so it reads as something else. */
    public CommonCurseRenderer(EntityRendererProvider.Context ctx, String kind, float shadow, float scale, int baseTint) {
        super(ctx);
        this.kind = kind;
        this.scale = scale;
        this.baseTint = baseTint;
        this.solid = RenderTypes.entityCutout(JJK.id("textures/entity/" + kind + ".png"));
        this.shadowRadius = shadow;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(T e, Frustum culler, double x, double y, double z, float partial) {
        return ClientProgression.canSee(e) && super.shouldRender(e, culler, x, y, z, partial);
    }

    @Override
    public void extractRenderState(T e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.visible = ClientProgression.canSee(e);
        s.yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        s.hurt = e.hurtTime > 0 ? e.hurtTime / 10f : 0f;
        float now = e.level().getGameTime() + partial;
        s.now = now;
        s.id = e.getId();
        s.windup = e.isWindingUp();
        s.exposed = e.isExposed();
        s.frozen = e.isFrozen();
        s.staggered = e.isStaggered();
        s.enraged = e.isEnraged();
        if (e.deathTime == 0) {
            double dx = e.getX() - e.xo, dy = e.getY() - e.yo, dz = e.getZ() - e.zo;
            boolean flies = e.isNoGravity();
            double speed = Math.sqrt(dx * dx + dz * dz + (flies ? dy * dy : 0));
            loop(e.getId(), speed > (flies ? 0.06 : 0.025) ? "move" : "idle", now);
        }
        s.pose = ClientAnimations.computeModel(e, now);
    }

    /** Keeps the right locomotion loop running, unless a clip from the server (attack, hurt, death) is on top. */
    private void loop(int id, String which, float now) {
        String want = kind + "_" + which;
        String cur = ClientAnimations.current(id);
        if (cur != null && !cur.equals(kind + "_idle") && !cur.equals(kind + "_move")) return;
        if (want.equals(cur) || AnimLibrary.get(want) == null) return;
        ClientAnimations.play(id, want, 1f, now);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        BbModel model = BbModels.get(kind);
        if (model == null || !s.visible) return;
        ps.pushPose();
        if (s.frozen) {
            // Hitstop: a shudder in place.
            float j = 0.035f * scale;
            ps.translate(Mth.sin(s.now * 9.1f + s.id) * j, 0, Mth.cos(s.now * 7.3f + s.id) * j);
        }
        ps.rotate(Axis.YP.rotationDegrees(180f - s.yaw));
        if (s.staggered) ps.rotate(Axis.ZP.rotationDegrees(Mth.sin(s.now * 0.9f) * 9f));
        ps.scale(scale / 16f, scale / 16f, scale / 16f);
        BbModel.Posing pose = RikaRenderer.posing(s.pose);
        Look look = look(s, baseTint);
        int light = look.lit ? 0xF000F0 : s.lightCoords;
        c.submitCustomGeometry(ps, solid, (p, buf) -> model.render(p, buf, pose, light, look.tint, look.overlay));
        ps.popPose();
        super.submit(s, ps, c, cam);
    }

    /** How it's drawn this frame: the colour it's multiplied by, the overlay flash, and whether it's lit regardless. */
    public record Look(int tint, int overlay, boolean lit) {}

    public static Look look(State s, int baseTint) {
        if (s.hurt > 0 && !s.exposed) {
            int gb = Math.round(255 - 110 * s.hurt);
            return new Look(0xFF000000 | 0xFF << 16 | gb << 8 | gb, OverlayTexture.NO_OVERLAY, false);
        }
        if (s.exposed) {
            // Open: flashing white and gold, four ticks each.
            boolean white = ((int) s.now / 3) % 2 == 0;
            return new Look(white ? 0xFFFFFFFF : 0xFFFFE08A, OverlayTexture.pack(OverlayTexture.u(white ? 0.55f : 0.25f), OverlayTexture.v(false)), true);
        }
        if (s.windup) {
            // About to commit: a hot pulse.
            float p = 0.5f + 0.5f * Mth.sin(s.now * 1.3f);
            int g = Math.round(110 + 50 * p), b = Math.round(60 + 30 * p);
            return new Look(0xFF000000 | 0xFF << 16 | g << 8 | b, OverlayTexture.pack(OverlayTexture.u(0.2f + 0.35f * p), OverlayTexture.v(false)), true);
        }
        if (s.enraged) return new Look(0xFFFF9C9C, OverlayTexture.NO_OVERLAY, false);
        return new Look(baseTint, OverlayTexture.NO_OVERLAY, false);
    }

    /** For tests and previews: the texture this renderer draws with. */
    public Identifier texture() {
        return JJK.id("textures/entity/" + kind + ".png");
    }
}
