package dev.rick.jjk.progression.prison;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Prison Realm, awakened in cursed energy. It carries the id the world gave it ({@link PrisonRealmState}): only
 * the world's current realm works, so a stale or copied cube is inert. Used, it opens on the player in front of the
 * user (sneaking with nobody in front: on the user); see {@link PrisonRealm#use}. Destroyed (burnt up in a cactus, blown
 * up, lost in the void), it frees the world to forge another.
 */
public class PrisonRealmItem extends Item {
    static final String KEY = "jjk_prison_realm";

    public PrisonRealmItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static UUID realmId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String s = data.copyTag().getStringOr(KEY, "");
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ItemStack create(UUID id) {
        ItemStack stack = new ItemStack(dev.rick.jjk.progression.ProgressionItems.PRISON_REALM);
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, id.toString());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        return PrisonRealm.use(sp, hand) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        super.onDestroyed(itemEntity);
        if (itemEntity.level() instanceof ServerLevel level) PrisonRealm.itemDestroyed(level, itemEntity.getItem());
    }
}
