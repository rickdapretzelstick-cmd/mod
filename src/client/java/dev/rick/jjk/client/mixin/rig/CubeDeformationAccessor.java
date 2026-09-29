package dev.rick.jjk.client.mixin.rig;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CubeDeformation.class)
public interface CubeDeformationAccessor {
    @Accessor("growX") float jjk$x();
    @Accessor("growY") float jjk$y();
    @Accessor("growZ") float jjk$z();
}
