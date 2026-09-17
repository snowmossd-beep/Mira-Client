package mira.client.features.hud.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.gui.font.FontRenderers;
import mira.client.features.hud.HudElement;
import mira.client.features.modules.misc.NameProtect;
import mira.client.setting.Setting;
import mira.client.setting.impl.ColorSetting;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.TextUtil;
import mira.client.utility.render.animation.AnimationUtility;

import java.awt.*;

public class WaterMark extends HudElement {
    public WaterMark() {
        super("WaterMark", 120, 50);
    }

    public static final Setting<Mode> mode = new Setting<>("Mode", Mode.Default);
    private final Setting<Boolean> showServer = new Setting<>("ShowServer", true);
    private final Setting<Boolean> showSubtitle = new Setting<>("ShowSubtitle", true);
    private final Setting<ColorSetting> accentColor = new Setting<>("AccentColor", new ColorSetting(-9192762));

    private final TextUtil textUtil = new TextUtil("Mira", "Mira", "Mira", "Mira", "Mira", "Mira", "Mira", "Mira", "Mira");

    private float widthAnim = 100;

    private static final int   LOGO_BOX     = 20;
    private static final float RADIUS       = 6f;
    private static final float PILL_RADIUS  = 6f;
    private static final float gapConst     = 5f;
    private static final Color PANEL_BG     = new Color(8, 11, 17, 145);
    private static final Color LOGO_BG      = new Color(255, 255, 255, 22);
    private static final Color SEPARATOR    = new Color(255, 255, 255, 42);
    private static final Color IP_PILL_BG   = new Color(255, 255, 255, 16);
    private static final Color SUBTITLE_TXT = new Color(225, 229, 238);
    private static final Color ARROW_COLOR  = new Color(170, 178, 192);

    private static final String VERSION = "v1.0.0";

    public enum Mode {
        Compact, Default
    }

    public void onRender2D(DrawContext context) {
        super.onRender2D(context);
        String username = ModuleManager.nameProtect.isEnabled() ? NameProtect.getCustomName() : mc.getSession().getUsername();
        String server = mc.isInSingleplayer() ? "Single Player" : mc.getNetworkHandler().getServerInfo().address;

        boolean compact = mode.getValue() == Mode.Compact;

        String titleText = "MIRA  " + VERSION;
        float titleW = FontRenderers.sf_bold_mini.getStringWidth(titleText);
        float serverW = FontRenderers.sf_bold_mini.getStringWidth(server);

        float row1H = 20f;
        float row2H = compact || !showSubtitle.getValue() ? 0f : 18f;
        float rowGap = row2H > 0 ? 3f : 0f;

        float pill1W = titleW + 16;
        float pill2W = showServer.getValue() && !compact ? serverW + 16 : 0;
        float row1PillsW = pill1W + (pill2W > 0 ? gapConst + pill2W : 0);

        float subtitleW = FontRenderers.sf_bold_mini.getStringWidth(username);
        float row2ContentW = row2H > 0 ? 10 + subtitleW + 8 + 10 + 10 : 0;

        float rightContentW = Math.max(row1PillsW, row2ContentW);

        float avatarSize = row1H + rowGap + row2H;
        float contentW = avatarSize + gapConst + rightContentW;
        widthAnim = AnimationUtility.fast(widthAnim, contentW, 15);
        float h = avatarSize;

        float x = getPosX();
        float y = getPosY();

        Color accent = new Color(accentColor.getValue().getColor(), true);
        Color pillAccentBg = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 125);

        Render2DEngine.addWindow(context.getMatrices(), x, y, x + widthAnim, y + h, 1f);

        float headSize = avatarSize;
        Render2DEngine.drawBlurredShadow(context.getMatrices(), x + 1, y + 1, headSize - 2, headSize - 2, 6, new Color(0, 0, 0, 70));
        Render2DEngine.drawRoundedBlur(context.getMatrices(), x, y, headSize, headSize, RADIUS, LOGO_BG);
        Render2DEngine.drawRoundBorder(context.getMatrices(), x, y, headSize, headSize, RADIUS, 1f, SEPARATOR);
        if (mc.player instanceof AbstractClientPlayerEntity acp) {
            Identifier skin = acp.getSkinTextures().texture();
            int pad = Math.max(3, (int) (headSize * 0.12f));
            int hx = (int) (x + pad);
            int hy = (int) (y + pad);
            int hSize = (int) (headSize - pad * 2);
            context.enableScissor((int) x, (int) y, (int) (x + headSize), (int) (y + headSize));
            context.drawTexture(skin, hx, hy, hSize, hSize, 8, 8, 8, 8, 64, 64);
            context.drawTexture(skin, hx, hy, hSize, hSize, 40, 8, 8, 8, 64, 64);
            context.disableScissor();
        } else {
            FontRenderers.sf_bold_mini.drawCenteredString(context.getMatrices(), "A", x + headSize / 2f, y + headSize / 2f - 3.5f, accentColor.getValue().getColor());
        }
        float cx = x + headSize + gapConst;

        Render2DEngine.drawBlurredShadow(context.getMatrices(), cx + 1, y + 1, pill1W - 2, row1H - 2, 6, new Color(0, 0, 0, 60));
        Render2DEngine.drawRoundedBlur(context.getMatrices(), cx, y, pill1W, row1H, RADIUS, pillAccentBg);
        FontRenderers.sf_bold_mini.drawCenteredString(context.getMatrices(), titleText, cx + pill1W / 2f, y + row1H / 2f - 3.5f, Color.WHITE.getRGB());
        float px = cx + pill1W + gapConst;

        if (pill2W > 0) {
            Render2DEngine.drawBlurredShadow(context.getMatrices(), px + 1, y + 1, pill2W - 2, row1H - 2, 6, new Color(0, 0, 0, 60));
            Render2DEngine.drawRoundedBlur(context.getMatrices(), px, y, pill2W, row1H, RADIUS, IP_PILL_BG);
            Render2DEngine.drawRoundBorder(context.getMatrices(), px, y, pill2W, row1H, RADIUS, 1f, SEPARATOR);
            FontRenderers.sf_bold_mini.drawCenteredString(context.getMatrices(), server, px + pill2W / 2f, y + row1H / 2f - 3.5f, new Color(200, 206, 220).getRGB());
        }

        if (row2H > 0) {
            float ry = y + row1H + rowGap;
            float row2W = rightContentW;
            Render2DEngine.drawBlurredShadow(context.getMatrices(), cx + 1, ry + 1, row2W - 2, row2H - 2, 6, new Color(0, 0, 0, 60));
            Render2DEngine.drawRoundedBlur(context.getMatrices(), cx, ry, row2W, row2H, RADIUS - 1f, PANEL_BG);
            Render2DEngine.drawRoundBorder(context.getMatrices(), cx, ry, row2W, row2H, RADIUS - 1f, 1f, SEPARATOR);
            FontRenderers.sf_bold_mini.drawString(context.getMatrices(), username, cx + 10, ry + row2H / 2f - 3.5f, SUBTITLE_TXT.getRGB());
            FontRenderers.sf_bold_mini.drawString(context.getMatrices(), ">", cx + row2W - 16, ry + row2H / 2f - 3.5f, ARROW_COLOR.getRGB());
        }

        Render2DEngine.popWindow();
        setBounds(x, y, widthAnim, h);
    }

    @Override
    public void onUpdate() {
        textUtil.tick();
    }
                                                   }
    
