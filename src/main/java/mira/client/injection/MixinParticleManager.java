package mira.client.injection;

import net.minecraft.client.particle.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import mira.client.core.manager.client.ModuleManager;
import mira.client.features.modules.render.NoRender;
import mira.client.features.modules.render.NoExplosionLag;

@Mixin(ParticleManager.class)
public class MixinParticleManager {
    @Inject(at = @At("HEAD"), method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", cancellable = true)
    public void addParticleHook(Particle p, CallbackInfo e) {
        NoRender nR = ModuleManager.noRender;
        NoExplosionLag nEL = ModuleManager.noExplosionLag;

        if (nEL.isEnabled() && nEL.explosions.getValue() && p instanceof ExplosionLargeParticle)
            e.cancel();

        if(!nR.isEnabled())
            return;
        
        if (nR.elderGuardian.getValue() && p instanceof ElderGuardianAppearanceParticle)
            e.cancel();

        if (nR.explosions.getValue() && p instanceof ExplosionLargeParticle)
            e.cancel();

        if (nR.campFire.getValue() && p instanceof CampfireSmokeParticle)
            e.cancel();

        if (nR.breakParticles.getValue() && p instanceof BlockDustParticle)
            e.cancel();

        if (nR.fireworks.getValue() && (p instanceof FireworksSparkParticle.FireworkParticle || p instanceof FireworksSparkParticle.Flash))
            e.cancel();
    }
    }
