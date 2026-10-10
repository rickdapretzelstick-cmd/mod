package dev.rick.jjk.client.rifle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.model.BbModel;
import dev.rick.jjk.client.model.BbModels;
import dev.rick.jjk.client.render.RikaRenderer;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The drawn Cursed Rifle in first person: the same action the third-person body performs, seen from the eye. Both arms
 * come in from below the view and close on the rifle (the trigger hand on the pistol grip, the support hand under the
 * fore-end), placed straight from just off screen so neither hand ever floats. The rifle rides at a relaxed ready low on
 * the right; aiming brings it up until the scope's eyepiece is at the eye (then the scope view takes over); the array
 * braces it at the shoulder, shudders through the charge and is driven back while the beam fires; sprinting carries it
 * at high port; shots kick it back and up. The rifle's own clips (arms, iris) play on it, as everyone else sees them.
 */
public final class RifleFirstPerson {
    private RifleFirstPerson() {}

    /** Fist centre from the arm's pivot, along the arm (pixels). */
    private static final float REACH = 9f;

    public static void submit(PoseStack ps, SubmitNodeCollector c, AvatarRenderState s, RifleStance.View v, int light) {
        BbModel model = BbModels.get(RifleClient.RIG);
        if (model == null || s.isInvisible) return;
        boolean scopedIn = v.phase == RifleServer.Phase.AIM && v.ads > 0.95f && v.phaseAge >= 4;
        if (scopedIn) return;
        float side = v.leftHanded ? -1 : 1;
        boolean aiming = v.phase == RifleServer.Phase.AIM || v.snap >= 0 && v.snap < 11 || v.volley >= 0;
        float aim = aiming ? v.ads : 0;
        float brace = RifleStance.array(v.phase) || v.phase == RifleServer.Phase.COOLDOWN ? v.brace : 0;
        float sprint = v.sprintW * (1 - aim);
        float rest = Math.max(0, 1 - aim - brace - sprint);

        // Where the butt sits (view space: x right, y up, -z ahead), and how the rifle turns from straight ahead.
        Vector3f butt = new Vector3f(0.38f * side, -0.33f, -0.34f).mul(rest);
        float down = 4 * rest, in = 13 * rest, roll = 0;
        butt.add(new Vector3f(0.19f * side, -0.30f, -0.36f).mul(brace));
        down += 2 * brace;
        in += 3 * brace;
        butt.add(new Vector3f(0.26f * side, -0.46f, -0.42f).mul(sprint));
        down += -40 * sprint;
        in += 50 * sprint;
        roll += -30 * sprint;
        // Aiming: the eyepiece comes to the eye (the scope view takes over once it's there).
        Vector3f eyepiece = new Vector3f(RifleStance.EYEPIECE).sub(RifleStance.BUTT).mul(RifleStance.SCALE / 16f);
        butt.add(new Vector3f(0, -0.085f, -0.40f).sub(eyepiece).mul(aim));

        float kick = RifleStance.recoil(v.shotAge) * (1 - 0.4f * brace);
        down -= 5 * kick;
        butt.z += 0.07f * kick;
        if (v.charge > 0) {
            float a = v.phase == RifleServer.Phase.FIRE ? 1.1f : (0.15f + 0.8f * v.charge) * (v.phase == RifleServer.Phase.READY ? 0.5f : 1f);
            float t = v.now * 2.3f;
            down += (float) Math.sin(t * 5.3f) * a;
            in += (float) Math.sin(t * 4.7f + 1.3f) * a * 0.7f;
            roll += (float) Math.sin(t * 6.1f + 0.4f) * a;
        }
        if (v.phase == RifleServer.Phase.FIRE) butt.z += 0.05f * Mth.clamp(v.phaseAge / 4f, 0, 1);
        if (v.flare >= 0) {
            float k = v.flare < 3 ? v.flare / 3 : Math.max(0, 1 - (v.flare - 6) / 5f);
            roll -= 70 * k * side;
        }
        if (v.bash >= 0) {
            float t = v.bash;
            float wind = t < 2 ? t / 2 : Math.max(0, 1 - (t - 2) / 1.5f);
            float strike = t < 2 ? 0 : t < 4 ? (t - 2) / 2 : Math.max(0, 1 - (t - 4) / 4f);
            // Wound back, then the stock driven out ahead, the muzzle tipping up past the view.
            down = Mth.lerp(wind, down, -20);
            down = Mth.lerp(strike, down, -35);
            in = Mth.lerp(strike, in, 40);
            butt.add(-0.10f * strike * side, 0.04f * strike, -0.28f * strike + 0.08f * wind);
        }
        // A little of the walk in the hands.
        float bob = (float) Math.sin(v.walkPos * 0.6662f) * v.walkSpeed * (1 - brace);
        butt.add(0.008f * bob, Math.abs(bob) * 0.01f, 0);

        Matrix3f rot = new Matrix3f().rotationYXZ(in * side * Mth.DEG_TO_RAD, -down * Mth.DEG_TO_RAD, roll * side * Mth.DEG_TO_RAD);
        Matrix4f rifle = new Matrix4f().translate(butt).mul(new Matrix4f().set(rot)).scale(RifleStance.SCALE / 16f)
                .translate(-RifleStance.BUTT.x, -RifleStance.BUTT.y, -RifleStance.BUTT.z);

        ps.pushPose();
        ps.mulPose(rifle);
        BbModel.Posing pose = RikaRenderer.posing(v.model);
        c.submitCustomGeometry(ps, RenderTypes.entityCutout(JJK.id("textures/entity/cursed_rifle.png")), (p, buf) -> model.render(p, buf, pose, light, 0xFFFFFFFF));
        ps.popPose();

        // The arms, from just below the view onto the grip and the fore-end.
        Vector3f grip = rifle.transformPosition(new Vector3f(RifleStance.GRIP));
        Vector3f fore = rifle.transformPosition(new Vector3f(RifleStance.FORE));
        Minecraft mc = Minecraft.getInstance();
        AvatarRenderer<?> renderer = (AvatarRenderer<?>) mc.getEntityRenderDispatcher().getRenderer(s);
        Identifier skin = s.skin.body().texturePath();
        boolean rightMain = side > 0;
        arm(ps, c, light, renderer, skin, s, rightMain, grip, new Vector3f(0.55f * side, -1.25f, 0.15f));
        arm(ps, c, light, renderer, skin, s, !rightMain, fore, new Vector3f(-0.30f * side, -1.3f, -0.15f));
    }

    /** One arm laid straight from {@code from} (off screen) to {@code target}, the fist on it. */
    private static void arm(PoseStack ps, SubmitNodeCollector c, int light, AvatarRenderer<?> renderer, Identifier skin, AvatarRenderState s, boolean right,
                            Vector3f target, Vector3f from) {
        Vector3f a = new Vector3f(target).sub(from).normalize();
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f b = new Vector3f(up).sub(new Vector3f(a).mul(up.dot(a)));
        if (b.lengthSquared() < 1e-6f) b.set(0, 0, 1);
        b.normalize();
        Vector3f nb = new Vector3f(b).negate();
        Vector3f cx = new Vector3f(a).cross(nb);
        Matrix3f basis = new Matrix3f().setColumn(0, cx).setColumn(1, a).setColumn(2, nb);
        Vector3f shoulder = new Vector3f(target).sub(new Vector3f(a).mul(REACH / 16f));
        float pivotX = right ? -5 : 5;
        ps.pushPose();
        ps.translate(shoulder.x, shoulder.y, shoulder.z);
        ps.mulPose(new Matrix4f().set(basis));
        // (The hand renderer tilts each arm by 0.1 rad about its pivot: undone here.)
        ps.mulPose(new Matrix4f().rotationZ(right ? -0.1f : 0.1f));
        ps.translate(-pivotX / 16f, -2 / 16f, 0);
        if (right) renderer.renderRightHand(ps, c, light, skin, s.showRightSleeve);
        else renderer.renderLeftHand(ps, c, light, skin, s.showLeftSleeve);
        ps.popPose();
    }
}
