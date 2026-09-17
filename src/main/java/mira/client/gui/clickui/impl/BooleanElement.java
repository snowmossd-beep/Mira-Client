package mira.client.gui.clickui.impl;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import mira.client.core.Managers;
import mira.client.features.modules.render.HudEditor;
import mira.client.gui.clickui.AbstractElement;
import mira.client.gui.clickui.ClickGUI;
import mira.client.gui.font.FontRenderers;
import mira.client.setting.Setting;
import mira.client.utility.render.Render2DEngine;
import java.awt.*;
import static mira.client.core.manager.IManager.mc;
import static mira.client.utility.render.animation.AnimationUtility.fast;
public class BooleanElement extends AbstractElement {
    private float anim  = 0f;
    private float anim2 = 0f;
    private static final Color TRACK_OFF = new Color(50, 50, 62, 220);
    private static final Color KNOB      = new Color(248, 248, 250, 255);
    private static final Color NAME_COL  = new Color(200, 200, 212, 255);
    private static final Color INDENT    = new Color(80, 130, 220, 200);
    public BooleanElement(Setting setting) {
        super(setting);
    }
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        anim  = fast(anim,  (boolean) setting.getValue() ? 1f : 0f, 20f);
        anim2 = fast(anim2, (boolean) setting.getValue() ? 1f : 0f, 8f);
        float trackW = 20f;
        float trackH = 9f;
        float trackX = x + width - trackW - 6;
        float trackY = y + height / 2f - trackH / 2f;
        Color raw = HudEditor.getColor(0);
        Color accent = (raw.getAlpha() < 10 || (raw.getRed() < 5 && raw.getGreen() < 5 && raw.getBlue() < 5))
                ? new Color(200, 80, 80, 255) : raw;
        int cr = (int)(TRACK_OFF.getRed()   + (accent.getRed()   - TRACK_OFF.getRed())   * anim);
        int cg = (int)(TRACK_OFF.getGreen() + (accent.getGreen() - TRACK_OFF.getGreen()) * anim);
        int cb = (int)(TRACK_OFF.getBlue()  + (accent.getBlue()  - TRACK_OFF.getBlue())  * anim);
        Render2DEngine.drawRound(ctx.getMatrices(), trackX, trackY, trackW, trackH, 5f, new Color(cr, cg, cb, 220));
        float ks = trackH - 3f;
        float kt = trackW - ks - 3f;
        Render2DEngine.drawRound(ctx.getMatrices(), trackX + 1.5f + kt * anim2, trackY + 1.5f, ks, ks, 4f, KNOB);
        if (setting.group != null)
            Render2DEngine.drawRect(ctx.getMatrices(), x + 4, y + 2, 1.5f, height - 4, INDENT);
        FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), setting.getName(),
                x + 6 + (setting.group != null ? 4f : 0f),
                y + height / 2f - 3f, NAME_COL.getRGB());
        if (Render2DEngine.isHovered(mx, my, trackX, trackY, trackW, trackH)) {
            if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND)
                GLFW.glfwSetCursor(mc.getWindow().getHandle(),
                        GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
            ClickGUI.anyHovered = true;
        }
    }
    @Override
    public void mouseClicked(int mx, int my, int button) {
        if (hovered && button == 0) {
            setting.setValue(!((Boolean) setting.getValue()));
            Managers.SOUND.playBoolean();
        }
        super.mouseClicked(mx, my, button);
    }
                                                }
