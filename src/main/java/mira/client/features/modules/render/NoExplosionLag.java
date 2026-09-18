package mira.client.features.modules.render;

import mira.client.features.modules.Module;
import mira.client.setting.Setting;

public class NoExplosionLag extends Module {
    public final Setting<Boolean> explosions = new Setting<>("Explosions", true);
    public final Setting<Boolean> fireBlock = new Setting<>("FireBlock", true);
    public final Setting<Boolean> fireEntity = new Setting<>("FireEntity", true);
    public final Setting<Boolean> fireOverlay = new Setting<>("FireOverlay", false);

    public NoExplosionLag() {
        super("NoExplosionLag", Category.RENDER);
    }
      }
