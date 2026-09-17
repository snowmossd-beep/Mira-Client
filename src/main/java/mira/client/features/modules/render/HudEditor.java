package mira.client.features.modules.render;

import mira.client.features.modules.Module;
import mira.client.gui.hud.HudEditorGui;
import mira.client.setting.Setting;
import mira.client.utility.render.ThemeManager;

import java.awt.*;

import static mira.client.features.modules.Module.mc;

public class HudEditor extends Module {

    public static final Setting<Boolean>   sticky      = new Setting<>("Sticky",       true);
    public static final Setting<HudStyle>  hudStyle    = new Setting<>("HudStyle",     HudStyle.Blurry);
    public static final Setting<ArrowsStyle> arrowsStyle = new Setting<>("ArrowsStyle", ArrowsStyle.Default);
    public static final Setting<Boolean>   glow        = new Setting<>("Light",        true);
    public static final Setting<Float>     hudRound    = new Setting<>("HudRound",     4f,   1f,  7f);
    public static final Setting<Float>     alpha       = new Setting<>("Alpha",        0.9f, 0f,  1f);
    public static final Setting<Float>     blend       = new Setting<>("Blend",        10f,  1f, 15f);
    public static final Setting<Float>     outline     = new Setting<>("Outline",      0.5f, 0f,  2.5f);
    public static final Setting<Float>     glow1       = new Setting<>("Glow",         0.5f, 0f,  1f);
    public static final Setting<Float>     blurOpacity = new Setting<>("BlurOpacity",  0.55f,0f,  1f);
    public static final Setting<Float>     blurStrength= new Setting<>("BlurStrength", 20f,  5f, 50f);
    public static final Setting<Integer>   colorSpeed  = new Setting<>("ColorSpeed",   5,   1, 20);
    public static final Setting<mira.client.setting.impl.ColorSetting> hcolor1 =
            new Setting<>("Color1", new mira.client.setting.impl.ColorSetting(0xFF5433FF));
    public static final Setting<mira.client.setting.impl.ColorSetting> acolor  =
            new Setting<>("Color2", new mira.client.setting.impl.ColorSetting(0xFF00FFFF));

    public static Color getColor(int index) {
        return ThemeManager.INSTANCE.getColor(index);
    }

    public static Color getColorAlpha(int index, int alpha) {
        return ThemeManager.INSTANCE.getColorAlpha(index, alpha);
    }

    public static int getColorInt(int index) {
        return ThemeManager.INSTANCE.getColor(index).getRGB();
    }

    public static int getFirstColor() {
        return ThemeManager.INSTANCE.getFirstColor();
    }

    public static int getSecondColor() {
        return ThemeManager.INSTANCE.getSecondColor();
    }

    public static int getColorGradient(int index) {
        return ThemeManager.gradient(5, index, ThemeManager.INSTANCE.getFirstColor(), ThemeManager.INSTANCE.getSecondColor());
    }

    public HudEditor() {
        super("HudEditor", Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        mc.setScreen(new HudEditorGui());
        disable("open");
    }

    public enum HudStyle   { Blurry, Flat, Glowing }
    public enum ArrowsStyle { Default, Arrows, Numbers, None, New }
    public enum ColorMode  { Static, Gradient, Rainbow }
    }
