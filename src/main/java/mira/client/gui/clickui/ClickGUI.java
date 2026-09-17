package mira.client.gui.clickui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.features.modules.render.MiraClientGui;
import mira.client.features.modules.render.ClickGui;
import mira.client.features.modules.Module;
import mira.client.gui.clickui.impl.*;
import mira.client.gui.font.FontRenderers;
import mira.client.gui.windows.WindowsScreen;
import mira.client.gui.windows.impl.FriendsWindow;
import mira.client.setting.Setting;
import mira.client.setting.impl.PositionSetting;
import mira.client.utility.color.ColorUtil;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.TextureStorage;
import mira.client.utility.render.ThemeManager;

import java.awt.*;
import java.util.*;
import java.util.List;

import static mira.client.features.modules.Module.mc;

public class ClickGUI extends Screen {

    public static boolean anyHovered = false;
    public static boolean close      = false;
    public static String currentDescription = "";
    public mira.client.utility.render.animation.EaseOutBack imageAnimation =
            new mira.client.utility.render.animation.EaseOutBack();

    private boolean isClose = false;

    private static final int   PANEL_WIDTH       = 125;
    private static final int   PANEL_HEIGHT      = 280;
    private static final int   PANEL_MARGIN      = 8;
    private static final int   PANEL_RADIUS      = 12;
    private static final int   TITLE_MARGIN_TOP  = 5;
    private static final float FONT_TITLE_BOX    = 12f;
    private static final int   TITLE_HEIGHT      = 20;
    private static final int   FUNCTION_HEIGHT   = 20;
    private static final int   SCROLL_AREA_Y     = TITLE_MARGIN_TOP + TITLE_HEIGHT;
    private static final int   SCROLL_AREA_H     = PANEL_HEIGHT - SCROLL_AREA_Y - 5;
    private static final int   SEARCH_HEIGHT     = 14;
    private static final int   SEARCH_MARGIN_BOT = 8;
    private static final int   SEARCH_MAX_WIDTH  = 140;
    private static final int   THEME_HEIGHT      = 20;
    private static final int   THEME_MARGIN_TOP  = 18;
    private static final int   THEME_MAX_WIDTH   = 320;
    private static final int   VISIBLE_THEMES    = 26;
    private static final float SCROLL_SPEED      = 12f;
    private static final float SCROLL_LERP       = 20f;
    private static final float THEME_SCROLL_SPEED = 15f;
    private static final float THEME_SCROLL_LERP  = 15f;
    private static final float THEME_ANIM_SPEED   = 0.2f;
    private static final int   MODULE_BG_ALPHA   = 150;
    private static final int   MODULE_OFF_BG_ALPHA = 10;
    private static final int   MODULE_ROUNDING   = 6;

    private static final Color GUI_COLOR          = new Color(0x0A, 0x0B, 0x0F, 110);
    private static final Color PANEL_BG_COLOR     = new Color(0x0A, 0x0B, 0x0F, 110);

    private static final Color PANEL_BORDER_COLOR_FIXED = new Color(0x00, 0x00, 0x00, 140);

    private static Color getBorderColor() {
        return PANEL_BORDER_COLOR_FIXED;
    }
    private static final Color HEADER_SEPARATOR_COLOR = new Color(0xFF, 0xFF, 0xFF, 20);
    private static final Color MODULE_OFF_COLOR   = new Color(190, 194, 205);
    private static final Color MODULE_OFF_BG_COLOR = new Color(0x34, 0x3A, 0x48, MODULE_OFF_BG_ALPHA);
    private static final Color SCROLLBAR_TRACK_COLOR = new Color(0x00, 0x00, 0x00, 60);
    private static final Color SCROLLBAR_THUMB_COLOR = new Color(180, 190, 210, 140);
    private static final Color SEARCH_HINT_COLOR  = new Color(160, 168, 185, 150);

    private static final Map<String, String> CAT_ICONS = new HashMap<>();
    static {
        CAT_ICONS.put("Combat",   "f");
        CAT_ICONS.put("Movement", "w");
        CAT_ICONS.put("Render",   "E");
        CAT_ICONS.put("Player",   "r");
        CAT_ICONS.put("Misc",     "v");
    }

    private static final List<Module.Category> CATS = Arrays.asList(
        Module.Category.COMBAT, Module.Category.MOVEMENT,
        Module.Category.RENDER, Module.Category.PLAYER, Module.Category.MISC);

    private static final Map<Module.Category, Float> scrollOffsets = new HashMap<>();
    private static final Map<Module.Category, Float> scrollTargets = new HashMap<>();
    private final Map<Module, Float> expandProgress = new HashMap<>();
    private final Map<Module, Float> arrowProgress  = new HashMap<>();

    private float openAnim = 0f;

    private static float themeScrollOffset = 0f;
    private static float themeScrollTarget = 0f;
    private float themeNameAnim   = 0f;

    private final SearchState searchState = new SearchState();
    private boolean functionBinding = false;
    private Module bindingModule = null;

    private Setting<?> draggingSlider = null;
    private int draggingSliderX = 0;
    private int draggingSliderW = 0;

    private final ExosToggleSwitchRenderer boolRend   = new ExosToggleSwitchRenderer();
    private final ExosSliderRenderer       sliderRend = new ExosSliderRenderer();
    private final ExosModeRenderer         modeRend   = new ExosModeRenderer();
    private final ExosBindRenderer         bindRend   = new ExosBindRenderer();

    public ClickGUI() {
        super(Text.literal("ClickGUI"));
        if (scrollOffsets.isEmpty()) {
            CATS.forEach(c -> { scrollOffsets.put(c, 0f); scrollTargets.put(c, 0f); });
        }
    }

    public static ClickGUI getClickGui() { return new ClickGUI(); }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) {}

    @Override
    public void init() {
        super.init();
        isClose = false;
        openAnim = 0f;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (isClose && openAnim <= 0.01f) { mc.setScreen(null); return; }
        openAnim = isClose
            ? Math.max(0f, openAnim - delta * 5f)
            : Math.min(1f, openAnim + delta * 5f);
        if (openAnim <= 0.01f) return;

        CATS.forEach(c -> {
            float t = scrollTargets.get(c), o = scrollOffsets.get(c);
            scrollOffsets.put(c, lerp(o, t, SCROLL_LERP * delta));
        });
        themeScrollOffset = lerp(themeScrollOffset, themeScrollTarget, THEME_SCROLL_LERP * delta);

        int totalW = CATS.size() * (PANEL_WIDTH + PANEL_MARGIN) - PANEL_MARGIN;
        int startX = (width - totalW) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, height / 2f, 0);
        ctx.getMatrices().scale(openAnim, openAnim, 1f);
        ctx.getMatrices().translate(-width / 2f, -height / 2f, 0);

        int idx = 0;
        for (Module.Category cat : CATS)
            renderPanel(ctx, startX + idx++ * (PANEL_WIDTH + PANEL_MARGIN), startY, cat, mx, my);

        Module hov = getHoveredModule(mx, my, startX, startY);
        if (hov != null && hov.getDescription() != null && !hov.getDescription().isEmpty())
            drawDescription(ctx, hov.getDescription(), startY);

        DescriptionRenderQueue.renderAll(ctx);
        renderSearchField(ctx);
        renderTheme(ctx, mx, my);

        ctx.getMatrices().pop();
    }

    private void drawDescription(DrawContext ctx, String desc, int startY) {
        float dw = FontRenderers.sf_medium.getStringWidth(desc) + 14f;
        float dh = 20f;
        float dx = (width - dw) / 2f;
        float dy = startY - dh - 10f;
        Color guiColor = getGuiColor();
        ClickGui gui = ModuleManager.clickGui;
        if (gui != null && gui.blur.getValue()) {
            Render2DEngine.drawRoundedBlur(ctx.getMatrices(), dx - 6, dy - 3.5f, dw + 12, dh, 6f, guiColor);
        }
        Render2DEngine.drawRound(ctx.getMatrices(), dx, dy, dw, dh, 6f, guiColor);
        FontRenderers.sf_medium.drawString(ctx.getMatrices(), desc, dx + 7, dy + 5, Color.WHITE.getRGB());
    }

    private void renderPanel(DrawContext ctx, int x, int y, Module.Category cat, int mx, int my) {
        Color guiColor = getGuiColor();
        ClickGui gui = ModuleManager.clickGui;
        if (gui != null && gui.blur.getValue()) {
            Render2DEngine.drawRoundedBlur(ctx.getMatrices(), x, y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS, guiColor);
        }

        Render2DEngine.drawRound(ctx.getMatrices(), x - 1, y - 1, PANEL_WIDTH + 2, PANEL_HEIGHT + 2, PANEL_RADIUS + 1, getBorderColor());
        Render2DEngine.drawRound(ctx.getMatrices(), x, y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS, PANEL_BG_COLOR);

        String title = cat.getName();
        String icon  = CAT_ICONS.getOrDefault(title, "");
        float titleW = FontRenderers.sf_bold.getStringWidth(title);
        float iconW  = icon.isEmpty() ? 0f : FontRenderers.icons.getStringWidth(icon) + 6f;
        float totalW = titleW + iconW;
        float startX = x + (PANEL_WIDTH - totalW) / 2f;
        float titleH = FontRenderers.sf_bold.getStringHeight(title);
        float textY  = y + TITLE_MARGIN_TOP + 2 + (FONT_TITLE_BOX - titleH) / 2f;

        if (!icon.isEmpty()) {
            FontRenderers.icons.drawString(ctx.getMatrices(), icon, startX, textY - 1f, Color.WHITE.getRGB());
        }
        FontRenderers.sf_bold.drawString(ctx.getMatrices(), title, startX + iconW, textY, Color.WHITE.getRGB());

        Render2DEngine.drawRect(ctx.getMatrices(), x + 8, y + TITLE_MARGIN_TOP + TITLE_HEIGHT - 2, PANEL_WIDTH - 16, 1f, HEADER_SEPARATOR_COLOR);

        int maxScroll = calcMaxScroll(cat);
        scrollTargets.put(cat, MathHelper.clamp(scrollTargets.get(cat), 0f, maxScroll));
        scrollOffsets.put(cat, MathHelper.clamp(scrollOffsets.get(cat), 0f, maxScroll));

        float offset = scrollOffsets.get(cat);
        renderScrollbar(ctx, x, y, cat, offset);

        ctx.getMatrices().push();
        Render2DEngine.addWindow(ctx.getMatrices(), x, y + SCROLL_AREA_Y, x + PANEL_WIDTH, y + SCROLL_AREA_Y + SCROLL_AREA_H, 1f);

        float curY = y + SCROLL_AREA_Y - offset;
        for (Module mod : Managers.MODULE.getModulesByCategory(cat)) {
            if (!isVisible(mod)) continue;

            float ep    = getExpandProg(mod);
            int   settH = (int)(computeSettH(mod) * ep);
            int   totalH = FUNCTION_HEIGHT + settH;

            if (curY + totalH < y + SCROLL_AREA_Y) { curY += totalH; continue; }
            if (curY > y + PANEL_HEIGHT) break;

            int col1 = mod.isOn() ? ColorUtil.getColorStyle(30)  : MODULE_OFF_COLOR.getRGB();
            int cm1  = mod.isOn() ? ColorUtil.getColorStyle(30,  MODULE_BG_ALPHA) : MODULE_OFF_BG_COLOR.getRGB();
            int cm2  = mod.isOn() ? ColorUtil.getColorStyle(120, MODULE_BG_ALPHA) : cm1;

            Render2DEngine.drawGradientRound(ctx.getMatrices(),
                x + 4, curY - 1, PANEL_WIDTH - 8, totalH - 1, MODULE_ROUNDING,
                new Color(cm2, true), new Color(cm2, true), new Color(cm2, true), new Color(cm2, true));

            String label = (functionBinding && bindingModule == mod) ? "Binding..." : mod.getName();
            if (mod.isOn()) {
                FontRenderers.sf_medium_modules.drawGradientString(ctx.getMatrices(), label, x + 10, curY + 3, 30);
            } else {
                FontRenderers.sf_medium_modules.drawString(ctx.getMatrices(), label, x + 10, curY + 3, col1);
            }

            if (settH > 0) {
                float setY = curY + FUNCTION_HEIGHT;
                ctx.getMatrices().push();
                Render2DEngine.addWindow(ctx.getMatrices(), x + 1, (int)setY, x + PANEL_WIDTH - 1, (int)(setY + settH), 1f);

                for (Setting<?> s : mod.getSettings()) {
                    if (!s.isVisible()) continue;
                    int sh = getSettH(s, PANEL_WIDTH - 20);
                    if (sh <= 0) continue;
                    if (isBool(s)) {
                        boolRend.render(ctx, s, x + 10, (int)setY, PANEL_WIDTH - 20, sh);
                    } else if (s.isNumberSetting() && s.hasRestriction()) {
                        sliderRend.render(ctx, s, x + 10, (int)setY - 2, PANEL_WIDTH - 20, sh);
                    } else if (s.isEnumSetting() && !s.isPositionSetting()) {
                        modeRend.render(ctx, s, x + 10, (int)setY, PANEL_WIDTH - 20, sh);
                    } else if (s.isBindSetting()) {
                        bindRend.render(ctx, s, x + 10, (int)setY, PANEL_WIDTH - 20, sh);
                    } else if (s.getValue() instanceof mira.client.setting.impl.SettingGroup
                            || s.getValue() instanceof mira.client.setting.impl.BooleanSettingGroup) {
                        renderGroupHeader(ctx, s, x + 10, (int)setY, PANEL_WIDTH - 20, sh);
                    }
                    setY += sh + 1;
                }
                Render2DEngine.popWindow();
                ctx.getMatrices().pop();
            }

            if (!mod.getSettings().isEmpty()) {
                float ap = arrowProgress.getOrDefault(mod, mod.isExpanded() ? 1f : 0f);
                ap = lerp(ap, mod.isExpanded() ? 1f : 0f, 15f);
                if (Math.abs((mod.isExpanded() ? 1f : 0f) - ap) < 0.001f) ap = mod.isExpanded() ? 1f : 0f;
                arrowProgress.put(mod, ap);

                int ax = x + PANEL_WIDTH - 15;
                int ay = (int)(curY + FUNCTION_HEIGHT / 2f - 2);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(ax, ay, 0);
                ctx.getMatrices().multiply(new Quaternionf().fromAxisAngleRad(new Vector3f(0, 0, 1), (float)Math.toRadians(90f * ap)));
                FontRenderers.sf_medium_modules.drawString(ctx.getMatrices(), "→", -4, -4, col1);
                ctx.getMatrices().pop();
            }

            curY += totalH;
        }

        Render2DEngine.popWindow();
        ctx.getMatrices().pop();
    }

    private void renderScrollbar(DrawContext ctx, int x, int y, Module.Category cat, float offset) {
        int max = calcMaxScroll(cat);
        if (max <= 0) return;
        int sbW = 3, sbX = x + PANEL_WIDTH - sbW - 1;
        int sbH = SCROLL_AREA_H - 30, sbY = y + SCROLL_AREA_Y + 15;
        Render2DEngine.drawRound(ctx.getMatrices(), sbX, sbY, sbW, sbH, 1f, SCROLLBAR_TRACK_COLOR);
        float prog = offset / max;
        int th = Math.max(6, (int)(sbH * (SCROLL_AREA_H / (float)(SCROLL_AREA_H + max))));
        int ty = sbY + (int)(prog * (sbH - th));
        Render2DEngine.drawRound(ctx.getMatrices(), sbX, ty, sbW, th, 1f, SCROLLBAR_THUMB_COLOR);
    }

    private static final int FRIEND_BTN_SIZE = 16;
    private static final int FRIEND_BTN_GAP  = 6;

    private void renderSearchField(DrawContext ctx) {
        Color guiColor = getGuiColor();
        int sw = SEARCH_MAX_WIDTH;
        int sx = (width - sw) / 2;
        int sy = (height + PANEL_HEIGHT) / 2 + SEARCH_MARGIN_BOT;
        ClickGui gui = ModuleManager.clickGui;
        if (gui != null && gui.blur.getValue()) {
            Render2DEngine.drawRoundedBlur(ctx.getMatrices(), sx, sy, sw, SEARCH_HEIGHT, 6f, guiColor);
        }
        Render2DEngine.drawRound(ctx.getMatrices(), sx, sy, sw, SEARCH_HEIGHT, 4f, guiColor);
        Render2DEngine.drawRoundBorder(ctx.getMatrices(), sx, sy, sw, SEARCH_HEIGHT, 4f, 1f, PANEL_BORDER_COLOR_FIXED);

        String disp; int col; float tx;
        if (searchState.text.isEmpty() && !searchState.focused) {
            disp = "Поиск...";
            col  = SEARCH_HINT_COLOR.getRGB();
            tx   = sx + (sw - FontRenderers.sf_medium.getStringWidth(disp)) / 2f;
        } else {
            String t = searchState.text;
            if (searchState.focused && searchState.cursorVisible) {
                int p = Math.min(searchState.cursorPosition, t.length());
                t = t.substring(0, p) + "|" + t.substring(p);
            }
            disp = t; col = Color.WHITE.getRGB(); tx = sx + 6;
        }
        FontRenderers.sf_medium.drawString(ctx.getMatrices(), disp, tx,
            sy + (SEARCH_HEIGHT - 8) / 2f, col);

        int fbx = sx + sw + FRIEND_BTN_GAP;
        int fby = sy + (SEARCH_HEIGHT - FRIEND_BTN_SIZE) / 2;
        double scale = mc.getWindow().getScaleFactor();
        double mX = mc.mouse.getX() / scale;
        double mY = mc.mouse.getY() / scale;
        boolean friendHov = mX >= fbx && mX <= fbx + FRIEND_BTN_SIZE && mY >= fby && mY <= fby + FRIEND_BTN_SIZE;
        Render2DEngine.drawRound(ctx.getMatrices(), fbx, fby, FRIEND_BTN_SIZE, FRIEND_BTN_SIZE, 4f,
                friendHov ? new Color(0x9147FF | (140 << 24), true) : guiColor);
        Render2DEngine.drawRoundBorder(ctx.getMatrices(), fbx, fby, FRIEND_BTN_SIZE, FRIEND_BTN_SIZE, 4f, 1f, PANEL_BORDER_COLOR_FIXED);
        RenderSystem.setShaderColor(1f, 1f, 1f, friendHov ? 1f : 0.8f);
        ctx.drawTexture(TextureStorage.friendIcon, fbx + 3, fby + 3, 0, 0, 10, 10, 10, 10);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderTheme(DrawContext ctx, int mx, int my) {
        Color guiColor = getGuiColor();
        int tw = THEME_MAX_WIDTH;
        int tx = (width - tw) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;
        int ty = startY - THEME_MARGIN_TOP - THEME_HEIGHT;

        ClickGui gui = ModuleManager.clickGui;
        if (gui != null && gui.blur.getValue()) {
            Render2DEngine.drawRoundedBlur(ctx.getMatrices(), tx, ty, tw, THEME_HEIGHT, 3f, guiColor);
        }
        Render2DEngine.drawRound(ctx.getMatrices(), tx, ty, tw, THEME_HEIGHT, 3f, guiColor);

        int circleSize = THEME_HEIGHT - 5;
        int pad = 5;
        int totalThemes = ThemeManager.INSTANCE.getStyles().size() + 1;
        float maxScroll = Math.max(0, (totalThemes - VISIBLE_THEMES) * (circleSize + pad));
        themeScrollTarget = MathHelper.clamp(themeScrollTarget, 0, maxScroll);
        themeScrollOffset = MathHelper.clamp(themeScrollOffset, 0, maxScroll);

        ctx.getMatrices().push();
        Render2DEngine.addWindow(ctx.getMatrices(), tx + 1, ty, tx + tw - 1, ty + THEME_HEIGHT, 1f);

        float startX = tx + pad - themeScrollOffset;
        int centerY  = (int)(ty + (THEME_HEIGHT - circleSize) / 2f + 0.9f);
        String hoveredTheme = null;

        for (ThemeManager.Style style : ThemeManager.INSTANCE.getStyles()) {
            int sx = (int)startX;
            int c1 = style.colors[0];
            int c2 = style.colors.length > 1 ? style.colors[1] : c1;
            int vc1 = ThemeManager.gradient(5, 0,   c1, c2);
            int vc2 = ThemeManager.gradient(5, 180, c1, c2);
            int vc3 = ThemeManager.gradient(5, 90,  c1, c2);
            int vc4 = ThemeManager.gradient(5, 360, c1, c2);
            Render2DEngine.drawGradientRound(ctx.getMatrices(),
                sx, centerY + 0.5f, circleSize, circleSize, circleSize / 2f,
                new Color(vc1, true), new Color(vc2, true), new Color(vc3, true), new Color(vc4, true));
            if (mx >= sx && mx <= sx + circleSize && my >= centerY && my <= centerY + circleSize) {
                hoveredTheme = style.name;
                Render2DEngine.drawRoundBorder(ctx.getMatrices(), sx - 1, centerY - 0.5f, circleSize + 2, circleSize + 2, 5f, 0.5f,
                    new Color(Color.WHITE.getRGB(), true));
            }
            startX += circleSize + pad;
        }

        Render2DEngine.popWindow();
        ctx.getMatrices().pop();

        if (hoveredTheme != null) themeNameAnim += (1f - themeNameAnim) * 0.2f;
        else                      themeNameAnim += (0f - themeNameAnim) * 0.2f;

        if (themeNameAnim > 0.01f && hoveredTheme != null) {
            float nw = FontRenderers.sf_medium.getStringWidth(hoveredTheme);
            float nx = (width - nw) / 2f;
            FontRenderers.sf_medium.drawString(ctx.getMatrices(), hoveredTheme, nx,
                ty - 12, ColorUtil.applyAlpha(Color.WHITE.getRGB(), themeNameAnim));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int tw = THEME_MAX_WIDTH;
        int tx = (width - tw) / 2;
        int ty = (height - PANEL_HEIGHT) / 2 - THEME_MARGIN_TOP - THEME_HEIGHT;
        if (mx >= tx && mx <= tx + tw && my >= ty && my <= ty + THEME_HEIGHT) {
            themeScrollTarget += (float)(-sy * THEME_SCROLL_SPEED);
            return true;
        }
        int totalW = CATS.size() * (PANEL_WIDTH + PANEL_MARGIN) - PANEL_MARGIN;
        int startX = (width - totalW) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;
        int i = 0;
        for (Module.Category cat : CATS) {
            int px = startX + i++ * (PANEL_WIDTH + PANEL_MARGIN);
            if (mx >= px && mx <= px + PANEL_WIDTH
                    && my >= startY + SCROLL_AREA_Y && my <= startY + SCROLL_AREA_Y + SCROLL_AREA_H) {
                int max = calcMaxScroll(cat);
                if (max > 0) {
                    scrollTargets.compute(cat, (k, v) -> MathHelper.clamp(v - (float)sy * SCROLL_SPEED, 0, max));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int totalW = CATS.size() * (PANEL_WIDTH + PANEL_MARGIN) - PANEL_MARGIN;
        int startX = (width - totalW) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;
        int sw = SEARCH_MAX_WIDTH, searchX = (width - sw) / 2;
        int searchY = (height + PANEL_HEIGHT) / 2 + SEARCH_MARGIN_BOT;
        int bx = searchX + 185, by = searchY + 2, bw2 = 16, bh2 = 16;

        if (mx >= searchX && mx <= searchX + sw && my >= searchY && my <= searchY + SEARCH_HEIGHT) {
            searchState.focused = true;
            searchState.cursorPosition = searchState.text.length();
            return true;
        }

        int fbx = searchX + sw + FRIEND_BTN_GAP;
        int fby = searchY + (SEARCH_HEIGHT - FRIEND_BTN_SIZE) / 2;
        if (mx >= fbx && mx <= fbx + FRIEND_BTN_SIZE && my >= fby && my <= fby + FRIEND_BTN_SIZE) {
            Setting<PositionSetting> friendPos = ModuleManager.windows.friendPos;
            mc.setScreen(new WindowsScreen(
                    FriendsWindow.get(
                            friendPos.getValue().getX() * mc.getWindow().getScaledWidth(),
                            friendPos.getValue().getY() * mc.getWindow().getScaledHeight(),
                            friendPos)
            ));
            return true;
        } else {
            searchState.focused = false;
        }

        {
            int ttx = (width - THEME_MAX_WIDTH) / 2;
            int startYt = (height - PANEL_HEIGHT) / 2;
            int tty = startYt - THEME_MARGIN_TOP - THEME_HEIGHT;
            int cs = THEME_HEIGHT - 5, pad = 5;
            int centerY = (int)(tty + (THEME_HEIGHT - cs) / 2f + 0.9f);
            float curX = ttx + pad - themeScrollTarget;

            if (btn == 0) {
                for (ThemeManager.Style style : ThemeManager.INSTANCE.getStyles()) {
                    if (mx >= curX && mx <= curX + cs && my >= centerY && my <= centerY + cs) {
                        ThemeManager.INSTANCE.setTheme(style); return true;
                    }
                    curX += cs + pad;
                }
            }
            if (btn == 1) {
                curX = ttx + pad - themeScrollTarget;
                for (ThemeManager.Style style : new ArrayList<>(ThemeManager.INSTANCE.getStyles())) {
                    if (mx >= curX && mx <= curX + cs && my >= centerY && my <= centerY + cs) {
                        if (style.name.startsWith("Custom")) ThemeManager.INSTANCE.removeStyle(style);
                        return true;
                    }
                    curX += cs + pad;
                }
            }
        }

        if (functionBinding && bindingModule != null) {
            bindingModule.setBind(-(btn + 2), true, false);
            functionBinding = false; bindingModule = null; return true;
        }

        int idx = 0;
        for (Module.Category cat : CATS) {
            int px = startX + idx++ * (PANEL_WIDTH + PANEL_MARGIN);
            float off = scrollOffsets.get(cat);
            float curY = startY + SCROLL_AREA_Y - off;

            for (Module mod : Managers.MODULE.getModulesByCategory(cat)) {
                if (!isVisible(mod)) continue;
                float ep    = expandProgress.getOrDefault(mod, mod.isExpanded() ? 1f : 0f);
                int   settH = (int)(computeSettH(mod) * ep);
                int   totalH = FUNCTION_HEIGHT + settH;

                if (curY + totalH < startY + SCROLL_AREA_Y) { curY += totalH; continue; }
                if (curY > startY + SCROLL_AREA_Y + SCROLL_AREA_H) break;

                if (mx >= px && mx <= px + PANEL_WIDTH
                        && my >= curY && my <= curY + FUNCTION_HEIGHT
                        && my >= startY + SCROLL_AREA_Y && my <= startY + SCROLL_AREA_Y + SCROLL_AREA_H) {
                    if (btn == 0)      { mod.toggle(); return true; }
                    else if (btn == 1) { mod.setExpanded(!mod.isExpanded()); return true; }
                    else if (btn == 2) { functionBinding = true; bindingModule = mod; return true; }
                }

                if (settH > 0) {
                    float setY = curY + FUNCTION_HEIGHT;
                    for (Setting<?> s : mod.getSettings()) {
                        if (!s.isVisible()) continue;
                        int sh = getSettH(s, PANEL_WIDTH - 20);
                        if (sh <= 0) continue;
                        if (mx >= px && mx <= px + PANEL_WIDTH
                                && my >= setY && my <= setY + sh
                                && my >= startY + SCROLL_AREA_Y && my <= startY + SCROLL_AREA_Y + SCROLL_AREA_H) {
                            if (isBool(s)) {
                                boolRend.mouseClicked(s, mx, my, btn, px + 10, (int)setY, PANEL_WIDTH - 20, sh);
                            } else if (s.isNumberSetting() && s.hasRestriction()) {
                                if (sliderRend.mouseClicked(s, mx, my, btn, px + 10, (int)setY - 2, PANEL_WIDTH - 20, sh)) {
                                    draggingSlider = s;
                                    draggingSliderX = px + 10;
                                    draggingSliderW = PANEL_WIDTH - 20;
                                }
                            } else if (s.isEnumSetting() && !s.isPositionSetting()) {
                                modeRend.mouseClicked(s, mx, my, btn, px + 10, (int)setY, PANEL_WIDTH - 20, sh);
                            } else if (s.isBindSetting()) {
                                bindRend.mouseClicked(s, mx, my, btn, px + 10, (int)setY, PANEL_WIDTH - 20, sh);
                                return true;
                            } else if (s.getValue() instanceof mira.client.setting.impl.BooleanSettingGroup bg) {
                                float aw = FontRenderers.sf_medium_mini.getStringWidth(bg.isExtended() ? "v" : ">");
                                int toggleW = 20;
                                int toggleX = px + 10 + (PANEL_WIDTH - 20) - toggleW - (int)aw - 6;
                                if (mx >= toggleX && mx <= toggleX + toggleW) {
                                    bg.setEnabled(!bg.isEnabled());
                                } else {
                                    toggleGroupExtended(s);
                                }
                                return true;
                            } else if (s.getValue() instanceof mira.client.setting.impl.SettingGroup) {
                                toggleGroupExtended(s);
                                return true;
                            }
                        }
                        setY += sh + 1;
                    }
                }
                curY += totalH;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingSlider != null && btn == 0) {
            sliderRend.mouseDragged(draggingSlider, mx, draggingSliderX, draggingSliderW);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (draggingSlider != null) {
            sliderRend.mouseReleased(draggingSlider);
            draggingSlider = null;
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean charTyped(char c, int kc) {
        if (searchState.focused && searchState.text.length() < 30) {
            String b = searchState.text.substring(0, searchState.cursorPosition);
            String a = searchState.text.substring(searchState.cursorPosition);
            searchState.text = b + c + a;
            searchState.cursorPosition++;
            resetScroll();
            return true;
        }
        return super.charTyped(c, kc);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mods) {
        if (bindRend.keyPressed(kc)) return true;
        if (functionBinding && bindingModule != null) {
            if (kc == GLFW.GLFW_KEY_ESCAPE || kc == GLFW.GLFW_KEY_DELETE)
                bindingModule.setBind(-1, false, false);
            else
                bindingModule.setBind(kc, false, false);
            functionBinding = false; bindingModule = null; return true;
        }
        if (searchState.focused) {
            switch (kc) {
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> { searchState.focused = false; return true; }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchState.cursorPosition > 0) {
                        searchState.text = searchState.text.substring(0, searchState.cursorPosition - 1)
                            + searchState.text.substring(searchState.cursorPosition);
                        searchState.cursorPosition--;
                        resetScroll();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (searchState.cursorPosition < searchState.text.length()) {
                        searchState.text = searchState.text.substring(0, searchState.cursorPosition)
                            + searchState.text.substring(searchState.cursorPosition + 1);
                        resetScroll();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT  -> { if (searchState.cursorPosition > 0) searchState.cursorPosition--; return true; }
                case GLFW.GLFW_KEY_RIGHT -> { if (searchState.cursorPosition < searchState.text.length()) searchState.cursorPosition++; return true; }
            }
        }
        if (kc == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(kc, sc, mods);
    }

    @Override
    public void close() {
        if (isClose) return;
        isClose = true;
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - searchState.lastCursorBlink >= 500) {
            searchState.cursorVisible = !searchState.cursorVisible;
            searchState.lastCursorBlink = now;
        }
    }

    private Color getGuiColor() {
        return GUI_COLOR;
    }

    private boolean isVisible(Module m) {
        String f = searchState.text.toLowerCase();
        return f.isEmpty() || m.getName().toLowerCase().contains(f);
    }

    private int calcMaxScroll(Module.Category cat) {
        int total = 0;
        for (Module m : Managers.MODULE.getModulesByCategory(cat)) {
            if (!isVisible(m)) continue;
            float ep = expandProgress.getOrDefault(m, m.isExpanded() ? 1f : 0f);
            total += FUNCTION_HEIGHT + (int)(computeSettH(m) * ep);
        }
        return Math.max(0, total - SCROLL_AREA_H);
    }

    private float getExpandProg(Module m) {
        float target = m.isExpanded() ? 1f : 0f;
        float prog   = expandProgress.getOrDefault(m, target);
        prog = lerp(prog, target, 15f);
        if (Math.abs(target - prog) < 0.001f) prog = target;
        expandProgress.put(m, prog);
        return prog;
    }

    private void renderGroupHeader(DrawContext ctx, Setting<?> s, int x, int y, int w, int h) {
        boolean extended = isGroupExtended(s);
        String arrow = extended ? "v" : ">";
        float aw = FontRenderers.sf_medium_mini.getStringWidth(arrow);
        if (s.getValue() instanceof mira.client.setting.impl.BooleanSettingGroup bg) {
            int toggleW = 20, toggleH = 9;
            int toggleX = x + w - toggleW - (int)aw - 6;
            int toggleY = (int)(y + h / 2f - toggleH / 2f);
            Color offColor = new Color(50, 50, 50, 200);
            int onColor = new Color(0, 200, 83, 255).getRGB();
            int trackColor = bg.isEnabled() ? onColor : offColor.getRGB();
            Render2DEngine.drawRound(ctx.getMatrices(), toggleX, toggleY, toggleW, toggleH, 4f, new Color(trackColor, true));
            int knobR = 6;
            float kx = bg.isEnabled() ? toggleX + toggleW - knobR - 1 : toggleX + 1;
            Render2DEngine.renderRoundedQuad(ctx.getMatrices(), Color.WHITE,
                    kx, toggleY + (toggleH - knobR) / 2f,
                    kx + knobR, toggleY + (toggleH - knobR) / 2f + knobR,
                    knobR / 2f, 8);
            FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), s.getName(), x, y + h / 2f - 1f, Color.WHITE.getRGB());
        } else {
            FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), s.getName(), x, y + h / 2f - 1f, Color.WHITE.getRGB());
        }
        FontRenderers.sf_medium_mini.drawString(ctx.getMatrices(), arrow, x + w - aw, y + h / 2f - 1f,
                new Color(200, 206, 220).getRGB());
    }

    private boolean isGroupExtended(Setting<?> s) {
        if (s.getValue() instanceof mira.client.setting.impl.SettingGroup sg) return sg.isExtended();
        if (s.getValue() instanceof mira.client.setting.impl.BooleanSettingGroup bg) return bg.isExtended();
        return false;
    }

    private void toggleGroupExtended(Setting<?> s) {
        if (s.getValue() instanceof mira.client.setting.impl.SettingGroup sg) sg.setExtended(!sg.isExtended());
        if (s.getValue() instanceof mira.client.setting.impl.BooleanSettingGroup bg) bg.setExtended(!bg.isExtended());
    }

    private int computeSettH(Module m) {
        int h = 0;
        for (Setting<?> s : m.getSettings()) {
            if (!s.isVisible()) continue;
            h += getSettH(s, PANEL_WIDTH - 20) + 1;
        }
        return Math.max(0, h);
    }

    private static final int GROUP_HEADER_H = 13;

    private int getSettH(Setting<?> s, int w) {
        if (!s.isVisible()) return 0;
        if (isBool(s)) return boolRend.getHeight();
        if (s.isNumberSetting() && s.hasRestriction()) return sliderRend.getHeight();
        if (s.isEnumSetting() && !s.isPositionSetting()) return modeRend.getHeight(s, w);
        if (s.isBindSetting()) return bindRend.getHeight();
        if (s.getValue() instanceof mira.client.setting.impl.SettingGroup
                || s.getValue() instanceof mira.client.setting.impl.BooleanSettingGroup)
            return GROUP_HEADER_H;
        return 0;
    }

    private boolean isBool(Setting<?> s) {
        return s.getValue() instanceof Boolean
            && !s.getName().equals("Enabled")
            && !s.getName().equals("Drawn");
    }

    private void resetScroll() {
        CATS.forEach(c -> { scrollTargets.put(c, 0f); scrollOffsets.put(c, 0f); });
    }

    private Module getHoveredModule(int mx, int my, int startX, int startY) {
        int idx = 0;
        for (Module.Category cat : CATS) {
            int px = startX + idx++ * (PANEL_WIDTH + PANEL_MARGIN);
            if (mx < px || mx > px + PANEL_WIDTH) continue;
            float off  = scrollOffsets.get(cat);
            float curY = startY + SCROLL_AREA_Y - off;
            for (Module m : Managers.MODULE.getModulesByCategory(cat)) {
                if (!isVisible(m)) continue;
                float ep     = expandProgress.getOrDefault(m, m.isExpanded() ? 1f : 0f);
                int   totalH = FUNCTION_HEIGHT + (int)(computeSettH(m) * ep);
                if (mx >= px && mx <= px + PANEL_WIDTH
                        && my >= curY && my <= curY + FUNCTION_HEIGHT
                        && my >= startY + SCROLL_AREA_Y
                        && my <= startY + SCROLL_AREA_Y + SCROLL_AREA_H)
                    return m;
                curY += totalH;
            }
        }
        return null;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.min(1f, t * 0.05f);
    }
}

class SearchState {
    String text = "";
    boolean focused = false;
    int cursorPosition = 0;
    long lastCursorBlink = System.currentTimeMillis();
    boolean cursorVisible = true;
}
