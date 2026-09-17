package mira.client.features.modules.render;
import mira.client.gui.miragui.MiraGui;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.setting.impl.ColorSetting;
import java.awt.*;
public final class MiraClientGui extends Module {
    public static final Setting<ColorSetting> onColor1 = new Setting<>("OnColor1", new ColorSetting(new Color(71, 0, 117, 255).getRGB()));
    public static final Setting<ColorSetting> onColor2 = new Setting<>("OnColor2", new ColorSetting(new Color(32, 1, 96, 255).getRGB()));
    public static final Setting<Float> scrollSpeed = new Setting<>("ScrollSpeed", 1f, 0.1F, 2.0F);
    public MiraClientGui() {
        super("MiraGui", Category.RENDER);
    }
    @Override
    public void onEnable() {
        mc.setScreen(MiraGui.getMiraGui());
        disable();
    }
    public static Color getColorByTheme(int id) {
        return switch (id) {
            case 0 -> new Color(37, 27, 41, 250);
            case 1 -> new Color(50, 35, 60, 250);
            case 2 -> new Color(-1);
            case 3, 8 -> new Color(0x656565);
            case 4 -> new Color(50, 35, 60, 178);
            case 5 -> new Color(133, 93, 162, 178);
            case 6 -> new Color(88, 64, 107, 178);
            case 7 -> new Color(25, 20, 30, 255);
            case 9 -> new Color(50, 35, 60, 178);
            default -> new Color(37, 27, 41, 250);
        };
    }
}
