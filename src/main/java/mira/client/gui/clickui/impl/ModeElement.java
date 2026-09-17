package mira.client.gui.clickui.impl;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import mira.client.core.Managers;
import mira.client.gui.clickui.AbstractElement;
import mira.client.gui.font.FontRenderers;
import mira.client.setting.Setting;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.TextureStorage;
import java.awt.*;
import java.util.Objects;
import static mira.client.utility.render.animation.AnimationUtility.fast;
public class ModeElement extends AbstractElement {
    public Setting setting2;
    private boolean open;
    private double wheight;
    private String prevMode;
    private float animation, animation2;
    private static final Color NAME_COL    = new Color(200, 200, 212, 255);
    private static final Color VALUE_COL   = new Color(215, 205, 175, 255);
    private static final Color DROPDOWN_BG = new Color(32, 32, 42, 240);
    private static final Color ITEM_HOV    = new Color(255, 255, 255, 12);
    private static final Color ITEM_SEL    = new Color(215, 205, 175, 200);
    public ModeElement(Setting setting) {
        super(setting);
        this.setting2 = setting;
        prevMode = setting.currentEnumName();
    }
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        animation  = fast(animation,  open ? 0f : 1f, 15f);
        animation2 = fast(animation2, 1f, 10f);
        MatrixStack ms = ctx.getMatrices();
        float tx = x + width - 11;
        float ty = y + (float)(wheight / 2f);
        ms.push();
        ms.translate(tx, ty, 0);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-180f * animation));
        ms.translate(-tx, -ty, 0);
        ms.translate((x + width - 14), y + 4.5f, 0);
        ctx.drawTexture(TextureStorage.guiArrow, 0, 0, 0, 0, 6, 6, 6, 6);
        ms.translate(-(x + width - 14), -(y + 4.5f), 0);
        ms.pop();
        if (setting.group != null)
            Render2DEngine.drawRect(ms, x + 4, y, 1.5f, (float)wheight, new Color(80, 130, 220, 200));
        FontRenderers.sf_medium_mini.drawString(ms, setting2.getName(),
                (setting.group != null ? 2f : 0f) + x + 6,
                (float)(y + wheight / 2f - 3f), NAME_COL.getRGB());
        if (animation2 < 0.99 && !Objects.equals(setting2.currentEnumName(), prevMode)) {
            FontRenderers.sf_medium_mini.drawString(ms, prevMode,
                    x + width - 18 - FontRenderers.sf_medium_mini.getStringWidth(prevMode),
                    (float)(y + wheight / 2f - 3f) - animation2 * 5,
                    Render2DEngine.applyOpacity(VALUE_COL, animation2));
            FontRenderers.sf_medium_mini.drawString(ms, setting2.currentEnumName(),
                    x + width - 18 - FontRenderers.sf_medium_mini.getStringWidth(setting2.currentEnumName()),
                    (float)(y + wheight / 2f - 3f) - animation2 * 5 + 5,
                    Render2DEngine.applyOpacity(VALUE_COL, 1f - animation2));
        } else {
            FontRenderers.sf_medium_mini.drawString(ms, setting2.currentEnumName(),
                    x + width - 18 - FontRenderers.sf_medium_mini.getStringWidth(setting.currentEnumName()),
                    (float)(y + wheight / 2f - 3f), VALUE_COL.getRGB());
        }
        if (open) {
            Render2DEngine.drawRound(ms, x + 2, (float)(y + wheight + 1), width - 4, setting2.getModes().length * 12f, 3f, DROPDOWN_BG);
            double offY = 0;
            for (int i = 0; i < setting2.getModes().length; i++) {
                String mode = setting2.getModes()[i];
                boolean sel = setting2.currentEnumName().equalsIgnoreCase(mode);
                boolean hov = Render2DEngine.isHovered(mx, my, x, y + wheight + offY, width, 12);
                if (hov) Render2DEngine.drawRect(ms, x + 2, (float)(y + wheight + offY), width - 4, 12, ITEM_HOV);
                FontRenderers.sf_medium_mini.drawString(ms, mode,
                        x + width / 2f - FontRenderers.sf_medium_mini.getStringWidth(mode) / 2f,
                        (float)(y + wheight + 2 + offY),
                        sel ? ITEM_SEL.getRGB() : NAME_COL.getRGB());
                offY += 12;
            }
        }
    }
    @Override
    public void mouseClicked(int mx, int my, int button) {
        if (Render2DEngine.isHovered(mx, my, x, y, width, (float)wheight)) {
            if (button == 0) {
                prevMode = setting2.currentEnumName();
                animation2 = 0;
                setting2.increaseEnum();
                Managers.SOUND.playBoolean();
            } else {
                open = !open;
                if (open) Managers.SOUND.playSwipeIn(); else Managers.SOUND.playSwipeOut();
            }
        }
        if (open) {
            double offY = 0;
            for (int i = 0; i < setting2.getModes().length; i++) {
                if (Render2DEngine.isHovered(mx, my, x, y + wheight + offY, width, 12) && button == 0) {
                    prevMode = setting2.currentEnumName();
                    animation2 = 0;
                    setting2.setEnumByNumber(i);
                    Managers.SOUND.playBoolean();
                }
                offY += 12;
            }
        }
        super.mouseClicked(mx, my, button);
    }
    public void setWHeight(double height) { this.wheight = height; }
    public boolean isOpen() { return open; }
}
