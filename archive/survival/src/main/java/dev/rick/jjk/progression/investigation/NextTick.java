package dev.rick.jjk.progression.investigation;

import net.minecraft.server.MinecraftServer;

/** Runs something once the current tick is over (after a realm closes, so its messages land after the trip home). */
final class NextTick {
    private NextTick() {}

    static void later(MinecraftServer server, Runnable r) {
        server.execute(r);
    }
}
