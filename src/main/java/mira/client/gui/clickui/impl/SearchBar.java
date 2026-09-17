package mira.client.gui.clickui.impl;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.StringHelper;
import org.lwjgl.glfw.GLFW;
import mira.client.MiraClient;
import mira.client.gui.clickui.AbstractButton;
import mira.client.gui.clickui.ClickGUI;
import mira.client.gui.font.FontRenderers;
import mira.client.utility.render.Render2DEngine;
import java.awt.*;
import static mira.client.features.modules.Module.mc;
public class SearchBar extends AbstractButton {
    public static String moduleName = "";
    public static boolean listening;
    private static final Color BG_NORMAL  = new Color(35, 35, 44, 220);
    private static final Color BG_ACTIVE  = new Color(42, 42, 54, 235);
    private static final Color BORDER     = new Color(65, 65, 82, 180);
    private static final Color BORDER_ACT = new Color(215, 205, 175, 180);
    private static final Color TEXT_HINT  = new Color(90, 90, 110, 255);
    private static final Color TEXT_INPUT = new Color(210, 210, 222, 255);
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        boolean hov = Render2DEngine.isHovered(mx, my, x, y, width, height);
        Color bg  = listening ? BG_ACTIVE  : BG_NORMAL;
        Color bdr = listening ? BORDER_ACT : BORDER;
        Render2DEngine.drawRound(ctx.getMatrices(), x + 4, y + 1.5f, width - 8, height - 3, 4f, bg);
        Render2DEngine.drawRect(ctx.getMatrices(), x + 4, y + height - 2, width - 8, 1f, bdr);
        String cursor = (mc.player == null || ((mc.player.age / 10) % 2 == 0)) ? "" : "|";
        if (!listening) {
            FontRenderers.sf_medium.drawString(ctx.getMatrices(), "Search...",
                    x + 10, y + height / 2f - 3.5f, TEXT_HINT.getRGB());
        } else {
            FontRenderers.sf_medium.drawString(ctx.getMatrices(),
                    moduleName.isEmpty() ? cursor : moduleName + cursor,
                    x + 10, y + height / 2f - 3.5f, TEXT_INPUT.getRGB());
        }
        if (hov) {
            if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND)
                GLFW.glfwSetCursor(mc.getWindow().getHandle(),
                        GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR));
            ClickGUI.anyHovered = true;
        }
    }
    @Override
    public void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        boolean hov = Render2DEngine.isHovered(mx, my, x, y, width, height);
        if (hov) listening = true;
        else { moduleName = ""; listening = false; }
        if (listening) MiraClient.currentKeyListener = MiraClient.KeyListening.Search;
    }
    @Override
    public void charTyped(char key, int keyCode) {
        if (StringHelper.isValidChar(key) && listening)
            moduleName = moduleName + key;
    }
    @Override
    public void keyTyped(int keyCode) {
        super.keyTyped(keyCode);
        if (keyCode == GLFW.GLFW_KEY_F && (
                InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) ||
                InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL))) {
            listening = !listening;
            MiraClient.currentKeyListener = MiraClient.KeyListening.Search;
            return;
        }
        if (MiraClient.currentKeyListener != MiraClient.KeyListening.Search) return;
        if (listening) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> { listening = false; moduleName = ""; }
                case GLFW.GLFW_KEY_BACKSPACE -> moduleName = SliderElement.removeLastChar(moduleName);
                case GLFW.GLFW_KEY_SPACE -> moduleName = moduleName + " ";
            }
        }
    }
}
