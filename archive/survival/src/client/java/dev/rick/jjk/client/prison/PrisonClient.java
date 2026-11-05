package dev.rick.jjk.client.prison;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.input.InputHandler;
import dev.rick.jjk.core.net.PrisonPayload;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The sealed player's side of the Prison Realm: what the server says about their seal, and the optional outside view.
 * The view (its own key, V by default; vanilla 26.3 has O for its friends list) is a third-person camera orbiting the grounded realm, turned with the mouse as
 * usual; it only watches. While it is on, movement, jumping, sneaking, attacking and using are held released, so nothing
 * is done from it (the server holds the player in the cell anyway). It switches off, and the player's own camera comes
 * back, when they press the key again, when they are released, on death or on disconnect.
 */
public final class PrisonClient {
    private static boolean sealed;
    @Nullable private static BlockPos realm;
    private static int stage, broken, rescue;
    private static boolean viewing;
    @Nullable private static CameraType restore;
    private static KeyMapping key;
    /** Camera distance from the realm, eased in against walls. */
    private static double distance = 6;

    private PrisonClient() {}

    public static void init() {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.prison_view", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, InputHandler.category()));
    }

    public static void apply(PrisonPayload p) {
        sealed = p.sealed();
        realm = p.sealed() ? BlockPos.of(p.realmPos()) : null;
        stage = p.stage();
        broken = p.broken();
        rescue = p.rescue();
        if (!sealed) stopViewing();
    }

    public static boolean sealed() {
        return sealed;
    }

    /** Whether the outside view has the camera (tests, the HUD). */
    public static boolean viewing() {
        return viewing;
    }

    /** Start of the client tick, before Minecraft reads its keys: watching only. */
    public static void beforeInput(Minecraft mc) {
        if (!viewing) return;
        var o = mc.options;
        for (KeyMapping k : new KeyMapping[] {o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint, o.keyAttack, o.keyUse, o.keyPickItem}) {
            while (k.consumeClick()) {
                // swallowed
            }
            k.setDown(false);
        }
    }

    public static void tick(Minecraft mc) {
        if (key == null) return;
        while (key.consumeClick()) {
            if (viewing) stopViewing();
            else if (sealed && mc.player != null && mc.player.isAlive()) startViewing(mc);
        }
        if (viewing && (!sealed || mc.player == null || !mc.player.isAlive() || realm == null)) stopViewing();
        if (viewing && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void startViewing(Minecraft mc) {
        viewing = true;
        restore = mc.options.getCameraType();
        distance = 6;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    public static void stopViewing() {
        if (!viewing) return;
        viewing = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && restore != null) mc.options.setCameraType(restore);
        restore = null;
    }

    public static void reset() {
        stopViewing();
        sealed = false;
        realm = null;
    }

    /** {x, y, z, yaw, pitch} for the outside view, or null when it is off. */
    @Nullable
    public static double[] apply(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (!viewing || realm == null || mc.level == null) return null;
        Vec3 centre = Vec3.atBottomCenterOf(realm).add(0, 0.9, 0);
        float y = yaw * Mth.DEG_TO_RAD, p = Mth.clamp(pitch, 5f, 80f) * Mth.DEG_TO_RAD;
        Vec3 look = new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
        Vec3 want = centre.subtract(look.scale(6));
        BlockHitResult hit = mc.level.clip(new ClipContext(centre, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        double d = hit.getType() == HitResult.Type.MISS ? 6 : Math.max(1.2, hit.getLocation().distanceTo(centre) - 0.3);
        distance += (d - distance) * (d < distance ? 0.6 : 0.2);
        Vec3 pos = centre.subtract(look.scale(distance));
        return new double[] {pos.x, pos.y, pos.z, yaw, Mth.clamp(pitch, 5f, 80f)};
    }

    public static void renderHud(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (!viewing || mc.player == null) return;
        String line = "OUTSIDE VIEW  watching only  [" + key.getTranslatedKeyMessage().getString() + "] back inside";
        int w = mc.font.width(line);
        int x = g.guiWidth() / 2 - w / 2, y = 12;
        g.fill(x - 6, y - 4, x + w + 6, y + 12, 0xA0100010);
        g.text(mc.font, line, x, y, 0xFFE070E0, false);
    }
}
