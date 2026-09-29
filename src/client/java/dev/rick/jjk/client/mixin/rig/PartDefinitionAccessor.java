package dev.rick.jjk.client.mixin.rig;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Map;

@Mixin(PartDefinition.class)
public interface PartDefinitionAccessor {
    @Accessor("cubes")
    List<CubeDefinition> jjk$cubes();

    @Accessor("partPose")
    PartPose jjk$pose();

    @Accessor("children")
    Map<String, PartDefinition> jjk$children();

    @Invoker("<init>")
    static PartDefinition jjk$create(List<CubeDefinition> cubes, PartPose pose) {
        throw new AssertionError();
    }
}
