package mira.client.gui.font;

import org.jetbrains.annotations.NotNull;
import mira.client.MiraClient;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;

public class FontRenderers {

    public static FontRenderer settings;
    public static FontRenderer modules;
    public static FontRenderer categories;
    public static FontRenderer icons;
    public static FontRenderer mid_icons;
    public static FontRenderer big_icons;
    public static FontRenderer thglitch;
    public static FontRenderer thglitchBig;
    public static FontRenderer monsterrat;
    public static FontRenderer sf_bold;
    public static FontRenderer sf_bold_mini;
    public static FontRenderer sf_bold_micro;
    public static FontRenderer sf_medium;
    public static FontRenderer sf_medium_mini;
    public static FontRenderer sf_medium_modules;
    public static FontRenderer minecraft;
    public static FontRenderer profont;
    public static FontRenderer gilroy;
    public static FontRenderer gilroy_bold;
    public static FontRenderer gilroy_mini;
    public static FontRenderer hud;

    private static final String FONTS_PATH = "assets/mira/fonts/";

    public static volatile RenderFonts[] rf_comfortaa   = new RenderFonts[256];
    public static volatile RenderFonts[] rf_durman      = new RenderFonts[256];
    public static volatile RenderFonts[] rf_glitched    = new RenderFonts[256];
    public static volatile RenderFonts[] rf_icons       = new RenderFonts[256];
    public static volatile RenderFonts[] rf_monsterrat  = new RenderFonts[256];
    public static volatile RenderFonts[] rf_profont     = new RenderFonts[256];
    public static volatile RenderFonts[] rf_sf_bold     = new RenderFonts[256];
    public static volatile RenderFonts[] rf_sf_medium   = new RenderFonts[256];
    public static volatile RenderFonts[] rf_iconsWex    = new RenderFonts[256];
    public static volatile RenderFonts[] rf_hud         = new RenderFonts[256];
    public static volatile RenderFonts[] rf_gilroy      = new RenderFonts[256];
    public static volatile RenderFonts[] rf_gilroy_bold = new RenderFonts[256];
    public static volatile RenderFonts[] rf_icomoon     = new RenderFonts[256];

    private static boolean rfInitialized = false;

    public static void initRenderFonts() {
        if (rfInitialized) return;
        rfInitialized = true;
        initArray(rf_comfortaa,   "comfortaa.ttf");
        initArray(rf_durman,      "durman.ttf");
        initArray(rf_glitched,    "glitched.ttf");
        initArray(rf_icons,       "icons.ttf");
        initArray(rf_monsterrat,  "monsterrat.ttf");
        initArray(rf_profont,     "profont.ttf");
        initArray(rf_sf_bold,     "sf_bold.ttf");
        initArray(rf_sf_medium,   "sf_medium.ttf");
        initArray(rf_iconsWex,    "iconsWex.ttf");
        initArray(rf_hud,         "hud.ttf");
        initArray(rf_gilroy,      "gilroy.ttf");
        initArray(rf_gilroy_bold, "gilroy-bold.ttf");
        initArray(rf_icomoon,     "icomoon.ttf");
    }

    private static void initArray(RenderFonts[] array, String filename) {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT,
                Objects.requireNonNull(FontRenderers.class.getClassLoader()
                    .getResourceAsStream(FONTS_PATH + filename)));
            for (int i = 1; i < array.length; i++) {
                array[i] = new RenderFonts(base, i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static FontRenderer getModulesRenderer() {
        return modules;
    }

    public static @NotNull FontRenderer create(float size, String name) throws IOException, FontFormatException {
        return new FontRenderer(
            Font.createFont(Font.TRUETYPE_FONT,
                Objects.requireNonNull(MiraClient.class.getClassLoader()
                    .getResourceAsStream(FONTS_PATH + name + ".ttf")))
                .deriveFont(Font.PLAIN, size / 2f),
            size / 2f);
    }
    }
