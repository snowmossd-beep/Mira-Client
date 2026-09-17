package mira.client.features.hud.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.util.Colors;
import org.lwjgl.opengl.GL40C;
import mira.client.core.manager.client.ModuleManager;
import mira.client.gui.font.FontRenderers;
import mira.client.gui.hud.HudEditorGui;
import mira.client.features.hud.HudElement;
import mira.client.features.modules.render.HudEditor;
import mira.client.features.modules.combat.Aura;
import mira.client.features.modules.misc.NameProtect;
import mira.client.setting.Setting;
import mira.client.utility.Timer;
import mira.client.utility.math.MathUtility;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.Render3DEngine;
import mira.client.utility.render.animation.EaseOutBack;
import mira.client.utility.render.animation.EaseOutCirc;

import java.awt.*;
import java.util.List;

public class TargetHud extends HudElement {
    private final Setting<Integer> animX = new Setting<>("AnimationX", 0, -2000, 2000);
    private final Setting<Integer> animY = new Setting<>("AnimationY", 0, -2000, 2000);
    private final Setting<HPmodeEn> hpMode = new Setting<>("HP Mode", HPmodeEn.HP);
    private final Setting<Boolean> funTimeHP = new Setting<>("FunTimeHP", false);
    private final Setting<Boolean> mini = new Setting<>("Mini", true);
    private final Setting<Boolean> absorp = new Setting<>("Absorption", true);

    public EaseOutBack animation = new EaseOutBack();
    public static EaseOutCirc healthAnimation = new EaseOutCirc();

    private boolean direction = false;
    private LivingEntity target;

    private final Timer timer = new Timer();

    // Modern glass palette: translucent but readable.
    private static final Color GLASS = new Color(9, 12, 18, 150);
    private static final Color GLASS_DARK = new Color(5, 8, 13, 105);
    private static final Color LINE = new Color(255, 255, 255, 38);

    public TargetHud() {
        super("TargetHud", 150, 50);
    }

    @Override
    public void onUpdate() {
        animation.update(direction);
        healthAnimation.update();
    }

    public void onRender2D(DrawContext context) {
        super.onRender2D(context);

        getTarget();
        if (target == null) return;

        float health = Math.min(target.getMaxHealth(), getHealth());

        context.getMatrices().push();

        if (!HudEditor.hudStyle.is(HudEditor.HudStyle.Blurry)) {
            if (mini.getValue())
                sizeAnimation(context.getMatrices(), getPosX() + 45 + animX.getValue(), getPosY() + 15 + animY.getValue(), animation.getAnimationd());
            else
                sizeAnimation(context.getMatrices(), getPosX() + 75 + animX.getValue(), getPosY() + 25 + animY.getValue(), animation.getAnimationd());
        }

        if (animation.getAnimationd() > 0) {
            float animationFactor = (float) MathUtility.clamp(animation.getAnimationd(), 0, 1f);
            if (mini.getValue())
                renderMiniNurik(context, health, animationFactor);
            else
                renderNurik(context, health, animationFactor);
        }
        context.getMatrices().pop();
    }

    private void getTarget() {
        if (Aura.target != null) {
            if (Aura.target instanceof LivingEntity) {
                target = (LivingEntity) Aura.target;
                direction = true;
            } else {
                target = null;
                direction = false;
            }
        } else if (mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof HudEditorGui) {
            target = mc.player;
            direction = true;
        } else {
            direction = false;
            if (animation.getAnimationd() < 0.02)
                target = null;
        }
    }

    private Color higherHpColor(float animationFactor) {
        float self = mc.player != null ? mc.player.getHealth() + mc.player.getAbsorptionAmount() : 0f;
        boolean higher = mc.player == null || target == mc.player || self >= getHealth();
        Color base = higher ? new Color(93, 255, 157) : new Color(255, 91, 91);
        return Render2DEngine.applyOpacity(base, animationFactor);
    }

    private void drawHealthDot(DrawContext context, float dotCenterX, float dotCenterY, float animationFactor) {
        float self = mc.player != null ? mc.player.getHealth() + mc.player.getAbsorptionAmount() : 0f;
        boolean higher = mc.player == null || target == mc.player || self >= getHealth();
        Color dot = higher ? new Color(93, 255, 157) : new Color(255, 91, 91); 
        Color glow = Render2DEngine.applyOpacity(dot, animationFactor * 0.7f);
        Color fill = Render2DEngine.applyOpacity(dot, animationFactor);

        float r = 3.2f; 
        
        Render2DEngine.drawBlurredShadow(context.getMatrices(), dotCenterX - r - 1, dotCenterY - r - 1, (r + 1) * 2, (r + 1) * 2, 6, glow);
        
        Render2DEngine.drawRound(context.getMatrices(), dotCenterX - r, dotCenterY - r, r * 2, r * 2, r, fill);
    }

    private void renderNurik(DrawContext context, float health, float animationFactor) {
        float hurtPercent = (Render2DEngine.interpolateFloat(MathUtility.clamp(target.hurtTime == 0 ? 0 : target.hurtTime + 1, 0, 10), target.hurtTime, Render3DEngine.getTickDelta())) / 8f;
        healthAnimation.setValue(health);
        health = (float) healthAnimation.getAnimationD();

        Render2DEngine.drawBlurredShadow(context.getMatrices(), getPosX() + 2, getPosY() + 2, 133, 43.5f, 10, new Color(0, 0, 0, (int)(75 * animationFactor)));
        Render2DEngine.drawRoundedBlur(context.getMatrices(), getPosX(), getPosY(), 137f, 47.5f, 8f, new Color(GLASS.getRed(), GLASS.getGreen(), GLASS.getBlue(), (int)(GLASS.getAlpha() * animationFactor)), HudEditor.blurStrength.getValue(), HudEditor.blurOpacity.getValue() * animationFactor);
        Render2DEngine.drawRoundBorder(context.getMatrices(), getPosX(), getPosY(), 137f, 47.5f, 8f, 1f, new Color(LINE.getRed(), LINE.getGreen(), LINE.getBlue(), (int)(LINE.getAlpha() * animationFactor)));

        setBounds(getPosX(), getPosY(), 137, 47.5f);

        if (target instanceof PlayerEntity) {
            RenderSystem.setShaderTexture(0, ((AbstractClientPlayerEntity) target).getSkinTextures().texture());
        } else {
            RenderSystem.setShaderTexture(0, mc.getEntityRenderDispatcher().getRenderer(target).getTexture(target));
        }

        Render2DEngine.drawRound(context.getMatrices(), getPosX() + 4, getPosY() + 4, 38, 38, 8f, new Color(GLASS_DARK.getRed(), GLASS_DARK.getGreen(), GLASS_DARK.getBlue(), (int)(GLASS_DARK.getAlpha() * animationFactor)));

        context.getMatrices().push();
        context.getMatrices().translate(getPosX() + 4 + 19, getPosY() + 4 + 19, 0);
        context.getMatrices().scale(1 - hurtPercent / 15f, 1 - hurtPercent / 15f, 1f);
        context.getMatrices().translate(-(getPosX() + 4 + 19), -(getPosY() + 4 + 19), 0);
        RenderSystem.enableBlend();
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clear(GL40C.GL_COLOR_BUFFER_BIT, false);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Render2DEngine.renderRoundedQuadInternal(context.getMatrices().peek().getPositionMatrix(), animationFactor, animationFactor, animationFactor, animationFactor, getPosX() + 6, getPosY() + 6, getPosX() + 6 + 34, getPosY() + 6 + 34, 6, 10);
        RenderSystem.blendFunc(GL40C.GL_DST_ALPHA, GL40C.GL_ONE_MINUS_DST_ALPHA);
        RenderSystem.setShaderColor(animationFactor, animationFactor - hurtPercent / 2, animationFactor - hurtPercent / 2, (float) MathUtility.clamp(animation.getAnimationd(), 0, 1f));
        Render2DEngine.renderTexture(context.getMatrices(), getPosX() + 6, getPosY() + 6, 34, 34, 8, 8, 8, 8, 64, 64);
        Render2DEngine.renderTexture(context.getMatrices(), getPosX() + 6, getPosY() + 6, 34, 34, 40, 8, 8, 8, 64, 64);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        context.getMatrices().pop();

        Render2DEngine.drawRound(context.getMatrices(), getPosX() + 48, getPosY() + 32, 85, 11f, 4f, new Color(255, 255, 255, (int)(24 * animationFactor)));
        Render2DEngine.drawRound(context.getMatrices(), getPosX() + 48, getPosY() + 32, MathUtility.clamp((85 * (health / target.getMaxHealth())), 8, 85), 11f, 4f,
                new Color(higherHpColor(animationFactor).getRGB(), true));

        FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), hpMode.getValue() == HPmodeEn.HP ? String.valueOf(Math.round(10.0 * getHealth()) / 10.0) : (((Math.round(10.0 * getHealth()) / 10.0) / 20f) * 100 + "%"), getPosX() + 92f, getPosY() + 35f,
                Render2DEngine.applyOpacity(Colors.WHITE, animationFactor));

        FontRenderers.sf_bold.drawString(context.getMatrices(), ModuleManager.nameProtect.isEnabled() && target == mc.player ? NameProtect.getCustomName() : target.getName().getString(), getPosX() + 48, getPosY() + 7,
                Render2DEngine.applyOpacity(Colors.WHITE, animationFactor));

        if (target instanceof PlayerEntity) {
            RenderSystem.setShaderColor(1f, 1f, 1f, (float) MathUtility.clamp(animation.getAnimationd(), 0, 1f));

            List<ItemStack> armor = ((PlayerEntity) target).getInventory().armor;
            ItemStack[] items = new ItemStack[]{target.getMainHandStack(), armor.get(3), armor.get(2), armor.get(1), armor.get(0), target.getOffHandStack()};

            float xItemOffset = getPosX() + 48;
            for (ItemStack itemStack : items) {
                context.getMatrices().push();
                context.getMatrices().translate(xItemOffset, getPosY() + 15, 0);
                context.getMatrices().scale(0.75f, 0.75f, 0.75f);
                context.drawItem(itemStack, 0, 0);
                context.drawItemInSlot(mc.textRenderer, itemStack, 0, 0);
                context.getMatrices().pop();
                xItemOffset += 12;
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        drawHealthDot(context, getPosX() + 137 - 7, getPosY() + 7, animationFactor);
    }

    private void renderMiniNurik(DrawContext context, float health, float animationFactor) {
        float hurtPercent = (Render2DEngine.interpolateFloat(MathUtility.clamp(target.hurtTime == 0 ? 0 : target.hurtTime + 1, 0, 10), target.hurtTime, Render3DEngine.getTickDelta())) / 8f;
        healthAnimation.setValue(health);
        health = (float) healthAnimation.getAnimationD();

        Render2DEngine.drawBlurredShadow(context.getMatrices(), getPosX() + 2, getPosY() + 2, 91, 31.5f, 7, new Color(0, 0, 0, (int)(75 * animationFactor)));
        Render2DEngine.drawRoundedBlur(context.getMatrices(), getPosX(), getPosY(), 95, 35.5f, 7f, new Color(GLASS.getRed(), GLASS.getGreen(), GLASS.getBlue(), (int)(GLASS.getAlpha() * animationFactor)), HudEditor.blurStrength.getValue(), HudEditor.blurOpacity.getValue() * animationFactor);
        Render2DEngine.drawRoundBorder(context.getMatrices(), getPosX(), getPosY(), 95, 35.5f, 7f, 1f, new Color(LINE.getRed(), LINE.getGreen(), LINE.getBlue(), (int)(LINE.getAlpha() * animationFactor)));

        setBounds(getPosX(), getPosY(), 95, 35.5f);

        if (target instanceof PlayerEntity) {
            RenderSystem.setShaderTexture(0, ((AbstractClientPlayerEntity) target).getSkinTextures().texture());
        } else {
            RenderSystem.setShaderTexture(0, mc.getEntityRenderDispatcher().getRenderer(target).getTexture(target));
        }

        Render2DEngine.drawRound(context.getMatrices(), getPosX() + 2.5f, getPosY() + 2.5f, 30, 30, 6f, new Color(GLASS_DARK.getRed(), GLASS_DARK.getGreen(), GLASS_DARK.getBlue(), (int)(GLASS_DARK.getAlpha() * animationFactor)));

        context.getMatrices().push();
        context.getMatrices().translate(getPosX() + 2.5 + 15, getPosY() + 2.5 + 15, 0);
        context.getMatrices().scale(1 - hurtPercent / 20f, 1 - hurtPercent / 20f, 1f);
        context.getMatrices().translate(-(getPosX() + 2.5 + 15), -(getPosY() + 2.5 + 15), 0);
        RenderSystem.enableBlend();
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clear(GL40C.GL_COLOR_BUFFER_BIT, false);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Render2DEngine.renderRoundedQuadInternal(context.getMatrices().peek().getPositionMatrix(), animationFactor, animationFactor, animationFactor, animationFactor, getPosX() + 2.5, getPosY() + 2.5, getPosX() + 2.5 + 30, getPosY() + 2.5 + 30, 5, 10);
        RenderSystem.blendFunc(GL40C.GL_DST_ALPHA, GL40C.GL_ONE_MINUS_DST_ALPHA);
        RenderSystem.setShaderColor(animationFactor, animationFactor - hurtPercent / 2, animationFactor - hurtPercent / 2, animationFactor);
        Render2DEngine.renderTexture(context.getMatrices(), getPosX() + 2.5, getPosY() + 2.5, 30, 30, 8, 8, 8, 8, 64, 64);
        Render2DEngine.renderTexture(context.getMatrices(), getPosX() + 2.5, getPosY() + 2.5, 30, 30, 40, 8, 8, 8, 64, 64);
        RenderSystem.defaultBlendFunc();
        context.getMatrices().pop();

        Render2DEngine.drawRound(context.getMatrices(), getPosX() + 38, getPosY() + 25, 52f, 7f, 2f, new Color(255, 255, 255, (int)(24 * animationFactor)));
        Render2DEngine.drawRound(context.getMatrices(), getPosX() + 38, getPosY() + 25, MathUtility.clamp((52f * (health / target.getMaxHealth())), 8, 52), 7f, 2f,
                new Color(higherHpColor(animationFactor).getRGB(), true));

        FontRenderers.sf_bold_mini.drawCenteredString(context.getMatrices(), hpMode.getValue() == HPmodeEn.HP ? String.valueOf(Math.round(10.0 * getHealth()) / 10.0) : (((Math.round(10.0 * getHealth()) / 10.0) / 20f) * 100 + "%"), getPosX() + 65, getPosY() + 27f, Render2DEngine.applyOpacity(Colors.WHITE, animationFactor));

        FontRenderers.sf_bold_mini.drawString(context.getMatrices(), ModuleManager.nameProtect.isEnabled() && target == mc.player ? NameProtect.getCustomName() : target.getName().getString(), getPosX() + 38, getPosY() + 5, Render2DEngine.applyOpacity(Colors.WHITE, animationFactor));

        if (target instanceof PlayerEntity) {
            
            RenderSystem.setShaderColor(1f, 1f, 1f, (float) MathUtility.clamp(animation.getAnimationd(), 0, 1f));
            List<ItemStack> armor = ((PlayerEntity) target).getInventory().armor;
            ItemStack[] items = new ItemStack[]{target.getMainHandStack(), armor.get(3), armor.get(2), armor.get(1), armor.get(0), target.getOffHandStack()};

            float xItemOffset = getPosX() + 38;
            for (ItemStack itemStack : items) {
                context.getMatrices().push();
                context.getMatrices().translate(xItemOffset, getPosY() + 13, 0);
                context.getMatrices().scale(0.5f, 0.5f, 0.5f);
                context.drawItem(itemStack, 0, 0);
                context.drawItemInSlot(mc.textRenderer, itemStack, 0, 0);
                context.getMatrices().pop();
                xItemOffset += 9;
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        drawHealthDot(context, getPosX() + 95 - 6, getPosY() + 6, animationFactor);
    }

    public float getHealth() {
        
        if (target instanceof PlayerEntity ent && (mc.getNetworkHandler() != null && mc.getNetworkHandler().getServerInfo() != null && mc.getNetworkHandler().getServerInfo().address.contains("funtime") || funTimeHP.getValue())) {
            ScoreboardObjective scoreBoard = null;
            String resolvedHp = "";
            if ((ent.getScoreboard()).getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME) != null) {
                scoreBoard = (ent.getScoreboard()).getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
                if (scoreBoard != null) {
                    ReadableScoreboardScore readableScoreboardScore = ent.getScoreboard().getScore(ent, scoreBoard);
                    MutableText text2 = ReadableScoreboardScore.getFormattedScore(readableScoreboardScore, scoreBoard.getNumberFormatOr(StyledNumberFormat.EMPTY));
                    resolvedHp = text2.getString();
                }
            }
            float numValue = 0;
            try {
                numValue = Float.parseFloat(resolvedHp);
            } catch (NumberFormatException ignored) {
            }
            return (absorp.getValue()) ? numValue + target.getAbsorptionAmount() : numValue;
        } else return (absorp.getValue()) ? target.getHealth() + target.getAbsorptionAmount() : target.getHealth();
    }

    public static void sizeAnimation(MatrixStack matrixStack, double width, double height, double animation) {
        matrixStack.translate(width, height, 0);
        matrixStack.scale((float) animation, (float) animation, 1);
        matrixStack.translate(-width, -height, 0);
    }

    public enum HPmodeEn {
        HP, Percentage
    }
    }
                              
