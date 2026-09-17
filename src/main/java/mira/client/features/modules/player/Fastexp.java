package mira.client.features.modules.player;

import mira.client.features.modules.Module;
import mira.client.injection.accesors.IMinecraftClient;
import mira.client.setting.Setting;
import net.minecraft.item.Items;

public class Fastexp extends Module {
    public Fastexp() {
        super("Fastexp", Category.PLAYER);
    }

    private final Setting<Integer> delay = new Setting<>("Delay", 0, 0, 20);

    @Override
    public void onUpdate() {
        if (mc.player == null) return;
        if (mc.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE) return;
        ((IMinecraftClient) mc).setUseCooldown(delay.getValue());
    }
}
