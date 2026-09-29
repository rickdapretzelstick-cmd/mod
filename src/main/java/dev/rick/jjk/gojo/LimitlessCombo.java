package dev.rick.jjk.gojo;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** A Gojo cast that Limitless (the Special key) changes while it winds up: Reversal Red's variants, Red MAX's rebound. */
public interface LimitlessCombo {
    /** Whether Limitless pressed now changes this cast. */
    boolean acceptsLimitless();

    /** Limitless was pressed. Returns whether the Special goes on cooldown for it. */
    boolean limitless(@Nullable Entity targetHint);
}
