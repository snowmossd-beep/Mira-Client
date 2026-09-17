package mira.client.features.modules.movement;

import net.minecraft.client.util.math.MatrixStack;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;

public class Parkour extends Module {
    public Parkour() {
        super("Parkour", Category.MOVEMENT);
    }

    private final Setting<Float> jumpFactor = new Setting<>("JumpFactor", 0.01f, 0.001f, 0.3f);

    private final mira.client.utility.Timer delay = new mira.client.utility.Timer();

    public void onRender3D(MatrixStack stack) {
        if (mc.player.isOnGround()
                && !mc.options.jumpKey.isPressed()
                && !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-jumpFactor.getValue(), 0, -jumpFactor.getValue()).offset(0, -0.99, 0)).iterator().hasNext()
                && delay.every(150))
            mc.player.jump();
    }
}
