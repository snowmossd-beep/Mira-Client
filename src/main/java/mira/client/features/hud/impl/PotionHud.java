package mira.client.features.hud.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Formatting;
import mira.client.features.hud.HudElement;
import mira.client.features.modules.render.HudEditor;
import mira.client.gui.font.FontRenderers;
import mira.client.setting.Setting;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.animation.AnimationUtility;

import java.awt.*;

public class PotionHud extends HudElement {
    public PotionHud() {
        super("Potions", 100, 100);
    }

    private float vAnimation, hAnimation;

    private final Setting<Boolean> colored = new Setting<>("Colored", false);

    public static String getDuration(StatusEffectInstance pe) {
        if (pe.isInfinite()) {
            return "*:*";
        } else {
            int var1 = pe.getDuration();
            int mins = var1 / 1200;
            String sec = String.format("%02d", (var1 % 1200) / 20);
            return mins + ":" + sec;
        }
    }

    public void onRender2D(DrawContext context) {
        super.onRender2D(context);

        int y_offset1 = 0;
        float max_width = 50;

        float pointerX = 0;
        for (StatusEffectInstance potionEffect : mc.player.getStatusEffects()) {
            StatusEffect potion = potionEffect.getEffectType().value();

            if (y_offset1 == 0)
                y_offset1 += 4;

            y_offset1 += 9;

            float nameWidth = FontRenderers.sf_bold_mini.getStringWidth(potion.getName().getString() + " " + (potionEffect.getAmplifier() + 1));
            float timeWidth = FontRenderers.sf_bold_mini.getStringWidth(getDuration(potionEffect));
            float width = (nameWidth + timeWidth) * 1.4f;

            if (width > max_width)
                max_width = width;

            if (timeWidth > pointerX)
                pointerX = timeWidth;
        }

        vAnimation = AnimationUtility.fast(vAnimation, 14 + y_offset1, 15);
        hAnimation = AnimationUtility.fast(hAnimation, max_width, 15);

        Render2DEngine.drawHudBase(context.getMatrices(), getPosX(), getPosY(), hAnimation, vAnimation, HudEditor.hudRound.getValue());

        if (HudEditor.hudStyle.is(HudEditor.HudStyle.Glowing)) {
            FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), "Potions", getPosX() + hAnimation / 2, getPosY() + 4, HudEditor.getColor(0));
        } else {
            FontRenderers.sf_bold.drawGradientCenteredString(context.getMatrices(), "Potions", getPosX() + hAnimation / 2, getPosY() + 4, 10);
        }

        if (y_offset1 > 0) {
            if (HudEditor.hudStyle.is(HudEditor.HudStyle.Blurry)) {
                Render2DEngine.drawRectDumbWay(context.getMatrices(), getPosX() + 4, getPosY() + 13, getPosX() + getWidth() - 4, getPosY() + 13.5f, new Color(0x54FFFFFF, true));
            } else {
                Render2DEngine.horizontalGradient(context.getMatrices(), getPosX() + 2, getPosY() + 13.7f, getPosX() + 2 + hAnimation / 2f - 2, getPosY() + 14, Render2DEngine.injectAlpha(HudEditor.getColor(0), 0), HudEditor.getColor(0));
                Render2DEngine.horizontalGradient(context.getMatrices(), getPosX() + 2 + hAnimation / 2f - 2, getPosY() + 13.7f, getPosX() + 2 + hAnimation - 4, getPosY() + 14, HudEditor.getColor(0), Render2DEngine.injectAlpha(HudEditor.getColor(0), 0));
            }
        }

        Render2DEngine.addWindow(context.getMatrices(), getPosX(), getPosY(), getPosX() + hAnimation, getPosY() + vAnimation, 1f);
        int y_offset = 0;
        for (StatusEffectInstance potionEffect : mc.player.getStatusEffects()) {
            StatusEffect potion = potionEffect.getEffectType().value();

            float px = getPosX() + (max_width - pointerX - 10);

            context.getMatrices().push();
            context.getMatrices().translate(getPosX() + 2, getPosY() + 16 + y_offset, 0);
            context.drawSprite(0, 0, 0, 8, 8, mc.getStatusEffectSpriteManager().getSprite(potionEffect.getEffectType()));
            context.getMatrices().pop();

            FontRenderers.sf_bold_mini.drawString(context.getMatrices(), potion.getName().getString() + " " + Formatting.RED + (potionEffect.getAmplifier() + 1), getPosX() + 12, getPosY() + 19 + y_offset, HudEditor.getColorInt(0));
            FontRenderers.sf_bold_mini.drawCenteredString(context.getMatrices(), getDuration(potionEffect), px + (getPosX() + max_width - px) / 2f, getPosY() + 19 + y_offset, HudEditor.getColorInt(0));
            Render2DEngine.drawRect(context.getMatrices(), px, getPosY() + 17 + y_offset, 0.5f, 8, new Color(0x44FFFFFF, true));
            y_offset += 9;
        }
        Render2DEngine.popWindow();
        setBounds(getPosX(), getPosY(), hAnimation, vAnimation);
    }
}
