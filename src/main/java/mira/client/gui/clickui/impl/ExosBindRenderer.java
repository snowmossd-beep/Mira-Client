package mira.client.gui.clickui.impl;
import net.minecraft.client.gui.DrawContext;
import mira.client.gui.font.FontRenderers;
import mira.client.setting.Setting;
import mira.client.setting.impl.Bind;
import mira.client.utility.render.Render2DEngine;
import java.awt.*;
import static mira.client.features.modules.Module.mc;
public class ExosBindRenderer {
    private static final int HEIGHT = 16;
    private Setting<?> listening;
    public int getHeight() { return HEIGHT; }
    public boolean isListening(Setting<?> setting) { return listening == setting; }
    public void render(DrawContext ctx, Setting<?> setting, int x, int y, int width, int height) {
        Bind bind = (Bind) setting.getValue();
        String valText = listening == setting ? "..." : bind.getBind();
        float valW = FontRenderers.sf_medium_mini.getStringWidth(valText);
        int boxW = (int) valW + 10;
        int boxX = x + width - boxW;
        int boxY = y + 2;
        int boxH = HEIGHT - 4;
        double scale = mc.getWindow().getScaleFactor();
        double mX = mc.mouse.getX() / scale;
        double mY = mc.mouse.getY() / scale;
        boolean hov = mX >= boxX && mX <= boxX + boxW && mY >= boxY && mY <= boxY + boxH;
        Color boxColor = listening == setting
                ? new Color(0, 200, 83, 200)
                : (hov ? new Color(70, 70, 90, 200) : new Color(45, 45, 60, 200));
        Render2DEngine.drawRound(ctx.getMatrices(), boxX, boxY, boxW, boxH, 4f, boxColor);
        FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), valText,
                boxX + 5, y + (HEIGHT - 7) / 2f, Color.WHITE.getRGB());
        float maxTW = boxX - x - 4;
        Render2DEngine.addWindow(ctx.getMatrices(), x, y, x + maxTW, y + HEIGHT, 1.0);
        FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), setting.getName(),
                x, y + (HEIGHT - 7) / 2f, Color.WHITE.getRGB());
        Render2DEngine.popWindow();
    }
    public boolean mouseClicked(Setting<?> setting, double mx, double my, int btn,
                                int x, int y, int width, int height) {
        Bind current = (Bind) setting.getValue();
        String valText = listening == setting ? "..." : current.getBind();
        float valW = FontRenderers.sf_medium_mini.getStringWidth(valText);
        int boxW = (int) valW + 10;
        int boxX = x + width - boxW;
        int boxY = y + 2;
        int boxH = HEIGHT - 4;
        if (listening == setting) {
            @SuppressWarnings("unchecked")
            Setting<Bind> bs = (Setting<Bind>) setting;
            bs.setValue(new Bind(btn, true, false));
            listening = null;
            return true;
        }
        if (mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + boxH && btn == 0) {
            listening = setting;
            return true;
        }
        return false;
    }
    public boolean keyPressed(int keyCode) {
        if (listening == null) return false;
        @SuppressWarnings("unchecked")
        Setting<Bind> bs = (Setting<Bind>) listening;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
            bs.setValue(new Bind(-1, false, false));
        } else {
            bs.setValue(new Bind(keyCode, false, false));
        }
        listening = null;
        return true;
    }
          }
          