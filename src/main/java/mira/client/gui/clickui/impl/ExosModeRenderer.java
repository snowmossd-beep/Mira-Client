package mira.client.gui.clickui.impl;
import net.minecraft.client.gui.DrawContext;
import mira.client.gui.font.FontRenderers;
import mira.client.setting.Setting;
import mira.client.utility.color.ColorUtil;
import mira.client.utility.render.Render2DEngine;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import static mira.client.features.modules.Module.mc;
public class ExosModeRenderer {
    private static final int TITLE_H  = 14;
    private static final int Y_OFFSET = 1;
    private static final int BOX_H    = 12;
    private static final int SPACING  = 2;
    private static final int PAD_H    = 3;
    private final Map<String, Float> hoverProg   = new HashMap<>();
    private final Map<String, Float> scrollOff   = new HashMap<>();
    public int getHeight(Setting<?> setting, int width) {
        int cx = 0, lines = 1;
        for (String mode : setting.getModes()) {
            int bw = (int) FontRenderers.sf_medium_mini.getStringWidth(mode) + PAD_H * 2;
            if (cx + bw > width) { cx = 0; lines++; }
            cx += bw + 4;
        }
        return TITLE_H + lines * (BOX_H + SPACING);
    }
    public void render(DrawContext ctx, Setting<?> setting, int x, int y, int width, int height) {
        double mxD = mc.mouse.getX() / mc.getWindow().getScaleFactor();
        double myD = mc.mouse.getY() / mc.getWindow().getScaleFactor();
        FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), setting.getName(),
                x, y, Color.WHITE.getRGB());
        int cx = x, cy = y + TITLE_H - Y_OFFSET;
        int lineH = BOX_H + SPACING;
        for (String mode : setting.getModes()) {
            int tw  = (int) FontRenderers.sf_medium_mini.getStringWidth(mode);
            int bw  = tw + PAD_H * 2;
            if (cx + bw > x + width) { cx = x; cy += lineH; }
            boolean sel = mode.equalsIgnoreCase(setting.currentEnumName());
            boolean hov = mxD >= cx && mxD <= cx + bw && myD >= cy && myD <= cy + BOX_H;
            float hp = hoverProg.getOrDefault(mode, 0f);
            if (sel) hp = 1f;
            else     hp += ((hov ? 1f : 0f) - hp) * 0.08f;
            hoverProg.put(mode, hp);
            int accentInt  = ColorUtil.getFirstColor();
            Color base     = new Color(30, 30, 30, 180);
            int bgColor    = sel ? accentInt : ColorUtil.interpolateColor(base.getRGB(), base.getRGB(), hp);
            int textColor  = ColorUtil.interpolateColor(
                    new Color(200, 200, 200).getRGB(), Color.WHITE.getRGB(), hp);
            Render2DEngine.drawRound(ctx.getMatrices(), cx, cy, bw, BOX_H, 2f,
                    new Color(bgColor, true));
            float offset = scrollOff.getOrDefault(mode, 0f);
            if (hov && tw > bw) offset = Math.min(offset + 0.5f, tw - bw);
            else                offset = Math.max(offset - 0.5f, 0f);
            scrollOff.put(mode, offset);
            Render2DEngine.addWindow(ctx.getMatrices(), cx, cy, cx + bw, cy + BOX_H, 1.0);
            ctx.getMatrices().push();
            FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), mode,
                    cx + bw / 2f - tw / 2f - offset,
                    cy + (BOX_H - 7) / 2f,
                    textColor);
            ctx.getMatrices().pop();
            Render2DEngine.popWindow();
            cx += bw + 4;
        }
    }
    public boolean mouseClicked(Setting<?> setting, double mx, double my, int btn,
                                int x, int y, int width, int height) {
        if (btn != 0) return false;
        int cx = x, cy = y + TITLE_H - Y_OFFSET;
        int lineH = BOX_H + SPACING;
        for (String mode : setting.getModes()) {
            int bw = (int) FontRenderers.sf_medium_mini.getStringWidth(mode) + PAD_H * 2;
            if (cx + bw > x + width) { cx = x; cy += lineH; }
            if (mx >= cx && mx <= cx + bw && my >= cy && my <= cy + BOX_H) {
                setting.setEnumByName(mode);
                return true;
            }
            cx += bw + 4;
        }
        return false;
    }
                                                }
