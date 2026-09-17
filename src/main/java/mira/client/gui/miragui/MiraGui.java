package mira.client.gui.miragui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import mira.client.MiraClient;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.features.modules.Module;
import mira.client.features.modules.render.MiraClientGui;
import mira.client.gui.font.FontRenderers;
import mira.client.gui.windows.WindowsScreen;
import mira.client.gui.windows.impl.FriendsWindow;
import mira.client.setting.Setting;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.TextureStorage;
import mira.client.utility.render.animation.EaseOutBack;
import com.mojang.blaze3d.systems.RenderSystem;

import mira.client.gui.miragui.components.ModulePlate;
import java.awt.Color;
import java.util.*;

import static mira.client.features.modules.Module.mc;

public class MiraGui extends Screen {

    private static MiraGui INSTANCE;
    public static EaseOutBack open_animation = new EaseOutBack(5);
    public static boolean open_direction = false;

    public float main_posY = 0;
    public Module.Category current_category = null;
    public Module.Category new_category = null;
    public static ModulePlate selected_plate = null;
    public static boolean scroll_lock = false;

    public void loadConfigs() {}
    public void loadFriends() {}

    static { INSTANCE = new MiraGui(); }

    private static final int PANEL_WIDTH  = 90;
    private static final int PANEL_HEIGHT = 220;
    private static final int PANEL_GAP    = 6;
    private static final int MODULE_H     = 20;
    private static final int HEADER_H     = 20;
    private static final int SCROLL_AREA_H = PANEL_HEIGHT - HEADER_H - 5;

    private static final Color C_BG      = new Color(17, 15, 28, 235);
    private static final Color C_PANEL   = new Color(17, 15, 28, 235);
    private static final Color C_SEARCH  = new Color(17, 15, 28, 235);
    private static final Color C_SCROLL_BG  = new Color(0, 0, 0, 50);
    private static final Color C_SCROLL_FG  = new Color(255, 255, 255, 150);

    private final List<Module.Category> categories = new ArrayList<>(Arrays.asList(
            Module.Category.COMBAT, Module.Category.MOVEMENT,
            Module.Category.RENDER, Module.Category.PLAYER, Module.Category.MISC
    ));

    private final Map<Module.Category, Float> scrollOffsets = new LinkedHashMap<>();
    private final Map<Module.Category, Float> scrollTargets = new LinkedHashMap<>();

    private String searchText = "";
    private boolean searchFocused = false;
    private boolean cursorVisible = true;
    private long lastBlink = 0;

    private static boolean themeMenuOpen = false;
    private float themeAnim = 0f;

    public static boolean mouse_state;
    public static int mouse_x, mouse_y;

    public MiraGui() {
        super(Text.of("MiraGui"));
        setInstance();
        for (Module.Category cat : categories) {
            scrollOffsets.put(cat, 0f);
            scrollTargets.put(cat, 0f);
        }
    }

    private void setInstance() { INSTANCE = this; }

    public static MiraGui getInstance() {
        if (INSTANCE == null) INSTANCE = new MiraGui();
        return INSTANCE;
    }

    public static MiraGui getMiraGui() {
        open_animation = new EaseOutBack();
        open_direction = true;
        return getInstance();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (Module.fullNullCheck()) renderBackground(ctx, mouseX, mouseY, delta);
        mouse_x = mouseX;
        mouse_y = mouseY;
        ctx.getMatrices().push();
        if (open_animation.getAnimationd() > 0) renderGui(ctx, mouseX, mouseY, delta);
        if (open_animation.getAnimationd() <= 0.01 && !open_direction) {
            open_animation = new EaseOutBack();
            mc.currentScreen = null;
            mc.setScreen(null);
        }
        ctx.getMatrices().pop();
    }

    private void renderGui(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (now - lastBlink > 500) { cursorVisible = !cursorVisible; lastBlink = now; }

        themeAnim += (themeMenuOpen ? 1f : 0f - themeAnim) * 0.2f;

        int totalW = categories.size() * (PANEL_WIDTH + PANEL_GAP) - PANEL_GAP;
        int startX = (width - totalW) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;
        main_posY = startY;

        for (Module.Category cat : categories) {
            float t = scrollTargets.get(cat);
            float o = scrollOffsets.get(cat);
            scrollOffsets.put(cat, o + (t - o) * 0.2f);
        }

        int idx = 0;
        for (Module.Category cat : categories) {
            int px = startX + idx++ * (PANEL_WIDTH + PANEL_GAP);
            renderPanel(ctx, px, startY, cat, mouseX, mouseY);
        }

        renderSearchBar(ctx, startX, startY, totalW, mouseX, mouseY);
        renderThemeBar(ctx, startX, startY, totalW, mouseX, mouseY);
        renderTitle(ctx, startX, startY, totalW);
    }

    private void renderPanel(DrawContext ctx, int x, int y, Module.Category cat, int mouseX, int mouseY) {
        Render2DEngine.drawRound(ctx.getMatrices(), x, y, PANEL_WIDTH, PANEL_HEIGHT, 12f, C_PANEL);

        String title = cat.getName();
        int tw = (int) FontRenderers.sf_medium.getStringWidth(title);
        FontRenderers.sf_medium.drawString(ctx.getMatrices(), title, x + (PANEL_WIDTH - tw) / 2, y + 5, -1);

        float offset = scrollOffsets.get(cat);
        int maxScroll = calcMaxScroll(cat);
        if (maxScroll > 0) {
            int sbX = x + PANEL_WIDTH - 4;
            int sbY = y + HEADER_H + 5;
            int sbH = SCROLL_AREA_H - 10;
            Render2DEngine.drawRound(ctx.getMatrices(), sbX, sbY, 2, sbH, 1f, C_SCROLL_BG);
            float prog = offset / maxScroll;
            int thumbH = Math.max(6, sbH * SCROLL_AREA_H / (SCROLL_AREA_H + maxScroll));
            int thumbY = sbY + (int)(prog * (sbH - thumbH));
            Render2DEngine.drawRound(ctx.getMatrices(), sbX, thumbY, 2, thumbH, 1f, C_SCROLL_FG);
        }

        Render2DEngine.addWindow(ctx.getMatrices(), x, y + HEADER_H, x + PANEL_WIDTH, y + HEADER_H + SCROLL_AREA_H, 1d);
        ctx.getMatrices().push();

        float curY = y + HEADER_H - offset;
        for (Module module : Managers.MODULE.getModulesByCategory(cat)) {
            if (!isVisible(module)) continue;
            if (curY + MODULE_H < y + HEADER_H || curY > y + PANEL_HEIGHT) { curY += MODULE_H; continue; }

            Color col1 = module.isOn() ? MiraClientGui.onColor1.getValue().getColorObject() : new Color(198, 198, 198);
            Color col2 = module.isOn() ? MiraClientGui.onColor2.getValue().getColorObject() : col1;
            int alpha = 20;

            if (module.isOn()) {
                Render2DEngine.drawGradientRound(ctx.getMatrices(), x + 4, curY, PANEL_WIDTH - 8, MODULE_H - 1, 4f,
                        withAlpha(col2, alpha), withAlpha(col2, alpha), withAlpha(col2, alpha), withAlpha(col2, alpha));
            }

            FontRenderers.sf_medium.drawString(ctx.getMatrices(), module.getName(), x + 8, curY + 3,
                    withAlpha(col1, 255).getRGB());

            curY += MODULE_H;
        }

        ctx.getMatrices().pop();
        Render2DEngine.popWindow();
    }

    private void renderSearchBar(DrawContext ctx, int startX, int startY, int totalW, int mouseX, int mouseY) {
        int sw = 180;
        int sx = (width - sw) / 2;
        int sy = startY + PANEL_HEIGHT + 10;

        Render2DEngine.drawRound(ctx.getMatrices(), sx, sy, sw, 20, 6f, C_SEARCH);

        String display;
        int textColor;
        int tx;
        if (searchText.isEmpty() && !searchFocused) {
            display = "Поиск...";
            textColor = new Color(255, 255, 255, 120).getRGB();
            int dw = (int) FontRenderers.sf_medium.getStringWidth(display);
            tx = sx + (sw - dw) / 2;
        } else {
            String t = searchText;
            if (searchFocused && cursorVisible) t += "|";
            display = t;
            textColor = -1;
            tx = sx + 6;
        }
        FontRenderers.sf_medium.drawString(ctx.getMatrices(), display, tx, sy + 5, textColor);

        int btnX = sx + sw + 6;
        int btnY = sy + 2;
        boolean hov = mouseX >= btnX && mouseX <= btnX + 16 && mouseY >= btnY && mouseY <= btnY + 16;
        Render2DEngine.drawRound(ctx.getMatrices(), btnX, btnY, 16, 16, 2f, hov ? C_SEARCH.brighter() : C_SEARCH);
        FontRenderers.icons.drawString(ctx.getMatrices(), "p", btnX + 3, btnY + 3, -1);

        int friendBtnX = btnX + 16 + 6;
        int friendBtnY = sy + 2;
        boolean friendHov = mouseX >= friendBtnX && mouseX <= friendBtnX + 16 && mouseY >= friendBtnY && mouseY <= friendBtnY + 16;
        Render2DEngine.drawRound(ctx.getMatrices(), friendBtnX, friendBtnY, 16, 16, 2f, friendHov ? C_SEARCH.brighter() : C_SEARCH);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, friendHov ? 0.95f : 0.75f);
        ctx.drawTexture(TextureStorage.friendIcon, friendBtnX + 3, friendBtnY + 3, 0, 0, 10, 10, 10, 10);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderThemeBar(DrawContext ctx, int startX, int startY, int totalW, int mouseX, int mouseY) {
        if (themeAnim < 0.01f) return;
        int tw = 180;
        int tx = (width - tw) / 2;
        int ty = startY + PANEL_HEIGHT + 36;
        int circleS = 11;
        int pad = 4;

        Render2DEngine.drawRound(ctx.getMatrices(), tx, ty, tw, 16, 3f,
                new Color(17, 15, 28, (int)(235 * themeAnim)));

        float cx = tx + pad;
        for (Module module : Managers.MODULE.modules) {
            if (!(module instanceof mira.client.features.modules.render.MiraClientGui)) continue;
        }

        Color c1 = MiraClientGui.onColor1.getValue().getColorObject();
        Color c2 = MiraClientGui.onColor2.getValue().getColorObject();
        Render2DEngine.drawGradientRound(ctx.getMatrices(), (int) cx, ty + 2, circleS, circleS, 5f, c1, c1, c2, c2);
    }

    private void renderTitle(DrawContext ctx, int startX, int startY, int totalW) {
        String title = "exosware";
        String ver = "v" + MiraClient.VERSION;
        int tx = startX + totalW / 2 - (int) FontRenderers.sf_bold.getStringWidth(title) / 2;
        int ty = startY - 22;
        FontRenderers.sf_bold.drawString(ctx.getMatrices(), title, tx, ty, MiraClientGui.onColor1.getValue().getColorObject().getRGB());
        FontRenderers.settings.drawString(ctx.getMatrices(), ver, tx + FontRenderers.sf_bold.getStringWidth(title) + 4, ty + 4, new Color(0x656565).getRGB());
    }

    private int calcMaxScroll(Module.Category cat) {
        int total = 0;
        for (Module m : Managers.MODULE.getModulesByCategory(cat)) {
            if (isVisible(m)) total += MODULE_H;
        }
        return Math.max(0, total - SCROLL_AREA_H);
    }

    private boolean isVisible(Module m) {
        if (searchText.isEmpty()) return true;
        return m.getName().toLowerCase().contains(searchText.toLowerCase());
    }

    private Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
    }

    public void onTick() {
        open_animation.update(open_direction);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouse_state = true;

        int sw = 180;
        int totalW = categories.size() * (PANEL_WIDTH + PANEL_GAP) - PANEL_GAP;
        int startX = (width - totalW) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;
        int searchX = (width - sw) / 2;
        int searchY = startY + PANEL_HEIGHT + 10;

        if (mouseX >= searchX && mouseX <= searchX + sw && mouseY >= searchY && mouseY <= searchY + 20) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        int btnX = searchX + sw + 6;
        int btnY = searchY + 2;
        if (mouseX >= btnX && mouseX <= btnX + 16 && mouseY >= btnY && mouseY <= btnY + 16) {
            themeMenuOpen = !themeMenuOpen;
            return true;
        }

        int friendBtnX = btnX + 16 + 6;
        int friendBtnY = searchY + 2;
        if (mouseX >= friendBtnX && mouseX <= friendBtnX + 16 && mouseY >= friendBtnY && mouseY <= friendBtnY + 16) {
            Setting<mira.client.setting.impl.PositionSetting> friendPos = ModuleManager.windows.friendPos;
            mc.setScreen(new WindowsScreen(
                    FriendsWindow.get(
                            friendPos.getValue().getX() * mc.getWindow().getScaledWidth(),
                            friendPos.getValue().getY() * mc.getWindow().getScaledHeight(),
                            friendPos)
            ));
            return true;
        }

        int idx = 0;
        for (Module.Category cat : categories) {
            int px = startX + idx++ * (PANEL_WIDTH + PANEL_GAP);
            float offset = scrollOffsets.get(cat);
            float curY = startY + HEADER_H - offset;

            for (Module module : Managers.MODULE.getModulesByCategory(cat)) {
                if (!isVisible(module)) continue;
                if (mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= curY && mouseY <= curY + MODULE_H
                        && mouseY >= startY + HEADER_H && mouseY <= startY + PANEL_HEIGHT) {
                    if (button == 0) module.toggle();
                    return true;
                }
                curY += MODULE_H;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouse_state = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalW = categories.size() * (PANEL_WIDTH + PANEL_GAP) - PANEL_GAP;
        int startX = (width - totalW) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;

        int idx = 0;
        for (Module.Category cat : categories) {
            int px = startX + idx++ * (PANEL_WIDTH + PANEL_GAP);
            if (mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= startY + HEADER_H && mouseY <= startY + PANEL_HEIGHT) {
                int maxScroll = calcMaxScroll(cat);
                if (maxScroll > 0) {
                    float newTarget = scrollTargets.get(cat) - (float)(scrollY * 12);
                    scrollTargets.put(cat, MathHelper.clamp(newTarget, 0f, maxScroll));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open_direction = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && searchText.length() < 30) {
            searchText += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
    }
    