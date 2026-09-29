package dev.rick.jjk.client.mixin.rig;

import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(CubeDefinition.class)
public interface CubeDefinitionAccessor {
    @Accessor("comment") String jjk$comment();
    @Accessor("origin") Vector3fc jjk$origin();
    @Accessor("dimensions") Vector3fc jjk$dimensions();
    @Accessor("grow") CubeDeformation jjk$grow();
    @Accessor("mirror") boolean jjk$mirror();
    @Accessor("texCoord") UVPair jjk$texCoord();
    @Accessor("texScale") UVPair jjk$texScale();
    @Accessor("visibleFaces") Set<Direction> jjk$faces();
}
