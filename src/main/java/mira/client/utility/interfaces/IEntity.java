package mira.client.utility.interfaces;

import net.minecraft.util.math.BlockPos;
import mira.client.features.modules.render.Trails;

import java.util.List;

public interface IEntity {
    List<Trails.Trail> getTrails();

    BlockPos mira_Recode$getVelocityBP();
}
