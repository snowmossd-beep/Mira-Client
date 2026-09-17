package mira.client.gui.clickui.impl;
import net.minecraft.client.gui.DrawContext;
import mira.client.gui.clickui.DescriptionRenderQueue;
import mira.client.gui.font.FontRenderers;
import mira.client.setting.Setting;
import mira.client.utility.color.ColorUtil;
import mira.client.utility.render.Render2DEngine;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import static mira.client.features.modules.Module.mc;
public class ExosToggleSwitchRenderer {
    private static final int HEIGHT        = 16;
    private static final int WIDTH         = 22;
    private static final int SWITCH_HEIGHT = 12;
    private static final int KNOB_RADIUS   = 8;
    private final Map<Setting<?>, Float> toggleMap = new HashMap<>();
    private final Map<Setting<?>, Float> scrollMap = new HashMap<>();
    public int getHeight() { return HEIGHT; }
    public void render(DrawContext ctx, Setting<?> setting, int x, int y, int width, int height) {
        boolean val = (Boolean) setting.getValue();
        float progress = toggleMap.getOrDefault(setting, val ? 1f : 0f);
        progress += ((val ? 1f : 0f) - progress) * 0.15f;
        toggleMap.put(setting, progress);
        int switchX = x + width - WIDTH + 4;
        int switchY = y + (HEIGHT - SWITCH_HEIGHT) / 2 - 2;
        Color offColor = new Color(50, 50, 50, 200);
        int onColor    = new Color(0, 200, 83, 255).getRGB();
        int bgColor    = ColorUtil.interpolateColor(offColor.getRGB(), onColor, progress);
        Render2DEngine.drawRound(ctx.getMatrices(), switchX, switchY, WIDTH, SWITCH_HEIGHT, 5f,
                new Color(bgColor, true));
        float kx = switchX + 3 + (WIDTH - KNOB_RADIUS - 5) * progress + KNOB_RADIUS / 2f;
        float ky = switchY + (SWITCH_HEIGHT - KNOB_RADIUS) / 2f + KNOB_RADIUS / 2f;
        Render2DEngine.renderRoundedQuad(ctx.getMatrices(), Color.WHITE,
                kx - KNOB_RADIUS / 2f, ky - KNOB_RADIUS / 2f,
                kx + KNOB_RADIUS / 2f, ky + KNOB_RADIUS / 2f,
                KNOB_RADIUS / 2f, 8);
        String text      = setting.getName();
        float maxTW      = switchX - x - 4;
        float textWidth  = FontRenderers.sf_medium_mini.getStringWidth(text);
        int   textY      = y + (HEIGHT - 7) / 2;
        double scale = mc.getWindow().getScaleFactor();
        double mX = mc.mouse.getX() / scale;
        double mY = mc.mouse.getY() / scale;
        float overflow = textWidth - maxTW;
        float offset   = scrollMap.getOrDefault(setting, 0f);
        boolean textHovered = mX >= x && mX <= x + maxTW && mY >= textY - 1 && mY <= textY + 8;
        if (textHovered && overflow > 0) offset = Math.min(offset + 0.5f, overflow);
        else offset = Math.max(offset - 0.5f, 0f);
        scrollMap.put(setting, offset);
        Render2DEngine.addWindow(ctx.getMatrices(), x, textY - 1, x + maxTW, textY + 8, 1.0);
        ctx.getMatrices().push();
        FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), text,
                x - offset, textY, Color.WHITE.getRGB());
        ctx.getMatrices().pop();
        Render2DEngine.popWindow();
        boolean switchHov = mX >= switchX && mX <= switchX + WIDTH && mY >= switchY && mY <= switchY + SWITCH_HEIGHT;
        if (switchHov && setting.getName() != null && !setting.getName().isEmpty()) {
            DescriptionRenderQueue.add(setting.getName(), (float) mX + 6, (float) mY + 6);
        }
    }
    public boolean mouseClicked(Setting<?> setting, double mx, double my, int btn,
                                int x, int y, int width, int height) {
        if (btn != 0) return false;
        int switchX = x + width - WIDTH + 4;
        int switchY = y + (HEIGHT - SWITCH_HEIGHT) / 2 - 2;
        if (mx >= switchX && mx <= switchX + WIDTH && my >= switchY && my <= switchY + SWITCH_HEIGHT) {
            @SuppressWarnings("unchecked")
            Setting<Boolean> bs = (Setting<Boolean>) setting;
            bs.setValue(!bs.getValue());
            return true;
        }
        return false;
    }
}
