package dev.rick.jjk.client.rifle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.model.BbModel;
import dev.rick.jjk.client.model.BbModels;
import dev.rick.jjk.client.render.RikaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * The Cursed Rifle in someone's hands: its Blockbench model ({@code models/bb/cursed_rifle.bbmodel}) posed by the
 * holder's rifle clips ({@link RifleClient}), so the arms unfold, the lenses build and the barrel kicks exactly as the
 * server's phase says, for the shooter and for everyone watching. In inventories and on the ground it is the flat icon
 * instead (see the item's definition); at rest it is the model's rest pose.
 *
 * <p>The item system only hands a special renderer the stack, so the holder is passed in from
 * {@code SpecialModelWrapperMixin} for the length of one {@code update}.
 */
public final class RifleItemRenderer implements SpecialModelRenderer<RifleItemRenderer.Arg> {
    /** Model pixels to the item's block (it's long: about 1.3 blocks held). */
    static final float SCALE = 0.55f;
    /** The grip, which sits at the hand. */
    static final Vector3f GRIP = new Vector3f(0, 11, 4);

    /** Extra turn (degrees, X then Y then Z) in third person, applied about the grip. */
    public static float[] THIRD_PERSON = {0, 0, 0};

    public record Arg(@Nullable PoseFrame pose, int holder, boolean world) {}

    private static final ThreadLocal<Object[]> CONTEXT = new ThreadLocal<>();

    public static void enter(@Nullable ItemOwner owner, ItemDisplayContext ctx) {
        CONTEXT.set(new Object[] {owner, ctx});
    }

    public static void exit() {
        CONTEXT.remove();
    }

    @Override
    public @Nullable Arg extractArgument(ItemStack stack) {
        Object[] c = CONTEXT.get();
        if (c == null || !(c[0] instanceof ItemOwner owner) || !(c[1] instanceof ItemDisplayContext ctx)) return null;
        LivingEntity e = owner.asLivingEntity();
        if (e == null || e.level() == null) return null;
        Minecraft mc = Minecraft.getInstance();
        float now = e.level().getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean world = ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        return new Arg(RifleClient.pose(e, now), e.getId(), world);
    }

    @Override
    public void submit(@Nullable Arg arg, PoseStack ps, SubmitNodeCollector c, int light, int overlay, boolean foil, int outline) {
        BbModel model = BbModels.get(RifleClient.RIG);
        if (model == null) return;
        ps.pushPose();
        if (arg != null && arg.world()) {
            ps.translate(0.5f, 0.5f, 0.5f);
            ps.mulPose(new org.joml.Matrix4f().rotationXYZ((float) Math.toRadians(THIRD_PERSON[0]), (float) Math.toRadians(THIRD_PERSON[1]),
                    (float) Math.toRadians(THIRD_PERSON[2])));
            ps.translate(-0.5f, -0.5f, -0.5f);
        }
        place(ps);
        BbModel.Posing pose = RikaRenderer.posing(arg == null ? null : arg.pose());
        c.submitCustomGeometry(ps, RenderTypes.entityCutout(JJK.id("textures/entity/cursed_rifle.png")), (p, buf) -> model.render(p, buf, pose, light, 0xFFFFFFFF));
        if (arg != null && arg.world()) {
            // Where the barrel's beam_origin is in the world this frame: the holder's beam starts there.
            Vector3f at = model.locate("beam_origin", pose, new Vector3f());
            Vector3f v = new Matrix4f(ps.last().pose()).transformPosition(at);
            Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
            RifleClient.muzzleDrawn(arg.holder(), cam.add(v.x, v.y, v.z), Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime());
        }
        ps.popPose();
    }

    /**
     * The drawn rifle where its holder's stance placed it ({@link RifleStance}): {@code ps} is the holder's model space,
     * in blocks. Records where its beam_origin is this frame, for shots and the beam to start there.
     */
    public static void submitPlaced(PoseStack ps, SubmitNodeCollector c, int light, RifleStance.View v) {
        BbModel model = BbModels.get(RifleClient.RIG);
        if (model == null) return;
        ps.pushPose();
        ps.scale(1 / 16f, 1 / 16f, 1 / 16f);
        ps.mulPose(v.rifle);
        BbModel.Posing pose = RikaRenderer.posing(v.model);
        c.submitCustomGeometry(ps, RenderTypes.entityCutout(JJK.id("textures/entity/cursed_rifle.png")), (p, buf) -> model.render(p, buf, pose, light, 0xFFFFFFFF));
        Vector3f at = model.locate("beam_origin", pose, new Vector3f());
        Vector3f w = new Matrix4f(ps.last().pose()).transformPosition(at);
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        RifleClient.muzzleDrawn(v.id, cam.add(w.x, w.y, w.z), mc.level == null ? 0 : mc.level.getGameTime());
        ps.popPose();
    }

    /** Model pixels into the item's space, the grip at its pivot. */
    static void place(PoseStack ps) {
        ps.translate(0.5f, 0.5f, 0.5f);
        ps.scale(SCALE / 16f, SCALE / 16f, SCALE / 16f);
        ps.translate(-GRIP.x, -GRIP.y, -GRIP.z);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        BbModel model = BbModels.get(RifleClient.RIG);
        PoseStack ps = new PoseStack();
        place(ps);
        Matrix4f m = ps.last().pose();
        Vector3f lo = model == null ? new Vector3f(-4, 4, -19) : model.min, hi = model == null ? new Vector3f(4, 20, 19) : model.max;
        for (int i = 0; i < 8; i++) {
            out.accept(m.transformPosition(new Vector3f((i & 1) == 0 ? lo.x : hi.x, (i & 2) == 0 ? lo.y : hi.y, (i & 4) == 0 ? lo.z : hi.z)));
        }
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Arg> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Arg> bake(SpecialModelRenderer.BakingContext context) {
            return new RifleItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
