package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.core.net.CompassPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Cursed Compass's item. Its needle is drawn by the client from what {@link CursedCompass} sends; used, it tells its
 * holder in a few words what it feels (never what will set the incident off).
 */
public final class CursedCompassItem extends Item {
    public CursedCompassItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer p) {
            CompassPayload r = CursedCompass.reading(p);
            String line = switch (r.state()) {
                case CompassPayload.DORMANT -> "The needle lies still. You are investigating nothing.";
                case CompassPayload.FAINT -> "The needle wanders. Whatever you are following is far from here.";
                case CompassPayload.GONE -> "The needle has gone slack. What it followed is gone.";
                default -> {
                    double d = Math.sqrt(p.distanceToSqr(r.x(), r.y(), r.z()));
                    yield d < 8 ? "The needle shudders. It is here." : d < 40 ? "The needle pulls hard. Close." : "The needle has found something.";
                }
            };
            p.sendOverlayMessage(Component.literal(line).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        return InteractionResult.SUCCESS;
    }
}
