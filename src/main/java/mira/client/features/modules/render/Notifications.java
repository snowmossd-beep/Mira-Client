package mira.client.features.modules.render;

import mira.client.features.modules.Module;
import mira.client.setting.Setting;

public final class Notifications extends Module {
    public Notifications() {
        super("Notifications", Category.RENDER);
    }

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.CrossHair);

    public enum Mode {
        Default, CrossHair, Text
    }
}
