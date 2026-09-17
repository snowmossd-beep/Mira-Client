package mira.client.utility.color;

import net.minecraft.util.math.MathHelper;
import mira.client.utility.render.ThemeManager;

import java.awt.*;

public class ColorUtil {

    public static int applyAlpha(int color, float alpha) {
        int a = (int)(((color >> 24) & 0xFF) * alpha);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int reAlphaInt(int color, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, float a) {
        int ai = MathHelper.clamp((int)(a * 255f), 0, 255);
        return (rgb & 0x00FFFFFF) | (ai << 24);
    }

    public static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int getRed(int hex)   { return (hex >> 16) & 255; }
    public static int getGreen(int hex) { return (hex >> 8) & 255; }
    public static int getBlue(int hex)  { return hex & 255; }
    public static int getAlpha(int hex) { return (hex >> 24) & 255; }

    public static int getFirstColor() {
        return ThemeManager.INSTANCE.getFirstColor();
    }

    public static int getSecondColor() {
        return ThemeManager.INSTANCE.getSecondColor();
    }

    public static int getColorStyle(float index) {
        return ThemeManager.gradient(5, (int) index,
            ThemeManager.INSTANCE.getFirstColor(),
            ThemeManager.INSTANCE.getSecondColor());
    }

    public static int getColorStyle(float index, int alpha) {
        int g = getColorStyle(index);
        return new Color((g >> 16) & 0xFF, (g >> 8) & 0xFF, g & 0xFF, MathHelper.clamp(alpha, 0, 255)).getRGB();
    }

    public static int interpolateColor(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int)(getRed(c1)   * (1 - t) + getRed(c2)   * t);
        int g = (int)(getGreen(c1) * (1 - t) + getGreen(c2) * t);
        int b = (int)(getBlue(c1)  * (1 - t) + getBlue(c2)  * t);
        int a = (int)(getAlpha(c1) * (1 - t) + getAlpha(c2) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
