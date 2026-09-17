package mira.client.injection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import mira.client.MiraClient;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.injection.accesors.IClientPlayerEntity;
import mira.client.features.modules.Module;
import mira.client.features.modules.render.ClientSettings;
import mira.client.utility.math.MathUtility;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.Render3DEngine;

import java.util.List;

import static mira.client.features.modules.Module.mc;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {
    private LivingEntity lastEntity;

    private float originalHeadYaw, originalPrevHeadYaw, originalPrevHeadPitch, originalHeadPitch;

    @Shadow
    protected M model;

    @Shadow
    @Final
    protected List<FeatureRenderer<T, M>> features;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void onRenderPre(T livingEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        if (Module.fullNullCheck()) return;
        if (mc.player != null && livingEntity == mc.player && mc.player.getControllingVehicle() == null && ClientSettings.renderRotations.getValue() && !MiraClient.isFuturePresent()) {
            originalHeadYaw = livingEntity.headYaw;
            originalPrevHeadYaw = livingEntity.prevHeadYaw;
            originalPrevHeadPitch = livingEntity.prevPitch;
            originalHeadPitch = livingEntity.getPitch();

            livingEntity.setPitch(((IClientPlayerEntity) MinecraftClient.getInstance().player).getLastPitch());
            livingEntity.prevPitch = Managers.PLAYER.lastPitch;
            livingEntity.headYaw = ((IClientPlayerEntity) MinecraftClient.getInstance().player).getLastYaw();
            livingEntity.bodyYaw = Render2DEngine.interpolateFloat(Managers.PLAYER.prevBodyYaw, Managers.PLAYER.bodyYaw, Render3DEngine.getTickDelta());
            livingEntity.prevHeadYaw = Managers.PLAYER.lastYaw;
            livingEntity.prevBodyYaw = Render2DEngine.interpolateFloat(Managers.PLAYER.prevBodyYaw, Managers.PLAYER.bodyYaw, Render3DEngine.getTickDelta());
        }

        if (livingEntity != mc.player && ModuleManager.freeCam.isEnabled() && ModuleManager.freeCam.track.getValue() && ModuleManager.freeCam.trackEntity != null && ModuleManager.freeCam.trackEntity == livingEntity) {
            ci.cancel();
            return;
        }

        lastEntity = livingEntity;
    }

    @Unique
    public void postRender(T livingEntity) {
        if (Module.fullNullCheck()) return;
        if (mc.player != null && livingEntity == mc.player && mc.player.getControllingVehicle() == null && ClientSettings.renderRotations.getValue() && !MiraClient.isFuturePresent()) {
            livingEntity.prevPitch = originalPrevHeadPitch;
            livingEntity.setPitch(originalHeadPitch);
            livingEntity.headYaw = originalHeadYaw;
            livingEntity.prevHeadYaw = originalPrevHeadYaw;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderPost(T livingEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        if (Module.fullNullCheck()) return;
        postRender(livingEntity);
    }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
    private void renderHook(Args args) {
        if (Module.fullNullCheck()) return;

        float alpha = -1f;

        if (ModuleManager.noRender.isEnabled() && ModuleManager.noRender.antiPlayerCollision.getValue() && lastEntity != mc.player && lastEntity instanceof PlayerEntity pl && !pl.isInvisible())
            alpha = MathUtility.clamp((float) (mc.player.squaredDistanceTo(lastEntity.getPos()) / 3f) + 0.2f, 0f, 1f);

        if (lastEntity != mc.player && lastEntity instanceof PlayerEntity pl && pl.isInvisible())
            alpha = 0.3f;

        if (alpha != -1)
            args.set(4, Render2DEngine.applyOpacity(0x26FFFFFF, alpha));
    }
}
