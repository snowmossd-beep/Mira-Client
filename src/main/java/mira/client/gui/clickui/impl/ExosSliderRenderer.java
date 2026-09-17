package mira.client.gui.clickui.impl;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import mira.client.gui.font.FontRenderers;
import mira.client.setting.Setting;
import mira.client.utility.color.ColorUtil;
import mira.client.utility.render.Render2DEngine;
import java.awt.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
public class ExosSliderRenderer {
    private static final int   HEIGHT           = 20;
    private static final int   BAR_HEIGHT       = 4;
    private static final float CIRCLE_RADIUS    = 6f;
    private static final float CIRCLE_SCALE_MAX = 1.2f;
    private static final float CIRCLE_SCALE_MIN = 1f;
    private static final float SCALE_STEP       = 0.05f;
    private final Map<Setting<?>, Double> circlePosMap   = new HashMap<>();
    private final Map<Setting<?>, Float>  circleScaleMap = new HashMap<>();
    private final Map<Setting<?>, Boolean> draggingMap   = new HashMap<>();
    public int getHeight() { return HEIGHT; }
    public void render(DrawContext ctx, Setting<?> setting, int x, int y, int width, int height) {
        int barX = x;
        int barY = y + height - BAR_HEIGHT - 4;
        int barW = width;
        Render2DEngine.drawRound(ctx.getMatrices(), barX, barY, barW, BAR_HEIGHT, 1f,
                new Color(50, 50, 50, 180));
        double val  = getVal(setting);
        double min  = getMin(setting);
        double max  = getMax(setting);
        double inc  = getIncrement(setting);
        double rounded = Math.round(val / inc) * inc;
        double progress = (max - min) == 0 ? 0 : (rounded - min) / (max - min);
        int targetPW = (int)(barW * progress);
        double circlePos = circlePosMap.getOrDefault(setting, (double) targetPW);
        if (circlePos == -1) circlePos = targetPW;
        circlePos += (targetPW - circlePos) * 0.2;
        circlePosMap.put(setting, circlePos);
        Render2DEngine.drawRound(ctx.getMatrices(), barX, barY, (float) circlePos, BAR_HEIGHT, 1f,
                new Color(ColorUtil.getFirstColor(), true));
        FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), setting.getName(),
                x, y + 2, Color.WHITE.getRGB());
        String valStr = formatValue(rounded, inc);
        float vw = FontRenderers.sf_medium_mini.getStringWidth(valStr);
        FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), valStr,
                x + width - vw, y + 2, Color.WHITE.getRGB());
        boolean dragging = draggingMap.getOrDefault(setting, false);
        float circleScale = circleScaleMap.getOrDefault(setting, CIRCLE_SCALE_MIN);
        if (dragging) {
            circleScale = Math.min(circleScale + SCALE_STEP, CIRCLE_SCALE_MAX);
        } else {
            circleScale = Math.max(circleScale - SCALE_STEP, CIRCLE_SCALE_MIN);
        }
        circleScaleMap.put(setting, circleScale);
        float cx = barX + (float) circlePos;
        float cy = barY + BAR_HEIGHT / 2f;
        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(cx, cy, 0);
        matrices.scale(circleScale, circleScale, 1f);
        matrices.translate(-cx, -cy, 0);
        Render2DEngine.renderRoundedQuad(matrices, Color.WHITE,
                cx - CIRCLE_RADIUS, cy - CIRCLE_RADIUS,
                cx + CIRCLE_RADIUS, cy + CIRCLE_RADIUS,
                CIRCLE_RADIUS, 8);
        matrices.pop();
    }
    public boolean mouseClicked(Setting<?> setting, double mx, double my, int btn,
                                int x, int y, int width, int height) {
        if (btn != 0) return false;
        int barX = x, barY = y + height - BAR_HEIGHT - 4, barW = width;
        if (mx >= barX && mx <= barX + barW && my >= barY - 3 && my <= barY + BAR_HEIGHT + 6) {
            updateValue(setting, mx, barX, barW);
            draggingMap.put(setting, true);
            return true;
        }
        return false;
    }
    public void mouseDragged(Setting<?> setting, double mx, int x, int width) {
        if (!draggingMap.getOrDefault(setting, false)) return;
        updateValue(setting, mx, x, width);
    }
    public void mouseReleased(Setting<?> setting) {
        draggingMap.put(setting, false);
    }
    @SuppressWarnings("unchecked")
    private void updateValue(Setting<?> setting, double mx, int barX, int barW) {
        double pct    = Math.max(0, Math.min(1, (mx - barX) / barW));
        double min    = getMin(setting);
        double max    = getMax(setting);
        double inc    = getIncrement(setting);
        double newVal = min + pct * (max - min);
        newVal = Math.round(newVal / inc) * inc;
        newVal = Math.max(min, Math.min(max, newVal));
        if (setting.getValue() instanceof Integer) {
            ((Setting<Integer>) setting).setValue((int) newVal);
        } else {
            ((Setting<Float>) setting).setValue((float) newVal);
        }
    }
    private double getVal(Setting<?> s) {
        Object v = s.getValue();
        if (v instanceof Float f) return f;
        if (v instanceof Double d) return d;
        if (v instanceof Integer i) return i;
        return 0;
    }
    private double getMin(Setting<?> s) {
        Object v = s.getMin();
        if (v instanceof Float f) return f;
        if (v instanceof Double d) return d;
        if (v instanceof Integer i) return i;
        return 0;
    }
    private double getMax(Setting<?> s) {
        Object v = s.getMax();
        if (v instanceof Float f) return f;
        if (v instanceof Double d) return d;
        if (v instanceof Integer i) return i;
        return 1;
    }
    private double getIncrement(Setting<?> s) {
        double min = getMin(s), max = getMax(s);
        if (s.getValue() instanceof Integer) return 1;
        double range = max - min;
        if (range <= 2)   return 0.01;
        if (range <= 20)  return 0.1;
        return 1;
    }
    private String formatValue(double val, double inc) {
        if (inc >= 1)   return String.format(Locale.US, "%d", (long) val);
        if (inc >= 0.1) return String.format(Locale.US, "%.1f", val);
        return String.format(Locale.US, "%.2f", val);
    }
}
