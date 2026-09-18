package mira.client.injection;

import net.minecraft.block.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import mira.client.core.manager.client.ModuleManager;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class MixinAbstractBlockState {
    @Inject(method = "getLuminance", at = @At("HEAD"), cancellable = true)
    public void getLuminanceHook(CallbackInfoReturnable<Integer> cir) {
        if (ModuleManager.xray.isEnabled()) {
            cir.setReturnValue(15);
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    public void getRenderTypeHook(CallbackInfoReturnable<BlockRenderType> cir) {
        if (!ModuleManager.noExplosionLag.isEnabled() || !ModuleManager.noExplosionLag.fireBlock.getValue()) return;

        AbstractBlock.AbstractBlockState self = (AbstractBlock.AbstractBlockState) (Object) this;
        if (self.getBlock() == Blocks.FIRE || self.getBlock() == Blocks.SOUL_FIRE) {
            cir.setReturnValue(BlockRenderType.INVISIBLE);
        }
    }
    }
